package org.matchlab.academic_net.gateway

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Where the Gateway is and how long the client waits for it.
 *
 * @property baseUrl address of the server without the API prefix: `https://api.matchlab.space`,
 * `http://10.0.2.2:8080` for a Docker Compose on the host of an Android emulator. A trailing slash is
 * ignored; the client itself adds `/api/v1`.
 * @property connectTimeout how long establishing the connection may take.
 * @property requestTimeout how long a whole call may take, from sending the request to the last byte of the
 * answer. It is also the longest pause between two pieces of data of one call.
 * @property logCalls debug builds only: logs the method, the URL and the status of every call, one block per
 * request and per response. Bodies are never logged (no mode of the client logs them), and the `Authorization`
 * header is masked.
 */
data class GatewayConfig(
    val baseUrl: String = PRODUCTION_BASE_URL,
    val connectTimeout: Duration = 10.seconds,
    val requestTimeout: Duration = 20.seconds,
    val logCalls: Boolean = false,
) {
    init {
        require(baseUrl.startsWith("https://", ignoreCase = true) || baseUrl.startsWith("http://", ignoreCase = true)) {
            "The base URL must start with http:// or https://: $baseUrl"
        }
        require(connectTimeout.isPositive()) { "The connect timeout must be positive: $connectTimeout" }
        require(requestTimeout.isPositive()) { "The request timeout must be positive: $requestTimeout" }
    }

    companion object {
        /** The server the released app talks to. */
        const val PRODUCTION_BASE_URL = "https://api.matchlab.space"
    }
}
