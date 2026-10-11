package org.matchlab.academic_net.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.feature.auth.domain.VerificationCodeRules
import org.matchlab.academic_net.feature.auth.ui.generated.resources.Res
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_code_hint
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_code_label
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_confirm
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_confirming
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_expired
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_network
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_resend_too_many
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_too_many_attempts
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_wrong_code
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_wrong_code_attempts
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_resend
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_resend_in
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_resending
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_verify_subtitle
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_verify_title

/**
 * The code from the email. The design has no such screen: it is built of the components of the
 * design system. Success has no callback: the session appears in the auth state and the app's
 * root leaves the sign-in flow.
 *
 * @param email the address the code was sent to, as [SignInRoute] reported it
 * @param onNavigateBack the back arrow, to change the address
 */
@Composable
fun VerifyEmailRoute(
    dependencies: AuthUiDependencies,
    email: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = email) {
        VerifyEmailViewModel(
            email = email,
            verifyCode = dependencies.verifyCode,
            requestCode = dependencies.requestCode,
            getLastChallenge = dependencies.getLastChallenge,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    VerifyEmailScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onCodeChange = viewModel::onCodeChange,
        onConfirmClick = viewModel::onConfirmClick,
        onResendClick = viewModel::onResendClick,
        modifier = modifier,
    )
}

@Composable
internal fun VerifyEmailScreen(
    state: VerifyEmailUiState,
    onNavigateBack: () -> Unit,
    onCodeChange: (String) -> Unit,
    onConfirmClick: () -> Unit,
    onResendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The code is the only thing to do here: the keyboard opens with the screen.
    val codeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { codeFocus.requestFocus() }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .imePadding(),
    ) {
        AppTopBar(
            title = stringResource(Res.string.auth_verify_title),
            onNavigateBack = onNavigateBack,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(Res.string.auth_verify_subtitle, state.email),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppTextField(
                value = state.code,
                onValueChange = onCodeChange,
                label = stringResource(Res.string.auth_code_label),
                modifier = Modifier.focusRequester(codeFocus),
                placeholder = stringResource(Res.string.auth_code_hint),
                // Not disabled while the code is checked: a disabled field loses the focus and the
                // keyboard, and the user would have to tap it again after a wrong code.
                enabled = !state.isLocked,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirmClick() }),
                errorText = state.errorText(),
            )
            AppButton(
                text = stringResource(if (state.isVerifying) Res.string.auth_confirming else Res.string.auth_confirm),
                onClick = onConfirmClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.code.length == VerificationCodeRules.LENGTH && !state.isVerifying && !state.isLocked,
            )
            val canResend = state.resendInSeconds == 0 && !state.isResending
            AppButton(
                text = when {
                    state.isResending -> stringResource(Res.string.auth_resending)
                    state.resendInSeconds > 0 ->
                        stringResource(Res.string.auth_resend_in, countdownText(state.resendInSeconds))
                    else -> stringResource(Res.string.auth_resend)
                },
                onClick = onResendClick,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.Text,
                enabled = canResend && !state.isVerifying,
            )
        }
    }
}

@Composable
private fun VerifyEmailUiState.errorText(): String? = when (error) {
    null -> null
    VerifyError.WrongCode -> attemptsLeft?.let { left ->
        pluralStringResource(Res.plurals.auth_error_wrong_code_attempts, left, left)
    } ?: stringResource(Res.string.auth_error_wrong_code)
    VerifyError.Expired -> stringResource(Res.string.auth_error_expired)
    VerifyError.TooManyAttempts -> stringResource(Res.string.auth_error_too_many_attempts)
    VerifyError.TooManyRequests -> stringResource(Res.string.auth_error_resend_too_many)
    VerifyError.Network -> stringResource(Res.string.auth_error_network)
}

/** "1:05": the time to wait as minutes and two-digit seconds. */
internal fun countdownText(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
