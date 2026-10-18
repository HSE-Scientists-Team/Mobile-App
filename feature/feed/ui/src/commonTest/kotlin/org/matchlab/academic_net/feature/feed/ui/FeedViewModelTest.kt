package org.matchlab.academic_net.feature.feed.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.feed.data.FeedDataSource
import org.matchlab.academic_net.feature.feed.data.FeedRepositoryImpl
import org.matchlab.academic_net.feature.feed.data.mock.MockFeedDataSource
import org.matchlab.academic_net.feature.feed.domain.usecase.GetRecommendedPeopleUseCase
import org.matchlab.academic_net.feature.feed.domain.usecase.GetViewerUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Mock data source whose answers can be switched off like a dead network. */
    private class SwitchableDataSource(
        private val delegate: FeedDataSource = MockFeedDataSource(latencyMillis = 0),
    ) : FeedDataSource {
        var peopleFail = false
        var viewerFail = false

        override suspend fun getViewer(): UserSummary =
            if (viewerFail) error("offline") else delegate.getViewer()

        override suspend fun getRecommendedPeople(): List<Collaborator> =
            if (peopleFail) error("offline") else delegate.getRecommendedPeople()
    }

    /** A server that does not recommend anyone until the test lets it, and counts the requests. */
    private class GatedDataSource(
        private val delegate: FeedDataSource = MockFeedDataSource(latencyMillis = 0),
    ) : FeedDataSource by delegate {
        private val gate = CompletableDeferred<Unit>()
        var peopleRequests = 0
            private set

        fun answer() {
            gate.complete(Unit)
        }

        override suspend fun getRecommendedPeople(): List<Collaborator> {
            peopleRequests++
            gate.await()
            return delegate.getRecommendedPeople()
        }
    }

    private fun viewModel(dataSource: FeedDataSource = SwitchableDataSource()): FeedViewModel {
        val repository = FeedRepositoryImpl(dataSource)
        return FeedViewModel(GetViewerUseCase(repository), GetRecommendedPeopleUseCase(repository))
    }

    @Test
    fun opensOnTheProjectsScope() = runTest {
        assertEquals(FeedScope.Projects, viewModel().uiState.value.scope)
    }

    @Test
    fun scopeCanBeSwitched() = runTest {
        val viewModel = viewModel()

        viewModel.onScopeSelect(FeedScope.People)
        assertEquals(FeedScope.People, viewModel.uiState.value.scope)

        viewModel.onScopeSelect(FeedScope.Projects)
        assertEquals(FeedScope.Projects, viewModel.uiState.value.scope)
    }

    @Test
    fun recommendedPeopleAreShownWithTheirSubtitles() = runTest {
        val state = viewModel().uiState.value

        assertFalse(state.isPeopleLoading)
        assertFalse(state.peopleFailed)
        assertEquals(listOf("Арджун Мехта", "Мария Соколова", "Даниил Крюков"), state.people.map { it.user.name })
        assertEquals("МГУ · аспирантка · биоинформатика", state.people[1].subtitle)
    }

    @Test
    fun viewerAvatarKeyIsTheViewersId() = runTest {
        assertEquals("user-me", viewModel().uiState.value.viewerAvatarKey)
    }

    @Test
    fun failedViewerLeavesTheDefaultAvatar() = runTest {
        val dataSource = SwitchableDataSource().apply { viewerFail = true }

        val state = viewModel(dataSource).uiState.value

        assertNull(state.viewerAvatarKey)
        assertEquals(3, state.people.size)
    }

    @Test
    fun failedPeopleCanBeRetried() = runTest {
        val dataSource = SwitchableDataSource().apply { peopleFail = true }
        val viewModel = viewModel(dataSource)
        assertTrue(viewModel.uiState.value.peopleFailed)
        assertFalse(viewModel.uiState.value.isPeopleLoading)
        assertTrue(viewModel.uiState.value.people.isEmpty())

        dataSource.peopleFail = false
        viewModel.onPeopleRetry()

        val state = viewModel.uiState.value
        assertFalse(state.peopleFailed)
        assertEquals(3, state.people.size)
    }

    @Test
    fun retryWhilePeopleAreLoadingAsksOnce() = runTest {
        val dataSource = GatedDataSource()
        val viewModel = viewModel(dataSource)
        assertTrue(viewModel.uiState.value.isPeopleLoading)

        viewModel.onPeopleRetry()
        viewModel.onPeopleRetry()
        dataSource.answer()

        assertEquals(1, dataSource.peopleRequests)
        val state = viewModel.uiState.value
        assertFalse(state.isPeopleLoading)
        assertEquals(3, state.people.size)
    }
}
