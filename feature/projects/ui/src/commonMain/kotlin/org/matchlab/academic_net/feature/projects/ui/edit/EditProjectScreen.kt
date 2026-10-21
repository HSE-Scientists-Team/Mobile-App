package org.matchlab.academic_net.feature.projects.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppConfirmDialog
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.InfoBannerTone
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.SpherePickerSheet
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies
import org.matchlab.academic_net.feature.projects.ui.VacantRoleCard
import org.matchlab.academic_net.feature.projects.ui.VacantRoleCardUiModel
import org.matchlab.academic_net.feature.projects.ui.create.ProjectGoalField
import org.matchlab.academic_net.feature.projects.ui.create.ProjectMonthFields
import org.matchlab.academic_net.feature.projects.ui.create.ProjectMonthSheet
import org.matchlab.academic_net.feature.projects.ui.create.ProjectProblemField
import org.matchlab.academic_net.feature.projects.ui.create.ProjectSphereField
import org.matchlab.academic_net.feature.projects.ui.create.ProjectTagsFormField
import org.matchlab.academic_net.feature.projects.ui.create.ProjectTitleField
import org.matchlab.academic_net.feature.projects.ui.create.ProjectTypeChips
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_add_role
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_cancel
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_close
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_conflict
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_delete
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_edit_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_load_project_error
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_not_found
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_retry
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_role_delete_text
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_role_delete_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_role_locked_hint
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_role_not_removable
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_roles_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_save_changes
import org.matchlab.academic_net.feature.projects.ui.role.RoleEditorSheet

/**
 * "04d Редактирование": the form of a published project. The title, the type, the sphere, the problem, the goal, the
 * tags and the months are saved by "Сохранить изменения", which closes the screen ([onClose]; 07 shows the changes from
 * the cache). The roles change at once, by requests of their own: the pencil edits a role (04n), the trash deletes a role
 * nobody works on (after 04r), "Добавить роль" adds one. Leaving the screen without saving asks nothing.
 *
 * @param onClose the cross of the app bar, and the successful save
 */
@Composable
fun EditProjectRoute(
    dependencies: ProjectsUiDependencies,
    projectId: ProjectId,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = "edit-${projectId.value}") {
        EditProjectViewModel(
            projectId = projectId,
            observeProject = dependencies.observeProject,
            loadProject = dependencies.loadProject,
            updateProject = dependencies.updateProject,
            addRole = dependencies.addRole,
            updateRole = dependencies.updateRole,
            removeRole = dependencies.removeRole,
            getSpheres = dependencies.getSpheres,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onClose()
    }

    EditProjectScreen(
        state = state,
        onClose = onClose,
        onTitleChange = viewModel::onTitleChange,
        onTypeSelect = viewModel::onTypeSelect,
        onSphereFieldClick = viewModel::onSphereFieldClick,
        onProblemChange = viewModel::onProblemChange,
        onGoalChange = viewModel::onGoalChange,
        onAddTag = viewModel::onAddTag,
        onRemoveTag = viewModel::onRemoveTag,
        onStartFieldClick = viewModel::onStartFieldClick,
        onEndFieldClick = viewModel::onEndFieldClick,
        onAddRoleClick = viewModel::onAddRoleClick,
        onEditRoleClick = viewModel::onRoleEditClick,
        onDeleteRoleClick = viewModel::onRoleDeleteClick,
        onSaveClick = viewModel::onSaveClick,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )

    if (state.isSpherePickerVisible) {
        SpherePickerSheet(
            spheres = state.spheres,
            selected = state.draft.sphere,
            loadFailed = state.spheresLoadFailed,
            onSelect = viewModel::onSphereSelect,
            onRetry = viewModel::onSpheresRetry,
            onDismiss = viewModel::onSpherePickerDismiss,
        )
    }
    state.monthPickerFor?.let { field ->
        ProjectMonthSheet(
            field = field,
            start = state.draft.start,
            end = state.draft.end,
            onSelect = viewModel::onMonthSelect,
            onDismiss = viewModel::onMonthPickerDismiss,
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
            errorText = state.roleFailure?.let { failure ->
                if (failure is ProjectConflictException) {
                    stringResource(Res.string.projects_conflict)
                } else {
                    failureText(failure)
                }
            },
        )
    }
    state.roleToDelete?.let { role ->
        AppConfirmDialog(
            title = stringResource(Res.string.projects_role_delete_title),
            text = stringResource(Res.string.projects_role_delete_text, role.title),
            confirmText = stringResource(Res.string.projects_delete),
            dismissText = stringResource(Res.string.projects_cancel),
            onConfirm = viewModel::onRoleDeleteConfirm,
            onDismiss = viewModel::onRoleDeleteDismiss,
            destructive = true,
        )
    }
}

