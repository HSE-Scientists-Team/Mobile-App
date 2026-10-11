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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppFab
import org.matchlab.academic_net.components.AppTabs
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.ProjectCard
import org.matchlab.academic_net.components.ProjectCardUiModel
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_create
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_empty_text
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_empty_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_load_error
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_my_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_participant_empty_text
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_participant_empty_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_tab_initiator
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_tab_participant
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_you_initiator
import org.matchlab.academic_net.feature.projects.ui.label
import org.matchlab.academic_net.feature.projects.ui.rolesCountText
import org.matchlab.academic_net.utils.formatMonthRange

/** "06 Мои проекты": the root screen of the "Проекты" tab. */
@Composable
fun MyProjectsRoute(
    dependencies: ProjectsUiDependencies,
    onCreateProject: () -> Unit,
    onProjectClick: (ProjectId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel { MyProjectsViewModel(dependencies.observeMyProjects) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MyProjectsScreen(state, onCreateProject, onProjectClick, modifier)
}

@Composable
internal fun MyProjectsScreen(
    state: MyProjectsUiState,
    onCreateProject: () -> Unit,
    onProjectClick: (ProjectId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(InitiatorTab) }
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
                )
            }
            when {
                // Projects the user takes part in come with joining a team (a later sprint).
                selectedTab != InitiatorTab -> CenteredMessage(
                    title = stringResource(Res.string.projects_participant_empty_title),
                    text = stringResource(Res.string.projects_participant_empty_text),
                )
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                state.hasError -> CenteredMessage(title = stringResource(Res.string.projects_load_error))
                state.projects.isEmpty() -> CenteredMessage(
                    title = stringResource(Res.string.projects_empty_title),
                    text = stringResource(Res.string.projects_empty_text),
                )
                else -> ProjectList(state.projects, onProjectClick)
            }
        }
        if (selectedTab == InitiatorTab) {
            AppFab(
                onClick = onCreateProject,
                contentDescription = stringResource(Res.string.projects_create),
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun ProjectList(projects: List<MyProjectItem>, onProjectClick: (ProjectId) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // The bottom padding keeps the last card clear of the FAB.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(projects, key = { it.id.value }) { project ->
            ProjectCard(project.toCardUiModel(), onClick = { onProjectClick(project.id) })
        }
    }
}

@Composable
private fun MyProjectItem.toCardUiModel(): ProjectCardUiModel = ProjectCardUiModel(
    title = title,
    author = stringResource(Res.string.projects_you_initiator),
    authorAvatar = AppAvatars.forKey(avatarKey),
    type = type.label(),
    footer = "${rolesCountText(rolesCount)} · ${formatMonthRange(period.start, period.end)}",
    tags = listOf(status.label(), sphere),
    isAuthorVerified = isInitiatorVerified,
)

@Composable
private fun CenteredMessage(title: String, text: String? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val InitiatorTab = 0
