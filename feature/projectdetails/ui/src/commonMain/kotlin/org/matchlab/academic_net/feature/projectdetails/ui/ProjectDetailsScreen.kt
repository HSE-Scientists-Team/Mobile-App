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
import org.matchlab.academic_net.components.paging.LoadMoreFooter
import org.matchlab.academic_net.components.paging.LoadMoreState
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
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_message_member
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_not_found_text
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_not_found_title
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_open_roles
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_retry
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_team
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_title

/**
 * "04 Проект": the page of a published project of someone else, to decide whether to join it.
 * Read-only: applying to a role ("Откликнуться"), sharing and reporting belong to later stories.
 * What the app does not know about the project (`docs/api-values.md`) is left out: no chips of the type, the status and
 * the sphere, no card of the months. The counter of the roles counts places, and a role of several places says how
 * many are taken.
 *
 * @param onNavigateBack the back arrow of the app bar
 * @param onInitiatorClick the initiator card itself, which leads to the initiator's profile
 * @param onContactInitiator the chat button of the initiator card; it is not shown on the
 *  viewer's own project
 * @param onMemberClick a member of "Команда" whose profile is known: opens the profile (the initiator's row uses
 *  [onInitiatorClick])
 * @param onContactMember the chat button of a member of "Команда"; the viewer's own row and a member without a
 *  profile have none (the initiator's row uses [onContactInitiator])
 */
@Composable
fun ProjectDetailsRoute(
    dependencies: ProjectDetailsUiDependencies,
    projectId: ProjectId,
    onNavigateBack: () -> Unit,
    onInitiatorClick: (UserId) -> Unit,
    onContactInitiator: (UserSummary) -> Unit,
    onMemberClick: (UserId) -> Unit,
    onContactMember: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = projectId.value) {
        ProjectDetailsViewModel(projectId, dependencies.getProjectDetails, dependencies.getProjectTeam)
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
        onTeamMemberClick = { member ->
            if (member.isInitiator) onInitiatorClick(member.user.id) else onMemberClick(member.user.id)
        },
        onTeamMemberChatClick = { member ->
            if (member.isInitiator) onContactInitiator(member.user) else onContactMember(member.user)
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
    onTeamMemberClick: (TeamMemberUiModel) -> Unit,
    onTeamMemberChatClick: (TeamMemberUiModel) -> Unit,
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
            is ProjectDetailsUiState.Content -> ProjectContent(
                project = state.project,
                onInitiatorClick = onInitiatorClick,
                onContactInitiatorClick = onContactInitiatorClick,
                onTeamMemberClick = onTeamMemberClick,
                onTeamMemberChatClick = onTeamMemberChatClick,
            )
        }
    }
}

@Composable
private fun ColumnScope.ProjectContent(
    project: ProjectDetailsUiModel,
    onInitiatorClick: () -> Unit,
    onContactInitiatorClick: () -> Unit,
    onTeamMemberClick: (TeamMemberUiModel) -> Unit,
    onTeamMemberChatClick: (TeamMemberUiModel) -> Unit,
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
        TeamSection(project.team, onTeamMemberClick, onTeamMemberChatClick)
        RolesSection(project)
        project.period?.let { ProjectPeriodCard(it) }
    }
}

/**
 * "Команда": the initiator first, then the members with their roles. While the people are being read (04e) the block has
 * its title and "Загрузка…"; without members besides the initiator it is not drawn. A person whose profile could not be
 * read is "Без имени" with the neutral avatar, no stats, no chat and no tap (04f).
 */
@Composable
private fun ColumnScope.TeamSection(
    team: TeamUiState,
    onMemberClick: (TeamMemberUiModel) -> Unit,
    onMemberChatClick: (TeamMemberUiModel) -> Unit,
) {
    if (team == TeamUiState.Hidden) return
    Text(
        text = stringResource(Res.string.projectdetails_team),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    when (team) {
        TeamUiState.Hidden -> Unit
        TeamUiState.Loading -> LoadMoreFooter(LoadMoreState.Loading, onRetry = {})
        is TeamUiState.Shown -> team.members.forEach { member ->
            PersonCard(
                model = member.toCardUiModel(),
                onClick = if (member.hasProfile) ({ onMemberClick(member) }) else null,
                onChatClick = { onMemberChatClick(member) },
            )
        }
    }
}

@Composable
private fun TeamMemberUiModel.toCardUiModel(): PersonCardUiModel = PersonCardUiModel(
    name = user.name,
    subtitle = if (isInitiator) stringResource(Res.string.projectdetails_initiator) else roleTitle,
    avatar = if (hasProfile) AppAvatars.forKey(avatarKey) else null,
    stats = if (hasProfile) personStatsText(rating, completedProjects) else null,
    showChatButton = canContact,
    chatContentDescription = stringResource(Res.string.projectdetails_message_member, nameOrPlaceholder(user.name)),
)

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
    stats = personStatsText(rating, completedProjects),
    showChatButton = canContact,
    chatContentDescription = stringResource(Res.string.projectdetails_message_initiator),
)
