package org.matchlab.academic_net.feature.projects.ui.edit

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectDraftError
import org.matchlab.academic_net.feature.projects.domain.ProjectInfoDraft
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.GetSpheresUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.LoadProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.RemoveRoleResult
import org.matchlab.academic_net.feature.projects.domain.usecase.RemoveRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UpdateProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UpdateRoleUseCase
import org.matchlab.academic_net.feature.projects.ui.create.DateField
import org.matchlab.academic_net.feature.projects.ui.role.RoleEditorState
import org.matchlab.academic_net.utils.withTag
import org.matchlab.academic_net.utils.withoutTag

/**
 * A role of the project as the edit form lists it.
 *
 * @property occupied places taken by people; a role with any cannot be deleted (API-GAP G64)
 */
@Immutable
data class EditableRole(
    val id: RoleId,
    val title: String,
    val skills: List<String>,
    val occupied: Int,
) {
    /** The trash is offered: nobody works on the role. */
    val canDelete: Boolean get() = occupied == 0
}

/** The note on top of the form (04m): something happened that the user has to know about. */
sealed interface EditBanner {
    /** The project changed on the server (a 409): "Проект изменился на сервере"; the form keeps what was typed. */
    data object Conflict : EditBanner

    /** The server refused to delete the role: it is not free (API-GAP G64): "Роль нельзя удалить". */
    data object RoleNotRemovable : EditBanner

    /** Deleting the role failed for [cause]; the banner says why (see `failureText`). */
    data class RoleDeleteFailed(val cause: Throwable) : EditBanner
}

/**
 * What "04d Редактирование" shows. The screen reads it in this order: [isGone] (the screen "Проект не найден"),
 * [isFormReady] (the form), [loadFailure] (the full-screen error with "Повторить"), and [isLoading] otherwise (the
 * progress indicator).
 *
 * @property isFormReady the project was read and the form was filled from it, once; the roles follow the project after
 *  that, the typed fields do not
 * @property draft the description as typed
 * @property errors field errors; empty until the first attempt to save, then kept up to date while editing
 * @property roles the roles of the project as the cache has them
 * @property monthPickerFor the month picker is open for this field
 * @property roleEditor the role sheet, `null` while it is closed
 * @property roleFailure why sending the role failed, shown inside the sheet; [ProjectConflictException] is the project
 *  having changed
 * @property roleToDelete the confirmation "Удалить роль?" is open for this role
 * @property isSaving "Сохранить изменения" is on its way, or has ended well and the screen is leaving
 * @property saveFailure why saving failed, under the button
 * @property isSaved the changes are saved: the screen closes ([isSaving] stays on until it has)
 */
@Immutable
data class EditProjectUiState(
    val isFormReady: Boolean = false,
    val isGone: Boolean = false,
    val loadFailure: Throwable? = null,
    val draft: ProjectInfoDraft = ProjectInfoDraft(),
    val errors: Set<ProjectDraftError> = emptySet(),
    val roles: List<EditableRole> = emptyList(),
    val spheres: List<Sphere> = emptyList(),
    val spheresLoadFailed: Boolean = false,
    val isSpherePickerVisible: Boolean = false,
    val monthPickerFor: DateField? = null,
    val roleEditor: RoleEditorState? = null,
    val isSavingRole: Boolean = false,
    val roleFailure: Throwable? = null,
    val roleToDelete: EditableRole? = null,
    val isRemovingRole: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailure: Throwable? = null,
    val isSaved: Boolean = false,
    val banner: EditBanner? = null,
) {
    /** Nothing is known about the project yet, and nothing has failed. */
    val isLoading: Boolean get() = !isFormReady && !isGone && loadFailure == null
}

/**
 * "04d Редактирование": "редактировать основную информацию, роли, теги", "удалять роли".
 *
 * **Form.** It is filled once, from the first project the cache has ([ProjectInfoDraft.of]; a value the app does not
 * know is empty and must be chosen). The roles follow the project from the cache afterwards, the typed fields are never
 * overwritten.
 *
 * **Saving.** [onSaveClick] sends the description (the roles are not part of it, API-GAP G63); a form that does not
 * pass the checks shows the errors and sends nothing. It does nothing while a request is on its way and once the
 * changes are saved ([EditProjectUiState.isSaved]): the guard stays on while the form leaves the screen. A stale
 * revision ([ProjectConflictException]) is the banner [EditBanner.Conflict] and the form keeps what was typed; a project
 * that is gone is "Проект не найден"; any other failure is [EditProjectUiState.saveFailure].
 *
 * **Roles** are changed by requests of their own, at once, not by "Сохранить изменения": the pencil opens the role
 * sheet ([onRoleEditClick]), "Добавить роль" the same sheet empty, the trash of a role nobody works on opens the
 * confirmation. Each of the three is guarded against a second start.
 */
