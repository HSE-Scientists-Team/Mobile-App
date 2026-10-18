package org.matchlab.academic_net.feature.projects.ui.create

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.result.FeatureUnavailableException
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.projects.domain.ProjectDraft
import org.matchlab.academic_net.feature.projects.domain.ProjectDraftError
import org.matchlab.academic_net.feature.projects.domain.usecase.GetSpheresUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.PublishProjectUseCase
import org.matchlab.academic_net.feature.projects.ui.role.RoleEditorState
import org.matchlab.academic_net.utils.withTag
import org.matchlab.academic_net.utils.withoutTag

/** The two month fields of the form. */
enum class DateField { Start, End }

@Immutable
data class CreateProjectUiState(
    val draft: ProjectDraft = ProjectDraft(),
    /** Field errors; empty until the first attempt to publish, then kept up to date while editing. */
    val errors: Set<ProjectDraftError> = emptySet(),
    val spheres: List<Sphere> = emptyList(),
    val spheresLoadFailed: Boolean = false,
    val isSpherePickerVisible: Boolean = false,
    /** The month picker is open for this field, `null` while it is closed. */
    val monthPickerFor: DateField? = null,
    /** The role sheet, `null` while it is closed. */
    val roleEditor: RoleEditorState? = null,
    val isPublishing: Boolean = false,
    /** Why publishing has failed; cleared by the next edit and by the next attempt. `null` while nothing has failed. */
    val publishFailure: Throwable? = null,
    /** Set once the project is published: the screen leaves for it. */
    val publishedProjectId: ProjectId? = null,
) {
    /** Publishing has failed, for whatever reason. */
    val publishFailed: Boolean get() = publishFailure != null

    /**
     * The server cannot create projects at all (API-GAP G40, G43), which is not a failure to try again: the screen says
     * "Создание проектов пока недоступно на сервере" instead of "Не удалось опубликовать проект".
     */
    val publishUnavailable: Boolean get() = publishFailure is FeatureUnavailableException
}

/** "05 Создание проекта": "опубликовать новый проект", "объявить роли", "выбрать сферу проекта". */
class CreateProjectViewModel(
    private val publishProject: PublishProjectUseCase,
    private val getSpheres: GetSpheresUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(CreateProjectUiState())
    val uiState: StateFlow<CreateProjectUiState> = state.asStateFlow()

    private var showErrors = false
    private var spheresJob: Job? = null

    init {
        loadSpheres()
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

    fun onAddRoleClick() = state.update { it.copy(roleEditor = RoleEditorState()) }

    fun onEditRoleClick(index: Int) {
        val role = state.value.draft.roles.getOrNull(index) ?: return
        state.update { it.copy(roleEditor = RoleEditorState(editedIndex = index, draft = role)) }
    }

    fun onDeleteRoleClick(index: Int) = editDraft { draft ->
        draft.copy(roles = draft.roles.filterIndexed { i, _ -> i != index })
    }

    fun onRoleTitleChange(title: String) = editRole { it.withTitle(title) }

    fun onRoleSkillAdd(skill: String) = editRole { it.withSkill(skill) }

    fun onRoleSkillRemove(skill: String) = editRole { it.withoutSkill(skill) }

    fun onRoleSave() {
        val editor = state.value.roleEditor ?: return
        val checked = editor.validated()
        if (checked.errors.isNotEmpty()) {
            state.update { it.copy(roleEditor = checked) }
            return
        }
        val role = checked.draft.normalized()
        editDraft { draft ->
            val index = checked.editedIndex
            val roles = if (index != null && index in draft.roles.indices) {
                draft.roles.toMutableList().also { it[index] = role }
            } else {
                draft.roles + role
            }
            draft.copy(roles = roles)
        }
        state.update { it.copy(roleEditor = null) }
    }

    fun onRoleEditorDismiss() = state.update { it.copy(roleEditor = null) }

    fun onPublishClick() {
        val current = state.value
        // One form makes one project. While the request runs the button does nothing, and neither
        // does it once the project exists: the form stays on the screen until the transition to the
        // new project is over, and a tap in that time must not publish a second copy.
        if (current.isPublishing || current.publishedProjectId != null) return
        val errors = current.draft.normalized().validate()
        showErrors = true
        if (errors.isNotEmpty()) {
            state.update { it.copy(errors = errors) }
            return
        }
        state.update { it.copy(errors = emptySet(), isPublishing = true, publishFailure = null) }
        viewModelScope.launch {
            when (val result = publishProject(current.draft)) {
                is SubmitResult.Success -> state.update {
                    it.copy(isPublishing = false, publishedProjectId = result.value.id)
                }
                is SubmitResult.Invalid -> state.update {
                    it.copy(isPublishing = false, errors = result.errors)
                }
                is SubmitResult.Failure -> state.update {
                    it.copy(isPublishing = false, publishFailure = result.cause)
                }
            }
        }
    }

    private fun editDraft(transform: (ProjectDraft) -> ProjectDraft) {
        state.update { current ->
            val draft = transform(current.draft)
            current.copy(
                draft = draft,
                errors = if (showErrors) draft.normalized().validate() else current.errors,
                publishFailure = null,
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
                onSuccess = { spheres ->
                    state.update { it.copy(spheres = spheres, spheresLoadFailed = false) }
                },
                onFailure = { state.update { it.copy(spheresLoadFailed = true) } },
            )
        }
    }
}
