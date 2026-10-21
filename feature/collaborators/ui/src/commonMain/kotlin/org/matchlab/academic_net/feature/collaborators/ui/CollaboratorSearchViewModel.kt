package org.matchlab.academic_net.feature.collaborators.ui

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
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.paging.PagedList
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorFilters
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.RoleReference

/**
 * A person in the result list with everything the row shows.
 *
 * The line under the name ("Stanford · Бакалавриат, 3 курс · Computer Science") is not made here: the study is written
 * in the language of the app, so the screen makes the line from [collaborator] (`subtitleText` of `:components`).
 * API-GAP G30: there is no rating in the row, the API has no `review_count` to tell "no reviews" from a low rating.
 */
@Immutable
data class CollaboratorItem(val collaborator: Collaborator) {
    val user: UserSummary get() = collaborator.user
}

/** The open filters sheet: the filters being edited and how many people they would show. */
@Immutable
data class FiltersSheetState(
    val draft: CollaboratorFilters,
    /** "Показать N"; `null` while counting or when the count failed: the button says "Показать". */
    val previewCount: Int? = null,
)

/**
 * What "08 Поиск коллабораторов" shows.
 *
 * The screen reads it in this order: [error] (the full-screen failure with "Повторить"), then [isLoading], then an empty
 * [results] ("Никого не нашли"), then the list with "Найдено [total]" above and the footer of the paging under it.
 */
@Immutable
data class CollaboratorSearchUiState(
    /** The role or the first page of a search started by the screen itself (opening, "Повторить") is on its way. */
    val isLoading: Boolean = true,
    /** Why the role or the first page failed: the full-screen error with "Повторить"; `null` otherwise. */
    val error: Throwable? = null,
    /** The title of the role of the search (the subtitle of the screen); `null` without a role or until it is loaded. */
    val roleTitle: String? = null,
    /** The text of the search bar, as typed. */
    val query: String = "",
    /** The filters of the search (the sheet edits a draft of them); the dot on the filters button says if any is on. */
    val filters: CollaboratorFilters = CollaboratorFilters(),
    /** The pages loaded so far, without repeats by user id. */
    val results: List<CollaboratorItem> = emptyList(),
    /** "Найдено N": the server's count for the search, not the size of [results]. */
    val total: Int = 0,
    /** The server may have more: the list asks for the next page as it is scrolled. */
    val hasMore: Boolean = false,
    /** The next page is on its way: the footer says "Загрузка…". */
    val isLoadingMore: Boolean = false,
    /** The next page failed: the footer offers "Повторить"; nothing is asked for until it is pressed. */
    val loadMoreFailure: Throwable? = null,
    /** Grows by one each time the first page of a new search replaces [results] (not on the next pages). */
    val resultsVersion: Int = 0,
    /** The open filters sheet ("08b"); `null` while it is closed. */
    val filtersSheet: FiltersSheetState? = null,
)

/**
 * "08 Поиск коллабораторов": "искать людей в проект на роль по фильтрам". For a [role] the search bar starts with the
 * role's skills; without one (opened from the feed) it starts empty and finds everyone. The view model only sends what
 * the user typed and chose: matching and ordering are the server's, and the people come page by page (the next one as the
 * list is scrolled). API-GAP G31: the role itself is not sent, so there is no match percentage and no order by the role.
 * Recommendations of people are not here but on the feed.
 *
 * **A new search** (typing, the "search" key, "Применить", "Повторить", the opening of the screen) always begins the same
 * way: the search and the page being loaded are cancelled, the generation grows, and the first page of the query on the
 * screen is asked for. An answer of an older generation changes nothing, so a slow answer never overwrites a newer one.
 * The previous results stay on the screen until the answer comes, except for a search that shows the full-screen loading
 * (the opening, "Повторить"). A failed first page, of any search, takes the results away and shows the full-screen error:
 * the old list does not belong to the new query.
 *
 * **One request at a time.** [onLoadMore] is called by the screen each time the end of the list is near, more than once
 * for the same end, so it guards itself: nothing is asked for while a search or a page is on its way (a search that waits
 * for the end of the typing counts), after a failure (only [onRetryLoadMore] asks again), and once the list is over.
 *
 * **"Показать N"** of the filters sheet is the server's count: the sheet opens with the total of the results on the
 * screen without a request, and a request (`count`) goes out only after the draft changes, 300 ms after the last change.
 *
 * @param pageSize how many people one page asks for, 1..[PageRequest.MaxLimit]
 */
