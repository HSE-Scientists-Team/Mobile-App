package org.matchlab.academic_net.feature.projects.ui.management

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
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.projects.domain.ProjectLifecycle
import org.matchlab.academic_net.feature.projects.domain.ProjectLifecycleAction
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ChangeLifecycleResult
import org.matchlab.academic_net.feature.projects.domain.usecase.ChangeProjectLifecycleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.LoadProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.RemoveMemberResult
import org.matchlab.academic_net.feature.projects.domain.usecase.RemoveMemberUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectResult
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectUseCase
import org.matchlab.academic_net.feature.projects.ui.role.RoleEditorState

/**
 * A person who works on a role, as the roles tab shows them (the server lists the members of a project, their profiles
 * are read separately, API-GAP G47).
 *
 * @property name empty without a profile: the card says "Без имени"
 * @property hasProfile the app could read the person's profile: only then the card has stats and opens the profile; a
 *  person without one is a card with the neutral avatar, no numbers and no tap
 * @property canKick "Выгнать" is offered: the viewer is the initiator and the person is neither the initiator nor the
 *  viewer (07b); a person without a profile is kicked all the same (their record stays, G52)
 * @property canContact the chat button is offered: the profile is known, the id is not empty and the person is not the
 *  initiator
 */
@Immutable
data class RoleMemberItem(
    val userId: UserId,
    val name: String,
    val avatarKey: String,
    val rating: Double?,
    val completedProjects: Int,
    val hasProfile: Boolean = true,
    val canKick: Boolean = false,
    val canContact: Boolean = false,
)

/**
 * A role of the project as the roles tab shows it.
 *
 * @property slots places of the role, at least 1 (API-GAP G49)
 * @property occupied places taken, as the server counts them but never more than [slots]
 * @property members the people on the role the app knows; may differ from [occupied] (API-GAP G52)
 * @property canFindPeople the search for people for the role is offered: to the initiator, for a role with a free place
 */
@Immutable
data class RoleItem(
    val id: RoleId,
    val title: String,
    val skills: List<String>,
    val slots: Int,
    val occupied: Int,
    val members: List<RoleMemberItem>,
    val canFindPeople: Boolean,
) {
    /** A place is free. */
    val isVacant: Boolean get() = occupied < slots

    /**
     * The tab draws a card of the role: it has a free place, or places taken by people the app does not know. A role
     * whose every taken place has its person is told by the people alone.
     */
    val showsCard: Boolean get() = isVacant || occupied > members.size

    /** The card has the line "1 из 3 мест занято": the role has several places, or has none free. */
    val showsPlaces: Boolean get() = slots > 1 || !isVacant
}

/**
 * The project as the three management tabs show it. What the app does not know about it (a value the server sent in a
 * form the app does not recognize, see `docs/api-values.md`) is `null` and is left out of the screen.
 *
 * @property type `null` when unknown: the overview has no chip
 * @property status `null` when unknown: the overview has no chip and 07c has no banner of the status
 * @property sphere the name of the sphere, `null` when unknown: the overview has no chip
 * @property period `null` when unknown: the overview has no card of the months
 * @property occupiedPlaces taken places of all roles, the "2" of "2 из 4 занято"
 * @property totalPlaces places of all roles, the "4" of "2 из 4 занято"
 * @property canUnpublish "Снять с публикации" is offered: the source can do it, the viewer is the initiator and
 *  [ProjectLifecycle.canUnpublish] holds for the status (recruiting, or unknown)
 * @property lifecycleActions "Начать", "Завершить", "Отменить проект" in this order: [ProjectLifecycle.actionsFor] the
 *  status, only for the initiator and only when the source can do it ([ProjectsCapabilities.canChangeLifecycle]); an
 *  unknown status offers all of them
 * @property canEdit the pencil of the app bar: the viewer is the initiator and the project is neither finished nor
 *  cancelled
 * @property offersEditAction the row "Редактировать проект" of 07c: the initiator, and the status is unknown, recruiting
 *  or unpublished (07e of the design, a project in progress, has the pencil but no such row)
 * @property showsResponses the row "Отклики · N" opens 07d: only the initiator has it
 * @property pendingJoinCount the "N" of that row (API-GAP G61: the server's `pending_join_count`); 0 is a row without a
 *  number
 */
