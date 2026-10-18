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
import org.matchlab.academic_net.feature.auth.domain.RegistrationOutcome
import org.matchlab.academic_net.feature.auth.domain.usecase.RegisterUseCase

/**
 * The registration form.
 *
 * Nothing is trimmed or changed here (the use case normalizes the address, and a password is taken as typed).
 * The state is a secret in part: [toString] says only whether there is text, not what it is.
 *
 * @property email what the address field shows, as typed
 * @property password what the password field holds; it lives in this state and nowhere else (not in the saved
 * state of the screen, see [RegisterViewModel])
 * @property repeatedPassword what the field "Повторите пароль" holds
 * @property errors what is wrong with the form, field by field ([CredentialsField]); it did not reach the server
 * then
 * @property problem why the registration did not happen: the address is refused ([AuthProblem.DomainNotAllowed],
 * [AuthProblem.AlreadyRegistered]; shown under the address) or the server was not asked or could not send the
 * letter ([AuthProblem.LetterNotSent], [AuthProblem.CallFailed]; shown under the button); `null` while there is none
 * @property isSubmitting the request is on its way, or it has been answered and the screen is on its way to the
 * next one: the form takes no more requests in both cases
 * @property letterSentTo the address the letter was sent to, as the server has it (an event: the screen leaves
 * for the confirmation of that address and tells [RegisterViewModel.onLetterSentHandled]); `null` otherwise
 */
@Immutable
data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val repeatedPassword: String = "",
    val errors: Set<CredentialsError> = emptySet(),
    val problem: AuthProblem? = null,
    val isSubmitting: Boolean = false,
    val letterSentTo: String? = null,
) {
    /**
     * What the address field tells: the problem of the address that the form has, else the refusal of the address by
     * the server (not of a university, or has an account already).
     */
    internal val emailMessage: FormMessage?
        get() = errors.of(CredentialsField.Email)?.let { FormMessage.Field(it) }
            ?: problem?.takeIf { it.isAboutEmail }?.let { FormMessage.Problem(it) }

    /** Whether the line of the address offers the way out "Войти": the address has an account already. */
    internal val offersSignIn: Boolean
        get() = problem == AuthProblem.AlreadyRegistered

    /** What the password field tells (it replaces the hint "От 8 символов"). */
    internal val passwordMessage: FormMessage?
        get() = errors.of(CredentialsField.Password)?.let { FormMessage.Field(it) }

    /** What the field of the repeated password tells: that the two passwords differ. */
    internal val repeatedPasswordMessage: FormMessage?
        get() = errors.of(CredentialsField.RepeatedPassword)?.let { FormMessage.Field(it) }

    /** What the line under the button tells: what has no field to stand under (a letter not sent, a failed call). */
    internal val buttonMessage: FormMessage?
        get() = problem?.takeUnless { it.isAboutEmail }?.let { FormMessage.Problem(it) }

    /** The address and the passwords are personal data and secrets: logs get none of them. */
    override fun toString(): String =
        "RegisterUiState(hasEmail=${email.isNotEmpty()}, hasPassword=${password.isNotEmpty()}, " +
            "hasRepeatedPassword=${repeatedPassword.isNotEmpty()}, errors=$errors, problem=$problem, " +
            "isSubmitting=$isSubmitting, letterSent=${letterSentTo != null})"
}

/**
 * "20 Регистрация": the address, a password and its repeat (the API has no way to restore a password, so a typo
 * in it would lock the account). The letter with the link is sent; the confirmation of the address is the next
 * screen.
 *
 * **What survives.** As for [SignInViewModel]: the form survives a change of the configuration, and only the
 * address comes back after the process was killed, from [savedState]; neither password is ever written there.
 *
 * **One letter per tap.** A letter replaces the one before it (its link stops working), so the form does not take
 * a second request while one is on its way, nor after it was answered, until the user is on this screen again
 * ([onReturned]): the screen of the confirmation slides in over it, and a tap in that time must not send another
 * letter. When the user comes back with the arrow of the confirmation screen ("Изменить почту") the form is as
 * they left it, ready to send again, probably to another address.
 *
 * @param initialEmail the address to start with (typed on the sign-in screen); an address saved in [savedState]
 * by an earlier incarnation of this screen wins over it
 * @param savedState the saved state of the screen: holds the address only
 */
