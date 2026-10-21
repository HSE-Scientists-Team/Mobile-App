package org.matchlab.academic_net.feature.auth.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.securestore.InMemorySecureStore
import org.matchlab.academic_net.securestore.SecureStore

private val NewKey = "session.v1"
private val PreviousKeys = listOf("session.user_id", "session.email", "session.access_token")

class SessionStorageTest {
    private val store = InMemorySecureStore()
    private val storage = SessionStorage(store)

    // ---- the session: one entry ----

    @Test
    fun emptyStoreHasNoSession() {
        assertNull(storage.load())
    }

    @Test
    fun sessionRoundTrips() {
        storage.save(SavedSession)

        assertEquals(SavedSession, storage.load())
    }

    @Test
    fun sessionIsOneEntryInAVersionedFormat() {
        storage.save(SavedSession)

        assertEquals("v1\nuser-me\nstudent@hse.ru\nsecret", store.read(NewKey))
        for (key in PreviousKeys) assertNull(store.read(key), key)
    }

    @Test
    fun saveIsASingleWrite() {
        val recording = RecordingStore(store)

        SessionStorage(recording).save(SavedSession)

        assertEquals(listOf(NewKey), recording.writes.map { it.first })
    }

    @Test
    fun savingAgainReplacesTheSession() {
        storage.save(SavedSession)
        val other = Session(UserId("user-2"), "other@hse.ru", AccessToken("token-2"))

        storage.save(other)

        assertEquals(other, storage.load())
    }

    @Test
    fun theTokenMayContainAnything() {
        for (token in listOf("a b", "a\nb", "\nstarts-with-a-break", "ends-with-a-break\n", "v1\nx\ny\nz", "токен-🔒")) {
            val session = SavedSession.copy(accessToken = AccessToken(token))

            storage.save(session)

            assertEquals(session, storage.load(), "token '$token'")
        }
    }

    @Test
    fun aLineBreakInTheUserIdOrTheAddressIsNotSaved() {
        assertFailsWith<IllegalArgumentException> { storage.save(SavedSession.copy(userId = UserId("a\nb"))) }
        assertFailsWith<IllegalArgumentException> { storage.save(SavedSession.copy(email = "a\nb@hse.ru")) }

        assertNull(store.read(NewKey))
    }

    @Test
    fun failedWriteIsRethrownAndLeavesTheOldSession() {
        storage.save(SavedSession)
        val failing = object : SecureStore by store {
            override fun write(key: String, value: String) = error("disk is full")
        }

        assertFailsWith<IllegalStateException> { SessionStorage(failing).save(SavedSession.copy(email = "new@hse.ru")) }

        assertEquals(SavedSession, storage.load())
    }

    // ---- erasing ----

    @Test
    fun clearErasesTheSession() {
        storage.save(SavedSession)

        storage.clear()

        assertNull(storage.load())
        assertNull(store.read(NewKey))
    }

    @Test
    fun clearErasesThePreviousFormatToo() {
        store.writePreviousFormat(SavedSession)

        storage.clear()

        for (key in PreviousKeys) assertNull(store.read(key), key)
        assertNull(storage.load())
    }

    @Test
    fun clearLeavesTheLastAddress() {
        storage.save(SavedSession)
        storage.saveLastEmail("student@hse.ru")

        storage.clear()

        assertEquals("student@hse.ru", storage.loadLastEmail())
    }

    @Test
    fun clearTriesEveryEntryAndRethrowsTheFirstFailure() {
        val inner = InMemorySecureStore()
        inner.write(NewKey, "v1\nuser-me\nstudent@hse.ru\nsecret")
        inner.writePreviousFormat(SavedSession)
        val failing = object : SecureStore by inner {
            override fun remove(key: String) {
                if (key == NewKey || key == "session.email") error("cannot remove $key")
                inner.remove(key)
            }
        }

        val failure = assertFailsWith<IllegalStateException> { SessionStorage(failing).clear() }

        assertEquals("cannot remove session.v1", failure.message)
        assertNotNull(inner.read(NewKey))
        assertNotNull(inner.read("session.email"))
        assertNull(inner.read("session.user_id"))
        assertNull(inner.read("session.access_token"))
    }

