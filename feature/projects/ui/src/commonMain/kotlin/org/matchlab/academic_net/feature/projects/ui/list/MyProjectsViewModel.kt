package org.matchlab.academic_net.feature.projects.ui.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.result.FeatureUnavailableException
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveMyProjectsUseCase

/**
 * A project in the "Я инициатор" list, with everything its card shows.
 *
 * @property type `null` when the server gives none (API-GAP G40): the card has no type chip
 * @property status `null` when the server gives none (API-GAP G41): the card has no status tag
 * @property period `null` when the server gives none (API-GAP G43): the footer of the card has no months
 */
@Immutable
data class MyProjectItem(
    val id: ProjectId,
    val title: String,
    val type: ProjectType?,
    val status: ProjectStatus?,
    val sphere: String,
    val rolesCount: Int,
    val period: ProjectPeriod?,
    val isInitiatorVerified: Boolean,
    /** Key of the initiator's avatar illustration. */
    val avatarKey: String,
)

/**
 * What "06 Мои проекты" shows on its "Я инициатор" tab. The screen reads it in this order: [isListUnavailable] (the
 * message that the server cannot list the projects), [isLoading], [hasError], an empty [projects], the list.
 *
 * @property isListUnavailable the server cannot list the viewer's projects (API-GAP G44): nothing is asked and the
 *  screen says so, instead of "Не удалось загрузить"
 * @property canCreate "Создать проект" is offered, i.e. the button of creating and no explanation of its absence
 *  (API-GAP G40, G43)
 */
@Immutable
data class MyProjectsUiState(
    val isLoading: Boolean = true,
    val projects: List<MyProjectItem> = emptyList(),
    val hasError: Boolean = false,
    val isListUnavailable: Boolean = false,
    val canCreate: Boolean = false,
)

/**
 * "06 Мои проекты": the projects the user has initiated. What [capabilities] switch off is neither asked nor offered:
 * without [ProjectsCapabilities.canListMine] (API-GAP G44) the use case is not called at all and the state says that the
 * list is unavailable; the same state comes when the flow fails with [FeatureUnavailableException]. Without
 * [ProjectsCapabilities.canCreate] (API-GAP G40, G43) [MyProjectsUiState.canCreate] is `false`.
 */
class MyProjectsViewModel(
    observeMyProjects: ObserveMyProjectsUseCase,
    capabilities: ProjectsCapabilities,
) : ViewModel() {
    private val initial = MyProjectsUiState(canCreate = capabilities.canCreate)

    val uiState: StateFlow<MyProjectsUiState> = if (capabilities.canListMine) {
        observeMyProjects()
            .map { projects -> initial.copy(isLoading = false, projects = projects.map(Project::toItem)) }
            .catch { cause ->
                emit(
                    initial.copy(
                        isLoading = false,
                        hasError = cause !is FeatureUnavailableException,
                        isListUnavailable = cause is FeatureUnavailableException,
                    ),
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), initial)
    } else {
        // API-GAP G44: the server cannot list the viewer's projects, so it is not asked.
        MutableStateFlow(initial.copy(isLoading = false, isListUnavailable = true)).asStateFlow()
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}

internal fun Project.toItem() = MyProjectItem(
    id = id,
    title = title,
    type = type,
    status = status,
    sphere = sphere.name,
    rolesCount = roles.size,
    period = period,
    isInitiatorVerified = initiator.isVerified,
    avatarKey = initiator.id.value,
)
