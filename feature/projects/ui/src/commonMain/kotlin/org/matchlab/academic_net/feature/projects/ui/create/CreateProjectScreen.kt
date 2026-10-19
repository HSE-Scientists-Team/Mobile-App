package org.matchlab.academic_net.feature.projects.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.SpherePickerSheet
import org.matchlab.academic_net.feature.projects.domain.ProjectDraftError
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies
import org.matchlab.academic_net.feature.projects.ui.VacantRoleCard
import org.matchlab.academic_net.feature.projects.ui.VacantRoleCardUiModel
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_add_role
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_close
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_create_unavailable
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_error_role
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_new_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_publish
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_publish_error
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_publishing
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_roles_title
import org.matchlab.academic_net.feature.projects.ui.role.RoleEditorSheet

/**
 * "05 Создание проекта": the title, the type, the sphere, the problem, the goal, the tags, the roles and the first and
 * the last month of the project. "Опубликовать проект" sends the form; when it is accepted the screen leaves for the new
 * project ([onPublished]), when the request fails the message is under the form, the form keeps what was typed and the
 * same button sends it again.
 *
 * @param onClose the cross of the app bar
 * @param onPublished the project was created: the screen leaves for it
 */
@Composable
fun CreateProjectRoute(
    dependencies: ProjectsUiDependencies,
    onClose: () -> Unit,
    onPublished: (ProjectId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel { CreateProjectViewModel(dependencies.publishProject, dependencies.getSpheres) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.publishedProjectId) {
        state.publishedProjectId?.let(onPublished)
    }

    CreateProjectScreen(
        state = state,
        onClose = onClose,
        onTitleChange = viewModel::onTitleChange,
        onTypeSelect = viewModel::onTypeSelect,
        onSphereFieldClick = viewModel::onSphereFieldClick,
        onStartFieldClick = viewModel::onStartFieldClick,
        onEndFieldClick = viewModel::onEndFieldClick,
        onProblemChange = viewModel::onProblemChange,
        onGoalChange = viewModel::onGoalChange,
        onAddTag = viewModel::onAddTag,
        onRemoveTag = viewModel::onRemoveTag,
        onAddRoleClick = viewModel::onAddRoleClick,
        onEditRoleClick = viewModel::onEditRoleClick,
        onDeleteRoleClick = viewModel::onDeleteRoleClick,
        onPublishClick = viewModel::onPublishClick,
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
        )
    }
}

@Composable
internal fun CreateProjectScreen(
    state: CreateProjectUiState,
    onClose: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTypeSelect: (ProjectType) -> Unit,
    onSphereFieldClick: () -> Unit,
    onStartFieldClick: () -> Unit,
    onEndFieldClick: () -> Unit,
    onProblemChange: (String) -> Unit,
    onGoalChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onAddRoleClick: () -> Unit,
    onEditRoleClick: (Int) -> Unit,
    onDeleteRoleClick: (Int) -> Unit,
    onPublishClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val draft = state.draft
    val errors = state.errors

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow)
            .imePadding(),
    ) {
        AppTopBar(
            title = stringResource(Res.string.projects_new_title),
            onNavigateBack = onClose,
            navigationIcon = AppIcons.X,
            navigationContentDescription = stringResource(Res.string.projects_close),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
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
                draft.roles.forEachIndexed { index, role ->
                    VacantRoleCard(
                        model = VacantRoleCardUiModel(role.title, role.skills, isEditable = true),
                        onDeleteClick = { onDeleteRoleClick(index) },
                        onEditClick = { onEditRoleClick(index) },
                    )
                }
                if (ProjectDraftError.RoleInvalid in errors) {
                    Text(
                        text = stringResource(Res.string.projects_error_role),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.error,
                    )
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
            if (state.publishFailed) {
                Text(
                    // A source that cannot create projects at all is not a failure to try again.
                    text = stringResource(
                        if (state.publishUnavailable) Res.string.projects_create_unavailable else Res.string.projects_publish_error,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
            }
            AppButton(
                text = stringResource(
                    if (state.isPublishing) Res.string.projects_publishing else Res.string.projects_publish,
                ),
                onClick = onPublishClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isPublishing,
            )
        }
    }
}
