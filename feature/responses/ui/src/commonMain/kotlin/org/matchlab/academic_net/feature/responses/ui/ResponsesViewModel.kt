package org.matchlab.academic_net.feature.responses.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.responses.domain.MyResponse
import org.matchlab.academic_net.feature.responses.domain.isWaitingForDecision
import org.matchlab.academic_net.feature.responses.domain.usecase.DecideInvitationUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.JoinActionResult
import org.matchlab.academic_net.feature.responses.domain.usecase.MarkInvitationsSeenUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.ObserveMyResponsesUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.RefreshMyResponsesUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.WithdrawApplicationUseCase
import org.matchlab.academic_net.utils.elapsedDays
import kotlin.time.Clock

/**
 * A record of the viewer on 15 / 15c with the project it is about.
 *
 * @property response the record, its project and its role (`null` when the project no longer has the role)
 * @property daysAgo how many days ago the record was made; `null` when the server sent no time
 */
@Immutable
data class ResponseItem(
    val response: MyResponse,
    val daysAgo: Int?,
) {
    val join: ProjectJoin get() = response.join
    val id: ProjectJoinId get() = response.join.id
    val projectId: ProjectId get() = response.join.projectId
    val projectTitle: String get() = response.project.title
    val roleTitle: String? get() = response.role?.title

    /** The initiator of the project: the one the chat of the record is with. */
    val initiator: UserSummary get() = response.project.initiator

    /** "Отозвать отклик": an application that waits for a decision. A record without a type has no actions. */
    val canWithdraw: Boolean
        get() = join.type == ProjectJoinType.Application && join.isWaitingForDecision

    /** ✓ and ×: an invitation that waits for a decision. */
    val canDecide: Boolean
        get() = join.type == ProjectJoinType.Invitation && join.isWaitingForDecision
}

/** The confirmation that is open on 15: 15n or 15o. */
sealed interface ResponseDialog {
    val id: ProjectJoinId
    val projectTitle: String
    val roleTitle: String?

    /** 15n "Отозвать отклик?". */
    data class Withdraw(
        override val id: ProjectJoinId,
        override val projectTitle: String,
        override val roleTitle: String?,
    ) : ResponseDialog

    /** 15o "Отклонить приглашение?". */
    data class DeclineInvitation(
        override val id: ProjectJoinId,
        override val projectTitle: String,
        override val roleTitle: String?,
    ) : ResponseDialog
}

/** Why an action on a record did not go through. */
sealed interface JoinFailure {
    /** The server refused (403, 404, 409): "Действие недоступно"; the list has been read again. */
    data object NotAllowed : JoinFailure

    /** The request failed for [cause]; the screen says why (see `failureText`). */
    data class Failed(val cause: Throwable) : JoinFailure
}

/**
 * What 15 / 15c show. The screen reads it in this order: [loadFailure] (the full-screen error with "Повторить"; only
 * while there is no list), [isLoading], then the list of the selected tab.
 *
 * @property applications the records that are not invitations ("Отклики": applications and those of an unknown type),
 *  in the order of the server
 * @property invitations the invitations ("Приглашения")
 * @property inProgress the records an action is on its way for: their buttons show an arc and take no taps
 * @property dialog the confirmation that is open
 * @property actionFailure why the last action failed; cleared by the next one
 */
@Immutable
data class ResponsesUiState(
    val isLoading: Boolean = true,
    val loadFailure: Throwable? = null,
    val applications: List<ResponseItem> = emptyList(),
    val invitations: List<ResponseItem> = emptyList(),
    val inProgress: Set<ProjectJoinId> = emptySet(),
    val dialog: ResponseDialog? = null,
    val actionFailure: JoinFailure? = null,
) {
    /** The number of the tab "Отклики · N": the records that wait for a decision. */
    val applicationsWaiting: Int get() = applications.count { it.join.isWaitingForDecision }

    /** The number of the tab "Приглашения · N": the invitations that wait for a decision. */
    val invitationsWaiting: Int get() = invitations.count { it.join.isWaitingForDecision }
}