@Immutable
data class ProjectDetails(
    val id: ProjectId,
    val title: String,
    val type: ProjectType?,
    val status: ProjectStatus?,
    val sphere: String?,
    val problem: String,
    val goal: String,
    val tags: List<String>,
    val period: ProjectPeriod?,
    val roles: List<RoleItem>,
    val occupiedPlaces: Int,
    val totalPlaces: Int,
    val canUnpublish: Boolean,
    val lifecycleActions: List<ProjectLifecycleAction> = emptyList(),
    val canEdit: Boolean = false,
    val offersEditAction: Boolean = false,
    val showsResponses: Boolean = false,
    val pendingJoinCount: Int = 0,
) {
    val rolesCount: Int get() = roles.size
}

/**
 * What "07 Управление проектом" shows. The screen reads it in this order: [project] (the three tabs), [loadFailure]
 * (the full-screen error with "Повторить"), [isLoading], and when none of them holds the project is not there
 * ("Проект не найден").
 *
 * @property isLoading the project is being read and nothing is known about it yet
 * @property project `null` while loading, after a failure that left nothing to show, and when the project does not
 *  exist
 * @property loadFailure why reading the project failed while there is nothing to show; `null` otherwise (a failure of
 *  a repeated read shows the project from the cache, not an error)
 * @property roleFailure why sending the new role failed, shown inside the role sheet, which stays open; cleared by the
 *  next try and by closing the sheet. [org.matchlab.academic_net.feature.projects.domain.ProjectConflictException] is
 *  the project having changed on the server: the screen says so in its own words
 * @property lifecycleDialog the confirmation of "Начать", "Завершить" or "Отменить проект" is open for this action
 * @property isChangingLifecycle the request of the action is on its way: the screen does not offer another one
 * @property lifecycleFailure why the last move of the project failed; cleared by the next action
 * @property actionFailed unpublishing has failed; cleared by the next action
 * @property kickDialog the confirmation 07i is open for this member
 * @property removingMember the member whose removal is on its way: their card shows an arc in "Выгнать" (07u)
 * @property memberFailure why the last removal failed (07v); cleared by the next one
 */
@Immutable
data class ProjectManagementUiState(
    val isLoading: Boolean = true,
    val project: ProjectDetails? = null,
    val loadFailure: Throwable? = null,
    val isUnpublishConfirmVisible: Boolean = false,
    val isUnpublishing: Boolean = false,
    val lifecycleDialog: ProjectLifecycleAction? = null,
    val isChangingLifecycle: Boolean = false,
    val lifecycleFailure: LifecycleFailure? = null,
    val roleEditor: RoleEditorState? = null,
    val isSavingRole: Boolean = false,
    val roleFailure: Throwable? = null,
    val actionFailed: Boolean = false,
    val kickDialog: KickTarget? = null,
    val removingMember: UserId? = null,
    val memberFailure: MemberFailure? = null,
)

/** The member the confirmation 07i is about. */
@Immutable
data class KickTarget(val userId: UserId, val name: String)

/** Why "Выгнать" did not remove the member. */
sealed interface MemberFailure {
    /** The server refused (403, 409), or the member is the initiator or the viewer: "Действие недоступно". */
    data object NotAllowed : MemberFailure

    /** The request failed for [cause]: "Не удалось выгнать участника. Попробуйте ещё раз" (07v). */
    data class Failed(val cause: Throwable) : MemberFailure
}

/** Why "Начать", "Завершить" or "Отменить проект" did not move the project. */
sealed interface LifecycleFailure {
    /**
     * The action is not available in the project's state: it is not offered for the status, or the server refused it
     * (a 409; the project has been read again): "Действие недоступно в текущем состоянии проекта".
     */
    data object NotAllowed : LifecycleFailure

    /** The request failed for [cause]; the screen says why (see `failureText`). */
    data class Failed(val cause: Throwable) : LifecycleFailure
}

