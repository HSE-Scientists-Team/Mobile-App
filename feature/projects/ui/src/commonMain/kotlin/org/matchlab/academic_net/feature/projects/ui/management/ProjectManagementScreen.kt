package org.matchlab.academic_net.feature.projects.ui.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.components.ActionListItem
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppConfirmDialog
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTabs
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.PersonCard
import org.matchlab.academic_net.components.PersonCardUiModel
import org.matchlab.academic_net.components.person.personStatsText
import org.matchlab.academic_net.components.project.ProjectDescriptionBlocks
import org.matchlab.academic_net.components.project.ProjectHeader
import org.matchlab.academic_net.components.project.ProjectPeriodCard
import org.matchlab.academic_net.components.project.RolesSectionHeader
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies
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
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_manage_tab_manage
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_manage_tab_overview
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_manage_tab_roles
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_not_found
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_roles_empty
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_roles_header
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_unpublish_confirm
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_unpublish_dialog_text
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_unpublish_dialog_title
import org.matchlab.academic_net.feature.projects.ui.role.RoleEditorSheet

/**
 * "07 Управление проектом" for the initiator. Editing the project and the lifecycle actions
 * (start, finish, cancel) of the design belong to later sprints and are not shown.
 */
@Composable
fun ProjectManagementRoute(
    dependencies: ProjectsUiDependencies,
    projectId: ProjectId,
    onNavigateBack: () -> Unit,
    onFindCollaborators: (ProjectId, RoleId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = projectId.value) {
        ProjectManagementViewModel(
            projectId = projectId,
            observeProject = dependencies.observeProject,
            unpublishProject = dependencies.unpublishProject,
            addRole = dependencies.addRole,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProjectManagementScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onAddRoleClick = viewModel::onAddRoleClick,
        onUnpublishClick = viewModel::onUnpublishClick,
        onFindPeopleClick = { roleId -> onFindCollaborators(projectId, roleId) },
        modifier = modifier,
    )

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
    state.roleEditor?.let { editor ->
        RoleEditorSheet(
            state = editor,
            onTitleChange = viewModel::onRoleTitleChange,
            onAddSkill = viewModel::onRoleSkillAdd,
            onRemoveSkill = viewModel::onRoleSkillRemove,
            onSave = viewModel::onRoleSave,
            onDismiss = viewModel::onRoleEditorDismiss,
            isSaving = state.isSavingRole,
        )
    }
}

@Composable
internal fun ProjectManagementScreen(
    state: ProjectManagementUiState,
    onNavigateBack: () -> Unit,
    onAddRoleClick: () -> Unit,
    onUnpublishClick: () -> Unit,
    onFindPeopleClick: (RoleId) -> Unit,
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
                        0 -> OverviewTab(project)
                        1 -> RolesTab(project, onAddRoleClick, onFindPeopleClick)
                        else -> ManageTab(project, state.isUnpublishing, onUnpublishClick)
                    }
                    if (state.actionFailed) {
                        Text(
                            text = stringResource(Res.string.projects_action_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                        )
                    }
                }
            }
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            else -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.projects_not_found),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** "07 … Обзор". */
@Composable
private fun ColumnScope.OverviewTab(project: ProjectDetails) {
    ProjectHeader(project.title, project.type, project.status, project.sphere)
    ProjectDescriptionBlocks(project.problem, project.goal, project.tags)
    ProjectPeriodCard(project.period)
}

/**
 * "07b … Роли". The kick button and the chat button of members need later stories, so members
 * are plain cards; the search button of a vacant role opens "08 Поиск коллабораторов" for it.
 */
@Composable
private fun RolesTab(
    project: ProjectDetails,
    onAddRoleClick: () -> Unit,
    onFindPeopleClick: (RoleId) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    RolesSectionHeader(
        title = stringResource(Res.string.projects_roles_header),
        occupiedCount = project.occupiedRoles.size,
        totalCount = project.rolesCount,
    )
    project.occupiedRoles.forEach { role ->
        PersonCard(role.toCardUiModel())
    }
    project.vacantRoles.forEach { role ->
        // The search for people who could take the role starts from the button of the card, which
        // only the initiator gets; the rest of the card is not clickable.
        VacantRoleCard(
            model = VacantRoleCardUiModel(role.title, role.skills, canFindPeople = role.canFindPeople),
            onFindPeopleClick = { onFindPeopleClick(role.id) },
        )
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

/** "07c … Управление": only "Снять с публикации" belongs to the first sprint. */
@Composable
private fun ManageTab(project: ProjectDetails, isUnpublishing: Boolean, onUnpublishClick: () -> Unit) {
    InfoBanner(
        text = stringResource(
            when (project.status) {
                ProjectStatus.Recruiting -> Res.string.projects_banner_recruiting
                ProjectStatus.Unpublished -> Res.string.projects_banner_unpublished
            },
        ),
    )
    if (project.canUnpublish) {
        ActionListItem(
            title = stringResource(Res.string.projects_action_unpublish),
            subtitle = stringResource(Res.string.projects_action_unpublish_hint),
            icon = AppIcons.EyeOff,
            // A tap while the request is in flight would only open a second dialog.
            onClick = { if (!isUnpublishing) onUnpublishClick() },
        )
    }
}

@Composable
private fun OccupiedRoleItem.toCardUiModel(): PersonCardUiModel = PersonCardUiModel(
    name = memberName,
    subtitle = roleTitle,
    avatar = AppAvatars.forKey(avatarKey),
    isVerified = isMemberVerified,
    stats = personStatsText(rating, completedProjects),
)
