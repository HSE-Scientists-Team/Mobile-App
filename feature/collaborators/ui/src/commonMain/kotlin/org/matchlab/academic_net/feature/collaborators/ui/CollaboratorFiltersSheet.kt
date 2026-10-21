package org.matchlab.academic_net.feature.collaborators.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppTagsField
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.FilterChipGroup
import org.matchlab.academic_net.components.FiltersSheet
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorFilters
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.Res
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_field_keywords
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_field_keywords_hint
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_field_university
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_field_university_hint
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_filter_practical
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_filter_publications
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_filter_scientific
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_filter_verified_projects
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_filters_title
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_group_work_type
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_group_works
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_reset
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_show
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_show_count
import org.matchlab.academic_net.utils.toggled
import org.matchlab.academic_net.utils.withTag
import org.matchlab.academic_net.utils.withoutTag

/**
 * "08b Фильтры коллабораторов": the draft of the filters, "Сбросить" in the header and "Показать N" below.
 *
 * There is no group "Статус" ("Открыт к сотрудничеству", "Верифицированный профиль"): the design of the app dropped it
 * (2026-10-20). "Есть верифицированные проекты" is a filter of the search (the server looks at its own data); the people
 * in the results carry no verification mark.
 *
 * @param showWorkTypes whether the group "Тип работы" is offered (only if the source can filter by the type of a
 * person's works, see `CollaboratorsCapabilities.projectTypeFilter` and `docs/api-values.md`); when it is not, the
 * group is not there at all and there is no line that explains why
 */
@Composable
internal fun CollaboratorFiltersSheet(
    state: FiltersSheetState,
    showWorkTypes: Boolean,
    onDraftChange: (CollaboratorFilters) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    FiltersSheet(
        title = stringResource(Res.string.collaborators_filters_title),
        onDismiss = onDismiss,
        secondaryText = stringResource(Res.string.collaborators_reset),
        onSecondary = onReset,
        primaryText = showLabel(state.previewCount),
        onPrimary = onApply,
        modifier = modifier,
    ) {
        FilterChipGroup(stringResource(Res.string.collaborators_group_works)) {
            ToggleChip(Res.string.collaborators_filter_verified_projects, draft.hasVerifiedProjects) {
                onDraftChange(draft.copy(hasVerifiedProjects = it))
            }
            ToggleChip(Res.string.collaborators_filter_publications, draft.hasPublications) {
                onDraftChange(draft.copy(hasPublications = it))
            }
        }
        // A source that cannot send `project_type` hides the group.
        if (showWorkTypes) {
            FilterChipGroup(stringResource(Res.string.collaborators_group_work_type)) {
                ToggleChip(Res.string.collaborators_filter_scientific, ProjectType.Scientific in draft.workTypes) {
                    onDraftChange(draft.copy(workTypes = draft.workTypes.toggled(ProjectType.Scientific, it)))
                }
                ToggleChip(Res.string.collaborators_filter_practical, ProjectType.Practical in draft.workTypes) {
                    onDraftChange(draft.copy(workTypes = draft.workTypes.toggled(ProjectType.Practical, it)))
                }
            }
        }
        // "Мой вуз" of the design is not made: the university is typed (the server decides how it is matched).
        AppTextField(
            value = draft.university,
            onValueChange = { onDraftChange(draft.copy(university = it)) },
            label = stringResource(Res.string.collaborators_field_university),
            placeholder = stringResource(Res.string.collaborators_field_university_hint),
        )
        AppTagsField(
            label = stringResource(Res.string.collaborators_field_keywords),
            tags = draft.keywords,
            onAddTag = { onDraftChange(draft.copy(keywords = draft.keywords.withTag(it))) },
            onRemoveTag = { onDraftChange(draft.copy(keywords = draft.keywords.withoutTag(it))) },
            placeholder = stringResource(Res.string.collaborators_field_keywords_hint),
        )
    }
}

@Composable
private fun showLabel(count: Int?): String {
    if (count == null) return stringResource(Res.string.collaborators_show)
    return pluralStringResource(Res.plurals.collaborators_show_count, count, count)
}

@Composable
private fun ToggleChip(label: StringResource, selected: Boolean, onChange: (Boolean) -> Unit) {
    AppFilterChip(text = stringResource(label), selected = selected, onClick = { onChange(!selected) })
}
