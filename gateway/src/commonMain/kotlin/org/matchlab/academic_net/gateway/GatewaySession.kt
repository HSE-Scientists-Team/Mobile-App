package org.matchlab.academic_net.gateway

import io.ktor.client.HttpClient
import kotlinx.coroutines.cancel
import org.matchlab.academic_net.gateway.internal.ApiRequests
import kotlin.coroutines.cancellation.CancellationException

/**
 * The calls of one signed-in user. The token the session was opened with goes into every request; a 401
 * answer to any of them is passed to the [SessionRejectionListener] given to [Gateway.openSession].
 *
 * A session is created when the user signs in (or the app finds the stored session) and closed when the user
 * signs out, so that nothing of the old user's requests is still running when the next one signs in. The
 * calls of the user's data (profile, achievements, reviews, people, projects, search) are added here by the
 * blocks that need them, each as a property with its own interface over [requests].
 *
 * Every call throws `RemoteFailure` when it fails and lets a `CancellationException` through, like
 * [AccountApi].
 */
class GatewaySession internal constructor(
    private val http: HttpClient,
    /** Where the calls of this session are made from: the client with the token, and the 401 reporting. */
    internal val requests: ApiRequests,
) : AutoCloseable {

    /**
     * Cancels the requests of this session that are still running (they end with a `CancellationException`)
     * and makes it refuse new ones the same way. The engine and the other sessions are not touched, and
     * [Gateway.account] keeps working: the client of the session is its own, only the engine is shared.
     *
     * Ktor cancels the running requests when the client has finished winding down, a moment after this call
     * returns, so an answer that arrives in that moment may still be delivered to its caller. The owner of the
     * session cancels the coroutines of its callers as well; this is the guarantee that nothing is left in the
     * engine.
     *
     * Calling it again does nothing.
     */
    override fun close() {
        // Cancel before closing: closing a client only lets its running requests finish.
        http.cancel(CancellationException("The session was closed"))
        http.close()
    }
}
