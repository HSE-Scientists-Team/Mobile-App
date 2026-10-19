package org.matchlab.academic_net.feature.complaints.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppMultilineField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.InfoBannerTone
import org.matchlab.academic_net.components.PersonCard
import org.matchlab.academic_net.components.PersonCardUiModel
import org.matchlab.academic_net.components.RadioListItem
import org.matchlab.academic_net.components.person.nameOrPlaceholder
import org.matchlab.academic_net.components.person.subtitleText
import org.matchlab.academic_net.feature.complaints.domain.ComplaintDescriptionMaxLength
import org.matchlab.academic_net.feature.complaints.domain.ComplaintDraftError
import org.matchlab.academic_net.feature.complaints.domain.ComplaintReason
import org.matchlab.academic_net.feature.complaints.domain.ComplaintSubject
import org.matchlab.academic_net.feature.complaints.domain.ComplaintTarget
import org.matchlab.academic_net.feature.complaints.domain.ProjectComplaintReason
import org.matchlab.academic_net.feature.complaints.domain.UserComplaintReason
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.Res
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_description_hint
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_description_label
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_done
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_error_description
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_error_reason
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_error_too_long
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_failed
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_not_allowed
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_reason_fake_account
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_reason_false_information
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_reason_insults
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_reason_other
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_reason_portfolio_fraud
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_reason_scam
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_reason_spam
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_reason_title
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_sent_text
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_sent_title
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_submit
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_title_project
import org.matchlab.academic_net.feature.complaints.ui.generated.resources.complaints_title_user

/**
 * 17 / 17b–17g "Пожаловаться": the complaint about a person or a project. The card of the target at the top (when it
 * could be read), the reasons as a radio list, "Что произошло" and "Отправить жалобу". The fields that are not filled
 * are told after the tap (17c), a 403 is "Подтвердите почту, чтобы отправлять жалобы" (17f), any other failure is "Не
 * удалось отправить жалобу. Попробуйте ещё раз" (17g). A sent complaint is 17e, with "Готово". Attachments and the
 * block switch of the design are not here (API-GAP G68; blocking is a later sprint).
 *
 * @param onNavigateBack the back arrow of the app bar
 * @param onDone "Готово" of 17e
 */
@Composable
fun ComplaintRoute(
    dependencies: ComplaintsUiDependencies,
    target: ComplaintTarget,
    onNavigateBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = "complaint-$target") {
        ComplaintViewModel(target, dependencies.getComplaintSubject, dependencies.sendComplaint)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ComplaintScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onReasonSelected = viewModel::onReasonSelected,
        onDescriptionChange = viewModel::onDescriptionChange,
        onSubmit = viewModel::onSubmit,
        onDone = onDone,
        modifier = modifier,
    )
}

@Composable
internal fun ComplaintScreen(
    state: ComplaintUiState,
    onNavigateBack: () -> Unit,
    onReasonSelected: (ComplaintReason) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // The screen has a field: the keyboard lifts the whole screen, so the button stays above it.
    Column(modifier.fillMaxSize().background(colors.surfaceContainerLow).imePadding()) {
        AppTopBar(
            title = stringResource(
                if (state.target is ComplaintTarget.Project) Res.string.complaints_title_project else Res.string.complaints_title_user,
            ),
            onNavigateBack = onNavigateBack,
        )
        if (state.isSent) {
            SentContent(onDone)
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.subject?.let { SubjectCard(it) }
                ReasonList(state, onReasonSelected)
                AppMultilineField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    label = stringResource(Res.string.complaints_description_label),
                    required = true,
                    placeholder = stringResource(Res.string.complaints_description_hint),
                    errorText = when {
                        ComplaintDraftError.DescriptionMissing in state.errors ->
                            stringResource(Res.string.complaints_error_description)
                        ComplaintDraftError.DescriptionTooLong in state.errors ->
                            stringResource(Res.string.complaints_error_too_long, ComplaintDescriptionMaxLength)
                        else -> null
                    },
                    enabled = !state.isSubmitting,
                    maxLines = 8,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.failure?.let { failure ->
                    InfoBanner(
                        text = stringResource(
                            when (failure) {
                                ComplaintFailure.NotAllowed -> Res.string.complaints_not_allowed
                                is ComplaintFailure.Failed -> Res.string.complaints_failed
                            },
                        ),
                        tone = InfoBannerTone.Error,
                    )
                }
                AppButton(
                    text = stringResource(Res.string.complaints_submit),
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    loading = state.isSubmitting,
                )
            }
        }
    }
}

/** The card at the top: a person (avatar, name, study) or a project (title, initiator and university). */
@Composable
private fun SubjectCard(subject: ComplaintSubject) {
    when (subject) {
        is ComplaintSubject.PersonSubject -> PersonCard(
            PersonCardUiModel(
                name = nameOrPlaceholder(subject.person.user.name),
                subtitle = subject.person.subtitleText(),
                avatar = AppAvatars.forKey(subject.person.user.id.value),
            ),
        )
        is ComplaintSubject.ProjectSubject -> AppCard(spacing = 8.dp) {
            Text(subject.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            val author = listOf(subject.initiatorName, subject.initiatorUniversity).filter { it.isNotBlank() }
            if (author.isNotEmpty()) {
                Text(
                    text = author.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Причина жалобы *": the reasons as a radio list; 17c puts "Выберите причину" under it. */
@Composable
private fun ReasonList(state: ComplaintUiState, onReasonSelected: (ComplaintReason) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(
                text = stringResource(Res.string.complaints_reason_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
            )
            Text(text = " *", style = MaterialTheme.typography.titleLarge, color = colors.error)
        }
        state.reasons.forEach { reason ->
            RadioListItem(
                text = stringResource(reason.label()),
                selected = reason == state.reason,
                onClick = { onReasonSelected(reason) },
            )
        }
        if (ComplaintDraftError.ReasonMissing in state.errors) {
            Text(
                text = stringResource(Res.string.complaints_error_reason),
                style = MaterialTheme.typography.labelSmall,
                color = colors.error,
            )
        }
    }
}

/** 17e: the complaint is sent; "Готово" leaves the screen. */
@Composable
private fun SentContent(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(Res.string.complaints_sent_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.complaints_sent_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        AppButton(
            text = stringResource(Res.string.complaints_done),
            onClick = onDone,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** The words of a reason in the app language. */
private fun ComplaintReason.label(): StringResource = when (this) {
    is UserComplaintReason -> when (this) {
        UserComplaintReason.Spam -> Res.string.complaints_reason_spam
        UserComplaintReason.Insults -> Res.string.complaints_reason_insults
        UserComplaintReason.PortfolioFraud -> Res.string.complaints_reason_portfolio_fraud
        UserComplaintReason.FakeAccount -> Res.string.complaints_reason_fake_account
        UserComplaintReason.Scam -> Res.string.complaints_reason_scam
        UserComplaintReason.Other -> Res.string.complaints_reason_other
    }
    is ProjectComplaintReason -> when (this) {
        ProjectComplaintReason.Spam -> Res.string.complaints_reason_spam
        ProjectComplaintReason.FalseInformation -> Res.string.complaints_reason_false_information
        ProjectComplaintReason.Scam -> Res.string.complaints_reason_scam
        ProjectComplaintReason.Other -> Res.string.complaints_reason_other
    }
}
