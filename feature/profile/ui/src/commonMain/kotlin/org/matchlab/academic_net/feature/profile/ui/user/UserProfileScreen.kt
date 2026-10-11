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
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppStatusChipTone
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.ui.ProfileHeaderCard
import org.matchlab.academic_net.feature.profile.ui.ProfileHeaderCardUiModel
import org.matchlab.academic_net.feature.profile.ui.ProfileSections
import org.matchlab.academic_net.feature.profile.ui.ProfileStatusUiModel
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.profile.ui.ProfileUiModel
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_load_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_message
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_not_found_text
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_not_found_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_retry
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_status_unverified
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_status_verified
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_title
import org.matchlab.academic_net.feature.profile.ui.rememberLinkOpener

/**
 * "09 Профиль коллаборатора": the profile of another person, read-only. The actions of the
 * design that belong to later stories (invite, open to collaboration, report, block) are not
 * shown; one wide "Написать" stands for the invite and the chat.
 *
 * @param onNavigateBack the back arrow of the app bar
 * @param onMessageClick "Написать"; not shown on the viewer's own page
 * @param onAllWorksClick "Все" of the works
 * @param onAllReviewsClick "Все N" of the reviews
 * @param onReviewClick a review card
 */
@Composable
fun UserProfileRoute(
    dependencies: ProfileUiDependencies,
    userId: UserId,
    onNavigateBack: () -> Unit,
    onMessageClick: (UserSummary) -> Unit,
    onAllWorksClick: (UserId) -> Unit,
    onAllReviewsClick: (UserId) -> Unit,
    onReviewClick: (ReviewId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = "user-profile-${userId.value}") {
        UserProfileViewModel(userId, dependencies.getUserProfile)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openLink = rememberLinkOpener()

    UserProfileScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onMessageClick = {
            (state as? UserProfileUiState.Content)?.profile?.user?.let(onMessageClick)
        },
        onWorkLinkClick = openLink,
        onAllWorksClick = { onAllWorksClick(userId) },
        onAllReviewsClick = { onAllReviewsClick(userId) },
        onReviewClick = onReviewClick,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
internal fun UserProfileScreen(
    state: UserProfileUiState,
    onNavigateBack: () -> Unit,
    onMessageClick: () -> Unit,
    onWorkLinkClick: (String) -> Unit,
    onAllWorksClick: () -> Unit,
    onAllReviewsClick: () -> Unit,
    onReviewClick: (ReviewId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        AppTopBar(title = stringResource(Res.string.profile_title), onNavigateBack = onNavigateBack)
        when (state) {
            UserProfileUiState.Loading -> LoadingState()
            UserProfileUiState.NotFound -> MessageState(
                title = stringResource(Res.string.profile_not_found_title),
                text = stringResource(Res.string.profile_not_found_text),
            )
            UserProfileUiState.Error -> MessageState(
                title = stringResource(Res.string.profile_load_error),
                actionText = stringResource(Res.string.profile_retry),
                onAction = onRetry,
            )
            is UserProfileUiState.Content -> ProfileContent(
                profile = state.profile,
                onMessageClick = onMessageClick,
                onWorkLinkClick = onWorkLinkClick,
                onAllWorksClick = onAllWorksClick,
                onAllReviewsClick = onAllReviewsClick,
                onReviewClick = onReviewClick,
            )
        }
    }
}

@Composable
private fun ColumnScope.ProfileContent(
    profile: ProfileUiModel,
    onMessageClick: () -> Unit,
    onWorkLinkClick: (String) -> Unit,
    onAllWorksClick: () -> Unit,
    onAllReviewsClick: () -> Unit,
    onReviewClick: (ReviewId) -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileHeaderCard(
            model = ProfileHeaderCardUiModel(
                name = profile.user.name,
                subtitle = profile.subtitle,
                avatar = AppAvatars.forKey(profile.user.id.value),
                isVerified = profile.user.isVerified,
                // Verification status of the person; on their own page it is not needed.
                statuses = if (profile.canMessage) listOf(verificationStatus(profile.user.isVerified)) else emptyList(),
                actionText = if (profile.canMessage) stringResource(Res.string.profile_message) else null,
            ),
            onActionClick = onMessageClick,
        )
        ProfileSections(
            profile = profile,
            isOwn = false,
            // Nothing to fill on someone else's page: empty sections are hidden there.
            onFillClick = {},
            onWorkClick = { work -> work.link?.let(onWorkLinkClick) },
            onAllWorksClick = onAllWorksClick,
            onAllReviewsClick = onAllReviewsClick,
            onReviewClick = onReviewClick,
        )
    }
}

@Composable
private fun verificationStatus(isVerified: Boolean): ProfileStatusUiModel = if (isVerified) {
    ProfileStatusUiModel(stringResource(Res.string.profile_status_verified), AppStatusChipTone.Success)
} else {
    ProfileStatusUiModel(stringResource(Res.string.profile_status_unverified), AppStatusChipTone.Off)
}
