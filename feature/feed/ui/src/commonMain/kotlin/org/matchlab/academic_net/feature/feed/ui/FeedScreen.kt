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
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppSearchBarButton
import org.matchlab.academic_net.components.AppSegmentedControl
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.AvatarSource
import org.matchlab.academic_net.components.PersonListItem
import org.matchlab.academic_net.components.PersonListItemUiModel
import org.matchlab.academic_net.components.ProjectCard
import org.matchlab.academic_net.components.ProjectCardUiModel
import org.matchlab.academic_net.components.SectionHeader
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.components.person.nameOrPlaceholder
import org.matchlab.academic_net.components.person.subtitleText
import org.matchlab.academic_net.components.project.projectCardUiModel
import org.matchlab.academic_net.feature.feed.ui.generated.resources.Res
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_people_empty_text
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_people_empty_title
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_people_error
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_people_find
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_people_title
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_profile
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_projects_empty_text
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_projects_empty_title
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_projects_error
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_projects_find
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_projects_title
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_retry
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_scope_people
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_scope_projects
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_search_people
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_search_projects
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_you_initiator
import org.matchlab.academic_net.feature.feed.ui.generated.resources.feed_title

/**
 * "01 Лента": the root screen of the "Лента" tab.
 *
 * The search bar is a button: it opens the search screen of the chosen scope ([onSearchPeopleClick]
 * for people, [onSearchProjectsClick] for projects). While [onSearchProjectsClick] is `null` the
 * bar of the "Projects" mode stays inactive.
 *
 * The "Проекты" scope shows the projects the server recommends: a tap on a card calls [onProjectClick]; when there are
 * none, "Найти проекты" calls [onSearchProjectsClick] (when it is given). The "Люди" scope shows the people the server
 * recommends: a tap on one calls [onPersonClick]; when there are none, "Найти людей" calls [onSearchPeopleClick] too. The header draws the
 * avatar of the signed-in user (`dependencies.viewerId`, with the initials of their name until the picture is there),
 * which opens the profile ([onProfileClick]).
 */
@Composable
fun FeedRoute(
    dependencies: FeedUiDependencies,
    onProfileClick: () -> Unit,
    onSearchPeopleClick: () -> Unit,
    onPersonClick: (UserSummary) -> Unit,
    onProjectClick: (ProjectId) -> Unit,
    modifier: Modifier = Modifier,
    onSearchProjectsClick: (() -> Unit)? = null,
) {
    val viewModel = viewModel {
        FeedViewModel(
            dependencies.viewerId,
            dependencies.observeViewerName,
            dependencies.getRecommendedPeople,
            dependencies.getRecommendedProjects,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FeedScreen(
        state = state,
        onProfileClick = onProfileClick,
        onScopeSelect = viewModel::onScopeSelect,
        onSearchPeopleClick = onSearchPeopleClick,
        onSearchProjectsClick = onSearchProjectsClick,
        onPersonClick = onPersonClick,
        onProjectClick = onProjectClick,
        onProjectsRetry = viewModel::onProjectsRetry,
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
    onProjectClick: (ProjectId) -> Unit,
    onProjectsRetry: () -> Unit,
    onPeopleRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize()) {
        FeedHeader(AvatarSource(state.viewerAvatarKey, state.viewerName), onProfileClick)
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
            FeedScope.Projects -> ProjectsContent(
                projects = state.projects,
                onProjectClick = onProjectClick,
                onRetry = onProjectsRetry,
                onFindProjectsClick = onSearchProjectsClick,
            )
            FeedScope.People -> PeopleContent(
                people = state.people,
                onPersonClick = onPersonClick,
                onRetry = onPeopleRetry,
                onFindPeopleClick = onSearchPeopleClick,
            )
        }
    }
}

/** "header": the app name and the viewer's avatar, which opens the profile. */
@Composable
private fun FeedHeader(avatar: AvatarSource, onProfileClick: () -> Unit) {
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
                source = avatar,
                contentDescription = stringResource(Res.string.feed_profile),
                size = 32.dp,
            )
        }
    }
}

/**
 * The projects block of "01": the title of the section and, under it, what the state says (see [FeedProjectsState]). A
 * failure and an empty answer are drawn alike, as a message; the way out of an empty answer is the search of projects, when
 * the screen has one ([onFindProjectsClick]).
 */
