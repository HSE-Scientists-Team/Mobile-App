package org.matchlab.academic_net.feature.responses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.InfoBannerTone
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.components.person.nameOrPlaceholder
import org.matchlab.academic_net.components.person.subtitleText
import org.matchlab.academic_net.feature.responses.ui.generated.resources.Res
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_accepted_banner
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_accepted_banner_no_role
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_action_unavailable
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_filter_all
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_filter_role
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_load_error
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_no_places
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_project_empty_text
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_project_empty_title
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_project_not_found
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_project_title
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_project_title_count
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_retry

/**
 * 07d "Отклики на роли": the applications to the roles of the initiator's project, with the chips "Все · N" and
 * "Роль · n" (the numbers are the applications that wait for a decision). ✓ and × decide at once; a decided card keeps its
 * chip ("Принят", "Отклонён"). After ✓ a banner says who joined which role. A candidate whose profile is known opens
 * it and has the chat; one whose profile could not be read is "Без имени" with the neutral avatar.
 *
 * @param onCandidateClick a candidate whose profile is known: opens it
 * @param onContactCandidate the chat button of a candidate
 */
@Composable
fun ProjectResponsesRoute(
    dependencies: ResponsesUiDependencies,
    projectId: ProjectId,
    onNavigateBack: () -> Unit,
    onCandidateClick: (UserId) -> Unit,
    onContactCandidate: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = "project-responses-${projectId.value}") {
        ProjectResponsesViewModel(
            projectId = projectId,
            observeProjectResponses = dependencies.observeProjectResponses,
            refreshProjectResponses = dependencies.refreshProjectResponses,
            decideApplication = dependencies.decideApplication,
            clock = dependencies.clock,
            timeZone = dependencies.timeZone,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProjectResponsesScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onRoleSelected = viewModel::onRoleSelected,
        onAcceptClick = viewModel::onAcceptClick,
        onDeclineClick = viewModel::onDeclineClick,
        onCandidateClick = onCandidateClick,
        onContactCandidate = onContactCandidate,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
internal fun ProjectResponsesScreen(
    state: ProjectResponsesUiState,
    onNavigateBack: () -> Unit,
    onRoleSelected: (RoleId?) -> Unit,
    onAcceptClick: (ProjectJoinId) -> Unit,
    onDeclineClick: (ProjectJoinId) -> Unit,
    onCandidateClick: (UserId) -> Unit,
    onContactCandidate: (UserSummary) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        AppTopBar(
            title = if (state.waitingCount > 0) {
                stringResource(Res.string.responses_project_title_count, state.waitingCount)
            } else {
                stringResource(Res.string.responses_project_title)
            },
            subtitle = state.projectTitle.takeIf { it.isNotBlank() },
            onNavigateBack = onNavigateBack,
        )
        when {
            state.isNotFound -> MessageState(title = stringResource(Res.string.responses_project_not_found))
            state.loadFailure != null -> MessageState(
                title = stringResource(Res.string.responses_load_error),
                text = failureText(state.loadFailure),
                actionText = stringResource(Res.string.responses_retry),
                onAction = onRetry,
            )
            state.isLoading -> LoadingState()
            state.items.isEmpty() -> MessageState(
                title = stringResource(Res.string.responses_project_empty_title),
                text = stringResource(Res.string.responses_project_empty_text),
            )
            else -> {
                RoleFilters(state, onRoleSelected)
                CandidateList(state, onAcceptClick, onDeclineClick, onCandidateClick, onContactCandidate)
            }
        }
    }
}

/** The chips "Все · N" and "Роль · n": a scrolling row, one chip per role that has applications. */
@Composable
private fun RoleFilters(state: ProjectResponsesUiState, onRoleSelected: (RoleId?) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppFilterChip(
            text = stringResource(Res.string.responses_filter_all, state.waitingCount),
            selected = state.selectedRole == null,
            onClick = { onRoleSelected(null) },
        )
        state.roleFilters.forEach { filter ->
            AppFilterChip(
                text = stringResource(Res.string.responses_filter_role, filter.title, filter.waiting),
                selected = state.selectedRole == filter.id,
                onClick = { onRoleSelected(filter.id) },
            )
        }
    }
}

@Composable
private fun CandidateList(
    state: ProjectResponsesUiState,
    onAcceptClick: (ProjectJoinId) -> Unit,
    onDeclineClick: (ProjectJoinId) -> Unit,
    onCandidateClick: (UserId) -> Unit,
    onContactCandidate: (UserSummary) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.accepted?.let { accepted ->
            item(key = "accepted") {
                val name = nameOrPlaceholder(accepted.candidateName)
                InfoBanner(
                    text = if (accepted.roleTitle.isBlank()) {
                        stringResource(Res.string.responses_accepted_banner_no_role, name)
                    } else {
                        stringResource(Res.string.responses_accepted_banner, name, accepted.roleTitle)
                    },
                    icon = AppIcons.Check,
                )
            }
        }
        state.noPlacesRole?.let { role ->
            item(key = "no-places") {
                InfoBanner(text = stringResource(Res.string.responses_no_places, role), icon = AppIcons.Lock)
            }
        }
        state.actionFailure?.let { failure ->
            item(key = "failure") {
                InfoBanner(
                    text = when (failure) {
                        JoinFailure.NotAllowed -> stringResource(Res.string.responses_action_unavailable)
                        is JoinFailure.Failed -> failureText(failure.cause)
                    },
                    tone = InfoBannerTone.Error,
                )
            }
        }
        items(state.visibleItems, key = { it.id.value }) { item ->
            val candidate = item.candidate
            val person = candidate?.person?.takeIf { item.hasProfile }
            CandidateCard(
                model = CandidateCardUiModel(
                    name = person?.user?.name.orEmpty(),
                    subtitle = person?.subtitleText().orEmpty(),
                    avatar = person?.let { AppAvatars.forKey(it.user.id.value) },
                    note = item.daysAgo?.let { candidateAppliedAgoText(it) },
                    tags = if (person != null) candidate.skills else emptyList(),
                    status = joinStatusChip(item.join.type, item.join.status),
                    showActions = item.canDecide,
                    showChat = item.hasProfile,
                    deciding = item.id in state.inProgress,
                    acceptEnabled = item.roleHasPlace,
                ),
                onClick = person?.let { { onCandidateClick(it.user.id) } },
                onAcceptClick = { onAcceptClick(item.id) },
                onDeclineClick = { onDeclineClick(item.id) },
                onChatClick = { person?.let { onContactCandidate(it.user) } },
            )
        }
    }
}
