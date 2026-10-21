package org.matchlab.academic_net.feature.responses.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.feature.responses.domain.Candidate
import org.matchlab.academic_net.feature.responses.domain.ProjectResponse
import org.matchlab.academic_net.feature.responses.domain.ProjectResponses
import org.matchlab.academic_net.feature.responses.domain.isWaitingForDecision
import org.matchlab.academic_net.feature.responses.domain.usecase.DecideApplicationUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.JoinActionResult
import org.matchlab.academic_net.feature.responses.domain.usecase.ObserveProjectResponsesUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.RefreshProjectResponsesUseCase
import org.matchlab.academic_net.feature.responses.domain.usecase.RefreshResponsesResult
import org.matchlab.academic_net.utils.elapsedDays
import kotlin.time.Clock

/**
 * An application on 07d with the role it is for.
 *
 * @property role the role of the project the record is for; `null` when the project no longer has it
 * @property roleHasPlace the role has a free place as the project says, and the server has not just said otherwise;
 *  without it accept is switched off (07r)
 * @property daysAgo how many days ago the application was made; `null` when the server sent no time
 */
@Immutable
data class CandidateItem(
    val response: ProjectResponse,
    val role: Role?,
    val roleHasPlace: Boolean,
    val daysAgo: Int?,
) {
    val join: ProjectJoin get() = response.join
    val id: ProjectJoinId get() = response.join.id
    val candidate: Candidate? get() = response.candidate

    /** ✓ and ×: the application waits for a decision (the rules of `ResponsesModels.kt`). */
    val canDecide: Boolean get() = join.isWaitingForDecision

    /** The profile of the candidate is known: the card has the chat and opens it. */
    val hasProfile: Boolean
        get() = candidate?.let { it.hasProfile && it.person.user.id.value.isNotBlank() } == true
}

/** A chip of the filter of 07d: a role that has applications, with the ones that wait. */
@Immutable
data class RoleFilterItem(val id: RoleId, val title: String, val waiting: Int)

/** 07q: the application that was just accepted. [candidateName] is empty without a profile ("Без имени"). */
@Immutable
data class AcceptedNotice(val candidateName: String, val roleTitle: String)

/**
 * What 07d shows. The screen reads it in this order: [isNotFound], [loadFailure] (the full-screen error with "Повторить"),
 * [isLoading], an empty [items] (07n), then the chips and the cards of [visibleItems].
 *
 * @property projectTitle the line under "Отклики · N" in the bar
 * @property items every application of the project, in the order of the server
 * @property waitingCount the "N" of "Отклики · N" and of "Все · N": the applications that wait for a decision
 * @property roleFilters the chips of the roles that have applications
 * @property selectedRole the chosen chip; `null` is "Все"
 * @property noPlacesRole the title of the selected role when it has no free place: the banner 07r and accept off
 * @property accepted the banner 07q; stays until the next action
 * @property inProgress the applications a decision is on its way for: an arc stands in place of ✓ ×
 * @property actionFailure why the last decision failed (07t: "Действие недоступно"); cleared by the next one
 */
@Immutable
data class ProjectResponsesUiState(
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val loadFailure: Throwable? = null,
    val projectTitle: String = "",
    val items: List<CandidateItem> = emptyList(),
    val waitingCount: Int = 0,
    val roleFilters: List<RoleFilterItem> = emptyList(),
    val selectedRole: RoleId? = null,
    val visibleItems: List<CandidateItem> = emptyList(),
    val noPlacesRole: String? = null,
    val accepted: AcceptedNotice? = null,
    val inProgress: Set<ProjectJoinId> = emptySet(),
    val actionFailure: JoinFailure? = null,
)

/**
 * 07d "Отклики на роли": the applications to the roles of the initiator's project.
 *
 * **Reading.** The list is the flow of [observeProjectResponses] (the project and its applications, invitations left out);
 * the view model asks the data layer to read it when it is created and again on [onRetry]. A project that is gone or a
 * list the server refuses (403, 404) is [ProjectResponsesUiState.isNotFound]; another failure is the error with "Повторить"
 * only while there is nothing to show.
 *
 * **Filter.** The chips are local: "Все" and the roles that have applications, each with the number of those that wait for a
 * decision. A role that loses its last application falls back to "Все".
 *
 * **Decisions.** ✓ and × send at once, without a dialog (the design has none). A decision is guarded by the set of
 * applications it is running for. Accept is not sent for a role without a free place ([CandidateItem.roleHasPlace]; when
 * the server says the role is full, the view model remembers it); [JoinActionResult.Done] with accept shows the banner
 * 07q. `Refused` is [JoinFailure.NotAllowed] (07t), a failure is told by its kind.
 */
