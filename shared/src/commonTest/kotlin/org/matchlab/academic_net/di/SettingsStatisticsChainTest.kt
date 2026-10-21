package org.matchlab.academic_net.di

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.matchlab.academic_net.AuditedStore
import org.matchlab.academic_net.RecordingAnalytics
import org.matchlab.academic_net.eventually
import org.matchlab.academic_net.feature.settings.ui.SettingsViewModel

/** The key of the choice on the disk (see the test of the preference on why it is spelled out). */
private const val Key = "analytics.statistics"

/**
 * The switch "Статистика использования" through the whole chain the app builds: the view model of the settings screen over
 * the use cases of a session graph, the adapter, the choice of the process on a store that can fail, and the analytics that
 * the choice switches. Every layer has its own tests over fakes; this is the one place where they meet, and where the order
 * of events is the one of a device: the store thread publishes a refusal that could not be saved, and the main thread hears
 * of it before it hears of the failure.
 *
 * The main dispatcher is a test dispatcher over the clock of the test, so the view model runs on the thread of the test;
 * the store and the sign-in run on real threads, and what depends on them is waited for in real time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStatisticsChainTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** A launch of the app with somebody signed in, the controller of the analytics running, and the settings screen open. */
    private class OpenScreen(val launch: AppLaunch, val analytics: RecordingAnalytics, val viewModel: SettingsViewModel)

    private suspend fun TestScope.openScreen(store: AuditedStore): OpenScreen {
        val analytics = RecordingAnalytics()
        val launch = AppLaunch(store = store, analytics = analytics)
        AppGraphs(launch.app, ComponentSessionGraphs(launch.app)).start(backgroundScope)
        withContext(Dispatchers.Default) { launch.signIn() }
        eventually { analytics.profileId == ServerUserId && analytics.enabled == true }
        val settings = launch.sessionGraph().dependencies.settings
        val viewModel = SettingsViewModel(
            getOpenToCollaboration = settings.getOpenToCollaboration,
            setOpenToCollaboration = settings.setOpenToCollaboration,
            observeStatistics = settings.observeStatistics,
            setStatistics = settings.setStatistics,
        )
        runCurrent()
        assertTrue(viewModel.statistics.value, "no choice: the switch is on")
        return OpenScreen(launch, analytics, viewModel)
    }

    @Test
    fun aRefusalThatTheStoreCannotWriteLeavesTheSwitchAndTheSendingOffAndTellsTheFailure() = runTest {
        val store = AuditedStore().apply { failingKeys = setOf(Key) }
        val screen = openScreen(store)

        screen.viewModel.onStatisticsToggle(false)
        eventually { screen.viewModel.statisticsFailedToSave.value }

        assertFalse(screen.viewModel.statistics.value, "the refusal is in force, so the switch stays off")
        eventually { screen.analytics.enabled == false }
        assertEquals(false, screen.launch.app.preference.enabled.value)
        assertNull(store.peek(Key), "nothing reached the store")
    }

    @Test
    fun theSwitchStaysOffWhenTheScreenAsksTheSameRefusalAgain() = runTest {
        val store = AuditedStore().apply { failingKeys = setOf(Key) }
        val screen = openScreen(store)
        screen.viewModel.onStatisticsToggle(false)
        eventually { screen.viewModel.statisticsFailedToSave.value }
        screen.viewModel.onStatisticsSaveFailureShown()

        screen.viewModel.onStatisticsToggle(false)
        runCurrent()

        assertEquals(listOf("write $Key"), store.mutations.filter { it == "write $Key" }, "off is not asked for again: the switch shows off")
        assertFalse(screen.viewModel.statisticsFailedToSave.value)
    }

    @Test
    fun aConsentThatTheStoreCannotWriteLeavesTheSwitchAndTheSendingOff() = runTest {
        val store = AuditedStore()
        val screen = openScreen(store)
        screen.viewModel.onStatisticsToggle(false)
        eventually { screen.analytics.enabled == false && store.peek(Key) == "0" }
        store.failingKeys = setOf(Key)

        screen.viewModel.onStatisticsToggle(true)
        eventually { screen.viewModel.statisticsFailedToSave.value }

        assertFalse(screen.viewModel.statistics.value, "a consent that was not saved is no consent")
        assertEquals(false, screen.analytics.enabled)
        assertEquals("0", store.peek(Key))
    }

    @Test
    fun aRefusalThatTheStoreCouldNotWriteIsReplacedByAConsentOnceTheStoreCan() = runTest {
        val store = AuditedStore().apply { failingKeys = setOf(Key) }
        val screen = openScreen(store)
        screen.viewModel.onStatisticsToggle(false)
        eventually { screen.viewModel.statisticsFailedToSave.value }
        store.failingKeys = emptySet()

        screen.viewModel.onStatisticsToggle(true)
        eventually { screen.analytics.enabled == true && store.peek(Key) == "1" }

        assertTrue(screen.viewModel.statistics.value)
        assertFalse(screen.viewModel.statisticsFailedToSave.value, "a new tap clears the old failure")
    }

    @Test
    fun aRefusalThatTheStoreTookSwitchesTheSwitchAndTheSendingOffWithoutAFailure() = runTest {
        val store = AuditedStore()
        val screen = openScreen(store)

        screen.viewModel.onStatisticsToggle(false)
        eventually { screen.analytics.enabled == false && store.peek(Key) == "0" }
        runCurrent()

        assertFalse(screen.viewModel.statistics.value)
        assertFalse(screen.viewModel.statisticsFailedToSave.value)
        assertEquals(listOf("write $Key"), store.mutations.filter { it == "write $Key" })
    }
}
