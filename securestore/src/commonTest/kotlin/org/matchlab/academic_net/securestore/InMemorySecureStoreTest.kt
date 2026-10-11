package org.matchlab.academic_net.securestore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemorySecureStoreTest {
    private val store = InMemorySecureStore()

    @Test
    fun valueRoundTrips() {
        store.write("token", "secret")

        assertEquals("secret", store.read("token"))
    }

    @Test
    fun missingKeyReadsAsNull() {
        assertNull(store.read("token"))
    }

    @Test
    fun writeReplacesAndRemoveDeletes() {
        store.write("token", "old")
        store.write("token", "new")
        assertEquals("new", store.read("token"))

        store.remove("token")
        assertNull(store.read("token"))
    }

    @Test
    fun storesDoNotShareValues() {
        store.write("token", "secret")

        assertNull(InMemorySecureStore().read("token"))
    }

    @Test
    fun wipeErasesEveryKeyAndTheStoreKeepsWorking() {
        store.write("a", "1")
        store.write("b", "2")

        store.wipe()

        assertNull(store.read("a"))
        assertNull(store.read("b"))
        store.write("a", "3")
        assertEquals("3", store.read("a"))
    }
}