    @Test
    fun wipeMakesTheWholeStoreEmpty() {
        storage.save(SavedSession)
        storage.saveLastEmail("student@hse.ru")

        storage.wipe()

        assertNull(storage.load())
        assertNull(storage.loadLastEmail())
        assertNull(store.read(NewKey))
    }

    @Test
    fun wipeFailureIsRethrown() {
        val failing = object : SecureStore by store {
            override fun wipe() = error("cannot wipe")
        }

        assertFailsWith<IllegalStateException> { SessionStorage(failing).wipe() }
    }

    // ---- broken entries are erased ----

    @Test
    fun entryOfAnotherVersionIsErased() {
        store.write(NewKey, "v2\nuser-me\nstudent@hse.ru\nsecret")

        assertNull(storage.load())
        assertNull(store.read(NewKey))
    }

    @Test
    fun entryWithMissingFieldsIsErased() {
        val records = listOf(
            "",
            "garbage",
            "v1",
            "v1\nuser-me",
            "v1\nuser-me\nstudent@hse.ru",
            "user-me\nstudent@hse.ru\nsecret",
        )
        for (record in records) {
            store.write(NewKey, record)

            assertNull(storage.load(), "record '$record'")
            assertNull(store.read(NewKey), "record '$record'")
        }
    }

    @Test
    fun entryWithBlankFieldsIsErased() {
        val records = listOf(
            "v1\n\nstudent@hse.ru\nsecret",
            "v1\nuser-me\n  \nsecret",
            "v1\nuser-me\nstudent@hse.ru\n  ",
        )
        for (record in records) {
            store.write(NewKey, record)

            assertNull(storage.load(), "record '$record'")
            assertNull(store.read(NewKey), "record '$record'")
        }
    }

    @Test
    fun brokenNewEntryDoesNotHideAGoodPreviousOne() {
        store.write(NewKey, "v2\nuser-me\nstudent@hse.ru\nsecret")
        store.writePreviousFormat(SavedSession)

        assertEquals(SavedSession, storage.load())
        assertEquals(SavedSession, storage.load())
    }

    @Test
    fun blankEntryOfThePreviousFormatIsErased() {
        for (blankKey in PreviousKeys) {
            store.writePreviousFormat(SavedSession)
            store.write(blankKey, "  ")

            assertNull(storage.load(), blankKey)

            for (key in PreviousKeys) assertNull(store.read(key), "$key after a blank $blankKey")
        }
    }

    // ---- the previous format ----

    @Test
    fun previousFormatIsReadAndMovedToTheNewEntry() {
        store.writePreviousFormat(SavedSession)

        assertEquals(SavedSession, storage.load())

        assertEquals("v1\nuser-me\nstudent@hse.ru\nsecret", store.read(NewKey))
        for (key in PreviousKeys) assertNull(store.read(key), key)
        assertEquals(SavedSession, storage.load())
    }

    @Test
    fun newEntryWinsOverThePreviousOne() {
        val newer = Session(UserId("user-2"), "other@hse.ru", AccessToken("token-2"))
        storage.save(newer)
        store.writePreviousFormat(SavedSession)

        assertEquals(newer, storage.load())
        // The old entries are not touched by a load that did not need them.
        assertEquals("secret", store.read("session.access_token"))
    }

    @Test
    fun previousFormatIsStillGoodIfTheMoveCannotBeWritten() {
        store.writePreviousFormat(SavedSession)
        val readOnly = object : SecureStore by store {
            override fun write(key: String, value: String) = error("disk is full")
        }

        assertEquals(SavedSession, SessionStorage(readOnly).load())

        for (key in PreviousKeys) assertNotNull(store.read(key), key)
        assertNull(store.read(NewKey))
    }