/**
 * "07 Управление проектом": "просматривать роли", "снять проект с публикации", "начать, завершить, отменить проект",
 * "объявить роли".
 *
 * **Reading.** The project is read from the server when the view model is created (the use case puts it into
 * [observeProject]), and the screen shows what [observeProject] has: a project read before is on the screen at once and
 * is refreshed in the background. A failure to read with nothing to show is the full-screen error, [onRetry] asks again
 * (a second tap while the read is on its way does nothing); a failure with a project on the screen changes nothing.
 * The server's "no such project" ([LoadResult.NotFound], or [ProjectNotFoundException] when adding a role) is the
 * screen "Проект не найден".
 *
 * **What the source cannot do** is not offered: "Снять с публикации" only with [ProjectsCapabilities.canUnpublish], to
 * the initiator, and while [ProjectLifecycle.canUnpublish] holds for the status (an unknown status does not hide the
 * action); "Начать", "Завершить" and "Отменить проект" only with [ProjectsCapabilities.canChangeLifecycle], to the
 * initiator, as [ProjectLifecycle.actionsFor] says.
 *
 * **Lifecycle.** An action opens its confirmation ([onLifecycleClick]); [onLifecycleConfirm] sends it. A move the
 * server refused is [LifecycleFailure.NotAllowed], a project that is gone is the screen "Проект не найден", another
 * failure is told by its kind. The project's new status arrives through the observed project.
 *
 * **Team.** "Выгнать" opens the confirmation ([onKickClick]); [onKickConfirm] removes the member under the guard of
 * [ProjectManagementUiState.removingMember]. The project without the member arrives through the observed project. The
 * server's refusal (or a member who is the initiator or the viewer) is [MemberFailure.NotAllowed], a project that is gone
 * is "Проект не найден", another failure is [MemberFailure.Failed].
 *
 * **Guards.** Unpublishing, moving the project and adding a role cannot start twice: [onUnpublishConfirm] and
 * [onLifecycleConfirm] work only for their open dialog and not while a request of either is on its way, [onRoleSave]
 * only for the open sheet and not while it is saving.
 */
