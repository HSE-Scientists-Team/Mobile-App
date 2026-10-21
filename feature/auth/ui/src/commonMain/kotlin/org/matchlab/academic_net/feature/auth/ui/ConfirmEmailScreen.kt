package org.matchlab.academic_net.feature.auth.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.feature.auth.ui.generated.resources.Res
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_change_email
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_confirm_message
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_confirm_title
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_confirmed
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_link_confirm
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_link_label
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_resend
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_resend_in

/**
 * "21 Подтвердите почту": the letter with the link was sent to [email]; the user opens the link (in the mail app or
 * the browser, away from this app) and comes back to say "Я подтвердил(а) почту", or pastes the link here. A
 * success has no callback: the session appears in the auth state and the app's root swaps the screens.
 *
 * Where the app cannot sign in by itself (the password of the registration is gone with the process that was
 * killed) the screen leaves for another one with [onSignInRequired] or [onRegisterAgain]; both close this screen
 * (the app goes back to the screen they ask for, with the address), as nothing is left to do here.
 *
 * @param email the address the letter was sent to, as [RegisterRoute] reported it; it is shown to the user and
 * is the key of the registration for the repository, so the screen is made for one address
 * @param onNavigateBack the back arrow, which is "Изменить почту", and the button with the same name: the user
 * wants another address; the app goes back to the registration, whose form is as it was left
 * @param onSignInRequired the user has to sign in by hand with [email]: "Я подтвердил(а)" or "Отправить ещё раз"
 * found that the password is not remembered (the process was killed after the letter was sent), or the letter
 * was sent again and the address had an account already. The sign-in screen does not know whether the address is
 * confirmed
 * @param onRegisterAgain the letter cannot be sent again: the password is not remembered, so the registration has
 * to be made once more, from the form (with [email] filled in)
 * @param onEmailConfirmed the address was confirmed (the pasted link was taken) and the app could not sign in by
 * itself because it does not remember the password: the sign-in screen with [email] says "Почта подтверждена.
 * Войдите". It is [onSignInRequired] unless the app wants to tell the two apart
 */
@Composable
fun ConfirmEmailRoute(
    dependencies: AuthUiDependencies,
    email: String,
    onNavigateBack: () -> Unit,
    onSignInRequired: (email: String) -> Unit,
    onRegisterAgain: (email: String) -> Unit,
    modifier: Modifier = Modifier,
    onEmailConfirmed: (email: String) -> Unit = onSignInRequired,
) {
    val viewModel = viewModel(key = email) {
        ConfirmEmailViewModel(
            email = email,
            confirmEmail = dependencies.confirmEmail,
            signInPending = dependencies.signInPending,
            resendLetter = dependencies.resendLetter,
            getPendingRegistration = dependencies.getPendingRegistration,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.exit) {
        state.exit?.let { exit ->
            exit.dispatch(email, onSignInRequired, onEmailConfirmed, onRegisterAgain)
            // Passed on: if the user comes back, the screen must not leave again.
            viewModel.onExitHandled()
        }
    }
    // The user comes back to the screen it had left for: it takes actions again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onReturned() }

    ConfirmEmailScreen(
        email = email,
        state = state,
        onLinkChange = viewModel::onLinkChange,
        onSignedInClick = viewModel::onSignedInClick,
        onConfirmLinkClick = viewModel::onConfirmLinkClick,
        onResendClick = viewModel::onResendClick,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
internal fun ConfirmEmailScreen(
    email: String,
    state: ConfirmUiState,
    onLinkChange: (String) -> Unit,
    onSignedInClick: () -> Unit,
    onConfirmLinkClick: () -> Unit,
    onResendClick: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthFormScreen(
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.auth_confirm_title),
                onNavigateBack = onNavigateBack,
                // The arrow goes back to the form where the address can be changed.
                navigationContentDescription = stringResource(Res.string.auth_change_email),
            )
        },
        modifier = modifier,
    ) {
        FormGroup(FormMetrics.PartGap) {
            Text(
                text = stringResource(Res.string.auth_confirm_message, email),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormGroup(FormMetrics.ButtonGap) {
                FormButton(
                    text = stringResource(Res.string.auth_confirmed),
                    onClick = onSignedInClick,
                    modifier = Modifier.fillMaxWidth(),
                    loading = state.running == ConfirmAction.SignIn,
                )
                // What came of this button and of "Отправить письмо ещё раз" (it has no place of its own).
                state.submitProblem?.let { FormErrorText(it.text()) }
            }
        }
        FormGroup(FormMetrics.LinkGap) {
            AppTextField(
                value = state.link,
                onValueChange = onLinkChange,
                label = stringResource(Res.string.auth_link_label),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onDone = { onConfirmLinkClick() }),
                // The link of the letter holds a one-time token: it must not be written to the saved state of the
                // system (the Bundle that goes to disk). The text lives in the view model, so a recreated activity
                // shows it anyway.
                saveState = false,
                errorText = state.linkProblem?.text(),
            )
            FormButton(
                text = stringResource(Res.string.auth_link_confirm),
                onClick = onConfirmLinkClick,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.Outlined,
                loading = state.running == ConfirmAction.ConfirmLink,
            )
        }
        FormGroup(FormMetrics.ButtonGap) {
            FormButton(
                text = if (state.isResendWaiting) {
                    stringResource(Res.string.auth_resend_in, countdownText(state.resendInSeconds))
                } else {
                    stringResource(Res.string.auth_resend)
                },
                onClick = onResendClick,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.Text,
                // Switched off while the pause lasts: the label is the countdown, in the grey of a disabled button.
                enabled = !state.isResendWaiting,
                loading = state.running == ConfirmAction.Resend,
            )
            FormButton(
                text = stringResource(Res.string.auth_change_email),
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.Text,
            )
        }
    }
}
