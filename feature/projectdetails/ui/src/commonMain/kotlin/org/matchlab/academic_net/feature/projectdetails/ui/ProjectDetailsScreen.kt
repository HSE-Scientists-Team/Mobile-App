package org.matchlab.academic_net.feature.projectdetails.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppConfirmDialog
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.InfoBannerTone
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.PersonCard
import org.matchlab.academic_net.components.PersonCardUiModel
import org.matchlab.academic_net.components.ReportIconButton
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
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_cancel
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_leave
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_leave_confirm
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_leave_dialog_text
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_leave_dialog_title
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_leave_not_allowed
import org.matchlab.academic_net.feature.projectdetails.ui.generated.resources.projectdetails_left_banner
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
 * "04 Проект": the page of a published project of someone else, to decide whether to join it. A free role has
 * "Откликнуться" (04s opens it; a role the viewer has applied to is "Вы откликнулись", 04x), the flag of the app bar
 * reports the project (09j), and a member who is not the initiator has "Покинуть проект" under the page (04y asks
 * first). Sharing is not here yet.
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
 * @param onApplyClick "Откликнуться" of a free role: the host opens the sheet 04s for it
 * @param onReportClick the flag of the app bar: the host opens the sheet 09j
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
    onApplyClick: (RoleId) -> Unit,
    onReportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = projectId.value) {
        ProjectDetailsViewModel(
            projectId = projectId,
            getProjectDetails = dependencies.getProjectDetails,
            getProjectTeam = dependencies.getProjectTeam,
            observeAppliedRoles = dependencies.observeAppliedRoles,
            leaveProject = dependencies.leaveProject,
        )
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
        onApplyClick = onApplyClick,
        onReportClick = onReportClick,
        onLeaveClick = viewModel::onLeaveClick,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
    (state as? ProjectDetailsUiState.Content)?.takeIf { it.isLeaveConfirmVisible }?.let { content ->
        AppConfirmDialog(
            title = stringResource(Res.string.projectdetails_leave_dialog_title),
            text = stringResource(Res.string.projectdetails_leave_dialog_text, content.project.title),
            confirmText = stringResource(Res.string.projectdetails_leave_confirm),
            dismissText = stringResource(Res.string.projectdetails_cancel),
            onConfirm = viewModel::onLeaveConfirm,
            onDismiss = viewModel::onLeaveDismiss,
            destructive = true,
        )
    }
}

@Composable
internal fun ProjectDetailsScreen(
    state: ProjectDetailsUiState,
    onNavigateBack: () -> Unit,
    onInitiatorClick: () -> Unit,
    onContactInitiatorClick: () -> Unit,
    onTeamMemberClick: (TeamMemberUiModel) -> Unit,
    onTeamMemberChatClick: (TeamMemberUiModel) -> Unit,
    onApplyClick: (RoleId) -> Unit,
    onReportClick: () -> Unit,
    onLeaveClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        AppTopBar(
            title = stringResource(Res.string.projectdetails_title),
            onNavigateBack = onNavigateBack,
            actions = {
                // Anyone but the initiator may report the project (09j).
                if ((state as? ProjectDetailsUiState.Content)?.project?.canReport == true) {
                    ReportIconButton(onClick = onReportClick)
                }
            },
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
            is ProjectDetailsUiState.Content -> {
                ProjectContent(
                    project = state.project,
                    hasLeft = state.hasLeft,
                    onInitiatorClick = onInitiatorClick,
                    onContactInitiatorClick = onContactInitiatorClick,
                    onTeamMemberClick = onTeamMemberClick,
                    onTeamMemberChatClick = onTeamMemberChatClick,
                    onApplyClick = onApplyClick,
                )
                if (state.project.canLeave && !state.hasLeft) LeaveFooter(state, onLeaveClick)
            }
        }
    }
}

@Composable
private fun ColumnScope.ProjectContent(
    project: ProjectDetailsUiModel,
    hasLeft: Boolean,
    onInitiatorClick: () -> Unit,
    onContactInitiatorClick: () -> Unit,
    onTeamMemberClick: (TeamMemberUiModel) -> Unit,
    onTeamMemberChatClick: (TeamMemberUiModel) -> Unit,
    onApplyClick: (RoleId) -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 04i: the viewer has just left the project.
        if (hasLeft) InfoBanner(text = stringResource(Res.string.projectdetails_left_banner), icon = AppIcons.Check)
        ProjectHeader(project.title, project.type, project.status, project.sphere)
        PersonCard(
            model = project.initiator.toCardUiModel(),
            onClick = onInitiatorClick,
            onChatClick = onContactInitiatorClick,
        )
        ProjectDescriptionBlocks(project.problem, project.goal, project.tags)
        TeamSection(project.team, onTeamMemberClick, onTeamMemberChatClick)
        RolesSection(project, onApplyClick)
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
 * 04g, 04h: "Покинуть проект" under the page: the full-width outlined button in the error color, with the banner of a
 * failure above it. An arc replaces the label while the request runs.
 */
@Composable
private fun LeaveFooter(content: ProjectDetailsUiState.Content, onLeaveClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val failure = content.leaveFailure) {
            null -> Unit
            LeaveFailure.NotAllowed ->
                InfoBanner(text = stringResource(Res.string.projectdetails_leave_not_allowed), tone = InfoBannerTone.Error)
            is LeaveFailure.Failed -> InfoBanner(text = failureText(failure.cause), tone = InfoBannerTone.Error)
        }
        AppButton(
            text = stringResource(Res.string.projectdetails_leave),
            onClick = onLeaveClick,
            modifier = Modifier.fillMaxWidth(),
            variant = AppButtonVariant.OutlinedError,
            loading = content.isLeaving,
        )
    }
}

/**
 * The open roles with "N из M занято" counted in places. A project without roles has no section; with every role
 * taken the section says so instead of listing nothing. A role of several places says how many are taken.
 */
@Composable
private fun ColumnScope.RolesSection(project: ProjectDetailsUiModel, onApplyClick: (RoleId) -> Unit) {
    if (project.rolesCount == 0) return
    RolesSectionHeader(
        title = stringResource(Res.string.projectdetails_open_roles),
        occupiedCount = project.occupiedPlaces,
        totalCount = project.totalPlaces,
    )
    project.vacantRoles.forEach { role ->
        RoleApplyCard(
            model = RoleApplyCardUiModel(role.title, role.skills, occupied = role.occupied, slots = role.slots),
            // Only a free role of a project that takes people has the button; one applied to says so and takes no tap.
            onApplyClick = if (project.canApply) ({ onApplyClick(role.id) }) else null,
            applied = role.id in project.appliedRoles,
        )
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
