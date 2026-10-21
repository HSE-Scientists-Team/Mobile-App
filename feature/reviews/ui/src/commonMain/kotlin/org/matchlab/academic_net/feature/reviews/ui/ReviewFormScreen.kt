package org.matchlab.academic_net.feature.reviews.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppIconButton
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppMultilineField
import org.matchlab.academic_net.components.AppSelectField
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.AvatarSource
import org.matchlab.academic_net.components.FieldLabel
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.InfoBannerTone
import org.matchlab.academic_net.components.PersonCard
import org.matchlab.academic_net.components.PersonCardUiModel
import org.matchlab.academic_net.components.RatingInput
import org.matchlab.academic_net.components.format.yearMonthText
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraftError
import org.matchlab.academic_net.feature.reviews.domain.ReviewLimits
import org.matchlab.academic_net.feature.reviews.domain.ReviewableProject
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.Res
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_already_reviewed
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_close
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_counter
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_error_project
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_error_rating
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_error_text
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_error_text_too_long
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_error_title
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_error_title_too_long
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_not_allowed
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_project_completed
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_project_label
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_project_placeholder
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_rating_label
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_send_failed
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_submit
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_text_hint
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_text_label
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_title
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_title_hint
import org.matchlab.academic_net.feature.reviews.ui.generated.resources.reviews_title_label

/**
 * 16 / 16b-16l "Отзыв о работе": the review of a person for a project the two completed together. The card of the
 * person at the top, then the fields "Проект" (it opens the picker of the common projects, [ReviewProjectSheet]),
 * "Оценка" (the stars), "Заголовок" and "Комментарий" with their counters, and "Отправить отзыв" under them. The fields
 * that are not filled are told after the first tap on the button (16g); the reason a review was not accepted is a banner
 * above the button (16i, 16j, 16k) and the form stays as it is.
 *
 * @param target the person who is reviewed; the card at the top is made of it
 * @param onClose the cross of the app bar
 * @param onSent the review went through: the host closes the form and tells the profile. It is called once per
 * composition of the form after the success; the host's `popBackStackOnce` makes a repeat harmless.
 */
@Composable
fun ReviewFormRoute(
    dependencies: ReviewsUiDependencies,
    target: UserSummary,
    onClose: () -> Unit,
    onSent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = "review-form-${target.id.value}") {
        ReviewFormViewModel(target.id, dependencies.getReviewableProjects, dependencies.sendReview)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isSent) { if (state.isSent) onSent() }
    ReviewFormScreen(
        target = target,
        state = state,
        onClose = onClose,
        onProjectSelected = viewModel::onProjectSelected,
        onRetryProjects = viewModel::onRetryProjects,
        onRatingChange = viewModel::onRatingChange,
        onTitleChange = viewModel::onTitleChange,
        onTextChange = viewModel::onTextChange,
        onSubmit = viewModel::onSubmit,
        modifier = modifier,
    )
}

/** What the fields look like while the review is on its way (16h): the design dims them to 38%. */
private const val BusyAlpha = 0.38f

