package org.matchlab.academic_net.feature.auth.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.CredentialsError
import org.matchlab.academic_net.feature.auth.domain.SignInOutcome
import org.matchlab.academic_net.feature.auth.domain.usecase.SignInUseCase

/**
 * The sign-in form.
 *
 * Neither text is trimmed or changed here: the use case normalizes the address, and the password is sent as it
 * was typed. The state is a secret in part: [toString] says only whether there is text, not what it is.
 *
 * @property email what the address field shows, as typed
 * @property password what the password field holds, as typed; it lives in this state and nowhere else (not in
 * the saved state of the screen, see [SignInViewModel])
 * @property errors what is wrong with the form; it did not reach the server then. Only the problems that cannot
 * be right are checked: an address that is not an address and an empty password.
 * @property problem why the server did not let the user in ([AuthProblem.WrongCredentials],
 * [AuthProblem.AccountUnavailable]) or why it was not asked ([AuthProblem.CallFailed]); `null` while there is none
 * @property isSubmitting the request is on its way, or it has signed the user in and the screen is about to be
 * replaced: the form takes no more requests in both cases
 */
@Immutable
data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val errors: Set<CredentialsError> = emptySet(),
    val problem: AuthProblem? = null,
    val isSubmitting: Boolean = false,
) {
    /** What the address field tells: the problem of the address, if the form has one. */
    internal val emailMessage: FormMessage?
        get() = errors.of(CredentialsField.Email)?.let { FormMessage.Field(it) }

    /**
     * What the password field tells (the red box and the line under it): the problem of the password, or the refusal
     * of the pair: what is wrong is the address and the password together, and the password is the field that is
     * typed again.
     */
    internal val passwordMessage: FormMessage?
        get() = errors.of(CredentialsField.Password)?.let { FormMessage.Field(it) }
            ?: problem?.takeIf { it == AuthProblem.WrongCredentials }?.let { FormMessage.Problem(it) }

    /** What the line under the button tells: what has no field to stand under (a refused account, a failed call). */
    internal val buttonMessage: FormMessage?
        get() = problem?.takeIf { it != AuthProblem.WrongCredentials }?.let { FormMessage.Problem(it) }

    /** The address and the password are personal data and a secret: logs get neither. */
    override fun toString(): String =
        "SignInUiState(hasEmail=${email.isNotEmpty()}, hasPassword=${password.isNotEmpty()}, errors=$errors, " +
            "problem=$problem, isSubmitting=$isSubmitting)"
}

/**
 * "19 Вход": the address and the password of an account that exists. A successful sign-in does not navigate from
 * here: the session appears in the auth state, and the app's root swaps the screens.
 *
 * **What survives.** The view model outlives a change of the configuration (the form is as it was after a
 * rotation); after the process was killed only the address comes back, from [savedState]. The password is never
 * written to [savedState]: that is saved by the system, to disk too, and a password has no business there. The
 * address is no secret.
 *
 * **One request at a time.** While a request is on its way [onSignInClick] does nothing; the same after the
 * sign-in succeeded, until the app's root has taken the screen away: a second tap in that time would ask the
 * server for a second session.
 *
 * @param initialEmail the address to start with (the one the user last signed in with); an address saved in
 * [savedState] by an earlier incarnation of this screen wins over it
 * @param savedState the saved state of the screen: holds the address only
 */
class SignInViewModel(
    initialEmail: String,
    private val signIn: SignInUseCase,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val state = MutableStateFlow(SignInUiState(email = savedState.get<String>(EmailKey) ?: initialEmail))
    val uiState: StateFlow<SignInUiState> = state.asStateFlow()

    /** The last address the screen was given from outside, to tell a new one from the same one given again. */
    private var suggestedEmail = initialEmail

    /**
     * The screen is given an address from outside (the registration screen sends the user back to sign in with the
     * one they typed): it replaces the text of the field, but only when it is new. The same address given again,
     * as it is each time the screen is composed again, leaves what the user typed since alone; so does an empty one,
     * which is "nothing to suggest".
     */
    fun onEmailSuggested(email: String) {
        if (email == suggestedEmail) return
        suggestedEmail = email
        if (email.isBlank() || email == state.value.email) return
        changeEmail(email)
    }

    fun onEmailChange(email: String) = changeEmail(email)

    fun onPasswordChange(password: String) {
        // A new password makes the answer of the server about the old one stale, and its own errors too.
        state.update {
            it.copy(password = password, errors = it.errors.without(CredentialsField.Password), problem = null)
        }
    }

    /** "Войти" (and the "Done" key of the keyboard). */
    fun onSignInClick() {
        val current = state.value
        if (current.isSubmitting) return
        state.update { it.copy(isSubmitting = true, errors = emptySet(), problem = null) }
        viewModelScope.launch {
            when (val result = signIn(current.email, current.password)) {
                is SubmitResult.Success -> when (result.value) {
                    // The session is in the auth state now and the app's root leaves this screen. The form stays
                    // "submitting" until it does: another tap would ask for another session.
                    SignInOutcome.SignedIn -> Unit
                    // "Not confirmed yet" is the answer to "I've confirmed", never to a sign-in form; if it came, the
                    // user would still have to be told something, and the plain refusal is the closest thing.
                    SignInOutcome.WrongCredentials, SignInOutcome.NotConfirmedYet ->
                        stop { it.copy(problem = AuthProblem.WrongCredentials) }
                    SignInOutcome.AccountUnavailable -> stop { it.copy(problem = AuthProblem.AccountUnavailable) }
                }
                is SubmitResult.Invalid -> stop { it.copy(errors = result.errors) }
                is SubmitResult.Failure -> stop { it.copy(problem = result.cause.asProblem()) }
            }
        }
    }

    private fun changeEmail(email: String) {
        savedState[EmailKey] = email
        state.update {
            it.copy(email = email, errors = it.errors.without(CredentialsField.Email), problem = null)
        }
    }

    /** Ends the request that was on its way and lets the user try again, with [change] made to the state. */
    private fun stop(change: (SignInUiState) -> SignInUiState) {
        state.update { change(it).copy(isSubmitting = false) }
    }

    private companion object {
        /** The only key of the saved state: there is no key for the password. */
        const val EmailKey = "email"
    }
}
