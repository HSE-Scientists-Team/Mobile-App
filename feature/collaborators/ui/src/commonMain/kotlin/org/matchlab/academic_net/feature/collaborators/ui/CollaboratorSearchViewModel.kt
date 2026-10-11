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
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorFilters
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.RoleReference
import org.matchlab.academic_net.feature.collaborators.domain.ScoredCollaborator

/** A person in the result list with everything the row shows. */
@Immutable
data class CollaboratorItem(
    val user: UserSummary,
    /** "Stanford · 3 курс бакалавриата · Computer Science". */
    val subtitle: String,
    /** Share of the role's skills the person has; `null` when the role asks for none. */
    val matchPercent: Int?,
)

/** The open filters sheet: the filters being edited and how many people they would show. */
@Immutable
data class FiltersSheetState(
    val draft: CollaboratorFilters,
    /** `null` while counting. */
    val previewCount: Int? = null,
)

@Immutable
data class CollaboratorSearchUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val roleTitle: String? = null,
    val query: String = "",
    val filters: CollaboratorFilters = CollaboratorFilters(),
    val results: List<CollaboratorItem> = emptyList(),
    val filtersSheet: FiltersSheetState? = null,
)

/**
 * "08 Поиск коллабораторов": "искать людей в проект на роль по фильтрам". The search bar starts
 * with the role's skills. The view model only sends what the user typed and chose: matching,
 * scoring and ordering are the server's. Recommendations of people are not here but on the feed.
 */
class CollaboratorSearchViewModel(
    private val projectId: ProjectId,
    private val roleId: RoleId,
    private val dependencies: CollaboratorsUiDependencies,
) : ViewModel() {
    private val state = MutableStateFlow(CollaboratorSearchUiState())
    val uiState: StateFlow<CollaboratorSearchUiState> = state.asStateFlow()

    private val role = RoleReference(projectId, roleId)
    private var searchJob: Job? = null
    private var previewJob: Job? = null

    init {
        load()
    }

    fun onRetry() = load()

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
    fun onSearchSubmit() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { refresh() }
    }

    fun onFiltersClick() {
        state.update { it.copy(filtersSheet = FiltersSheetState(it.filters)) }
        updatePreview(state.value.filters)
    }

    fun onFiltersDraftChange(draft: CollaboratorFilters) {
        state.update { it.copy(filtersSheet = FiltersSheetState(draft)) }
        updatePreview(draft)
    }

    fun onFiltersReset() = onFiltersDraftChange(CollaboratorFilters())

    fun onFiltersDismiss() {
        previewJob?.cancel()
        state.update { it.copy(filtersSheet = null) }
    }

    fun onFiltersApply() {
        val draft = state.value.filtersSheet?.draft ?: return
        previewJob?.cancel()
        state.update { it.copy(filters = draft, filtersSheet = null) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { refresh() }
    }

    private fun load() {
        state.update { it.copy(isLoading = true, hasError = false) }
        viewModelScope.launch {
            dependencies.getRoleRequirement(projectId, roleId).fold(
                onSuccess = { found ->
                    val query = found.skills.joinToString(", ")
                    state.update { it.copy(roleTitle = found.roleTitle, query = query) }
                    refresh()
                },
                onFailure = { state.update { it.copy(isLoading = false, hasError = true) } },
            )
        }
    }

    private suspend fun refresh() {
        val current = state.value
        val result = dependencies.searchCollaborators(CollaboratorQuery(current.query, current.filters, role))
        result.fold(
            onSuccess = { people ->
                state.update {
                    it.copy(
                        isLoading = false,
                        hasError = false,
                        results = people.map(ScoredCollaborator::toItem),
                    )
                }
            },
            onFailure = { state.update { it.copy(isLoading = false, hasError = true) } },
        )
    }

    private fun updatePreview(draft: CollaboratorFilters) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val query = CollaboratorQuery(state.value.query, draft, role)
            dependencies.searchCollaborators(query).onSuccess { people ->
                state.update { current ->
                    // The answer is for the filters on screen only if they have not changed since.
                    if (current.filtersSheet?.draft == draft) {
                        current.copy(filtersSheet = FiltersSheetState(draft, people.size))
                    } else {
                        current
                    }
                }
            }
        }
    }

    private companion object {
        const val SearchDebounceMillis = 300L
    }
}

private fun ScoredCollaborator.toItem() = CollaboratorItem(
    user = collaborator.user,
    subtitle = collaborator.subtitle,
    matchPercent = matchPercent,
)
