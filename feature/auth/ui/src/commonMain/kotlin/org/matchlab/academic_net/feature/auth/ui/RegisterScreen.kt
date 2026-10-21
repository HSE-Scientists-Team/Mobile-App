package org.matchlab.academic_net.feature.auth.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppCheckboxRow
import org.matchlab.academic_net.components.AppPasswordField
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.feature.auth.ui.generated.resources.Res
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_email_hint
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_email_label
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_have_account
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_password_hint
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_password_label
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_register
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_register_title
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_repeat_password_label
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_share_statistics
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_share_statistics_note
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_sign_in

/**
 * "20 Регистрация": the address of the university, a password and its repeat, and the box "Отправлять статистику
 * использования". The server sends a letter with a link; the account exists only once the link is opened, and the next
 * screen waits for that.
 *
 * The box is not in the design: it stands under the repeated password, on, with the note "Можно изменить в
 * настройках" under it. Clearing it never stops the registration.
 *
 * Only the address and the box of the form survive the death of the process; the passwords are never saved (see
 * [RegisterViewModel]).
 *
 * @param email the address to start with (the one typed on the sign-in screen); a new value given here replaces
 * what the user has typed only when it differs from the one given before, see [RegisterViewModel.onEmailSuggested]
 * @param onLetterSent the letter is on its way to the address (as the server has it), and `shareStatistics` is the box
 * as the user left it: the app keeps that choice as the setting of the device (the box that was never touched is "on",
 * and this is where that is kept) and opens the confirmation for the address.
 * @param onShareStatisticsChange the user ticked or cleared the box: the app keeps the choice as the setting of the device
 * at once, so that a refusal is never lost to a form that is left, or to a box changed after the letter has gone. The screen stays in the back stack, and what the user did on it stays too: the arrow of the confirmation
 * screen ("Изменить почту") comes back to the filled form
 * @param onSignInClick the user has an account already ("Уже есть аккаунт? Войти", or "Войти" next to "Аккаунт с
 * этой почтой уже есть"): the app opens the sign-in with the address typed so far
 * @param onNavigateBack the back arrow
 */
@Composable
fun RegisterRoute(
    dependencies: AuthUiDependencies,
    email: String,
    onLetterSent: (email: String, shareStatistics: Boolean) -> Unit,
    onShareStatisticsChange: (shareStatistics: Boolean) -> Unit,
    onSignInClick: (email: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel {
        RegisterViewModel(
            initialEmail = email,
            register = dependencies.register,
            savedState = createSavedStateHandle(),
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(email) { viewModel.onEmailSuggested(email) }
    LaunchedEffect(state.letterSentTo) {
        state.letterSentTo?.let { sentTo ->
            // The box as it is now: the user may have changed it while the request was on its way.
            onLetterSent(sentTo, state.shareStatistics)
            // Passed on: if the user comes back, the screen must not leave again.
            viewModel.onLetterSentHandled()
        }
    }
    // The user comes back from the confirmation: the form takes requests again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onReturned() }

    RegisterScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onRepeatedPasswordChange = viewModel::onRepeatedPasswordChange,
        onShareStatisticsChange = { share ->
            viewModel.onShareStatisticsChange(share)
            // Kept at once, not only with the letter: the person may leave the form before the answer comes, or tick the
            // box when the letter has gone, and the device must have what the box shows.
            onShareStatisticsChange(share)
        },
        onRegisterClick = viewModel::onRegisterClick,
        onSignInClick = { onSignInClick(viewModel.uiState.value.email) },
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
internal fun RegisterScreen(
    state: RegisterUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRepeatedPasswordChange: (String) -> Unit,
    onShareStatisticsChange: (Boolean) -> Unit,
    onRegisterClick: () -> Unit,
    onSignInClick: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthFormScreen(
        topBar = { AppTopBar(title = stringResource(Res.string.auth_register_title), onNavigateBack = onNavigateBack) },
        modifier = modifier,
    ) {
        FormGroup(FormMetrics.PartGap) {
            AppTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = stringResource(Res.string.auth_email_label),
                placeholder = stringResource(Res.string.auth_email_hint),
                keyboardOptions = EmailKeyboardOptions,
                errorText = state.emailMessage?.text(),
                // With an account that exists already the way out stands next to the text.
                errorAction = if (state.offersSignIn) {
                    {
                        FormButton(
                            text = stringResource(Res.string.auth_sign_in),
                            onClick = onSignInClick,
                            variant = AppButtonVariant.Text,
                        )
                    }
                } else {
                    null
                },
            )
            AppPasswordField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(Res.string.auth_password_label),
                hint = stringResource(Res.string.auth_password_hint),
                errorText = state.passwordMessage?.text(),
                imeAction = ImeAction.Next,
            )
            AppPasswordField(
                value = state.repeatedPassword,
                onValueChange = onRepeatedPasswordChange,
                label = stringResource(Res.string.auth_repeat_password_label),
                errorText = state.repeatedPasswordMessage?.text(),
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { onRegisterClick() }),
            )
            // Not in the design: a row of the shared component, in the tokens of the form, under the last field.
            AppCheckboxRow(
                text = stringResource(Res.string.auth_share_statistics),
                checked = state.shareStatistics,
                onCheckedChange = onShareStatisticsChange,
                note = stringResource(Res.string.auth_share_statistics_note),
            )
        }
        FormGroup(FormMetrics.ButtonGap) {
            FormButton(
                text = stringResource(Res.string.auth_register),
                onClick = onRegisterClick,
                modifier = Modifier.fillMaxWidth(),
                loading = state.isSubmitting,
            )
            state.buttonMessage?.let { FormErrorText(it.text()) }
            FormButton(
                text = stringResource(Res.string.auth_have_account),
                onClick = onSignInClick,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.Text,
            )
        }
    }
}
