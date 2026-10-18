package org.matchlab.academic_net.gateway.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import org.matchlab.academic_net.gateway.GatewayConfig
import kotlin.time.Duration

/** Every path of the API starts with it; the address of the server comes before it. */
internal const val ApiPrefix = "/api/v1"

/** Where the calls go: the server of [GatewayConfig.baseUrl] and the API prefix, without a trailing slash. */
internal fun apiBaseOf(config: GatewayConfig): String = config.baseUrl.trimEnd('/') + ApiPrefix

/**
 * The value for Ktor's timeouts: whole milliseconds, at least one (Ktor rejects zero, which would mean "no
 * timeout" to the engines). The engines take at most `Int.MAX_VALUE` milliseconds, about 24 days; a longer
 * [Duration] is as good as an infinite one, and both become Ktor's own marker for "no timeout".
 */
internal fun Duration.toTimeoutMillis(): Long {
    val millis = inWholeMilliseconds
    if (isInfinite() || millis > Int.MAX_VALUE) return HttpTimeoutConfig.INFINITE_TIMEOUT_MS
    return millis.coerceAtLeast(1)
}

/**
 * The client every call of a [org.matchlab.academic_net.gateway.Gateway] is made with, session or not; the
 * client of a session is derived from it with `HttpClient.config`, so it inherits all of this.
 *
 * The [engine] is given as an instance, so closing a client never closes it: the engine lives as long as
 * the Gateway, the clients may come and go. Ktor must not throw for a status itself (`expectSuccess` off):
 * [ApiRequests] reads the body of an error first. There is no body conversion plugin on purpose, see
 * [ApiRequests].
 *
 * The only logging that can be switched on is [LogLevel.INFO]: the method, the URL and the status of a call.
 * `Authorization` is masked as well, in case a later change raises the level.
 */
internal fun createHttpClient(config: GatewayConfig, engine: HttpClientEngine, callLogger: Logger): HttpClient =
    HttpClient(engine) {
        expectSuccess = false

        install(HttpTimeout) {
            connectTimeoutMillis = config.connectTimeout.toTimeoutMillis()
            requestTimeoutMillis = config.requestTimeout.toTimeoutMillis()
            // The longest silence between two pieces of one answer cannot be longer than the whole call.
            socketTimeoutMillis = config.requestTimeout.toTimeoutMillis()
        }

        if (config.logCalls) {
            install(Logging) {
                logger = callLogger
                level = LogLevel.INFO
                sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
            }
        }
    }
