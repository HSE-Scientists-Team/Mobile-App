package org.matchlab.academic_net.mockserver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockAccountsTest {
    @Test
    fun viewerIsCalledYouAndVerified() {
        assertEquals("Вы", MockAccounts.viewer.name)
        assertTrue(MockAccounts.viewer.isVerified)
    }

    @Test
    fun trustedDomainsAreLowerCaseRegistryNames() {
        assertTrue(MockTrustedDomains.domains.isNotEmpty())
        assertTrue(MockTrustedDomains.domains.all { it == it.lowercase() && '.' in it && '@' !in it })
        assertTrue("hse.ru" in MockTrustedDomains.domains)
    }
}
