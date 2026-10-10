package org.matchlab.academic_net.feature.projects.ui.role

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppBottomSheet
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppTagsField
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.SheetHeader
import org.matchlab.academic_net.feature.projects.domain.ProjectLimits
import org.matchlab.academic_net.feature.projects.domain.RoleDraftError
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_cancel
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_error_required
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_role_edit
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_role_error_too_long
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_role_new
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_role_save
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_role_skills
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_role_skills_hint
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_role_title
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_role_title_hint

/**
 * Bottom sheet to name a role and list its skills. The design has no such screen, so it is put
 * together from the filter sheet's parts (sheet header, fields, cancel / apply buttons).
 */
@Composable
fun RoleEditorSheet(
    state: RoleEditorState,
    onTitleChange: (String) -> Unit,
    onAddSkill: (String) -> Unit,
    onRemoveSkill: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
) {
    val titleError = when {
        RoleDraftError.TitleEmpty in state.errors -> stringResource(Res.string.projects_error_required)
        RoleDraftError.TitleTooLong in state.errors ->
            stringResource(Res.string.projects_role_error_too_long, ProjectLimits.MaxRoleTitleLength)
        else -> null
    }
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        SheetHeader(
            title = stringResource(
                if (state.editedIndex == null) Res.string.projects_role_new else Res.string.projects_role_edit,
            ),
        )
        Column(
            Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppTextField(
                value = state.draft.title,
                onValueChange = onTitleChange,
                label = stringResource(Res.string.projects_role_title),
                placeholder = stringResource(Res.string.projects_role_title_hint),
                errorText = titleError,
            )
            AppTagsField(
                label = stringResource(Res.string.projects_role_skills),
                tags = state.draft.skills,
                onAddTag = onAddSkill,
                onRemoveTag = onRemoveSkill,
                placeholder = stringResource(Res.string.projects_role_skills_hint),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(
                    text = stringResource(Res.string.projects_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variant = AppButtonVariant.Outlined,
                )
                AppButton(
                    text = stringResource(Res.string.projects_role_save),
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving,
                )
            }
        }
    }
}