/**
 * 15 / 15c "Мои отклики" and "Приглашения": the viewer's side of the applications and the invitations.
 *
 * **Reading.** The list is the flow of [observeMyResponses], collected while the view model lives; `null` (not read yet)
 * is the progress indicator. The list counts as known to the screen only after one read of the server has succeeded in
 * this view model ([refreshMyResponses]): the flow may hold records that came by themselves (an application made on 04
 * before the first read) and that would be an incomplete list. Until then a failure of the read, or of a project of a
 * record, is the full-screen error with "Повторить". [onShown] (each entry to the tab) reads the list again; once the
 * list is known, a failure of that read is not shown. The flow ends with a failure when a project of a record cannot be
 * read; the next [onShown] or [onRetry] collects it again.
 *
 * **Tabs.** A record of type `Invitation` is under "Приглашения", the rest under "Отклики". The numbers are the records
 * that wait for a decision (`Pending` or unknown).
 *
 * **Seen.** While the tab is shown ([onShown] .. [onHidden]) every list that arrives is reported to
 * [markInvitationsSeen] (its invitations): that is what puts the dot of the tab out.
 *
 * **Actions.** "Отозвать" and × open a confirmation; ✓ sends at once. An action is guarded by the set of records it is
 * running for: a second one for the same record is ignored. `Refused` is [JoinFailure.NotAllowed], a failure is told by
 * its kind.
 */
