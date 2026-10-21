package org.matchlab.academic_net.feature.auth.data

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.AuthRepository
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.ConfirmationOutcome
import org.matchlab.academic_net.feature.auth.domain.Credentials
import org.matchlab.academic_net.feature.auth.domain.Password
import org.matchlab.academic_net.feature.auth.domain.PendingRegistration
import org.matchlab.academic_net.feature.auth.domain.RegistrationOutcome
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.auth.domain.SignInOutcome
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * [AuthRepository] over the [AuthDataSource] (the server) and the [SessionStorage] (the device).
 *
 * The state of the session is kept here, in memory: the saved session is read once, in
 * [restoreSession]. [io] runs the blocking secure-store calls; [timeSource] counts down the pause
 * before the letter of a registration may be sent again ([resendInterval]; a test passes
 * `TestTimeSource`); [revokeTimeout] is the longest a sign-out waits for the server, and
 * [verifyTimeout] the longest [expireSession] waits for the answer to its question.
 *
 * **The sign-in is not lost by accident.** The saved session makes the user signed in at the start
 * before the network is asked anything; only the server's explicit refusal of the token ends the
 * session, and a 401 on a call is a reason to ask, not to sign out (see [expireSession]). No
 * failure of the network, of the server or of the store (a store that cannot be read is not a store
 * that holds nothing, and nothing is erased because of it) ends it. When the session does end, the
 * address of the user is kept for the sign-in screen.
 *
 * **Registrations.** A registration that got its letter is remembered with its password, in memory
 * only, until the user is signed in (by any means), signs out or loses the session. The password
 * lets the app sign in by itself after the link is confirmed. The registration and its password go
 * together, so every [PendingRegistration] this repository returns has `hasPassword = true`.
 *
 * **Changes of the session** (a sign-in, an expiry, a sign-out) are made one at a time, under a
 * lock, and cannot be cancelled once the server's word is in: a 401 may arrive from a late request
 * of an old session at the very moment the user signs in or out, and the state must not end up
 * half-way between the two. The network is never called under the lock.
 *
 * If the session cannot be written to the store (after a few tries) the user is still signed in,
 * until the process ends: failing a sign-in the server accepted would be worse.
 *
 * A token left on the disk by a sign-out would sign the user in at the next launch, so the
 * sign-out retries the erase and, if that still fails, wipes the whole store (see [signOut]).
 */
