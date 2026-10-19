package org.matchlab.academic_net.feature.responses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.components.AppConfirmDialog
import org.matchlab.academic_net.components.AppTabs
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.InfoBannerTone
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.ProjectCard
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.components.person.nameOrPlaceholder
import org.matchlab.academic_net.components.project.projectCardUiModel
import org.matchlab.academic_net.feature.responses.ui.generated.resources.Res
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_action_unavailable
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_action_withdraw
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_cancel
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_decline_confirm
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_decline_text
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_decline_title
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_empty_applications_text
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_empty_applications_title
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_empty_invitations_text
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_empty_invitations_title
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_invitation_role
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_load_error
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_retry
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_role_line
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_section_applications
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_section_invitations
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_tab_applications
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_tab_applications_count
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_tab_invitations
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_tab_invitations_count
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_title
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_withdraw_confirm
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_withdraw_text
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_withdraw_text_no_role
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_withdraw_title

/**
 * Tab "Отклики" (15 / 15c): the viewer's applications to roles ("Отклики · N") and the invitations they got
 * ("Приглашения · N"), each with the status of the record. A pending application has "Отозвать отклик" (15n asks first);
 * a pending invitation has ✓ (accepts at once) and × (15o asks first); decided records have only their chip. The list is
 * read again at each entry to the tab.
 *
 * @param onProjectClick a record: opens the page of its project
 * @param onContactInitiator the chat button: the chat with the initiator of the project of the record
 */
@Composable
fun ResponsesRoute(
    dependencies: ResponsesUiDependencies,
    onProjectClick: (ProjectId) -> Unit,
    onContactInitiator: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel {
        ResponsesViewModel(
            observeMyResponses = dependencies.observeMyResponses,
            refreshMyResponses = dependencies.refreshMyResponses,
            withdrawApplication = dependencies.withdrawApplication,
            decideInvitation = dependencies.decideInvitation,
            markInvitationsSeen = dependencies.markInvitationsSeen,
            clock = dependencies.clock,
            timeZone = dependencies.timeZone,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The tabs share one navigation destination: leaving the tab ends the composition but not the lifecycle, leaving
    // the screen or the app stops it. The same start reads the list again (coming back to the tab or to the app).
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        dependencies.visibility.onShown()
        viewModel.onShown()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        dependencies.visibility.onHidden()
        viewModel.onHidden()
    }
    DisposableEffect(Unit) {
        onDispose {
            dependencies.visibility.onHidden()
            viewModel.onHidden()
        }
    }

    ResponsesScreen(
        state = state,
        onProjectClick = onProjectClick,
        onContactInitiator = onContactInitiator,
        onWithdrawClick = viewModel::onWithdrawClick,
        onAcceptClick = viewModel::onAcceptClick,
        onDeclineClick = viewModel::onDeclineClick,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
    state.dialog?.let { dialog ->
        ResponseDialogView(dialog, viewModel::onDialogConfirm, viewModel::onDialogDismiss)
    }
}

@Composable
internal fun ResponsesScreen(
    state: ResponsesUiState,
    onProjectClick: (ProjectId) -> Unit,
    onContactInitiator: (UserSummary) -> Unit,
    onWithdrawClick: (ProjectJoinId) -> Unit,
    onAcceptClick: (ProjectJoinId) -> Unit,
    onDeclineClick: (ProjectJoinId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(ApplicationsTab) }
    val hasList = !state.isLoading && state.loadFailure == null
    Column(modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(Res.string.responses_title))
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp),
        ) {
            AppTabs(
                tabs = listOf(
                    // The numbers say how many records wait; none to count, or no list yet: no number (15d, 15e, 15f).
                    state.applicationsWaiting.takeIf { hasList && it > 0 }
                        ?.let { stringResource(Res.string.responses_tab_applications_count, it) }
                        ?: stringResource(Res.string.responses_tab_applications),
                    state.invitationsWaiting.takeIf { hasList && it > 0 }
                        ?.let { stringResource(Res.string.responses_tab_invitations_count, it) }
                        ?: stringResource(Res.string.responses_tab_invitations),
                ),
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )
        }
        val isInvitations = selectedTab == InvitationsTab
        when {
            state.loadFailure != null -> MessageState(
                title = stringResource(Res.string.responses_load_error),
                text = failureText(state.loadFailure),
                actionText = stringResource(Res.string.responses_retry),
                onAction = onRetry,
            )
            state.isLoading -> LoadingState()
            (if (isInvitations) state.invitations else state.applications).isEmpty() -> if (isInvitations) {
                MessageState(
                    title = stringResource(Res.string.responses_empty_invitations_title),
                    text = stringResource(Res.string.responses_empty_invitations_text),
                )
            } else {
                MessageState(
                    title = stringResource(Res.string.responses_empty_applications_title),
                    text = stringResource(Res.string.responses_empty_applications_text),
                )
            }
            else -> ResponseList(
                state = state,
                invitations = isInvitations,
                onProjectClick = onProjectClick,
                onContactInitiator = onContactInitiator,
                onWithdrawClick = onWithdrawClick,
                onAcceptClick = onAcceptClick,
                onDeclineClick = onDeclineClick,
            )
        }
    }
}

