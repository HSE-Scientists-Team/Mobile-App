package org.matchlab.academic_net.securestore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reversible, obviously not plain: "abc" is stored as "enc:cba". Text that does not carry the
 * prefix of the current key is corrupted; [temporaryFailure] simulates a busy keystore.
 */
private class ReversingCipher : StringCipher {
    var temporaryFailure: Exception? = null
    var destroyed = 0
        private set
    private var prefix = "enc:"

    override fun encrypt(plain: String): String = prefix + plain.reversed()

    override fun decrypt(encoded: String): String {
        temporaryFailure?.let { throw it }
        if (!encoded.startsWith(prefix)) throw CorruptedSecretException("not produced by this cipher")
        return encoded.removePrefix(prefix).reversed()
    }

    /** A new key: what was encrypted before has another prefix and cannot be decrypted. */
    override fun destroyKey() {
        destroyed++
        prefix = "enc$destroyed:"
    }
}

private class MapStorage : KeyValueStorage {
    val data = mutableMapOf<String, String>()
    var failOnRemove = false

    override fun get(key: String): String? = data[key]

    override fun put(key: String, value: String) {
        data[key] = value
    }

    override fun remove(key: String) {
        if (failOnRemove) error("storage is read-only")
        data.remove(key)
    }
}

class CipherSecureStoreTest {
    private val storage = MapStorage()
    private val cipher = ReversingCipher()
    private val store = CipherSecureStore(storage, cipher)

    @Test
    fun valueRoundTrips() {
        store.write("token", "secret-123")

        assertEquals("secret-123", store.read("token"))
    }

    @Test
    fun storageHoldsOnlyCiphertext() {
        store.write("token", "secret-123")

        val stored = storage.data.getValue("token")
        assertFalse("secret-123" in stored)
        assertEquals("enc:321-terces", stored)
    }

    @Test
    fun missingKeyReadsAsNull() {
        assertNull(store.read("token"))
    }

    @Test
    fun writeReplacesThePreviousValue() {
        store.write("token", "old")
        store.write("token", "new")

        assertEquals("new", store.read("token"))
    }

    @Test
    fun keysAreIndependent() {
        store.write("a", "1")
        store.write("b", "2")
        store.remove("a")

        assertNull(store.read("a"))
        assertEquals("2", store.read("b"))
    }

    @Test
    fun removeOfMissingKeyIsNoOp() {
        store.remove("token")

        assertNull(store.read("token"))
    }

    @Test
    fun corruptedValueIsRemovedAndReadAsNull() {
        // Written by something else: a restored backup, a lost key.
        storage.put("token", "plain text, not ciphertext")

        assertNull(store.read("token"))
        assertFalse("token" in storage.data)
    }

    @Test
    fun corruptedValueStillReadsAsNullWhenItCannotBeRemoved() {
        storage.put("token", "garbage")
        storage.failOnRemove = true

        assertNull(store.read("token"))
        assertTrue("token" in storage.data)
    }

    @Test
    fun temporaryFailureReadsAsNullAndKeepsTheValue() {
        store.write("token", "secret-123")
        cipher.temporaryFailure = IllegalStateException("keystore daemon is busy")

        assertNull(store.read("token"))
        assertEquals("enc:321-terces", storage.data["token"])

        // The failure passes: the same value is readable again.
        cipher.temporaryFailure = null
        assertEquals("secret-123", store.read("token"))
    }

    @Test
    fun everyKindOfTemporaryFailureKeepsTheValue() {
        storage.put("token", "enc:321-terces")
        val failures = listOf(
            RuntimeException("provider failed"),
            Exception("keystore is not loaded"),
            UnsupportedOperationException("not now"),
        )

        for (failure in failures) {
            cipher.temporaryFailure = failure
            assertNull(store.read("token"))
            assertTrue("token" in storage.data, "removed after $failure")
        }
    }

    @Test
    fun corruptedValueDoesNotAffectOtherKeys() {
        store.write("good", "1")
        storage.put("bad", "garbage")

        assertNull(store.read("bad"))
        assertEquals("1", store.read("good"))
    }

    @Test
    fun wipeDestroysTheKeyAndLeavesOnlyUnreadableCiphertext() {
        store.write("a", "1")
        store.write("b", "2")

        store.wipe()

        assertEquals(1, cipher.destroyed)
        assertNull(store.read("a"))
        assertNull(store.read("b"))
    }

    @Test
    fun storeWorksAfterWipe() {
        store.write("token", "old")
        store.wipe()

        store.write("token", "new")

        assertEquals("new", store.read("token"))
    }

    @Test
    fun wipeFailureIsNotSwallowed() {
        val failing = CipherSecureStore(storage, object : StringCipher {
            override fun encrypt(plain: String): String = plain

            override fun decrypt(encoded: String): String = encoded

            override fun destroyKey() = error("keystore is locked")
        })

        assertFailsWith<IllegalStateException> { failing.wipe() }
    }

    @Test
    fun encryptionFailureIsNotSwallowed() {
        val failing = CipherSecureStore(storage, object : StringCipher {
            override fun encrypt(plain: String): String = error("keystore is locked")

            override fun decrypt(encoded: String): String = error("keystore is locked")

            override fun destroyKey() = error("keystore is locked")
        })

        assertFailsWith<IllegalStateException> { failing.write("token", "secret") }
        assertTrue(storage.data.isEmpty())
    }
}