@Composable
internal fun EditProjectScreen(
    state: EditProjectUiState,
    onClose: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTypeSelect: (ProjectType) -> Unit,
    onSphereFieldClick: () -> Unit,
    onProblemChange: (String) -> Unit,
    onGoalChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onStartFieldClick: () -> Unit,
    onEndFieldClick: () -> Unit,
    onAddRoleClick: () -> Unit,
    onEditRoleClick: (RoleId) -> Unit,
    onDeleteRoleClick: (RoleId) -> Unit,
    onSaveClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow)
            .imePadding(),
    ) {
        AppTopBar(
            title = stringResource(Res.string.projects_edit_title),
            onNavigateBack = onClose,
            navigationIcon = AppIcons.X,
            navigationContentDescription = stringResource(Res.string.projects_close),
        )
        when {
            state.isGone -> MessageState(title = stringResource(Res.string.projects_not_found))
            state.isFormReady -> EditForm(
                state = state,
                onTitleChange = onTitleChange,
                onTypeSelect = onTypeSelect,
                onSphereFieldClick = onSphereFieldClick,
                onProblemChange = onProblemChange,
                onGoalChange = onGoalChange,
                onAddTag = onAddTag,
                onRemoveTag = onRemoveTag,
                onStartFieldClick = onStartFieldClick,
                onEndFieldClick = onEndFieldClick,
                onAddRoleClick = onAddRoleClick,
                onEditRoleClick = onEditRoleClick,
                onDeleteRoleClick = onDeleteRoleClick,
                onSaveClick = onSaveClick,
            )
            state.loadFailure != null -> MessageState(
                title = stringResource(Res.string.projects_load_project_error),
                text = failureText(state.loadFailure),
                actionText = stringResource(Res.string.projects_retry),
                onAction = onRetry,
            )
            else -> LoadingState()
        }
    }
}

@Composable
private fun ColumnScope.EditForm(
    state: EditProjectUiState,
    onTitleChange: (String) -> Unit,
    onTypeSelect: (ProjectType) -> Unit,
    onSphereFieldClick: () -> Unit,
    onProblemChange: (String) -> Unit,
    onGoalChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onStartFieldClick: () -> Unit,
    onEndFieldClick: () -> Unit,
    onAddRoleClick: () -> Unit,
    onEditRoleClick: (RoleId) -> Unit,
    onDeleteRoleClick: (RoleId) -> Unit,
    onSaveClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val draft = state.draft
    val errors = state.errors
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when (val banner = state.banner) {
            null -> Unit
            EditBanner.Conflict -> InfoBanner(text = stringResource(Res.string.projects_conflict), tone = InfoBannerTone.Error)
            EditBanner.RoleNotRemovable ->
                InfoBanner(text = stringResource(Res.string.projects_role_not_removable), tone = InfoBannerTone.Error)
            is EditBanner.RoleDeleteFailed -> InfoBanner(text = failureText(banner.cause), tone = InfoBannerTone.Error)
        }
        ProjectTitleField(draft.title, onTitleChange, errors)
        ProjectTypeChips(draft.type, onTypeSelect, errors)
        ProjectSphereField(draft.sphere, onSphereFieldClick, errors)
        ProjectProblemField(draft.problem, onProblemChange, errors)
        ProjectGoalField(draft.goal, onGoalChange, errors)
        ProjectTagsFormField(draft.tags, onAddTag, onRemoveTag)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(Res.string.projects_roles_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            state.roles.forEach { role ->
                VacantRoleCard(
                    model = VacantRoleCardUiModel(role.title, role.skills, isEditable = true, canDelete = role.canDelete),
                    onDeleteClick = { onDeleteRoleClick(role.id) },
                    onEditClick = { onEditRoleClick(role.id) },
                )
                // 04j: the trash of a role with people is dimmed, and the card says why.
                if (!role.canDelete) {
                    Text(
                        text = stringResource(Res.string.projects_role_locked_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            AppButton(
                text = stringResource(Res.string.projects_add_role),
                onClick = onAddRoleClick,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.Outlined,
            )
        }
        ProjectMonthFields(draft.start, draft.end, onStartFieldClick, onEndFieldClick, errors)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.saveFailure?.let { failure ->
            Text(
                text = failureText(failure),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
            )
        }
        AppButton(
            text = stringResource(Res.string.projects_save_changes),
            onClick = onSaveClick,
            modifier = Modifier.fillMaxWidth(),
            loading = state.isSaving,
        )
    }
}
