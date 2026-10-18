package org.matchlab.academic_net.feature.auth.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Registration and sign-in with the e-mail of a university and a password.
 *
 * A new user registers (the server sends a letter with a link), opens the link and signs in; the
 * account exists only after the link was opened. There is no way to restore a forgotten password.
 * The password of a registration is kept in memory until the user is signed in, so that the app
 * can sign in by itself once the link is confirmed; it is never saved. What is saved is the
 * session (the user, the address and the token) and the address the user last signed in or
 * registered with.
 *
 * **The session is not lost by accident.** It ends in two ways only: the user signs out
 * ([signOut]), or the server says that it does not accept the token ([restoreSession] and
 * [expireSession] ask it). A restart, a missing network, a timeout, a server error, a store that
 * cannot be read for the moment: none of these ends it.
 *
 * The addresses passed in are already [EmailRules.normalize]d, the passwords are as typed and
 * already checked by [PasswordRules]; the use cases take care of it. The server calls throw when
 * they fail (no network, a server error that has no outcome of its own, see `RemoteFailure`); the
 * use cases turn it into a result. A [kotlinx.coroutines.CancellationException] is never turned
 * into anything.
 */
interface AuthRepository {
    /** The state of the session; it starts as [AuthState.Restoring] until [restoreSession] is done. */
    val authState: StateFlow<AuthState>

    /**
     * Reads the saved session: [AuthState.Restoring] becomes [AuthState.SignedIn] at once if there
     * is one (without waiting for the network), otherwise [AuthState.SignedOut] with the address
     * the user last used. Then, in the same call, the server is asked whether it still accepts the
     * token: only an explicit refusal (the token is not valid, or it belongs to somebody else) ends
     * the session, with `sessionExpired`. If the server cannot be reached or does not answer
     * properly the session stays: the app starts offline, and a 401 to a later call asks the server
     * again, see [expireSession]. A session that cannot be read for the moment is not erased.
     *
     * It does something only once.
     */
    suspend fun restoreSession()

    /**
     * Registers [credentials]: the server stores the registration and sends a letter with a link.
     * On [RegistrationOutcome.LetterSent] the credentials and the time are remembered (in memory
     * only; registering the same address again replaces the remembered password), and the address
     * is saved for the sign-in screen of the next launch.
     */
    suspend fun register(credentials: Credentials): RegistrationOutcome

    /** The registration of [email] whose letter was sent in this run of the app, or `null`. */
    fun pendingRegistration(email: String): PendingRegistration?

    /**
     * Sends the letter of the registration of [email] again; the link of the previous letter stops
     * working. It is a new registration with the remembered password: `null` if there is none (the
     * user has to register again). [RegistrationOutcome.AlreadyRegistered] means that the address
     * was confirmed meanwhile; the registration is kept so that [signInPending] can follow.
     */
    suspend fun resendLetter(email: String): RegistrationOutcome?

    /**
     * Confirms the address with [linkOrToken], the text pasted from the letter (the whole link or its token).
     * [email] picks the remembered password of the registration: when the address is confirmed and
     * there is one, the user is signed in by it, see [ConfirmationOutcome.Confirmed.signedIn]. A failure
     * of that sign-in is not a failure of the confirmation, which has happened.
     */
    suspend fun confirmEmail(email: String, linkOrToken: String): ConfirmationOutcome

    /**
     * Signs in. On [SignInOutcome.SignedIn] the session is saved together with the address,
     * [authState] becomes [AuthState.SignedIn] and the remembered registrations are forgotten.
     */
    suspend fun signIn(credentials: Credentials): SignInOutcome

    /**
     * "I've confirmed": signs in with the remembered password of the registration of [email]; `null`
     * if there is none. The difference from [signIn] is only in the answer: the credentials are
     * right, so a refusal means [SignInOutcome.NotConfirmedYet].
     */
    suspend fun signInPending(email: String): SignInOutcome?

    /**
     * A call of the session was answered with 401 for [token]. A single 401 does not sign the user
     * out: the server is asked about the token (calls made at the same time share one question,
     * which is waited for a few seconds), and only if it refuses the token the saved session is
     * erased and [authState] becomes [AuthState.SignedOut] with `sessionExpired`. If the server
     * accepts the token, or does not answer in time or at all, the session stays.
     *
     * A token that is not the current session's (the answer came late, after the user had signed
     * in again) changes nothing and costs no question.
     */
    suspend fun expireSession(token: AccessToken)

    /**
     * Signs out. The saved session is erased first and [authState] becomes signed out (keeping the
     * address of the session as [AuthState.SignedOut.lastEmail]), then the server is asked to
     * revoke the token for a few seconds; the user is out even if that request fails or hangs.
     */
    suspend fun signOut()
}
