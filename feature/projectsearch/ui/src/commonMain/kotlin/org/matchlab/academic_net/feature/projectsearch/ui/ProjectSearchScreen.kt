package org.matchlab.academic_net.feature.projectsearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.ProjectCard
import org.matchlab.academic_net.components.ProjectCardUiModel
import org.matchlab.academic_net.components.SearchTopBar
import org.matchlab.academic_net.components.SpherePickerSheet
import org.matchlab.academic_net.components.project.projectCardUiModel
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_back
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_empty_text
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_empty_title
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_error
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_filters
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_found
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_reset_filters
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_retry
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_search_hint
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_sphere_any

/**
 * "02 Поиск проектов" with the "03 Фильтры" sheet: finding a published project to join. The
 * screen is opened over the tabs (from the feed's search field), so it has its own back arrow.
 *
 * @param onProjectClick opens the page of the project the card shows; while it is `null` the
 *  cards are not clickable
 * @param autoFocusSearch puts the cursor in the search bar, for when the user came from the
 *  search field of the feed
 */
@Composable
fun ProjectSearchRoute(
    dependencies: ProjectSearchUiDependencies,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onProjectClick: ((ProjectId) -> Unit)? = null,
    autoFocusSearch: Boolean = false,
) {
    val viewModel = viewModel { ProjectSearchViewModel(dependencies) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProjectSearchScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onQueryChange = viewModel::onQueryChange,
        onSearchSubmit = viewModel::onSearchSubmit,
        onFiltersClick = viewModel::onFiltersClick,
        onFiltersClear = viewModel::onFiltersClear,
        onRetry = viewModel::onRetry,
        onProjectClick = onProjectClick,
        autoFocusSearch = autoFocusSearch,
        modifier = modifier,
    )

    state.filtersSheet?.let { sheet ->
        ProjectFiltersSheet(
            state = sheet,
            onDraftChange = viewModel::onFiltersDraftChange,
            onSphereFieldClick = viewModel::onSphereFieldClick,
            onReset = viewModel::onFiltersReset,
            onCancel = viewModel::onFiltersCancel,
            onApply = viewModel::onFiltersApply,
        )
        if (sheet.isSpherePickerOpen) {
            SpherePickerSheet(
                spheres = state.spheres,
                selected = sheet.draft.sphere,
                loadFailed = state.spheresLoadFailed,
                onSelect = viewModel::onSphereSelect,
                onRetry = viewModel::onSpheresRetry,
                onDismiss = viewModel::onSpherePickerDismiss,
                anyOptionLabel = stringResource(Res.string.projectsearch_sphere_any),
                onSelectAny = { viewModel.onSphereSelect(null) },
            )
        }
    }
}

@Composable
internal fun ProjectSearchScreen(
    state: ProjectSearchUiState,
    onNavigateBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onFiltersClick: () -> Unit,
    onFiltersClear: () -> Unit,
    onRetry: () -> Unit,
    onProjectClick: ((ProjectId) -> Unit)?,
    modifier: Modifier = Modifier,
    autoFocusSearch: Boolean = false,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            // The list ends above the keyboard, so its last cards can be scrolled into view.
            .imePadding(),
    ) {
        SearchTopBar(
            query = state.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(Res.string.projectsearch_search_hint),
            onNavigateBack = onNavigateBack,
            backContentDescription = stringResource(Res.string.projectsearch_back),
            filtersActive = !state.filters.isEmpty,
            onFiltersClick = onFiltersClick,
            filtersContentDescription = stringResource(Res.string.projectsearch_filters),
            onSearch = onSearchSubmit,
            autoFocus = autoFocusSearch,
        )
        when {
            state.isLoading -> LoadingState()
            state.hasError -> MessageState(
                title = stringResource(Res.string.projectsearch_error),
                actionText = stringResource(Res.string.projectsearch_retry),
                onAction = onRetry,
            )
            state.results.isEmpty() -> MessageState(
                title = stringResource(Res.string.projectsearch_empty_title),
                text = stringResource(Res.string.projectsearch_empty_text),
                // With filters on, the likely reason for nothing is the filters themselves.
                actionText = if (state.filters.isEmpty) null else stringResource(Res.string.projectsearch_reset_filters),
                onAction = if (state.filters.isEmpty) null else onFiltersClear,
            )
            else -> ProjectResults(state.results, onProjectClick)
        }
    }
}

@Composable
private fun ProjectResults(results: List<ProjectSearchItem>, onProjectClick: ((ProjectId) -> Unit)?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Text(
                text = pluralStringResource(Res.plurals.projectsearch_found, results.size, results.size),
                // 16dp to the first card, the cards themselves are 12dp apart.
                modifier = Modifier.padding(bottom = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(results, key = { it.id.value }) { project ->
            ProjectCard(
                model = project.toCardUiModel(),
                onClick = onProjectClick?.let { open -> { open(project.id) } },
            )
        }
    }
}

@Composable
private fun ProjectSearchItem.toCardUiModel(): ProjectCardUiModel = projectCardUiModel(
    title = title,
    author = "$initiatorName · $initiatorUniversity",
    avatarKey = initiatorAvatarKey,
    isAuthorVerified = isInitiatorVerified,
    type = type,
    status = status,
    sphere = sphere,
    rolesCount = rolesCount,
    period = period,
)
