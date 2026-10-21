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
import org.matchlab.academic_net.components.AppBottomSheet
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppMultilineField
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.InfoBannerTone
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.RoleCardTitle
import org.matchlab.academic_net.components.SheetHeader
import org.matchlab.academic_net.components.SkillChips
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.feature.responses.domain.JoinMessageMaxLength
import org.matchlab.academic_net.feature.responses.ui.generated.resources.Res
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_apply_conflict
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_no_longer_available
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_apply_message
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_apply_message_hint
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_apply_not_allowed
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_apply_submit
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_apply_title
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_load_error
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_message_counter
import org.matchlab.academic_net.feature.responses.ui.generated.resources.responses_retry

/**
 * 04s–04w "Откликнуться на роль": the bottom sheet with the role (its name and skills), the optional message and
 * "Отправить отклик". The server's refusals are told in a banner of the error tone over the button: 04u (the role is taken
 * or the viewer has applied already), 04v (an e-mail that is not confirmed), "Проект или роль больше недоступны"; anything
 * else by the kind of the failure (04w: no connection). After a success [onApplied] is called once (the host closes the
 * sheet) and the button keeps its arc until it is gone.
 *
 * @param requestId makes a fresh view model per opening: it is part of the key, so a sheet opened again does not show the
 *  text of the one before
 * @param onDismiss the sheet is swiped away or tapped outside
 */
@Composable
fun ApplyToRoleSheet(
    dependencies: ResponsesUiDependencies,
    projectId: ProjectId,
    roleId: RoleId,
    requestId: Int,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
) {
    val viewModel = viewModel(key = "apply-${projectId.value}-${roleId.value}-$requestId") {
        ApplyToRoleViewModel(projectId, roleId, dependencies.getRoleToApply, dependencies.applyToRole)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isApplied) { if (state.isApplied) onApplied() }

    AppBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(title = stringResource(Res.string.responses_apply_title))
        when {
            state.isLoading -> SheetLoading()
            state.isGone -> SheetMessage(stringResource(Res.string.responses_no_longer_available))
            state.loadFailure != null -> SheetBox {
                MessageState(
                    title = stringResource(Res.string.responses_load_error),
                    text = failureText(state.loadFailure),
                    actionText = stringResource(Res.string.responses_retry),
                    onAction = viewModel::onRetry,
                )
            }
            else -> ApplyForm(state, viewModel::onMessageChange, viewModel::onSubmit)
        }
    }
}

@Composable
private fun SheetLoading() = SheetBox { LoadingState() }

@Composable
private fun SheetMessage(text: String) = SheetBox { MessageState(title = text) }

/**
 * The room of a state that has no content of its own in a sheet (loading, an error): the states fill the space they are
 * given, and a sheet has no height of its own, so they get a fixed one here instead of the whole screen.
 */
@Composable
private fun SheetBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(SheetStateHeight)) { content() }
}

private val SheetStateHeight = 200.dp

@Composable
private fun ColumnScope.ApplyForm(
    state: ApplyToRoleUiState,
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
        state.failure?.let { InfoBanner(text = applyFailureText(it), tone = InfoBannerTone.Error) }
        AppCard(spacing = 8.dp, cornerRadius = 12.dp, verticalPadding = 12.dp) {
            RoleCardTitle(state.roleTitle)
            if (state.skills.isNotEmpty()) SkillChips(state.skills)
        }
        AppMultilineField(
            value = state.message,
            onValueChange = onMessageChange,
            label = stringResource(Res.string.responses_apply_message),
            placeholder = stringResource(Res.string.responses_apply_message_hint),
            counterText = stringResource(
                Res.string.responses_message_counter,
                state.message.length,
                JoinMessageMaxLength,
            ),
            enabled = !state.isSubmitting,
        )
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
        AppButton(
            text = stringResource(Res.string.responses_apply_submit),
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            loading = state.isSubmitting,
        )
    }
}

@Composable
private fun applyFailureText(failure: ApplyFailure): String = when (failure) {
    ApplyFailure.Conflict -> stringResource(Res.string.responses_apply_conflict)
    ApplyFailure.NotAllowed -> stringResource(Res.string.responses_apply_not_allowed)
    ApplyFailure.Gone -> stringResource(Res.string.responses_no_longer_available)
    is ApplyFailure.Failed -> failureText(failure.cause)
}
