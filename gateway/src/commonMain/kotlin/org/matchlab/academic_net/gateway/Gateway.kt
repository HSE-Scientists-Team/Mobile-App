package org.matchlab.academic_net.gateway

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.bearerAuth
import org.matchlab.academic_net.gateway.internal.ApiRequests
import org.matchlab.academic_net.gateway.internal.KtorAccountApi
import org.matchlab.academic_net.gateway.internal.apiBaseOf
import org.matchlab.academic_net.gateway.internal.createHttpClient

/**
 * The client of the MatchLab Gateway API (`docs/openapi.yaml`): one HTTP engine, one JSON configuration, one
 * set of timeouts and one translation of failures for every call.
 *
 * Calls come in two kinds. [account] holds those made without a session: registering, confirming the mail,
 * signing in, and the check and the sign-out that name their token explicitly. [openSession] gives the calls of
 * a signed-in user. The Gateway lives as long as the app; a session lives as long as the user stays signed in.
 *
 * The [engine] is not owned by the Gateway: it is never closed here, so whoever created it decides when its
 * connections go. In the app that is never, in tests it is a `MockEngine`.
 *
 * With [GatewayConfig.logCalls] the method, URL and status of every call are printed (to the console of the
 * platform): never a body, never a token.
 */
class Gateway internal constructor(
    config: GatewayConfig,
    engine: HttpClientEngine,
    callLogger: Logger,
) {
    /** For the app: [engine] is the one of the platform unless a test passes its own. */
    constructor(config: GatewayConfig, engine: HttpClientEngine = platformHttpEngine(config)) :
        this(config, engine, Logger.SIMPLE)

    private val apiBase = apiBaseOf(config)

    // Not for a session: no Authorization of its own. A session derives its own client from it.
    private val http = createHttpClient(config, engine, callLogger)

    /** Calls not made for a session; the session check and the sign-out take their token explicitly. */
    val account: AccountApi = KtorAccountApi(ApiRequests(http, apiBase))

    /**
     * Opens the calls of a signed-in user: [accessToken] goes into every request, and a 401 answer to any of
     * them is reported to [onRejected] (see [SessionRejectionListener]) before the call fails.
     *
     * Each session has a client of its own over the shared engine, so [GatewaySession.close] cancels exactly
     * the requests of this session.
     */
    fun openSession(accessToken: String, onRejected: SessionRejectionListener): GatewaySession {
        require(accessToken.isNotBlank()) { "The access token cannot be blank" }
        val sessionHttp = http.config {
            defaultRequest { bearerAuth(accessToken) }
        }
        val requests = ApiRequests(sessionHttp, apiBase) { onRejected.onSessionRejected(accessToken) }
        // The storage links are signed: the client for them has no token. It is made when the first file moves
        // (most sessions never move one) and is closed with the session.
        val externalHttp = lazy { http.config { } }
        return GatewaySession(sessionHttp, requests, externalHttp)
    }
}
