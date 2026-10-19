package org.matchlab.academic_net.feature.projects.ui.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.components.ActionListItem
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppConfirmDialog
import org.matchlab.academic_net.components.AppIconButton
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppStatusChip
import org.matchlab.academic_net.components.AppTabs
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.InfoBannerTone
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.PersonCard
import org.matchlab.academic_net.components.PersonCardUiModel
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.components.person.nameOrPlaceholder
import org.matchlab.academic_net.components.person.personStatsText
import org.matchlab.academic_net.components.project.ProjectDescriptionBlocks
import org.matchlab.academic_net.components.project.ProjectHeader
import org.matchlab.academic_net.components.project.ProjectPeriodCard
import org.matchlab.academic_net.components.project.RolesSectionHeader
import org.matchlab.academic_net.components.project.chip
import org.matchlab.academic_net.components.project.rolePlacesText
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectLifecycleAction
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies
import org.matchlab.academic_net.feature.projects.ui.ParticipantManageCard
import org.matchlab.academic_net.feature.projects.ui.VacantRoleCard
import org.matchlab.academic_net.feature.projects.ui.VacantRoleCardUiModel
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_action_error
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_action_unpublish
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_action_unpublish_hint
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_add_role
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_back
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_banner_recruiting
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_banner_unpublished
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_cancel
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_conflict
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_kick
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_kick_dialog_text
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_kick_dialog_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_kick_failed
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_load_project_error
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_member_not_allowed
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_responses
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_responses_count
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_responses_hint
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_manage_empty
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_manage_tab_manage
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_manage_tab_overview
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_manage_tab_roles
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_not_found
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_retry
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_roles_empty
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_roles_header
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_unpublish_confirm
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_unpublish_dialog_text
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_unpublish_dialog_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_action_cancel_project
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_action_cancel_project_hint
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_action_complete
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_action_complete_hint
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_action_edit
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_action_edit_hint
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_action_start
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_action_start_hint
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_banner_cancelled
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_banner_finished
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_banner_started
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_cancel_project_dialog_text
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_cancel_project_dialog_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_complete_confirm
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_complete_dialog_text
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_complete_dialog_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_lifecycle_not_allowed
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_manage_status
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_start_confirm
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_start_dialog_text
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_start_dialog_title
import org.matchlab.academic_net.feature.projects.ui.role.RoleEditorSheet

/**
 * "07 Управление проектом" for the initiator: the overview, the roles with their people, and 07c with the status of the
 * project and its actions (start, unpublish, edit, finish, cancel). Each tab starts with the row "Отклики · N" (07d). A
 * person of a role has the chat button and "Выгнать" (07i asks first). What the app does not know about the project
 * (`docs/api-values.md`) is left out, see [ProjectManagementViewModel].
 *
 * @param onEditProject the pencil of the app bar and the row "Редактировать проект": opens 04d
 * @param onMemberClick a person of a role whose profile is known: opens the profile
 * @param onContactMember the chat button of a person of a role
 * @param onResponsesClick the row "Отклики · N": opens 07d, the applications to the roles of the project
 */