@Composable
internal fun ReviewFormScreen(
    target: UserSummary,
    state: ReviewFormUiState,
    onClose: () -> Unit,
    onProjectSelected: (ProjectId) -> Unit,
    onRetryProjects: () -> Unit,
    onRatingChange: (Int) -> Unit,
    onTitleChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val draft = state.draft
    val errors = state.errors
    // The picker of the project; it forgets what was marked in it when it is closed.
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val busyAlpha = if (state.isSubmitting) BusyAlpha else 1f
    val submitText = stringResource(Res.string.reviews_submit)

    // The screen has fields: the keyboard lifts the whole screen, so the button stays above it.
    Column(modifier.fillMaxSize().background(colors.surfaceContainerLow).imePadding()) {
        AppTopBar(
            title = stringResource(Res.string.reviews_title),
            onNavigateBack = onClose,
            navigationIcon = AppIcons.X,
            navigationContentDescription = stringResource(Res.string.reviews_close),
            actions = {
                // The check mark of the bar does the same as the button under the fields.
                AppIconButton(
                    icon = AppIcons.Check,
                    contentDescription = submitText,
                    onClick = onSubmit,
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The role of the person in a project is not known before a project is chosen, so the card has no subtitle.
            PersonCard(
                PersonCardUiModel(
                    name = target.name,
                    subtitle = "",
                    avatar = AvatarSource(target.id.value, target.name),
                ),
            )
            val chosen = state.chosenProject
            AppSelectField(
                label = stringResource(Res.string.reviews_project_label),
                value = chosen?.title,
                placeholder = stringResource(Res.string.reviews_project_placeholder),
                onClick = { pickerOpen = true },
                modifier = Modifier.alpha(busyAlpha),
                errorText = if (ReviewDraftError.ProjectMissing in errors) {
                    stringResource(Res.string.reviews_error_project)
                } else {
                    null
                },
                required = true,
                valueCaption = chosen?.let { completedCaption(it) },
                enabled = !state.isSubmitting,
            )
            Column(Modifier.fillMaxWidth().alpha(busyAlpha), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(stringResource(Res.string.reviews_rating_label), required = true)
                // The cells of the stars are 40dp with 4dp of room around each star: the first star lines up with the fields.
                RatingInput(
                    value = draft.rating,
                    onValueChange = onRatingChange,
                    modifier = Modifier.offset(x = (-4).dp),
                    enabled = !state.isSubmitting,
                )
                if (ReviewDraftError.RatingMissing in errors) {
                    Text(
                        text = stringResource(Res.string.reviews_error_rating),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.error,
                    )
                }
            }
            AppTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                label = stringResource(Res.string.reviews_title_label),
                modifier = Modifier.alpha(busyAlpha),
                placeholder = stringResource(Res.string.reviews_title_hint),
                enabled = !state.isSubmitting,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                errorText = when {
                    ReviewDraftError.TitleBlank in errors -> stringResource(Res.string.reviews_error_title)
                    ReviewDraftError.TitleTooLong in errors -> stringResource(Res.string.reviews_error_title_too_long)
                    else -> null
                },
                required = true,
                counterText = counterText(state.titleLength, ReviewLimits.MaxTitle),
                counterIsError = state.isTitleOverLimit,
            )
            AppMultilineField(
                value = draft.text,
                onValueChange = onTextChange,
                label = stringResource(Res.string.reviews_text_label),
                modifier = Modifier.alpha(busyAlpha),
                required = true,
                placeholder = stringResource(Res.string.reviews_text_hint),
                counterText = counterText(state.textLength, ReviewLimits.MaxText),
                errorText = when {
                    ReviewDraftError.TextBlank in errors -> stringResource(Res.string.reviews_error_text)
                    ReviewDraftError.TextTooLong in errors -> stringResource(Res.string.reviews_error_text_too_long)
                    else -> null
                },
                enabled = !state.isSubmitting,
                // 16: the empty box is as low as a one-line field and grows with the text.
                minHeight = 0.dp,
                counterIsError = state.isTextOverLimit,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.failure?.let { failure ->
                InfoBanner(
                    text = stringResource(
                        when (failure) {
                            ReviewSendFailure.AlreadyReviewed -> Res.string.reviews_already_reviewed
                            ReviewSendFailure.NotAllowed -> Res.string.reviews_not_allowed
                            is ReviewSendFailure.Failed -> Res.string.reviews_send_failed
                        },
                    ),
                    tone = InfoBannerTone.Error,
                )
            }
            AppButton(
                text = submitText,
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                loading = state.isSubmitting,
            )
        }
    }

    if (pickerOpen) {
        ReviewProjectSheet(
            projects = state.projects,
            selected = draft.projectId,
            onSelect = { id ->
                pickerOpen = false
                onProjectSelected(id)
            },
            onRetry = onRetryProjects,
            onDismiss = { pickerOpen = false },
        )
    }
}

/** "Завершён · мар 2026": the line under the title of a project of the picker and of the field. */
@Composable
internal fun completedCaption(project: ReviewableProject): String? {
    val completedAt = project.completedAt ?: return null
    val date = completedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return stringResource(Res.string.reviews_project_completed, yearMonthText(YearMonth(date.year, date.month)))
}

/** "38 / 255" and "159 / 10 000": the counter under a field. */
@Composable
private fun counterText(length: Int, limit: Int): String =
    stringResource(Res.string.reviews_counter, groupThousands(length), groupThousands(limit))

/** The digits of [number] in groups of three, apart by a no-break space: 10000 is "10 000" (the design writes it so). */
internal fun groupThousands(number: Int): String {
    val digits = number.toString()
    val head = digits.length % 3
    return buildString {
        digits.forEachIndexed { index, digit ->
            if (index > 0 && (index - head) % 3 == 0) append(NoBreakSpace)
            append(digit)
        }
    }
}

private const val NoBreakSpace = ' '
