package org.matchlab.academic_net.feature.profile.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppIcon
import org.matchlab.academic_net.components.AppIconButton
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.NotificationListItem
import org.matchlab.academic_net.components.NotificationListItemUiModel
import org.matchlab.academic_net.components.format.chatListTimeText
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.VerificationItem
import org.matchlab.academic_net.feature.profile.domain.VerificationOutcome
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.ui.ProfileHeaderCard
import org.matchlab.academic_net.feature.profile.ui.ProfileHeaderCardUiModel
import org.matchlab.academic_net.feature.profile.ui.ProfileSections
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.profile.ui.ProfileUiModel
import org.matchlab.academic_net.feature.profile.ui.VerificationResultItem
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_edit
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_load_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_mark_pending
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_my_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_result_rejected
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_result_verified
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_retry
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_settings
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_verification_rejected
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_verification_title
import org.matchlab.academic_net.feature.profile.ui.rememberLinkOpener

/**
 * "10 Мой профиль": the root screen of the "Профиль" tab: the viewer's profile as others see it,
 * what is being checked in it and the decisions of the moderators.
 *
 * "Связаться с поддержкой" of the design is not shown: there is no support channel yet.
 *
 * @param onEditClick the pencil of the app bar and every empty section ("Не заполнено")
 * @param onSettingsClick the gear of the app bar; `null` while the app has no settings, then the
 *  gear is not shown
 * @param onAllWorksClick "Все" of the portfolio
 * @param onAllReviewsClick "Все N" of the reviews
 * @param onReviewClick a review card
 */
@Composable
fun MyProfileRoute(
    dependencies: ProfileUiDependencies,
    onEditClick: () -> Unit,
    onSettingsClick: (() -> Unit)?,
    onAllWorksClick: (UserId) -> Unit,
    onAllReviewsClick: (UserId) -> Unit,
    onReviewClick: (ReviewId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel {
        MyProfileViewModel(
            observeMyProfile = dependencies.observeMyProfile,
            observeVerificationResults = dependencies.observeVerificationResults,
            markResultsSeen = dependencies.markResultsSeen,
            visibility = dependencies.myProfileVisibility,
            clock = dependencies.clock,
            timeZone = dependencies.timeZone,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openLink = rememberLinkOpener()
    // The view model marks decisions as seen only while this screen is visible. The tabs share one
    // navigation destination, so leaving the tab ends the composition (onDispose) but not the
    // lifecycle; leaving the screen or the app stops it. The shown decisions go away at that point.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onScreenShown() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenLeft() }
    DisposableEffect(Unit) { onDispose { viewModel.onScreenLeft() } }

    MyProfileScreen(
        state = state,
        onEditClick = onEditClick,
        onSettingsClick = onSettingsClick,
        onWorkLinkClick = openLink,
        onAllWorksClick = {
            (state as? MyProfileUiState.Content)?.profile?.user?.id?.let(onAllWorksClick)
        },
        onAllReviewsClick = {
            (state as? MyProfileUiState.Content)?.profile?.user?.id?.let(onAllReviewsClick)
        },
        onReviewClick = onReviewClick,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
internal fun MyProfileScreen(
    state: MyProfileUiState,
    onEditClick: () -> Unit,
    onSettingsClick: (() -> Unit)?,
    onWorkLinkClick: (String) -> Unit,
    onAllWorksClick: () -> Unit,
    onAllReviewsClick: () -> Unit,
    onReviewClick: (ReviewId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        AppTopBar(
            title = stringResource(Res.string.profile_my_title),
            actions = {
                AppIconButton(
                    icon = AppIcons.Pencil,
                    contentDescription = stringResource(Res.string.profile_edit),
                    onClick = onEditClick,
                )
                if (onSettingsClick != null) {
                    AppIconButton(
                        icon = AppIcons.Settings,
                        contentDescription = stringResource(Res.string.profile_settings),
                        onClick = onSettingsClick,
                    )
                }
            },
        )
        when (state) {
            MyProfileUiState.Loading -> LoadingState()
            MyProfileUiState.Error -> MessageState(
                title = stringResource(Res.string.profile_load_error),
                actionText = stringResource(Res.string.profile_retry),
                onAction = onRetry,
            )
            is MyProfileUiState.Content -> MyProfileContent(
                state = state,
                onEditClick = onEditClick,
                onWorkLinkClick = onWorkLinkClick,
                onAllWorksClick = onAllWorksClick,
                onAllReviewsClick = onAllReviewsClick,
                onReviewClick = onReviewClick,
            )
        }
    }
}

@Composable
private fun ColumnScope.MyProfileContent(
    state: MyProfileUiState.Content,
    onEditClick: () -> Unit,
    onWorkLinkClick: (String) -> Unit,
    onAllWorksClick: () -> Unit,
    onAllReviewsClick: () -> Unit,
    onReviewClick: (ReviewId) -> Unit,
) {
    // The bottom navigation belongs to the scaffold of the app, so no insets of the screen here.
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileHeader(state.profile)
        state.results.forEach { ResultItem(it) }
        if (state.queue.isNotEmpty()) VerificationCard(state.queue)
        ProfileSections(
            profile = state.profile,
            isOwn = true,
            onFillClick = onEditClick,
            onWorkClick = { work: PortfolioWork -> work.link?.let(onWorkLinkClick) },
            onAllWorksClick = onAllWorksClick,
            onAllReviewsClick = onAllReviewsClick,
            onReviewClick = onReviewClick,
        )
    }
}

@Composable
private fun ProfileHeader(profile: ProfileUiModel) {
    ProfileHeaderCard(
        ProfileHeaderCardUiModel(
            name = profile.user.name,
            subtitle = profile.subtitle,
            avatar = AppAvatars.forKey(profile.user.id.value),
            isVerified = profile.user.isVerified,
        ),
    )
}

/** The decision of a moderator: "Подтверждено: Сертификат Deep Learning" and when it was made. */
@Composable
private fun ResultItem(result: VerificationResultItem) {
    val verified = result.outcome == VerificationOutcome.Verified
    NotificationListItem(
        NotificationListItemUiModel(
            title = stringResource(
                if (verified) Res.string.profile_result_verified else Res.string.profile_result_rejected,
                result.title,
            ),
            time = chatListTimeText(result.time),
            icon = if (verified) AppIcons.ShieldCheck else AppIcons.ShieldX,
        ),
    )
}

/** "Верификация профиля": works and documents that are being checked or need more proof. */
@Composable
private fun VerificationCard(queue: List<VerificationItem>) {
    val colors = MaterialTheme.colorScheme
    AppCard(spacing = 12.dp) {
        Text(
            text = stringResource(Res.string.profile_verification_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
        )
        queue.forEach { item ->
            val rejected = item.status == VerificationStatus.Rejected
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    icon = if (rejected) AppIcons.ShieldX else AppIcons.Clock,
                    contentDescription = null,
                    size = 20.dp,
                    tint = if (rejected) colors.error else colors.onSurfaceVariant,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    Text(
                        text = stringResource(
                            if (rejected) Res.string.profile_verification_rejected else Res.string.profile_mark_pending,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (rejected) colors.error else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
