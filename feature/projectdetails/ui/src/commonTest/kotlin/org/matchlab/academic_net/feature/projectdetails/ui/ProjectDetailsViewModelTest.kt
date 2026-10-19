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
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectDetailsUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectTeamUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.TeamMember
import org.matchlab.academic_net.common.model.ProjectMember
import org.matchlab.academic_net.common.model.RoleMember
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

        /** What the team call answers; [teamFailure] makes it throw. */
        var team: List<TeamMember> = emptyList()
        var teamFailure: Throwable? = null
        var teamGate: CompletableDeferred<Unit>? = null
        val teamAsked = mutableListOf<Project>()

        override suspend fun getTeam(project: Project): List<TeamMember> {
            teamAsked += project
            teamGate?.await()
            teamFailure?.let { throw it }
            return team
        }

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
        ProjectDetailsViewModel(ProjectId("p1"), GetProjectDetailsUseCase(repository), GetProjectTeamUseCase(repository))

    private fun details(
        viewerIsInitiator: Boolean = false,
        roles: List<Role> = emptyList(),
        university: String = "МГУ",
        rating: Double? = 4.5,
        completed: Int = 3,
        sphere: Sphere? = Sphere("7", "Биология"),
        type: ProjectType? = null,
        status: ProjectStatus? = null,
        period: ProjectPeriod? = null,
        members: List<ProjectMember> = emptyList(),
    ) = ProjectDetails(
        project = Project(
            id = ProjectId("p1"),
            title = "AI-навигация",
            type = type,
            sphere = sphere,
            problem = "Проблема",
            goal = "Цель",
            tags = listOf("ML", "Robotics"),
            initiator = UserSummary(UserId("owner-1"), "Елена", isVerified = true),
            roles = roles,
            status = status,
            period = period,
            viewerIsInitiator = viewerIsInitiator,
            members = members,
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
            assertEquals("Биология", sphere)
            assertEquals("Проблема", problem)
            assertEquals("Цель", goal)
            assertEquals(listOf("ML", "Robotics"), tags)
        }
    }

    @Test
    fun aProjectOfUnknownValuesIsShownWithoutThem() {
        repository.answer = details(sphere = null, type = null, status = null, period = null)

        with(viewModel().content) {
            assertNull(sphere)
            assertNull(type)
            assertNull(status)
            assertNull(period)
            assertEquals("AI-навигация", title)
        }
    }

    @Test
    fun theKnownValuesOfAProjectReachThePage() {
        val period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3))
        repository.answer = details(type = ProjectType.Scientific, status = ProjectStatus.Unpublished, period = period)

        with(viewModel().content) {
            assertEquals(ProjectType.Scientific, type)
            assertEquals(ProjectStatus.Unpublished, status)
            assertEquals(period, this.period)
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

    // ---- "Команда" ----

    private fun member(user: String, role: String = "r1", title: String = "Dev", viewer: Boolean = false) =
        ProjectMember(UserId(user), RoleId(role), title, viewer)

    private fun teamMember(
        user: String,
        name: String = "Имя $user",
        title: String = "Dev",
        hasProfile: Boolean = true,
        viewer: Boolean = false,
        rating: Double? = 4.0,
        done: Int = 2,
    ) = TeamMember(
        person = RoleMember(UserSummary(UserId(user), if (hasProfile) name else ""), rating, done, hasProfile),
        roleTitle = title,
        isViewer = viewer,
    )

    private fun ProjectDetailsViewModel.shownTeam() = assertIs<TeamUiState.Shown>(content.team).members

    @Test
    fun aProjectWithoutMembersHasNoTeamAndAsksForNone() {
        repository.answer = details()

        assertEquals(TeamUiState.Hidden, viewModel().content.team)
        assertEquals(emptyList(), repository.teamAsked)
    }

    @Test
    fun aProjectWhoseOnlyMemberIsTheInitiatorHasNoTeamAndAsksForNone() {
        repository.answer = details(members = listOf(member("owner-1")))

        assertEquals(TeamUiState.Hidden, viewModel().content.team)
        assertEquals(emptyList(), repository.teamAsked)
    }

    @Test
    fun theTeamIsLoadingUntilItsPeopleComeAndThePageIsShownMeanwhile() {
        repository.answer = details(members = listOf(member("u1")))
        repository.team = listOf(teamMember("u1"))
        repository.teamGate = CompletableDeferred()

        val viewModel = viewModel()

        assertEquals(TeamUiState.Loading, viewModel.content.team)
        assertEquals("AI-навигация", viewModel.content.title)
        repository.teamGate!!.complete(Unit)
        assertIs<TeamUiState.Shown>(viewModel.content.team)
    }

    @Test
    fun theInitiatorIsFirstFromWhatThePageKnowsAndThenTheMembersInOrder() {
        repository.answer = details(
            university = "МГУ",
            rating = 4.9,
            completed = 7,
            members = listOf(member("owner-1"), member("u1"), member("u2")),
        )
        repository.team = listOf(teamMember("u1", title = "Dev"), teamMember("u2", title = "Дизайнер"))

        val members = viewModel().shownTeam()

        assertEquals(listOf("owner-1", "u1", "u2"), members.map { it.user.id.value })
        val initiator = members.first()
        assertTrue(initiator.isInitiator)
        assertEquals("", initiator.roleTitle)
        assertEquals(4.9, initiator.rating)
        assertEquals(7, initiator.completedProjects)
        assertTrue(initiator.hasProfile)
        assertTrue(initiator.canContact)
        assertEquals(listOf("Dev", "Дизайнер"), members.drop(1).map { it.roleTitle })
        assertTrue(members.drop(1).none { it.isInitiator })
    }

    @Test
    fun theInitiatorCannotBeContactedOnTheirOwnProject() {
        repository.answer = details(viewerIsInitiator = true, members = listOf(member("u1")))
        repository.team = listOf(teamMember("u1"))

        assertFalse(viewModel().shownTeam().first().canContact)
    }

    @Test
    fun aMemberWithoutAProfileHasNoChatAndNoStats() {
        repository.answer = details(members = listOf(member("u1")))
        repository.team = listOf(teamMember("u1", hasProfile = false, rating = null, done = 0))

        val card = viewModel().shownTeam()[1]

        assertFalse(card.hasProfile)
        assertFalse(card.canContact)
        assertEquals("", card.user.name)
        assertNull(card.rating)
    }

    @Test
    fun theViewerOnTheTeamCannotWriteToThemselves() {
        repository.answer = details(members = listOf(member("me", viewer = true), member("u1")))
        repository.team = listOf(teamMember("me", viewer = true), teamMember("u1"))

        val cards = viewModel().shownTeam()

        assertFalse(cards[1].canContact)
        assertTrue(cards[2].canContact)
    }

    @Test
    fun aMemberWithABlankIdCannotBeContacted() {
        repository.answer = details(members = listOf(member("u1")))
        repository.team = listOf(teamMember("  "))

        assertFalse(viewModel().shownTeam()[1].canContact)
    }

    @Test
    fun anEmptyTeamAnswerHidesTheBlock() {
        repository.answer = details(members = listOf(member("u1")))
        repository.team = emptyList()

        assertEquals(TeamUiState.Hidden, viewModel().content.team)
    }

    @Test
    fun aFailureOfTheTeamNeverFailsThePageAndShowsMembersWithoutProfiles() {
        repository.answer = details(members = listOf(member("owner-1"), member("u1", title = "Dev")))
        repository.teamFailure = IllegalStateException("offline")

        val members = viewModel().shownTeam()

        assertEquals(listOf("owner-1", "u1"), members.map { it.user.id.value })
        assertFalse(members[1].hasProfile)
        assertEquals("Dev", members[1].roleTitle)
        assertFalse(members[1].canContact)
    }

    @Test
    fun theInitiatorAndOtherMembersAreFilteredBeforeTheTeamIsAsked() {
        repository.answer = details(members = listOf(member("owner-1"), member("u1"), member("u1"), member("u1", role = "r2")))

        viewModel()

        assertEquals(
            listOf(UserId("u1") to RoleId("r1"), UserId("u1") to RoleId("r2")),
            repository.teamAsked.single().members.map { it.userId to it.roleId },
        )
    }

    @Test
    fun theRetryAfterAFailureReadsThePageAndTheTeamAgain() {
        repository.failure = cause
        val viewModel = viewModel()
        repository.failure = null
        repository.answer = details(members = listOf(member("u1")))
        repository.team = listOf(teamMember("u1"))

        viewModel.onRetry()

        assertEquals(2, repository.asked.size)
        assertEquals(1, repository.teamAsked.size)
        assertEquals(2, viewModel.shownTeam().size)
    }

    @Test
    fun aNotFoundProjectNeverAsksForATeam() {
        repository.answer = null

        viewModel()

        assertEquals(emptyList(), repository.teamAsked)
    }
}
