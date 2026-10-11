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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
 * "08 Поиск коллабораторов": for one vacant [role], or for everyone when it is `null` (opened from
 * the feed; [autoFocusSearch] then puts the cursor in the search bar). Opening a person leads to
 * their profile, which is a story of the next block; until it exists [onCollaboratorClick] starts
 * a chat.
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
        onCollaboratorClick = onCollaboratorClick,
        autoFocusSearch = autoFocusSearch,
        modifier = modifier,
    )

    state.filtersSheet?.let { sheet ->
        CollaboratorFiltersSheet(
            state = sheet,
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
            state.isLoading -> LoadingState()
            state.hasError -> MessageState(
                title = stringResource(Res.string.collaborators_error),
                actionText = stringResource(Res.string.collaborators_retry),
                onAction = onRetry,
            )
            state.results.isEmpty() -> MessageState(
                title = stringResource(Res.string.collaborators_empty_title),
                text = stringResource(Res.string.collaborators_empty_search),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") { ResultsHeader(state.results.size) }
                items(state.results, key = { it.user.id.value }) { person ->
                    PersonListItem(
                        model = PersonListItemUiModel(
                            name = person.user.name,
                            subtitle = person.subtitle,
                            avatar = AppAvatars.forKey(person.user.id.value),
                            isVerified = person.user.isVerified,
                        ),
                        onClick = { onCollaboratorClick(person.user) },
                        outlined = true,
                    )
                }
            }
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
