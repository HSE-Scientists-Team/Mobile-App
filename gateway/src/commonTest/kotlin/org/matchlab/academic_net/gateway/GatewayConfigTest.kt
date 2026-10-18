package org.matchlab.academic_net.gateway

import io.ktor.client.plugins.HttpTimeoutConfig
import org.matchlab.academic_net.gateway.internal.ApiPrefix
import org.matchlab.academic_net.gateway.internal.apiBaseOf
import org.matchlab.academic_net.gateway.internal.toTimeoutMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GatewayConfigTest {
    @Test
    fun defaultsAreTheProductionServerWithTheAgreedTimeouts() {
        val config = GatewayConfig()

        assertEquals("https://api.matchlab.space", config.baseUrl)
        assertEquals(10.seconds, config.connectTimeout)
        assertEquals(20.seconds, config.requestTimeout)
        assertFalse(config.logCalls)
    }

    @Test
    fun theBaseUrlMayBeHttpForALocalServer() {
        assertEquals("http://10.0.2.2:8080", GatewayConfig(baseUrl = "http://10.0.2.2:8080").baseUrl)
        assertEquals("HTTP://LOCALHOST:8080", GatewayConfig(baseUrl = "HTTP://LOCALHOST:8080").baseUrl)
    }

    @Test
    fun aBaseUrlWithoutHttpOrHttpsIsRejected() {
        assertFailsWith<IllegalArgumentException> { GatewayConfig(baseUrl = "") }
        assertFailsWith<IllegalArgumentException> { GatewayConfig(baseUrl = "api.matchlab.space") }
        assertFailsWith<IllegalArgumentException> { GatewayConfig(baseUrl = "ftp://api.matchlab.space") }
        assertFailsWith<IllegalArgumentException> { GatewayConfig(baseUrl = "/api/v1") }
    }

    @Test
    fun timeoutsMustBePositive() {
        assertFailsWith<IllegalArgumentException> { GatewayConfig(connectTimeout = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { GatewayConfig(connectTimeout = (-1).seconds) }
        assertFailsWith<IllegalArgumentException> { GatewayConfig(requestTimeout = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { GatewayConfig(requestTimeout = (-5).seconds) }
    }

    @Test
    fun anInfiniteTimeoutIsAllowed() {
        assertEquals(Duration.INFINITE, GatewayConfig(requestTimeout = Duration.INFINITE).requestTimeout)
    }

    @Test
    fun aConfigIsAValueSoItCanBeCopiedAndCompared() {
        val debug = GatewayConfig().copy(logCalls = true)

        assertEquals(GatewayConfig(logCalls = true), debug)
        assertEquals("https://api.matchlab.space", debug.baseUrl)
    }

    @Test
    fun theApiIsReachedUnderItsVersionedPrefix() {
        assertEquals("/api/v1", ApiPrefix)
        assertEquals("https://api.matchlab.space/api/v1", apiBaseOf(GatewayConfig()))
        assertEquals("http://10.0.2.2:8080/api/v1", apiBaseOf(GatewayConfig(baseUrl = "http://10.0.2.2:8080/")))
    }

    @Test
    fun timeoutsAreConvertedToWholeMillisecondsOfAtLeastOne() {
        assertEquals(20_000L, 20.seconds.toTimeoutMillis())
        assertEquals(1_500L, 1500.milliseconds.toTimeoutMillis())
        // Ktor rejects a timeout of 0, and the engines would read it as "none".
        assertEquals(1L, 100.microseconds.toTimeoutMillis())
        assertEquals(HttpTimeoutConfig.INFINITE_TIMEOUT_MS, Duration.INFINITE.toTimeoutMillis())
    }

    @Test
    fun aTimeoutTheEnginesCannotHoldIsNoTimeout() {
        // OkHttp refuses more than Int.MAX_VALUE milliseconds (about 24 days).
        assertEquals(Int.MAX_VALUE.toLong(), Int.MAX_VALUE.milliseconds.toTimeoutMillis())
        assertEquals(HttpTimeoutConfig.INFINITE_TIMEOUT_MS, (Int.MAX_VALUE.toLong() + 1).milliseconds.toTimeoutMillis())
        assertEquals(HttpTimeoutConfig.INFINITE_TIMEOUT_MS, 365.days.toTimeoutMillis())
    }

    @Test
    fun aSubMillisecondTimeoutStillBuildsAGateway() {
        val config = GatewayConfig(connectTimeout = 100.microseconds, requestTimeout = 900.microseconds)

        // Building the client must not throw for a value Ktor would reject if passed on unchanged.
        GatewayFixture(config) { json("{}") }
    }
}
