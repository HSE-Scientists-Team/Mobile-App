package org.matchlab.academic_net.gateway

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** OkHttp; its timeouts are those of [config], and Ktor's `HttpTimeout` sets the same ones for every call. */
actual fun platformHttpEngine(config: GatewayConfig): HttpClientEngine {
    val connectMillis = config.connectTimeout.toOkHttpMillis()
    val requestMillis = config.requestTimeout.toOkHttpMillis()
    return OkHttp.create {
        config {
            connectTimeout(connectMillis, TimeUnit.MILLISECONDS)
            readTimeout(requestMillis, TimeUnit.MILLISECONDS)
            writeTimeout(requestMillis, TimeUnit.MILLISECONDS)
        }
    }
}

/** OkHttp takes whole milliseconds below `Int.MAX_VALUE` and reads 0 as "no timeout": the same rule as for Ktor. */
private fun Duration.toOkHttpMillis(): Long {
    val millis = inWholeMilliseconds
    return if (isInfinite() || millis > Int.MAX_VALUE) 0 else millis.coerceAtLeast(1)
}
