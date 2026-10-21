package org.matchlab.academic_net.feature.responses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.components.AppBottomSheet
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppMultilineField
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.InfoBannerTone
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.RadioListItem
import org.matchlab.academic_net.components.RoleSelectCard
import org.matchlab.academic_net.components.SheetHeader
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.feature.responses.ui.generated.resources.Res
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_invite_conflict
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_invite_create_project
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_invite_empty_text
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_invite_empty_title
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_invite_group_project
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_invite_group_role
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_invite_message
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_invite_message_hint
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_invite_not_allowed
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_invite_sent
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_invite_submit
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_invite_title
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_load_error
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_no_longer_available
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_retry

/**
 * 09k–09p "Пригласить в проект": the bottom sheet over the profile of a person. The viewer's projects that take people
 * are a radio list ("Проект"), the free roles of the chosen one are cards ("Роль"; the chosen one has a primary
 * outline), then the optional message and "Отправить приглашение". Without such a project (09m) the sheet offers to
 * create one. The server's refusals are a banner of the error tone over the button: 09o (the role is taken, or the person
 * is invited already), 09p (only one's own projects), "Проект или роль больше недоступны"; anything else by its kind.
 * After a success [onInvited] is called once (the host closes the sheet and shows [InvitationSentBanner]).
 *
 * @param candidateId the person who is invited
 * @param preselected the role chosen on the way here (from 08 with a role); `null` for none
 * @param requestId makes a fresh view model per opening: it is part of the key
 * @param onCreateProject the button of 09m
 */
@Composable
fun InviteToProjectSheet(
    dependencies: ResponsesUiDependencies,
    candidateId: UserId,
    preselected: InvitePreselection?,
    requestId: Int,
    onDismiss: () -> Unit,
    onInvited: () -> Unit,
    onCreateProject: () -> Unit,
) {
    val viewModel = viewModel(key = "invite-${candidateId.value}-$requestId") {
        InviteToProjectViewModel(candidateId, preselected, dependencies.getInviteOptions, dependencies.inviteToProject)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isInvited) { if (state.isInvited) onInvited() }

    AppBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(title = stringResource(Res.string.responses_invite_title))
        when {
            state.isLoading -> SheetBox { LoadingState() }
            state.loadFailure != null -> SheetBox {
                MessageState(
                    title = stringResource(Res.string.responses_load_error),
                    text = failureText(state.loadFailure),
                    actionText = stringResource(Res.string.responses_retry),
                    onAction = viewModel::onRetry,
                )
            }
            state.options.isEmpty() -> SheetBox {
                MessageState(
                    title = stringResource(Res.string.responses_invite_empty_title),
                    text = stringResource(Res.string.responses_invite_empty_text),
                    actionText = stringResource(Res.string.responses_invite_create_project),
                    onAction = onCreateProject,
                )
            }
            else -> InviteForm(
                state = state,
                onProjectSelected = viewModel::onProjectSelected,
                onRoleSelected = viewModel::onRoleSelected,
                onMessageChange = viewModel::onMessageChange,
                onSubmit = viewModel::onSubmit,
            )
        }
    }
}

/**
 * The room of a state that has no content of its own in a sheet (loading, an error, nothing to choose): the states fill
 * the space they are given, and a sheet has no height of its own, so they get a fixed one here instead of the whole screen.
 */
@Composable
private fun SheetBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(SheetStateHeight)) { content() }
}

private val SheetStateHeight = 240.dp

@Composable
private fun ColumnScope.InviteForm(
    state: InviteToProjectUiState,
    onProjectSelected: (ProjectId) -> Unit,
    onRoleSelected: (RoleId) -> Unit,
    onMessageChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        state.failure?.let { InfoBanner(text = inviteFailureText(it), tone = InfoBannerTone.Error) }
        Group(stringResource(Res.string.responses_invite_group_project)) {
            state.options.forEach { option ->
                RadioListItem(
                    text = option.project.title,
                    selected = option.project.id == state.selectedProject,
                    onClick = { onProjectSelected(option.project.id) },
                )
            }
        }
        // The roles are those of the chosen project; before a choice there is nothing to pick from.
        if (state.selectedProject != null) {
            Group(stringResource(Res.string.responses_invite_group_role)) {
                state.roles.forEach { role ->
                    RoleSelectCard(
                        role = role.title,
                        skills = role.skills,
                        selected = role.id == state.selectedRole,
                        onClick = { onRoleSelected(role.id) },
                    )
                }
            }
        }
        AppMultilineField(
            value = state.message,
            onValueChange = onMessageChange,
            label = stringResource(Res.string.responses_invite_message),
            placeholder = stringResource(Res.string.responses_invite_message_hint),
            enabled = !state.isSubmitting,
        )
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
        AppButton(
            text = stringResource(Res.string.responses_invite_submit),
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmit,
            loading = state.isSubmitting,
        )
    }
}

/** A titled group of the form: "Проект", "Роль". */
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}

@Composable
private fun inviteFailureText(failure: InviteFailure): String = when (failure) {
    InviteFailure.Conflict -> stringResource(Res.string.responses_invite_conflict)
    InviteFailure.NotAllowed -> stringResource(Res.string.responses_invite_not_allowed)
    InviteFailure.Gone -> stringResource(Res.string.responses_no_longer_available)
    is InviteFailure.Failed -> failureText(failure.cause)
}

/** The banner 09q "Приглашение отправлено": [InfoBanner] with a check, above the header of the profile. */
@Composable
fun InvitationSentBanner(modifier: Modifier = Modifier) {
    InfoBanner(
        text = stringResource(Res.string.responses_invite_sent),
        modifier = modifier,
        icon = AppIcons.Check,
    )
}
