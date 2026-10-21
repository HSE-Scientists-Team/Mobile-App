package org.matchlab.academic_net.feature.profile.ui.my

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.feature.profile.domain.ProfileCapabilities
import org.matchlab.academic_net.feature.profile.domain.VerificationItem
import org.matchlab.academic_net.feature.profile.domain.VerificationOutcome
import org.matchlab.academic_net.feature.profile.domain.VerificationResult
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.feature.profile.domain.usecase.MarkResultsSeenUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveMyProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveVerificationResultsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.RefreshMyProfileUseCase
import org.matchlab.academic_net.feature.profile.ui.FakeMyProfileRepository
import org.matchlab.academic_net.feature.profile.ui.installMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.keepCollecting
import org.matchlab.academic_net.feature.profile.ui.myProfile
import org.matchlab.academic_net.feature.profile.ui.person
import org.matchlab.academic_net.feature.profile.ui.uninstallMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.userProfile
import org.matchlab.academic_net.feature.profile.ui.work
import org.matchlab.academic_net.utils.ChatListTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * "10 Мой профиль": the viewer's profile as state, asked for again when the screen is shown, and (for a server that tells the
 * states of records) the queue of the moderator and the decisions that are shown once and marked seen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyProfileViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val now = Instant.parse("2026-10-12T10:00:00Z")
    private val repository = FakeMyProfileRepository()
    private val visibility = MyProfileVisibility()

    private fun viewModel(
        capabilities: ProfileCapabilities = ProfileCapabilities(verificationStatuses = false, interestTypes = false),
    ) = MyProfileViewModel(
        observeMyProfile = ObserveMyProfileUseCase(repository),
        refreshMyProfile = RefreshMyProfileUseCase(repository),
        observeVerificationResults = ObserveVerificationResultsUseCase(repository),
        markResultsSeen = MarkResultsSeenUseCase(repository),
        visibility = visibility,
        capabilities = capabilities,
        clock = object : Clock {
            override fun now(): Instant = now
        },
        timeZone = { TimeZone.UTC },
    )

    private val withStatuses = ProfileCapabilities(verificationStatuses = true, interestTypes = false)

    private fun MyProfileViewModel.content() = assertIs<MyProfileUiState.Content>(uiState.value)

    private fun decision(id: String, seen: Boolean, decidedAt: Instant = now) = VerificationResult(
        id = VerificationResultId(id),
        subject = VerificationSubject.Work(WorkId("w-$id")),
        subjectTitle = "Работа $id",
        outcome = VerificationOutcome.Rejected,
        decidedAt = decidedAt,
        isSeen = seen,
    )

    // ---- the profile ----

    @Test
    fun theScreenIsLoadingUntilSomebodyWatchesIt() = runTest {
        val viewModel = viewModel()
        assertEquals(MyProfileUiState.Loading, viewModel.uiState.value)

        keepCollecting(viewModel.uiState)

        assertIs<MyProfileUiState.Content>(viewModel.uiState.value)
    }

    @Test
    fun theOwnProfileIsShownAsTheOwnersPage() = runTest {
        repository.profile.value = myProfile(person = person(name = "Арджун"), works = listOf(work("w1")))
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)

        val content = viewModel.content()

        assertEquals("Арджун", content.profile.user.name)
        assertFalse(content.profile.canMessage, "nobody writes to themselves")
        assertFalse(content.needsName)
        assertEquals(listOf("w1"), content.profile.works.map { it.id.value })
    }

    @Test
    fun theOpenToCollaborationFlagReachesTheOwnPage() = runTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)
        assertFalse(viewModel.content().profile.openToCollaboration)

        repository.profile.value = myProfile().copy(profile = userProfile(openToCollaboration = true))

        assertTrue(viewModel.content().profile.openToCollaboration)
    }

    @Test
    fun aNewAccountNeedsAName() = runTest {
        repository.profile.value = myProfile(person = person(name = "", study = Study()))
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)

        assertTrue(viewModel.content().needsName)
    }

    @Test
    fun theScreenFollowsTheProfileWhenItChanges() = runTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)

        repository.profile.value = myProfile(person = person(name = "Новое имя"))

        assertEquals("Новое имя", viewModel.content().profile.user.name)
    }

    @Test
    fun aFailureOfTheFirstLoadIsAnErrorAndRetryObservesAgain() = runTest {
        repository.observeFailure = IllegalStateException("no network")
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)
        // The flow hands over a copy of the exception, so what it says is compared, not the instance.
        val error = assertIs<MyProfileUiState.Error>(viewModel.uiState.value)
        assertEquals("no network", error.cause.message)

        repository.observeFailure = null
        viewModel.onRetry()

        assertIs<MyProfileUiState.Content>(viewModel.uiState.value)
    }

    // ---- asked for again ----

    @Test
    fun theProfileIsAskedForAgainWhenTheScreenIsShown() = runTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)

        viewModel.onScreenShown()

        assertEquals(1, repository.calls.count { it == "refresh" })
        assertTrue(visibility.isVisible.value)
    }

    @Test
    fun theFirstLoadIsNotDoubledByTheScreenBeingShown() = runTest {
        val viewModel = viewModel()

        viewModel.onScreenShown() // nothing is there yet, and the stream does the first load itself

        assertEquals(0, repository.calls.count { it == "refresh" })
        assertTrue(visibility.isVisible.value)
    }

    @Test
    fun aRefreshThatIsStillRunningIsNotStartedAgain() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.onRefresh = { gate.await() }
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)

        viewModel.onScreenShown()
        viewModel.onScreenShown()
        viewModel.onScreenShown()
        gate.complete(Unit)
        viewModel.onScreenShown()

        assertEquals(2, repository.calls.count { it == "refresh" }, "one while running, one after it ended")
    }

    @Test
    fun aFailedRefreshIsNotToldAndTheProfileStays() = runTest {
        repository.profile.value = myProfile(person = person(name = "Арджун"))
        repository.onRefresh = { throw IllegalStateException("no network") }
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)

        viewModel.onScreenShown()

        assertEquals("Арджун", viewModel.content().profile.user.name)
    }

    // ---- without the states of the records (the Gateway) ----

    @Test
    fun withoutStatesThereIsNoQueueAndNoResultsWhateverTheDataSays() = runTest {
        repository.profile.value = myProfile(
            queue = listOf(VerificationItem(VerificationSubject.Work(WorkId("w1")), "Работа", VerificationStatus.Pending)),
        )
        repository.results = MutableStateFlow(listOf(decision("1", seen = false)))
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)
        viewModel.onScreenShown()

        val content = viewModel.content()

        assertEquals(emptyList(), content.queue)
        assertEquals(emptyList(), content.results)
        assertEquals(emptyList(), repository.seen, "nothing is marked seen")
    }

    @Test
    fun withoutStatesTheResultsAreNotEvenObserved() = runTest {
        var observed = false
        repository.results = flow {
            observed = true
            emit(emptyList())
        }
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)
        viewModel.onScreenShown()

        assertFalse(observed)
    }

    // ---- with the states of the records ----

    @Test
    fun theQueueOfTheModeratorIsShownWhenTheServerTellsTheStates() = runTest {
        val queue = listOf(VerificationItem(VerificationSubject.Work(WorkId("w1")), "Работа", VerificationStatus.Pending))
        repository.profile.value = myProfile(queue = queue)
        val viewModel = viewModel(withStatuses)
        keepCollecting(viewModel.uiState)

        assertEquals(queue, viewModel.content().queue)
    }

    @Test
    fun anUnseenDecisionIsShownAndMarkedSeenWhileTheScreenIsVisible() = runTest {
        repository.results = MutableStateFlow(listOf(decision("1", seen = false)))
        val viewModel = viewModel(withStatuses)
        keepCollecting(viewModel.uiState)

        viewModel.onScreenShown()

        val shown = viewModel.content().results.single()
        assertEquals(VerificationResultId("1"), shown.id)
        assertEquals("Работа 1", shown.title)
        assertEquals(VerificationOutcome.Rejected, shown.outcome)
        assertIs<ChatListTime.Today>(shown.time)
        assertEquals(listOf(setOf(VerificationResultId("1"))), repository.seen)
    }

    @Test
    fun aDecisionThatCameWhileTheScreenWasNotVisibleStaysNewsUntilItIs() = runTest {
        repository.results = MutableStateFlow(listOf(decision("1", seen = false)))
        val viewModel = viewModel(withStatuses)
        keepCollecting(viewModel.uiState)

        assertEquals(emptyList(), viewModel.content().results)
        assertEquals(emptyList(), repository.seen, "the user has not looked at it")

        viewModel.onScreenShown()

        assertEquals(1, viewModel.content().results.size)
        assertEquals(1, repository.seen.size)
    }

    @Test
    fun aDecisionIsMarkedSeenOnlyOnce() = runTest {
        val results = MutableStateFlow(listOf(decision("1", seen = false)))
        repository.results = results
        val viewModel = viewModel(withStatuses)
        keepCollecting(viewModel.uiState)
        viewModel.onScreenShown()

        // The server has marked it by now, and the list emits again with the same decision, now seen.
        results.value = listOf(decision("1", seen = true))
        // The profile changes too: the combined stream emits again.
        repository.profile.value = myProfile(person = person(name = "Другое имя"))

        assertEquals(1, repository.seen.size)
        assertEquals(1, viewModel.content().results.size, "a decision that was shown stays until the user leaves")
    }

    @Test
    fun leavingTheScreenClearsTheDecisionsThatWereShown() = runTest {
        repository.results = MutableStateFlow(listOf(decision("1", seen = false)))
        val viewModel = viewModel(withStatuses)
        keepCollecting(viewModel.uiState)
        viewModel.onScreenShown()
        assertEquals(1, viewModel.content().results.size)

        viewModel.onScreenLeft()

        assertEquals(emptyList(), viewModel.content().results)
        assertFalse(visibility.isVisible.value)
    }

    @Test
    fun aDecisionThatArrivesWhileTheScreenIsOpenIsShownAtOnce() = runTest {
        val results = MutableStateFlow(emptyList<VerificationResult>())
        repository.results = results
        val viewModel = viewModel(withStatuses)
        keepCollecting(viewModel.uiState)
        viewModel.onScreenShown()
        assertEquals(emptyList(), viewModel.content().results)

        results.value = listOf(decision("2", seen = false))

        assertEquals(listOf(VerificationResultId("2")), viewModel.content().results.map { it.id })
        assertEquals(1, repository.seen.size)
    }

    @Test
    fun aDecisionOfYesterdayIsTold() = runTest {
        val yesterday = Instant.parse("2026-10-11T12:00:00Z")
        repository.results = MutableStateFlow(listOf(decision("1", seen = false, decidedAt = yesterday)))
        val viewModel = viewModel(withStatuses)
        keepCollecting(viewModel.uiState)

        viewModel.onScreenShown()

        assertEquals(ChatListTime.Yesterday, viewModel.content().results.single().time)
    }

    @Test
    fun aFailedMarkingDoesNotHideTheDecision() = runTest {
        repository.results = MutableStateFlow(listOf(decision("1", seen = false)))
        repository.onMarkSeen = { throw IllegalStateException("no network") }
        val viewModel = viewModel(withStatuses)
        keepCollecting(viewModel.uiState)

        viewModel.onScreenShown()

        assertEquals(1, viewModel.content().results.size)
    }
}
