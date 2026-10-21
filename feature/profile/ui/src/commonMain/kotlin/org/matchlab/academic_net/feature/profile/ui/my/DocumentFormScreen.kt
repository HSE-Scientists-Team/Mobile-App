package org.matchlab.academic_net.feature.profile.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import org.matchlab.academic_net.components.AppConfirmDialog
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppIconButton
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.feature.profile.domain.DocumentDraftError
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.ProfileLimits
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_cancel
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_close
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_delete_confirm
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_delete_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_banner
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_delete
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_delete_text
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_delete_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_edit_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_issuer
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_issuer_hint
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_kind
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_proof
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_title_hint
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_year
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_field_year_hint
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_kind_certificate
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_kind_education
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_new_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_issuer_too_long
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_link
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_proof_missing
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_required
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_title_too_long
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_year
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_file_unreadable
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_item_gone_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_load_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_public_warning
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_resubmit_warning_document
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_retry
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_save
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_save_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_send_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_submit
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_field_link_hint

/**
 * The form of a new education document or certificate ("Новый документ"). The design has no such
 * screen; it follows "11 Добавить работу": the kind, the title, the issuer, the year and the
 * proof, which is required because every document goes to moderation.
 *
 * The proof is a link in a field: there is no file to upload yet, and the API has no private field for it (API-GAP
 * G17), so the link is the public `url` of the record and the form warns that everyone who opens the profile can see it.
 *
 * @param onClose the cross, and the document being added
 */
@Composable
fun AddDocumentRoute(
    dependencies: ProfileUiDependencies,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DocumentFormRoute(dependencies, documentId = null, onClose = onClose, modifier = modifier)
}

/**
 * "11c Редактировать документ": the form of [AddDocumentRoute] on a document of the viewer, with what the server says
 * about it (the state, the comment of the moderator), and the actions: "Сохранить", "Отправить на проверку", "Удалить
 * документ" (after a confirmation). A document that was changed by someone else meanwhile is loaded again with a
 * message; a document that is gone is "Запись удалена".
 *
 * @param documentId the document to edit
 * @param onClose the cross, and the document being saved or deleted
 */
@Composable
fun EditDocumentRoute(
    dependencies: ProfileUiDependencies,
    documentId: DocumentId,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DocumentFormRoute(dependencies, documentId = documentId, onClose = onClose, modifier = modifier)
}

