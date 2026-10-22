package org.matchlab.academic_net.feature.auth.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppPasswordField
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.SecurityNote
import org.matchlab.academic_net.feature.auth.ui.generated.resources.Res
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_create_account
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_email_confirmed
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_email_hint
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_email_label
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_password_label
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_secure_note
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_session_expired
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_sign_in
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_sign_in_title

/**
 * "19 Вход": the address and the password of an account. It is the first screen of the app for a user who has no
 * session, and only for them: the app's root shows [AuthSplash] while the saved session is read and the tabs
 * when there is one, so a user who is still signed in never sees this screen, not even for a frame. A successful
 * sign-in has no callback: the session appears in the auth state and the root swaps the screens.
 *
 * Only the address of the form survives the death of the process; the password is never saved (see
 * [SignInViewModel]).
 *
 * [signInStart] makes the first two arguments from the state of a user who is signed out.
 *
 * @param email the address to start with: the one the user last signed in with (`AuthState.SignedOut.lastEmail`),
 * so that after "Сеанс завершён" or a sign-out only the password is left to type; empty if there is none. The
 * password is never given. The address typed by the user is the user's: a new value given here replaces it only when
 * it differs from the one given before (the registration screen sends the address back this way), see
 * [SignInViewModel.onEmailSuggested]
 * @param sessionExpired the server stopped accepting the session the user had: a banner says so
 * ("Сеанс завершён. Войдите снова")
 * @param onRegisterClick "Создать аккаунт": the user wants to register; the address typed so far is passed on
 * @param emailConfirmed the address was confirmed and the app could not sign in by itself: a banner says so
 * ("Почта подтверждена. Войдите"); [sessionExpired] is told first if both are set
 * @param onClose the sign-in was opened over the screens a visitor was browsing: the arrow of the bar returns to them.
 * `null` when there is nothing below the screen and the bar has no arrow
 */
@Composable
fun SignInRoute(
    dependencies: AuthUiDependencies,
    email: String,
    sessionExpired: Boolean,
    onRegisterClick: (email: String) -> Unit,
    modifier: Modifier = Modifier,
    emailConfirmed: Boolean = false,
    onClose: (() -> Unit)? = null,
) {
    val viewModel = viewModel {
        SignInViewModel(
            initialEmail = email,
            signIn = dependencies.signIn,
            savedState = createSavedStateHandle(),
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(email) { viewModel.onEmailSuggested(email) }

    SignInScreen(
        state = state,
        sessionExpired = sessionExpired,
        emailConfirmed = emailConfirmed,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSignInClick = viewModel::onSignInClick,
        onRegisterClick = { onRegisterClick(viewModel.uiState.value.email) },
        modifier = modifier,
        onClose = onClose,
    )
}

@Composable
internal fun SignInScreen(
    state: SignInUiState,
    sessionExpired: Boolean,
    emailConfirmed: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignInClick: () -> Unit,
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    // Opened over the browsing of a visitor, the screen has an arrow back to it ([onClose]); with nothing below it,
    // there is nowhere to go back to and the bar has no arrow.
    AuthFormScreen(
        topBar = { AppTopBar(title = stringResource(Res.string.auth_sign_in_title), onNavigateBack = onClose) },
        modifier = modifier,
    ) {
        FormGroup(FormMetrics.PartGap) {
            when (signInBanner(sessionExpired, emailConfirmed)) {
                SignInBanner.SessionEnded ->
                    InfoBanner(stringResource(Res.string.auth_session_expired), icon = AppIcons.Clock)
                SignInBanner.EmailConfirmed -> InfoBanner(stringResource(Res.string.auth_email_confirmed))
                null -> Unit
            }
            AppTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = stringResource(Res.string.auth_email_label),
                placeholder = stringResource(Res.string.auth_email_hint),
                keyboardOptions = EmailKeyboardOptions,
                errorText = state.emailMessage?.text(),
            )
            AppPasswordField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(Res.string.auth_password_label),
                errorText = state.passwordMessage?.text(),
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { onSignInClick() }),
            )
        }
        FormGroup(FormMetrics.ButtonGap) {
            FormButton(
                text = stringResource(Res.string.auth_sign_in),
                onClick = onSignInClick,
                modifier = Modifier.fillMaxWidth(),
                loading = state.isSubmitting,
            )
            state.buttonMessage?.let { FormErrorText(it.text()) }
            FormButton(
                text = stringResource(Res.string.auth_create_account),
                onClick = onRegisterClick,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.Text,
            )
        }
        SecurityNote(stringResource(Res.string.auth_secure_note))
    }
}
