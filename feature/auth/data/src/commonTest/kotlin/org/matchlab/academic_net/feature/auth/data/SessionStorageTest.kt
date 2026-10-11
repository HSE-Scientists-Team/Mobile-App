package org.matchlab.academic_net.feature.auth.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.securestore.InMemorySecureStore
import org.matchlab.academic_net.securestore.SecureStore

private val Saved = Session(UserId("user-me"), "student@hse.ru", AccessToken("secret"))

class SessionStorageTest {
    private val store = InMemorySecureStore()
    private val storage = SessionStorage(store)

    @Test
    fun emptyStoreHasNoSession() {
        assertNull(storage.load())
    }

    @Test
    fun sessionRoundTrips() {
        storage.save(Saved)

        assertEquals(Saved, storage.load())
        assertEquals("secret", store.read("session.access_token"))
    }

    @Test
    fun clearErasesTheSession() {
        storage.save(Saved)

        storage.clear()

        assertNull(storage.load())
        assertNull(store.read("session.user_id"))
        assertNull(store.read("session.email"))
        assertNull(store.read("session.access_token"))
    }

    @Test
    fun partialRecordIsNoSessionAndIsErased() {
        store.write("session.user_id", "user-me")
        store.write("session.email", "student@hse.ru")

        assertNull(storage.load())
        assertNull(store.read("session.user_id"))
        assertNull(store.read("session.email"))
    }

    @Test
    fun blankEntryIsNoSession() {
        storage.save(Saved)
        store.write("session.access_token", "  ")

        assertNull(storage.load())
        assertNull(store.read("session.user_id"))
    }

    @Test
    fun clearTriesEveryEntryAndRethrowsTheFirstFailure() {
        val inner = InMemorySecureStore()
        inner.write("session.user_id", "user-me")
        inner.write("session.email", "student@hse.ru")
        inner.write("session.access_token", "secret")
        val failing = object : SecureStore by inner {
            override fun remove(key: String) {
                if (key == "session.user_id") error("cannot remove")
                inner.remove(key)
            }
        }

        assertFailsWith<IllegalStateException> { SessionStorage(failing).clear() }

        assertNull(inner.read("session.email"))
        assertNull(inner.read("session.access_token"))
    }

    @Test
    fun wipeMakesTheWholeStoreEmpty() {
        storage.save(Saved)

        storage.wipe()

        assertNull(storage.load())
        assertNull(store.read("session.access_token"))
    }

    @Test
    fun wipeFailureIsRethrown() {
        val failing = object : SecureStore by store {
            override fun wipe() = error("cannot wipe")
        }

        assertFailsWith<IllegalStateException> { SessionStorage(failing).wipe() }
    }
}
