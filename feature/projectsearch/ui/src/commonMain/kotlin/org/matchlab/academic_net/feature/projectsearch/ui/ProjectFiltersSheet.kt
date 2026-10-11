package org.matchlab.academic_net.feature.projectsearch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppSelectField
import org.matchlab.academic_net.components.AppTagsField
import org.matchlab.academic_net.components.FilterChipGroup
import org.matchlab.academic_net.components.FiltersSheet
import org.matchlab.academic_net.components.project.label
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectDuration
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectFilters
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_apply
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_cancel
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_duration_3_to_6
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_duration_over_6
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_duration_up_to_3
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_field_keywords
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_field_keywords_hint
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_field_sphere
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_filters_title
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_group_duration
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_group_type
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_reset
import org.matchlab.academic_net.feature.projectsearch.ui.generated.resources.projectsearch_sphere_any
import org.matchlab.academic_net.utils.toggled
import org.matchlab.academic_net.utils.withTag
import org.matchlab.academic_net.utils.withoutTag

/**
 * "03 Фильтры (bottom sheet)": type and duration chips, the sphere and keywords. It edits the
 * draft only; the applied filters change on "Применить". The sphere choice is a second sheet the
 * screen opens on [onSphereFieldClick].
 */
@Composable
internal fun ProjectFiltersSheet(
    state: ProjectFiltersSheetState,
    onDraftChange: (ProjectFilters) -> Unit,
    onSphereFieldClick: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    FiltersSheet(
        title = stringResource(Res.string.projectsearch_filters_title),
        onDismiss = onCancel,
        secondaryText = stringResource(Res.string.projectsearch_cancel),
        onSecondary = onCancel,
        primaryText = stringResource(Res.string.projectsearch_apply),
        onPrimary = onApply,
        modifier = modifier,
        headerActionText = stringResource(Res.string.projectsearch_reset),
        onHeaderAction = onReset,
    ) {
        FilterChipGroup(stringResource(Res.string.projectsearch_group_type)) {
            ProjectType.entries.forEach { type ->
                val selected = type in draft.types
                AppFilterChip(
                    text = type.label(),
                    selected = selected,
                    onClick = { onDraftChange(draft.copy(types = draft.types.toggled(type, !selected))) },
                )
            }
        }
        AppSelectField(
            label = stringResource(Res.string.projectsearch_field_sphere),
            value = draft.sphere?.name,
            placeholder = stringResource(Res.string.projectsearch_sphere_any),
            onClick = onSphereFieldClick,
        )
        FilterChipGroup(stringResource(Res.string.projectsearch_group_duration)) {
            ProjectDuration.entries.forEach { duration ->
                val selected = duration in draft.durations
                AppFilterChip(
                    text = duration.label(),
                    selected = selected,
                    onClick = { onDraftChange(draft.copy(durations = draft.durations.toggled(duration, !selected))) },
                )
            }
        }
        AppTagsField(
            label = stringResource(Res.string.projectsearch_field_keywords),
            tags = draft.keywords,
            onAddTag = { onDraftChange(draft.copy(keywords = draft.keywords.withTag(it))) },
            onRemoveTag = { onDraftChange(draft.copy(keywords = draft.keywords.withoutTag(it))) },
            placeholder = stringResource(Res.string.projectsearch_field_keywords_hint),
        )
    }
}

/** "до 3 мес", "3–6 мес", "6+ мес" | "Up to 3 mo", "3–6 mo", "6+ mo". */
@Composable
internal fun ProjectDuration.label(): String = stringResource(
    when (this) {
        ProjectDuration.UpTo3Months -> Res.string.projectsearch_duration_up_to_3
        ProjectDuration.From3To6Months -> Res.string.projectsearch_duration_3_to_6
        ProjectDuration.Over6Months -> Res.string.projectsearch_duration_over_6
    },
)