class ProjectManagementViewModel(
    private val projectId: ProjectId,
    observeProject: ObserveProjectUseCase,
    private val loadProject: LoadProjectUseCase,
    private val unpublishProject: UnpublishProjectUseCase,
    private val addRole: AddRoleUseCase,
    private val changeLifecycle: ChangeProjectLifecycleUseCase,
    private val removeMember: RemoveMemberUseCase,
    private val capabilities: ProjectsCapabilities,
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())
    private var loadJob: Job? = null

    val uiState: StateFlow<ProjectManagementUiState> = combine(
        observeProject(projectId).catch { cause ->
            // The flow never fails by its contract; if it does, the screen offers to try again.
            local.update { it.copy(loadFailure = cause) }
            emit(null)
        },
        local,
    ) { observed, own ->
        val project = if (own.isGone) null else observed?.toDetails(capabilities)
        ProjectManagementUiState(
            isLoading = project == null && !own.isGone && own.loadFailure == null,
            project = project,
            loadFailure = if (project == null) own.loadFailure else null,
            isUnpublishConfirmVisible = own.isUnpublishConfirmVisible,
            isUnpublishing = own.isUnpublishing,
            lifecycleDialog = own.lifecycleDialog,
            isChangingLifecycle = own.isChangingLifecycle,
            lifecycleFailure = own.lifecycleFailure,
            roleEditor = own.roleEditor,
            isSavingRole = own.isSavingRole,
            roleFailure = own.roleFailure,
            actionFailed = own.actionFailed,
            kickDialog = own.kickDialog,
            removingMember = own.removingMember,
            memberFailure = own.memberFailure,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), ProjectManagementUiState())

    init {
        load()
    }

    /** "Повторить" of the full-screen error: reads the project again. It does nothing while a read is on its way. */
    fun onRetry() {
        if (local.value.loadFailure == null || loadJob?.isActive == true) return
        local.update { it.copy(loadFailure = null) }
        load()
    }

    fun onUnpublishClick() = local.update {
        if (it.isChangingLifecycle) it else it.copy(isUnpublishConfirmVisible = true, actionFailed = false, lifecycleFailure = null)
    }

    fun onUnpublishDismiss() = local.update { it.copy(isUnpublishConfirmVisible = false) }

    fun onUnpublishConfirm() {
        // Only the open dialog confirms. Its button can be tapped again while the dialog fades out,
        // which is after the request has started, and may be after it has ended.
        val current = local.value
        if (!current.isUnpublishConfirmVisible || current.isUnpublishing || current.isChangingLifecycle) return
        local.update { it.copy(isUnpublishConfirmVisible = false, isUnpublishing = true, actionFailed = false) }
        viewModelScope.launch {
            val result = unpublishProject(projectId)
            // The project's new status arrives through the observed project.
            val failed = result is UnpublishProjectResult.Failure || result is UnpublishProjectResult.NotFound
            local.update { it.copy(isUnpublishing = false, actionFailed = failed) }
        }
    }

    /** An action of 07c ("Начать", "Завершить", "Отменить проект") opens its confirmation. */
    fun onLifecycleClick(action: ProjectLifecycleAction) = local.update {
        if (it.isChangingLifecycle || it.isUnpublishing) {
            it
        } else {
            it.copy(lifecycleDialog = action, actionFailed = false, lifecycleFailure = null)
        }
    }

    fun onLifecycleDismiss() = local.update { it.copy(lifecycleDialog = null) }

    /**
     * "Начать", "Завершить" or "Отменить" in the confirmation. Only the open dialog confirms, and not while a move or
     * unpublishing is on its way: the button can be tapped again while the dialog fades out.
     */
    fun onLifecycleConfirm() {
        val current = local.value
        val action = current.lifecycleDialog ?: return
        if (current.isChangingLifecycle || current.isUnpublishing) return
        local.update { it.copy(lifecycleDialog = null, isChangingLifecycle = true, lifecycleFailure = null) }
        viewModelScope.launch {
            when (val result = changeLifecycle(projectId, action)) {
                // The project's new status arrives through the observed project.
                is ChangeLifecycleResult.Changed -> local.update { it.copy(isChangingLifecycle = false) }
                ChangeLifecycleResult.NotAllowed -> local.update {
                    it.copy(isChangingLifecycle = false, lifecycleFailure = LifecycleFailure.NotAllowed)
                }
                ChangeLifecycleResult.NotFound -> local.update { it.copy(isChangingLifecycle = false, isGone = true) }
                is ChangeLifecycleResult.Failure -> local.update {
                    it.copy(isChangingLifecycle = false, lifecycleFailure = LifecycleFailure.Failed(result.cause))
                }
            }
        }
    }

    /** "Выгнать" of a member: opens 07i. Only for a member the card offers it for, and not while a removal runs. */
    fun onKickClick(userId: UserId) {
        val member = uiState.value.project?.roles?.flatMap { it.members }?.firstOrNull { it.userId == userId }
        if (member == null || !member.canKick) return
        local.update {
            if (it.removingMember != null) it else it.copy(kickDialog = KickTarget(userId, member.name), memberFailure = null)
        }
    }

    fun onKickDismiss() = local.update { it.copy(kickDialog = null) }

    /** "Выгнать" of 07i: works once per dialog (the button can be tapped again while the dialog fades out). */
    fun onKickConfirm() {
        var target: KickTarget? = null
        local.update {
            target = if (it.removingMember == null) it.kickDialog else null
            if (target != null) it.copy(kickDialog = null, removingMember = target.userId) else it.copy(kickDialog = null)
        }
        val kicked = target ?: return
        viewModelScope.launch {
            when (val result = removeMember(projectId, kicked.userId)) {
                // The project without the member arrives through the observed project.
                is RemoveMemberResult.Removed -> local.update { it.copy(removingMember = null) }
                RemoveMemberResult.NotAllowed ->
                    local.update { it.copy(removingMember = null, memberFailure = MemberFailure.NotAllowed) }
                RemoveMemberResult.NotFound -> local.update { it.copy(removingMember = null, isGone = true) }
                is RemoveMemberResult.Failure ->
                    local.update { it.copy(removingMember = null, memberFailure = MemberFailure.Failed(result.cause)) }
            }
        }
    }

    fun onAddRoleClick() =
        local.update { it.copy(roleEditor = RoleEditorState(), roleFailure = null, actionFailed = false) }

    fun onRoleTitleChange(title: String) = editRole { it.withTitle(title) }

    fun onRoleSkillAdd(skill: String) = editRole { it.withSkill(skill) }

    fun onRoleSkillRemove(skill: String) = editRole { it.withoutSkill(skill) }

    fun onRoleEditorDismiss() = local.update { it.copy(roleEditor = null, roleFailure = null) }

    /**
     * "Сохранить" in the role sheet. A role that does not pass the checks keeps the sheet with the errors, a failure
     * keeps it with [ProjectManagementUiState.roleFailure] so that the user can try again; the project having gone
     * (a [ProjectNotFoundException]) closes it and the screen says "Проект не найден".
     */
    fun onRoleSave() {
        val state = local.value
        val editor = state.roleEditor ?: return
        if (state.isSavingRole) return
        local.update { it.copy(isSavingRole = true, roleFailure = null, actionFailed = false) }
        viewModelScope.launch {
            when (val result = addRole(projectId, editor.draft)) {
                is SubmitResult.Success -> local.update { it.copy(isSavingRole = false, roleEditor = null) }
                is SubmitResult.Invalid -> local.update {
                    it.copy(isSavingRole = false, roleEditor = editor.copy(errors = result.errors))
                }
                is SubmitResult.Failure -> local.update {
                    if (result.cause is ProjectNotFoundException) {
                        it.copy(isSavingRole = false, roleEditor = null, isGone = true)
                    } else {
                        it.copy(isSavingRole = false, roleFailure = result.cause)
                    }
                }
            }
        }
    }

    private fun editRole(transform: (RoleEditorState) -> RoleEditorState) {
        local.update { it.copy(roleEditor = it.roleEditor?.let(transform)) }
    }

    /**
     * Reads the project; the project itself arrives through the observed flow, so a found one changes nothing here
     * (the screen keeps its progress indicator until the flow has it, whatever the order of the two).
     */
    private fun load() {
        loadJob = viewModelScope.launch {
            when (val result = loadProject(projectId)) {
                is LoadResult.Found -> Unit
                LoadResult.NotFound -> local.update { it.copy(isGone = true) }
                is LoadResult.Failure -> local.update { it.copy(loadFailure = result.cause) }
            }
        }
    }

    private data class LocalState(
        /** Reading the project failed. */
        val loadFailure: Throwable? = null,
        /** The server said there is no such project, whatever the observed flow still holds. */
        val isGone: Boolean = false,
        val isUnpublishConfirmVisible: Boolean = false,
        val isUnpublishing: Boolean = false,
        val lifecycleDialog: ProjectLifecycleAction? = null,
        val isChangingLifecycle: Boolean = false,
        val lifecycleFailure: LifecycleFailure? = null,
        val roleEditor: RoleEditorState? = null,
        val isSavingRole: Boolean = false,
        val roleFailure: Throwable? = null,
        val actionFailed: Boolean = false,
        val kickDialog: KickTarget? = null,
        val removingMember: UserId? = null,
        val memberFailure: MemberFailure? = null,
    )

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}

