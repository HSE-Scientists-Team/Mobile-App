package org.matchlab.academic_net.feature.projectdetails.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.projectdetails.domain.GetProjectDetailsUseCase
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetails as LoadedProject
import org.matchlab.academic_net.feature.projectdetails.domain.ProjectDetailsResult

/** The initiator as the card under the project title shows them. */
@Immutable
data class InitiatorUiModel(
    val user: UserSummary,
    /** Stands in for the initiator's picture until profiles carry images. */
    val avatarKey: String,
    /** As the server wrote it; may be blank. */
    val university: String,
    /** Average of reviews, `null` while there are none. */
    val rating: Double?,
    val completedProjects: Int,
    /** `false` on the viewer's own project: nobody writes to themselves. */
    val canContact: Boolean,
)

/** An open role of the project with the skills it needs. */
@Immutable
data class VacantRoleUiModel(
    val id: RoleId,
    val title: String,
    val skills: List<String>,
)

/** The project as the page shows it; carries no localized text, the screen builds the labels. */
@Immutable
data class ProjectDetailsUiModel(
    val title: String,
    val type: ProjectType,
    val status: ProjectStatus,
    val sphere: String,
    val problem: String,
    val goal: String,
    val tags: List<String>,
    val initiator: InitiatorUiModel,
    /** Only the open roles: the filled ones are counted in [occupiedRolesCount]. */
    val vacantRoles: List<VacantRoleUiModel>,
    val occupiedRolesCount: Int,
    val rolesCount: Int,
    val period: ProjectPeriod,
)

/** What the project page shows: the loading, the answer of the server or the failure. */
sealed interface ProjectDetailsUiState {
    /** The project is being requested. */
    data object Loading : ProjectDetailsUiState

    /** The server has no such project for the viewer: unknown or taken off publication. */
    data object NotFound : ProjectDetailsUiState

    /** The request failed; "Повторить" asks again. */
    data object Error : ProjectDetailsUiState

    /** The project was found; [project] is everything the page shows. */
    data class Content(val project: ProjectDetailsUiModel) : ProjectDetailsUiState
}

/**
 * "04 Проект": the page of someone else's project, read-only. It loads the project once, when it
 * is created, and again on [onRetry]. Applying to a role belongs to a later story.
 */
class ProjectDetailsViewModel(
    private val projectId: ProjectId,
    private val getProjectDetails: GetProjectDetailsUseCase,
) : ViewModel() {
    private val state = MutableStateFlow<ProjectDetailsUiState>(ProjectDetailsUiState.Loading)

    val uiState: StateFlow<ProjectDetailsUiState> = state.asStateFlow()

    init {
        load()
    }

    /** "Повторить" after a failed load; a request that is still running is not doubled. */
    fun onRetry() {
        if (state.value is ProjectDetailsUiState.Loading) return
        state.value = ProjectDetailsUiState.Loading
        load()
    }

    private fun load() {
        viewModelScope.launch {
            state.value = when (val result = getProjectDetails(projectId)) {
                is ProjectDetailsResult.Found -> ProjectDetailsUiState.Content(result.details.toUiModel())
                ProjectDetailsResult.NotFound -> ProjectDetailsUiState.NotFound
                is ProjectDetailsResult.Failure -> ProjectDetailsUiState.Error
            }
        }
    }
}

private fun LoadedProject.toUiModel(): ProjectDetailsUiModel = ProjectDetailsUiModel(
    title = project.title,
    type = project.type,
    status = project.status,
    sphere = project.sphere.name,
    problem = project.problem,
    goal = project.goal,
    tags = project.tags,
    initiator = InitiatorUiModel(
        user = project.initiator,
        avatarKey = project.initiator.id.value,
        university = initiator.university,
        rating = initiator.rating,
        completedProjects = initiator.completedProjects,
        canContact = !project.viewerIsInitiator,
    ),
    vacantRoles = project.vacantRoles.map { VacantRoleUiModel(it.id, it.title, it.skills) },
    occupiedRolesCount = project.occupiedRolesCount,
    rolesCount = project.roles.size,
    period = project.period,
)
