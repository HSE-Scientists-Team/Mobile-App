package org.matchlab.academic_net.feature.projects.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppSelectField
import org.matchlab.academic_net.components.AppTagsField
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.format.yearMonthText
import org.matchlab.academic_net.components.project.label
import org.matchlab.academic_net.feature.projects.domain.ProjectDraftError
import org.matchlab.academic_net.feature.projects.domain.ProjectLimits
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_end_sheet_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_error_end_before_start
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_error_month
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_error_required
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
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_start_sheet_title

// The fields of the description of a project, shared by "05 Создание проекта" and "04d Редактирование": the same
// labels, hints, checks and errors. Each function is one child of the form's column; the roles are different in the two
// screens (a draft list there, the roles of the server here) and are drawn by them.

/** The title of the project; [errors] are the form's, the field shows the ones about the title. */
@Composable
internal fun ProjectTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    errors: Set<ProjectDraftError>,
    modifier: Modifier = Modifier,
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = stringResource(Res.string.projects_field_title),
        placeholder = stringResource(Res.string.projects_field_title_hint),
        errorText = when {
            ProjectDraftError.TitleEmpty in errors -> stringResource(Res.string.projects_error_required)
            ProjectDraftError.TitleTooLong in errors ->
                stringResource(Res.string.projects_error_title_too_long, ProjectLimits.MaxTitleLength)
            else -> null
        },
    )
}

/** "Тип проекта": one chip for each [ProjectType]; tapping the chosen one again keeps it. */
@Composable
internal fun ProjectTypeChips(
    selected: ProjectType?,
    onSelect: (ProjectType) -> Unit,
    errors: Set<ProjectDraftError>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.projects_field_type),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProjectType.entries.forEach { type ->
                AppFilterChip(text = type.label(), selected = selected == type, onClick = { onSelect(type) })
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
}

/** "Сфера": a field that opens the picker; empty while nothing is chosen (or the project's sphere is unknown). */
@Composable
internal fun ProjectSphereField(
    sphere: Sphere?,
    onClick: () -> Unit,
    errors: Set<ProjectDraftError>,
    modifier: Modifier = Modifier,
) {
    AppSelectField(
        label = stringResource(Res.string.projects_field_sphere),
        value = sphere?.name,
        placeholder = stringResource(Res.string.projects_field_sphere_hint),
        onClick = onClick,
        modifier = modifier,
        errorText = if (ProjectDraftError.SphereMissing in errors) {
            stringResource(Res.string.projects_error_sphere)
        } else {
            null
        },
    )
}

/** "Проблема": a text of several lines. */
@Composable
internal fun ProjectProblemField(
    value: String,
    onValueChange: (String) -> Unit,
    errors: Set<ProjectDraftError>,
    modifier: Modifier = Modifier,
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = stringResource(Res.string.projects_field_problem),
        placeholder = stringResource(Res.string.projects_field_problem_hint),
        singleLine = false,
        errorText = if (ProjectDraftError.ProblemEmpty in errors) {
            stringResource(Res.string.projects_error_required)
        } else {
            null
        },
    )
}

/** "Цель": a text of several lines. */
@Composable
internal fun ProjectGoalField(
    value: String,
    onValueChange: (String) -> Unit,
    errors: Set<ProjectDraftError>,
    modifier: Modifier = Modifier,
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = stringResource(Res.string.projects_field_goal),
        placeholder = stringResource(Res.string.projects_field_goal_hint),
        singleLine = false,
        errorText = if (ProjectDraftError.GoalEmpty in errors) stringResource(Res.string.projects_error_required) else null,
    )
}

/** "Теги": chips of the entered tags and the line to type a new one. */
@Composable
internal fun ProjectTagsFormField(
    tags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTagsField(
        label = stringResource(Res.string.projects_field_tags),
        tags = tags,
        onAddTag = onAddTag,
        onRemoveTag = onRemoveTag,
        modifier = modifier,
        placeholder = stringResource(Res.string.projects_field_tags_hint),
    )
}

/** "Начало" and "Завершение": two fields side by side that open the month picker. */
@Composable
internal fun ProjectMonthFields(
    start: YearMonth?,
    end: YearMonth?,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    errors: Set<ProjectDraftError>,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppSelectField(
            label = stringResource(Res.string.projects_field_start),
            value = start?.let { monthFieldText(it) },
            placeholder = stringResource(Res.string.projects_field_month_hint),
            onClick = onStartClick,
            modifier = Modifier.weight(1f),
            errorText = if (ProjectDraftError.StartMissing in errors) {
                stringResource(Res.string.projects_error_month)
            } else {
                null
            },
        )
        AppSelectField(
            label = stringResource(Res.string.projects_field_end),
            value = end?.let { monthFieldText(it) },
            placeholder = stringResource(Res.string.projects_field_month_hint),
            onClick = onEndClick,
            modifier = Modifier.weight(1f),
            errorText = when {
                ProjectDraftError.EndMissing in errors -> stringResource(Res.string.projects_error_month)
                ProjectDraftError.EndBeforeStart in errors -> stringResource(Res.string.projects_error_end_before_start)
                else -> null
            },
        )
    }
}

/**
 * The month picker of the field [field]: the end is most likely near the start, so with nothing chosen the picker
 * starts at the year of the start and, failing that, at this year.
 */
@Composable
internal fun ProjectMonthSheet(
    field: DateField,
    start: YearMonth?,
    end: YearMonth?,
    onSelect: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val selected = if (field == DateField.Start) start else end
    MonthPickerSheet(
        title = stringResource(
            if (field == DateField.Start) Res.string.projects_start_sheet_title else Res.string.projects_end_sheet_title,
        ),
        selected = selected,
        initialYear = selected?.year ?: start?.year ?: today.year,
        minYear = today.year - 2,
        maxYear = today.year + 10,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

/** "Окт 2026" | "Oct 2026": a month as the value of a field, which starts with a capital letter. */
@Composable
private fun monthFieldText(value: YearMonth): String = yearMonthText(value).replaceFirstChar { it.uppercase() }