class RegisterViewModel(
    initialEmail: String,
    private val register: RegisterUseCase,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val state = MutableStateFlow(RegisterUiState(email = savedState.get<String>(EmailKey) ?: initialEmail))
    val uiState: StateFlow<RegisterUiState> = state.asStateFlow()

    /** The last address the screen was given from outside, to tell a new one from the same one given again. */
    private var suggestedEmail = initialEmail

    /** The letter was sent and the screen has gone, or is going, to the confirmation. */
    private var isLeaving = false

    /** See [SignInViewModel.onEmailSuggested]. */
    fun onEmailSuggested(email: String) {
        if (email == suggestedEmail) return
        suggestedEmail = email
        if (email.isBlank() || email == state.value.email) return
        changeEmail(email)
    }

    fun onEmailChange(email: String) = changeEmail(email)

    fun onPasswordChange(password: String) {
        // The error of the repeated field is about the two passwords together, so it is stale as well.
        state.update {
            it.copy(
                password = password,
                errors = it.errors.without(CredentialsField.Password).without(CredentialsField.RepeatedPassword),
                problem = it.problem.keptWhenEditingPassword(),
            )
        }
    }

    fun onRepeatedPasswordChange(password: String) {
        state.update {
            it.copy(
                repeatedPassword = password,
                errors = it.errors.without(CredentialsField.RepeatedPassword),
                problem = it.problem.keptWhenEditingPassword(),
            )
        }
    }

    /** "Зарегистрироваться" (and the "Done" key of the keyboard). */
    fun onRegisterClick() {
        val current = state.value
        if (current.isSubmitting) return
        state.update { it.copy(isSubmitting = true, errors = emptySet(), problem = null) }
        viewModelScope.launch {
            when (val result = register(current.email, current.password, current.repeatedPassword)) {
                is SubmitResult.Success -> when (val outcome = result.value) {
                    is RegistrationOutcome.LetterSent -> {
                        // The form stays "submitting": the screen is on its way to the confirmation.
                        isLeaving = true
                        state.update { it.copy(letterSentTo = outcome.pending.email) }
                    }
                    RegistrationOutcome.DomainNotAllowed -> stop { it.copy(problem = AuthProblem.DomainNotAllowed) }
                    RegistrationOutcome.AlreadyRegistered -> stop { it.copy(problem = AuthProblem.AlreadyRegistered) }
                }
                is SubmitResult.Invalid -> stop { it.copy(errors = result.errors) }
                is SubmitResult.Failure -> stop { it.copy(problem = result.cause.asLetterProblem()) }
            }
        }
    }

    /**
     * The screen has passed [RegisterUiState.letterSentTo] on. Without this the event would stay in the state, and
     * the screen, composed again when the user comes back, would leave for the confirmation once more. The form
     * stays "submitting" (see [onReturned]).
     */
    fun onLetterSentHandled() {
        state.update { it.copy(letterSentTo = null) }
    }

    /**
     * The screen is on top again: the user came back from the confirmation, which is what the arrow "Изменить почту"
     * of that screen and the back gesture do. The form takes requests again. It does nothing until the letter was
     * sent and passed on, so showing the screen for the first time, and again after a rotation, changes nothing.
     */
    fun onReturned() {
        if (!isLeaving || state.value.letterSentTo != null) return
        isLeaving = false
        state.update { it.copy(isSubmitting = false) }
    }

    private fun changeEmail(email: String) {
        savedState[EmailKey] = email
        // A new address makes every answer about the old one stale.
        state.update { it.copy(email = email, errors = it.errors.without(CredentialsField.Email), problem = null) }
    }

    /**
     * Ends the request that was on its way and lets the user try again, with [change] made to the state.
     */
    private fun stop(change: (RegisterUiState) -> RegisterUiState) {
        state.update { change(it).copy(isSubmitting = false) }
    }

    private companion object {
        /** The only key of the saved state: there is no key for the passwords. */
        const val EmailKey = "email"
    }
}

/**
 * What stays told after a password was edited: the refusals of the address ("не из вуза", "аккаунт уже есть") are
 * about the address, so a password does not settle them; a failed call is over with the next edit.
 */
private fun AuthProblem?.keptWhenEditingPassword(): AuthProblem? = takeIf { it?.isAboutEmail == true }
