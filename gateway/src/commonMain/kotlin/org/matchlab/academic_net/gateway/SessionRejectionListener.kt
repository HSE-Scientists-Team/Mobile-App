package org.matchlab.academic_net.gateway

/**
 * Told when the server refuses the token of a session: a 401 on a call made for that session.
 *
 * It is the way the rest of the app learns that the user has to sign in again, whichever screen made
 * the call. [Gateway.openSession] takes one; the repository that owns the stored session implements it
 * (it compares the token with the current session's and signs out only if they match, because the answer
 * may come late, after the user has already signed in as someone else).
 *
 * The listener is called once for every call that got the 401, while that call is still running, before
 * the call throws `RemoteFailure.Http(401)`. It runs even if the caller of the call was cancelled in the
 * meantime. It is not called for the 401 of `AccountApi.login` (a wrong password), nor for the explicit
 * `AccountApi.currentSession` and `AccountApi.revokeSession`, whose 401 the repository handles itself.
 * If it throws, the exception is attached to the failure as a suppressed one and the call still ends
 * with the 401.
 */
fun interface SessionRejectionListener {
    /** The server did not accept [accessToken], the token the session was opened with. */
    suspend fun onSessionRejected(accessToken: String)
}