@Composable
private fun ProjectsContent(
    projects: FeedProjectsState,
    onProjectClick: (ProjectId) -> Unit,
    onRetry: () -> Unit,
    onFindProjectsClick: (() -> Unit)?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            SectionHeader(title = stringResource(Res.string.feed_projects_title))
        }
        when (projects) {
            FeedProjectsState.Loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is FeedProjectsState.Failed -> item(key = "error") {
                CenteredMessage(
                    title = stringResource(Res.string.feed_projects_error),
                    text = failureText(projects.cause),
                    modifier = Modifier.fillMaxWidth(),
                    actionText = stringResource(Res.string.feed_retry),
                    onAction = onRetry,
                )
            }
            is FeedProjectsState.Loaded -> if (projects.projects.isEmpty()) {
                item(key = "empty") {
                    CenteredMessage(
                        title = stringResource(Res.string.feed_projects_empty_title),
                        text = stringResource(Res.string.feed_projects_empty_text),
                        modifier = Modifier.fillMaxWidth(),
                        actionText = onFindProjectsClick?.let { stringResource(Res.string.feed_projects_find) },
                        onAction = onFindProjectsClick,
                    )
                }
            } else {
                items(projects.projects, key = { it.id.value }) { project ->
                    ProjectCard(
                        model = project.toCardUiModel(),
                        onClick = { onProjectClick(project.id) },
                    )
                }
            }
        }
    }
}

/**
 * The card of a recommended project, built like the cards of the search: the type, the status and the sphere as chips, the
 * team size and the months in the footer. The initiator's line is "Вы инициатор" on the viewer's own project, otherwise
 * their name ("Без имени" when there is none) and the university.
 */
@Composable
private fun FeedProject.toCardUiModel(): ProjectCardUiModel {
    val project = recommended.project
    return projectCardUiModel(
        title = project.title,
        author = if (project.viewerIsInitiator) {
            stringResource(Res.string.feed_you_initiator)
        } else {
            authorLine(nameOrPlaceholder(project.initiator.name), recommended.initiatorUniversity)
        },
        avatarKey = project.initiator.id.value,
        avatarName = project.initiator.name,
        type = project.type,
        status = project.status,
        sphere = project.sphere?.name,
        rolesCount = project.roles.size,
        period = project.period,
    )
}

/** The [name] and the [university] joined by " · ", the university left out when it is blank. */
private fun authorLine(name: String, university: String): String =
    listOf(name, university.trim()).filter { it.isNotEmpty() }.joinToString(" · ")

/**
 * The people block of "01c": the title of the section and, under it, what the state says (see [FeedPeopleState]).
 * A failure and an empty answer are drawn alike, as a message with the way out under it.
 */
@Composable
private fun PeopleContent(
    people: FeedPeopleState,
    onPersonClick: (UserSummary) -> Unit,
    onRetry: () -> Unit,
    onFindPeopleClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            SectionHeader(title = stringResource(Res.string.feed_people_title))
        }
        when (people) {
            FeedPeopleState.NotLoaded, FeedPeopleState.Loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is FeedPeopleState.Failed -> item(key = "error") {
                CenteredMessage(
                    title = stringResource(Res.string.feed_people_error),
                    text = failureText(people.cause),
                    modifier = Modifier.fillMaxWidth(),
                    actionText = stringResource(Res.string.feed_retry),
                    onAction = onRetry,
                )
            }
            is FeedPeopleState.Loaded -> if (people.people.isEmpty()) {
                item(key = "empty") {
                    CenteredMessage(
                        title = stringResource(Res.string.feed_people_empty_title),
                        text = stringResource(Res.string.feed_people_empty_text),
                        modifier = Modifier.fillMaxWidth(),
                        actionText = stringResource(Res.string.feed_people_find),
                        onAction = onFindPeopleClick,
                    )
                }
            } else {
                items(people.people, key = { it.user.id.value }) { person ->
                    PersonListItem(
                        model = PersonListItemUiModel(
                            name = person.user.name,
                            subtitle = person.collaborator.subtitleText(),
                            avatar = AvatarSource(person.user.id.value, person.user.name),
                        ),
                        onClick = { onPersonClick(person.user) },
                        outlined = true,
                    )
                }
            }
        }
    }
}

/**
 * A title, an explaining [text] and, with [actionText] and [onAction], an outlined button below: a message for a place
 * inside a list (`MessageState` of `:components` takes the whole screen).
 */
@Composable
private fun CenteredMessage(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
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
        if (actionText != null && onAction != null) {
            AppButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier.padding(top = 4.dp),
                variant = AppButtonVariant.Outlined,
            )
        }
    }
}
