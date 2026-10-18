package org.matchlab.academic_net.feature.collaborators.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppSearchBar
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.FiltersButton
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.PersonListItem
import org.matchlab.academic_net.components.PersonListItemUiModel
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.components.paging.LoadMoreEffect
import org.matchlab.academic_net.components.paging.LoadMoreFooter
import org.matchlab.academic_net.components.paging.loadMoreStateOf
import org.matchlab.academic_net.components.person.subtitleText
import org.matchlab.academic_net.feature.collaborators.domain.RoleReference
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.Res
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_back
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_empty_search
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_empty_title
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_error
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_filters
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_found
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_retry
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_search_hint
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_title

/**
 * "08 Поиск коллабораторов": for one vacant [role], or for everyone when it is `null` (opened from the feed;
 * [autoFocusSearch] then puts the cursor in the search bar). The people come page by page, the next one as the list is
 * scrolled ("Загрузка…" under it, or "Не удалось загрузить" with "Повторить"). A tap on a person calls
 * [onCollaboratorClick]; the filters sheet (08b) is opened from the screen itself.
 *
 * @param dependencies what the search needs; its capabilities decide which filters the sheet offers
 */
@Composable
fun CollaboratorSearchRoute(
    dependencies: CollaboratorsUiDependencies,
    role: RoleReference?,
    onNavigateBack: () -> Unit,
    onCollaboratorClick: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
    autoFocusSearch: Boolean = false,
) {
    val viewModel = viewModel(key = role?.let { "${it.projectId.value}/${it.roleId.value}" } ?: "everyone") {
        CollaboratorSearchViewModel(role, dependencies)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CollaboratorSearchScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onQueryChange = viewModel::onQueryChange,
        onSearchSubmit = viewModel::onSearchSubmit,
        onFiltersClick = viewModel::onFiltersClick,
        onRetry = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onRetryLoadMore = viewModel::onRetryLoadMore,
        onCollaboratorClick = onCollaboratorClick,
        autoFocusSearch = autoFocusSearch,
        modifier = modifier,
    )

    state.filtersSheet?.let { sheet ->
        CollaboratorFiltersSheet(
            state = sheet,
            // API-GAP G11: the server cannot filter by the type of a person's works, so the group is not offered.
            showWorkTypes = dependencies.capabilities.projectTypeFilter,
            onDraftChange = viewModel::onFiltersDraftChange,
            onReset = viewModel::onFiltersReset,
            onApply = viewModel::onFiltersApply,
            onDismiss = viewModel::onFiltersDismiss,
        )
    }
}

@Composable
internal fun CollaboratorSearchScreen(
    state: CollaboratorSearchUiState,
    onNavigateBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onFiltersClick: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onCollaboratorClick: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
    autoFocusSearch: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(autoFocusSearch) {
        if (autoFocusSearch) searchFocus.requestFocus()
    }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow)
            // The list ends above the keyboard, so its last rows can be scrolled into view.
            .imePadding(),
    ) {
        AppTopBar(
            title = stringResource(Res.string.collaborators_title),
            subtitle = state.roleTitle,
            onNavigateBack = onNavigateBack,
            navigationContentDescription = stringResource(Res.string.collaborators_back),
            actions = {
                FiltersButton(
                    active = !state.filters.isEmpty,
                    onClick = onFiltersClick,
                    contentDescription = stringResource(Res.string.collaborators_filters),
                )
            },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        ) {
            AppSearchBar(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(Res.string.collaborators_search_hint),
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                onSearch = onSearchSubmit,
            )
        }
        when {
            state.error != null -> MessageState(
                title = stringResource(Res.string.collaborators_error),
                text = failureText(state.error),
                actionText = stringResource(Res.string.collaborators_retry),
                onAction = onRetry,
            )
            state.isLoading -> LoadingState()
            state.results.isEmpty() -> MessageState(
                title = stringResource(Res.string.collaborators_empty_title),
                text = stringResource(Res.string.collaborators_empty_search),
            )
            else -> ResultsList(
                state = state,
                onLoadMore = onLoadMore,
                onRetryLoadMore = onRetryLoadMore,
                onCollaboratorClick = onCollaboratorClick,
            )
        }
    }
}

/**
 * "Найдено N", the people loaded so far and the end of the list that asks for the next page. The list is shown from its
 * start for every new set of results ([CollaboratorSearchUiState.resultsVersion]): left where the user had scrolled the
 * old list, a short new one would be near its end at once and ask for its next page.
 */
@Composable
private fun ResultsList(
    state: CollaboratorSearchUiState,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onCollaboratorClick: (UserSummary) -> Unit,
) {
    key(state.resultsVersion) {
        val listState = rememberLazyListState()
        val footer = loadMoreStateOf(
            isLoadingMore = state.isLoadingMore,
            failed = state.loadMoreFailure != null,
            hasMore = state.hasMore,
        )
        LoadMoreEffect(listState = listState, state = footer, onLoadMore = onLoadMore)
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { ResultsHeader(state.total) }
            items(state.results, key = { it.user.id.value }) { person ->
                PersonListItem(
                    model = PersonListItemUiModel(
                        name = person.user.name,
                        subtitle = person.collaborator.subtitleText(),
                        avatar = AppAvatars.forKey(person.user.id.value),
                        isVerified = person.user.isVerified,
                    ),
                    onClick = { onCollaboratorClick(person.user) },
                    outlined = true,
                )
            }
            item(key = "footer") { LoadMoreFooter(state = footer, onRetry = onRetryLoadMore) }
        }
    }
}

@Composable
private fun ResultsHeader(count: Int) {
    val text = pluralStringResource(Res.plurals.collaborators_found, count, count)
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
