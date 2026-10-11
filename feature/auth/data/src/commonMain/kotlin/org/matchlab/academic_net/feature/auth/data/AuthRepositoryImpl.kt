package org.matchlab.academic_net.feature.auth.data

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.matchlab.academic_net.feature.auth.domain.AuthRepository
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.CodeRequestOutcome
import org.matchlab.academic_net.feature.auth.domain.CodeVerificationOutcome
import org.matchlab.academic_net.feature.auth.domain.VerificationChallenge

/**
 * [AuthRepository] over the [AuthDataSource] (the server) and the [SessionStorage] (the device).
 *
 * The state of the session is kept here, in memory: the saved session is only read once, in
 * [restoreSession]. [io] runs the blocking secure-store calls; [timeSource] counts down the
 * time to the next allowed request of a code (a test passes `TestTimeSource`).
 *
 * If the session cannot be written to the store the user is still signed in, until the process
 * ends: failing a sign-in the server accepted would be worse.
 *
 * A token left on the disk by a sign-out would sign the user in at the next launch, so the
 * sign-out retries the erase and, if that still fails, wipes the whole store (see [signOut]).
 */
class AuthRepositoryImpl(
    private val source: AuthDataSource,
    private val storage: SessionStorage,
    private val io: CoroutineDispatcher,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : AuthRepository {
    private val state = MutableStateFlow<AuthState>(AuthState.Restoring)
    override val authState: StateFlow<AuthState> = state.asStateFlow()

    /** The last code sent per address: when, and what the server asked to wait until the next one. */
    private val challenges = MutableStateFlow<Map<String, SentCode>>(emptyMap())

    private class SentCode(val sentAt: TimeMark, val resendAfter: Duration)

    /**
     * Set when a sign-out could not erase the saved session. The record may still be on the
     * disk, so this process must not raise a session from the store again.
     */
    private val erasureFailed = MutableStateFlow(false)

    override suspend fun restoreSession() {
        if (state.value !is AuthState.Restoring) return

        // An unreadable store is "no session", not a crash on every launch; so is a record that a
        // sign-out of this process failed to erase.
        val session = if (erasureFailed.value) {
            null
        } else {
            withContext(io) { runCatching { storage.load() }.getOrNull() }
        }
        // A sign-in that finished meanwhile wins over the restored session.
        state.compareAndSet(
            AuthState.Restoring,
            session?.let { AuthState.SignedIn(it) } ?: AuthState.SignedOut,
        )
    }

    override suspend fun requestCode(email: String): CodeRequestOutcome {
        val outcome = source.requestCode(email)
        if (outcome is CodeRequestOutcome.Sent) {
            val sent = SentCode(timeSource.markNow(), outcome.challenge.resendAfter)
            challenges.update { it + (email to sent) }
        }
        return outcome
    }

    override fun lastChallenge(email: String): VerificationChallenge? {
        val sent = challenges.value[email] ?: return null
        val left = (sent.resendAfter - sent.sentAt.elapsedNow()).coerceAtLeast(Duration.ZERO)
        return VerificationChallenge(email, left)
    }

    override suspend fun verifyCode(email: String, code: String): CodeVerificationOutcome {
        val outcome = source.verifyCode(email, code)
        if (outcome is CodeVerificationOutcome.Verified) {
            // The server has accepted the code: the sign-in is completed even if the caller is
            // cancelled right now.
            withContext(NonCancellable) {
                withContext(io) { runCatching { storage.save(outcome.session) } }
                challenges.update { it - email }
                state.value = AuthState.SignedIn(outcome.session)
            }
        }
        return outcome
    }

    override suspend fun signOut() {
        // Local first: the user is out the moment they ask, whatever the network does.
        withContext(NonCancellable) {
            val token = (state.value as? AuthState.SignedIn)?.session?.accessToken
            eraseSavedSession()
            challenges.value = emptyMap()
            state.value = AuthState.SignedOut
            if (token != null) runCatching { source.revoke(token) }
        }
    }

    /**
     * Erases the saved session: [ERASE_ATTEMPTS] tries of [SessionStorage.clear], as a failure of
     * the platform store is often temporary. If none succeeded the failure is remembered in
     * [erasureFailed] and the store is wiped as a whole, which leaves nothing readable even when
     * single entries cannot be removed. If that fails too, nothing more can be done here.
     */
    private suspend fun eraseSavedSession() {
        repeat(ERASE_ATTEMPTS) { attempt ->
            val erased = withContext(io) { runCatching { storage.clear() }.isSuccess }
            if (erased) return
            if (attempt < ERASE_ATTEMPTS - 1) delay(ERASE_RETRY_DELAY)
        }
        erasureFailed.value = true
        withContext(io) { runCatching { storage.wipe() } }
    }

    private companion object {
        const val ERASE_ATTEMPTS = 3
        val ERASE_RETRY_DELAY = 100.milliseconds
    }
}
