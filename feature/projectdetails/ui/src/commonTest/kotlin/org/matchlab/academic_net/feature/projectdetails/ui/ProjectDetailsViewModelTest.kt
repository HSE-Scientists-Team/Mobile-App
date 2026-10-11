package org.matchlab.academic_net.feature.projectdetails.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.feature.projectdetails.data.MockProjectDetailsDataSource
import org.matchlab.academic_net.feature.projectdetails.data.ProjectDetailsRepositoryImpl
import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectDetailsUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetails
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetailsRepository
import org.matchlab.academic_net.mockserver.MockProjectCatalog
import org.matchlab.academic_net.mockserver.MockServerProject

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailsViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** The mock server behind a spy: counts the requests, can fail and can hold a request. */
    private class SpyRepository(catalog: List<MockServerProject> = MockProjectCatalog.all) : ProjectDetailsRepository {
        private val server = ProjectDetailsRepositoryImpl(MockProjectDetailsDataSource(catalog, latencyMillis = 0))
        var calls = 0
        var failing = false

        /** While set, a request waits until it is completed. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun getProjectDetails(id: ProjectId): ProjectDetails? {
            calls++
            gate?.await()
            if (failing) error("no network")
            return server.getProjectDetails(id)
        }
    }

    /** "AI-навигация по кампусу": 2 of its 4 roles are taken, the initiator has 4.9 and 12 projects. */
    private val campus = MockProjectCatalog.all.first()
    private val campusId = campus.project.id

    private fun viewModel(repository: ProjectDetailsRepository, id: ProjectId = campusId) =
        ProjectDetailsViewModel(id, GetProjectDetailsUseCase(repository))

    private fun content(viewModel: ProjectDetailsViewModel): ProjectDetailsUiModel =
        assertIs<ProjectDetailsUiState.Content>(viewModel.uiState.value).project

    /** The states the view model goes through, in order. */
    private fun TestScope.recordStates(viewModel: ProjectDetailsViewModel): List<ProjectDetailsUiState> {
        val states = mutableListOf<ProjectDetailsUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }
        return states
    }

    @Test
    fun loadsTheProjectWhenCreated() = runTest {
        val repository = SpyRepository().apply { gate = CompletableDeferred() }
        val viewModel = viewModel(repository)
        val states = recordStates(viewModel)

        assertEquals(ProjectDetailsUiState.Loading, viewModel.uiState.value)

        repository.gate?.complete(Unit)

        assertEquals(2, states.size)
        assertEquals(ProjectDetailsUiState.Loading, states[0])
        assertIs<ProjectDetailsUiState.Content>(states[1])
        assertEquals(1, repository.calls)
    }

    @Test
    fun mapsTheProjectAndItsInitiator() = runTest {
        val project = content(viewModel(SpyRepository()))

        assertEquals("AI-навигация по кампусу", project.title)
        assertEquals(ProjectType.Scientific, project.type)
        assertEquals(ProjectStatus.Recruiting, project.status)
        assertEquals("Computer Science", project.sphere)
        assertEquals(campus.project.problem, project.problem)
        assertEquals(campus.project.goal, project.goal)
        assertEquals(listOf("Machine Learning", "Computer Vision", "Mobile Dev"), project.tags)
        assertEquals(campus.project.period, project.period)

        val initiator = project.initiator
        assertEquals(campus.project.initiator, initiator.user)
        assertEquals("user-elena", initiator.avatarKey)
        assertEquals("Stanford", initiator.university)
        assertEquals(4.9, initiator.rating)
        assertEquals(12, initiator.completedProjects)
        assertTrue(initiator.canContact)
    }

    @Test
    fun showsOnlyTheVacantRolesAndCountsTheFilledOnes() = runTest {
        val project = content(viewModel(SpyRepository()))

        assertEquals(
            listOf("Mobile Developer", "UX Researcher"),
            project.vacantRoles.map { it.title },
        )
        assertEquals(listOf("Kotlin", "Android"), project.vacantRoles.first().skills)
        assertEquals(
            campus.project.roles.filter { it.isVacant }.map { it.id },
            project.vacantRoles.map { it.id },
        )
        assertEquals(2, project.occupiedRolesCount)
        assertEquals(4, project.rolesCount)
    }

    @Test
    fun hasNoVacantRolesWhenEveryRoleIsTaken() = runTest {
        val member = campus.project.roles.first { it.member != null }.member
        val takenRoles = campus.project.roles.map { role -> role.copy(member = role.member ?: member) }
        val taken = campus.copy(project = campus.project.copy(roles = takenRoles))

        val project = content(viewModel(SpyRepository(listOf(taken))))

        assertTrue(project.vacantRoles.isEmpty())
        assertEquals(4, project.occupiedRolesCount)
        assertEquals(4, project.rolesCount)
    }

    @Test
    fun hasNoRolesAtAllWhenTheProjectHasNone() = runTest {
        val bare = campus.copy(project = campus.project.copy(roles = emptyList()))

        val project = content(viewModel(SpyRepository(listOf(bare))))

        assertTrue(project.vacantRoles.isEmpty())
        assertEquals(0, project.rolesCount)
    }

    @Test
    fun keepsTheRatingEmptyWhenThereAreNoReviews() = runTest {
        val unrated = campus.copy(initiatorRating = null, initiatorCompletedProjects = 0)

        val initiator = content(viewModel(SpyRepository(listOf(unrated)))).initiator

        assertEquals(null, initiator.rating)
        assertEquals(0, initiator.completedProjects)
    }

    @Test
    fun cannotContactTheInitiatorOfTheViewersOwnProject() = runTest {
        val own = campus.copy(project = campus.project.copy(viewerIsInitiator = true))

        val project = content(viewModel(SpyRepository(listOf(own))))

        assertFalse(project.initiator.canContact)
        // The rest of the page is the same: the project is shown read-only.
        assertEquals(campus.project.title, project.title)
    }

    @Test
    fun showsNotFoundForAnUnknownProject() = runTest {
        val viewModel = viewModel(SpyRepository(), ProjectId("project-unknown"))

        assertEquals(ProjectDetailsUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun showsNotFoundForAnUnpublishedProject() = runTest {
        val unpublished = campus.copy(project = campus.project.copy(status = ProjectStatus.Unpublished))

        val viewModel = viewModel(SpyRepository(listOf(unpublished)))

        assertEquals(ProjectDetailsUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun showsErrorWhenTheRequestFails() = runTest {
        val viewModel = viewModel(SpyRepository().apply { failing = true })

        assertEquals(ProjectDetailsUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun retryLoadsTheProjectAfterAnError() = runTest {
        val repository = SpyRepository().apply { failing = true }
        val viewModel = viewModel(repository)
        val states = recordStates(viewModel)
        assertEquals(ProjectDetailsUiState.Error, viewModel.uiState.value)

        repository.failing = false
        viewModel.onRetry()

        assertEquals(
            listOf(
                ProjectDetailsUiState.Error,
                ProjectDetailsUiState.Loading,
            ),
            states.take(2),
        )
        assertIs<ProjectDetailsUiState.Content>(states.last())
        assertEquals(2, repository.calls)
    }

    @Test
    fun retryWhileLoadingDoesNotAskTwice() = runTest {
        val repository = SpyRepository().apply { gate = CompletableDeferred() }
        val viewModel = viewModel(repository)

        viewModel.onRetry()
        viewModel.onRetry()

        assertEquals(1, repository.calls)
        assertEquals(ProjectDetailsUiState.Loading, viewModel.uiState.value)

        repository.gate?.complete(Unit)

        assertIs<ProjectDetailsUiState.Content>(viewModel.uiState.value)
        assertEquals(1, repository.calls)
    }
}