class ProjectResponsesViewModel(
    private val projectId: ProjectId,
    observeProjectResponses: ObserveProjectResponsesUseCase,
    private val refreshProjectResponses: RefreshProjectResponsesUseCase,
    private val decideApplication: DecideApplicationUseCase,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())
    private var loadJob: Job? = null

    val uiState: StateFlow<ProjectResponsesUiState> = combine(
        observeProjectResponses(projectId).catch { cause ->
            // The flow never fails by its contract; if it does, the screen offers to try again.
            local.update { it.copy(loadFailure = cause) }
            emit(null)
        },
        local,
    ) { data, own -> buildState(data, own) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), ProjectResponsesUiState())

    init {
        load()
    }

    /** "Повторить" of the error: reads the applications again. It does nothing while a read is on its way. */
    fun onRetry() {
        if (local.value.loadFailure == null || loadJob?.isActive == true) return
        local.update { it.copy(loadFailure = null) }
        load()
    }

    /** A chip of the filter; `null` is "Все". */
    fun onRoleSelected(roleId: RoleId?) = local.update { it.copy(selectedRole = roleId) }

    /** ✓ of an application. */
    fun onAcceptClick(id: ProjectJoinId) = decide(id, accept = true)

    /** × of an application. */
    fun onDeclineClick(id: ProjectJoinId) = decide(id, accept = false)

    /** Hides the banners 07q and 07t. */
    fun onNoticeDismiss() = local.update { it.copy(accepted = null, actionFailure = null) }

    private fun decide(id: ProjectJoinId, accept: Boolean) {
        val item = uiState.value.items.firstOrNull { it.id == id } ?: return
        if (!item.canDecide || (accept && !item.roleHasPlace)) return
        var started = false
        local.update {
            started = id !in it.inProgress
            if (started) it.copy(inProgress = it.inProgress + id, accepted = null, actionFailure = null) else it
        }
        if (!started) return
        viewModelScope.launch {
            val result = try {
                decideApplication(item.join, accept)
            } finally {
                local.update { it.copy(inProgress = it.inProgress - id) }
            }
            when (result) {
                is JoinActionResult.Done -> if (accept) {
                    val notice = AcceptedNotice(
                        candidateName = item.candidate?.person?.user?.name.orEmpty(),
                        roleTitle = item.role?.title.orEmpty(),
                    )
                    local.update { it.copy(accepted = notice) }
                }
                JoinActionResult.RoleFull -> local.update { it.copy(fullRoles = it.fullRoles + item.join.roleId) }
                is JoinActionResult.Refused -> local.update { it.copy(actionFailure = JoinFailure.NotAllowed) }
                is JoinActionResult.Failure -> local.update { it.copy(actionFailure = JoinFailure.Failed(result.cause)) }
            }
        }
    }

    /** Reads the list; the records themselves arrive through the observed flow. */
    private fun load() {
        loadJob = viewModelScope.launch {
            when (val result = refreshProjectResponses(projectId)) {
                RefreshResponsesResult.Done -> Unit
                RefreshResponsesResult.NotFound -> local.update { it.copy(isNotFound = true) }
                is RefreshResponsesResult.Failure -> local.update { it.copy(loadFailure = result.cause) }
            }
        }
    }

    private fun buildState(data: ProjectResponses?, own: LocalState): ProjectResponsesUiState {
        if (own.isNotFound) return ProjectResponsesUiState(isLoading = false, isNotFound = true)
        if (data == null) {
            return ProjectResponsesUiState(isLoading = own.loadFailure == null, loadFailure = own.loadFailure)
        }
        val now = clock.now()
        val zone = timeZone()
        val roles = data.project.roles.associateBy { it.id }
        val items = data.responses.map { response ->
            val role = roles[response.join.roleId]
            CandidateItem(
                response = response,
                role = role,
                roleHasPlace = (role?.isVacant ?: true) && response.join.roleId !in own.fullRoles,
                daysAgo = response.join.createdAt?.let { elapsedDays(it, now, zone) },
            )
        }
        val filters = data.project.roles.mapNotNull { role ->
            val ofRole = items.filter { it.join.roleId == role.id }
            if (ofRole.isEmpty()) null else RoleFilterItem(role.id, role.title, ofRole.count { it.canDecide })
        }
        val selected = own.selectedRole?.takeIf { id -> filters.any { it.id == id } }
        val visible = if (selected == null) items else items.filter { it.join.roleId == selected }
        val selectedRole = selected?.let { roles[it] }
        return ProjectResponsesUiState(
            isLoading = false,
            projectTitle = data.project.title,
            items = items,
            waitingCount = items.count { it.canDecide },
            roleFilters = filters,
            selectedRole = selected,
            visibleItems = visible,
            noPlacesRole = if (selected != null && visible.none { it.roleHasPlace }) {
                selectedRole?.title.orEmpty()
            } else {
                null
            },
            accepted = own.accepted,
            inProgress = own.inProgress,
            actionFailure = own.actionFailure,
        )
    }

    private data class LocalState(
        val loadFailure: Throwable? = null,
        /** The project or the list is gone for the viewer. */
        val isNotFound: Boolean = false,
        val selectedRole: RoleId? = null,
        val inProgress: Set<ProjectJoinId> = emptySet(),
        val accepted: AcceptedNotice? = null,
        val actionFailure: JoinFailure? = null,
        /** Roles the server said have no place, whatever the project in the cache says. */
        val fullRoles: Set<RoleId> = emptySet(),
    )

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}