@Composable
private fun DocumentFormRoute(
    dependencies: ProfileUiDependencies,
    documentId: DocumentId?,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val viewModel = viewModel(key = "document-form-${documentId?.value ?: "new"}") {
        DocumentFormViewModel(
            documentId = documentId,
            addDocument = dependencies.addDocument,
            getMyDocument = dependencies.getMyDocument,
            saveDocument = dependencies.saveDocument,
            deletePortfolioItem = dependencies.deletePortfolioItem,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickProofFile = rememberProofFilePicker(
        onChosen = viewModel::onProofFileChosen,
        onFailed = viewModel::onProofFileUnreadable,
    )

    val isDone = (state as? DocumentFormUiState.Content)?.isDone == true
    LaunchedEffect(isDone) {
        if (isDone) onClose()
    }

    DocumentFormScreen(
        state = state,
        isEditing = documentId != null,
        onClose = onClose,
        onKindSelect = viewModel::onKindSelect,
        onTitleChange = viewModel::onTitleChange,
        onIssuerChange = viewModel::onIssuerChange,
        onYearChange = viewModel::onYearChange,
        onProofLinkChange = viewModel::onProofLinkChange,
        onPickProofFile = pickProofFile,
        onRemoveProofFile = viewModel::onProofFileRemoved,
        onSaveClick = viewModel::onSaveClick,
        onSendClick = viewModel::onSendClick,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
internal fun DocumentFormScreen(
    state: DocumentFormUiState,
    isEditing: Boolean,
    onClose: () -> Unit,
    onKindSelect: (DocumentKind) -> Unit,
    onTitleChange: (String) -> Unit,
    onIssuerChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onProofLinkChange: (String) -> Unit,
    onPickProofFile: () -> Unit,
    onRemoveProofFile: () -> Unit,
    onSaveClick: () -> Unit,
    onSendClick: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val content = state as? DocumentFormUiState.Content
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    // The main button and the check mark of the bar do the same: add, or save.
    val mainText = stringResource(if (isEditing) Res.string.profile_save else Res.string.profile_submit)

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow)
            .imePadding(),
    ) {
        AppTopBar(
            title = stringResource(
                if (isEditing) Res.string.profile_document_edit_title else Res.string.profile_document_new_title,
            ),
            onNavigateBack = onClose,
            navigationIcon = AppIcons.X,
            navigationContentDescription = stringResource(Res.string.profile_close),
            actions = {
                if (content != null) {
                    AppIconButton(
                        icon = AppIcons.Check,
                        contentDescription = mainText,
                        onClick = onSaveClick,
                        enabled = !content.isBusy,
                        contentColor = colors.primary,
                    )
                }
            },
        )
        when (state) {
            DocumentFormUiState.Loading -> LoadingState()
            DocumentFormUiState.NotFound -> MessageState(
                title = stringResource(Res.string.profile_item_gone_title),
                actionText = stringResource(Res.string.profile_close),
                onAction = onClose,
            )
            is DocumentFormUiState.Error -> MessageState(
                title = stringResource(Res.string.profile_load_error),
                text = failureText(state.cause),
                actionText = stringResource(Res.string.profile_retry),
                onAction = onRetry,
            )
            is DocumentFormUiState.Content -> DocumentForm(
                state = state,
                mainText = mainText,
                onKindSelect = onKindSelect,
                onTitleChange = onTitleChange,
                onIssuerChange = onIssuerChange,
                onYearChange = onYearChange,
                onProofLinkChange = onProofLinkChange,
                onPickProofFile = onPickProofFile,
                onRemoveProofFile = onRemoveProofFile,
                onSaveClick = onSaveClick,
                onSendClick = onSendClick,
                onDeleteClick = { confirmingDelete = true },
            )
        }
    }

    if (confirmingDelete) {
        AppConfirmDialog(
            title = stringResource(Res.string.profile_document_delete_title),
            text = stringResource(Res.string.profile_document_delete_text),
            confirmText = stringResource(Res.string.profile_delete_confirm),
            dismissText = stringResource(Res.string.profile_cancel),
            onConfirm = {
                confirmingDelete = false
                onDeleteConfirmed()
            },
            onDismiss = { confirmingDelete = false },
            destructive = true,
        )
    }
}

/** The fields of the form of a document with, when it is edited, the banners above them and the actions under them. */
@Composable
private fun ColumnScope.DocumentForm(
    state: DocumentFormUiState.Content,
    mainText: String,
    onKindSelect: (DocumentKind) -> Unit,
    onTitleChange: (String) -> Unit,
    onIssuerChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onProofLinkChange: (String) -> Unit,
    onPickProofFile: () -> Unit,
    onRemoveProofFile: () -> Unit,
    onSaveClick: () -> Unit,
    onSendClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val draft = state.draft
    val errors = state.errors
    val base = state.base
    val required = stringResource(Res.string.profile_error_required)
    val problemText = when (state.problem) {
        FormProblem.SaveFailed -> stringResource(if (state.isEditing) Res.string.profile_save_error else Res.string.profile_send_error)
        FormProblem.SendFailed -> stringResource(Res.string.profile_send_error)
        FormProblem.FileUnreadable -> stringResource(Res.string.profile_file_unreadable)
        FormProblem.DeleteFailed -> stringResource(Res.string.profile_delete_error)
        // The newer version is told by the banner at the top of the form.
        FormProblem.Conflict, null -> null
    }

    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (base != null) {
            PortfolioBanners(
                status = base.status,
                moderationComment = base.moderationComment,
                isConflict = state.problem == FormProblem.Conflict,
            )
        }
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
            enabled = !state.isBusy,
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
            enabled = !state.isBusy,
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
            enabled = !state.isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            errorText = if (DocumentDraftError.YearInvalid in errors) {
                stringResource(Res.string.profile_error_year, ProfileLimits.MinYear, ProfileLimits.MaxYear)
            } else {
                null
            },
        )
        AppTextField(
            value = draft.proof?.url.orEmpty(),
            onValueChange = onProofLinkChange,
            label = stringResource(Res.string.profile_document_field_proof),
            placeholder = stringResource(Res.string.profile_work_field_link_hint),
            enabled = !state.isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            errorText = when {
                DocumentDraftError.ProofMissing in errors -> stringResource(Res.string.profile_error_proof_missing)
                DocumentDraftError.ProofInvalid in errors -> stringResource(Res.string.profile_error_link)
                else -> null
            },
        )
        // API-GAP G17: the link is the public url of the record, and the form says so.
        InfoBanner(stringResource(Res.string.profile_proof_public_warning))
        ProofFileSection(
            chosenName = draft.proofFile?.fileName,
            hasExisting = draft.hasEvidenceFile,
            removed = draft.removeEvidenceFile,
            enabled = !state.isBusy,
            onPick = onPickProofFile,
            onRemove = onRemoveProofFile,
        )
        if (base == null) {
            InfoBanner(stringResource(Res.string.profile_document_banner))
        } else {
            if (state.checksAgain) {
                InfoBanner(stringResource(Res.string.profile_resubmit_warning_document), icon = AppIcons.Clock)
            }
            PortfolioActions(
                canSend = state.canSend,
                isSending = state.running == FormAction.Sending,
                enabled = !state.isBusy,
                onSend = onSendClick,
                deleteText = stringResource(Res.string.profile_document_delete),
                onDelete = onDeleteClick,
            )
        }
    }
    FormBottomBar(
        text = mainText,
        onClick = onSaveClick,
        enabled = !state.isBusy,
        errorText = problemText,
        loading = state.running == FormAction.Saving,
    )
}
