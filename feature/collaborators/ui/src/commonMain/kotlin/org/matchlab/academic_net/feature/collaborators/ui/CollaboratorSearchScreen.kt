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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppBadgeDot
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppIconButton
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppSearchBar
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.PersonListItem
import org.matchlab.academic_net.components.PersonListItemUiModel
import org.matchlab.academic_net.feature.collaborators.domain.RoleReference
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.Res
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_back
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_empty_search
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_empty_title
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_error
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_filters
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_retry
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_search_hint
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_title
import org.matchlab.academic_net.utils.RussianPluralForm
import org.matchlab.academic_net.utils.russianPluralForm
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_found_few
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_found_many
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_found_one

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
                Box {
                    AppIconButton(
                        icon = AppIcons.Sliders,
                        contentDescription = stringResource(Res.string.collaborators_filters),
                        onClick = onFiltersClick,
                    )
                    if (!state.filters.isEmpty) {
                        AppBadgeDot(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
                    }
                }
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
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            state.hasError -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = stringResource(Res.string.collaborators_error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                AppButton(
                    text = stringResource(Res.string.collaborators_retry),
                    onClick = onRetry,
                    variant = AppButtonVariant.Outlined,
                )
            }
            state.results.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = stringResource(Res.string.collaborators_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.collaborators_empty_search),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
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
    val text = stringResource(
        when (russianPluralForm(count)) {
            RussianPluralForm.One -> Res.string.collaborators_found_one
            RussianPluralForm.Few -> Res.string.collaborators_found_few
            RussianPluralForm.Many -> Res.string.collaborators_found_many
        },
        count,
    )
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
