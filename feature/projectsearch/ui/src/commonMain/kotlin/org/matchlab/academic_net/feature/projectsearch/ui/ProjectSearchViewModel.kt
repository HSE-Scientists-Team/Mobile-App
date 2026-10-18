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
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.paging.PagedList
import org.matchlab.academic_net.feature.projectsearch.domain.FoundProject
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectFilters
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery

/**
 * A project in the result list with everything its card shows.
 *
 * @property initiatorUniversity shown after the name on the author line ("Проф. Елена Васкес · Stanford"); blank when
 *  unknown
 * @property initiatorAvatarKey stands in for the initiator's picture until profiles carry images
 * @property isOwnProject the viewer's own project (API-GAP G53: the search may include it): the author line says
 *  "Вы инициатор" instead of the name
 * @property type `null` when the server gives none (API-GAP G40): the card has no type chip
 * @property status `null` when the server gives none (API-GAP G41): the card has no status tag
 * @property rolesCount how many roles the project has: the "4 роли" of the footer
 * @property period `null` when the server gives none (API-GAP G43): the footer of the card has no months
 */
@Immutable
data class ProjectSearchItem(
    val id: ProjectId,
    val title: String,
    val initiatorName: String,
    val initiatorUniversity: String,
    val initiatorAvatarKey: String,
    val isInitiatorVerified: Boolean,
    val isOwnProject: Boolean,
    val type: ProjectType?,
    val status: ProjectStatus?,
    val sphere: String,
    val rolesCount: Int,
    val period: ProjectPeriod?,
)

/**
 * The open filters sheet: the filters being edited. They are a copy of the applied ones and only
 * replace them on "Применить". [isSpherePickerOpen] shows the sphere choice over the sheet.
 *
 * @property typeFilter the group "Тип проекта" is offered: the server can filter by the type (API-GAP G40)
 * @property durationFilter the group "Сроки" is offered: the server can filter by the duration (API-GAP G45)
 */
@Immutable
data class ProjectFiltersSheetState(
    val draft: ProjectFilters,
    val isSpherePickerOpen: Boolean = false,
    val typeFilter: Boolean = false,
    val durationFilter: Boolean = false,
)

/**
 * Everything the search screen shows: the query, the applied filters, the result and the state of
 * the filter sheet. It carries no localized text; the screen builds the labels.
 *
 * The screen reads it in this order: [error] (the full-screen failure with "Повторить"), then [isLoading], then an empty
 * [results] ("Ничего не найдено"), then the list with "Найдено [total]" above and the footer of the paging under it.
 */
@Immutable
data class ProjectSearchUiState(
    /** The first page of a search started by the screen itself (opening, "Повторить") is on its way. */
    val isLoading: Boolean = true,
    /** Why the first page failed: the full-screen error with "Повторить"; `null` otherwise. */
    val error: Throwable? = null,
    /** The text of the search bar, as typed. */
    val query: String = "",
    /** The filters of the search (the sheet edits a draft of them); the dot on the filters button says if any is on. */
    val filters: ProjectFilters = ProjectFilters(),
    /** The pages loaded so far, without repeats by project id. */
    val results: List<ProjectSearchItem> = emptyList(),
    /** "Найдено N": the server's count for the search, not the size of [results]. */
    val total: Int = 0,
    /** The server may have more: the list asks for the next page as it is scrolled. `false` while a new search runs. */
    val hasMore: Boolean = false,
    /** The next page is on its way: the footer says "Загрузка…". */
    val isLoadingMore: Boolean = false,
    /** The next page failed: the footer offers "Повторить"; nothing is asked for until it is pressed. */
    val loadMoreFailure: Throwable? = null,
    /** Grows by one each time the first page of a new search replaces [results] (not on the next pages). */
    val resultsVersion: Int = 0,
    /** The open filters sheet ("03"); `null` while it is closed. */
    val filtersSheet: ProjectFiltersSheetState? = null,
    /** Loaded when the sphere choice is first opened. */
    val spheres: List<Sphere> = emptyList(),
    val spheresLoadFailed: Boolean = false,
) {
    /** The first page failed. */
    val hasError: Boolean get() = error != null
}

