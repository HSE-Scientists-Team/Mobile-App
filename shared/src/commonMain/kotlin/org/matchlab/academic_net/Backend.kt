package org.matchlab.academic_net

import org.matchlab.academic_net.gateway.GatewayConfig

/**
 * Where the app gets its data from: the server of the MatchLab Gateway.
 *
 * The platform entry points choose the address and hand it to [App]: the Android build from the Gradle property
 * `matchlab.gatewayUrl`, the iOS app always the production server. It is fixed for the whole process: the object graph
 * is built once for it. Everything but the chats (the API has none, API-GAP G50) goes to the server, the sign-in
 * included; there is no in-memory mode that plays the server.
 */
sealed interface Backend {
    /**
     * The MatchLab Gateway.
     *
     * @property baseUrl address of the server without the API prefix (`https://api.matchlab.space`,
     * `http://10.0.2.2:8080` for a Docker Compose on the host of an Android emulator); `null` or blank is the
     * production server ([GatewayConfig.PRODUCTION_BASE_URL]; API-GAP G04: the spec does not name the address of the
     * working environment, this one comes from the user). Anything else must start with `http://` or `https://`;
     * a value that does not fails here, at once, and not at the first sign-in.
     * @property logCalls debug builds only: print the method, the URL and the status of every call (never bodies, the
     * `Authorization` header is masked).
     */
    data class Gateway(val baseUrl: String? = null, val logCalls: Boolean = false) : Backend {
        init {
            toConfig()
        }
    }
}

/** The settings of the Gateway client for this backend. */
internal fun Backend.toConfig(): GatewayConfig = when (this) {
    is Backend.Gateway ->
        GatewayConfig(baseUrl = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: GatewayConfig.PRODUCTION_BASE_URL, logCalls = logCalls)
}
