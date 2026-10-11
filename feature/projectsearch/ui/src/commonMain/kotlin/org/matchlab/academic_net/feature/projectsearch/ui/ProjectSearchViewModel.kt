package org.matchlab.academic_net.feature.projectsearch.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.feature.projectsearch.domain.FoundProject
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectFilters
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery

/** A project in the result list with everything its card shows. */
@Immutable
data class ProjectSearchItem(
    val id: ProjectId,
    val title: String,
    val initiatorName: String,
    /** Shown after the name on the author line: "Проф. Елена Васкес · Stanford". */
    val initiatorUniversity: String,
    /** Stands in for the initiator's picture until profiles carry images. */
    val initiatorAvatarKey: String,
    val isInitiatorVerified: Boolean,
    val type: ProjectType,
    val status: ProjectStatus,
    val sphere: String,
    val rolesCount: Int,
    val period: ProjectPeriod,
)

/**
 * The open filters sheet: the filters being edited. They are a copy of the applied ones and only
 * replace them on "Применить". [isSpherePickerOpen] shows the sphere choice over the sheet.
 */
@Immutable
data class ProjectFiltersSheetState(
    val draft: ProjectFilters,
    val isSpherePickerOpen: Boolean = false,
)

/**
 * Everything the search screen shows: the query, the applied filters, the result and the state of
 * the filter sheet. It carries no localized text; the screen builds the labels.
 */
@Immutable
data class ProjectSearchUiState(
    /** Only for the first search and for a search after an error: later ones keep the old list. */
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val query: String = "",
    /** The filters the shown [results] were searched with. */
    val filters: ProjectFilters = ProjectFilters(),
    val results: List<ProjectSearchItem> = emptyList(),
    val filtersSheet: ProjectFiltersSheetState? = null,
    /** Loaded when the sphere choice is first opened. */
    val spheres: List<Sphere> = emptyList(),
    val spheresLoadFailed: Boolean = false,
)

/**
 * "02 Поиск проектов" and "03 Фильтры": looking for a project to join. The view model only sends
 * what the user typed and chose: matching and ordering are the server's. It does not produce
 * localized text; the screen builds labels, dates and plurals from the data.
 *
 * Typing starts the search a moment after the last key, the keyboard's "search" and "Применить"
 * start it at once; a new search replaces the one still running.
 */
class ProjectSearchViewModel(
    private val dependencies: ProjectSearchUiDependencies,
) : ViewModel() {
    private val state = MutableStateFlow(ProjectSearchUiState())
    val uiState: StateFlow<ProjectSearchUiState> = state.asStateFlow()

    private var searchJob: Job? = null
    private var spheresJob: Job? = null

    init {
        search()
    }

    fun onQueryChange(query: String) {
        state.update { it.copy(query = query) }
        // Typing triggers the search a moment after the last key.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SearchDebounceMillis)
            refresh()
        }
    }

    /** The "search" key of the keyboard: no need to wait. */
    fun onSearchSubmit() = search()

    /** "Повторить" after an error: the list is replaced by the progress indicator again. */
    fun onRetry() {
        state.update { it.copy(isLoading = true, hasError = false) }
        search()
    }

    /** Opens the filter sheet with a copy of the applied filters as its draft. */
    fun onFiltersClick() {
        state.update { it.copy(filtersSheet = ProjectFiltersSheetState(draft = it.filters)) }
    }

    /** The user changed something in the sheet; the applied filters stay until "Применить". */
    fun onFiltersDraftChange(draft: ProjectFilters) {
        state.update { current ->
            current.copy(filtersSheet = current.filtersSheet?.copy(draft = draft))
        }
    }

    /** "Сбросить" in the sheet's header: empties the draft; nothing is searched until "Применить". */
    fun onFiltersReset() = onFiltersDraftChange(ProjectFilters())

    /** "Отмена" and swiping the sheet away: the applied filters stay as they were. */
    fun onFiltersCancel() {
        state.update { it.copy(filtersSheet = null) }
    }

    /** "Применить": the draft becomes the applied filters and the search runs with them. */
    fun onFiltersApply() {
        val draft = state.value.filtersSheet?.draft ?: return
        state.update { it.copy(filters = draft, filtersSheet = null) }
        search()
    }

    /** "Сбросить фильтры" of the empty result: drops the applied filters and searches at once. */
    fun onFiltersClear() {
        state.update { it.copy(filters = ProjectFilters()) }
        search()
    }

    /** Opens the sphere choice; the spheres are loaded the first time only. */
    fun onSphereFieldClick() {
        state.update { current ->
            current.copy(filtersSheet = current.filtersSheet?.copy(isSpherePickerOpen = true))
        }
        val current = state.value
        if (current.spheres.isEmpty() && !current.spheresLoadFailed && spheresJob?.isActive != true) {
            loadSpheres()
        }
    }

    /** A sphere row was tapped; `null` is "Любая сфера", i.e. no restriction. */
    fun onSphereSelect(sphere: Sphere?) {
        state.update { current ->
            val sheet = current.filtersSheet ?: return@update current
            current.copy(
                filtersSheet = sheet.copy(draft = sheet.draft.copy(sphere = sphere), isSpherePickerOpen = false),
            )
        }
    }

    /** The sphere choice was closed without picking: the draft keeps its sphere. */
    fun onSpherePickerDismiss() {
        state.update { current ->
            current.copy(filtersSheet = current.filtersSheet?.copy(isSpherePickerOpen = false))
        }
    }

    /** "Повторить" after the spheres failed to load. */
    fun onSpheresRetry() {
        state.update { it.copy(spheresLoadFailed = false) }
        loadSpheres()
    }

    private fun search() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val current = state.value
        dependencies.searchProjects(ProjectQuery(current.query, current.filters)).fold(
            onSuccess = { found ->
                state.update {
                    it.copy(isLoading = false, hasError = false, results = found.map(FoundProject::toItem))
                }
            },
            onFailure = { state.update { it.copy(isLoading = false, hasError = true) } },
        )
    }

    private fun loadSpheres() {
        spheresJob?.cancel()
        spheresJob = viewModelScope.launch {
            dependencies.getSpheres().fold(
                // An empty list would leave the picker on its progress indicator for good.
                onSuccess = { spheres ->
                    state.update { it.copy(spheres = spheres, spheresLoadFailed = spheres.isEmpty()) }
                },
                onFailure = { state.update { it.copy(spheresLoadFailed = true) } },
            )
        }
    }

    private companion object {
        const val SearchDebounceMillis = 300L
    }
}

private fun FoundProject.toItem() = ProjectSearchItem(
    id = project.id,
    title = project.title,
    initiatorName = project.initiator.name,
    initiatorUniversity = initiatorUniversity,
    initiatorAvatarKey = project.initiator.id.value,
    isInitiatorVerified = project.initiator.isVerified,
    type = project.type,
    status = project.status,
    sphere = project.sphere.name,
    rolesCount = project.roles.size,
    period = project.period,
)
