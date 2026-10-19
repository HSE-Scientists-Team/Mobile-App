package org.matchlab.academic_net.feature.projects.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.components.AppFab
import org.matchlab.academic_net.components.AppTabs
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.ProjectCard
import org.matchlab.academic_net.components.ProjectCardUiModel
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.components.person.nameOrPlaceholder
import org.matchlab.academic_net.components.project.projectCardUiModel
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_create
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_create_unavailable
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_empty_text
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_empty_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_list_unavailable
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_load_error
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_my_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_participant_empty_text
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_participant_empty_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_retry
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_tab_initiator
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_tab_participant
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_you_initiator

/**
 * "06 Мои проекты": the root screen of the "Проекты" tab. The tab "Я инициатор" lists every project of the user, drawing
 * of each what the app knows (see `docs/api-values.md`); a card has a badge with the number of the new applications
 * (06e); a failure to read the list has "Повторить". What the source of the data switches off is not offered: without a
 * list of the user's projects the tab says so, without creating there is no "Создать проект". The tab "Я участник" (06b)
 * lists the projects the user takes part in, read when the tab is chosen.
 *
 * @param onParticipatingProjectClick a project of the tab "Я участник": opens its page (04, as a member)
 */
@Composable
fun MyProjectsRoute(
    dependencies: ProjectsUiDependencies,
    onCreateProject: () -> Unit,
    onProjectClick: (ProjectId) -> Unit,
    onParticipatingProjectClick: (ProjectId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel {
        MyProjectsViewModel(dependencies.observeMyProjects, dependencies.capabilities, dependencies.getParticipatingProjects)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MyProjectsScreen(
        state = state,
        onCreateProject = onCreateProject,
        onProjectClick = onProjectClick,
        onParticipatingProjectClick = onParticipatingProjectClick,
        onRetry = viewModel::onRetry,
        onParticipantTabShown = viewModel::onParticipantTabShown,
        onParticipantRetry = viewModel::onParticipantRetry,
        modifier = modifier,
    )
}

@Composable
internal fun MyProjectsScreen(
    state: MyProjectsUiState,
    onCreateProject: () -> Unit,
    onProjectClick: (ProjectId) -> Unit,
    onParticipatingProjectClick: (ProjectId) -> Unit,
    onRetry: () -> Unit,
    onParticipantTabShown: () -> Unit,
    onParticipantRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(InitiatorTab) }
    // The projects of "Я участник" are read as soon as the screen opens, so the tab has its number before it is chosen,
    // and again each time the tab is chosen. A read that is running is not doubled.
    LaunchedEffect(Unit) { onParticipantTabShown() }
    LaunchedEffect(selectedTab) { if (selectedTab != InitiatorTab) onParticipantTabShown() }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = stringResource(Res.string.projects_my_title))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp),
            ) {
                AppTabs(
                    tabs = listOf(
                        stringResource(Res.string.projects_tab_initiator),
                        stringResource(Res.string.projects_tab_participant),
                    ),
                    selectedIndex = selectedTab,
                    onTabSelected = { selectedTab = it },
                    // A number only for a list that has been read: not while it loads, fails or cannot be listed.
                    counts = listOf(
                        state.projects.size.takeIf { !state.isListUnavailable && !state.isLoading && !state.hasError },
                        (state.participating as? ParticipatingState.Loaded)?.projects?.size,
                    ),
                )
            }
            when {
                selectedTab != InitiatorTab -> ParticipatingContent(
                    state = state.participating,
                    onProjectClick = onParticipatingProjectClick,
                    onRetry = onParticipantRetry,
                )
                // The source of the data cannot list the user's projects (and, maybe, cannot create one).
                state.isListUnavailable -> MessageState(
                    title = stringResource(Res.string.projects_list_unavailable),
                    text = if (state.canCreate) null else stringResource(Res.string.projects_create_unavailable),
                )
                state.isLoading -> LoadingState()
                state.hasError -> MessageState(
                    title = stringResource(Res.string.projects_load_error),
                    text = failureText(state.error),
                    actionText = stringResource(Res.string.projects_retry),
                    onAction = onRetry,
                )
                state.projects.isEmpty() -> MessageState(
                    title = stringResource(Res.string.projects_empty_title),
                    text = stringResource(Res.string.projects_empty_text),
                )
                else -> ProjectList(state.projects, onProjectClick)
            }
        }
        // Without creating in the source of the data there is nothing for the button to open.
        if (selectedTab == InitiatorTab && state.canCreate) {
            AppFab(
                onClick = onCreateProject,
                contentDescription = stringResource(Res.string.projects_create),
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

/** The tab "Я участник" (06b, 06d): loading, the error with "Повторить", nothing yet, or the projects with their status. */
@Composable
private fun ParticipatingContent(
    state: ParticipatingState,
    onProjectClick: (ProjectId) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        ParticipatingState.Idle, ParticipatingState.Loading -> LoadingState()
        is ParticipatingState.Failed -> MessageState(
            title = stringResource(Res.string.projects_load_error),
            text = failureText(state.cause),
            actionText = stringResource(Res.string.projects_retry),
            onAction = onRetry,
        )
        is ParticipatingState.Loaded -> if (state.projects.isEmpty()) {
            MessageState(
                title = stringResource(Res.string.projects_participant_empty_title),
                text = stringResource(Res.string.projects_participant_empty_text),
            )
        } else {
            ProjectList(state.projects, onProjectClick, byInitiator = true)
        }
    }
}

@Composable
private fun ProjectList(projects: List<MyProjectItem>, onProjectClick: (ProjectId) -> Unit, byInitiator: Boolean = false) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // The bottom padding keeps the last card clear of the FAB.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(projects, key = { it.id.value }) { project ->
            ProjectCard(project.toCardUiModel(byInitiator), onClick = { onProjectClick(project.id) })
        }
    }
}

@Composable
private fun MyProjectItem.toCardUiModel(byInitiator: Boolean): ProjectCardUiModel = projectCardUiModel(
    title = title,
    // The viewer's own project says "Вы инициатор"; one the viewer takes part in names its initiator.
    author = if (byInitiator) nameOrPlaceholder(initiatorName) else stringResource(Res.string.projects_you_initiator),
    avatarKey = avatarKey,
    type = type,
    status = status,
    sphere = sphere,
    rolesCount = rolesCount,
    period = period,
    // 06c, 06d: the status is a chip after the type ("В работе", "Опубликован", "Снят с публикации", "Отменён").
    statusAsChip = true,
).copy(badgeCount = if (byInitiator) 0 else pendingJoinCount)

private const val InitiatorTab = 0
