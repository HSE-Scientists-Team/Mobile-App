package org.matchlab.academic_net.feature.projectdetails.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectDetailsUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.InitiatorInfo
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetails
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetailsRepository

/** "04 Проект": loading, the answer of the server, the failure and the retry, on a fake repository with a gate. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailsViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeRepository : ProjectDetailsRepository {
        var answer: ProjectDetails? = null
        var failure: Throwable? = null
        var gate: CompletableDeferred<Unit>? = null
        val asked = mutableListOf<ProjectId>()

        override suspend fun getProjectDetails(id: ProjectId): ProjectDetails? {
            asked += id
            gate?.await()
            failure?.let { throw it }
            return answer
        }
    }

    private val repository = FakeRepository()
    private val cause = IllegalStateException("offline")

    private fun viewModel() =
        ProjectDetailsViewModel(ProjectId("p1"), GetProjectDetailsUseCase(repository))

    private fun details(
        viewerIsInitiator: Boolean = false,
        roles: List<Role> = emptyList(),
        university: String = "МГУ",
        rating: Double? = 4.5,
        completed: Int = 3,
    ) = ProjectDetails(
        project = Project(
            id = ProjectId("p1"),
            title = "AI-навигация",
            type = null,
            sphere = Sphere("Biology", "Biology"),
            problem = "Проблема",
            goal = "Цель",
            tags = listOf("ML", "Robotics"),
            initiator = UserSummary(UserId("owner-1"), "Елена", isVerified = true),
            roles = roles,
            status = null,
            period = null,
            viewerIsInitiator = viewerIsInitiator,
        ),
        initiator = InitiatorInfo(university, rating, completed),
    )

    private fun role(id: String, slots: Int = 1, occupied: Int = 0, skills: List<String> = emptyList()) =
        Role(RoleId(id), "Роль $id", skills, slots, emptyList(), occupied)

    private val ProjectDetailsViewModel.content get() = assertIs<ProjectDetailsUiState.Content>(uiState.value).project

    // ---- the answers ----

    @Test
    fun theProjectIsAskedForWhenTheScreenOpensAndShownAsContent() {
        repository.answer = details()

        val viewModel = viewModel()

        assertEquals(listOf(ProjectId("p1")), repository.asked)
        with(viewModel.content) {
            assertEquals("AI-навигация", title)
            assertEquals("Biology", sphere)
            assertEquals("Проблема", problem)
            assertEquals("Цель", goal)
            assertEquals(listOf("ML", "Robotics"), tags)
        }
    }

    @Test
    fun theScreenIsLoadingUntilTheAnswerComes() {
        repository.answer = details()
        repository.gate = CompletableDeferred()

        val viewModel = viewModel()

        assertEquals(ProjectDetailsUiState.Loading, viewModel.uiState.value)
        repository.gate!!.complete(Unit)
        assertIs<ProjectDetailsUiState.Content>(viewModel.uiState.value)
    }

    @Test
    fun aProjectTheViewerMayNotSeeIsNotFound() {
        repository.answer = null

        assertEquals(ProjectDetailsUiState.NotFound, viewModel().uiState.value)
    }

    @Test
    fun aFailureIsAnErrorWithItsCause() {
        repository.failure = cause

        val state = viewModel().uiState.value

        assertSame(cause, assertIs<ProjectDetailsUiState.Error>(state).cause)
    }

    // ---- what the page shows of the initiator and the places ----

    @Test
    fun theInitiatorIsShownWithTheirFactsAndCanBeWrittenTo() {
        repository.answer = details(university = "НИУ ВШЭ", rating = 4.8, completed = 12)

        with(viewModel().content.initiator) {
            assertEquals(UserSummary(UserId("owner-1"), "Елена", isVerified = true), user)
            assertEquals("owner-1", avatarKey)
            assertEquals("НИУ ВШЭ", university)
            assertEquals(4.8, rating)
            assertEquals(12, completedProjects)
            assertTrue(canContact)
        }
    }

    @Test
    fun nobodyWritesToThemselvesOnTheirOwnProject() {
        repository.answer = details(viewerIsInitiator = true)

        assertFalse(viewModel().content.initiator.canContact)
    }

    @Test
    fun anInitiatorWithoutAnyNumbersHasNoRatingAndABlankUniversity() {
        repository.answer = details(university = "", rating = null, completed = 0)

        with(viewModel().content.initiator) {
            assertEquals("", university)
            assertNull(rating)
            assertEquals(0, completedProjects)
        }
    }

    @Test
    fun theTypeTheStatusAndThePeriodStayUnknown() {
        repository.answer = details()

        with(viewModel().content) {
            assertNull(type)
            assertNull(status)
            assertNull(period)
        }
    }

    @Test
    fun onlyTheRolesWithAFreePlaceAreListedAndAllPlacesAreCounted() {
        repository.answer = details(
            roles = listOf(
                role("full", slots = 1, occupied = 1),
                role("free", slots = 3, occupied = 1, skills = listOf("Python")),
                role("empty", slots = 1, occupied = 0),
                role("over", slots = 2, occupied = 5),
            ),
        )

        with(viewModel().content) {
            assertEquals(listOf(RoleId("free"), RoleId("empty")), vacantRoles.map { it.id })
            assertEquals(
                VacantRoleUiModel(RoleId("free"), "Роль free", listOf("Python"), occupied = 1, slots = 3),
                vacantRoles.first(),
            )
            assertEquals(4, rolesCount, "the roles that are full count too")
            assertEquals(1 + 1 + 0 + 2, occupiedPlaces, "a role is counted up to its places")
            assertEquals(1 + 3 + 1 + 2, totalPlaces)
        }
    }

    @Test
    fun aProjectWithoutRolesHasNoRolesAndNoPlaces() {
        repository.answer = details(roles = emptyList())

        with(viewModel().content) {
            assertEquals(emptyList(), vacantRoles)
            assertEquals(0, rolesCount)
            assertEquals(0, occupiedPlaces)
            assertEquals(0, totalPlaces)
        }
    }

    // ---- the retry ----

    @Test
    fun theRetryAfterAFailureAsksAgainAndShowsTheProject() {
        repository.failure = cause
        val viewModel = viewModel()
        repository.failure = null
        repository.answer = details()

        viewModel.onRetry()

        assertEquals(2, repository.asked.size)
        assertIs<ProjectDetailsUiState.Content>(viewModel.uiState.value)
    }

    @Test
    fun theRetryShowsTheLoadingStateAndIsNotDoubled() {
        repository.failure = cause
        val viewModel = viewModel()
        repository.failure = null
        repository.gate = CompletableDeferred()

        viewModel.onRetry()
        assertEquals(ProjectDetailsUiState.Loading, viewModel.uiState.value)
        viewModel.onRetry()
        viewModel.onRetry()

        assertEquals(2, repository.asked.size)
    }

    @Test
    fun aFailedRetryShowsTheErrorAgain() {
        repository.failure = cause
        val viewModel = viewModel()

        viewModel.onRetry()

        assertIs<ProjectDetailsUiState.Error>(viewModel.uiState.value)
        assertEquals(2, repository.asked.size)
    }

    @Test
    fun theRetryDoesNothingOutsideAnError() {
        repository.answer = details()
        val content = viewModel()
        content.onRetry()
        assertEquals(1, repository.asked.size)

        repository.answer = null
        val notFound = viewModel()
        notFound.onRetry()
        assertEquals(ProjectDetailsUiState.NotFound, notFound.uiState.value)
        assertEquals(2, repository.asked.size, "one more for the second screen, none for the retries")
    }
}
