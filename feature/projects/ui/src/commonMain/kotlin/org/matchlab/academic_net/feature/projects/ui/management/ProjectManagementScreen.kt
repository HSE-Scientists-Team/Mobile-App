package org.matchlab.academic_net.feature.projects.ui.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.components.ActionListItem
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppConfirmDialog
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTabs
import org.matchlab.academic_net.components.AppTagChip
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.PersonCard
import org.matchlab.academic_net.components.PersonCardUiModel
import org.matchlab.academic_net.components.TagsBlock
import org.matchlab.academic_net.components.TextBlock
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
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_block_goal
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_block_problem
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_block_tags
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_cancel
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_manage_tab_manage
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_manage_tab_overview
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_manage_tab_roles
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_member_rating
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_not_found
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_roles_empty
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_roles_occupied
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_roles_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_unpublish_confirm
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_unpublish_dialog_text
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_unpublish_dialog_title
import org.matchlab.academic_net.feature.projects.ui.completedProjectsText
import org.matchlab.academic_net.feature.projects.ui.label
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
                        1 -> RolesTab(project, onAddRoleClick)
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

/** "07 … Обзор". The design's dates card comes with the dates story of the second sprint. */
@Composable
private fun OverviewTab(project: ProjectDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = project.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppTagChip(project.type.label())
            AppTagChip(project.status.label())
            AppTagChip(project.sphere)
        }
    }
    TextBlock(stringResource(Res.string.projects_block_problem), project.problem)
    TextBlock(stringResource(Res.string.projects_block_goal), project.goal)
    if (project.tags.isNotEmpty()) {
        TagsBlock(stringResource(Res.string.projects_block_tags), project.tags)
    }
}

/** "07b … Роли". The kick button and the chat button need later stories, so members are plain cards. */
@Composable
private fun RolesTab(project: ProjectDetails, onAddRoleClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.projects_roles_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
        )
        Text(
            text = stringResource(Res.string.projects_roles_occupied, project.occupiedRoles.size, project.rolesCount),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
    project.occupiedRoles.forEach { role ->
        PersonCard(role.toCardUiModel())
    }
    project.vacantRoles.forEach { role ->
        VacantRoleCard(VacantRoleCardUiModel(role.title, role.skills))
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
private fun OccupiedRoleItem.toCardUiModel(): PersonCardUiModel {
    val completed = completedProjectsText(completedProjects)
    val stats = if (rating != null) {
        stringResource(Res.string.projects_member_rating, formatRating(rating)) + " · " + completed
    } else {
        completed
    }
    return PersonCardUiModel(
        name = memberName,
        subtitle = roleTitle,
        avatar = AppAvatars.forKey(avatarKey),
        isVerified = isMemberVerified,
        stats = stats,
    )
}

/** One decimal digit, with a point: 4.8. */
private fun formatRating(rating: Double): String {
    val tenths = (rating * 10).roundToInt().coerceIn(0, 50)
    return "${tenths / 10}.${tenths % 10}"
}