class AuthRepositoryImpl(
    private val source: AuthDataSource,
    private val storage: SessionStorage,
    private val io: CoroutineDispatcher,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val resendInterval: Duration = 60.seconds,
    private val revokeTimeout: Duration = 5.seconds,
    private val verifyTimeout: Duration = 5.seconds,
) : AuthRepository {
    private val state = MutableStateFlow<AuthState>(AuthState.Restoring)
    override val authState: StateFlow<AuthState> = state.asStateFlow()

    /** The registrations whose letter was sent in this run of the app, by address. */
    private val registrations = MutableStateFlow<Map<String, SentRegistration>>(emptyMap())

    private class SentRegistration(val password: Password, val sentAt: TimeMark)

    /** Held while the session is signed in, expired or signed out, together with the saved record. */
    private val sessionChanges = Mutex()

    /** The question about a token that is being asked now, so that parallel 401s share it. */
    private class Verification(val token: AccessToken, val refused: CompletableDeferred<Boolean>)

    private val verificationLock = Mutex()
    private var verification: Verification? = null

    override suspend fun restoreSession() {
        if (state.value !is AuthState.Restoring) return

        // An unreadable store is "no session", not a crash on every launch and not a reason to erase
        // anything. If a sign-out takes the state while the store is read (and could not erase the record), the
        // compare below fails and what was read is dropped; after it the check above ends the call at once.
        val stored = withContext(io) {
            StoredState(
                runCatching { storage.load() }.getOrNull(),
                runCatching { storage.loadLastEmail() }.getOrNull(),
            )
        }
        // A sign-in that finished meanwhile wins over the restored session, and then the saved
        // one is not checked: it is not the session of this process any more.
        val restored = stored.session?.let { AuthState.SignedIn(it) }
            ?: AuthState.SignedOut(lastEmail = stored.lastEmail)
        if (!state.compareAndSet(AuthState.Restoring, restored)) return

        if (stored.session != null) verifyRestored(stored.session)
    }

    private class StoredState(val session: Session?, val lastEmail: String?)

    /**
     * Asks the server about the session that was signed in from the store, without having waited
     * for it. Only an explicit refusal (the token is not valid, or it belongs to somebody else) ends
     * the session; no answer (no network, a timeout, a server error) leaves it, as the app must
     * start offline.
     */
    private suspend fun verifyRestored(session: Session) {
        val check = suspendRunCatching { source.checkSession(session.accessToken) }.getOrNull() ?: return
        if (!check.confirms(session)) endSession(session.accessToken)
    }

    override suspend fun register(credentials: Credentials): RegistrationOutcome =
        when (source.register(credentials)) {
            RegisterReply.LetterSent -> {
                // The answer is in: the registration is remembered even if the caller is cancelled right now.
                withContext(NonCancellable) {
                    // The same address again: the new password replaces the old one and the pause starts over.
                    val sent = SentRegistration(credentials.password, timeSource.markNow())
                    registrations.update { it + (credentials.email to sent) }
                    // For the sign-in screen of the next launch, if the process is killed before the link is opened.
                    withContext(io) { runCatching { storage.saveLastEmail(credentials.email) } }
                }
                val pending = PendingRegistration(credentials.email, resendInterval, hasPassword = true)
                RegistrationOutcome.LetterSent(pending)
            }
            RegisterReply.DomainNotAllowed -> RegistrationOutcome.DomainNotAllowed
            // A registration that is remembered stays: the address may have been confirmed from a letter
            // meanwhile, and "I've confirmed" signs in with its password.
            RegisterReply.AlreadyRegistered -> RegistrationOutcome.AlreadyRegistered
        }

    override fun pendingRegistration(email: String): PendingRegistration? {
        val sent = registrations.value[email] ?: return null
        val left = (resendInterval - sent.sentAt.elapsedNow()).coerceAtLeast(Duration.ZERO)
        return PendingRegistration(email, left, hasPassword = true)
    }

    override suspend fun resendLetter(email: String): RegistrationOutcome? {
        val sent = registrations.value[email] ?: return null
        return register(Credentials(email, sent.password))
    }

    override suspend fun confirmEmail(email: String, linkOrToken: String): ConfirmationOutcome =
        when (source.confirmEmail(linkOrToken)) {
            ConfirmReply.Confirmed, ConfirmReply.AlreadyConfirmed ->
                ConfirmationOutcome.Confirmed(signInAfterConfirmation(email))
            ConfirmReply.InvalidLink -> ConfirmationOutcome.InvalidLink
            ConfirmReply.Expired -> ConfirmationOutcome.LinkExpired
            ConfirmReply.DomainNotAllowed -> ConfirmationOutcome.DomainNotAllowed
        }

    /**
     * Signs in with the password remembered for [email]; whether the user is in now. The address is
     * confirmed whatever happens here, and the caller must be told so: a refusal or a failure of
     * this sign-in only means that the user signs in by hand. Only a cancellation passes.
     */
    private suspend fun signInAfterConfirmation(email: String): Boolean {
        val sent = registrations.value[email] ?: return false
        val outcome = suspendRunCatching { signIn(Credentials(email, sent.password)) }.getOrNull()
        return outcome == SignInOutcome.SignedIn
    }

    override suspend fun signIn(credentials: Credentials): SignInOutcome =
        signInWith(credentials, whenUnauthorized = SignInOutcome.WrongCredentials)

    override suspend fun signInPending(email: String): SignInOutcome? {
        val sent = registrations.value[email] ?: return null
        // The credentials are the ones that were registered: a 401 can only mean that no account exists yet.
        return signInWith(Credentials(email, sent.password), whenUnauthorized = SignInOutcome.NotConfirmedYet)
    }

    private suspend fun signInWith(credentials: Credentials, whenUnauthorized: SignInOutcome): SignInOutcome =
        when (val reply = source.signIn(credentials)) {
            is SignInReply.Success -> {
                startSession(reply.session)
                SignInOutcome.SignedIn
            }
            SignInReply.Unauthorized -> whenUnauthorized
            SignInReply.Forbidden -> SignInOutcome.AccountUnavailable
        }

    /**
     * Saves [session] and the address, and signs in, even if the caller is cancelled right now: the
     * server has accepted the sign-in. Neither write is needed for the sign-in itself.
     */
    private suspend fun startSession(session: Session) {
        withContext(NonCancellable) {
            sessionChanges.withLock {
                saveSession(session)
                withContext(io) { runCatching { storage.saveLastEmail(session.email) } }
                registrations.value = emptyMap()
                state.value = AuthState.SignedIn(session)
            }
        }
    }

    /**
     * Writes [session] to the store, [SAVE_ATTEMPTS] tries at most: a store that is busy for the moment
     * should not cost the user the sign-in at the next launch. If every try fails the user is still
     * signed in for as long as the process lives.
     */
    private suspend fun saveSession(session: Session) {
        repeat(SAVE_ATTEMPTS) { attempt ->
            val saved = withContext(io) { runCatching { storage.save(session) }.isSuccess }
            if (saved) return
            if (attempt < SAVE_ATTEMPTS - 1) delay(SAVE_RETRY_DELAY)
        }
    }

    override suspend fun expireSession(token: AccessToken) {
        withContext(NonCancellable) {
            // Only the current session can expire: this may be the late answer to a request of an
            // earlier one, after the user has signed out or in as somebody else.
            val session = currentSession(token) ?: return@withContext
            // A 401 on a call is not yet the server's word on the session: the call may have hit a
            // moment when the token was not good enough (a fault on the server's side), and signing the
            // user out for that would lose a good session. The server is asked about the token itself.
            if (serverRefuses(session)) endSession(token)
        }
    }

    /**
     * Whether the server says that it does not accept [session] (or that it belongs to somebody
     * else). It is asked once for every token at a time: calls that come while the question is on
     * its way wait for its answer instead of asking again. No answer within [verifyTimeout], or a
     * failure, is not a refusal.
     */
    private suspend fun serverRefuses(session: Session): Boolean {
        val (asked, isAsking) = verificationLock.withLock {
            val running = verification?.takeIf { it.token == session.accessToken }
            if (running != null) {
                running to false
            } else {
                Verification(session.accessToken, CompletableDeferred()).also { verification = it } to true
            }
        }
        if (!isAsking) return asked.refused.await()

        var refused = false
        try {
            val check = withTimeoutOrNull(verifyTimeout) {
                suspendRunCatching { source.checkSession(session.accessToken) }.getOrNull()
            }
            refused = check != null && !check.confirms(session)
        } finally {
            // Whatever happened, the ones waiting get an answer, and the next 401 asks again.
            withContext(NonCancellable) {
                verificationLock.withLock { if (verification === asked) verification = null }
                asked.refused.complete(refused)
            }
        }
        return refused
    }

    /** Whether the answer of the server to a question about [session] is "yes, it is accepted". */
    private fun SessionCheck.confirms(session: Session): Boolean =
        this is SessionCheck.Valid && userId == session.userId

    /**
     * Ends the session with [token], the one the server has refused: erases it, keeps its address for
     * the sign-in screen and signs out with `sessionExpired`. Does nothing if the session is not the
     * current one any more, or was ended by somebody else already.
     */
    private suspend fun endSession(token: AccessToken) {
        withContext(NonCancellable) {
            sessionChanges.withLock {
                val session = currentSession(token) ?: return@withLock
                eraseSavedSession()
                rememberLastEmail(session.email)
                registrations.value = emptyMap()
                state.value = AuthState.SignedOut(sessionExpired = true, lastEmail = session.email)
            }
        }
    }

    private fun currentSession(token: AccessToken): Session? =
        (state.value as? AuthState.SignedIn)?.session?.takeIf { it.accessToken == token }

    override suspend fun signOut() {
        // Local first: the user is out the moment they ask, whatever the network does.
        withContext(NonCancellable) {
            val token = sessionChanges.withLock {
                val session = (state.value as? AuthState.SignedIn)?.session
                val lastEmail = session?.email ?: (state.value as? AuthState.SignedOut)?.lastEmail
                eraseSavedSession()
                if (session != null) rememberLastEmail(session.email)
                registrations.value = emptyMap()
                state.value = AuthState.SignedOut(lastEmail = lastEmail)
                session?.accessToken
            }
            // Not a reason to keep the user waiting: a token that is not revoked is only a token
            // nobody has any more.
            if (token != null) withTimeoutOrNull(revokeTimeout) { suspendRunCatching { source.revoke(token) } }
        }
    }

    /**
     * Erases the saved session: [ERASE_ATTEMPTS] tries of [SessionStorage.clear], as a failure of
     * the platform store is often temporary. If none succeeded the store is wiped as a whole, which
     * leaves nothing readable even when single entries cannot be removed. If that fails too, nothing
     * more can be done here.
     */
    private suspend fun eraseSavedSession() {
        repeat(ERASE_ATTEMPTS) { attempt ->
            val erased = withContext(io) { runCatching { storage.clear() }.isSuccess }
            if (erased) return
            if (attempt < ERASE_ATTEMPTS - 1) delay(ERASE_RETRY_DELAY)
        }
        withContext(io) { runCatching { storage.wipe() } }
    }

    /**
     * Saves [email] as the last address after the session was erased: a wipe takes it too, and the
     * sign-in screen should still have it. A store that cannot write it only costs the user the typing.
     */
    private suspend fun rememberLastEmail(email: String) {
        withContext(io) { runCatching { storage.saveLastEmail(email) } }
    }

    private companion object {
        const val SAVE_ATTEMPTS = 3
        val SAVE_RETRY_DELAY = 100.milliseconds
        const val ERASE_ATTEMPTS = 3
        val ERASE_RETRY_DELAY = 100.milliseconds
    }
}
