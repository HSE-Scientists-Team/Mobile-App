package org.matchlab.academic_net.feature.profile.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.InfoCard
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.format.yearMonthText
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.profile.ui.ReviewCard
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_load_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_retry
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_review_not_found_text
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_review_not_found_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_review_project_finished
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_review_title
import org.matchlab.academic_net.feature.profile.ui.toCardUiModel

/**
 * "09d Отзыв — подробно": one review with its whole text and the month its project ended. The
 * "⋮" menu of the design (report) belongs to a later story and is not shown.
 *
 * @param onAuthorClick the author line of the card, which leads to the author's profile
 */
@Composable
fun ReviewDetailsRoute(
    dependencies: ProfileUiDependencies,
    reviewId: ReviewId,
    onNavigateBack: () -> Unit,
    onAuthorClick: (UserId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = "review-${reviewId.value}") {
        ReviewDetailsViewModel(reviewId, dependencies.getReview)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReviewDetailsScreen(state, onNavigateBack, onAuthorClick, viewModel::onRetry, modifier)
}

@Composable
internal fun ReviewDetailsScreen(
    state: ReviewDetailsUiState,
    onNavigateBack: () -> Unit,
    onAuthorClick: (UserId) -> Unit,
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
            ReviewDetailsUiState.Error -> MessageState(
                title = stringResource(Res.string.profile_load_error),
                actionText = stringResource(Res.string.profile_retry),
                onAction = onRetry,
            )
            is ReviewDetailsUiState.Content -> ReviewContent(state.review, onAuthorClick)
        }
    }
}

@Composable
private fun ColumnScope.ReviewContent(review: Review, onAuthorClick: (UserId) -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The month is in the card below, so the author line has none.
        ReviewCard(
            model = review.toCardUiModel(withDate = false),
            onAuthorClick = { onAuthorClick(review.author.id) },
        )
        InfoCard(
            text = stringResource(Res.string.profile_review_project_finished, yearMonthText(review.projectFinished)),
            icon = AppIcons.Calendar,
        )
    }
}
