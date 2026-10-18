package org.matchlab.academic_net.feature.profile.ui.user

import kotlinx.coroutines.CompletableDeferred
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.profile.domain.UserWorks
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserWorksUseCase
import org.matchlab.academic_net.feature.profile.ui.FakeProfileRepository
import org.matchlab.academic_net.feature.profile.ui.SomeoneElse
import org.matchlab.academic_net.feature.profile.ui.Viewer
import org.matchlab.academic_net.feature.profile.ui.installMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.uninstallMainDispatcher
import org.matchlab.academic_net.feature.profile.ui.work
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** "09e Все работы": the list of a person, and the viewer's own list that is asked for again after the form of a work. */
class UserWorksViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val repository = FakeProfileRepository()

    private fun viewModel(id: UserId = SomeoneElse) = UserWorksViewModel(id, GetUserWorksUseCase(repository))

    private fun UserWorksViewModel.content() = assertIs<UserWorksUiState.Content>(uiState.value)

    @Test
    fun theWorksOfAPersonAreLoadedOnce() {
        repository.onGetWorks = { UserWorks(listOf(work("w1"), work("w2")), isViewer = false) }

        val content = viewModel().content()

        assertEquals(listOf("w1", "w2"), content.works.map { it.id.value })
        assertEquals(false, content.isViewer)
        assertEquals(listOf("getWorks"), repository.calls)
    }

    @Test
    fun aPersonWithoutWorksHasAnEmptyListNotAnError() {
        assertEquals(emptyList(), viewModel().content().works)
    }

    @Test
    fun aPersonTheServerDoesNotKnowIsNotFound() {
        repository.onGetWorks = { null }

        assertEquals(UserWorksUiState.NotFound, viewModel().uiState.value)
    }

    @Test
    fun aFailureIsAnErrorAndRetryAsksAgain() {
        val cause = IllegalStateException("no network")
        repository.onGetWorks = { throw cause }
        val viewModel = viewModel()
        assertEquals(UserWorksUiState.Error(cause), viewModel.uiState.value)

        repository.onGetWorks = { UserWorks(listOf(work()), isViewer = false) }
        viewModel.onRetry()

        assertEquals(1, viewModel.content().works.size)
        assertEquals(2, repository.calls.size)
    }

    @Test
    fun retryOnALoadedListAsksNothing() {
        val viewModel = viewModel()

        viewModel.onRetry()

        assertEquals(1, repository.calls.size)
    }

    // ---- the viewer's own list ----

    @Test
    fun theViewersListIsAskedForAgainWhenTheScreenIsShownAgain() {
        repository.onGetWorks = { UserWorks(listOf(work("w1")), isViewer = true) }
        val viewModel = viewModel(Viewer)

        repository.onGetWorks = { UserWorks(listOf(work("w1"), work("w2")), isViewer = true) }
        viewModel.onScreenShown()

        assertEquals(listOf("w1", "w2"), viewModel.content().works.map { it.id.value })
        assertEquals(2, repository.calls.size)
    }

    @Test
    fun theListOfSomebodyElseIsNeverAskedForAgain() {
        repository.onGetWorks = { UserWorks(listOf(work("w1")), isViewer = false) }
        val viewModel = viewModel()

        viewModel.onScreenShown()
        viewModel.onScreenShown()

        assertEquals(1, repository.calls.size)
    }

    @Test
    fun theFirstLoadIsNotDoubledByTheScreenBeingShown() {
        val gate = CompletableDeferred<Unit>()
        repository.onGetWorks = { gate.await(); UserWorks(listOf(work()), isViewer = true) }
        val viewModel = viewModel(Viewer)

        viewModel.onScreenShown() // the list is not there yet
        gate.complete(Unit)

        assertEquals(1, repository.calls.size)
    }

    @Test
    fun aRefreshThatIsStillRunningIsNotStartedAgain() {
        repository.onGetWorks = { UserWorks(listOf(work("w1")), isViewer = true) }
        val viewModel = viewModel(Viewer)
        val gate = CompletableDeferred<Unit>()
        repository.onGetWorks = { gate.await(); UserWorks(listOf(work("w1"), work("w2")), isViewer = true) }

        viewModel.onScreenShown()
        viewModel.onScreenShown()
        viewModel.onScreenShown()
        gate.complete(Unit)

        assertEquals(2, repository.calls.size, "one load and one refresh")
        assertEquals(2, viewModel.content().works.size)
    }

    @Test
    fun aFailedRefreshKeepsTheListAndIsNotTold() {
        repository.onGetWorks = { UserWorks(listOf(work("w1")), isViewer = true) }
        val viewModel = viewModel(Viewer)

        repository.onGetWorks = { throw IllegalStateException("no network") }
        viewModel.onScreenShown()

        assertEquals(listOf("w1"), viewModel.content().works.map { it.id.value })
    }

    @Test
    fun aRefreshThatFindsNobodyKeepsTheListToo() {
        repository.onGetWorks = { UserWorks(listOf(work("w1")), isViewer = true) }
        val viewModel = viewModel(Viewer)

        repository.onGetWorks = { null }
        viewModel.onScreenShown()

        assertEquals(listOf("w1"), viewModel.content().works.map { it.id.value })
    }

    @Test
    fun aRefreshIsAskedAgainAfterTheLastOneEnded() {
        repository.onGetWorks = { UserWorks(listOf(work("w1")), isViewer = true) }
        val viewModel = viewModel(Viewer)

        viewModel.onScreenShown()
        viewModel.onScreenShown()

        assertEquals(3, repository.calls.size)
    }

    // ---- a work that came twice ----

    // The list of the screen is keyed by the id of a work, and a key used twice crashes it: every id is there once.

    @Test
    fun aWorkThatCameTwiceIsShownOnceInTheOrderOfItsFirstPlace() {
        repository.onGetWorks = { UserWorks(listOf(work("w1"), work("w2"), work("w1")), isViewer = false) }

        val content = viewModel().content()

        assertEquals(listOf("w1", "w2"), content.works.map { it.id.value })
        assertEquals(false, content.isViewer)
    }

    @Test
    fun aListOfTheSameWorkIsOneWork() {
        repository.onGetWorks = { UserWorks(listOf(work("w1"), work("w1"), work("w1")), isViewer = false) }

        assertEquals(listOf("w1"), viewModel().content().works.map { it.id.value })
    }

    @Test
    fun worksThatLookAlikeButHaveOtherIdsAreAllShown() {
        repository.onGetWorks = {
            UserWorks(listOf(work("w1", title = "Одно"), work("w2", title = "Одно"), work("w3", title = "Одно")), isViewer = false)
        }

        assertEquals(listOf("w1", "w2", "w3"), viewModel().content().works.map { it.id.value })
    }

    @Test
    fun aWorkThatCameTwiceIsShownOnceAfterRetryToo() {
        repository.onGetWorks = { throw IllegalStateException("no network") }
        val viewModel = viewModel()

        repository.onGetWorks = { UserWorks(listOf(work("w1"), work("w1"), work("w2")), isViewer = false) }
        viewModel.onRetry()

        assertEquals(listOf("w1", "w2"), viewModel.content().works.map { it.id.value })
    }

    @Test
    fun aWorkThatCameTwiceInTheRefreshOfTheViewersListIsShownOnce() {
        repository.onGetWorks = { UserWorks(listOf(work("w1")), isViewer = true) }
        val viewModel = viewModel(Viewer)

        repository.onGetWorks = { UserWorks(listOf(work("w2"), work("w1"), work("w2"), work("w3"), work("w1")), isViewer = true) }
        viewModel.onScreenShown()

        val content = viewModel.content()
        assertEquals(listOf("w2", "w1", "w3"), content.works.map { it.id.value })
        assertEquals(true, content.isViewer)
        assertEquals(2, repository.calls.size)
    }

    @Test
    fun aWorkThatCameTwiceInTheFirstLoadOfTheViewersListIsShownOnce() {
        repository.onGetWorks = { UserWorks(listOf(work("w1"), work("w1")), isViewer = true) }

        val content = viewModel(Viewer).content()

        assertEquals(listOf("w1"), content.works.map { it.id.value })
        assertEquals(true, content.isViewer)
    }
}
