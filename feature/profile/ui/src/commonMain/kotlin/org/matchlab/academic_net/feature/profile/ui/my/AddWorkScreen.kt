package org.matchlab.academic_net.feature.profile.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppIconButton
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.feature.profile.domain.ProfileLimits
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.WorkDraftError
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_close
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_description_too_long
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_link
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_required
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_title_too_long
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_error_link
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_send_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_submit
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_submit_unverified
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_submitting
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_banner
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_field_description
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_field_description_hint
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_field_link
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_field_link_hint
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_field_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_field_title_hint
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_field_type
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_new_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_type_practical
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_type_scientific

/**
 * "11 Добавить работу в портфолио": the type, the title, the description, the link and the proof
 * of a work. With a link or a proof the work goes to moderation ("Отправить на проверку"),
 * otherwise it is added as unconfirmed ("Добавить без подтверждения"); the button says which.
 *
 * @param onClose the cross, and the work being added
 */
@Composable
fun AddWorkRoute(
    dependencies: ProfileUiDependencies,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel { AddWorkViewModel(dependencies.addWork) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var proofSheetVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isAdded) {
        if (state.isAdded) onClose()
    }

    AddWorkScreen(
        state = state,
        onClose = onClose,
        onTypeSelect = viewModel::onTypeSelect,
        onTitleChange = viewModel::onTitleChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onLinkChange = viewModel::onLinkChange,
        onProofClick = { proofSheetVisible = true },
        onSubmitClick = viewModel::onSubmitClick,
        modifier = modifier,
    )

    if (proofSheetVisible) {
        ProofLinkSheet(
            current = state.draft.proof,
            onAttach = { proof: ProofAttachment ->
                viewModel.onProofChange(proof)
                proofSheetVisible = false
            },
            onRemove = {
                viewModel.onProofChange(null)
                proofSheetVisible = false
            },
            onDismiss = { proofSheetVisible = false },
        )
    }
}

@Composable
internal fun AddWorkScreen(
    state: AddWorkUiState,
    onClose: () -> Unit,
    onTypeSelect: (ProjectType) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLinkChange: (String) -> Unit,
    onProofClick: () -> Unit,
    onSubmitClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val draft = state.draft
    val errors = state.errors
    val required = stringResource(Res.string.profile_error_required)
    val submitText = stringResource(
        when {
            state.isSubmitting -> Res.string.profile_submitting
            draft.sendsForVerification -> Res.string.profile_submit
            else -> Res.string.profile_submit_unverified
        },
    )

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow)
            .imePadding(),
    ) {
        AppTopBar(
            title = stringResource(Res.string.profile_work_new_title),
            onNavigateBack = onClose,
            navigationIcon = AppIcons.X,
            navigationContentDescription = stringResource(Res.string.profile_close),
            actions = {
                AppIconButton(
                    icon = AppIcons.Check,
                    contentDescription = submitText,
                    onClick = onSubmitClick,
                    enabled = !state.isSubmitting,
                    contentColor = colors.primary,
                )
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.profile_work_field_type),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectType.entries.forEach { type ->
                        AppFilterChip(
                            text = stringResource(
                                when (type) {
                                    ProjectType.Scientific -> Res.string.profile_work_type_scientific
                                    ProjectType.Practical -> Res.string.profile_work_type_practical
                                },
                            ),
                            selected = draft.type == type,
                            onClick = { onTypeSelect(type) },
                        )
                    }
                }
            }
            AppTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                label = stringResource(Res.string.profile_work_field_title),
                placeholder = stringResource(Res.string.profile_work_field_title_hint),
                errorText = when {
                    WorkDraftError.TitleEmpty in errors -> required
                    WorkDraftError.TitleTooLong in errors ->
                        stringResource(Res.string.profile_error_title_too_long, ProfileLimits.MaxTitleLength)
                    else -> null
                },
            )
            AppTextField(
                value = draft.description,
                onValueChange = onDescriptionChange,
                label = stringResource(Res.string.profile_work_field_description),
                placeholder = stringResource(Res.string.profile_work_field_description_hint),
                singleLine = false,
                errorText = when {
                    WorkDraftError.DescriptionEmpty in errors -> required
                    WorkDraftError.DescriptionTooLong in errors ->
                        stringResource(Res.string.profile_error_description_too_long, ProfileLimits.MaxDescriptionLength)
                    else -> null
                },
            )
            AppTextField(
                value = draft.link,
                onValueChange = onLinkChange,
                label = stringResource(Res.string.profile_work_field_link),
                placeholder = stringResource(Res.string.profile_work_field_link_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                errorText = if (WorkDraftError.LinkInvalid in errors) {
                    stringResource(Res.string.profile_error_link)
                } else {
                    null
                },
            )
            ProofUploadCard(
                proof = draft.proof,
                errorText = if (WorkDraftError.ProofInvalid in errors) {
                    stringResource(Res.string.profile_proof_error_link)
                } else {
                    null
                },
                onClick = onProofClick,
            )
            InfoBanner(stringResource(Res.string.profile_work_banner))
        }
        FormBottomBar(
            text = submitText,
            onClick = onSubmitClick,
            enabled = !state.isSubmitting,
            errorText = if (state.submitFailed) stringResource(Res.string.profile_send_error) else null,
        )
    }
}
