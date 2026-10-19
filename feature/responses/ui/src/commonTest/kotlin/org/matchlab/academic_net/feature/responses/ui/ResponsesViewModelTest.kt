package org.matchlab.academic_net.feature.responses.ui

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.responses.domain.JoinRefusal
import org.matchlab.academic_net.feature.responses.domain.JoinRefusedException
import org.matchlab.academic_net.feature.responses.domain.usecase.DecideInvitationUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.MarkInvitationsSeenUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.ObserveMyResponsesUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.RefreshMyResponsesUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.WithdrawApplicationUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 15 / 15c: the tabs by type, the actions by the rules of the statuses, the guards, the refusals and the seen mark. */
class ResponsesViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val joins = FakeMyJoins()
    private val projects = FakeProjectSource(listOf(project("p1", listOf(role("r1"), role("r2"))), project("p2")))

    /** The view model with the tab entered ([shown]): the list is known only after a successful read of the server. */
    private fun viewModel(shown: Boolean = true): ResponsesViewModel = ResponsesViewModel(
        observeMyResponses = ObserveMyResponsesUseCase(joins, projects),
        refreshMyResponses = RefreshMyResponsesUseCase(joins),
        withdrawApplication = WithdrawApplicationUseCase(joins),
        decideInvitation = DecideInvitationUseCase(joins, projects),
        markInvitationsSeen = MarkInvitationsSeenUseCase(joins),
        clock = FixedClock,
        timeZone = UtcZone,
    ).also { if (shown) it.onShown() }

    private fun serve(vararg list: org.matchlab.academic_net.common.model.ProjectJoin) {
        joins.server = list.toList()
        joins.joins.value = list.toList()
    }

    private val ResponsesViewModel.state get() = uiState.value
    private val invitation = ProjectJoinType.Invitation
    private val application = ProjectJoinType.Application

    // ---- reading ----

    @Test
    fun theScreenIsLoadingUntilAReadOfTheServerHasSucceeded() {
        serve(join("a"))
        val viewModel = viewModel(shown = false)
        assertTrue(viewModel.state.isLoading, "the flow holds a list, but it may be incomplete")

        viewModel.onShown()

        assertFalse(viewModel.state.isLoading)
        assertEquals(1, viewModel.state.applications.size)
    }

    @Test
    fun anEmptyListOfTheServerIsAnEmptyScreenNotLoading() {
        val viewModel = viewModel()

        assertFalse(viewModel.state.isLoading)
        assertEquals(emptyList(), viewModel.state.applications)
    }

    @Test
    fun theTabsFollowTheTypeAndAnUntypedRecordIsAnApplication() {
        serve(
            join("a", type = application),
            join("i", type = invitation),
            join("n", type = null),
        )

        val state = viewModel().state

        assertEquals(listOf("a", "n"), state.applications.map { it.id.value })
        assertEquals(listOf("i"), state.invitations.map { it.id.value })
    }

    @Test
    fun theNumbersCountThePendingRecordsOnly() {
        serve(
            join("a1", type = application, status = ProjectJoinStatus.Pending),
            join("a2", type = application, status = null),
            join("a3", type = application, status = ProjectJoinStatus.Accepted),
            join("a4", type = application, status = ProjectJoinStatus.Cancelled),
            join("i1", type = invitation, status = ProjectJoinStatus.Pending),
            join("i2", type = invitation, status = ProjectJoinStatus.Rejected),
        )

        val state = viewModel().state

        assertEquals(1, state.applicationsWaiting)
        assertEquals(1, state.invitationsWaiting)
    }

    @Test
    fun theActionsFollowTheTypeAndTheStatus() {
        serve(
            join("app-pending", type = application, status = ProjectJoinStatus.Pending),
            join("app-unknown", type = application, status = null),
            join("app-done", type = application, status = ProjectJoinStatus.Accepted),
            join("inv-pending", type = invitation, status = ProjectJoinStatus.Pending),
            join("inv-unknown", type = invitation, status = null),
            join("inv-done", type = invitation, status = ProjectJoinStatus.Rejected),
            join("untyped", type = null, status = ProjectJoinStatus.Pending),
        )

        val state = viewModel().state
        val all = (state.applications + state.invitations).associateBy { it.id.value }

        assertEquals(setOf("app-pending"), all.filterValues { it.canWithdraw }.keys)
        assertEquals(setOf("inv-pending"), all.filterValues { it.canDecide }.keys)
        assertFalse(all.getValue("untyped").canWithdraw || all.getValue("untyped").canDecide, "no type, no actions")
    }

    @Test
    fun theItemCarriesTheProjectTheRoleAndTheInitiator() {
        serve(join("a", projectId = "p1", roleId = "r2"), join("b", projectId = "p1", roleId = "gone"))

        val items = viewModel().state.applications

        assertEquals("Проект p1", items[0].projectTitle)
        assertEquals("Роль r2", items[0].roleTitle)
        assertNull(items[1].roleTitle, "the project no longer has the role")
        assertEquals("Автор p1", items[0].initiator.name)
    }

    @Test
    fun aRecordWhoseProjectIsNotVisibleIsNotShown() {
        serve(join("a", projectId = "p1"), join("b", projectId = "hidden"))

        assertEquals(listOf("a"), viewModel().state.applications.map { it.id.value })
    }

    @Test
    fun theDaysAgoAreCalendarDaysAndNullWithoutATime() {
        serve(
            join("today", createdAt = FixedClock.now),
            join("two", createdAt = at(8L * 86_400 + 23 * 3600)),
            join("none", createdAt = null),
            join("future", createdAt = at(11L * 86_400)),
        )

        val days = viewModel().state.applications.associate { it.id.value to it.daysAgo }

        assertEquals(mapOf("today" to 0, "two" to 2, "none" to null, "future" to 0), days)
    }

    // ---- entering the tab ----

    @Test
    fun enteringTheTabReadsTheListAgainEveryTime() {
        serve(join("a"))
        val viewModel = viewModel(shown = false)

        viewModel.onShown()
        viewModel.onHidden()
        viewModel.onShown()

        assertEquals(2, joins.refreshes)
    }

    @Test
    fun aFailedReadWithoutAListIsTheFullScreenErrorAndRetryReadsAgain() {
        val cause = RemoteFailure.NoConnection()
        joins.refreshFailure = cause
        val viewModel = viewModel(shown = false)

        viewModel.onShown()

        with(viewModel.state) {
            assertSame(cause, loadFailure)
            assertFalse(isLoading)
        }
        joins.refreshFailure = null
        joins.server = listOf(join("a"))
        viewModel.onRetry()

        with(viewModel.state) {
            assertNull(loadFailure)
            assertEquals(listOf("a"), applications.map { it.id.value })
        }
    }

    @Test
    fun retryWithoutAnErrorDoesNothing() {
        serve(join("a"))
        val viewModel = viewModel()
        val before = joins.refreshes

        viewModel.onRetry()

        assertEquals(before, joins.refreshes)
    }

    @Test
    fun aFailedRereadWithAListOnTheScreenShowsNoError() {
        serve(join("a"))
        val viewModel = viewModel()
        joins.refreshFailure = RemoteFailure.Http(500)

        viewModel.onShown()

        with(viewModel.state) {
            assertNull(loadFailure)
            assertEquals(1, applications.size)
        }
    }

    @Test
    fun aFailureToReadAProjectIsTheErrorWithoutAListAndNothingWithOne() {
        val cause = RemoteFailure.Timeout()
        projects.getFailures[ProjectId("p1")] = cause
        serve(join("a"))

        val viewModel = viewModel(shown = false)

        // The coroutines machinery may hand the exception on as a copy, so its kind is what is compared.
        assertIs<RemoteFailure.Timeout>(viewModel.state.loadFailure)

        projects.getFailures.clear()
        joins.server = listOf(join("a"))
        viewModel.onShown()
        assertEquals(listOf("a"), viewModel.state.applications.map { it.id.value }, "the next entry collects again")
        assertNull(viewModel.state.loadFailure)

        projects.getFailures[ProjectId("p1")] = cause
        joins.joins.value = listOf(join("a"), join("b"))
        assertNull(viewModel.state.loadFailure, "with a list a failed project read changes nothing")
        assertEquals(listOf("a"), viewModel.state.applications.map { it.id.value })
    }

    // ---- the seen mark ----

    /**
     * BUG (production, ResponsesViewModel.refresh): the list is already in the flow (read for the dot of the tab), the first
     * entry reads it again, the server's list equals the cached one, so the flow emits nothing and `onList` never runs;
     * `refresh().onSuccess` makes the list known but does not report the seen mark. The dot comes back after leaving 15.
     */
    @Test
    fun theFirstEntryMarksTheInvitationsSeenWhenTheListWasAlreadyInTheCache() {
        serve(join("i1", type = invitation, createdAt = at(100)), join("i2", type = invitation, createdAt = at(300)))
        val viewModel = viewModel(shown = false)
        assertTrue(joins.marked.isEmpty(), "the list arrived, but the tab was not shown")

        viewModel.onShown()

        assertEquals(listOf(at(300)), joins.marked)
    }

    @Test
    fun theInvitationsAreMarkedSeenOnlyWhileTheTabIsShown() {
        serve(join("i1", type = invitation, createdAt = at(100)), join("i2", type = invitation, createdAt = at(300)))
        val viewModel = viewModel(shown = false)
        viewModel.onShown()
        viewModel.onHidden()
        viewModel.onShown()
        assertEquals(at(300), joins.marked.last(), "a known list is marked on the next entry")

        joins.joins.value = joins.joins.value.orEmpty() + join("i3", type = invitation, createdAt = at(500))
        assertEquals(at(500), joins.marked.last(), "a list that arrives on the open tab is marked")

        viewModel.onHidden()
        val marks = joins.marked.size
        joins.joins.value = joins.joins.value.orEmpty() + join("i4", type = invitation, createdAt = at(900))
        assertEquals(marks, joins.marked.size, "nothing is marked on a hidden tab")
    }

    @Test
    fun showingTheTabBeforeTheListArrivesMarksWhenItDoes() {
        val viewModel = viewModel(shown = false)
        viewModel.onShown()
        assertTrue(joins.marked.isEmpty())

        joins.joins.value = listOf(join("i1", type = invitation, createdAt = at(100)))

        assertEquals(listOf(at(100)), joins.marked)
    }

    // ---- withdrawing and declining ----

    @Test
    fun withdrawOpensTheDialogOnlyForAWaitingApplication() {
        serve(join("a"), join("done", status = ProjectJoinStatus.Accepted), join("i", type = invitation))
        val viewModel = viewModel()

        viewModel.onWithdrawClick(ProjectJoinId("done"))
        viewModel.onWithdrawClick(ProjectJoinId("i"))
        viewModel.onWithdrawClick(ProjectJoinId("nope"))
        assertNull(viewModel.state.dialog)

        viewModel.onWithdrawClick(ProjectJoinId("a"))
        assertEquals(
            ResponseDialog.Withdraw(ProjectJoinId("a"), "Проект p1", "Роль r1"),
            viewModel.state.dialog,
        )
    }

    @Test
    fun confirmingTheWithdrawalSendsItOnceAndTheRecordBecomesWithdrawn() {
        serve(join("a"))
        val viewModel = viewModel()
        viewModel.onWithdrawClick(ProjectJoinId("a"))

        viewModel.onDialogConfirm()
        viewModel.onDialogConfirm()

        assertEquals(listOf("withdraw a"), joins.calls)
        assertNull(viewModel.state.dialog)
        assertEquals(ProjectJoinStatus.Cancelled, viewModel.state.applications.single().join.status)
        assertTrue(viewModel.state.inProgress.isEmpty())
        assertNull(viewModel.state.actionFailure)
    }

    @Test
    fun dismissingTheDialogSendsNothing() {
        serve(join("a"))
        val viewModel = viewModel()
        viewModel.onWithdrawClick(ProjectJoinId("a"))

        viewModel.onDialogDismiss()
        viewModel.onDialogConfirm()

        assertTrue(joins.calls.isEmpty())
    }

    @Test
    fun declineOpensTheDialogOnlyForAWaitingInvitationAndConfirmingDeclines() {
        serve(join("i", type = invitation), join("old", type = invitation, status = ProjectJoinStatus.Accepted), join("a"))
        val viewModel = viewModel()

        viewModel.onDeclineClick(ProjectJoinId("old"))
        viewModel.onDeclineClick(ProjectJoinId("a"))
        assertNull(viewModel.state.dialog)

        viewModel.onDeclineClick(ProjectJoinId("i"))
        assertIs<ResponseDialog.DeclineInvitation>(viewModel.state.dialog)
        viewModel.onDialogConfirm()

        assertEquals(listOf("decide i false"), joins.calls)
        assertEquals(listOf(ProjectId("p1")), projects.refreshed, "declining reads the project again too")
    }

    @Test
    fun aSecondActionOnTheSameRecordWhileOneRunsIsIgnored() {
        serve(join("a"))
        joins.gate = Gate()
        val viewModel = viewModel()

        viewModel.onWithdrawClick(ProjectJoinId("a"))
        viewModel.onDialogConfirm()
        assertEquals(setOf(ProjectJoinId("a")), viewModel.state.inProgress)
        viewModel.onWithdrawClick(ProjectJoinId("a"))
        assertNull(viewModel.state.dialog, "no dialog for a record that is busy")
        viewModel.onDialogConfirm()
        joins.gate!!.open()

        assertEquals(listOf("withdraw a"), joins.calls)
        assertTrue(viewModel.state.inProgress.isEmpty())
    }

    // ---- accepting ----

    @Test
    fun acceptingSendsAtOnceWithoutADialogAndReadsTheProjectAgain() {
        serve(join("i", type = invitation))
        val viewModel = viewModel()

        viewModel.onAcceptClick(ProjectJoinId("i"))

        assertNull(viewModel.state.dialog)
        assertEquals(listOf("decide i true"), joins.calls)
        assertEquals(listOf(ProjectId("p1")), projects.refreshed)
        assertEquals(ProjectJoinStatus.Accepted, viewModel.state.invitations.single().join.status)
    }

    @Test
    fun aDoubleTapOnAcceptSendsOnce() {
        serve(join("i", type = invitation))
        joins.gate = Gate()
        val viewModel = viewModel()

        viewModel.onAcceptClick(ProjectJoinId("i"))
        viewModel.onAcceptClick(ProjectJoinId("i"))
        joins.gate!!.open()

        assertEquals(1, joins.calls.size)
    }

    @Test
    fun acceptingAnApplicationOrADecidedInvitationDoesNothing() {
        serve(join("a"), join("old", type = invitation, status = ProjectJoinStatus.Accepted))
        val viewModel = viewModel()

        viewModel.onAcceptClick(ProjectJoinId("a"))
        viewModel.onAcceptClick(ProjectJoinId("old"))
        viewModel.onAcceptClick(ProjectJoinId("nope"))

        assertTrue(joins.calls.isEmpty())
    }

    @Test
    fun differentRecordsMayRunTogether() {
        serve(join("i1", type = invitation), join("i2", type = invitation))
        joins.gate = Gate()
        val viewModel = viewModel()

        viewModel.onAcceptClick(ProjectJoinId("i1"))
        viewModel.onAcceptClick(ProjectJoinId("i2"))

        assertEquals(setOf(ProjectJoinId("i1"), ProjectJoinId("i2")), viewModel.state.inProgress)
        joins.gate!!.open()
        assertEquals(2, joins.calls.size)
    }

    // ---- failures ----

    @Test
    fun everyRefusalIsNotAllowedAndTheListIsReadAgain() {
        for (refusal in JoinRefusal.entries) {
            joins.calls.clear()
            serve(join("i", type = invitation))
            joins.failure = JoinRefusedException(refusal)
            val viewModel = viewModel()
            val before = joins.refreshes

            viewModel.onAcceptClick(ProjectJoinId("i"))

            assertEquals(JoinFailure.NotAllowed, viewModel.state.actionFailure, "$refusal")
            assertEquals(before + 1, joins.refreshes, "$refusal")
            assertTrue(viewModel.state.inProgress.isEmpty())
        }
    }

    @Test
    fun anyOtherFailureKeepsItsCauseAndTheNextActionClearsItAndItCanBeDismissed() {
        serve(join("i", type = invitation))
        val cause = RemoteFailure.NoConnection()
        joins.failure = cause
        val viewModel = viewModel()

        viewModel.onAcceptClick(ProjectJoinId("i"))
        assertSame(cause, assertIs<JoinFailure.Failed>(viewModel.state.actionFailure).cause)

        viewModel.onFailureDismiss()
        assertNull(viewModel.state.actionFailure)

        viewModel.onAcceptClick(ProjectJoinId("i"))
        joins.failure = null
        viewModel.onAcceptClick(ProjectJoinId("i"))
        assertNull(viewModel.state.actionFailure)
        assertEquals(3, joins.calls.size)
    }
}