class EditProjectViewModel(
    private val projectId: ProjectId,
    observeProject: ObserveProjectUseCase,
    private val loadProject: LoadProjectUseCase,
    private val updateProject: UpdateProjectUseCase,
    private val addRole: AddRoleUseCase,
    private val updateRole: UpdateRoleUseCase,
    private val removeRole: RemoveRoleUseCase,
    private val getSpheres: GetSpheresUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(EditProjectUiState())
    val uiState: StateFlow<EditProjectUiState> = state.asStateFlow()

    private var showErrors = false
    private var loadJob: Job? = null
    private var spheresJob: Job? = null

    init {
        viewModelScope.launch {
            observeProject(projectId)
                .catch { cause ->
                    // The flow never fails by its contract; if it does, the screen offers to try again.
                    state.update { it.copy(loadFailure = cause) }
                }
                .collect { project -> if (project != null) onProject(project) }
        }
        load()
        loadSpheres()
    }

    /** "Повторить" of the full-screen error: reads the project again, unless a read is on its way. */
    fun onRetry() {
        if (state.value.loadFailure == null || loadJob?.isActive == true) return
        state.update { it.copy(loadFailure = null) }
        load()
    }

    fun onTitleChange(title: String) = editDraft { it.copy(title = title) }

    fun onTypeSelect(type: ProjectType) = editDraft { it.copy(type = type) }

    fun onProblemChange(problem: String) = editDraft { it.copy(problem = problem) }

    fun onGoalChange(goal: String) = editDraft { it.copy(goal = goal) }

    fun onAddTag(tag: String) = editDraft { it.copy(tags = it.tags.withTag(tag)) }

    fun onRemoveTag(tag: String) = editDraft { it.copy(tags = it.tags.withoutTag(tag)) }

    fun onSphereFieldClick() {
        state.update { it.copy(isSpherePickerVisible = true) }
        if (state.value.spheres.isEmpty()) loadSpheres()
    }

    fun onSphereSelect(sphere: Sphere) {
        editDraft { it.copy(sphere = sphere) }
        state.update { it.copy(isSpherePickerVisible = false) }
    }

    fun onSpherePickerDismiss() = state.update { it.copy(isSpherePickerVisible = false) }

    fun onSpheresRetry() = loadSpheres()

    fun onStartFieldClick() = state.update { it.copy(monthPickerFor = DateField.Start) }

    fun onEndFieldClick() = state.update { it.copy(monthPickerFor = DateField.End) }

    fun onMonthPickerDismiss() = state.update { it.copy(monthPickerFor = null) }

    fun onMonthSelect(month: YearMonth) {
        val field = state.value.monthPickerFor ?: return
        editDraft { draft ->
            when (field) {
                DateField.Start -> draft.copy(start = month)
                DateField.End -> draft.copy(end = month)
            }
        }
        state.update { it.copy(monthPickerFor = null) }
    }

    /** "Сохранить изменения". */
    fun onSaveClick() {
        val current = state.value
        // The form stays on the screen until it has closed: a tap in that time must not send the changes again.
        if (current.isSaving || current.isSaved || !current.isFormReady) return
        val errors = current.draft.normalized().validate()
        showErrors = true
        if (errors.isNotEmpty()) {
            state.update { it.copy(errors = errors) }
            return
        }
        state.update { it.copy(errors = emptySet(), isSaving = true, saveFailure = null, banner = null) }
        viewModelScope.launch {
            when (val result = updateProject(projectId, current.draft)) {
                is SubmitResult.Success -> state.update { it.copy(isSaved = true) }
                is SubmitResult.Invalid -> state.update { it.copy(isSaving = false, errors = result.errors) }
                is SubmitResult.Failure -> state.update {
                    when (result.cause) {
                        is ProjectNotFoundException -> it.copy(isSaving = false, isGone = true)
                        is ProjectConflictException -> it.copy(isSaving = false, banner = EditBanner.Conflict)
                        else -> it.copy(isSaving = false, saveFailure = result.cause)
                    }
                }
            }
        }
    }

    fun onAddRoleClick() = state.update { it.copy(roleEditor = RoleEditorState(), roleFailure = null) }

    /** The pencil of a role: the sheet opens with the role's title and skills. */
    fun onRoleEditClick(roleId: RoleId) {
        val role = state.value.roles.firstOrNull { it.id == roleId } ?: return
        state.update {
            it.copy(
                roleEditor = RoleEditorState(
                    editedRoleId = roleId,
                    draft = RoleDraft(role.title, role.skills),
                ),
                roleFailure = null,
            )
        }
    }

    fun onRoleTitleChange(title: String) = editRole { it.withTitle(title) }

    fun onRoleSkillAdd(skill: String) = editRole { it.withSkill(skill) }

    fun onRoleSkillRemove(skill: String) = editRole { it.withoutSkill(skill) }

    fun onRoleEditorDismiss() = state.update { it.copy(roleEditor = null, roleFailure = null) }

    /**
     * "Сохранить" in the role sheet: a new role is added, an existing one is updated. A role that does not pass the
     * checks keeps the sheet with the errors; a failure keeps it with [EditProjectUiState.roleFailure]; the project
     * having gone closes it and the screen says "Проект не найден". It does nothing while the role is being sent.
     */
    fun onRoleSave() {
        val current = state.value
        val editor = current.roleEditor ?: return
        if (current.isSavingRole) return
        state.update { it.copy(isSavingRole = true, roleFailure = null, banner = null) }
        viewModelScope.launch {
            val roleId = editor.editedRoleId
            val result = if (roleId != null) {
                updateRole(projectId, roleId, editor.draft)
            } else {
                addRole(projectId, editor.draft)
            }
            when (result) {
                is SubmitResult.Success -> state.update { it.copy(isSavingRole = false, roleEditor = null) }
                is SubmitResult.Invalid -> state.update {
                    it.copy(isSavingRole = false, roleEditor = editor.copy(errors = result.errors))
                }
                is SubmitResult.Failure -> state.update {
                    if (result.cause is ProjectNotFoundException) {
                        it.copy(isSavingRole = false, roleEditor = null, isGone = true)
                    } else {
                        it.copy(isSavingRole = false, roleFailure = result.cause)
                    }
                }
            }
        }
    }

    /** The trash of a role: opens "Удалить роль?". A role with people on it is not offered (and not accepted here). */
    fun onRoleDeleteClick(roleId: RoleId) {
        val current = state.value
        val role = current.roles.firstOrNull { it.id == roleId } ?: return
        if (!role.canDelete || current.isRemovingRole) return
        state.update { it.copy(roleToDelete = role, banner = null) }
    }

    fun onRoleDeleteDismiss() = state.update { it.copy(roleToDelete = null) }

    /**
     * "Удалить" in the confirmation. Only the open dialog confirms, and not while another deletion is on its way: the
     * button can be tapped again while the dialog fades out.
     */
    fun onRoleDeleteConfirm() {
        val current = state.value
        val role = current.roleToDelete ?: return
        if (current.isRemovingRole) return
        state.update { it.copy(roleToDelete = null, isRemovingRole = true, banner = null) }
        viewModelScope.launch {
            // The project without the role arrives through the observed project.
            when (val result = removeRole(projectId, role.id)) {
                is RemoveRoleResult.Removed -> state.update { it.copy(isRemovingRole = false) }
                RemoveRoleResult.NotRemovable -> state.update {
                    it.copy(isRemovingRole = false, banner = EditBanner.RoleNotRemovable)
                }
                RemoveRoleResult.NotFound -> state.update { it.copy(isRemovingRole = false, isGone = true) }
                is RemoveRoleResult.Failure -> state.update {
                    it.copy(isRemovingRole = false, banner = EditBanner.RoleDeleteFailed(result.cause))
                }
            }
        }
    }

    /** The cache has the project: the roles follow it, the form is filled the first time only. */
    private fun onProject(project: Project) = state.update { current ->
        current.copy(
            isFormReady = true,
            loadFailure = null,
            draft = if (current.isFormReady) current.draft else ProjectInfoDraft.of(project),
            roles = project.roles.map { EditableRole(it.id, it.title, it.skills, it.occupied) },
        )
    }

    private fun load() {
        loadJob = viewModelScope.launch {
            when (val result = loadProject(projectId)) {
                is LoadResult.Found -> Unit
                LoadResult.NotFound -> state.update { it.copy(isGone = true) }
                is LoadResult.Failure -> state.update { current ->
                    // A failed repeated read with a form on the screen changes nothing.
                    if (current.isFormReady) current else current.copy(loadFailure = result.cause)
                }
            }
        }
    }

    private fun editDraft(transform: (ProjectInfoDraft) -> ProjectInfoDraft) {
        state.update { current ->
            val draft = transform(current.draft)
            current.copy(
                draft = draft,
                errors = if (showErrors) draft.normalized().validate() else current.errors,
                saveFailure = null,
            )
        }
    }

    private fun editRole(transform: (RoleEditorState) -> RoleEditorState) {
        state.update { current -> current.copy(roleEditor = current.roleEditor?.let(transform)) }
    }

    /** Asks for the spheres unless an answer is still on its way: taps on "Повторить" are not doubled. */
    private fun loadSpheres() {
        if (spheresJob?.isActive == true) return
        spheresJob = viewModelScope.launch {
            getSpheres().fold(
                onSuccess = { spheres -> state.update { it.copy(spheres = spheres, spheresLoadFailed = false) } },
                onFailure = { state.update { it.copy(spheresLoadFailed = true) } },
            )
        }
    }
}