/**
 * "02 Поиск проектов" and "03 Фильтры": looking for a project to join. The view model only sends what the user typed and
 * chose: matching and ordering are the server's (API-GAP G53), and the projects come page by page, the next one as the
 * list is scrolled. It does not produce localized text; the screen builds labels, dates and plurals from the data.
 *
 * **A new search** (typing, the "search" key, "Применить", "Повторить", the opening of the screen) always begins the same
 * way: the search and the page being loaded are cancelled, the generation grows, and the first page of the query on the
 * screen is asked for. While it is on its way nothing more is asked for ([ProjectSearchUiState.hasMore] is `false`, the
 * footer of the list is over). An answer of an older generation changes nothing, so a slow answer never overwrites a
 * newer one. The previous results stay on the screen until the answer comes, except for a search that shows the
 * full-screen loading (the opening, "Повторить", "Сбросить фильтры"). A failed first page, of any search, takes the
 * results away and shows the full-screen error: the old list does not belong to the new query. Typing starts the search
 * 300 ms after the last key, the keyboard's "search" and "Применить" start it at once.
 *
 * **One request at a time.** [onLoadMore] is called by the screen each time the end of the list is near, more than once
 * for the same end, so it guards itself: nothing is asked for while a search or a page is on its way (a search that waits
 * for the end of the typing counts), after a failure (only [onRetryLoadMore] asks again), and once the list is over. The
 * pages are joined by project id: a project that the server's list shifted onto the next page is shown once.
 *
 * **The filters sheet** offers the groups of types and durations only as far as the server can filter by them
 * ([ProjectSearchUiDependencies.capabilities], API-GAP G40, G45); the flags are in [ProjectFiltersSheetState].
 *
 * @param pageSize how many projects one page asks for, 1..[PageRequest.MaxLimit]
 */
