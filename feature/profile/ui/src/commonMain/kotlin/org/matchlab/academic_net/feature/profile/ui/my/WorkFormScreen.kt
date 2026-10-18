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
import org.matchlab.academic_net.common.model.ProjectType
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
import org.matchlab.academic_net.feature.profile.domain.ProfileLimits
import org.matchlab.academic_net.feature.profile.domain.WorkDraftError
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_cancel
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_close
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_delete_confirm
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_delete_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_description_too_long
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_link
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_required
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_title_too_long
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_item_gone_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_load_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_resubmit_warning_work
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_retry
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_save
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_save_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_send_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_submit
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_submit_unverified
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_banner
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_delete
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_delete_text
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_delete_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_edit_title
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
import org.matchlab.academic_net.feature.profile.ui.rememberLinkOpener

/**
 * "11 Добавить работу в портфолио": the type, the title, the description and the link of a work. With a link the work
 * goes to moderation ("Отправить на проверку"), otherwise it is added as unconfirmed ("Добавить без подтверждения");
 * the button says which. There is no file to upload yet: the proof of a work is its link.
 *
 * @param onClose the cross, and the work being added
 */
@Composable
fun AddWorkRoute(
    dependencies: ProfileUiDependencies,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkFormRoute(dependencies, workId = null, onClose = onClose, modifier = modifier)
}

/**
 * "11b Редактировать работу": the form of [AddWorkRoute] on a work of the viewer, with what the server says about it
 * (the state, the comment of the moderator), and the actions: "Сохранить", "Отправить на проверку", "Открыть ссылку",
 * "Удалить работу" (after a confirmation). A work that was changed by someone else meanwhile is loaded again with a
 * message; a work that is gone is "Запись удалена".
 *
 * @param workId the work to edit
 * @param onClose the cross, and the work being saved or deleted
 */
@Composable
fun EditWorkRoute(
    dependencies: ProfileUiDependencies,
    workId: WorkId,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkFormRoute(dependencies, workId = workId, onClose = onClose, modifier = modifier)
}

@Composable
private fun WorkFormRoute(
    dependencies: ProfileUiDependencies,
    workId: WorkId?,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val viewModel = viewModel(key = "work-form-${workId?.value ?: "new"}") {
        WorkFormViewModel(
            workId = workId,
            addWork = dependencies.addWork,
            getMyWork = dependencies.getMyWork,
            saveWork = dependencies.saveWork,
            deletePortfolioItem = dependencies.deletePortfolioItem,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openLink = rememberLinkOpener()

    val isDone = (state as? WorkFormUiState.Content)?.isDone == true
    LaunchedEffect(isDone) {
        if (isDone) onClose()
    }

    WorkFormScreen(
        state = state,
        isEditing = workId != null,
        onClose = onClose,
        onTypeSelect = viewModel::onTypeSelect,
        onTitleChange = viewModel::onTitleChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onLinkChange = viewModel::onLinkChange,
        onSaveClick = viewModel::onSaveClick,
        onSendClick = viewModel::onSendClick,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onOpenLink = openLink,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
internal fun WorkFormScreen(
    state: WorkFormUiState,
    isEditing: Boolean,
    onClose: () -> Unit,
    onTypeSelect: (ProjectType) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLinkChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onSendClick: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onOpenLink: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val content = state as? WorkFormUiState.Content
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    // The main button and the check mark of the bar do the same: add, or save.
    val mainText = stringResource(
        when {
            isEditing -> Res.string.profile_save
            content?.draft?.sendsForVerification == true -> Res.string.profile_submit
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
            title = stringResource(if (isEditing) Res.string.profile_work_edit_title else Res.string.profile_work_new_title),
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
            WorkFormUiState.Loading -> LoadingState()
            WorkFormUiState.NotFound -> MessageState(
                title = stringResource(Res.string.profile_item_gone_title),
                actionText = stringResource(Res.string.profile_close),
                onAction = onClose,
            )
            is WorkFormUiState.Error -> MessageState(
                title = stringResource(Res.string.profile_load_error),
                text = failureText(state.cause),
                actionText = stringResource(Res.string.profile_retry),
                onAction = onRetry,
            )
            is WorkFormUiState.Content -> WorkForm(
                state = state,
                mainText = mainText,
                onTypeSelect = onTypeSelect,
                onTitleChange = onTitleChange,
                onDescriptionChange = onDescriptionChange,
                onLinkChange = onLinkChange,
                onSaveClick = onSaveClick,
                onSendClick = onSendClick,
                onDeleteClick = { confirmingDelete = true },
                onOpenLink = onOpenLink,
            )
        }
    }

    if (confirmingDelete) {
        AppConfirmDialog(
            title = stringResource(Res.string.profile_work_delete_title),
            text = stringResource(Res.string.profile_work_delete_text),
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

/** The fields of the form of a work with, when it is edited, the banners above them and the actions under them. */
@Composable
private fun ColumnScope.WorkForm(
    state: WorkFormUiState.Content,
    mainText: String,
    onTypeSelect: (ProjectType) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLinkChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onSendClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val draft = state.draft
    val errors = state.errors
    val base = state.base
    val required = stringResource(Res.string.profile_error_required)
    val problemText = when (state.problem) {
        FormProblem.SaveFailed -> stringResource(if (state.isEditing) Res.string.profile_save_error else Res.string.profile_send_error)
        FormProblem.SendFailed -> stringResource(Res.string.profile_send_error)
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
            enabled = !state.isBusy,
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
            enabled = !state.isBusy,
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
            enabled = !state.isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            errorText = if (WorkDraftError.LinkInvalid in errors) stringResource(Res.string.profile_error_link) else null,
        )
        if (draft.hasEvidenceFile) EvidenceAttachedCard()
        if (base == null) {
            InfoBanner(stringResource(Res.string.profile_work_banner))
        } else {
            if (state.checksAgain) {
                InfoBanner(stringResource(Res.string.profile_resubmit_warning_work), icon = AppIcons.Clock)
            }
            PortfolioActions(
                canSend = state.canSend,
                isSending = state.running == FormAction.Sending,
                enabled = !state.isBusy,
                onSend = onSendClick,
                deleteText = stringResource(Res.string.profile_work_delete),
                onDelete = onDeleteClick,
                onOpenLink = base.link?.let { link -> { onOpenLink(link) } },
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
