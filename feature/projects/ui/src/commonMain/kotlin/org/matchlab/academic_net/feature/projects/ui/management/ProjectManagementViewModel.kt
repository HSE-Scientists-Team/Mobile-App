package org.matchlab.academic_net.feature.projects.ui.management

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectResult
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectUseCase
import org.matchlab.academic_net.feature.projects.ui.role.RoleEditorState

@Immutable
data class OccupiedRoleItem(
    val roleTitle: String,
    val memberName: String,
    val isMemberVerified: Boolean,
    val avatarKey: String,
    val rating: Double?,
    val completedProjects: Int,
)

@Immutable
data class VacantRoleItem(
    val id: RoleId,
    val title: String,
    val skills: List<String>,
    /** The search for people for this role is offered: to the project initiator only. */
    val canFindPeople: Boolean,
)

/** The project as the three management tabs show it. */
@Immutable
data class ProjectDetails(
    val id: ProjectId,
    val title: String,
    val type: ProjectType,
    val status: ProjectStatus,
    val sphere: String,
    val problem: String,
    val goal: String,
    val tags: List<String>,
    val period: ProjectPeriod,
    val occupiedRoles: List<OccupiedRoleItem>,
    val vacantRoles: List<VacantRoleItem>,
) {
    val rolesCount: Int get() = occupiedRoles.size + vacantRoles.size
    val canUnpublish: Boolean get() = status == ProjectStatus.Recruiting
}

@Immutable
data class ProjectManagementUiState(
    val isLoading: Boolean = true,
    /** `null` while loading, or when the project does not exist ([isLoading] is `false`). */
    val project: ProjectDetails? = null,
    val isUnpublishConfirmVisible: Boolean = false,
    val isUnpublishing: Boolean = false,
    val roleEditor: RoleEditorState? = null,
    val isSavingRole: Boolean = false,
    /** Unpublishing or adding a role has failed; cleared by the next action. */
    val actionFailed: Boolean = false,
)

/** "07 Управление проектом": "просматривать роли", "снять проект с публикации", "объявить роли". */
class ProjectManagementViewModel(
    private val projectId: ProjectId,
    observeProject: ObserveProjectUseCase,
    private val unpublishProject: UnpublishProjectUseCase,
    private val addRole: AddRoleUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<ProjectManagementUiState> = combine(
        observeProject(projectId).catch { emit(null) },
        local,
    ) { project, local ->
        ProjectManagementUiState(
            isLoading = false,
            project = project?.toDetails(),
            isUnpublishConfirmVisible = local.isUnpublishConfirmVisible,
            isUnpublishing = local.isUnpublishing,
            roleEditor = local.roleEditor,
            isSavingRole = local.isSavingRole,
            actionFailed = local.actionFailed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), ProjectManagementUiState())

    fun onUnpublishClick() = local.update { it.copy(isUnpublishConfirmVisible = true, actionFailed = false) }

    fun onUnpublishDismiss() = local.update { it.copy(isUnpublishConfirmVisible = false) }

    fun onUnpublishConfirm() {
        // Only the open dialog confirms. Its button can be tapped again while the dialog fades out,
        // which is after the request has started, and may be after it has ended.
        val current = local.value
        if (!current.isUnpublishConfirmVisible || current.isUnpublishing) return
        local.update { it.copy(isUnpublishConfirmVisible = false, isUnpublishing = true, actionFailed = false) }
        viewModelScope.launch {
            val result = unpublishProject(projectId)
            // The project's new status arrives through the observed project.
            val failed = result is UnpublishProjectResult.Failure || result is UnpublishProjectResult.NotFound
            local.update { it.copy(isUnpublishing = false, actionFailed = failed) }
        }
    }

    fun onAddRoleClick() = local.update { it.copy(roleEditor = RoleEditorState(), actionFailed = false) }

    fun onRoleTitleChange(title: String) = editRole { it.withTitle(title) }

    fun onRoleSkillAdd(skill: String) = editRole { it.withSkill(skill) }

    fun onRoleSkillRemove(skill: String) = editRole { it.withoutSkill(skill) }

    fun onRoleEditorDismiss() = local.update { it.copy(roleEditor = null) }

    fun onRoleSave() {
        val state = local.value
        val editor = state.roleEditor ?: return
        if (state.isSavingRole) return
        local.update { it.copy(isSavingRole = true, actionFailed = false) }
        viewModelScope.launch {
            when (val result = addRole(projectId, editor.draft)) {
                is SubmitResult.Success -> local.update { it.copy(isSavingRole = false, roleEditor = null) }
                is SubmitResult.Invalid -> local.update {
                    it.copy(isSavingRole = false, roleEditor = editor.copy(errors = result.errors))
                }
                is SubmitResult.Failure -> local.update { it.copy(isSavingRole = false, actionFailed = true) }
            }
        }
    }

    private fun editRole(transform: (RoleEditorState) -> RoleEditorState) {
        local.update { it.copy(roleEditor = it.roleEditor?.let(transform)) }
    }

    private data class LocalState(
        val isUnpublishConfirmVisible: Boolean = false,
        val isUnpublishing: Boolean = false,
        val roleEditor: RoleEditorState? = null,
        val isSavingRole: Boolean = false,
        val actionFailed: Boolean = false,
    )

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}

internal fun Project.toDetails() = ProjectDetails(
    id = id,
    title = title,
    type = type,
    status = status,
    sphere = sphere.name,
    problem = problem,
    goal = goal,
    tags = tags,
    period = period,
    occupiedRoles = roles.mapNotNull { role ->
        val member = role.member ?: return@mapNotNull null
        OccupiedRoleItem(
            roleTitle = role.title,
            memberName = member.user.name,
            isMemberVerified = member.user.isVerified,
            avatarKey = member.user.id.value,
            rating = member.rating,
            completedProjects = member.completedProjects,
        )
    },
    vacantRoles = vacantRoles.map { VacantRoleItem(it.id, it.title, it.skills, canFindPeople = viewerIsInitiator) },
)
