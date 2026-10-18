package org.matchlab.academic_net.feature.projectdetails.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.PersonCard
import org.matchlab.academic_net.components.PersonCardUiModel
import org.matchlab.academic_net.components.RoleApplyCard
import org.matchlab.academic_net.components.RoleApplyCardUiModel
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.components.person.nameOrPlaceholder
import org.matchlab.academic_net.components.person.personStatsText
import org.matchlab.academic_net.components.project.ProjectDescriptionBlocks
import org.matchlab.academic_net.components.project.ProjectHeader
import org.matchlab.academic_net.components.project.ProjectPeriodCard
import org.matchlab.academic_net.components.project.RolesSectionHeader
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_all_roles_filled
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_error
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_initiator
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_initiator_subtitle
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_message_initiator
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_not_found_text
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_not_found_title
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_open_roles
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_retry
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_title

/**
 * "04 Проект": the page of a published project of someone else, to decide whether to join it.
 * Read-only: applying to a role ("Откликнуться"), sharing and reporting belong to later stories.
 * What the server does not tell (API-GAP G40, G41, G43) is left out: no chips of the type and the status, no card of
 * the months. The counter of the roles counts places, and a role of several places says how many are taken.
 *
 * @param onNavigateBack the back arrow of the app bar
 * @param onInitiatorClick the initiator card itself, which leads to the initiator's profile
 * @param onContactInitiator the chat button of the initiator card; it is not shown on the
 *  viewer's own project
 */
@Composable
fun ProjectDetailsRoute(
    dependencies: ProjectDetailsUiDependencies,
    projectId: ProjectId,
    onNavigateBack: () -> Unit,
    onInitiatorClick: (UserId) -> Unit,
    onContactInitiator: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = projectId.value) {
        ProjectDetailsViewModel(projectId, dependencies.getProjectDetails)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProjectDetailsScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onInitiatorClick = {
            (state as? ProjectDetailsUiState.Content)?.project?.initiator?.user?.id?.let(onInitiatorClick)
        },
        onContactInitiatorClick = {
            (state as? ProjectDetailsUiState.Content)?.project?.initiator?.user?.let(onContactInitiator)
        },
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
internal fun ProjectDetailsScreen(
    state: ProjectDetailsUiState,
    onNavigateBack: () -> Unit,
    onInitiatorClick: () -> Unit,
    onContactInitiatorClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        AppTopBar(
            title = stringResource(Res.string.projectdetails_title),
            onNavigateBack = onNavigateBack,
        )
        when (state) {
            ProjectDetailsUiState.Loading -> LoadingState()
            ProjectDetailsUiState.NotFound -> MessageState(
                title = stringResource(Res.string.projectdetails_not_found_title),
                text = stringResource(Res.string.projectdetails_not_found_text),
            )
            is ProjectDetailsUiState.Error -> MessageState(
                title = stringResource(Res.string.projectdetails_error),
                text = failureText(state.cause),
                actionText = stringResource(Res.string.projectdetails_retry),
                onAction = onRetry,
            )
            is ProjectDetailsUiState.Content -> ProjectContent(state.project, onInitiatorClick, onContactInitiatorClick)
        }
    }
}

@Composable
private fun ColumnScope.ProjectContent(
    project: ProjectDetailsUiModel,
    onInitiatorClick: () -> Unit,
    onContactInitiatorClick: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProjectHeader(project.title, project.type, project.status, project.sphere)
        PersonCard(
            model = project.initiator.toCardUiModel(),
            onClick = onInitiatorClick,
            onChatClick = onContactInitiatorClick,
        )
        ProjectDescriptionBlocks(project.problem, project.goal, project.tags)
        RolesSection(project)
        project.period?.let { ProjectPeriodCard(it) }
    }
}

/**
 * The open roles with "N из M занято" counted in places. A project without roles has no section; with every role
 * taken the section says so instead of listing nothing. A role of several places says how many are taken.
 */
@Composable
private fun ColumnScope.RolesSection(project: ProjectDetailsUiModel) {
    if (project.rolesCount == 0) return
    RolesSectionHeader(
        title = stringResource(Res.string.projectdetails_open_roles),
        occupiedCount = project.occupiedPlaces,
        totalCount = project.totalPlaces,
    )
    project.vacantRoles.forEach { role ->
        // No button yet: applying belongs to the next sprint.
        RoleApplyCard(RoleApplyCardUiModel(role.title, role.skills, occupied = role.occupied, slots = role.slots))
    }
    if (project.vacantRoles.isEmpty()) {
        Text(
            text = stringResource(Res.string.projectdetails_all_roles_filled),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InitiatorUiModel.toCardUiModel(): PersonCardUiModel = PersonCardUiModel(
    name = nameOrPlaceholder(user.name),
    subtitle = if (university.isBlank()) {
        stringResource(Res.string.projectdetails_initiator)
    } else {
        stringResource(Res.string.projectdetails_initiator_subtitle, university)
    },
    avatar = AppAvatars.forKey(avatarKey),
    isVerified = user.isVerified,
    stats = personStatsText(rating, completedProjects),
    showChatButton = canContact,
    chatContentDescription = stringResource(Res.string.projectdetails_message_initiator),
)
