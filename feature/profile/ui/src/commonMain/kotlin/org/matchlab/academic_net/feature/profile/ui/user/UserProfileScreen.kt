package org.matchlab.academic_net.feature.profile.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.ReportIconButton
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.components.person.nameOrPlaceholder
import org.matchlab.academic_net.components.person.subtitleText
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.ui.ProfileHeaderCard
import org.matchlab.academic_net.feature.profile.ui.ProfileHeaderCardUiModel
import org.matchlab.academic_net.feature.profile.ui.headerStatuses
import org.matchlab.academic_net.feature.profile.ui.ProfileSections
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.profile.ui.ProfileUiModel
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_invite
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_load_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_not_found_text
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_not_found_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_retry
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_title
import org.matchlab.academic_net.feature.profile.ui.openableLink
import org.matchlab.academic_net.feature.profile.ui.rememberLinkOpener

/**
 * "09 Профиль коллаборатора": the profile of another person. The flag of the app bar reports them (09c), "Пригласить в
 * проект" under the header invites them to a project (09k), the chat icon of the header starts a chat. None of the three
 * is on the viewer's own page. Blocking is a later sprint and is not shown.
 *
 * A work with a web link opens the link (API-GAP G56: a link of another kind is not opened); a document is not a
 * button. A review card opens the review in full, its author row opens the profile of the author, its chat icon starts
 * a chat with them (an author with no id, API-GAP G55, has neither).
 *
 * @param onNavigateBack the back arrow of the app bar
 * @param onMessageClick the chat icon of the header ("Написать"); not shown on the viewer's own page
 * @param onAllWorksClick "Все" of the works
 * @param onAllReviewsClick "Все N" of the reviews
 * @param onReviewClick a review card: the person whose page this is, and the review
 * @param onAuthorClick the author row of a review card
 * @param onAuthorChatClick the chat icon of a review card
 * @param onInviteClick "Пригласить в проект": the host opens the sheet 09k
 * @param onReportClick the flag of the app bar: the host opens the sheet 09c
 * @param topBanner drawn above the header, e.g. "Приглашение отправлено" (09q); `null` for none
 */
@Composable
fun UserProfileRoute(
    dependencies: ProfileUiDependencies,
    userId: UserId,
    onNavigateBack: () -> Unit,
    onMessageClick: (UserSummary) -> Unit,
    onAllWorksClick: (UserId) -> Unit,
    onAllReviewsClick: (UserId) -> Unit,
    onReviewClick: (UserId, ReviewId) -> Unit,
    onAuthorClick: (UserId) -> Unit,
    onAuthorChatClick: (UserSummary) -> Unit,
    onInviteClick: () -> Unit,
    onReportClick: () -> Unit,
    topBanner: (@Composable () -> Unit)? = null,
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
        onReviewClick = { reviewId -> onReviewClick(userId, reviewId) },
        onAuthorClick = onAuthorClick,
        onAuthorChatClick = onAuthorChatClick,
        onInviteClick = onInviteClick,
        onReportClick = onReportClick,
        onRetry = viewModel::onRetry,
        modifier = modifier,
        topBanner = topBanner,
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
    onAuthorClick: (UserId) -> Unit,
    onAuthorChatClick: (UserSummary) -> Unit,
    onInviteClick: () -> Unit,
    onReportClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    topBanner: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        AppTopBar(
            title = stringResource(Res.string.profile_title),
            onNavigateBack = onNavigateBack,
            actions = {
                // Nobody reports themselves (09c).
                if ((state as? UserProfileUiState.Content)?.profile?.canReport == true) {
                    ReportIconButton(onClick = onReportClick)
                }
            },
        )
        when (state) {
            UserProfileUiState.Loading -> LoadingState()
            UserProfileUiState.NotFound -> MessageState(
                title = stringResource(Res.string.profile_not_found_title),
                text = stringResource(Res.string.profile_not_found_text),
            )
            is UserProfileUiState.Error -> MessageState(
                title = stringResource(Res.string.profile_load_error),
                text = failureText(state.cause),
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
                onAuthorClick = onAuthorClick,
                onAuthorChatClick = onAuthorChatClick,
                onInviteClick = onInviteClick,
                topBanner = topBanner,
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
    onAuthorClick: (UserId) -> Unit,
    onAuthorChatClick: (UserSummary) -> Unit,
    onInviteClick: () -> Unit,
    topBanner: (@Composable () -> Unit)?,
) {
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        topBanner?.invoke()
        ProfileHeaderCard(
            model = ProfileHeaderCardUiModel(
                name = nameOrPlaceholder(profile.user.name),
                subtitle = profile.person.subtitleText(),
                // API-GAP G18: no real avatars yet, an illustration chosen by the id.
                avatar = AppAvatars.forKey(profile.user.id.value),
                statuses = profile.headerStatuses(),
                showChatButton = profile.canMessage,
            ),
            onChatClick = onMessageClick,
        )
        if (profile.canInvite) {
            AppButton(
                text = stringResource(Res.string.profile_invite),
                onClick = onInviteClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ProfileSections(
            profile = profile,
            isOwn = false,
            // Nothing to fill on someone else's page: empty sections are hidden there.
            onFillClick = {},
            // API-GAP G56: only a web link is opened; the card of a work with another kind of link is not a button.
            onWorkClick = { work -> openableLink(work.link)?.let(onWorkLinkClick) },
            // The documents of another person are not buttons: there is nothing to open.
            onDocumentClick = {},
            onAllWorksClick = onAllWorksClick,
            onAllReviewsClick = onAllReviewsClick,
            onReviewClick = onReviewClick,
            onAuthorClick = onAuthorClick,
            onAuthorChatClick = onAuthorChatClick,
        )
    }
}
