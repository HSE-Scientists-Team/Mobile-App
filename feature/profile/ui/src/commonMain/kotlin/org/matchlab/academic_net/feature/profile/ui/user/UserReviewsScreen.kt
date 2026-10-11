package org.matchlab.academic_net.feature.profile.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
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
 * "09b Отзывы о пользователе": the numbers over all reviews and the whole list, newest first.
 *
 * @param onReviewClick a review card, which opens the review in full
 */
@Composable
fun UserReviewsRoute(
    dependencies: ProfileUiDependencies,
    userId: UserId,
    onNavigateBack: () -> Unit,
    onReviewClick: (ReviewId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = "user-reviews-${userId.value}") {
        UserReviewsViewModel(userId, dependencies.getUserReviews)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserReviewsScreen(state, onNavigateBack, onReviewClick, viewModel::onRetry, modifier)
}

@Composable
internal fun UserReviewsScreen(
    state: UserReviewsUiState,
    onNavigateBack: () -> Unit,
    onReviewClick: (ReviewId) -> Unit,
    onRetry: () -> Unit,
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
            UserReviewsUiState.Error -> MessageState(
                title = stringResource(Res.string.profile_load_error),
                actionText = stringResource(Res.string.profile_retry),
                onAction = onRetry,
            )
            is UserReviewsUiState.Content -> if (state.items.isEmpty()) {
                MessageState(title = stringResource(Res.string.profile_no_reviews))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { SummaryCard(state.summary) }
                    items(state.items, key = { it.id.value }) { review ->
                        ReviewCard(
                            model = review.toCardUiModel(withDate = true),
                            onClick = { onReviewClick(review.id) },
                            maxTextLines = 3,
                        )
                    }
                }
            }
        }
    }
}

/** "12 отзывов · 11 положительных" with a line on who leaves the reviews. */
@Composable
private fun SummaryCard(summary: ReviewsSummary) {
    val colors = MaterialTheme.colorScheme
    AppCard(spacing = 2.dp) {
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
