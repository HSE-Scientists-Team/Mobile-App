package org.matchlab.academic_net.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.matchlab.academic_net.AuditedStore
import org.matchlab.academic_net.Backend
import org.matchlab.academic_net.RecordingAnalytics
import org.matchlab.academic_net.RecordingAnalytics.Event
import org.matchlab.academic_net.analytics.NoOpAnalytics
import org.matchlab.academic_net.analytics.ProjectSource
import org.matchlab.academic_net.analytics.UserSource
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.eventually
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.securestore.InMemorySecureStore
import org.matchlab.academic_net.waitInRealTime

/** The key of the choice on the disk (see the test of the preference on why it is spelled out). */
private const val Key = "analytics.statistics"

/**
 * The analytics in the graphs of the process: what the platform gives reaches the graph, the choice of the person is one
 * object for the whole process and lives in the store of the process, the settings of a session are that choice, and
 * [AppGraphs] starts the controller of the analytics once, counts the taps with the analytics of the platform and saves the
 * choice of the sign-up form.
 *
 * The choice is kept on the real IO dispatcher (the graph builds it that way), so what depends on it is waited for in real
 * time ([eventually]); the sign-in talks to a mock engine and is run outside the virtual time of the test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsGraphTest {
    private val user = Session(UserId("user-1"), "user-1@hse.ru", AccessToken("token-1"))
    private val other = Session(UserId("user-2"), "user-2@hse.ru", AccessToken("token-2"))

    private fun graphsOf(launch: AppLaunch) = AppGraphs(launch.app, ComponentSessionGraphs(launch.app))

    private fun TestScope.startedGraphsOf(launch: AppLaunch) = graphsOf(launch).also { it.start(backgroundScope) }

    // ---- the analytics of the platform ----

    @Test
    fun withoutAnAnalyticsFromThePlatformTheGraphGetsTheEmptyOne() {
        val app = createAppComponent(InMemorySecureStore(), Backend.Gateway(), FakeServer().factory)

        assertSame(NoOpAnalytics, app.analytics)
    }

    @Test
    fun theAnalyticsOfThePlatformReachesTheGraph() {
        val analytics = RecordingAnalytics()

        val app = AppLaunch(analytics = analytics).app

        assertSame(analytics, app.analytics)
    }

    @Test
    fun theAnalyticsOfOneProcessIsNotTheAnalyticsOfAnother() {
        val first = RecordingAnalytics()
        val second = RecordingAnalytics()

        assertSame(first, AppLaunch(analytics = first).app.analytics)
        assertSame(second, AppLaunch(analytics = second).app.analytics)
    }

    // ---- the choice of the person ----

    @Test
    fun thereIsOnePreferencePerProcess() {
        val app = AppLaunch().app

        assertSame(app.preference, app.preference)
        assertNotSame(app.preference, AppLaunch().app.preference)
    }

    @Test
    fun theChoiceLivesInTheStoreOfTheProcessAndSurvivesTheProcess() = runTest {
        val store = AuditedStore()
        val first = AppLaunch(store = store).app

        first.preference.set(false)

        assertEquals("0", store.peek(Key))
        val again = AppLaunch(store = store).app
        assertNull(again.preference.enabled.value, "a new process knows nothing until it reads")
        again.preference.load()
        assertEquals(false, again.preference.enabled.value)
    }

    @Test
    fun theSettingsOfASessionAreTheChoiceOfTheProcess() = runTest {
        val store = AuditedStore()
        val launch = AppLaunch(store = store)
        val settings = launch.sessionGraph(user).dependencies.settings
        assertEquals(true, settings.observeStatistics().first(), "no choice: on")

        val saved = settings.setStatistics(false)

        assertTrue(saved.isSuccess)
        assertEquals(false, launch.app.preference.enabled.value, "the switch writes the preference of the process")
        assertEquals("0", store.peek(Key))
        assertEquals(false, settings.observeStatistics().first())

        launch.app.preference.set(true)
        assertEquals(true, settings.observeStatistics().first(), "a choice made elsewhere (the sign-up form) shows in the switch")
    }

    @Test
    fun theChoiceIsTheDeviceNotTheSession() = runTest {
        val launch = AppLaunch()
        launch.sessionGraph(user).dependencies.settings.setStatistics(false)

        val next = launch.sessionGraph(other).dependencies.settings

        assertEquals(false, next.observeStatistics().first(), "another account on the same device meets the same choice")
    }

    @Test
    fun aFailedYesOfTheSwitchIsAFailedResultAndChangesNothing() = runTest {
        val store = AuditedStore().apply { failingKeys = setOf(Key) }
        val launch = AppLaunch(store = store)
        val settings = launch.sessionGraph(user).dependencies.settings

        val saved = settings.setStatistics(true)

        assertTrue(saved.isFailure)
        assertNull(launch.app.preference.enabled.value)
        assertEquals(true, settings.observeStatistics().first())
    }

    @Test
    fun aFailedNoOfTheSwitchIsAFailedResultAndTheRefusalHoldsForTheRun() = runTest {
        val store = AuditedStore().apply { failingKeys = setOf(Key) }
        val launch = AppLaunch(store = store)
        val settings = launch.sessionGraph(user).dependencies.settings

        val saved = settings.setStatistics(false)

        assertTrue(saved.isFailure)
        assertEquals(false, launch.app.preference.enabled.value)
        assertEquals(false, settings.observeStatistics().first(), "the switch stays off: the refusal is in force")
        assertNull(store.peek(Key))
    }

    // ---- the clicks ----

    @Test
    fun theClicksGoToTheAnalyticsOfThePlatform() {
        val analytics = RecordingAnalytics()
        val graphs = graphsOf(AppLaunch(analytics = analytics))

        graphs.clicks.projectCardClicked("project-1", ProjectSource.FeedRecommendations)
        graphs.clicks.projectOpened("project-1", ProjectSource.FeedRecommendations)
        graphs.clicks.userClicked("user-2", UserSource.ReviewAuthor)

        assertEquals(
            listOf(
                Event("project_card_click", mapOf("project_id" to "project-1", "source" to "feed_recommendations")),
                Event("project_click", mapOf("project_id" to "project-1", "source" to "feed_recommendations")),
                Event("user_click", mapOf("user_id" to "user-2", "source" to "review_author")),
            ),
            analytics.events,
        )
    }

    @Test
    fun theClicksOfAProcessWithoutAnAnalyticsDoNothing() {
        val graphs = graphsOf(AppLaunch())

        graphs.clicks.projectCardClicked("project-1", ProjectSource.ProjectSearch)
        graphs.clicks.userClicked("user-2", UserSource.PeopleSearch)
    }

    // ---- the choice of the sign-up form ----

    @Test
    fun theChoiceOfTheSignUpFormIsSavedByTheProcess() = runTest {
        val store = AuditedStore()
        val launch = AppLaunch(store = store)
        val graphs = startedGraphsOf(launch)

        graphs.onStatisticsChosen(false)
        eventually { launch.app.preference.enabled.value == false }
        assertEquals("0", store.peek(Key))

        graphs.onStatisticsChosen(true)
        eventually { launch.app.preference.enabled.value == true }
        assertEquals("1", store.peek(Key))
    }

    @Test
    fun beforeTheStartAChoiceIsDroppedSilently() = runTest {
        val store = AuditedStore()
        val launch = AppLaunch(store = store)
        val graphs = graphsOf(launch)

        graphs.onStatisticsChosen(false)
        waitInRealTime(200.milliseconds)

        assertNull(store.peek(Key))
        assertNull(launch.app.preference.enabled.value)
        assertEquals(emptyList(), store.mutations)
    }

    @Test
    fun theChoiceIsSavedInTheScopeOfTheFirstStartWhateverComesLater() = runTest {
        val launch = AppLaunch()
        val graphs = startedGraphsOf(launch)
        val later = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        graphs.start(later)
        later.cancel()

        graphs.onStatisticsChosen(false)

        eventually { launch.app.preference.enabled.value == false }
    }

    @Test
    fun aRefusalOfTheSignUpFormThatCannotBeSavedIsNotReportedAndStillHoldsForTheRun() = runTest {
        val store = AuditedStore().apply { failingKeys = setOf(Key) }
        val launch = AppLaunch(store = store)
        val graphs = startedGraphsOf(launch)

        graphs.onStatisticsChosen(false)
        eventually { launch.app.preference.enabled.value == false }
        waitInRealTime(50.milliseconds)

        assertEquals(listOf("write $Key"), store.mutations, "the write was tried once; there was no yes to take back")
        assertNull(store.peek(Key))
        // The failure was not rethrown into the scope of the process, which still works: once the store can write, the
        // next choice is saved.
        store.failingKeys = emptySet()
        graphs.onStatisticsChosen(true)
        eventually { launch.app.preference.enabled.value == true }
        assertEquals("1", store.peek(Key))
    }

    @Test
    fun aRefusalOfTheSignUpFormThatCannotBeSavedRemovesAnOlderYesFromTheStore() = runTest {
        val store = AuditedStore(mapOf(Key to "1")).apply { failingKeys = setOf(Key) }
        val launch = AppLaunch(store = store)
        val graphs = startedGraphsOf(launch)
        eventually { launch.app.preference.enabled.value == true }

        graphs.onStatisticsChosen(false)
        eventually { launch.app.preference.enabled.value == false && store.peek(Key) == null }

        // The next start of the app has no choice: the default applies again, not the yes that was taken back.
        val nextStart = AppLaunch(store = store).app.preference
        nextStart.load()
        assertNull(nextStart.enabled.value)
    }

    @Test
    fun aRefusalOfTheSignUpFormThatCannotBeSavedLeavesAnOlderRefusalInTheStore() = runTest {
        val store = AuditedStore(mapOf(Key to "0")).apply { failingKeys = setOf(Key) }
        val launch = AppLaunch(store = store)
        val graphs = startedGraphsOf(launch)
        eventually { launch.app.preference.enabled.value == false }

        graphs.onStatisticsChosen(false)
        eventually { "write $Key" in store.mutations }
        waitInRealTime(50.milliseconds)

        assertEquals("0", store.peek(Key), "the refusal that the store has stays")
        assertEquals(listOf("write $Key"), store.mutations, "the write was tried once and nothing was removed")
        assertEquals(false, launch.app.preference.enabled.value)

        // The next start of the app still has the refusal.
        val nextStart = AppLaunch(store = store).app.preference
        nextStart.load()
        assertEquals(false, nextStart.enabled.value)
    }

    @Test
    fun aConsentOfTheSignUpFormThatCannotBeSavedLeavesAnOlderRefusalInTheStore() = runTest {
        val store = AuditedStore(mapOf(Key to "0")).apply { failingKeys = setOf(Key) }
        val launch = AppLaunch(store = store)
        val graphs = startedGraphsOf(launch)
        eventually { launch.app.preference.enabled.value == false }

        graphs.onStatisticsChosen(true)
        eventually { "write $Key" in store.mutations }
        waitInRealTime(50.milliseconds)

        assertEquals(false, launch.app.preference.enabled.value, "the yes was not saved, so the refusal stands")
        assertEquals("0", store.peek(Key))
        assertEquals(listOf("write $Key"), store.mutations, "nothing was removed")
    }

    @Test
    fun aConsentOfTheSignUpFormThatCannotBeSavedIsNotReportedAndLeavesNoChoice() = runTest {
        val store = AuditedStore().apply { failingKeys = setOf(Key) }
        val launch = AppLaunch(store = store)
        val graphs = startedGraphsOf(launch)

        graphs.onStatisticsChosen(true)
        eventually { "write $Key" in store.mutations }
        waitInRealTime(50.milliseconds)

        assertNull(launch.app.preference.enabled.value)
        assertNull(store.peek(Key))
        // The scope of the process still works.
        store.failingKeys = emptySet()
        graphs.onStatisticsChosen(true)
        eventually { launch.app.preference.enabled.value == true }
        assertEquals("1", store.peek(Key))
    }

    // ---- the controller of the analytics ----

    @Test
    fun startingTheGraphsStartsTheControllerOfTheAnalyticsOnce() = runTest {
        val analytics = RecordingAnalytics()
        val launch = AppLaunch(analytics = analytics)
        val graphs = graphsOf(launch)

        graphs.start(backgroundScope)
        graphs.start(backgroundScope)
        eventually { analytics.enabled == false }

        withContext(Dispatchers.Default) { launch.signIn() }
        eventually { analytics.profileId == ServerUserId }

        // A second controller would have told the profile a second time.
        assertEquals(1, analytics.calls.count { it == "profile=$ServerUserId" }, analytics.calls.toString())
    }

    @Test
    fun signingInAndOutReachesTheAnalyticsOfThePlatformWithoutAChoiceBeingWritten() = runTest {
        val analytics = RecordingAnalytics()
        val store = AuditedStore()
        val launch = AppLaunch(store = store, analytics = analytics)
        startedGraphsOf(launch)
        // The session is restored (there is none): nobody is signed in, so nobody's events and nothing is sent.
        eventually { analytics.profileIds == listOf<String?>(null) && analytics.enabled == false }

        withContext(Dispatchers.Default) { launch.signIn() }
        eventually { analytics.profileId == ServerUserId && analytics.enabled == true }

        withContext(Dispatchers.Default) { launch.app.auth.signOut() }
        eventually { analytics.profileIds.size == 3 && analytics.enabled == false }

        assertEquals(listOf(null, ServerUserId, null), analytics.profileIds)
        assertFalse("write $Key" in store.mutations, "the default was written to the store: ${store.mutations}")
        assertNull(launch.app.preference.enabled.value)
    }

    @Test
    fun theSwitchOfTheSettingsStopsAndResumesTheSending() = runTest {
        val analytics = RecordingAnalytics()
        val launch = AppLaunch(analytics = analytics)
        startedGraphsOf(launch)
        withContext(Dispatchers.Default) { launch.signIn() }
        eventually { analytics.profileId == ServerUserId && analytics.enabled == true }
        val settings = launch.sessionGraph().dependencies.settings

        assertTrue(settings.setStatistics(false).isSuccess)
        eventually { analytics.enabled == false }
        assertEquals(ServerUserId, analytics.profileId, "the user is still the same, only the sending stopped")

        assertTrue(settings.setStatistics(true).isSuccess)
        eventually { analytics.enabled == true }
    }

    @Test
    fun aNoOfTheSignUpFormKeepsTheNewAccountOffAfterTheSignIn() = runTest {
        val analytics = RecordingAnalytics()
        val launch = AppLaunch(analytics = analytics)
        val graphs = startedGraphsOf(launch)
        graphs.onStatisticsChosen(false)
        eventually { launch.app.preference.enabled.value == false }

        withContext(Dispatchers.Default) { launch.signIn() }
        eventually { analytics.profileId == ServerUserId }

        assertEquals(false, analytics.enabled)
        assertFalse(true in analytics.enabledCalls, analytics.calls.toString())
    }

    @Test
    fun aRefusalThatCannotBeSavedKeepsTheNewAccountOffAfterTheSignIn() = runTest {
        val analytics = RecordingAnalytics()
        val store = AuditedStore().apply { failingKeys = setOf(Key) }
        val launch = AppLaunch(store = store, analytics = analytics)
        val graphs = startedGraphsOf(launch)
        graphs.onStatisticsChosen(false)
        eventually { launch.app.preference.enabled.value == false }

        withContext(Dispatchers.Default) { launch.signIn() }
        eventually { analytics.profileId == ServerUserId }

        assertEquals(false, analytics.enabled, "the person said no, so nothing is sent although the no could not be saved")
        assertFalse(true in analytics.enabledCalls, analytics.calls.toString())
        assertNull(store.peek(Key))
    }

    @Test
    fun aConsentThatCannotBeSavedLeavesTheDefaultOnAfterTheSignIn() = runTest {
        val analytics = RecordingAnalytics()
        val store = AuditedStore().apply { failingKeys = setOf(Key) }
        val launch = AppLaunch(store = store, analytics = analytics)
        val graphs = startedGraphsOf(launch)
        graphs.onStatisticsChosen(true)
        eventually { "write $Key" in store.mutations }
        waitInRealTime(50.milliseconds)

        withContext(Dispatchers.Default) { launch.signIn() }
        eventually { analytics.profileId == ServerUserId }

        assertNull(launch.app.preference.enabled.value, "there is no choice: the yes was not saved")
        assertEquals(true, analytics.enabled, "the default of a signed-in user without a choice")
    }

    @Test
    fun theSwitchOfTheSettingsStopsTheSendingEvenWhenTheStoreCannotWrite() = runTest {
        val analytics = RecordingAnalytics()
        val store = AuditedStore().apply { failingKeys = setOf(Key) }
        val launch = AppLaunch(store = store, analytics = analytics)
        startedGraphsOf(launch)
        withContext(Dispatchers.Default) { launch.signIn() }
        eventually { analytics.profileId == ServerUserId && analytics.enabled == true }
        val settings = launch.sessionGraph().dependencies.settings

        val saved = settings.setStatistics(false)

        assertTrue(saved.isFailure, "the screen is told that the choice was not saved")
        eventually { analytics.enabled == false }
        assertEquals(false, settings.observeStatistics().first(), "and the switch stays off, as the sending is")
    }

    @Test
    fun aYesOfTheSignUpFormSendsFromTheSignUpOn() = runTest {
        val analytics = RecordingAnalytics()
        val launch = AppLaunch(analytics = analytics)
        val graphs = startedGraphsOf(launch)

        graphs.onStatisticsChosen(true)

        eventually { analytics.enabled == true }
        assertNull(analytics.profileId, "nobody is signed in yet")
    }
}