@Composable
fun ProjectManagementRoute(
    dependencies: ProjectsUiDependencies,
    projectId: ProjectId,
    onNavigateBack: () -> Unit,
    onFindCollaborators: (ProjectId, RoleId) -> Unit,
    onEditProject: (ProjectId) -> Unit,
    onMemberClick: (UserId) -> Unit,
    onContactMember: (UserSummary) -> Unit,
    onResponsesClick: (ProjectId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = projectId.value) {
        ProjectManagementViewModel(
            projectId = projectId,
            observeProject = dependencies.observeProject,
            loadProject = dependencies.loadProject,
            unpublishProject = dependencies.unpublishProject,
            addRole = dependencies.addRole,
            changeLifecycle = dependencies.changeLifecycle,
            removeMember = dependencies.removeMember,
            capabilities = dependencies.capabilities,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProjectManagementScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onAddRoleClick = viewModel::onAddRoleClick,
        onUnpublishClick = viewModel::onUnpublishClick,
        onLifecycleClick = viewModel::onLifecycleClick,
        onEditClick = { onEditProject(projectId) },
        onFindPeopleClick = { roleId -> onFindCollaborators(projectId, roleId) },
        onMemberClick = onMemberClick,
        onContactMember = onContactMember,
        onResponsesClick = { onResponsesClick(projectId) },
        onKickClick = viewModel::onKickClick,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
    state.kickDialog?.let { target ->
        AppConfirmDialog(
            title = stringResource(Res.string.projects_kick_dialog_title),
            text = stringResource(Res.string.projects_kick_dialog_text, nameOrPlaceholder(target.name)),
            confirmText = stringResource(Res.string.projects_kick),
            dismissText = stringResource(Res.string.projects_cancel),
            onConfirm = viewModel::onKickConfirm,
            onDismiss = viewModel::onKickDismiss,
            destructive = true,
        )
    }

    if (state.isUnpublishConfirmVisible) {
        AppConfirmDialog(
            title = stringResource(Res.string.projects_unpublish_dialog_title),
            text = stringResource(Res.string.projects_unpublish_dialog_text),
            confirmText = stringResource(Res.string.projects_unpublish_confirm),
            dismissText = stringResource(Res.string.projects_cancel),
            onConfirm = viewModel::onUnpublishConfirm,
            onDismiss = viewModel::onUnpublishDismiss,
        )
    }
    state.lifecycleDialog?.let { action ->
        LifecycleDialog(action, onConfirm = viewModel::onLifecycleConfirm, onDismiss = viewModel::onLifecycleDismiss)
    }
    state.roleEditor?.let { editor ->
        RoleEditorSheet(
            state = editor,
            onTitleChange = viewModel::onRoleTitleChange,
            onAddSkill = viewModel::onRoleSkillAdd,
            onRemoveSkill = viewModel::onRoleSkillRemove,
            onSave = viewModel::onRoleSave,
            onDismiss = viewModel::onRoleEditorDismiss,
            isSaving = state.isSavingRole,
            errorText = state.roleFailure?.let { roleFailureText(it) },
        )
    }
}

@Composable
internal fun ProjectManagementScreen(
    state: ProjectManagementUiState,
    onNavigateBack: () -> Unit,
    onAddRoleClick: () -> Unit,
    onUnpublishClick: () -> Unit,
    onLifecycleClick: (ProjectLifecycleAction) -> Unit,
    onEditClick: () -> Unit,
    onFindPeopleClick: (RoleId) -> Unit,
    onMemberClick: (UserId) -> Unit,
    onContactMember: (UserSummary) -> Unit,
    onResponsesClick: () -> Unit,
    onKickClick: (UserId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val project = state.project

    Column(modifier.fillMaxSize().background(colors.surfaceContainerLow)) {
        AppTopBar(
            title = project?.title.orEmpty(),
            onNavigateBack = onNavigateBack,
            navigationContentDescription = stringResource(Res.string.projects_back),
            actions = {
                // The pencil of the design: the way to 04d for the initiator of a project that can still change.
                if (project?.canEdit == true) {
                    AppIconButton(
                        icon = AppIcons.Pencil,
                        contentDescription = stringResource(Res.string.projects_action_edit),
                        onClick = onEditClick,
                        contentColor = colors.primary,
                    )
                }
            },
        )
        when {
            project != null -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 16.dp),
                ) {
                    AppTabs(
                        tabs = listOf(
                            stringResource(Res.string.projects_manage_tab_overview),
                            stringResource(Res.string.projects_manage_tab_roles),
                            stringResource(Res.string.projects_manage_tab_manage),
                        ),
                        selectedIndex = selectedTab,
                        onTabSelected = { selectedTab = it },
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (selectedTab) {
                        0 -> OverviewTab(project, onResponsesClick)
                        1 -> RolesTab(
                            project = project,
                            memberFailure = state.memberFailure,
                            removingMember = state.removingMember,
                            onAddRoleClick = onAddRoleClick,
                            onFindPeopleClick = onFindPeopleClick,
                            onMemberClick = onMemberClick,
                            onContactMember = onContactMember,
                            onKickClick = onKickClick,
                            onResponsesClick = onResponsesClick,
                        )
                        else -> ManageTab(
                            project = project,
                            isBusy = state.isUnpublishing || state.isChangingLifecycle,
                            onUnpublishClick = onUnpublishClick,
                            onLifecycleClick = onLifecycleClick,
                            onEditClick = onEditClick,
                            onResponsesClick = onResponsesClick,
                        )
                    }
                    if (state.actionFailed) {
                        ActionErrorText(stringResource(Res.string.projects_action_error))
                    }
                    when (val failure = state.lifecycleFailure) {
                        null -> Unit
                        LifecycleFailure.NotAllowed -> ActionErrorText(stringResource(Res.string.projects_lifecycle_not_allowed))
                        is LifecycleFailure.Failed -> ActionErrorText(failureText(failure.cause))
                    }
                }
            }
            state.loadFailure != null -> MessageState(
                title = stringResource(Res.string.projects_load_project_error),
                text = failureText(state.loadFailure),
                actionText = stringResource(Res.string.projects_retry),
                onAction = onRetry,
            )
            state.isLoading -> LoadingState()
            else -> MessageState(title = stringResource(Res.string.projects_not_found))
        }
    }
}

/**
 * What the role sheet says when sending the role failed: a project that changed on the server (a 409) is told in its
 * own words, anything else by the kind of the failure.
 */
@Composable
private fun roleFailureText(cause: Throwable): String =
    if (cause is ProjectConflictException) stringResource(Res.string.projects_conflict) else failureText(cause)

/** The row "Отклики · N" with "Кандидаты на вакантные роли": the way to 07d, first on every tab of the initiator. */
@Composable
private fun ResponsesEntry(project: ProjectDetails, onClick: () -> Unit) {
    if (!project.showsResponses) return
    ActionListItem(
        title = if (project.pendingJoinCount > 0) {
            stringResource(Res.string.projects_responses_count, project.pendingJoinCount)
        } else {
            stringResource(Res.string.projects_responses)
        },
        subtitle = stringResource(Res.string.projects_responses_hint),
        icon = AppIcons.Users,
        onClick = onClick,
    )
}

/** "07 … Обзор". The chips of the type, the status and the sphere, and the card of the months, are drawn when known. */
@Composable
private fun ColumnScope.OverviewTab(project: ProjectDetails, onResponsesClick: () -> Unit) {
    ResponsesEntry(project, onResponsesClick)
    ProjectHeader(project.title, project.type, project.status, project.sphere)
    ProjectDescriptionBlocks(project.problem, project.goal, project.tags)
    project.period?.let { ProjectPeriodCard(it) }
}

/**
 * "07b … Роли". Each person of a role is a card with the chat button and "Выгнать" (07i asks first; an arc replaces the
 * label while the member is removed, 07u); the initiator has no "Выгнать". A person whose profile could not be read is
 * "Без имени" with the neutral avatar, has no chat and is not a button. The search button of a role with a free place
 * opens "08 Поиск коллабораторов" for it. The failure of a removal is a banner under the row "Отклики · N" (07v).
 *
 * The counter is of places. Each role shows its people (as many as the server listed, G52), then a card of the role
 * when it has a free place or has places taken by people the app does not know; that card has the line of taken places
 * when the role has several or none is free.
 */
@Composable
private fun RolesTab(
    project: ProjectDetails,
    memberFailure: MemberFailure?,
    removingMember: UserId?,
    onAddRoleClick: () -> Unit,
    onFindPeopleClick: (RoleId) -> Unit,
    onMemberClick: (UserId) -> Unit,
    onContactMember: (UserSummary) -> Unit,
    onKickClick: (UserId) -> Unit,
    onResponsesClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ResponsesEntry(project, onResponsesClick)
    when (memberFailure) {
        null -> Unit
        MemberFailure.NotAllowed ->
            InfoBanner(text = stringResource(Res.string.projects_member_not_allowed), tone = InfoBannerTone.Error)
        is MemberFailure.Failed ->
            InfoBanner(text = stringResource(Res.string.projects_kick_failed), tone = InfoBannerTone.Error)
    }
    RolesSectionHeader(
        title = stringResource(Res.string.projects_roles_header),
        occupiedCount = project.occupiedPlaces,
        totalCount = project.totalPlaces,
    )
    project.roles.forEach { role ->
        role.members.forEach { member ->
            val card = member.toCardUiModel(role.title).copy(showChatButton = member.canContact)
            val open = if (member.hasProfile) ({ onMemberClick(member.userId) }) else null
            val chat = { onContactMember(UserSummary(member.userId, member.name)) }
            if (member.canKick) {
                ParticipantManageCard(
                    person = card,
                    onKickClick = { onKickClick(member.userId) },
                    onChatClick = chat,
                    onClick = open,
                    removing = removingMember == member.userId,
                )
            } else {
                PersonCard(model = card, onClick = open, onChatClick = chat)
            }
        }
        if (role.showsCard) {
            // The search for people who could take the role starts from the button of the card, which
            // only the initiator gets for a role with a free place; the rest of the card is not clickable.
            VacantRoleCard(
                model = VacantRoleCardUiModel(
                    role = role.title,
                    skills = role.skills,
                    canFindPeople = role.canFindPeople,
                    places = if (role.showsPlaces) rolePlacesText(role.occupied, role.slots) else null,
                ),
                onFindPeopleClick = { onFindPeopleClick(role.id) },
            )
        }
    }
    if (project.rolesCount == 0) {
        Text(
            text = stringResource(Res.string.projects_roles_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
    }
    AppButton(
        text = stringResource(Res.string.projects_add_role),
        onClick = onAddRoleClick,
        modifier = Modifier.fillMaxWidth(),
        variant = AppButtonVariant.Outlined,
    )
}

/**
 * "07c … Управление": the status of the project with its chip, the banner that tells what the status means, and the
 * actions in the order of the design: "Начать проект", "Снять с публикации", "Редактировать проект", "Завершить проект",
 * "Отменить проект" (red). Each is drawn only when [ProjectDetails] offers it. A status the app does not know has no chip
 * and no banner; a tab that has neither a banner nor an action says so.
 *
 * @param isBusy a request of an action is on its way: a tap would only open a second dialog, so the rows do nothing
 */
@Composable
private fun ManageTab(
    project: ProjectDetails,
    isBusy: Boolean,
    onUnpublishClick: () -> Unit,
    onLifecycleClick: (ProjectLifecycleAction) -> Unit,
    onEditClick: () -> Unit,
    onResponsesClick: () -> Unit,
) {
    val status = project.status
    if (status != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.projects_manage_status),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val chip = status.chip()
            AppStatusChip(chip.text, chip.tone, showIcon = false)
        }
    }
    // After the row of the status and before its banner (07c).
    ResponsesEntry(project, onResponsesClick)
    if (status != null) InfoBanner(text = stringResource(status.bannerText()))
    val actions = project.lifecycleActions
    ProjectLifecycleAction.Start.takeIf { it in actions }?.let { LifecycleRow(it, isBusy, onLifecycleClick) }
    if (project.canUnpublish) {
        ActionListItem(
            title = stringResource(Res.string.projects_action_unpublish),
            subtitle = stringResource(Res.string.projects_action_unpublish_hint),
            icon = AppIcons.EyeOff,
            onClick = { if (!isBusy) onUnpublishClick() },
        )
    }
    if (project.offersEditAction) {
        ActionListItem(
            title = stringResource(Res.string.projects_action_edit),
            subtitle = stringResource(Res.string.projects_action_edit_hint),
            icon = AppIcons.Pencil,
            onClick = onEditClick,
        )
    }
    ProjectLifecycleAction.Complete.takeIf { it in actions }?.let { LifecycleRow(it, isBusy, onLifecycleClick) }
    ProjectLifecycleAction.Cancel.takeIf { it in actions }?.let { LifecycleRow(it, isBusy, onLifecycleClick) }
    if (status == null && !project.canUnpublish && !project.offersEditAction && actions.isEmpty()) {
        Text(
            text = stringResource(Res.string.projects_manage_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LifecycleRow(
    action: ProjectLifecycleAction,
    isBusy: Boolean,
    onLifecycleClick: (ProjectLifecycleAction) -> Unit,
) {
    val texts = action.rowTexts()
    ActionListItem(
        title = stringResource(texts.title),
        subtitle = stringResource(texts.hint),
        icon = when (action) {
            ProjectLifecycleAction.Start -> AppIcons.Check
            ProjectLifecycleAction.Complete -> AppIcons.Flag
            ProjectLifecycleAction.Cancel -> AppIcons.Ban
        },
        // A tap while a request is in flight would only open a second dialog.
        onClick = { if (!isBusy) onLifecycleClick(action) },
        destructive = action == ProjectLifecycleAction.Cancel,
    )
}

/** The words of an action: its row and, in [LifecycleDialog], its confirmation. */
private class ActionTexts(
    val title: StringResource,
    val hint: StringResource,
    val dialogTitle: StringResource,
    val dialogText: StringResource,
    val confirm: StringResource,
    val dismiss: StringResource,
)

private fun ProjectLifecycleAction.rowTexts(): ActionTexts = when (this) {
    ProjectLifecycleAction.Start -> ActionTexts(
        title = Res.string.projects_action_start,
        hint = Res.string.projects_action_start_hint,
        dialogTitle = Res.string.projects_start_dialog_title,
        dialogText = Res.string.projects_start_dialog_text,
        confirm = Res.string.projects_start_confirm,
        dismiss = Res.string.projects_cancel,
    )
    ProjectLifecycleAction.Complete -> ActionTexts(
        title = Res.string.projects_action_complete,
        hint = Res.string.projects_action_complete_hint,
        dialogTitle = Res.string.projects_complete_dialog_title,
        dialogText = Res.string.projects_complete_dialog_text,
        confirm = Res.string.projects_complete_confirm,
        dismiss = Res.string.projects_cancel,
    )
    // "Назад" and not "Отмена" in the dialog of the cancellation, so that nobody mixes the two up (07l).
    ProjectLifecycleAction.Cancel -> ActionTexts(
        title = Res.string.projects_action_cancel_project,
        hint = Res.string.projects_action_cancel_project_hint,
        dialogTitle = Res.string.projects_cancel_project_dialog_title,
        dialogText = Res.string.projects_cancel_project_dialog_text,
        confirm = Res.string.projects_action_cancel_project,
        dismiss = Res.string.projects_back,
    )
}

/** "07j", "07k", "07l": the confirmation of an action; the cancellation of the project is the destructive one. */
@Composable
private fun LifecycleDialog(action: ProjectLifecycleAction, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val texts = action.rowTexts()
    AppConfirmDialog(
        title = stringResource(texts.dialogTitle),
        text = stringResource(texts.dialogText),
        confirmText = stringResource(texts.confirm),
        dismissText = stringResource(texts.dismiss),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = action == ProjectLifecycleAction.Cancel,
    )
}

/** What the banner of 07c says about the status. */
private fun ProjectStatus.bannerText(): StringResource = when (this) {
    ProjectStatus.Recruiting -> Res.string.projects_banner_recruiting
    ProjectStatus.Unpublished -> Res.string.projects_banner_unpublished
    ProjectStatus.Started -> Res.string.projects_banner_started
    ProjectStatus.Finished -> Res.string.projects_banner_finished
    ProjectStatus.Cancelled -> Res.string.projects_banner_cancelled
}

@Composable
private fun ActionErrorText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/** A person on a role as the person card shows them: the role is the line under the name. An empty name is "Без имени". */
@Composable
private fun RoleMemberItem.toCardUiModel(roleTitle: String): PersonCardUiModel = PersonCardUiModel(
    name = name,
    subtitle = roleTitle,
    // A person whose profile could not be read has the neutral avatar and no numbers ("Без имени", 04f).
    avatar = if (hasProfile) AppAvatars.forKey(avatarKey) else null,
    stats = if (hasProfile) personStatsText(rating, completedProjects) else null,
)
