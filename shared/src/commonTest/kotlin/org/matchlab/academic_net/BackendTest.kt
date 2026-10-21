package org.matchlab.academic_net

import org.matchlab.academic_net.gateway.GatewayConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Where the Gateway client is pointed: the production server unless another address is given, and only a real address. */
class BackendTest {
    @Test
    fun noAddressMeansTheProductionServer() {
        assertEquals(GatewayConfig.PRODUCTION_BASE_URL, Backend.Gateway().toConfig().baseUrl)
        assertEquals(GatewayConfig.PRODUCTION_BASE_URL, Backend.Gateway(baseUrl = null).toConfig().baseUrl)
    }

    @Test
    fun aBlankAddressIsTheProductionServerToo() {
        for (blank in listOf("", " ", "   ", "\t", "\n  \n")) {
            assertEquals(GatewayConfig.PRODUCTION_BASE_URL, Backend.Gateway(baseUrl = blank).toConfig().baseUrl, "'$blank'")
        }
    }

    @Test
    fun anAddressOfOurOwnIsKept() {
        assertEquals("http://10.0.2.2:8080", Backend.Gateway(baseUrl = "http://10.0.2.2:8080").toConfig().baseUrl)
        assertEquals("https://staging.example.com", Backend.Gateway(baseUrl = "https://staging.example.com").toConfig().baseUrl)
    }

    @Test
    fun spacesAroundAnAddressAreCutOff() {
        assertEquals("http://localhost:8080", Backend.Gateway(baseUrl = "  http://localhost:8080 \n").toConfig().baseUrl)
    }

    @Test
    fun anAddressWithoutTheSchemeIsRefusedAtOnceAndNotAtTheFirstSignIn() {
        assertFailsWith<IllegalArgumentException> { Backend.Gateway(baseUrl = "api.example.com") }
        assertFailsWith<IllegalArgumentException> { Backend.Gateway(baseUrl = "ftp://api.example.com") }
        assertFailsWith<IllegalArgumentException> { Backend.Gateway(baseUrl = "//api.example.com") }
    }

    @Test
    fun theSchemeMayBeInAnyCase() {
        assertEquals("HTTPS://API.EXAMPLE.COM", Backend.Gateway(baseUrl = "HTTPS://API.EXAMPLE.COM").toConfig().baseUrl)
    }

    @Test
    fun theLogOfCallsIsOffByDefaultAndReachesTheConfigWhenAsked() {
        assertFalse(Backend.Gateway().toConfig().logCalls)
        assertTrue(Backend.Gateway(logCalls = true).toConfig().logCalls)
    }

    @Test
    fun theTimeoutsAreTheDefaultsOfTheConfig() {
        val config = Backend.Gateway(baseUrl = "http://localhost:8080").toConfig()

        assertEquals(GatewayConfig().connectTimeout, config.connectTimeout)
        assertEquals(GatewayConfig().requestTimeout, config.requestTimeout)
    }

    @Test
    fun twoBackendsWithTheSameSettingsAreEqual() {
        assertEquals(Backend.Gateway(baseUrl = "http://localhost:8080"), Backend.Gateway(baseUrl = "http://localhost:8080"))
    }
}
