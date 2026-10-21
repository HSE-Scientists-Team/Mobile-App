package org.matchlab.academic_net.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.securestore.SecureStore

/**
 * The key under which the choice lives on the disk of every install, and its two values. They are spelled out here on
 * purpose: a changed word would make every existing install forget the choice of its person.
 */
private const val Key = "analytics.statistics"

/**
 * [StatisticsPreference]: the choice "send usage statistics" of the device, kept in the secure store as "1" / "0".
 *
 * The store calls run on a test dispatcher that shares the clock of the test, and the order of what happens at one moment
 * is the order in which it was started. That is what lets a test put a cancellation between the start of a write and its
 * execution: a coroutine launched after the one that writes runs after its call has been dispatched and before the write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsPreferenceTest {
    private fun TestScope.preference(store: SecureStore) =
        StatisticsPreference(store, StandardTestDispatcher(testScheduler))

    // ---- reading the choice ----

    @Test
    fun thereIsNoChoiceUntilTheStoreHasBeenRead() = runTest {
        val store = ScriptedStore(mapOf(Key to "1"))
        val preference = preference(store)

        assertNull(preference.enabled.value)
        assertEquals(0, store.reads)
    }

    @Test
    fun anEmptyStoreIsNoChoice() = runTest {
        val store = ScriptedStore()
        val preference = preference(store)

        preference.load()

        assertNull(preference.enabled.value)
        assertEquals(1, store.reads)
    }

    @Test
    fun yesAndNoAreReadAsTheyAreStored() = runTest {
        listOf("1" to true, "0" to false).forEach { (stored, expected) ->
            val preference = preference(ScriptedStore(mapOf(Key to stored)))

            preference.load()

            assertEquals(expected, preference.enabled.value, "stored '$stored'")
        }
    }

    @Test
    fun anythingButOneAndZeroIsNoChoice() = runTest {
        listOf("", " ", "2", "10", "01", "-1", " 1", "1 ", "0\n", "true", "false", "yes", "no", "on", "off", "null", "\n")
            .forEach { stored ->
                val preference = preference(ScriptedStore(mapOf(Key to stored)))

                preference.load()

                assertNull(preference.enabled.value, "stored '$stored'")
            }
    }

    @Test
    fun aStoreThatFailsOnReadingIsNoChoiceAndLoadDoesNotThrow() = runTest {
        val store = ScriptedStore(mapOf(Key to "0"))
        store.readFailure = IllegalStateException("the keystore is locked")
        val preference = preference(store)

        preference.load()

        assertNull(preference.enabled.value)
        assertEquals(1, store.reads)
    }

    @Test
    fun theStoreIsReadOnce() = runTest {
        val store = ScriptedStore(mapOf(Key to "1"))
        val preference = preference(store)

        preference.load()
        store.put(Key, "0")
        preference.load()
        preference.load()

        assertEquals(1, store.reads)
        assertEquals(true, preference.enabled.value, "a later load does not read what changed behind its back")
    }

    @Test
    fun loadsThatComeTogetherReadOnceAndEachReturnsOnlyWhenTheChoiceIsThere() = runTest {
        val store = ScriptedStore(mapOf(Key to "0"))
        val preference = preference(store)
        val seenWhenLoadReturned = mutableListOf<Boolean?>()

        val loads = List(3) {
            launch {
                preference.load()
                seenWhenLoadReturned += preference.enabled.value
            }
        }
        runCurrent()

        assertTrue(loads.all { it.isCompleted })
        assertEquals(1, store.reads)
        assertEquals(listOf<Boolean?>(false, false, false), seenWhenLoadReturned)
    }

    @Test
    fun aLoadThatIsCancelledBeforeTheReadBeganReadsNothingAndLeavesTheStoreToBeReadLater() = runTest {
        val store = ScriptedStore(mapOf(Key to "0"))
        val preference = preference(store)

        val loading = launch { preference.load() }
        // Started after `loading` and before its read: the cancellation lands while the read waits for the dispatcher.
        launch { loading.cancel() }
        runCurrent()

        assertTrue(loading.isCancelled)
        assertEquals(0, store.reads)
        assertNull(preference.enabled.value)

        preference.load()

        assertEquals(1, store.reads)
        assertEquals(false, preference.enabled.value)
    }

    // ---- saving the choice ----

    @Test
    fun setSavesYesAndNoUnderTheKeyOfTheAppAndPublishesThem() = runTest {
        val store = ScriptedStore()
        val preference = preference(store)

        preference.set(true)
        assertEquals("1", store.peek(Key))
        assertEquals(true, preference.enabled.value)

        preference.set(false)
        assertEquals("0", store.peek(Key))
        assertEquals(false, preference.enabled.value)

        assertEquals(listOf(Key to "1", Key to "0"), store.writes)
    }

    @Test
    fun theChoiceIsPublishedOnlyAfterTheStoreHasTakenIt() = runTest {
        val store = ScriptedStore()
        val preference = preference(store)
        val seenWhileWriting = mutableListOf<Boolean?>()
        store.onWrite = { _, _ -> seenWhileWriting += preference.enabled.value }

        preference.set(true)

        assertEquals(listOf<Boolean?>(null), seenWhileWriting, "the flow already showed the choice while it was being written")
        assertEquals(true, preference.enabled.value)
    }

    @Test
    fun theFlowShowsEveryChoiceAsItIsMade() = runTest {
        val preference = preference(ScriptedStore(mapOf(Key to "1")))
        val seen = mutableListOf<Boolean?>()
        backgroundScope.launch { preference.enabled.collect { seen += it } }
        runCurrent()

        preference.load()
        runCurrent()
        preference.set(false)
        runCurrent()
        preference.set(true)
        runCurrent()

        assertEquals(listOf<Boolean?>(null, true, false, true), seen)
    }

    @Test
    fun twoSavesAreAppliedInTheOrderTheyCame() = runTest {
        val store = ScriptedStore()
        val preference = preference(store)

        val first = launch { preference.set(true) }
        val second = launch { preference.set(false) }
        runCurrent()

        assertTrue(first.isCompleted && second.isCompleted)
        assertEquals(listOf(Key to "1", Key to "0"), store.writes)
        assertEquals(false, preference.enabled.value)
        assertEquals("0", store.peek(Key))
    }

    // ---- a store that cannot write: a "yes" is not published, a "no" is ----

    @Test
    fun aFailedYesReachesTheCallerAndPublishesNothing() = runTest {
        val store = ScriptedStore()
        store.writeFailure = IllegalStateException("the keystore cannot write")
        val preference = preference(store)

        val failure = assertFailsWith<IllegalStateException> { preference.set(true) }

        assertEquals("the keystore cannot write", failure.message)
        assertNull(preference.enabled.value)
        assertNull(store.peek(Key))
    }

    @Test
    fun aFailedYesLeavesTheChoiceThatWasThere() = runTest {
        val store = ScriptedStore()
        val preference = preference(store)
        preference.set(false)
        store.writeFailure = IllegalStateException("the keystore cannot write")

        assertFailsWith<IllegalStateException> { preference.set(true) }

        assertEquals(false, preference.enabled.value, "a consent that could not be saved must not switch sending on")
        assertEquals("0", store.peek(Key))
    }

    @Test
    fun aFailedYesOverAnUnreadStoreLeavesNoChoiceAtAll() = runTest {
        val store = ScriptedStore(mapOf(Key to "0"))
        store.writeFailure = IllegalStateException("the keystore cannot write")
        val preference = preference(store)

        assertFailsWith<IllegalStateException> { preference.set(true) }

        assertNull(preference.enabled.value)
    }

    @Test
    fun aFailedNoReachesTheCallerAndIsPublishedAllTheSame() = runTest {
        val store = ScriptedStore()
        val failure = IllegalStateException("the keystore cannot write")
        store.writeFailure = failure
        val preference = preference(store)

        val heard = assertFailsWith<IllegalStateException> { preference.set(false) }

        // The coroutines machinery may hand over a copy of the exception (a recovered stack trace) when it crosses the
        // dispatcher of the store, so the failure is told by its kind and its message, not by identity.
        assertEquals(failure.message, heard.message, "the caller hears the failure of the store")
        assertEquals(false, preference.enabled.value, "the refusal holds for this run although it could not be saved")
        assertNull(store.peek(Key), "nothing reached the store")
        assertEquals(emptyList(), store.writes)
    }

    @Test
    fun aFailedNoIsAlreadyPublishedWhenTheCallerHearsOfTheFailure() = runTest {
        val store = ScriptedStore()
        store.writeFailure = IllegalStateException("the keystore cannot write")
        val preference = preference(store)
        var shownWhenTheCallerHeard: Boolean? = null

        try {
            preference.set(false)
        } catch (failure: IllegalStateException) {
            shownWhenTheCallerHeard = preference.enabled.value
        }

        assertEquals(false, shownWhenTheCallerHeard)
    }

    @Test
    fun aFailedNoOverAYesIsPublishedAndTheNoIsTheLastWord() = runTest {
        val store = ScriptedStore()
        val preference = preference(store)
        preference.set(true)
        store.writeFailure = IllegalStateException("the keystore cannot write")

        assertFailsWith<IllegalStateException> { preference.set(false) }

        assertEquals(false, preference.enabled.value)
    }

    @Test
    fun aFailedNoOverANoStaysNoAndStillReachesTheCaller() = runTest {
        val store = ScriptedStore()
        val preference = preference(store)
        preference.set(false)
        store.writeFailure = IllegalStateException("the keystore cannot write")

        assertFailsWith<IllegalStateException> { preference.set(false) }

        assertEquals(false, preference.enabled.value)
        assertEquals("0", store.peek(Key))
    }

    @Test
    fun aNoThatCouldNotBeWrittenIsGoneAtTheNextStartWhenTheStoreHadNoChoice() = runTest {
        val store = ScriptedStore()
        store.writeFailure = IllegalStateException("the keystore cannot write")
        assertFailsWith<IllegalStateException> { preference(store).set(false) }

        store.writeFailure = null
        val next = preference(store)
        next.load()

        assertNull(next.enabled.value, "the store has no choice, so the default applies again")
    }

    @Test
    fun aFailedNoRemovesTheOlderYesSoThatItCannotComeBackAtTheNextStart() = runTest {
        val store = ScriptedStore(mapOf(Key to "1", "session.token" to "kept"))
        val preference = preference(store)
        preference.load()
        assertEquals(true, preference.enabled.value)
        store.writeFailure = IllegalStateException("the keystore cannot write")

        val heard = assertFailsWith<IllegalStateException> { preference.set(false) }

        assertEquals("the keystore cannot write", heard.message)
        assertEquals(false, preference.enabled.value, "the refusal holds for this run")
        assertNull(store.peek(Key), "the old yes is gone from the store")
        assertEquals(listOf(Key), store.removals)
        assertEquals("kept", store.peek("session.token"), "only the choice is removed, nothing else")
        assertEquals(0, store.wipes)

        store.writeFailure = null
        val next = preference(store)
        next.load()
        assertNull(next.enabled.value, "the next start has no choice: the default applies again, not the yes that was taken back")
    }

    @Test
    fun aFailedNoWhoseRemovalFailsTooReachesTheCallerWithTheFailureOfTheWriteAndTheRefusalHolds() = runTest {
        val store = ScriptedStore(mapOf(Key to "1"))
        store.writeFailure = IllegalStateException("the keystore cannot write")
        store.removeFailure = IllegalStateException("the keystore cannot remove")
        val preference = preference(store)

        val heard = assertFailsWith<IllegalStateException> { preference.set(false) }

        assertEquals("the keystore cannot write", heard.message, "the failure of the removal must not replace the one of the write")
        assertEquals(false, preference.enabled.value)
        assertEquals(listOf(Key), store.removals, "the removal was tried")
        assertEquals("1", store.peek(Key), "the store could not remove it, so the old yes is still there")
    }

    @Test
    fun aFailedNoOverAStoredNoLeavesTheNoInTheStore() = runTest {
        val store = ScriptedStore(mapOf(Key to "0", "session.token" to "kept"))
        store.writeFailure = IllegalStateException("the keystore cannot write")
        val preference = preference(store)

        val heard = assertFailsWith<IllegalStateException> { preference.set(false) }

        assertEquals("the keystore cannot write", heard.message)
        assertEquals(false, preference.enabled.value)
        assertEquals("0", store.peek(Key), "the refusal that the store has stays")
        assertEquals(emptyList(), store.removals, "an older no is not removed")
        assertEquals("kept", store.peek("session.token"))

        store.writeFailure = null
        val next = preference(store)
        next.load()
        assertEquals(false, next.enabled.value, "the next start still has the refusal")
    }

    @Test
    fun aFailedNoOverAStoredNoThatWasLoadedFirstLeavesTheNoInTheStoreToo() = runTest {
        val store = ScriptedStore(mapOf(Key to "0"))
        val preference = preference(store)
        preference.load()
        assertEquals(false, preference.enabled.value)
        store.writeFailure = IllegalStateException("the keystore cannot write")

        assertFailsWith<IllegalStateException> { preference.set(false) }

        assertEquals(false, preference.enabled.value)
        assertEquals("0", store.peek(Key))
        assertEquals(emptyList(), store.removals)
    }

    @Test
    fun aFailedNoOverAnEmptyStoreRemovesNothingAndStillHoldsForTheRun() = runTest {
        val store = ScriptedStore()
        store.writeFailure = IllegalStateException("the keystore cannot write")
        val preference = preference(store)

        val heard = assertFailsWith<IllegalStateException> { preference.set(false) }

        assertEquals("the keystore cannot write", heard.message)
        assertEquals(false, preference.enabled.value)
        assertEquals(emptyList(), store.removals, "there is no readable yes to take back")
        assertNull(store.peek(Key))
    }

    @Test
    fun aFailedNoOverAnUnreadableStoreRemovesNothing() = runTest {
        // The platform cannot read what is stored now: it may be a yes, and it may be a no that is only hidden for the
        // moment, so nothing is erased.
        val store = ScriptedStore(mapOf(Key to "1"))
        store.hidden = true
        store.writeFailure = IllegalStateException("the keystore cannot write")
        val preference = preference(store)

        val heard = assertFailsWith<IllegalStateException> { preference.set(false) }

        assertEquals("the keystore cannot write", heard.message)
        assertEquals(false, preference.enabled.value)
        assertEquals(emptyList(), store.removals)
    }

    @Test
    fun aFailedNoOverAStoredNoThatCannotBeReadNowKeepsTheNoForTheNextStart() = runTest {
        val store = ScriptedStore(mapOf(Key to "0"))
        store.hidden = true
        store.writeFailure = IllegalStateException("the keystore cannot write")
        val preference = preference(store)

        assertFailsWith<IllegalStateException> { preference.set(false) }

        assertEquals(false, preference.enabled.value)
        assertEquals(emptyList(), store.removals, "a no that is only hidden for now must not be erased")
        store.hidden = false
        store.writeFailure = null
        val next = preference(store)
        next.load()
        assertEquals(false, next.enabled.value, "once the store can be read again the refusal is still there")
    }

    @Test
    fun aReadThatFailsInTheFailureBranchDoesNotGetInTheWay() = runTest {
        val store = ScriptedStore(mapOf(Key to "1"))
        store.writeFailure = IllegalStateException("the keystore cannot write")
        store.readFailure = IllegalStateException("the keystore cannot read")
        val preference = preference(store)
        var shownWhenTheCallerHeard: Boolean? = null

        val heard = try {
            preference.set(false)
            null
        } catch (failure: IllegalStateException) {
            shownWhenTheCallerHeard = preference.enabled.value
            failure
        }

        assertNotNull(heard, "the save did not work, and the caller must hear of it")
        assertEquals("the keystore cannot write", heard.message, "the failure of the write, not the one of the read")
        assertEquals(false, shownWhenTheCallerHeard, "the refusal is published before the caller hears of the failure")
        assertEquals(false, preference.enabled.value)
    }

    @Test
    fun aReadThatFailsInTheFailureBranchDoesNotMakeTheLoadReadAgain() = runTest {
        val store = ScriptedStore(mapOf(Key to "1"))
        store.writeFailure = IllegalStateException("the keystore cannot write")
        store.readFailure = IllegalStateException("the keystore cannot read")
        val preference = preference(store)
        assertFailsWith<IllegalStateException> { preference.set(false) }
        val readsOfTheFailedSave = store.reads

        preference.load()

        assertEquals(readsOfTheFailedSave, store.reads)
        assertEquals(false, preference.enabled.value)
    }

    @Test
    fun aFailedNoIsPublishedAlsoWhenTheRemovalFails() = runTest {
        val store = ScriptedStore()
        store.writeFailure = IllegalStateException("the keystore cannot write")
        store.removeFailure = IllegalStateException("the keystore cannot remove")
        val preference = preference(store)
        var shownWhenTheCallerHeard: Boolean? = null

        try {
            preference.set(false)
        } catch (failure: IllegalStateException) {
            shownWhenTheCallerHeard = preference.enabled.value
        }

        assertEquals(false, shownWhenTheCallerHeard)
        // And the store is not read by the load afterwards: what was set is the choice, even if the old yes could not be
        // removed. (The failed save itself looked at the old choice once.)
        val readsOfTheFailedSave = store.reads
        preference.load()
        assertEquals(readsOfTheFailedSave, store.reads)
        assertEquals(false, preference.enabled.value)
    }

    @Test
    fun aFailedYesRemovesNothingAndTheStoreKeepsWhatItHeld() = runTest {
        listOf("1", "0", null).forEach { stored ->
            val store = ScriptedStore(if (stored == null) emptyMap() else mapOf(Key to stored))
            store.writeFailure = IllegalStateException("the keystore cannot write")
            val preference = preference(store)

            assertFailsWith<IllegalStateException> { preference.set(true) }

            assertEquals(emptyList(), store.removals, "stored '$stored'")
            assertEquals(stored, store.peek(Key), "stored '$stored'")
            assertEquals(0, store.wipes)
        }
    }

    @Test
    fun aSaveThatTheStoreTookRemovesNothing() = runTest {
        val store = ScriptedStore(mapOf(Key to "1"))
        val preference = preference(store)

        preference.set(false)
        preference.set(true)
        preference.set(false)

        assertEquals(emptyList(), store.removals)
        assertEquals(0, store.wipes)
        assertEquals("0", store.peek(Key))
    }

    @Test
    fun theFlowShowsAFailedNoAndNothingForAFailedYes() = runTest {
        val store = ScriptedStore()
        store.writeFailure = IllegalStateException("the keystore cannot write")
        val preference = preference(store)
        val seen = mutableListOf<Boolean?>()
        backgroundScope.launch { preference.enabled.collect { seen += it } }
        runCurrent()

        assertFailsWith<IllegalStateException> { preference.set(true) }
        runCurrent()
        assertEquals(listOf<Boolean?>(null), seen)

        assertFailsWith<IllegalStateException> { preference.set(false) }
        runCurrent()
        assertEquals(listOf<Boolean?>(null, false), seen)
    }

    @Test
    fun aFailedWriteDoesNotLockTheNextCall() = runTest {
        val store = ScriptedStore()
        val preference = preference(store)
        store.writeFailure = IllegalStateException("the keystore cannot write")
        assertFailsWith<IllegalStateException> { preference.set(true) }

        store.writeFailure = null
        preference.set(false)

        assertEquals(false, preference.enabled.value)
        assertEquals("0", store.peek(Key))
    }

    @Test
    fun aSaveAfterAFailedNoWorksAndReplacesIt() = runTest {
        val store = ScriptedStore()
        val preference = preference(store)
        store.writeFailure = IllegalStateException("the keystore cannot write")
        assertFailsWith<IllegalStateException> { preference.set(false) }

        store.writeFailure = null
        preference.set(true)
        assertEquals(true, preference.enabled.value, "a yes that the store took replaces the refusal of this run")
        assertEquals("1", store.peek(Key))

        preference.set(false)
        assertEquals(false, preference.enabled.value)
        assertEquals("0", store.peek(Key))
        assertEquals(listOf(Key to "1", Key to "0"), store.writes)
        assertEquals(listOf(Key to "0", Key to "1", Key to "0"), store.attempts)
    }

    @Test
    fun aFailedYesBeforeTheFirstLoadLeavesTheStoreToBeRead() = runTest {
        val store = ScriptedStore(mapOf(Key to "0"))
        val preference = preference(store)
        store.writeFailure = IllegalStateException("the keystore cannot write")
        assertFailsWith<IllegalStateException> { preference.set(true) }

        preference.load()

        assertEquals(1, store.reads, "nothing was set, so the stored choice is still to be read")
        assertEquals(false, preference.enabled.value)
    }

    @Test
    fun aFailedNoBeforeTheFirstLoadIsNotCoveredByTheLateRead() = runTest {
        // The refusal could not be saved, but it is the newest word of the person: the yes that the store holds must
        // not come back over it when the store is read afterwards.
        val store = ScriptedStore(mapOf(Key to "1"))
        store.writeFailure = IllegalStateException("the keystore cannot write")
        val preference = preference(store)
        assertFailsWith<IllegalStateException> { preference.set(false) }
        // The failed save looks at the old choice once, to decide whether to remove it; that is not the load.
        val readsOfTheFailedSave = store.reads

        preference.load()

        assertEquals(readsOfTheFailedSave, store.reads, "what was set is the choice, so the load has nothing to read")
        assertEquals(false, preference.enabled.value)
    }

    @Test
    fun aFailingNoThatComesWhileTheStoreIsBeingReadWaitsForTheReadAndThenWins() = runTest {
        val store = ScriptedStore(mapOf(Key to "1"))
        store.writeFailure = IllegalStateException("the keystore cannot write")
        val preference = preference(store)
        val order = mutableListOf<String>()
        store.onRead = { order += "read" }
        store.onWrite = { _, value -> order += "write $value, shown ${preference.enabled.value}" }

        val loading = launch { preference.load() }
        val saving = launch { runCatching { preference.set(false) } }
        runCurrent()

        assertTrue(loading.isCompleted && saving.isCompleted)
        // The load reads first and shows what it read; the save writes after that, and its failure looks at the old choice.
        assertEquals(listOf("read", "write 0, shown true", "read"), order)
        assertEquals(false, preference.enabled.value, "the refusal is newer than the stored yes that was read before it")
    }

    // ---- save and load meet ----

    @Test
    fun aSaveBeforeTheFirstLoadIsNotCoveredByTheLateRead() = runTest {
        val store = ScriptedStore(mapOf(Key to "0"))
        val preference = preference(store)

        preference.set(true)
        preference.load()

        assertEquals(0, store.reads, "what was set is the choice, there is nothing to read")
        assertEquals(true, preference.enabled.value)
        assertEquals("1", store.peek(Key))
    }

    @Test
    fun aSaveThatComesWhileTheStoreIsBeingReadWaitsForTheRead() = runTest {
        val store = ScriptedStore(mapOf(Key to "0"))
        val preference = preference(store)
        val order = mutableListOf<String>()
        store.onRead = { order += "read" }
        store.onWrite = { _, value -> order += "write $value, shown ${preference.enabled.value}" }

        val loading = launch { preference.load() }
        val saving = launch { preference.set(true) }
        runCurrent()

        assertTrue(loading.isCompleted && saving.isCompleted)
        // The read comes first and its result is shown before the write, so the late read can never cover the newer write.
        assertEquals(listOf("read", "write 1, shown false"), order)
        assertEquals(true, preference.enabled.value)
        assertEquals("1", store.peek(Key))
    }

    @Test
    fun theChoiceSurvivesANewObjectOverTheSameStore() = runTest {
        val store = ScriptedStore()
        preference(store).set(false)

        val again = preference(store)
        assertNull(again.enabled.value, "a new object knows nothing until it reads")
        again.load()
        assertEquals(false, again.enabled.value)

        again.set(true)
        val third = preference(store)
        third.load()
        assertEquals(true, third.enabled.value)
    }

    // ---- a caller that is cancelled ----

    @Test
    fun aCallerCancelledAfterTheWriteBeganStillSeesItFinishedAndPublished() = runTest {
        val store = ScriptedStore()
        val preference = preference(store)

        val saving = launch { preference.set(false) }
        // Started after `saving` and before its write: the cancellation lands while the write waits for the dispatcher.
        launch { saving.cancel() }
        runCurrent()

        assertTrue(saving.isCancelled)
        assertEquals(listOf(Key to "0"), store.writes)
        assertEquals("0", store.peek(Key))
        assertEquals(false, preference.enabled.value, "a write that reached the store is published too")
    }

    @Test
    fun aCancelledSaveDoesNotLockTheNextOne() = runTest {
        val store = ScriptedStore()
        val preference = preference(store)
        val saving = launch { preference.set(false) }
        launch { saving.cancel() }
        runCurrent()

        val next = launch { preference.set(true) }
        runCurrent()

        assertTrue(next.isCompleted && !next.isCancelled)
        assertEquals(true, preference.enabled.value)
        assertEquals(listOf(Key to "0", Key to "1"), store.writes)
    }

    @Test
    fun aCallerCancelledWhileItWaitsForItsTurnWritesNothing() = runTest {
        val store = ScriptedStore()
        val preference = preference(store)

        val first = launch { preference.set(true) }
        val waiting = launch { preference.set(false) }
        launch { waiting.cancel() }
        runCurrent()

        assertTrue(first.isCompleted && !first.isCancelled)
        assertTrue(waiting.isCancelled)
        assertEquals(listOf(Key to "1"), store.attempts, "the cancelled caller never reached the store")
        assertEquals(true, preference.enabled.value)
        assertEquals("1", store.peek(Key))
    }

    @Test
    fun aCallerCancelledWhileAFailingNoIsBeingWrittenStillSeesTheRefusalPublished() = runTest {
        val store = ScriptedStore(mapOf(Key to "1"))
        store.writeFailure = IllegalStateException("the keystore cannot write")
        val preference = preference(store)
        var heard: Throwable? = null

        val saving = launch {
            try {
                preference.set(false)
            } catch (failure: Throwable) {
                heard = failure
            }
        }
        // Started after `saving` and before its write: the cancellation lands while the write waits for the dispatcher.
        launch { saving.cancel() }
        runCurrent()

        assertTrue(saving.isCancelled)
        assertNotNull(heard, "the call must not return as if the write had worked")
        assertEquals(listOf(Key to "0"), store.attempts)
        assertEquals(emptyList(), store.writes)
        assertEquals(false, preference.enabled.value, "the refusal is published although its caller was cancelled")
        assertEquals(listOf(Key), store.removals, "and the older choice is removed, as it is for a caller that stays")
    }

    @Test
    fun aCallerCancelledWhileAFailingYesIsBeingWrittenPublishesNothing() = runTest {
        val store = ScriptedStore()
        store.writeFailure = IllegalStateException("the keystore cannot write")
        val preference = preference(store)

        val saving = launch { runCatching { preference.set(true) } }
        launch { saving.cancel() }
        runCurrent()

        assertTrue(saving.isCancelled)
        assertEquals(listOf(Key to "1"), store.attempts)
        assertNull(preference.enabled.value)
    }

    @Test
    fun aCancelledFailingNoDoesNotLockTheNextOne() = runTest {
        val store = ScriptedStore()
        store.writeFailure = IllegalStateException("the keystore cannot write")
        val preference = preference(store)
        val saving = launch { runCatching { preference.set(false) } }
        launch { saving.cancel() }
        runCurrent()

        store.writeFailure = null
        val next = launch { preference.set(true) }
        runCurrent()

        assertTrue(next.isCompleted && !next.isCancelled)
        assertEquals(true, preference.enabled.value)
        assertEquals(listOf(Key to "1"), store.writes)
    }

    @Test
    fun aCancelledWaitingCallerDoesNotLockTheOthers() = runTest {
        val store = ScriptedStore()
        val preference = preference(store)
        val first = launch { preference.set(true) }
        val waiting = launch { preference.set(false) }
        launch { waiting.cancel() }
        runCurrent()

        preference.set(false)

        assertTrue(first.isCompleted)
        assertEquals(false, preference.enabled.value)
        assertEquals(listOf(Key to "1", Key to "0"), store.writes)
    }
}
