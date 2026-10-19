package org.matchlab.academic_net.feature.projects.ui.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.result.FeatureUnavailableException
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveMyProjectsUseCase

/**
 * A project in the "Я инициатор" list, with everything its card shows. What the app does not know about a project (a
 * type, a status, a sphere or months that the server sent in a form the app does not recognize, see
 * `docs/api-values.md`) is `null`: the card leaves that part out, and the project itself is always in the list.
 *
 * @property type `null` when unknown: the card has no type chip
 * @property status `null` when unknown: the card has no status tag
 * @property sphere the name of the sphere, `null` when unknown: the card has no sphere tag
 * @property period `null` when unknown: the footer of the card has no months
 * @property avatarKey key of the initiator's avatar illustration
 */
@Immutable
data class MyProjectItem(
    val id: ProjectId,
    val title: String,
    val type: ProjectType?,
    val status: ProjectStatus?,
    val sphere: String?,
    val rolesCount: Int,
    val period: ProjectPeriod?,
    val avatarKey: String,
)

/**
 * What "06 Мои проекты" shows on its "Я инициатор" tab. The screen reads it in this order: [isListUnavailable] (the
 * message that the source cannot list the projects), [isLoading], [error] (the failure with "Повторить"), an empty
 * [projects], the list.
 *
 * @property error why the list could not be read; `null` while it is read or has been. "Повторить" reads it again
 * @property isListUnavailable the source cannot list the viewer's projects (`ProjectsCapabilities.canListMine` is off, or
 *  the flow failed with [FeatureUnavailableException]): nothing is asked and the screen says so, instead of "Не удалось
 *  загрузить"
 * @property canCreate "Создать проект" is offered, i.e. the button of creating and no explanation of its absence
 */
@Immutable
data class MyProjectsUiState(
    val isLoading: Boolean = true,
    val projects: List<MyProjectItem> = emptyList(),
    val error: Throwable? = null,
    val isListUnavailable: Boolean = false,
    val canCreate: Boolean = false,
) {
    /** Reading the list failed. */
    val hasError: Boolean get() = error != null
}

/**
 * "06 Мои проекты": the projects the user has initiated, all of those the data layer gives (a project whose type, status
 * or sphere the app does not know is listed too). What [capabilities] switch off is neither asked nor offered: without
 * [ProjectsCapabilities.canListMine] the use case is not called at all and the state says that the list is
 * unavailable; the same state comes when the flow fails with [FeatureUnavailableException]. Without
 * [ProjectsCapabilities.canCreate] [MyProjectsUiState.canCreate] is `false`.
 *
 * **Reading.** The list is the data layer's flow, which updates itself after a project is created or taken off
 * publication, and it is collected while the screen is shown. A failure to read it (the first read comes to the
 * collector as an exception) ends the flow and is [MyProjectsUiState.error]; [onRetry] reads it again, with the progress
 * indicator in place of the error.
 */
class MyProjectsViewModel(
    observeMyProjects: ObserveMyProjectsUseCase,
    capabilities: ProjectsCapabilities,
) : ViewModel() {
    private val initial = MyProjectsUiState(canCreate = capabilities.canCreate)

    /** The taps on "Повторить"; each one makes [uiState] read the list again. */
    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MyProjectsUiState> = if (capabilities.canListMine) {
        retries
            .onStart { emit(Unit) }
            .withIndex()
            .flatMapLatest { (attempt, _) ->
                observeMyProjects()
                    .map { projects -> initial.copy(isLoading = false, projects = projects.toItems()) }
                    .catch { cause -> emit(failed(cause)) }
                    // A repeated read shows the progress indicator in place of the error; the first one starts from it.
                    .onStart { if (attempt > 0) emit(initial) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), initial)
    } else {
        // The source cannot list the viewer's projects, so it is not asked.
        MutableStateFlow(initial.copy(isLoading = false, isListUnavailable = true)).asStateFlow()
    }

    /**
     * "Повторить" of the error: reads the list again. It does nothing while there is no error, so a second tap during
     * the read does not start another one.
     */
    fun onRetry() {
        if (!uiState.value.hasError) return
        retries.tryEmit(Unit)
    }

    private fun failed(cause: Throwable): MyProjectsUiState =
        if (cause is FeatureUnavailableException) {
            initial.copy(isLoading = false, isListUnavailable = true)
        } else {
            initial.copy(isLoading = false, error = cause)
        }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}

/**
 * The projects as cards, in the order the data layer gives them. A project that comes twice is listed once, as the list
 * keys its rows by the id of the project.
 */
private fun List<Project>.toItems(): List<MyProjectItem> = distinctBy { it.id }.map(Project::toItem)

internal fun Project.toItem() = MyProjectItem(
    id = id,
    title = title,
    type = type,
    status = status,
    sphere = sphere?.name,
    rolesCount = roles.size,
    period = period,
    avatarKey = initiator.id.value,
)