class CollaboratorSearchViewModel(
    private val role: RoleReference?,
    private val dependencies: CollaboratorsUiDependencies,
    private val pageSize: Int = PageRequest.DefaultLimit,
) : ViewModel() {
    private val state = MutableStateFlow(CollaboratorSearchUiState())
    val uiState: StateFlow<CollaboratorSearchUiState> = state.asStateFlow()

    /** Grows with each new search; the answer of an older one is dropped. */
    private var generation = 0
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var previewJob: Job? = null
    private var requirementJob: Job? = null

    /** The pages of the search on the screen, joined; the next page is [PagedList.next]. */
    private var paged = NothingLoaded

    /** The query that gave [paged]: the next page is asked for it, whatever the search bar says by then. */
    private var activeQuery = CollaboratorQuery()

    /** The role's title and skills have arrived (never, without a role). */
    private var requirementLoaded = false

    init {
        require(pageSize in 1..PageRequest.MaxLimit) { "pageSize $pageSize is out of 1..${PageRequest.MaxLimit}" }
        start()
    }

    /** "Повторить" of the full-screen error: asks again for what failed. It does nothing while that is on its way. */
    fun onRetry() {
        if (state.value.error == null) return
        if (requirementJob?.isActive == true || searchJob?.isActive == true) return
        if (role != null && !requirementLoaded) {
            loadRequirement(role)
        } else {
            startSearch(delayed = false, fullscreen = true)
        }
    }

    /** The text of the search bar changed: the search starts a moment after the last key. */
    fun onQueryChange(query: String) {
        state.update { it.copy(query = query) }
        startSearch(delayed = true, fullscreen = false)
    }

    /** The "search" key of the keyboard: no need to wait. */
    fun onSearchSubmit() {
        startSearch(delayed = false, fullscreen = false)
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

    /**
     * The filters button: opens the sheet with the filters of the search. If the results on the screen are the answer to
     * the current query, "Показать N" is their total, with no request; otherwise it is counted.
     */
    fun onFiltersClick() {
        val current = state.value
        previewJob?.cancel()
        val resultsAreCurrent = !current.isLoading && current.error == null && searchJob?.isActive != true
        if (resultsAreCurrent) {
            state.update { it.copy(filtersSheet = FiltersSheetState(it.filters, previewCount = it.total)) }
        } else {
            state.update { it.copy(filtersSheet = FiltersSheetState(it.filters)) }
            updatePreview(current.filters)
        }
    }

    /**
     * A filter of the sheet changed: the number on "Показать" is unknown until the server counts it. It does nothing
     * when the sheet is not open: a late call after "Применить" or a dismissal (a last tap, a callback of the closing
     * sheet) must neither open the sheet again nor start a count for it.
     */
    fun onFiltersDraftChange(draft: CollaboratorFilters) {
        if (state.value.filtersSheet == null) return
        state.update { it.copy(filtersSheet = FiltersSheetState(draft)) }
        updatePreview(draft)
    }

    /** "Сбросить" in the sheet: every filter of the draft off. Like [onFiltersDraftChange], it needs the sheet open. */
    fun onFiltersReset() = onFiltersDraftChange(CollaboratorFilters())

    /** The sheet is closed without applying: the draft and the count that is on its way are dropped. */
    fun onFiltersDismiss() {
        previewJob?.cancel()
        state.update { it.copy(filtersSheet = null) }
    }

    /** "Показать N": the draft becomes the filters of the search, the sheet closes and the search starts at once. */
    fun onFiltersApply() {
        val draft = state.value.filtersSheet?.draft ?: return
        previewJob?.cancel()
        state.update { it.copy(filters = draft, filtersSheet = null) }
        startSearch(delayed = false, fullscreen = false)
    }

    /** The opening of the screen: without a role the search starts at once, with one the role comes first. */
    private fun start() {
        if (role == null) {
            startSearch(delayed = false, fullscreen = true)
        } else {
            loadRequirement(role)
        }
    }

    /**
     * The role's title and skills. The skills become the text of the search bar unless the user has typed something
     * already; then the first search for it starts.
     */
    private fun loadRequirement(role: RoleReference) {
        state.update { it.copy(isLoading = true, error = null) }
        requirementJob = viewModelScope.launch {
            dependencies.getRoleRequirement(role.projectId, role.roleId).fold(
                onSuccess = { found ->
                    requirementLoaded = true
                    state.update {
                        it.copy(
                            roleTitle = found.roleTitle,
                            query = if (it.query.isEmpty()) found.skills.joinToString(", ") else it.query,
                        )
                    }
                    startSearch(delayed = false, fullscreen = true)
                },
                onFailure = { cause -> state.update { it.copy(isLoading = false, error = cause) } },
            )
        }
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
                isLoadingMore = false,
                loadMoreFailure = null,
            )
        }
        searchJob = viewModelScope.launch {
            if (delayed) delay(SearchDebounceMillis)
            val query = state.value.let { CollaboratorQuery(it.query, it.filters) }
            dependencies.searchCollaborators(query, PageRequest(offset = 0, limit = pageSize)).fold(
                onSuccess = { page -> if (searchGeneration == generation) showFirstPage(query, page) },
                onFailure = { cause -> if (searchGeneration == generation) showFirstPageFailure(cause) },
            )
        }
    }

    /** The first page of a search replaces everything: the list is shown from its start (a new [resultsVersion]). */
    private fun showFirstPage(query: CollaboratorQuery, page: Page<Collaborator>) {
        activeQuery = query
        // API-GAP G34: the order is the server's and its list may shift between two pages, so a person who came
        // twice is shown once (by user id); "Найдено N" is the server's total, never less than what is loaded.
        paged = PagedList.of(page.map(::CollaboratorItem)) { it.user.id }
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
            dependencies.searchCollaborators(query, next).fold(
                onSuccess = { page ->
                    if (pageGeneration == generation) {
                        paged = paged.append(page.map(::CollaboratorItem)) { it.user.id }
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

    /** Counts the people [draft] would show, 300 ms after the last change of the draft; a failure is not shown. */
    private fun updatePreview(draft: CollaboratorFilters) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(PreviewDebounceMillis)
            val query = CollaboratorQuery(state.value.query, draft)
            dependencies.countCollaborators(query).onSuccess { count ->
                state.update { current ->
                    // The count is for the filters on screen only if they have not changed since.
                    if (current.filtersSheet?.draft == draft) {
                        current.copy(filtersSheet = FiltersSheetState(draft, count))
                    } else {
                        current
                    }
                }
            }
        }
    }

    private companion object {
        const val SearchDebounceMillis = 300L
        const val PreviewDebounceMillis = 300L

        /** No page is loaded and none is asked for: before the first answer and after a failed first page. */
        val NothingLoaded = PagedList<CollaboratorItem>(next = null)
    }
}
