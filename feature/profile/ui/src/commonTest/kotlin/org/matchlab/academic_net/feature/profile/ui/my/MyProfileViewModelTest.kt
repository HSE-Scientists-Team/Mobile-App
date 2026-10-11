package org.matchlab.academic_net.feature.profile.ui.my

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.feature.profile.domain.VerificationOutcome
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.ui.FlakySource
import org.matchlab.academic_net.feature.profile.ui.TestGraph
import org.matchlab.academic_net.feature.profile.ui.TestZone
import org.matchlab.academic_net.feature.profile.ui.installMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.keepCollecting
import org.matchlab.academic_net.feature.profile.ui.mockSource
import org.matchlab.academic_net.feature.profile.ui.uninstallMainDispatcher
import org.matchlab.academic_net.utils.ChatListTime

@OptIn(ExperimentalCoroutinesApi::class)
class MyProfileViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    /** The screen of the tab; it is on the screen unless [visible] is `false`, like the route does. */
    private fun viewModel(graph: TestGraph, visible: Boolean = true) = MyProfileViewModel(
        observeMyProfile = graph.observeMyProfile,
        observeVerificationResults = graph.observeVerificationResults,
        markResultsSeen = graph.markResultsSeen,
        visibility = graph.dependencies().myProfileVisibility,
        clock = graph.dependencies().clock,
        timeZone = { TestZone },
    ).also { if (visible) it.onScreenShown() }

    private fun MyProfileViewModel.content() = assertIs<MyProfileUiState.Content>(uiState.value)

    @Test
    fun showsTheOwnProfileWithWhatIsBeingChecked() = runTest {
        val viewModel = viewModel(TestGraph(mockSource()))
        keepCollecting(viewModel.uiState)

        val content = viewModel.content()

        assertEquals("Алексей Морозов", content.profile.user.name)
        assertFalse(content.profile.canMessage)
        assertEquals(
            listOf(VerificationStatus.Pending, VerificationStatus.Rejected).toSet(),
            content.queue.map { it.status }.toSet(),
        )
        assertEquals(setOf("Сертификат Deep Learning", "Прототип AR-навигации"), content.queue.map { it.title }.toSet())
    }

    @Test
    fun showsTheUnseenDecisionAndMarksItSeen() = runTest {
        val graph = TestGraph(mockSource())
        val viewModel = viewModel(graph)
        keepCollecting(viewModel.uiState)

        val results = viewModel.content().results

        assertEquals(1, results.size)
        assertEquals("Прототип AR-навигации", results.single().title)
        assertEquals(VerificationOutcome.Rejected, results.single().outcome)
        // It was made an hour before "now", the same day.
        assertIs<ChatListTime.Today>(results.single().time)
        // The badge on the tab goes out because the decision has been shown.
        assertFalse(graph.observeHasUnseenResults().first())
    }

    @Test
    fun decisionOfTheModeratorArrivesWhileTheScreenIsOpen() = runTest {
        val graph = TestGraph(mockSource())
        val viewModel = viewModel(graph)
        keepCollecting(viewModel.uiState)

        advanceTimeBy(9.seconds)
        runCurrent()

        val content = viewModel.content()
        // The new decision is first, the old one stays.
        assertEquals(listOf("Сертификат Deep Learning", "Прототип AR-навигации"), content.results.map { it.title })
        assertEquals(VerificationOutcome.Verified, content.results.first().outcome)
        // The certificate left the queue.
        assertEquals(listOf("Прототип AR-навигации"), content.queue.map { it.title })
        assertFalse(graph.observeHasUnseenResults().first())
    }

    @Test
    fun badgeIsOnAndNothingIsMarkedWhileTheScreenIsNotVisible() = runTest {
        val graph = TestGraph(mockSource())

        // Nobody shows the decisions yet: the seeded rejection is news.
        assertTrue(graph.observeHasUnseenResults().first())

        // The view model is alive (it outlives the tab) but the screen is not on the display.
        val viewModel = viewModel(graph, visible = false)
        keepCollecting(viewModel.uiState)

        assertTrue(viewModel.content().results.isEmpty())
        assertTrue(graph.observeHasUnseenResults().first())
        assertTrue(graph.dependencies().observeTabBadge().first())

        // The user opens the tab: the decision is shown and the dot goes out.
        viewModel.onScreenShown()

        assertEquals(1, viewModel.content().results.size)
        assertFalse(graph.observeHasUnseenResults().first())
        assertFalse(graph.dependencies().observeTabBadge().first())
    }

    @Test
    fun decisionThatArrivesAfterTheUserLeftStaysNews() = runTest {
        val graph = TestGraph(mockSource())
        val viewModel = viewModel(graph)
        keepCollecting(viewModel.uiState)
        assertFalse(graph.observeHasUnseenResults().first())

        // The user went to another tab; the view model stays alive for a few seconds more.
        viewModel.onScreenLeft()
        advanceTimeBy(9.seconds)
        runCurrent()

        // The moderator confirmed the certificate in the meantime: the tab shows the dot, and the
        // decision is not marked as seen though somebody is still subscribed to the view model.
        assertTrue(graph.observeHasUnseenResults().first())
        assertTrue(graph.dependencies().observeTabBadge().first())
        assertTrue(viewModel.content().results.isEmpty())
        assertEquals(listOf("Прототип AR-навигации"), viewModel.content().queue.map { it.title })

        // The user comes back: the decision is shown and marked, the dot goes out.
        viewModel.onScreenShown()

        val content = viewModel.content()
        assertEquals(listOf("Сертификат Deep Learning"), content.results.map { it.title })
        assertEquals(VerificationOutcome.Verified, content.results.single().outcome)
        assertFalse(graph.observeHasUnseenResults().first())
        assertFalse(graph.dependencies().observeTabBadge().first())
    }

    @Test
    fun decisionOnTheOpenScreenNeverLightsTheDot() = runTest {
        // A real server answers with a delay: between the decision and "marked seen" the domain
        // flow says "unseen". The tab must not blink in that gap.
        val graph = TestGraph(mockSource(latencyMillis = 300))
        val viewModel = viewModel(graph)
        keepCollecting(viewModel.uiState)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(1, viewModel.content().results.size)

        val domainValues = mutableListOf<Boolean>()
        val badgeValues = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            graph.observeHasUnseenResults().toList(domainValues)
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            graph.dependencies().observeTabBadge().toList(badgeValues)
        }
        assertEquals(listOf(false), badgeValues)

        // The certificate is confirmed at the 8th second.
        advanceTimeBy(8.seconds)
        runCurrent()

        // It is on the screen at once, newest first, and has been marked seen.
        assertEquals(
            listOf("Сертификат Deep Learning", "Прототип AR-навигации"),
            viewModel.content().results.map { it.title },
        )
        assertFalse(graph.observeHasUnseenResults().first())
        // The domain flow did go "unseen" for the latency of the server...
        assertTrue(true in domainValues, "the gap must exist for the test to prove anything: $domainValues")
        // ...and the dot on the tab did not.
        assertEquals(listOf(false), badgeValues)
    }

    @Test
    fun shownDecisionsLeaveWhenTheUserLeavesTheScreen() = runTest {
        val graph = TestGraph(mockSource())
        val viewModel = viewModel(graph)
        keepCollecting(viewModel.uiState)
        assertEquals(1, viewModel.content().results.size)

        viewModel.onScreenLeft()

        val content = viewModel.content()
        assertTrue(content.results.isEmpty())
        // What is being checked stays.
        assertEquals(2, content.queue.size)
    }

    @Test
    fun failureIsAnErrorAndRetryLoadsAgain() = runTest {
        val source = FlakySource(mockSource()).apply { failReads = true }
        val viewModel = viewModel(TestGraph(source))
        keepCollecting(viewModel.uiState)
        assertEquals(MyProfileUiState.Error, viewModel.uiState.value)

        source.failReads = false
        viewModel.onRetry()

        assertIs<MyProfileUiState.Content>(viewModel.uiState.value)
    }
}