class ResponsesViewModel(
    private val observeMyResponses: ObserveMyResponsesUseCase,
    private val refreshMyResponses: RefreshMyResponsesUseCase,
    private val withdrawApplication: WithdrawApplicationUseCase,
    private val decideInvitation: DecideInvitationUseCase,
    private val markInvitationsSeen: MarkInvitationsSeenUseCase,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ViewModel() {
    private val state = MutableStateFlow(ResponsesUiState())
    val uiState: StateFlow<ResponsesUiState> = state.asStateFlow()

    private var observeJob: Job? = null
    private var refreshJob: Job? = null
    /** The flow has delivered a list. */
    private var hasList = false

    /** A read of the server has succeeded: only then the list in the flow is the whole list. */
    private var refreshed = false
    private var observeFailed = false
    private var isShown = false

    init {
        observe()
    }

    /** The tab came on the screen: marks what it shows as seen and reads the list again. */
    fun onShown() {
        isShown = true
        if (isKnown) markSeen()
        if (observeFailed) observe()
        refresh()
    }

    /** The tab left the screen. */
    fun onHidden() {
        isShown = false
    }

    /** "Повторить" of the error: collects and reads the list again, with the progress indicator in place of the error. */
    fun onRetry() {
        if (state.value.loadFailure == null) return
        state.update { it.copy(isLoading = true, loadFailure = null) }
        observe()
        refresh()
    }

    /** "Отозвать отклик": opens 15n. Only for an application that waits and is not busy. */
    fun onWithdrawClick(id: ProjectJoinId) {
        val item = find(id) ?: return
        if (!item.canWithdraw || id in state.value.inProgress) return
        state.update {
            it.copy(dialog = ResponseDialog.Withdraw(id, item.projectTitle, item.roleTitle), actionFailure = null)
        }
    }

    /** × of an invitation: opens 15o. */
    fun onDeclineClick(id: ProjectJoinId) {
        val item = find(id) ?: return
        if (!item.canDecide || id in state.value.inProgress) return
        state.update {
            it.copy(dialog = ResponseDialog.DeclineInvitation(id, item.projectTitle, item.roleTitle), actionFailure = null)
        }
    }

    /** ✓ of an invitation: accepts at once, without a dialog. */
    fun onAcceptClick(id: ProjectJoinId) {
        val item = find(id) ?: return
        if (!item.canDecide) return
        run(id) { decideInvitation(item.join, accept = true) }
    }

    fun onDialogDismiss() = state.update { it.copy(dialog = null) }

    /** The confirming button of the open dialog; works once per dialog (the button can be tapped again while it fades). */
    fun onDialogConfirm() {
        var dialog: ResponseDialog? = null
        state.update {
            dialog = it.dialog
            it.copy(dialog = null)
        }
        val confirmed = dialog ?: return
        val item = find(confirmed.id) ?: return
        when (confirmed) {
            is ResponseDialog.Withdraw -> run(confirmed.id) { withdrawApplication(item.join) }
            is ResponseDialog.DeclineInvitation -> run(confirmed.id) { decideInvitation(item.join, accept = false) }
        }
    }

    /** Hides the message of a failed action. */
    fun onFailureDismiss() = state.update { it.copy(actionFailure = null) }

    private fun find(id: ProjectJoinId): ResponseItem? =
        state.value.let { current -> (current.applications + current.invitations).firstOrNull { it.id == id } }

    private fun run(id: ProjectJoinId, action: suspend () -> JoinActionResult) {
        var started = false
        state.update {
            started = id !in it.inProgress
            if (started) it.copy(inProgress = it.inProgress + id, actionFailure = null) else it
        }
        if (!started) return
        viewModelScope.launch {
            val failure = try {
                when (val result = action()) {
                    // The record changes in the list that the data layer keeps; the screen follows it.
                    is JoinActionResult.Done -> null
                    JoinActionResult.RoleFull -> JoinFailure.NotAllowed
                    is JoinActionResult.Refused -> JoinFailure.NotAllowed
                    is JoinActionResult.Failure -> JoinFailure.Failed(result.cause)
                }
            } finally {
                state.update { it.copy(inProgress = it.inProgress - id) }
            }
            if (failure != null) state.update { it.copy(actionFailure = failure) }
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeFailed = false
        observeJob = viewModelScope.launch {
            observeMyResponses()
                .catch { cause ->
                    if (cause is CancellationException) throw cause
                    observeFailed = true
                    // With the whole list on the screen a failed read of a project changes nothing.
                    state.update { if (isKnown) it else it.copy(isLoading = false, loadFailure = cause) }
                }
                .collect { responses -> if (responses != null) onList(responses) }
        }
    }

    private fun onList(responses: List<MyResponse>) {
        val now = clock.now()
        val zone = timeZone()
        val items = responses.map { response ->
            ResponseItem(response, response.join.createdAt?.let { elapsedDays(it, now, zone) })
        }
        hasList = true
        state.update {
            val lists = it.copy(
                applications = items.filter { item -> item.join.type != ProjectJoinType.Invitation },
                invitations = items.filter { item -> item.join.type == ProjectJoinType.Invitation },
            )
            // Before the first successful read the list may be incomplete: the screen keeps its progress or its error.
            if (refreshed) lists.copy(isLoading = false, loadFailure = null) else lists
        }
        if (isKnown && isShown) markSeen()
    }

    /** The screen may show the list: the flow has one and a read of the server has succeeded. */
    private val isKnown: Boolean get() = hasList && refreshed

    private fun markSeen() {
        markInvitationsSeen(state.value.invitations.map { it.join })
    }

    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshMyResponses()
                .onSuccess {
                    refreshed = true
                    // The flow already holds the list: now it is the whole one.
                    if (hasList) state.update { it.copy(isLoading = false, loadFailure = null) }
                    // The list did not change, so the flow says nothing: the invitations on the screen are seen now.
                    if (isKnown && isShown) markSeen()
                }
                .onFailure { cause ->
                    // The known list stays; before the first successful read the screen says what failed.
                    if (!refreshed) state.update { it.copy(isLoading = false, loadFailure = cause) }
                }
        }
    }
}
