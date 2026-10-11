package org.matchlab.academic_net.feature.projects.ui.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveMyProjectsUseCase

/** A project in the "Я инициатор" list, with everything its card shows. */
@Immutable
data class MyProjectItem(
    val id: ProjectId,
    val title: String,
    val type: ProjectType,
    val status: ProjectStatus,
    val sphere: String,
    val rolesCount: Int,
    val period: ProjectPeriod,
    val isInitiatorVerified: Boolean,
    /** Key of the initiator's avatar illustration. */
    val avatarKey: String,
)

@Immutable
data class MyProjectsUiState(
    val isLoading: Boolean = true,
    val projects: List<MyProjectItem> = emptyList(),
    val hasError: Boolean = false,
)

class MyProjectsViewModel(
    observeMyProjects: ObserveMyProjectsUseCase,
) : ViewModel() {
    val uiState: StateFlow<MyProjectsUiState> = observeMyProjects()
        .map { projects -> MyProjectsUiState(isLoading = false, projects = projects.map(Project::toItem)) }
        .catch { emit(MyProjectsUiState(isLoading = false, hasError = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), MyProjectsUiState())

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
