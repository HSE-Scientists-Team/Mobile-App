package org.matchlab.academic_net.feature.auth.data.mock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.matchlab.academic_net.feature.auth.data.AuthDataSource
import org.matchlab.academic_net.feature.auth.data.ConfirmReply
import org.matchlab.academic_net.feature.auth.data.RegisterReply
import org.matchlab.academic_net.feature.auth.data.SessionCheck
import org.matchlab.academic_net.feature.auth.data.SignInReply
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Credentials
import org.matchlab.academic_net.feature.auth.domain.Password
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.gateway.contract.ConfirmationLinks
import org.matchlab.academic_net.gateway.contract.UnverifiedContract
import org.matchlab.academic_net.mockserver.MockAccounts
import org.matchlab.academic_net.mockserver.MockTrustedDomains
import org.matchlab.academic_net.utils.UuidIdGenerator

/**
 * In-memory stand-in for the account server: answers after [latencyMillis], like a network call.
 *
 * It plays the server, and its rules are the contract for the real backend (`docs/openapi.yaml`):
 *  - the address is normalized (trimmed, lower case); its domain is the part after `@`;
 *  - the domain is trusted if it is in [trustedDomains] or is a subdomain of one
 *    (`student.hse.ru` under `hse.ru`); the registry is kept by the administration panel and never
 *    sent to the app; an untrusted domain cannot register (403);
 *  - an address that has an account cannot register again (409);
 *  - registering stores a request with the address, the password and the time; registering the same
 *    address again replaces the password and the time; there is no account until the link is opened;
 *  - the letter always carries the token [MockLetterToken] (a link that ends with it or has it in
 *    `?token=` is taken as well), and it confirms the last request that was made, the only one the mock
 *    keeps: the account is created with the password of that request. A request older than
 *    [LinkLifetime] answers 410; a token that is not the letter's, or when no request waits for it (it
 *    was used), answers 404;
 *  - signing in with an address that has no account or with a wrong password answers 401, also for
 *    an address whose request is not confirmed yet (as the real server does);
 *  - every sign-in lands on the [MockAccounts.viewer] and gets its own opaque token;
 *  - the mock does not remember the tokens of an earlier launch, so it accepts any token that is not
 *    blank, except those revoked in this run of the app.
 *
 * The mock never answers [SignInReply.Forbidden], [ConfirmReply.AlreadyConfirmed] or
 * [ConfirmReply.DomainNotAllowed]: its registry does not change and its accounts cannot be disabled.
 *
 * Time comes from [timeSource] (tests pass `TestTimeSource`), the tokens of the sessions from
 * [tokenGenerator].
 */
class MockAuthDataSource(
    private val latencyMillis: Long = DefaultLatencyMillis,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    trustedDomains: Set<String> = MockTrustedDomains.domains,
    private val tokenGenerator: () -> String = { UuidIdGenerator.newId() },
) : AuthDataSource {
    private val trustedDomains: Set<String> = trustedDomains.mapTo(mutableSetOf()) { it.trim().lowercase() }

    private val lock = Mutex()

    /** The password of each confirmed address. */
    private val accounts = mutableMapOf<String, Password>()

    /** The last request, the one the token of the letter belongs to; `null` once the token is used. */
    private var request: Request? = null

    private val revokedTokens = mutableSetOf<String>()

    private class Request(val address: String, val password: Password, val madeAt: TimeMark)

    override suspend fun register(credentials: Credentials): RegisterReply {
        simulateNetwork()
        val address = normalize(credentials.email)
        if (!isTrusted(address)) return RegisterReply.DomainNotAllowed

        return lock.withLock {
            if (address in accounts) return@withLock RegisterReply.AlreadyRegistered

            request = Request(address, credentials.password, timeSource.markNow())
            RegisterReply.LetterSent
        }
    }

    // The mock reads the text like the Gateway does, so that the same links work in both. Text with no
    // token is not sent anywhere, so it is not waited for either.
    @OptIn(UnverifiedContract::class)
    override suspend fun confirmEmail(linkOrToken: String): ConfirmReply {
        val token = ConfirmationLinks.extractToken(linkOrToken) ?: return ConfirmReply.InvalidLink
        simulateNetwork()
        if (token != MockLetterToken) return ConfirmReply.InvalidLink

        return lock.withLock {
            val waiting = request ?: return@withLock ConfirmReply.InvalidLink
            if (waiting.madeAt.elapsedNow() >= LinkLifetime) return@withLock ConfirmReply.Expired

            // The request is deleted and the token with it: the second try with the same letter finds nothing.
            request = null
            accounts[waiting.address] = waiting.password
            ConfirmReply.Confirmed
        }
    }

    override suspend fun signIn(credentials: Credentials): SignInReply {
        simulateNetwork()
        val address = normalize(credentials.email)

        return lock.withLock {
            if (accounts[address] != credentials.password) return@withLock SignInReply.Unauthorized

            SignInReply.Success(Session(MockAccounts.viewer.id, address, AccessToken(tokenGenerator())))
        }
    }

    override suspend fun checkSession(token: AccessToken): SessionCheck {
        simulateNetwork()
        val accepted = lock.withLock { token.value.isNotBlank() && token.value !in revokedTokens }
        return if (accepted) SessionCheck.Valid(MockAccounts.viewer.id) else SessionCheck.Rejected
    }

    override suspend fun revoke(token: AccessToken) {
        simulateNetwork()
        lock.withLock { revokedTokens += token.value }
    }

    private fun normalize(email: String): String = email.trim().lowercase()

    private fun isTrusted(address: String): Boolean {
        if (address.count { it == '@' } != 1) return false
        val domain = address.substringAfter('@')
        return domain.isNotEmpty() && trustedDomains.any { domain == it || domain.endsWith(".$it") }
    }

    private suspend fun simulateNetwork() {
        if (latencyMillis > 0) delay(latencyMillis)
    }

    companion object {
        /** The token every letter of the mock carries; paste it (or a link with it) to confirm the address. */
        const val MockLetterToken = "123456"

        /** How long the link of a letter works. */
        val LinkLifetime: Duration = 30.minutes

        private const val DefaultLatencyMillis = 600L
    }
}
