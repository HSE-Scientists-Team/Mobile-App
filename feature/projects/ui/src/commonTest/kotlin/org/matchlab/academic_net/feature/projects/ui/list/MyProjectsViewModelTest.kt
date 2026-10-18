package org.matchlab.academic_net.feature.projects.ui.list

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.result.FeatureUnavailableException
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveMyProjectsUseCase
import org.matchlab.academic_net.feature.projects.ui.FakeProjectsRepository
import org.matchlab.academic_net.feature.projects.ui.Gate
import org.matchlab.academic_net.feature.projects.ui.caps
import org.matchlab.academic_net.feature.projects.ui.installMainDispatcher
import org.matchlab.academic_net.feature.projects.ui.keepCollecting
import org.matchlab.academic_net.feature.projects.ui.project
import org.matchlab.academic_net.feature.projects.ui.role
import org.matchlab.academic_net.feature.projects.ui.uninstallMainDispatcher

/** "06 Мои проекты", "Я инициатор": what the server cannot list is not asked for. */
class MyProjectsViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val repository = FakeProjectsRepository(caps(canListMine = true))

    private fun TestScope.viewModel(): MyProjectsViewModel {
        val viewModel = MyProjectsViewModel(ObserveMyProjectsUseCase(repository), repository.capabilities)
        keepCollecting(viewModel.uiState)
        return viewModel
    }

    @Test
    fun withoutTheCapabilityTheListIsNotAskedForAndTheScreenSaysItIsUnavailable() = runTest {
        repository.capabilities = caps(canListMine = false)

        val state = viewModel().uiState.value

        assertEquals(0, repository.myProjectsCalls, "API-GAP G44: the use case is not even called")
        assertTrue(state.isListUnavailable)
        assertFalse(state.isLoading)
        assertFalse(state.hasError)
        assertEquals(emptyList(), state.projects)
    }

    @Test
    fun theCreateButtonFollowsTheCapabilityInEveryState() = runTest {
        for (canCreate in listOf(true, false)) {
            for (canListMine in listOf(true, false)) {
                repository.capabilities = caps(canCreate = canCreate, canListMine = canListMine)
                repository.myProjects = flow { throw IllegalStateException("offline") }
                assertEquals(canCreate, viewModel().uiState.value.canCreate, "create=$canCreate list=$canListMine failed")

                repository.myProjects = MutableStateFlow(emptyList())
                assertEquals(canCreate, viewModel().uiState.value.canCreate, "create=$canCreate list=$canListMine loaded")
            }
        }
    }

    @Test
    fun theProjectsAreListedWithWhatTheirCardsShow() = runTest {
        repository.myProjects = MutableStateFlow(
            listOf(project("a", title = "AI-навигация", roles = listOf(role("r1"), role("r2")))),
        )

        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.hasError)
        assertFalse(state.isListUnavailable)
        assertEquals(
            listOf(
                MyProjectItem(
                    id = ProjectId("a"),
                    title = "AI-навигация",
                    type = null,
                    status = null,
                    sphere = "Biology",
                    rolesCount = 2,
                    period = null,
                    isInitiatorVerified = true,
                    avatarKey = "owner-a",
                ),
            ),
            state.projects,
        )
    }

    @Test
    fun theScreenIsLoadingUntilTheFirstListComes() = runTest {
        val gate = Gate()
        repository.myProjects = flow {
            gate.await()
            emit(listOf(project("a")))
        }

        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(emptyList(), viewModel.uiState.value.projects)

        gate.open()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.projects.size)
    }

    @Test
    fun anEmptyListIsNotAnError() = runTest {
        repository.myProjects = MutableStateFlow(emptyList())

        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.hasError)
        assertFalse(state.isListUnavailable)
        assertEquals(emptyList(), state.projects)
    }

    @Test
    fun aNewListReplacesTheOldOne() = runTest {
        val source = MutableStateFlow(listOf(project("a")))
        repository.myProjects = source

        val viewModel = viewModel()
        source.value = listOf(project("b"), project("a"))

        assertEquals(listOf("b", "a"), viewModel.uiState.value.projects.map { it.id.value })
    }

    @Test
    fun theServerSayingItCannotListIsNotAFailure() = runTest {
        repository.myProjects = flow { throw FeatureUnavailableException("G44") }

        val state = viewModel().uiState.value

        assertTrue(state.isListUnavailable)
        assertFalse(state.hasError, "nothing failed: asking again would not help")
        assertFalse(state.isLoading)
    }

    @Test
    fun anyOtherFailureIsAnError() = runTest {
        repository.myProjects = flow { throw IllegalStateException("offline") }

        val state = viewModel().uiState.value

        assertTrue(state.hasError)
        assertFalse(state.isListUnavailable)
        assertFalse(state.isLoading)
        assertEquals(emptyList(), state.projects)
    }
}