class ProjectSearchViewModel(
    private val dependencies: ProjectSearchUiDependencies,
    private val pageSize: Int = PageRequest.DefaultLimit,
) : ViewModel() {
    private val state = MutableStateFlow(ProjectSearchUiState())
    val uiState: StateFlow<ProjectSearchUiState> = state.asStateFlow()

    /** Grows with each new search; the answer of an older one is dropped. */
    private var generation = 0
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var spheresJob: Job? = null

    /** The pages of the search on the screen, joined; the next page is [PagedList.next]. */
    private var paged = NothingLoaded

    /** The query that gave [paged]: the next page is asked for it, whatever the search bar says by then. */
    private var activeQuery = ProjectQuery()

    init {
        require(pageSize in 1..PageRequest.MaxLimit) { "pageSize $pageSize is out of 1..${PageRequest.MaxLimit}" }
        startSearch(delayed = false, fullscreen = true)
    }

    /** The text of the search bar changed: the search starts a moment after the last key. */
    fun onQueryChange(query: String) {
        state.update { it.copy(query = query) }
        startSearch(delayed = true, fullscreen = false)
    }

    /** The "search" key of the keyboard: no need to wait. */
    fun onSearchSubmit() = startSearch(delayed = false, fullscreen = false)

    /** "Повторить" of the full-screen error: the list is replaced by the progress indicator again. */
    fun onRetry() {
        if (state.value.error == null || searchJob?.isActive == true) return
        startSearch(delayed = false, fullscreen = true)
    }

    /** The end of the list is near: asks for the next page unless that is not possible now (see the class). */
    fun onLoadMore() {
        val current = state.value
        if (current.error != null || current.loadMoreFailure != null) return
        if (searchJob?.isActive == true || loadMoreJob?.isActive == true) return
        loadNextPage(paged.next ?: return)
    }

    /** "Повторить" in the footer after the next page failed: asks for the same page again. */
    fun onRetryLoadMore() {
        if (state.value.loadMoreFailure == null) return
        if (searchJob?.isActive == true || loadMoreJob?.isActive == true) return
        loadNextPage(paged.next ?: return)
    }

    /** Opens the filter sheet with a copy of the applied filters as its draft. */
    fun onFiltersClick() {
        val capabilities = dependencies.capabilities
        state.update {
            it.copy(
                filtersSheet = ProjectFiltersSheetState(
                    draft = it.filters,
                    typeFilter = capabilities.typeFilter,
                    durationFilter = capabilities.durationFilter,
                ),
            )
        }
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
        startSearch(delayed = false, fullscreen = false)
    }

    /**
     * "Сбросить фильтры" of the empty result: drops the applied filters and searches at once, with the progress
     * indicator in place of the "nothing found" that no longer holds.
     */
    fun onFiltersClear() {
        state.update { it.copy(filters = ProjectFilters()) }
        startSearch(delayed = false, fullscreen = true)
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

    /**
     * A new search for the query on the screen (see the class). [delayed] waits 300 ms for the end of the typing,
     * [fullscreen] shows the full-screen loading instead of the previous results while the first page is on its way.
     */
    private fun startSearch(delayed: Boolean, fullscreen: Boolean) {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        val searchGeneration = ++generation
        state.update {
            it.copy(
                isLoading = it.isLoading || fullscreen,
                error = if (fullscreen) null else it.error,
                // The old list has no next page for the new query: nothing is asked for until the first page is here.
                hasMore = false,
                isLoadingMore = false,
                loadMoreFailure = null,
            )
        }
        searchJob = viewModelScope.launch {
            if (delayed) delay(SearchDebounceMillis)
            val query = state.value.let { ProjectQuery(it.query, it.filters) }
            dependencies.searchProjects(query, PageRequest(offset = 0, limit = pageSize)).fold(
                onSuccess = { page -> if (searchGeneration == generation) showFirstPage(query, page) },
                onFailure = { cause -> if (searchGeneration == generation) showFirstPageFailure(cause) },
            )
        }
    }

    /** The first page of a search replaces everything: the list is shown from its start (a new `resultsVersion`). */
    private fun showFirstPage(query: ProjectQuery, page: Page<FoundProject>) {
        activeQuery = query
        // API-GAP G53: the order is the server's and its list may shift between two pages, so a project that came
        // twice is shown once (by id); "Найдено N" is the server's total, never less than what is loaded.
        paged = PagedList.of(page.map(FoundProject::toItem)) { it.id.value }
        state.update {
            it.copy(
                isLoading = false,
                error = null,
                results = paged.items,
                total = paged.total,
                hasMore = paged.hasMore,
                resultsVersion = it.resultsVersion + 1,
            )
        }
    }

    private fun showFirstPageFailure(cause: Throwable) {
        paged = NothingLoaded
        state.update {
            it.copy(isLoading = false, error = cause, results = emptyList(), total = 0, hasMore = false)
        }
    }

    /** The page [next] of [activeQuery]; the answer of a search that has been replaced meanwhile is dropped. */
    private fun loadNextPage(next: PageRequest) {
        val pageGeneration = generation
        val query = activeQuery
        state.update { it.copy(isLoadingMore = true, loadMoreFailure = null) }
        loadMoreJob = viewModelScope.launch {
            dependencies.searchProjects(query, next).fold(
                onSuccess = { page ->
                    if (pageGeneration == generation) {
                        paged = paged.append(page.map(FoundProject::toItem)) { it.id.value }
                        state.update {
                            it.copy(
                                results = paged.items,
                                total = paged.total,
                                hasMore = paged.hasMore,
                                isLoadingMore = false,
                            )
                        }
                    }
                },
                onFailure = { cause ->
                    if (pageGeneration == generation) {
                        state.update { it.copy(isLoadingMore = false, loadMoreFailure = cause) }
                    }
                },
            )
        }
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

        /** No page is loaded and none is asked for: before the first answer and after a failed first page. */
        val NothingLoaded = PagedList<ProjectSearchItem>(next = null)
    }
}

private fun FoundProject.toItem() = ProjectSearchItem(
    id = project.id,
    title = project.title,
    initiatorName = project.initiator.name,
    initiatorUniversity = initiatorUniversity,
    initiatorAvatarKey = project.initiator.id.value,
    isInitiatorVerified = project.initiator.isVerified,
    isOwnProject = project.viewerIsInitiator,
    type = project.type,
    status = project.status,
    sphere = project.sphere.name,
    rolesCount = project.roles.size,
    period = project.period,
)
