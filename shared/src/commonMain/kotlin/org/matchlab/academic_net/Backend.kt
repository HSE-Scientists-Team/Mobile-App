package org.matchlab.academic_net

import org.matchlab.academic_net.gateway.GatewayConfig

/**
 * Where the app gets its data from: the server of the MatchLab Gateway, or the in-memory mock that plays it.
 *
 * The platform entry points choose it and hand it to [App]: the Android build from the Gradle property
 * `matchlab.backend` (`-Pmatchlab.backend=mock`), the iOS app always [Gateway]. It is fixed for the whole process:
 * the object graph is built once for it.
 *
 * What each mode means:
 * - [Mock]: every feature, the sign-in included, runs on the `Mock…DataSource`s. Nothing touches the network and no
 *   Gateway client is created. A new account needs no server: any address of a trusted domain registers, and the
 *   letter's link is `123456`. This is the mode to check the screens in, because with an unreachable server the
 *   Gateway mode shows only error screens.
 * - [Gateway]: the sign-in, the registration and the session check go to the server. The features that are not
 *   connected to it yet (all but the sign-in, in this build) still run on the mocks, as in [Mock]; the chats are
 *   local in both modes (the API has none), but without the demo chats, because their people do not exist on the
 *   server.
 */
sealed interface Backend {
    /** The in-memory mock server. */
    data object Mock : Backend

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
internal fun Backend.Gateway.toConfig(): GatewayConfig =
    GatewayConfig(baseUrl = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: GatewayConfig.PRODUCTION_BASE_URL, logCalls = logCalls)
