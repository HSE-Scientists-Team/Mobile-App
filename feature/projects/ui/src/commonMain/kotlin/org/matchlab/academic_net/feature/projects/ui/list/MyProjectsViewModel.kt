package org.matchlab.academic_net.feature.projects.ui.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.result.FeatureUnavailableException
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.usecase.GetParticipatingProjectsUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveMyProjectsUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ParticipatingProjectsResult

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
 * @property pendingJoinCount the number of new applications of the project (06e): the card has a badge with it while it is
 *  more than 0 (API-GAP G61: what exactly the server counts is not described)
 * @property initiatorName the name of the project's initiator: the author line of a project the viewer takes part in
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
    val pendingJoinCount: Int = 0,
    val initiatorName: String = "",
)

/**
 * The tab "Я участник" (06b): the projects the viewer is a member of, loaded when the tab is chosen (one list of the
 * server, `mine=participant`).
 */
sealed interface ParticipatingState {
    /** The tab has not been chosen yet: nothing is read. */
    data object Idle : ParticipatingState

    /** The projects are being read (06b has no list yet). */
    data object Loading : ParticipatingState

    /** The projects the viewer takes part in, in the order of their records; may be empty. */
    data class Loaded(val projects: List<MyProjectItem>) : ParticipatingState

    /** Reading failed for [cause]; the screen says why and offers "Повторить". */
    data class Failed(val cause: Throwable) : ParticipatingState
}

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
 * @property participating the tab "Я участник"
 */
@Immutable
data class MyProjectsUiState(
    val isLoading: Boolean = true,
    val projects: List<MyProjectItem> = emptyList(),
    val error: Throwable? = null,
    val isListUnavailable: Boolean = false,
    val canCreate: Boolean = false,
    val participating: ParticipatingState = ParticipatingState.Idle,
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
    private val getParticipatingProjects: GetParticipatingProjectsUseCase,
) : ViewModel() {
    private val initial = MyProjectsUiState(canCreate = capabilities.canCreate)

    /** The state of the tab "Я участник"; it is part of [uiState]. */
    private val participating = MutableStateFlow<ParticipatingState>(ParticipatingState.Idle)
    private var participatingJob: Job? = null

    /** The taps on "Повторить"; each one makes [uiState] read the list again. */
    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val initiatorState: StateFlow<MyProjectsUiState> = if (capabilities.canListMine) {
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

    val uiState: StateFlow<MyProjectsUiState> = combine(initiatorState, participating) { own, joined ->
        own.copy(participating = joined)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), initial)

    /**
     * "Повторить" of the error: reads the list again. It does nothing while there is no error, so a second tap during
     * the read does not start another one.
     */
    fun onRetry() {
        if (!uiState.value.hasError) return
        retries.tryEmit(Unit)
    }

    /**
     * The tab "Я участник" was chosen: reads the projects the viewer takes part in. Membership can change between two
     * visits, so each choice reads again; a list already on the screen stays until the new one arrives, and stays when the
     * read fails (only a tab without a list shows the error). A read that is running is not doubled.
     */
    fun onParticipantTabShown() = loadParticipating()

    /** "Повторить" of the error of the tab "Я участник". */
    fun onParticipantRetry() {
        if (participating.value !is ParticipatingState.Failed) return
        loadParticipating()
    }

    private fun loadParticipating() {
        if (participatingJob?.isActive == true) return
        if (participating.value !is ParticipatingState.Loaded) participating.value = ParticipatingState.Loading
        participatingJob = viewModelScope.launch {
            when (val result = getParticipatingProjects()) {
                is ParticipatingProjectsResult.Loaded ->
                    participating.value = ParticipatingState.Loaded(result.projects.distinctBy { it.id }.map(Project::toItem))
                // A failed repeated read leaves the list that is on the screen; only a tab without one shows the error.
                is ParticipatingProjectsResult.Failure ->
                    if (participating.value !is ParticipatingState.Loaded) {
                        participating.value = ParticipatingState.Failed(result.cause)
                    }
            }
        }
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
    pendingJoinCount = pendingJoinCount,
    initiatorName = initiator.name,
)
