package org.matchlab.academic_net.feature.profile.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppIcon
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.PersonCard
import org.matchlab.academic_net.components.PersonCardUiModel
import org.matchlab.academic_net.components.RatingStars
import org.matchlab.academic_net.components.RatingStarsSize
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.components.format.yearMonthText
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.profile.ui.authorLine
import org.matchlab.academic_net.feature.profile.ui.canMessageAuthor
import org.matchlab.academic_net.feature.profile.ui.canOpenAuthor
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_load_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_message_author
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_retry
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_review_not_found_text
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_review_not_found_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_review_project
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_review_project_line
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_review_title

/**
 * "09d Отзыв — подробно": one review about [userId] with its whole text and the month its project ended. The "⋮" menu of
 * the design (report) belongs to a later story and is not shown.
 *
 * @param reviewId the review; the API cannot give one review by itself (API-GAP G14), so the repository looks it up in
 * the reviews of [userId]
 * @param onAuthorClick the author card, which leads to the author's profile; the card is not a button when the server
 * gave the review no id of the author (API-GAP G55)
 * @param onAuthorChatClick the chat icon of the author card; not shown for a review the viewer wrote, nor for an author
 * with no id (API-GAP G55)
 */
@Composable
fun ReviewDetailsRoute(
    dependencies: ProfileUiDependencies,
    userId: UserId,
    reviewId: ReviewId,
    onNavigateBack: () -> Unit,
    onAuthorClick: (UserId) -> Unit,
    onAuthorChatClick: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = "review-${userId.value}-${reviewId.value}") {
        ReviewDetailsViewModel(userId, reviewId, dependencies.getReview)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReviewDetailsScreen(state, onNavigateBack, onAuthorClick, onAuthorChatClick, viewModel::onRetry, modifier)
}

@Composable
internal fun ReviewDetailsScreen(
    state: ReviewDetailsUiState,
    onNavigateBack: () -> Unit,
    onAuthorClick: (UserId) -> Unit,
    onAuthorChatClick: (UserSummary) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        AppTopBar(title = stringResource(Res.string.profile_review_title), onNavigateBack = onNavigateBack)
        when (state) {
            ReviewDetailsUiState.Loading -> LoadingState()
            ReviewDetailsUiState.NotFound -> MessageState(
                title = stringResource(Res.string.profile_review_not_found_title),
                text = stringResource(Res.string.profile_review_not_found_text),
            )
            is ReviewDetailsUiState.Error -> MessageState(
                title = stringResource(Res.string.profile_load_error),
                text = failureText(state.cause),
                actionText = stringResource(Res.string.profile_retry),
                onAction = onRetry,
            )
            is ReviewDetailsUiState.Content -> ReviewContent(state.review, onAuthorClick, onAuthorChatClick)
        }
    }
}

/**
 * The review as a page, not a card: the author, the title, the stars with the mark beside them, the project and the
 * text, whole, in paragraphs. The page scrolls.
 */
@Composable
private fun ColumnScope.ReviewContent(
    review: Review,
    onAuthorClick: (UserId) -> Unit,
    onAuthorChatClick: (UserSummary) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PersonCard(
            model = PersonCardUiModel(
                name = review.author.name,
                subtitle = review.authorLine(),
                // API-GAP G18: no real avatars yet. API-GAP G15: the API does not say whether the author is verified,
                // so with the Gateway there is no shield.
                avatar = AppAvatars.forKey(review.author.id.value),
                isVerified = review.author.isVerified,
                // API-GAP G55: an author the server gave no id for has no profile to open and nobody to message.
                showChatButton = review.canMessageAuthor,
                chatContentDescription = stringResource(Res.string.profile_message_author),
            ),
            onClick = if (review.canOpenAuthor) ({ onAuthorClick(review.author.id) }) else null,
            onChatClick = { onAuthorChatClick(review.author) },
        )
        Text(review.title, style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RatingStars(value = review.rating, size = RatingStarsSize.Medium)
            Text(review.rating.toString(), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        }
        ProjectLine(review)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            review.text.lines().map(String::trim).filter(String::isNotEmpty).forEach { paragraph ->
                Text(paragraph, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** "Проект «AI-навигация по кампусу» · завершён в мар 2026"; without the month, or without the project, shorter. */
@Composable
private fun ProjectLine(review: Review) {
    if (review.projectTitle.isBlank()) return
    val finished = review.projectFinished
    val text = if (finished != null) {
        stringResource(Res.string.profile_review_project_line, review.projectTitle, yearMonthText(finished))
    } else {
        stringResource(Res.string.profile_review_project, review.projectTitle)
    }
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(AppIcons.Calendar, contentDescription = null, size = 20.dp, tint = colors.onSurfaceVariant)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}