    @Test
    fun previousFormatIsStillGoodIfTheOldEntriesCannotBeErased() {
        store.writePreviousFormat(SavedSession)
        val noErasing = object : SecureStore by store {
            override fun remove(key: String) = error("cannot remove")
        }

        assertEquals(SavedSession, SessionStorage(noErasing).load())

        // The new entry is in place and is read first from now on.
        assertEquals("v1\nuser-me\nstudent@hse.ru\nsecret", store.read(NewKey))
        assertEquals(SavedSession, storage.load())
    }

    @Test
    fun partialPreviousFormatIsNoSessionAndIsNotErased() {
        // The app was killed between writes, or one entry cannot be read now: nothing tells which, so
        // nothing is destroyed.
        store.write("session.user_id", "user-me")
        store.write("session.email", "student@hse.ru")

        assertNull(storage.load())

        assertEquals("user-me", store.read("session.user_id"))
        assertEquals("student@hse.ru", store.read("session.email"))
    }

    // ---- an entry that cannot be read for the moment ----

    @Test
    fun unreadableStoreIsNoSessionAndNothingIsErased() {
        storage.save(SavedSession)
        val locked = UnreadableStore(store)

        assertNull(SessionStorage(locked).load())

        assertEquals(0, locked.removes)
        assertEquals(0, locked.wipes)
        assertNotNull(store.read(NewKey))
        // The keystore is back: the session was never lost.
        assertEquals(SavedSession, storage.load())
    }

    @Test
    fun storeThatIsUnreadableForAWhileGivesTheSessionBackWhenItRecovers() {
        storage.save(SavedSession)
        // However many reads fail, nothing is erased, and the first load that is read in full finds the session.
        for (unreadableReads in 1..6) {
            val locked = UnreadableStore(store, unreadableReads)
            val lockedStorage = SessionStorage(locked)
            var found: Session? = null
            var loads = 0
            while (found == null && loads < 10) {
                found = lockedStorage.load()
                loads++
            }

            assertEquals(SavedSession, found, "unreadable reads $unreadableReads")
            assertEquals(0, locked.removes, "unreadable reads $unreadableReads")
        }
    }

    @Test
    fun anEntryOfThePreviousFormatThatCannotBeReadNowIsNotErased() {
        // The reads of a load: 1 is the new entry, 2 to 4 are the three entries of the previous format.
        for (unreadableRead in 2..4) {
            val inner = InMemorySecureStore().also { it.writePreviousFormat(SavedSession) }
            val flaky = OneUnreadableReadStore(inner, unreadableRead)

            assertNull(SessionStorage(flaky).load(), "unreadable read $unreadableRead")

            assertEquals(0, flaky.removes, "unreadable read $unreadableRead")
            for (key in PreviousKeys) assertNotNull(inner.read(key), key)
            // The entry can be read again: the session is found.
            assertEquals(SavedSession, SessionStorage(flaky).load(), "unreadable read $unreadableRead")
        }
    }

    // ---- the last address ----

    @Test
    fun lastAddressIsNullWhenNeverSaved() {
        assertNull(storage.loadLastEmail())
    }

    @Test
    fun lastAddressRoundTripsAndIsReplaced() {
        storage.saveLastEmail("student@hse.ru")
        assertEquals("student@hse.ru", storage.loadLastEmail())

        storage.saveLastEmail("other@hse.ru")
        assertEquals("other@hse.ru", storage.loadLastEmail())
        assertEquals("other@hse.ru", store.read("session.last_email"))
    }

    @Test
    fun blankLastAddressIsNone() {
        store.write("session.last_email", "  ")

        assertNull(storage.loadLastEmail())
    }

    @Test
    fun lastAddressIsNotTheSessionAndTheSessionIsNotTheAddress() {
        storage.saveLastEmail("student@hse.ru")

        assertNull(storage.load())

        storage.save(SavedSession)
        storage.clear()
        assertEquals("student@hse.ru", storage.loadLastEmail())
    }

    @Test
    fun unreadableLastAddressIsNoneAndIsNotErased() {
        storage.saveLastEmail("student@hse.ru")
        val locked = UnreadableStore(store)

        assertNull(SessionStorage(locked).loadLastEmail())

        assertEquals(0, locked.removes)
        assertEquals("student@hse.ru", storage.loadLastEmail())
    }
}
