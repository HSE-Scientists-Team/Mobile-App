package org.matchlab.academic_net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.analytics.StatisticsPreference
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.Session

/** The key of the choice on the disk (see the test of the preference on why it is spelled out). */
private const val Key = "analytics.statistics"

/**
 * [AnalyticsController] tells the analytics whether it may send and whose events they are, from the choice of the person
 * and the state of the session.
 *
 * What the analytics is told is read from [RecordingAnalytics.calls] (`profile=…`, `enabled=…`, in the order of the calls).
 * The exact order of the calls is checked where the contract fixes it: the first thing the controller does, and a sign-in,
 * where the profile must come before the switch. Elsewhere a test looks at what the analytics holds after the calls
 * ([RecordingAnalytics.profileId], [RecordingAnalytics.enabled]), so that a call that only repeats what the SDK already
 * holds does not make a test fail.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsControllerTest {
    private fun session(userId: String, token: String = "token-1") =
        Session(UserId(userId), "$userId@hse.ru", AccessToken(token))

    private fun signedIn(userId: String, token: String = "token-1") = AuthState.SignedIn(session(userId, token))

    /**
     * The controller over the real [StatisticsPreference] (on a store that records what is done to it) and a recording
     * analytics. [io] is where the preference reads and writes; by default the dispatcher of the test, so that
     * [settle] runs it too.
     */
    private class Rig(
        private val scope: TestScope,
        stored: String? = null,
        initial: AuthState = AuthState.Restoring,
        io: CoroutineDispatcher = StandardTestDispatcher(scope.testScheduler),
    ) {
        val store = AuditedStore(if (stored == null) emptyMap() else mapOf(Key to stored))
        val preference = StatisticsPreference(store, io)
        val analytics = RecordingAnalytics()
        val auth = MutableStateFlow(initial)
        val controller = AnalyticsController(analytics, preference, auth)

        /** Starts the controller in the background scope of the test and lets everything that is ready run. */
        fun start(inScope: CoroutineScope = scope.backgroundScope) {
            controller.start(inScope)
            settle()
        }

        /** Lets everything that is ready run. */
        fun settle() = scope.runCurrent()
    }

    // ---- before the choice is read ----

    @Test
    fun nothingIsDecidedUntilTheStoredChoiceHasBeenRead() = runTest {
        val held = HeldDispatcher()
        // The person said no; the session is restored at the start, and the read of the choice is still on its way.
        val rig = Rig(this, stored = "0", initial = signedIn("user-1"), io = held)

        rig.start()

        assertEquals(emptyList(), rig.analytics.calls, "nothing may be told while the choice is unknown, not even 'off'")
        assertNull(rig.preference.enabled.value)

        held.release()
        rig.settle()

        assertEquals(listOf("profile=user-1", "enabled=false"), rig.analytics.calls)
        assertFalse(true in rig.analytics.enabledCalls, "sending was on for a moment although the person said no")
    }

    @Test
    fun theDefaultIsNotAppliedWhileTheChoiceIsStillBeingRead() = runTest {
        val held = HeldDispatcher()
        val rig = Rig(this, stored = null, initial = signedIn("user-1"), io = held)

        rig.start()

        assertEquals(emptyList(), rig.analytics.calls)

        held.release()
        rig.settle()

        assertEquals(listOf("profile=user-1", "enabled=true"), rig.analytics.calls)
    }

    // ---- what the analytics is told, by the choice and the session ----

    @Test
    fun withoutAChoiceAndWhileTheSessionIsRestoringNothingIsSent() = runTest {
        val rig = Rig(this, stored = null, initial = AuthState.Restoring)

        rig.start()

        assertEquals(listOf("enabled=false"), rig.analytics.calls, "no profile is touched while it is not known who is there")
    }

    @Test
    fun withoutAChoiceASignedInUserIsOnAndTheProfileComesFirst() = runTest {
        val rig = Rig(this, stored = null, initial = signedIn("user-1"))

        rig.start()

        assertEquals(listOf("profile=user-1", "enabled=true"), rig.analytics.calls)
    }

    @Test
    fun withoutAChoiceNobodySignedInIsOffAndTheProfileIsCleared() = runTest {
        val rig = Rig(this, stored = null, initial = AuthState.SignedOut())

        rig.start()

        assertEquals(listOf("profile=null", "enabled=false"), rig.analytics.calls)
    }

    @Test
    fun aStoredNoKeepsASignedInUserOff() = runTest {
        val rig = Rig(this, stored = "0", initial = signedIn("user-1"))

        rig.start()

        assertEquals(listOf("profile=user-1", "enabled=false"), rig.analytics.calls)
    }

    @Test
    fun aStoredYesSendsEvenBeforeAnybodySignsIn() = runTest {
        // The sign-up form saves the choice before the account exists.
        val signedOut = Rig(this, stored = "1", initial = AuthState.SignedOut())
        signedOut.start()
        assertEquals(listOf("profile=null", "enabled=true"), signedOut.analytics.calls)

        val restoring = Rig(this, stored = "1", initial = AuthState.Restoring)
        restoring.start()
        assertEquals(listOf("enabled=true"), restoring.analytics.calls)
    }

    @Test
    fun aStoredYesWithASignedInUserIsOnWithTheProfile() = runTest {
        val rig = Rig(this, stored = "1", initial = signedIn("user-1"))

        rig.start()

        assertEquals(listOf("profile=user-1", "enabled=true"), rig.analytics.calls)
    }

    // ---- the session changes ----

    @Test
    fun signingInTurnsTheDefaultOnAndSigningOutTurnsItOff() = runTest {
        val rig = Rig(this)
        rig.start()
        assertEquals(listOf("enabled=false"), rig.analytics.calls)

        rig.auth.value = signedIn("user-1")
        rig.settle()
        // The profile first, so that what sending allows is already the user's.
        assertEquals(listOf("enabled=false", "profile=user-1", "enabled=true"), rig.analytics.calls)

        rig.auth.value = AuthState.SignedOut()
        rig.settle()
        assertEquals(
            listOf("enabled=false", "profile=user-1", "enabled=true", "profile=null", "enabled=false"),
            rig.analytics.calls,
        )
    }

    @Test
    fun aRestoredSessionOfAPersonWhoSaidNoNeverSwitchesSendingOn() = runTest {
        val rig = Rig(this, stored = "0", initial = AuthState.Restoring)
        rig.start()

        rig.auth.value = signedIn("user-1")
        rig.settle()
        rig.auth.value = AuthState.SignedOut()
        rig.settle()
        rig.auth.value = signedIn("user-2")
        rig.settle()

        assertFalse(true in rig.analytics.enabledCalls, rig.analytics.calls.toString())
        assertEquals(false, rig.analytics.enabled)
        assertEquals("user-2", rig.analytics.profileId)
    }

    @Test
    fun signingOutTakesTheProfileAwayAndKeepsTheChoiceOfTheDevice() = runTest {
        val rig = Rig(this, stored = "1", initial = signedIn("user-1"))
        rig.start()

        rig.auth.value = AuthState.SignedOut()
        rig.settle()

        assertNull(rig.analytics.profileId)
        assertEquals(null, rig.analytics.profileIds.last())
        assertEquals(true, rig.analytics.enabled, "the choice belongs to the device, not to the account")
        assertFalse(false in rig.analytics.enabledCalls)
    }

    @Test
    fun anotherAccountAfterASignOutGetsItsOwnProfile() = runTest {
        val rig = Rig(this, initial = signedIn("user-1"))
        rig.start()

        rig.auth.value = AuthState.SignedOut()
        rig.settle()
        rig.auth.value = signedIn("user-2")
        rig.settle()

        assertEquals(listOf("user-1", null, "user-2"), rig.analytics.profileIds)
        assertEquals(true, rig.analytics.enabled)
    }

    @Test
    fun anotherAccountThatReplacesTheSessionDirectlyGetsItsOwnProfileWithoutPassingThroughNobody() = runTest {
        val rig = Rig(this, initial = signedIn("user-1"))
        rig.start()

        rig.auth.value = signedIn("user-2")
        rig.settle()

        assertEquals(listOf<String?>("user-1", "user-2"), rig.analytics.profileIds)
        assertEquals(true, rig.analytics.enabled)
    }

    // ---- the choice changes ----

    @Test
    fun aChangeOfTheChoiceInTheSettingsReachesTheAnalyticsAtOnce() = runTest {
        val rig = Rig(this, initial = signedIn("user-1"))
        rig.start()
        assertEquals(true, rig.analytics.enabled, "on by default")

        rig.preference.set(false)
        rig.settle()
        assertEquals(false, rig.analytics.enabled)
        assertEquals("enabled=false", rig.analytics.calls.last())

        rig.preference.set(true)
        rig.settle()
        assertEquals(true, rig.analytics.enabled)
        assertEquals("enabled=true", rig.analytics.calls.last())

        assertEquals(setOf<String?>("user-1"), rig.analytics.profileIds.toSet(), "the choice never changes whose events they are")
    }

    @Test
    fun theChoiceOfTheSignUpFormIsInForceBeforeTheAccountExists() = runTest {
        val rig = Rig(this, initial = AuthState.SignedOut())
        rig.start()
        assertEquals(false, rig.analytics.enabled)

        rig.preference.set(true)
        rig.settle()
        assertEquals(true, rig.analytics.enabled)
        assertNull(rig.analytics.profileId)

        rig.auth.value = signedIn("user-1")
        rig.settle()
        assertEquals(true, rig.analytics.enabled)
        assertEquals("user-1", rig.analytics.profileId)
    }

    @Test
    fun aNoOfTheSignUpFormKeepsTheNewAccountOff() = runTest {
        val rig = Rig(this, initial = AuthState.SignedOut())
        rig.start()

        rig.preference.set(false)
        rig.settle()
        rig.auth.value = signedIn("user-1")
        rig.settle()

        assertEquals(false, rig.analytics.enabled)
        assertEquals("user-1", rig.analytics.profileId)
        assertFalse(true in rig.analytics.enabledCalls, rig.analytics.calls.toString())
    }

    @Test
    fun aNoSavedWhileSignedInTurnsSendingOffAndAYesTurnsItBackOn() = runTest {
        val rig = Rig(this, stored = "1", initial = signedIn("user-1"))
        rig.start()

        rig.preference.set(false)
        rig.settle()
        assertEquals(false, rig.analytics.enabled)

        rig.auth.value = AuthState.SignedOut()
        rig.settle()
        assertEquals(false, rig.analytics.enabled, "signing out does not undo the choice")

        rig.preference.set(true)
        rig.settle()
        assertEquals(true, rig.analytics.enabled)
    }

    // ---- a choice that could not be saved ----

    @Test
    fun aRefusalThatCouldNotBeSavedStillSwitchesSendingOff() = runTest {
        val rig = Rig(this, initial = signedIn("user-1"))
        rig.start()
        assertEquals(true, rig.analytics.enabled, "on by default")
        rig.store.failingKeys = setOf(Key)

        assertFailsWith<IllegalStateException> { rig.preference.set(false) }
        rig.settle()

        assertEquals(false, rig.analytics.enabled, "the safe side: the refusal holds although it could not be saved")
        assertEquals("enabled=false", rig.analytics.calls.last())
        assertNull(rig.store.peek(Key))
    }

    @Test
    fun aRefusalThatCouldNotBeSavedHoldsThroughASignOutAndANewSignIn() = runTest {
        val rig = Rig(this, initial = signedIn("user-1"))
        rig.start()
        rig.store.failingKeys = setOf(Key)
        assertFailsWith<IllegalStateException> { rig.preference.set(false) }
        rig.settle()
        val callsBefore = rig.analytics.calls.size

        rig.auth.value = AuthState.SignedOut()
        rig.settle()
        rig.auth.value = signedIn("user-2")
        rig.settle()

        assertEquals("user-2", rig.analytics.profileId)
        assertEquals(false, rig.analytics.enabled)
        assertFalse(true in rig.analytics.enabledCalls.drop(1), rig.analytics.calls.drop(callsBefore).toString())
    }

    @Test
    fun aConsentThatCouldNotBeSavedSwitchesNothingOn() = runTest {
        val rig = Rig(this, initial = AuthState.SignedOut())
        rig.start()
        assertEquals(false, rig.analytics.enabled)
        rig.store.failingKeys = setOf(Key)

        assertFailsWith<IllegalStateException> { rig.preference.set(true) }
        rig.settle()

        assertEquals(false, rig.analytics.enabled, "the yes was not saved, so it is no choice")
        assertNull(rig.preference.enabled.value)
    }

    @Test
    fun aConsentThatCouldNotBeSavedLeavesTheDefaultOfASignedInUser() = runTest {
        val rig = Rig(this, initial = signedIn("user-1"))
        rig.start()
        rig.store.failingKeys = setOf(Key)

        assertFailsWith<IllegalStateException> { rig.preference.set(true) }
        rig.settle()

        assertEquals(true, rig.analytics.enabled, "no choice: on while somebody is signed in")
        assertNull(rig.preference.enabled.value)
    }

    @Test
    fun aRefusalThatCouldNotBeSavedIsReplacedByAYesThatCouldBe() = runTest {
        val rig = Rig(this, initial = signedIn("user-1"))
        rig.start()
        rig.store.failingKeys = setOf(Key)
        assertFailsWith<IllegalStateException> { rig.preference.set(false) }
        rig.settle()
        assertEquals(false, rig.analytics.enabled)

        rig.store.failingKeys = emptySet()
        rig.preference.set(true)
        rig.settle()

        assertEquals(true, rig.analytics.enabled)
        assertEquals("1", rig.store.peek(Key))
    }

    // ---- the same decision is not told twice ----

    @Test
    fun aSignedOutStateThatDiffersOnlyInWhatTheScreenShowsTellsNothingNew() = runTest {
        val rig = Rig(this, initial = AuthState.SignedOut())
        rig.start()
        val before = rig.analytics.calls.toList()

        rig.auth.value = AuthState.SignedOut(sessionExpired = true, lastEmail = "user-1@hse.ru")
        rig.settle()

        assertEquals(before, rig.analytics.calls)
    }

    @Test
    fun aNewTokenOfTheSameUserTellsNothingNew() = runTest {
        val rig = Rig(this, initial = signedIn("user-1", token = "token-1"))
        rig.start()
        val before = rig.analytics.calls.toList()

        rig.auth.value = signedIn("user-1", token = "token-2")
        rig.settle()

        assertEquals(before, rig.analytics.calls)
    }

    @Test
    fun theSameChoiceSavedAgainTellsNothingNew() = runTest {
        val rig = Rig(this, stored = "1", initial = signedIn("user-1"))
        rig.start()
        val before = rig.analytics.calls.toList()

        rig.preference.set(true)
        rig.settle()
        rig.preference.set(true)
        rig.settle()

        assertEquals(before, rig.analytics.calls)
    }

    @Test
    fun theSessionRestoredAfterTheStartTellsEachThingOnce() = runTest {
        val rig = Rig(this, initial = AuthState.Restoring)
        rig.start()

        rig.auth.value = signedIn("user-1")
        rig.settle()

        assertEquals(1, rig.analytics.calls.count { it == "profile=user-1" })
        assertEquals(1, rig.analytics.calls.count { it == "enabled=true" })
    }

    // ---- the controller does not write ----

    @Test
    fun theDefaultIsAppliedButNeverWrittenToTheStore() = runTest {
        val rig = Rig(this, stored = null, initial = AuthState.Restoring)
        rig.start()

        rig.auth.value = signedIn("user-1")
        rig.settle()
        rig.auth.value = AuthState.SignedOut()
        rig.settle()
        rig.auth.value = signedIn("user-2")
        rig.settle()

        assertEquals(true, rig.analytics.enabled, "the default is on while somebody is signed in")
        assertEquals(emptyList(), rig.store.mutations, "the controller wrote to the store")
        assertNull(rig.store.peek(Key))
        assertNull(rig.preference.enabled.value, "there is still no choice: the default is not a choice")
    }

    @Test
    fun aStoredNoIsNeverOverwrittenBySigningIn() = runTest {
        val rig = Rig(this, stored = "0", initial = AuthState.Restoring)
        rig.start()

        rig.auth.value = signedIn("user-1")
        rig.settle()
        rig.auth.value = AuthState.SignedOut()
        rig.settle()
        rig.auth.value = signedIn("user-2")
        rig.settle()

        assertEquals(emptyList(), rig.store.mutations, "the controller wrote to the store")
        assertEquals("0", rig.store.peek(Key))
        assertEquals(false, rig.preference.enabled.value)
    }

    @Test
    fun aStoredYesIsNotTouchedEither() = runTest {
        val rig = Rig(this, stored = "1", initial = signedIn("user-1"))
        rig.start()

        rig.auth.value = AuthState.SignedOut()
        rig.settle()

        assertEquals(emptyList(), rig.store.mutations)
        assertEquals("1", rig.store.peek(Key))
    }

    @Test
    fun aChoiceThatCouldNotBeReadStaysInTheStoreForTheNextStart() = runTest {
        // The person said no earlier; the platform cannot read the store right now, so for the controller there is no choice.
        val rig = Rig(this, stored = "0", initial = signedIn("user-1"))
        rig.store.hidden = true

        rig.start()

        assertEquals(true, rig.analytics.enabled, "the default of a device without a choice: on while somebody is signed in")
        assertEquals(emptyList(), rig.store.mutations, "the default must not be written over what could not be read")
        rig.store.hidden = false
        assertEquals("0", rig.store.peek(Key), "the 'no' is still there for the next start")
    }

    // ---- the work ends with its scope ----

    @Test
    fun theWorkEndsWithTheScopeItWasStartedIn() = runTest {
        val rig = Rig(this)
        val scope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        rig.start(scope)
        assertEquals(listOf("enabled=false"), rig.analytics.calls)

        scope.cancel()
        rig.auth.value = signedIn("user-1")
        rig.preference.set(true)
        rig.settle()

        assertEquals(listOf("enabled=false"), rig.analytics.calls, "a cancelled controller still told the analytics")
    }

    @Test
    fun nothingIsToldBeforeTheWorkIsStarted() = runTest {
        val rig = Rig(this, stored = "1", initial = signedIn("user-1"))

        rig.settle()

        assertTrue(rig.analytics.calls.isEmpty())
    }
}
