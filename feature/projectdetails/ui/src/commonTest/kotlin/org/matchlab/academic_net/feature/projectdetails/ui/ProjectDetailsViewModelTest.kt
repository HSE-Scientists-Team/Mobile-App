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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.projectdetails.domain.LeaveNotAllowedException
import org.matchlab.academic_net.feature.projectdetails.domain.LeaveProjectUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.ObserveAppliedRolesUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectGoneException
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
    fun tearDown() {
        collectors.cancel()
        Dispatchers.resetMain()
    }

    /** Keeps the state of every view model of a test collected, the way a screen does (the state is `WhileSubscribed`). */
    private val collectors = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())

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

        /** The calls of [leaveProject]; [leaveFailure] fails it, [leaveGate] holds it back, [onLeave] changes the server. */
        val left = mutableListOf<ProjectId>()
        var leaveFailure: Throwable? = null
        var leaveGate: CompletableDeferred<Unit>? = null
        var onLeave: () -> Unit = {}

        override suspend fun leaveProject(id: ProjectId) {
            left += id
            leaveGate?.await()
            leaveFailure?.let { throw it }
            onLeave()
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

    /** The roles the viewer has applied to, as the data layer's list of records tells. */
    private val appliedRoles = MutableStateFlow<Set<RoleId>>(emptySet())
    private var appliedSource: (ProjectId) -> Flow<Set<RoleId>> = { appliedRoles }

    private fun viewModel(): ProjectDetailsViewModel {
        val viewModel = ProjectDetailsViewModel(
            ProjectId("p1"),
            GetProjectDetailsUseCase(repository),
            GetProjectTeamUseCase(repository),
            ObserveAppliedRolesUseCase { appliedSource(it) },
            LeaveProjectUseCase(repository),
        )
        collectors.launch { viewModel.uiState.collect {} }
        return viewModel
    }

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

    // ---- "Откликнуться" and "Вы откликнулись" ----

    private val ProjectDetailsViewModel.contentState get() = assertIs<ProjectDetailsUiState.Content>(uiState.value)

    @Test
    fun applyingIsOfferedToAnOutsiderOfARecruitingOrUnknownStatusProject() {
        for (status in listOf(ProjectStatus.Recruiting, null)) {
            repository.answer = details(status = status)
            assertTrue(viewModel().content.canApply, "status $status")
        }
    }

    @Test
    fun applyingIsNotOfferedForAnyOtherStatus() {
        for (status in listOf(ProjectStatus.Unpublished, ProjectStatus.Started, ProjectStatus.Finished, ProjectStatus.Cancelled)) {
            repository.answer = details(status = status)
            assertFalse(viewModel().content.canApply, "status $status")
        }
    }

    @Test
    fun applyingIsNotOfferedToTheInitiatorOrToAMember() {
        repository.answer = details(viewerIsInitiator = true, status = ProjectStatus.Recruiting)
        assertFalse(viewModel().content.canApply)

        repository.answer = details(status = ProjectStatus.Recruiting, members = listOf(member("me", viewer = true)))
        assertFalse(viewModel().content.canApply)

        repository.answer = details(status = ProjectStatus.Recruiting, members = listOf(member("other")))
        assertTrue(viewModel().content.canApply, "somebody else on the team does not matter")
    }

    @Test
    fun theAppliedRolesComeFromTheListOfRecordsAndFollowIt() {
        repository.answer = details(roles = listOf(role("r1"), role("r2")))
        val viewModel = viewModel()
        assertEquals(emptySet(), viewModel.content.appliedRoles)

        appliedRoles.value = setOf(RoleId("r2"))
        assertEquals(setOf(RoleId("r2")), viewModel.content.appliedRoles)

        appliedRoles.value = emptySet()
        assertEquals(emptySet(), viewModel.content.appliedRoles)
    }

    @Test
    fun anAppliedRoleBeforeThePageArrivesIsThereWhenItDoes() {
        appliedRoles.value = setOf(RoleId("r1"))
        repository.gate = CompletableDeferred()
        repository.answer = details(roles = listOf(role("r1")))
        val viewModel = viewModel()
        assertEquals(ProjectDetailsUiState.Loading, viewModel.uiState.value)

        repository.gate!!.complete(Unit)

        assertEquals(setOf(RoleId("r1")), viewModel.content.appliedRoles)
    }

    @Test
    fun aFailureOfTheAppliedRolesNeverFailsThePage() {
        appliedSource = { flow { throw RemoteFailure.NoConnection() } }
        repository.answer = details()

        val viewModel = viewModel()

        assertEquals(emptySet(), viewModel.content.appliedRoles)
    }

    @Test
    fun theFlagToReportIsOfferedToEveryoneButTheInitiator() {
        repository.answer = details(viewerIsInitiator = false)
        assertTrue(viewModel().content.canReport)

        repository.answer = details(viewerIsInitiator = true)
        assertFalse(viewModel().content.canReport)
    }

    // ---- "Покинуть проект" ----

    private fun asMember() {
        repository.answer = details(members = listOf(member("me", viewer = true), member("u2")))
        repository.team = listOf(teamMember("me", viewer = true), teamMember("u2"))
    }

    @Test
    fun leavingIsOfferedToAMemberWhoIsNotTheInitiator() {
        asMember()
        assertTrue(viewModel().content.canLeave)

        repository.answer = details(viewerIsInitiator = true, members = listOf(member("me", viewer = true)))
        assertFalse(viewModel().content.canLeave)

        repository.answer = details(members = listOf(member("u2")))
        assertFalse(viewModel().content.canLeave)
    }

    @Test
    fun leavingWhenNotOfferedOpensNoDialog() {
        repository.answer = details()
        val viewModel = viewModel()

        viewModel.onLeaveClick()
        viewModel.onLeaveConfirm()

        assertFalse(viewModel.contentState.isLeaveConfirmVisible)
        assertEquals(emptyList(), repository.left)
    }

    @Test
    fun theDialogOpensAndClosesWithoutLeaving() {
        asMember()
        val viewModel = viewModel()

        viewModel.onLeaveClick()
        assertTrue(viewModel.contentState.isLeaveConfirmVisible)
        viewModel.onLeaveDismiss()

        assertFalse(viewModel.contentState.isLeaveConfirmVisible)
        assertEquals(emptyList(), repository.left)
    }

    @Test
    fun confirmingWithoutTheDialogDoesNothing() {
        asMember()
        val viewModel = viewModel()

        viewModel.onLeaveConfirm()

        assertEquals(emptyList(), repository.left)
        assertFalse(viewModel.contentState.isLeaving)
    }

    @Test
    fun leavingReadsThePageAgainAndShowsTheBanner() {
        asMember()
        repository.onLeave = { repository.answer = details(members = listOf(member("u2"))) }
        val viewModel = viewModel()
        viewModel.onLeaveClick()

        viewModel.onLeaveConfirm()

        assertEquals(listOf(ProjectId("p1")), repository.left)
        assertEquals(2, repository.asked.size, "the page is read again")
        with(viewModel.contentState) {
            assertTrue(hasLeft)
            assertFalse(isLeaving)
            assertFalse(isLeaveConfirmVisible)
            assertNull(leaveFailure)
            assertFalse(project.canLeave, "the viewer is not on the team now")
        }
    }

    @Test
    fun leavingIsGuardedWhileItRunsAndAfterIt() {
        asMember()
        repository.leaveGate = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.onLeaveClick()

        viewModel.onLeaveConfirm()
        assertTrue(viewModel.contentState.isLeaving)
        viewModel.onLeaveConfirm()
        viewModel.onLeaveClick()
        assertFalse(viewModel.contentState.isLeaveConfirmVisible, "no new dialog while leaving")
        repository.leaveGate!!.complete(Unit)
        viewModel.onLeaveClick()
        viewModel.onLeaveConfirm()

        assertEquals(1, repository.left.size)
        assertFalse(viewModel.contentState.isLeaveConfirmVisible, "no dialog after the viewer has left")
    }

    @Test
    fun theGuardHoldsWhileThePageIsReadAgain() {
        asMember()
        val reread = CompletableDeferred<Unit>()
        val viewModel = viewModel()
        viewModel.onLeaveClick()
        repository.onLeave = { repository.gate = reread }

        viewModel.onLeaveConfirm()

        with(viewModel.contentState) {
            assertTrue(hasLeft)
            assertTrue(isLeaving, "the arc stays until the page is read")
        }
        reread.complete(Unit)
        assertFalse(viewModel.contentState.isLeaving)
    }

    @Test
    fun aRefusalIsNotAllowedAndTheNextTryClearsIt() {
        asMember()
        repository.leaveFailure = LeaveNotAllowedException(ProjectId("p1"))
        val viewModel = viewModel()
        viewModel.onLeaveClick()

        viewModel.onLeaveConfirm()

        with(viewModel.contentState) {
            assertEquals(LeaveFailure.NotAllowed, leaveFailure)
            assertFalse(isLeaving)
            assertFalse(hasLeft)
        }
        assertEquals(1, repository.asked.size, "a refusal does not read the page again")
        viewModel.onLeaveClick()
        assertNull(viewModel.contentState.leaveFailure)
        assertTrue(viewModel.contentState.isLeaveConfirmVisible)
    }

    @Test
    fun aGoneProjectIsNotFound() {
        asMember()
        repository.leaveFailure = ProjectGoneException(ProjectId("p1"))
        val viewModel = viewModel()
        viewModel.onLeaveClick()

        viewModel.onLeaveConfirm()

        assertEquals(ProjectDetailsUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun anyOtherFailureKeepsItsCauseAndTheUserMayTryAgain() {
        asMember()
        repository.leaveFailure = cause
        val viewModel = viewModel()
        viewModel.onLeaveClick()

        viewModel.onLeaveConfirm()

        assertSame(cause, assertIs<LeaveFailure.Failed>(viewModel.contentState.leaveFailure).cause)
        assertFalse(viewModel.contentState.isLeaving)
        repository.leaveFailure = null
        viewModel.onLeaveClick()
        viewModel.onLeaveConfirm()
        assertEquals(2, repository.left.size)
        assertTrue(viewModel.contentState.hasLeft)
    }

    @Test
    fun aFailureOfTheRereadAfterLeavingKeepsTheScreenAndTheBanner() {
        asMember()
        val viewModel = viewModel()
        viewModel.onLeaveClick()
        repository.onLeave = { repository.failure = cause }

        viewModel.onLeaveConfirm()

        with(viewModel.contentState) {
            assertTrue(hasLeft)
            assertFalse(isLeaving)
        }
    }

    @Test
    fun aProjectThatIsGoneWhenReadAgainAfterLeavingIsNotFound() {
        asMember()
        val viewModel = viewModel()
        viewModel.onLeaveClick()
        repository.onLeave = { repository.answer = null }

        viewModel.onLeaveConfirm()

        assertEquals(ProjectDetailsUiState.NotFound, viewModel.uiState.value)
    }
}
