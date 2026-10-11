package org.matchlab.academic_net.feature.feed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppSearchBarButton
import org.matchlab.academic_net.components.AppSegmentedControl
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.PersonListItem
import org.matchlab.academic_net.components.PersonListItemUiModel
import org.matchlab.academic_net.components.SectionHeader
import org.matchlab.academic_net.feature.feed.ui.generated.resources.Res
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_people_empty_text
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_people_empty_title
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_people_error
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_people_title
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_profile
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_projects_text
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_projects_title
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_retry
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_scope_people
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_scope_projects
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_search_people
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_search_projects
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_title

/**
 * "01 Лента": the root screen of the "Лента" tab.
 *
 * The search bar is a button: it opens the search screen of the chosen scope ([onSearchPeopleClick]
 * for people, [onSearchProjectsClick] for projects). While [onSearchProjectsClick] is `null` the
 * bar of the "Projects" mode stays inactive.
 */
@Composable
fun FeedRoute(
    dependencies: FeedUiDependencies,
    onProfileClick: () -> Unit,
    onSearchPeopleClick: () -> Unit,
    onPersonClick: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
    onSearchProjectsClick: (() -> Unit)? = null,
) {
    val viewModel = viewModel { FeedViewModel(dependencies.getViewer, dependencies.getRecommendedPeople) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FeedScreen(
        state = state,
        onProfileClick = onProfileClick,
        onScopeSelect = viewModel::onScopeSelect,
        onSearchPeopleClick = onSearchPeopleClick,
        onSearchProjectsClick = onSearchProjectsClick,
        onPersonClick = onPersonClick,
        onPeopleRetry = viewModel::onPeopleRetry,
        modifier = modifier,
    )
}

@Composable
internal fun FeedScreen(
    state: FeedUiState,
    onProfileClick: () -> Unit,
    onScopeSelect: (FeedScope) -> Unit,
    onSearchPeopleClick: () -> Unit,
    onSearchProjectsClick: (() -> Unit)?,
    onPersonClick: (UserSummary) -> Unit,
    onPeopleRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize()) {
        FeedHeader(state.viewerAvatarKey, onProfileClick)
        // The top edge meets the header, the bottom one is rounded like cards and the navigation bar.
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppSegmentedControl(
                options = listOf(
                    stringResource(Res.string.feed_scope_projects),
                    stringResource(Res.string.feed_scope_people),
                ),
                selectedIndex = state.scope.ordinal,
                onSelect = { onScopeSelect(FeedScope.entries[it]) },
            )
            when (state.scope) {
                FeedScope.Projects -> AppSearchBarButton(
                    placeholder = stringResource(Res.string.feed_search_projects),
                    onClick = onSearchProjectsClick,
                )
                FeedScope.People -> AppSearchBarButton(
                    placeholder = stringResource(Res.string.feed_search_people),
                    onClick = onSearchPeopleClick,
                )
            }
        }
        when (state.scope) {
            FeedScope.Projects -> CenteredMessage(
                title = stringResource(Res.string.feed_projects_title),
                text = stringResource(Res.string.feed_projects_text),
            )
            FeedScope.People -> PeopleContent(state, onPersonClick, onPeopleRetry)
        }
    }
}

/** "header": the app name and the viewer's avatar, which opens the profile. */
@Composable
private fun FeedHeader(avatarKey: String?, onProfileClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.feed_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        // 48dp touch target around the 32dp avatar, which sits 8dp from the edge as in the design.
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onProfileClick),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(
                image = AppAvatars.forKey(avatarKey.orEmpty()),
                contentDescription = stringResource(Res.string.feed_profile),
                size = 32.dp,
            )
        }
    }
}

@Composable
private fun PeopleContent(
    state: FeedUiState,
    onPersonClick: (UserSummary) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            SectionHeader(title = stringResource(Res.string.feed_people_title))
        }
        when {
            state.isPeopleLoading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            state.peopleFailed -> item(key = "error") {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.feed_people_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    AppButton(
                        text = stringResource(Res.string.feed_retry),
                        onClick = onRetry,
                        variant = AppButtonVariant.Outlined,
                    )
                }
            }
            state.people.isEmpty() -> item(key = "empty") {
                CenteredMessage(
                    title = stringResource(Res.string.feed_people_empty_title),
                    text = stringResource(Res.string.feed_people_empty_text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> items(state.people, key = { it.user.id.value }) { person ->
                PersonListItem(
                    model = PersonListItemUiModel(
                        name = person.user.name,
                        subtitle = person.subtitle,
                        avatar = AppAvatars.forKey(person.user.id.value),
                        isVerified = person.user.isVerified,
                    ),
                    onClick = { onPersonClick(person.user) },
                    outlined = true,
                )
            }
        }
    }
}

@Composable
private fun CenteredMessage(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
