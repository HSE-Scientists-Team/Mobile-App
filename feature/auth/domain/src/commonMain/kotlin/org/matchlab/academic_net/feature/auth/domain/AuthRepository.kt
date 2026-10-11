package org.matchlab.academic_net.feature.auth.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Sign-in with the e-mail of a university. There is no password: the address is proved with a
 * one-time code, and the same flow registers a user who has no account yet.
 *
 * The addresses passed in are already [EmailRules.normalize]d, the codes are
 * [VerificationCodeRules.sanitize]d; the use cases take care of it. The server calls throw when
 * they fail (no network); the use cases turn it into a result.
 */
interface AuthRepository {
    /** The state of the session; it starts as [AuthState.Restoring] until [restoreSession] is done. */
    val authState: StateFlow<AuthState>

    /** Reads the saved session: [AuthState.Restoring] becomes [AuthState.SignedIn] or [AuthState.SignedOut]. */
    suspend fun restoreSession()

    /** Asks the server to e-mail a new code to [email]; it cancels the previous one. */
    suspend fun requestCode(email: String): CodeRequestOutcome

    /**
     * The code last sent to [email] in this run of the app, with [VerificationChallenge.resendAfter]
     * counted down to now; `null` when none was sent.
     */
    fun lastChallenge(email: String): VerificationChallenge?

    /** Checks [code]; on [CodeVerificationOutcome.Verified] the session is saved and [authState] is signed in. */
    suspend fun verifyCode(email: String, code: String): CodeVerificationOutcome

    /**
     * Signs out. The saved session is erased first and [authState] becomes signed out, then the
     * server is asked to revoke the token; the user is out even if that request fails.
     */
    suspend fun signOut()
}