internal fun Project.toDetails(capabilities: ProjectsCapabilities) = ProjectDetails(
    id = id,
    title = title,
    type = type,
    status = status,
    sphere = sphere?.name,
    problem = problem,
    goal = goal,
    tags = tags,
    period = period,
    roles = roles.map { role ->
        RoleItem(
            id = role.id,
            title = role.title,
            skills = role.skills,
            slots = role.slots,
            occupied = minOf(role.occupied, role.slots),
            members = role.members.map { member ->
                val isInitiator = member.user.id == initiator.id
                RoleMemberItem(
                    userId = member.user.id,
                    name = member.user.name,
                    avatarKey = member.user.id.value,
                    rating = member.rating,
                    completedProjects = member.completedProjects,
                    hasProfile = member.hasProfile,
                    // Only the initiator removes people, and never themselves (the viewer of this screen is the initiator).
                    canKick = viewerIsInitiator && !isInitiator,
                    canContact = !isInitiator && member.hasProfile && member.user.id.value.isNotBlank(),
                )
            },
            // Only the initiator looks for people, and only for a role with a free place.
            canFindPeople = viewerIsInitiator && role.isVacant,
        )
    },
    occupiedPlaces = occupiedPlaces,
    totalPlaces = totalPlaces,
    // A source that cannot unpublish, a viewer who is not the initiator and a status that is neither recruiting nor
    // unknown have no action (docs/api-values.md: an unknown status is not a reason to hide it).
    canUnpublish = capabilities.canUnpublish && viewerIsInitiator && ProjectLifecycle.canUnpublish(status),
    lifecycleActions = if (capabilities.canChangeLifecycle && viewerIsInitiator) {
        ProjectLifecycle.actionsFor(status)
    } else {
        emptyList()
    },
    canEdit = viewerIsInitiator && status != ProjectStatus.Finished && status != ProjectStatus.Cancelled,
    offersEditAction = viewerIsInitiator &&
        (status == null || status == ProjectStatus.Recruiting || status == ProjectStatus.Unpublished),
    showsResponses = viewerIsInitiator,
    pendingJoinCount = pendingJoinCount,
)
