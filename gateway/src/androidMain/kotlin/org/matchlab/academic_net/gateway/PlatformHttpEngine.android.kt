package org.matchlab.academic_net.gateway

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * OkHttp; its timeouts are those of [config], and Ktor's `HttpTimeout` sets the same ones for every call. It does not
 * resend a request on its own (see the comment on `retryOnConnectionFailure` below).
 */
actual fun platformHttpEngine(config: GatewayConfig): HttpClientEngine {
    val connectMillis = config.connectTimeout.toOkHttpMillis()
    val requestMillis = config.requestTimeout.toOkHttpMillis()
    return OkHttp.create {
        config {
            connectTimeout(connectMillis, TimeUnit.MILLISECONDS)
            readTimeout(requestMillis, TimeUnit.MILLISECONDS)
            writeTimeout(requestMillis, TimeUnit.MILLISECONDS)
            // By default OkHttp sends a request again, without telling anybody, when its connection turns out to be
            // dead before any answer came (a stale keep-alive connection, a dropped network). For a POST that could
            // run it twice: a work added twice (POST /users/me/achievements), a record sent twice, a second letter of
            // a registration, a second session of a sign-in (API-GAP G48: the API has no idempotency keys). The
            // screens offer "Повторить" for a failed call, so the user decides about a second try.
            retryOnConnectionFailure(false)
        }
    }
}

/** OkHttp takes whole milliseconds below `Int.MAX_VALUE` and reads 0 as "no timeout": the same rule as for Ktor. */
private fun Duration.toOkHttpMillis(): Long {
    val millis = inWholeMilliseconds
    return if (isInfinite() || millis > Int.MAX_VALUE) 0 else millis.coerceAtLeast(1)
}
