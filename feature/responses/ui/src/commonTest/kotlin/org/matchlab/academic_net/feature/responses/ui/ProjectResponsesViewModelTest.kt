package org.matchlab.academic_net.feature.responses.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.responses.domain.JoinRefusal
import org.matchlab.academic_net.feature.responses.domain.JoinRefusedException
import org.matchlab.academic_net.feature.responses.domain.ProjectResponse
import org.matchlab.academic_net.feature.responses.domain.ProjectResponsesUnavailableException
import org.matchlab.academic_net.feature.responses.domain.usecase.DecideApplicationUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.ObserveProjectResponsesUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.RefreshProjectResponsesUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 07d: the list and the filter, the guard of ✓ ×, the full role (07r), the notice 07q and the refusals 07t. */
class ProjectResponsesViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() {
        collectors.cancel()
        uninstallMainDispatcher()
    }

    private val collectors = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())
    private val projectJoins = FakeProjectJoins()
    private val projects = FakeProjectSource(
        listOf(project("p1", listOf(role("r1", slots = 2), role("r2"), role("r3")))),
    )
    private val id = ProjectId("p1")

    private fun response(
        joinId: String,
        roleId: String = "r1",
        candidate: String? = joinId,
        status: ProjectJoinStatus? = ProjectJoinStatus.Pending,
        hasProfile: Boolean = true,
        createdAt: Long? = null,
    ) = ProjectResponse(
        join(joinId, "p1", roleId, candidate, ProjectJoinType.Application, status, createdAt?.let(::at)),
        candidate?.let { candidate(it, "Имя $it", hasProfile) },
    )

    private fun serve(vararg list: ProjectResponse) {
        projectJoins.server = mapOf(id to list.toList())
    }

    private fun viewModel(): ProjectResponsesViewModel {
        val viewModel = ProjectResponsesViewModel(
            id,
            ObserveProjectResponsesUseCase(projectJoins, projects),
            RefreshProjectResponsesUseCase(projectJoins, projects),
            DecideApplicationUseCase(projectJoins, projects),
            FixedClock,
            UtcZone,
        )
        collectors.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private val ProjectResponsesViewModel.state get() = uiState.value

    // ---- reading ----

    @Test
    fun theScreenIsLoadingUntilTheListArrives() {
        serve(response("a"))
        projectJoins.refreshGate = Gate()

        val viewModel = viewModel()

        assertTrue(viewModel.state.isLoading)
        projectJoins.refreshGate!!.open()
        assertFalse(viewModel.state.isLoading)
        assertEquals("Проект p1", viewModel.state.projectTitle)
    }

    @Test
    fun noApplicationsIsAnEmptyScreenNotLoading() {
        serve()

        val state = viewModel().state

        assertFalse(state.isLoading)
        assertEquals(emptyList(), state.items)
        assertEquals(0, state.waitingCount)
        assertEquals(emptyList(), state.roleFilters)
    }

    @Test
    fun anInvisibleProjectOrARefusedListIsNotFound() {
        val hidden = ProjectResponsesViewModel(
            ProjectId("hidden"),
            ObserveProjectResponsesUseCase(projectJoins, projects),
            RefreshProjectResponsesUseCase(projectJoins, projects),
            DecideApplicationUseCase(projectJoins, projects),
        )
        collectors.launch { hidden.uiState.collect {} }
        assertTrue(hidden.state.isNotFound)

        projectJoins.refreshFailure = ProjectResponsesUnavailableException(id)
        assertTrue(viewModel().state.isNotFound)
    }

    @Test
    fun aFailureIsTheErrorAndRetryReadsAgain() {
        val cause = RemoteFailure.NoConnection()
        projectJoins.refreshFailure = cause
        serve(response("a"))
        val viewModel = viewModel()
        assertSame(cause, viewModel.state.loadFailure)
        assertFalse(viewModel.state.isLoading)

        projectJoins.refreshFailure = null
        viewModel.onRetry()

        assertNull(viewModel.state.loadFailure)
        assertEquals(1, viewModel.state.items.size)
    }

    @Test
    fun retryWithoutAnErrorDoesNothing() {
        serve(response("a"))
        val viewModel = viewModel()
        val calls = projectJoins.calls.size

        viewModel.onRetry()

        assertEquals(calls, projectJoins.calls.size)
    }

    @Test
    fun theInvitationsOfTheInitiatorAreNotShown() {
        serve(
            response("a"),
            ProjectResponse(join("inv", type = ProjectJoinType.Invitation), candidate("u9")),
        )

        assertEquals(listOf("a"), viewModel().state.items.map { it.id.value })
    }

    // ---- the filter ----

    @Test
    fun theChipsAreTheRolesThatHaveApplicationsWithTheNumberOfWaitingOnes() {
        serve(
            response("a", "r1"),
            response("b", "r1", status = ProjectJoinStatus.Accepted),
            response("c", "r1", status = null),
            response("d", "r2", status = ProjectJoinStatus.Declined),
        )

        val state = viewModel().state

        assertEquals(
            listOf(RoleFilterItem(RoleId("r1"), "Роль r1", 2), RoleFilterItem(RoleId("r2"), "Роль r2", 0)),
            state.roleFilters,
            "r3 has no applications, so no chip",
        )
        assertEquals(2, state.waitingCount)
    }

    @Test
    fun selectingARoleFiltersTheCardsAndAllResetsIt() {
        serve(response("a", "r1"), response("b", "r2"), response("c", "r1"))
        val viewModel = viewModel()

        viewModel.onRoleSelected(RoleId("r1"))
        assertEquals(listOf("a", "c"), viewModel.state.visibleItems.map { it.id.value })
        assertEquals(3, viewModel.state.items.size)
        assertEquals(3, viewModel.state.waitingCount, "the number of \"Все\" stays")

        viewModel.onRoleSelected(null)
        assertEquals(3, viewModel.state.visibleItems.size)
        assertNull(viewModel.state.selectedRole)
    }

    @Test
    fun aRoleWithoutApplicationsFallsBackToAll() {
        serve(response("a", "r1"))
        val viewModel = viewModel()

        viewModel.onRoleSelected(RoleId("r3"))

        assertNull(viewModel.state.selectedRole)
        assertEquals(1, viewModel.state.visibleItems.size)
    }

    // ---- the cards ----

    @Test
    fun aCandidateWithoutAProfileHasNoChatAndNoTap() {
        serve(response("a", hasProfile = false), response("b", candidate = null), response("c"))

        val items = viewModel().state.items.associateBy { it.id.value }

        assertFalse(items.getValue("a").hasProfile)
        assertFalse(items.getValue("b").hasProfile)
        assertNull(items.getValue("b").candidate)
        assertTrue(items.getValue("c").hasProfile)
    }

    @Test
    fun theDaysAgoComeFromTheTimeOfTheRecord() {
        serve(response("a", createdAt = 8L * 86_400 + 100), response("b", createdAt = null))

        val days = viewModel().state.items.associate { it.id.value to it.daysAgo }

        assertEquals(mapOf("a" to 2, "b" to null), days)
    }

    @Test
    fun decidedApplicationsHaveNoButtons() {
        serve(
            response("p", status = ProjectJoinStatus.Pending),
            response("u", status = null),
            response("a", status = ProjectJoinStatus.Accepted),
            response("d", status = ProjectJoinStatus.Declined),
            response("w", status = ProjectJoinStatus.Withdrawn),
        )

        val deciding = viewModel().state.items.filter { it.canDecide }.map { it.id.value }

        assertEquals(listOf("p", "u"), deciding)
    }

    // ---- deciding ----

    @Test
    fun acceptingSendsAtOnceShowsTheNoticeAndReadsTheProjectAgain() {
        serve(response("a", "r2"))
        val viewModel = viewModel()

        viewModel.onAcceptClick(ProjectJoinId("a"))

        assertEquals(listOf("decide a true"), projectJoins.calls.filter { it.startsWith("decide") })
        assertEquals(AcceptedNotice("Имя a", "Роль r2"), viewModel.state.accepted)
        assertEquals(ProjectJoinStatus.Accepted, viewModel.state.items.single().join.status)
        assertEquals(listOf(id), projects.refreshed)
        assertTrue(viewModel.state.inProgress.isEmpty())
    }

    @Test
    fun theNoticeNamesACandidateWithoutAProfileAsEmpty() {
        serve(response("a", hasProfile = false))
        val viewModel = viewModel()

        viewModel.onAcceptClick(ProjectJoinId("a"))

        assertEquals("", viewModel.state.accepted?.candidateName)
    }

    @Test
    fun decliningSendsAtOnceWithoutANotice() {
        serve(response("a"))
        val viewModel = viewModel()

        viewModel.onDeclineClick(ProjectJoinId("a"))

        assertTrue("decide a false" in projectJoins.calls)
        assertNull(viewModel.state.accepted)
        assertEquals(ProjectJoinStatus.Declined, viewModel.state.items.single().join.status)
    }

    @Test
    fun theNoticeGoesAwayWithTheNextActionOrOnDismiss() {
        serve(response("a", "r2"), response("b", "r3"))
        val viewModel = viewModel()
        viewModel.onAcceptClick(ProjectJoinId("a"))
        assertTrue(viewModel.state.accepted != null)

        viewModel.onDeclineClick(ProjectJoinId("b"))
        assertNull(viewModel.state.accepted)

        viewModel.onNoticeDismiss()
        assertNull(viewModel.state.actionFailure)
    }

    @Test
    fun aDoubleTapSendsOneDecision() {
        serve(response("a"))
        projectJoins.gate = Gate()
        val viewModel = viewModel()

        viewModel.onAcceptClick(ProjectJoinId("a"))
        assertEquals(setOf(ProjectJoinId("a")), viewModel.state.inProgress)
        viewModel.onAcceptClick(ProjectJoinId("a"))
        viewModel.onDeclineClick(ProjectJoinId("a"))
        projectJoins.gate!!.open()

        assertEquals(1, projectJoins.calls.count { it.startsWith("decide") })
    }

    @Test
    fun aDecidedOrUnknownRecordIsNotDecidedAgain() {
        serve(response("done", status = ProjectJoinStatus.Accepted))
        val viewModel = viewModel()

        viewModel.onAcceptClick(ProjectJoinId("done"))
        viewModel.onDeclineClick(ProjectJoinId("nope"))

        assertTrue(projectJoins.calls.none { it.startsWith("decide") })
    }

    // ---- a full role (07r) ----

    @Test
    fun aRoleWithoutAFreePlaceCannotBeAcceptedIntoAndTheBannerNamesIt() {
        projects.projects.value = mapOf(id to project("p1", listOf(role("r1", slots = 1, occupied = 1))))
        serve(response("a", "r1"))
        val viewModel = viewModel()

        viewModel.onRoleSelected(RoleId("r1"))

        assertFalse(viewModel.state.items.single().roleHasPlace)
        assertEquals("Роль r1", viewModel.state.noPlacesRole)
        viewModel.onAcceptClick(ProjectJoinId("a"))
        assertTrue(projectJoins.calls.none { it.startsWith("decide") }, "accept is off")
        viewModel.onDeclineClick(ProjectJoinId("a"))
        assertTrue("decide a false" in projectJoins.calls, "declining stays possible")
    }

    @Test
    fun theBannerIsOnlyForASelectedRole() {
        projects.projects.value = mapOf(id to project("p1", listOf(role("r1", slots = 1, occupied = 1))))
        serve(response("a", "r1"))

        assertNull(viewModel().state.noPlacesRole)
    }

    @Test
    fun aRoleTheProjectNoLongerHasStaysAcceptableForTheServerToJudge() {
        serve(response("a", "gone"))
        val viewModel = viewModel()

        assertTrue(viewModel.state.items.single().roleHasPlace)
        assertNull(viewModel.state.items.single().role)
    }

    // ---- failures ----

    @Test
    fun aRefusalIs07tAndTheListIsReadAgain() {
        for (refusal in JoinRefusal.entries) {
            projectJoins.calls.clear()
            projectJoins.failure = JoinRefusedException(refusal)
            serve(response("a"))
            val viewModel = viewModel()

            viewModel.onDeclineClick(ProjectJoinId("a"))

            assertEquals(JoinFailure.NotAllowed, viewModel.state.actionFailure, "$refusal")
            assertTrue(projectJoins.calls.count { it == "refresh p1" } >= 2, "$refusal: read again")
            assertTrue(viewModel.state.inProgress.isEmpty())
        }
    }

    @Test
    fun anyOtherFailureKeepsItsCause() {
        val cause = RemoteFailure.Http(500)
        projectJoins.failure = cause
        serve(response("a"))
        val viewModel = viewModel()

        viewModel.onAcceptClick(ProjectJoinId("a"))

        assertSame(cause, assertIs<JoinFailure.Failed>(viewModel.state.actionFailure).cause)
        assertNull(viewModel.state.accepted)
    }
}