@Composable
private fun ResponseList(
    state: ResponsesUiState,
    invitations: Boolean,
    onProjectClick: (ProjectId) -> Unit,
    onContactInitiator: (UserSummary) -> Unit,
    onWithdrawClick: (ProjectJoinId) -> Unit,
    onAcceptClick: (ProjectJoinId) -> Unit,
    onDeclineClick: (ProjectJoinId) -> Unit,
) {
    val items = if (invitations) state.invitations else state.applications
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(
                    if (invitations) Res.string.responses_section_invitations else Res.string.responses_section_applications,
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        state.actionFailure?.let { failure ->
            item(key = "failure") {
                InfoBanner(
                    text = when (failure) {
                        JoinFailure.NotAllowed -> stringResource(Res.string.responses_action_unavailable)
                        is JoinFailure.Failed -> failureText(failure.cause)
                    },
                    tone = InfoBannerTone.Error,
                )
            }
        }
        items(items, key = { it.id.value }) { item ->
            val busy = item.id in state.inProgress
            if (invitations) {
                InvitationItem(
                    item = item,
                    busy = busy,
                    onClick = { onProjectClick(item.projectId) },
                    onAcceptClick = { onAcceptClick(item.id) },
                    onDeclineClick = { onDeclineClick(item.id) },
                    onChatClick = { onContactInitiator(item.initiator) },
                )
            } else {
                ApplicationItem(
                    item = item,
                    busy = busy,
                    onClick = { onProjectClick(item.projectId) },
                    onWithdrawClick = { onWithdrawClick(item.id) },
                    onChatClick = { onContactInitiator(item.initiator) },
                )
            }
        }
    }
}

/** "Card/Application": the project, the status chip and "Роль: … · отклик N назад"; a pending one has the actions. */
@Composable
private fun ApplicationItem(
    item: ResponseItem,
    busy: Boolean,
    onClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onChatClick: () -> Unit,
) {
    val role = item.roleTitle?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.responses_role_line, it) }
    val ago = item.daysAgo?.let { appliedAgoText(it) }
    ApplicationCard(
        model = ApplicationCardUiModel(
            title = item.projectTitle,
            subtitle = listOfNotNull(role, ago).joinToString(" · "),
            status = joinStatusChip(item.join.type, item.join.status, pendingChip = true),
            actionText = if (item.canWithdraw) stringResource(Res.string.responses_action_withdraw) else null,
            showChatButton = item.canWithdraw && item.initiator.id.value.isNotBlank(),
            actionLoading = busy,
        ),
        onClick = onClick,
        onActionClick = onWithdrawClick,
        onChatClick = onChatClick,
    )
}

/**
 * "Card/Project" of an invitation (15l, 15m): the project, the role the initiator invites to and their message; a
 * pending one has ✓ × and the chat, a decided one has its chip in place of the status tag.
 */
@Composable
private fun InvitationItem(
    item: ResponseItem,
    busy: Boolean,
    onClick: () -> Unit,
    onAcceptClick: () -> Unit,
    onDeclineClick: () -> Unit,
    onChatClick: () -> Unit,
) {
    val project = item.response.project
    val chip = joinStatusChip(item.join.type, item.join.status)
    val card = projectCardUiModel(
        title = project.title,
        author = nameOrPlaceholder(project.initiator.name),
        avatarKey = project.initiator.id.value,
        type = project.type,
        // A decided record has its own chip instead of the status of the project.
        status = if (chip == null) project.status else null,
        sphere = project.sphere?.name,
        rolesCount = project.roles.size,
        period = project.period,
    )
    ProjectCard(
        model = card.copy(
            statusChip = chip,
            invitationRole = item.roleTitle?.takeIf { it.isNotBlank() }
                ?.let { stringResource(Res.string.responses_invitation_role, it) },
            invitationMessage = item.join.message.takeIf { it.isNotBlank() },
            showInvitationActions = item.canDecide,
            invitationDeciding = busy,
        ),
        onClick = onClick,
        onAcceptClick = onAcceptClick,
        onDeclineClick = onDeclineClick,
        onChatClick = onChatClick,
    )
}

/** 15n and 15o: the confirmation of the action; both end something for good, so the confirming button is red. */
@Composable
private fun ResponseDialogView(dialog: ResponseDialog, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val role = dialog.roleTitle?.takeIf { it.isNotBlank() }
    when (dialog) {
        is ResponseDialog.Withdraw -> AppConfirmDialog(
            title = stringResource(Res.string.responses_withdraw_title),
            text = if (role != null) {
                stringResource(Res.string.responses_withdraw_text, role, dialog.projectTitle)
            } else {
                stringResource(Res.string.responses_withdraw_text_no_role, dialog.projectTitle)
            },
            confirmText = stringResource(Res.string.responses_withdraw_confirm),
            dismissText = stringResource(Res.string.responses_cancel),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            destructive = true,
        )
        is ResponseDialog.DeclineInvitation -> AppConfirmDialog(
            title = stringResource(Res.string.responses_decline_title),
            text = stringResource(Res.string.responses_decline_text, dialog.projectTitle),
            confirmText = stringResource(Res.string.responses_decline_confirm),
            dismissText = stringResource(Res.string.responses_cancel),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            destructive = true,
        )
    }
}

private const val ApplicationsTab = 0
private const val InvitationsTab = 1
