package org.matchlab.academic_net.gateway

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlin.time.DurationUnit

/**
 * NSURLSession through Ktor's Darwin engine. `NSURLSessionConfiguration` has no separate connect timeout:
 * the time without data and the time of the whole resource are both the request timeout of [config].
 */
actual fun platformHttpEngine(config: GatewayConfig): HttpClientEngine {
    val timeout = config.requestTimeout
    val seconds = if (timeout.isInfinite()) Double.MAX_VALUE else timeout.toDouble(DurationUnit.SECONDS)
    return Darwin.create {
        configureSession {
            timeoutIntervalForRequest = seconds
            timeoutIntervalForResource = seconds
        }
    }
}
