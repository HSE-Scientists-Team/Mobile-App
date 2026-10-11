package org.matchlab.academic_net.feature.auth.data.mock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.matchlab.academic_net.feature.auth.data.AuthDataSource
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.CodeRequestOutcome
import org.matchlab.academic_net.feature.auth.domain.CodeVerificationOutcome
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.auth.domain.VerificationChallenge
import org.matchlab.academic_net.mockserver.MockAccounts
import org.matchlab.academic_net.mockserver.MockTrustedDomains
import org.matchlab.academic_net.utils.UuidIdGenerator

/**
 * In-memory stand-in for the sign-in server: answers after [latencyMillis], like a network call.
 *
 * It plays the server, and its rules are the contract for the real backend:
 *  - the address is normalized (trimmed, lower case); its domain is the part after `@`;
 *  - the domain is trusted if it is in [trustedDomains] or is a subdomain of one
 *    (`student.hse.ru` under `hse.ru`); the registry is kept by the administration panel and
 *    never sent to the app; for an untrusted domain no e-mail is sent;
 *  - the code is six digits and lives [CodeLifetime]; a new code cancels the old one;
 *  - the next code can be requested after [ResendInterval], and no more than [HourlyLimit] e-mails
 *    per address per hour are sent;
 *  - [MaxAttempts] wrong codes burn the code;
 *  - a right code finds or creates the account (sign-in and registration are the same action) and
 *    issues an opaque token; the mock gives every account the [MockAccounts.viewer] id.
 *
 * Time comes from [timeSource] (tests pass `TestTimeSource`); the code and the token from the
 * generators ([codeGenerator] always answers `123456` by default).
 */
class MockAuthDataSource(
    private val latencyMillis: Long = DefaultLatencyMillis,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    trustedDomains: Set<String> = MockTrustedDomains.domains,
    private val codeGenerator: () -> String = { MockCode },
    private val tokenGenerator: () -> String = { UuidIdGenerator.newId() },
) : AuthDataSource {
    private val trustedDomains: Set<String> = trustedDomains.mapTo(mutableSetOf()) { it.trim().lowercase() }

    private val lock = Mutex()

    /** The valid code of each address. */
    private val pendingCodes = mutableMapOf<String, PendingCode>()

    /** When the e-mails to each address were sent, within the last hour. */
    private val sentEmails = mutableMapOf<String, List<TimeMark>>()

    private val activeTokens = mutableSetOf<String>()

    private class PendingCode(val code: String, val issuedAt: TimeMark, val failedAttempts: Int = 0)

    override suspend fun requestCode(email: String): CodeRequestOutcome {
        simulateNetwork()
        val address = email.trim().lowercase()
        if (!isTrusted(address)) return CodeRequestOutcome.UntrustedDomain

        return lock.withLock {
            val sent = sentEmails[address].orEmpty().filter { it.elapsedNow() < HourlyWindow }

            // Both limits may apply at once; the later of the two moments is when to come back.
            val untilResend = sent.lastOrNull()?.let { ResendInterval - it.elapsedNow() }
            val untilWindowFrees = if (sent.size >= HourlyLimit) HourlyWindow - sent.first().elapsedNow() else null
            val wait = listOfNotNull(untilResend, untilWindowFrees).filter { it.isPositive() }.maxOrNull()
            if (wait != null) {
                sentEmails[address] = sent
                return@withLock CodeRequestOutcome.TooManyRequests(wait)
            }

            pendingCodes[address] = PendingCode(codeGenerator(), timeSource.markNow())
            sentEmails[address] = sent + timeSource.markNow()
            CodeRequestOutcome.Sent(VerificationChallenge(address, ResendInterval))
        }
    }

    override suspend fun verifyCode(email: String, code: String): CodeVerificationOutcome {
        simulateNetwork()
        val address = email.trim().lowercase()

        return lock.withLock {
            val pending = pendingCodes[address] ?: return@withLock CodeVerificationOutcome.Expired
            if (pending.issuedAt.elapsedNow() >= CodeLifetime) {
                pendingCodes.remove(address)
                return@withLock CodeVerificationOutcome.Expired
            }

            if (code == pending.code) {
                pendingCodes.remove(address)
                val token = tokenGenerator()
                activeTokens += token
                CodeVerificationOutcome.Verified(Session(MockAccounts.viewer.id, address, AccessToken(token)))
            } else {
                val failed = pending.failedAttempts + 1
                if (failed >= MaxAttempts) {
                    pendingCodes.remove(address)
                    CodeVerificationOutcome.TooManyAttempts
                } else {
                    pendingCodes[address] = PendingCode(pending.code, pending.issuedAt, failed)
                    CodeVerificationOutcome.WrongCode(attemptsLeft = MaxAttempts - failed)
                }
            }
        }
    }

    override suspend fun revoke(token: AccessToken) {
        simulateNetwork()
        lock.withLock { activeTokens.remove(token.value) }
    }

    /** Whether [token] was issued and not revoked (the mock's view of "this device is signed in"). */
    internal suspend fun isTokenActive(token: AccessToken): Boolean =
        lock.withLock { token.value in activeTokens }

    private fun isTrusted(address: String): Boolean {
        if (address.count { it == '@' } != 1) return false
        val domain = address.substringAfter('@')
        return domain.isNotEmpty() && trustedDomains.any { domain == it || domain.endsWith(".$it") }
    }

    private suspend fun simulateNetwork() {
        if (latencyMillis > 0) delay(latencyMillis)
    }

    companion object {
        /** How long a code can be used. */
        val CodeLifetime: Duration = 10.minutes

        /** The wait before the next code may be requested. */
        val ResendInterval: Duration = 60.seconds

        /** The sliding window of the e-mail limit. */
        val HourlyWindow: Duration = 1.hours

        /** E-mails per address per [HourlyWindow]. */
        const val HourlyLimit = 5

        /** Wrong codes before the code is burned. */
        const val MaxAttempts = 5

        /** The code the mock always issues by default. */
        const val MockCode = "123456"

        private const val DefaultLatencyMillis = 600L
    }
}
