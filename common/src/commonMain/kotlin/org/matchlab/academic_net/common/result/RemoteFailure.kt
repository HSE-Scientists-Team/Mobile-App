package org.matchlab.academic_net.common.result

/**
 * Why a call to the server failed, as far as the app needs to tell the cases apart.
 *
 * The gateway throws one of these from every call that fails because of the network or the answer
 * ([NoConnection], [Timeout], [Http], [Malformed]); the data layer decides what a status code means
 * for its feature (a 404 on a profile is "not found", a 409 on a project is a conflict), and the UI
 * picks a text by the case. Cancellation is not a failure and is never wrapped: a
 * `CancellationException` passes through every layer untouched.
 *
 * The message of a failure is a diagnostic for the developer, in English, and is never shown to the
 * user. It does not repeat anything the server sent.
 */
sealed class RemoteFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The server could not be reached: no network, the host does not resolve, the connection broke. */
    class NoConnection(cause: Throwable? = null) : RemoteFailure("No connection to the server", cause)

    /** The server was reached but did not answer within the time allowed. */
    class Timeout(cause: Throwable? = null) : RemoteFailure("The server did not answer in time", cause)

    /**
     * The server answered with a status other than 2xx.
     *
     * [serverMessage] is the `{"error": ...}` text of the body, for diagnostics only: it is never shown
     * to the user and is deliberately not part of [message]. It is `null` when the body had none.
     */
    class Http(val status: Int, val serverMessage: String? = null) : RemoteFailure("HTTP $status")

    /** The server answered with 2xx, but the body is not what the API promises (not JSON, a field is missing). */
    class Malformed(cause: Throwable? = null) : RemoteFailure("Unexpected answer from the server", cause)
}
