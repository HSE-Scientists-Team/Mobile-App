package org.matchlab.academic_net.feature.projects.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppSelectField
import org.matchlab.academic_net.components.AppTagsField
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.SpherePickerSheet
import org.matchlab.academic_net.components.format.yearMonthText
import org.matchlab.academic_net.components.project.label
import org.matchlab.academic_net.feature.projects.domain.ProjectDraftError
import org.matchlab.academic_net.feature.projects.domain.ProjectLimits
import org.matchlab.academic_net.feature.projects.ui.ProjectsUiDependencies
import org.matchlab.academic_net.feature.projects.ui.VacantRoleCard
import org.matchlab.academic_net.feature.projects.ui.VacantRoleCardUiModel
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_add_role
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_close
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_create_unavailable
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_end_sheet_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_error_end_before_start
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_error_month
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_error_required
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_error_role
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_error_sphere
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_error_title_too_long
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_error_type
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_field_end
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_field_goal
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_field_goal_hint
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_field_month_hint
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_field_problem
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_field_problem_hint
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_field_sphere
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_field_sphere_hint
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_field_start
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_field_tags
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_field_tags_hint
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_field_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_field_title_hint
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_field_type
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_new_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_publish
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_publish_error
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_publishing
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_roles_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_start_sheet_title
import org.matchlab.academic_net.feature.projects.ui.role.RoleEditorSheet

/**
 * "05 Создание проекта". The start and end dates of the design are left out: setting a project's
 * dates is a story of the second sprint.
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
        val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
        val draft = state.draft
        val selected = if (field == DateField.Start) draft.start else draft.end
        MonthPickerSheet(
            title = stringResource(
                if (field == DateField.Start) Res.string.projects_start_sheet_title else Res.string.projects_end_sheet_title,
            ),
            selected = selected,
            // The end is most likely near the start; with nothing chosen the picker starts at this year.
            initialYear = selected?.year ?: draft.start?.year ?: today.year,
            minYear = today.year - 2,
            maxYear = today.year + 10,
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
    val required = stringResource(Res.string.projects_error_required)
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
            AppTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                label = stringResource(Res.string.projects_field_title),
                placeholder = stringResource(Res.string.projects_field_title_hint),
                errorText = when {
                    ProjectDraftError.TitleEmpty in errors -> required
                    ProjectDraftError.TitleTooLong in errors ->
                        stringResource(Res.string.projects_error_title_too_long, ProjectLimits.MaxTitleLength)
                    else -> null
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.projects_field_type),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectType.entries.forEach { type ->
                        AppFilterChip(
                            text = type.label(),
                            selected = draft.type == type,
                            onClick = { onTypeSelect(type) },
                        )
                    }
                }
                if (ProjectDraftError.TypeMissing in errors) {
                    Text(
                        text = stringResource(Res.string.projects_error_type),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.error,
                    )
                }
            }
            AppSelectField(
                label = stringResource(Res.string.projects_field_sphere),
                value = draft.sphere?.name,
                placeholder = stringResource(Res.string.projects_field_sphere_hint),
                onClick = onSphereFieldClick,
                errorText = if (ProjectDraftError.SphereMissing in errors) {
                    stringResource(Res.string.projects_error_sphere)
                } else {
                    null
                },
            )
            AppTextField(
                value = draft.problem,
                onValueChange = onProblemChange,
                label = stringResource(Res.string.projects_field_problem),
                placeholder = stringResource(Res.string.projects_field_problem_hint),
                singleLine = false,
                errorText = if (ProjectDraftError.ProblemEmpty in errors) required else null,
            )
            AppTextField(
                value = draft.goal,
                onValueChange = onGoalChange,
                label = stringResource(Res.string.projects_field_goal),
                placeholder = stringResource(Res.string.projects_field_goal_hint),
                singleLine = false,
                errorText = if (ProjectDraftError.GoalEmpty in errors) required else null,
            )
            AppTagsField(
                label = stringResource(Res.string.projects_field_tags),
                tags = draft.tags,
                onAddTag = onAddTag,
                onRemoveTag = onRemoveTag,
                placeholder = stringResource(Res.string.projects_field_tags_hint),
            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppSelectField(
                    label = stringResource(Res.string.projects_field_start),
                    value = draft.start?.let { monthFieldText(it) },
                    placeholder = stringResource(Res.string.projects_field_month_hint),
                    onClick = onStartFieldClick,
                    modifier = Modifier.weight(1f),
                    errorText = if (ProjectDraftError.StartMissing in errors) {
                        stringResource(Res.string.projects_error_month)
                    } else {
                        null
                    },
                )
                AppSelectField(
                    label = stringResource(Res.string.projects_field_end),
                    value = draft.end?.let { monthFieldText(it) },
                    placeholder = stringResource(Res.string.projects_field_month_hint),
                    onClick = onEndFieldClick,
                    modifier = Modifier.weight(1f),
                    errorText = when {
                        ProjectDraftError.EndMissing in errors -> stringResource(Res.string.projects_error_month)
                        ProjectDraftError.EndBeforeStart in errors ->
                            stringResource(Res.string.projects_error_end_before_start)
                        else -> null
                    },
                )
            }
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
                    // API-GAP G40, G43: a server that cannot create projects is not a failure to try again.
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

/** "Окт 2026" | "Oct 2026": a month as the value of a field, which starts with a capital letter. */
@Composable
private fun monthFieldText(value: YearMonth): String = yearMonthText(value).replaceFirstChar { it.uppercase() }
