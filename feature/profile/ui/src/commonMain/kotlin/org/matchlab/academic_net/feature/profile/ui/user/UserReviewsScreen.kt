package org.matchlab.academic_net.feature.profile.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.RatingStars
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.components.paging.LoadMoreEffect
import org.matchlab.academic_net.components.paging.LoadMoreFooter
import org.matchlab.academic_net.components.paging.LoadMoreState
import org.matchlab.academic_net.components.person.formatRating
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.ReviewsSummary
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.profile.ui.ReviewCard
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_load_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_no_reviews
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_not_found_text
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_not_found_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_retry
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_reviews
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_reviews_hint
import org.matchlab.academic_net.feature.profile.ui.reviewsCountText
import org.matchlab.academic_net.feature.profile.ui.reviewsTitle
import org.matchlab.academic_net.feature.profile.ui.toCardUiModel

/**
 * "09b Отзывы о пользователе": the numbers over all reviews and the list, newest first, which loads the next page as it
 * is scrolled ("Загрузка…" under it, or "Не удалось загрузить" with "Повторить").
 *
 * @param onReviewClick a review card, which opens the review in full: the person the reviews are about, and the review
 * @param onAuthorClick the author row of a review card
 * @param onAuthorChatClick the chat icon of a review card
 */
@Composable
fun UserReviewsRoute(
    dependencies: ProfileUiDependencies,
    userId: UserId,
    onNavigateBack: () -> Unit,
    onReviewClick: (UserId, ReviewId) -> Unit,
    onAuthorClick: (UserId) -> Unit,
    onAuthorChatClick: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = "user-reviews-${userId.value}") {
        UserReviewsViewModel(userId, dependencies.getUserReviews, dependencies.getReviewsPage)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserReviewsScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onReviewClick = { reviewId -> onReviewClick(userId, reviewId) },
        onAuthorClick = onAuthorClick,
        onAuthorChatClick = onAuthorChatClick,
        onRetry = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onRetryLoadMore = viewModel::onRetryLoadMore,
        modifier = modifier,
    )
}

@Composable
internal fun UserReviewsScreen(
    state: UserReviewsUiState,
    onNavigateBack: () -> Unit,
    onReviewClick: (ReviewId) -> Unit,
    onAuthorClick: (UserId) -> Unit,
    onAuthorChatClick: (UserSummary) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        // "Отзывы · ★ 4.8": the rating is known once the reviews are loaded.
        val title = if (state is UserReviewsUiState.Content) {
            reviewsTitle(state.summary.average)
        } else {
            stringResource(Res.string.profile_reviews)
        }
        AppTopBar(title = title, onNavigateBack = onNavigateBack)
        when (state) {
            UserReviewsUiState.Loading -> LoadingState()
            UserReviewsUiState.NotFound -> MessageState(
                title = stringResource(Res.string.profile_not_found_title),
                text = stringResource(Res.string.profile_not_found_text),
            )
            is UserReviewsUiState.Error -> MessageState(
                title = stringResource(Res.string.profile_load_error),
                text = failureText(state.cause),
                actionText = stringResource(Res.string.profile_retry),
                onAction = onRetry,
            )
            is UserReviewsUiState.Content -> if (state.items.isEmpty()) {
                MessageState(title = stringResource(Res.string.profile_no_reviews))
            } else {
                ReviewsList(
                    content = state,
                    onReviewClick = onReviewClick,
                    onAuthorClick = onAuthorClick,
                    onAuthorChatClick = onAuthorChatClick,
                    onLoadMore = onLoadMore,
                    onRetryLoadMore = onRetryLoadMore,
                )
            }
        }
    }
}

/** The summary, the reviews loaded so far and the end of the list that asks for the next page. */
@Composable
private fun ReviewsList(
    content: UserReviewsUiState.Content,
    onReviewClick: (ReviewId) -> Unit,
    onAuthorClick: (UserId) -> Unit,
    onAuthorChatClick: (UserSummary) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val footer = when {
        content.loadMoreFailure != null -> LoadMoreState.Failed
        content.isLoadingMore -> LoadMoreState.Loading
        content.hasMore -> LoadMoreState.Idle
        else -> LoadMoreState.End
    }
    LoadMoreEffect(listState = listState, state = footer, onLoadMore = onLoadMore)
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") { SummaryCard(content.summary) }
        items(content.items, key = { "review-${it.id.value}" }) { review ->
            ReviewCard(
                model = review.toCardUiModel(withDate = true),
                onClick = { onReviewClick(review.id) },
                onAuthorClick = { onAuthorClick(review.author.id) },
                onChatClick = { onAuthorChatClick(review.author) },
            )
        }
        item(key = "footer") { LoadMoreFooter(state = footer, onRetry = onRetryLoadMore) }
    }
}

/**
 * The numbers over all reviews: the average with the stars under it (rounded to whole stars), and, beside it, "12 отзывов
 * · 11 положительных" with a line on who leaves the reviews. Without an average (the server gave none) only the numbers
 * are there.
 */
@Composable
private fun SummaryCard(summary: ReviewsSummary) {
    val colors = MaterialTheme.colorScheme
    val average = summary.average
    AppCard {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (average != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = formatRating(average),
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.onSurface,
                    )
                    RatingStars(value = average.roundToInt())
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = reviewsCountText(summary.count, summary.positiveCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(Res.string.profile_reviews_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
