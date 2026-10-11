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
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.feature.profile.domain.DocumentDraftError
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.ProfileLimits
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_close
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_banner
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_issuer
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_issuer_hint
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_kind
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_title_hint
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_year
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_year_hint
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_kind_certificate
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_kind_education
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_new_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_issuer_too_long
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_proof_missing
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_required
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_title_too_long
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_year
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_error_link
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_send_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_submit
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_submitting

/**
 * The form of a new education document or certificate ("Новый документ"). The design has no such
 * screen; it follows "11 Добавить работу": the kind, the title, the issuer, the year and the
 * proof, which is required because every document goes to moderation.
 *
 * @param onClose the cross, and the document being added
 */
@Composable
fun AddDocumentRoute(
    dependencies: ProfileUiDependencies,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel { AddDocumentViewModel(dependencies.addDocument) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var proofSheetVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isAdded) {
        if (state.isAdded) onClose()
    }

    AddDocumentScreen(
        state = state,
        onClose = onClose,
        onKindSelect = viewModel::onKindSelect,
        onTitleChange = viewModel::onTitleChange,
        onIssuerChange = viewModel::onIssuerChange,
        onYearChange = viewModel::onYearChange,
        onProofClick = { proofSheetVisible = true },
        onSubmitClick = viewModel::onSubmitClick,
        modifier = modifier,
    )

    if (proofSheetVisible) {
        ProofLinkSheet(
            current = state.draft.proof,
            onAttach = { proof ->
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
internal fun AddDocumentScreen(
    state: AddDocumentUiState,
    onClose: () -> Unit,
    onKindSelect: (DocumentKind) -> Unit,
    onTitleChange: (String) -> Unit,
    onIssuerChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onProofClick: () -> Unit,
    onSubmitClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val draft = state.draft
    val errors = state.errors
    val required = stringResource(Res.string.profile_error_required)

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow)
            .imePadding(),
    ) {
        AppTopBar(
            title = stringResource(Res.string.profile_document_new_title),
            onNavigateBack = onClose,
            navigationIcon = AppIcons.X,
            navigationContentDescription = stringResource(Res.string.profile_close),
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
                    text = stringResource(Res.string.profile_document_field_kind),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocumentKind.entries.forEach { kind ->
                        AppFilterChip(
                            text = stringResource(
                                when (kind) {
                                    DocumentKind.Education -> Res.string.profile_document_kind_education
                                    DocumentKind.Certificate -> Res.string.profile_document_kind_certificate
                                },
                            ),
                            selected = draft.kind == kind,
                            onClick = { onKindSelect(kind) },
                        )
                    }
                }
            }
            AppTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                label = stringResource(Res.string.profile_document_field_title),
                placeholder = stringResource(Res.string.profile_document_field_title_hint),
                errorText = when {
                    DocumentDraftError.TitleEmpty in errors -> required
                    DocumentDraftError.TitleTooLong in errors ->
                        stringResource(Res.string.profile_error_title_too_long, ProfileLimits.MaxTitleLength)
                    else -> null
                },
            )
            AppTextField(
                value = draft.issuer,
                onValueChange = onIssuerChange,
                label = stringResource(Res.string.profile_document_field_issuer),
                placeholder = stringResource(Res.string.profile_document_field_issuer_hint),
                errorText = when {
                    DocumentDraftError.IssuerEmpty in errors -> required
                    DocumentDraftError.IssuerTooLong in errors ->
                        stringResource(Res.string.profile_error_issuer_too_long, ProfileLimits.MaxIssuerLength)
                    else -> null
                },
            )
            AppTextField(
                value = draft.year,
                onValueChange = onYearChange,
                label = stringResource(Res.string.profile_document_field_year),
                placeholder = stringResource(Res.string.profile_document_field_year_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                errorText = if (DocumentDraftError.YearInvalid in errors) {
                    stringResource(Res.string.profile_error_year, ProfileLimits.MinYear, ProfileLimits.MaxYear)
                } else {
                    null
                },
            )
            ProofUploadCard(
                proof = draft.proof,
                errorText = when {
                    DocumentDraftError.ProofMissing in errors -> stringResource(Res.string.profile_error_proof_missing)
                    DocumentDraftError.ProofInvalid in errors -> stringResource(Res.string.profile_proof_error_link)
                    else -> null
                },
                onClick = onProofClick,
            )
            InfoBanner(stringResource(Res.string.profile_document_banner))
        }
        FormBottomBar(
            text = stringResource(
                if (state.isSubmitting) Res.string.profile_submitting else Res.string.profile_submit,
            ),
            onClick = onSubmitClick,
            enabled = !state.isSubmitting,
            errorText = if (state.submitFailed) stringResource(Res.string.profile_send_error) else null,
        )
    }
}
