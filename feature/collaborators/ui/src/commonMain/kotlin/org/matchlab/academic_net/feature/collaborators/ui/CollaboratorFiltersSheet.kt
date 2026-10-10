package org.matchlab.academic_net.feature.collaborators.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.components.AppBottomSheet
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppTagsField
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.SheetHeader
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorFilters
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.Res
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_field_keywords
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_field_keywords_hint
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_field_university
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_field_university_hint
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_filter_practical
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_filter_publications
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_filter_scientific
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_filter_verified
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_filter_verified_projects
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_filters_title
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_group_status
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_group_work_type
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_group_works
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_reset
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_show
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_show_count_few
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_show_count_many
import org.matchlab.academic_net.feature.collaborators.ui.generated.resources.collaborators_show_count_one
import org.matchlab.academic_net.utils.RussianPluralForm
import org.matchlab.academic_net.utils.russianPluralForm
import org.matchlab.academic_net.utils.withTag
import org.matchlab.academic_net.utils.withoutTag

/** "08b Фильтры коллабораторов". */
@Composable
internal fun CollaboratorFiltersSheet(
    state: FiltersSheetState,
    onDraftChange: (CollaboratorFilters) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        SheetHeader(stringResource(Res.string.collaborators_filters_title))
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FilterGroup(Res.string.collaborators_group_status) {
                ToggleChip(Res.string.collaborators_filter_verified, draft.verifiedProfile) {
                    onDraftChange(draft.copy(verifiedProfile = it))
                }
            }
            FilterGroup(Res.string.collaborators_group_works) {
                ToggleChip(Res.string.collaborators_filter_verified_projects, draft.hasVerifiedProjects) {
                    onDraftChange(draft.copy(hasVerifiedProjects = it))
                }
                ToggleChip(Res.string.collaborators_filter_publications, draft.hasPublications) {
                    onDraftChange(draft.copy(hasPublications = it))
                }
            }
            FilterGroup(Res.string.collaborators_group_work_type) {
                ToggleChip(Res.string.collaborators_filter_scientific, ProjectType.Scientific in draft.workTypes) {
                    onDraftChange(draft.copy(workTypes = draft.workTypes.toggle(ProjectType.Scientific, it)))
                }
                ToggleChip(Res.string.collaborators_filter_practical, ProjectType.Practical in draft.workTypes) {
                    onDraftChange(draft.copy(workTypes = draft.workTypes.toggle(ProjectType.Practical, it)))
                }
            }
            // "Мой вуз" of the design needs the user's profile; until then the university is typed.
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
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppButton(
                text = stringResource(Res.string.collaborators_reset),
                onClick = onReset,
                modifier = Modifier.weight(1f),
                variant = AppButtonVariant.Outlined,
            )
            AppButton(
                text = showLabel(state.previewCount),
                onClick = onApply,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun showLabel(count: Int?): String {
    if (count == null) return stringResource(Res.string.collaborators_show)
    return stringResource(
        when (russianPluralForm(count)) {
            RussianPluralForm.One -> Res.string.collaborators_show_count_one
            RussianPluralForm.Few -> Res.string.collaborators_show_count_few
            RussianPluralForm.Many -> Res.string.collaborators_show_count_many
        },
        count,
    )
}

@Composable
private fun FilterGroup(title: StringResource, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ToggleChip(label: StringResource, selected: Boolean, onChange: (Boolean) -> Unit) {
    AppFilterChip(text = stringResource(label), selected = selected, onClick = { onChange(!selected) })
}

private fun <T> Set<T>.toggle(item: T, on: Boolean): Set<T> = if (on) this + item else this - item
