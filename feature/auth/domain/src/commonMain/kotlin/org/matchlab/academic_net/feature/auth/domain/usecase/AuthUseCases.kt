package org.matchlab.academic_net.feature.auth.domain.usecase

import kotlinx.coroutines.flow.StateFlow
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.AuthRepository
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.ConfirmationInputError
import org.matchlab.academic_net.feature.auth.domain.ConfirmationOutcome
import org.matchlab.academic_net.feature.auth.domain.Credentials
import org.matchlab.academic_net.feature.auth.domain.CredentialsError
import org.matchlab.academic_net.feature.auth.domain.EmailError
import org.matchlab.academic_net.feature.auth.domain.EmailRules
import org.matchlab.academic_net.feature.auth.domain.Password
import org.matchlab.academic_net.feature.auth.domain.PasswordRules
import org.matchlab.academic_net.feature.auth.domain.PendingRegistration
import org.matchlab.academic_net.feature.auth.domain.RegistrationOutcome
import org.matchlab.academic_net.feature.auth.domain.SignInOutcome
import org.matchlab.academic_net.utils.suspendRunCatching

/** Whether the user is signed in: the app's root decides which graph to show by it. */
class ObserveAuthStateUseCase(private val repository: AuthRepository) {
    operator fun invoke(): StateFlow<AuthState> = repository.authState
}

/** Reads the saved session on the app's start. */
class RestoreSessionUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.restoreSession()
}

/**
 * "Войти": signs in with the e-mail and the password.
 *
 * The form is checked here, and only for what cannot be right: an address that fails [EmailRules]
 * and an empty password. A form with a problem does not reach the server ([SubmitResult.Invalid]); the
 * verdict of the server is the [SignInOutcome]; a failure of the call is [SubmitResult.Failure].
 *
 * **A password the schema cannot hold is never right.** The schema `Credentials` is one for the registration
 * and for the sign-in (the password is [PasswordRules.MinBytes] to [PasswordRules.MaxBytes] bytes of UTF-8), so
 * no account has a password outside of it. Such a password gets the verdict [SignInOutcome.WrongCredentials] at
 * once, without a request: a request that the schema does not allow may be refused as a bad one (400), which the
 * user would read as "something went wrong" instead of "wrong address or password". (It is not a problem of the
 * form: the form of a sign-in does not name the rules of a registration.)
 */
class SignInUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): SubmitResult<SignInOutcome, CredentialsError> {
        val normalized = EmailRules.normalize(email)
        val errors = buildSet<CredentialsError> {
            EmailRules.validate(normalized)?.let { add(it.toCredentialsError()) }
            if (password.isEmpty()) add(CredentialsError.PasswordEmpty)
        }
        if (errors.isNotEmpty()) return SubmitResult.Invalid(errors)

        // The password is not empty here, so what is left of the rules is its length.
        if (PasswordRules.validate(password) != null) return SubmitResult.Success(SignInOutcome.WrongCredentials)

        return suspendRunCatching { repository.signIn(Credentials(normalized, Password(password))) }.toSubmitResult()
    }
}

/**
 * "Зарегистрироваться": creates a registration, the server sends the letter with the link.
 *
 * Every problem of the form is reported at once: the address ([EmailRules]), the password
 * ([PasswordRules]) and the repeated password, which must be the same as the password. Nothing
 * reaches the server until the form is right ([SubmitResult.Invalid]). Which domains may register
 * is decided by the server ([RegistrationOutcome.DomainNotAllowed]).
 */
class RegisterUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(
        email: String,
        password: String,
        repeatedPassword: String,
    ): SubmitResult<RegistrationOutcome, CredentialsError> {
        val normalized = EmailRules.normalize(email)
        val errors = buildSet<CredentialsError> {
            EmailRules.validate(normalized)?.let { add(it.toCredentialsError()) }
            PasswordRules.validate(password)?.let { add(it) }
            if (password != repeatedPassword) add(CredentialsError.PasswordsDiffer)
        }
        if (errors.isNotEmpty()) return SubmitResult.Invalid(errors)

        return suspendRunCatching { repository.register(Credentials(normalized, Password(password))) }.toSubmitResult()
    }
}

/**
 * "Подтвердить": confirms the address with the link pasted from the letter (or just its token).
 *
 * Empty text does not reach the server ([SubmitResult.Invalid]). Whether the text holds a token and
 * whether the server knows it is told by the [ConfirmationOutcome]. [email] is the address of the
 * registration; the app signs in by itself with the password it remembers for it.
 */
class ConfirmEmailUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(
        email: String,
        linkOrToken: String,
    ): SubmitResult<ConfirmationOutcome, ConfirmationInputError> {
        if (linkOrToken.isBlank()) return SubmitResult.Invalid(setOf(ConfirmationInputError.Empty))

        return suspendRunCatching { repository.confirmEmail(EmailRules.normalize(email), linkOrToken) }.toSubmitResult()
    }
}

/**
 * "Я подтвердил(а)": signs in with the password remembered for the registration of [email]. The
 * outcome is `null` when the password is not in memory (the app was restarted): the user types it
 * on the sign-in screen. [SignInOutcome.NotConfirmedYet] means that the link was not opened yet.
 */
class SignInPendingUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String): Result<SignInOutcome?> =
        suspendRunCatching { repository.signInPending(EmailRules.normalize(email)) }
}

/**
 * "Отправить письмо ещё раз": registers [email] again with the remembered password; the link of the
 * previous letter stops working. The outcome is `null` when the password is not in memory: the
 * user registers again. [RegistrationOutcome.AlreadyRegistered] means that the address was
 * confirmed meanwhile: [SignInPendingUseCase] follows.
 */
class ResendLetterUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String): Result<RegistrationOutcome?> =
        suspendRunCatching { repository.resendLetter(EmailRules.normalize(email)) }
}

/**
 * The registration of [email] that waits for its link, with the time left until the letter may be sent
 * again; `null` if none.
 */
class GetPendingRegistrationUseCase(private val repository: AuthRepository) {
    operator fun invoke(email: String): PendingRegistration? =
        repository.pendingRegistration(EmailRules.normalize(email))
}

/** "Выйти из аккаунта". */
class SignOutUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.signOut()
}

/** The problem of the address as the problem of the field of the form it was typed in. */
private fun EmailError.toCredentialsError(): CredentialsError = when (this) {
    EmailError.Empty -> CredentialsError.EmailEmpty
    EmailError.Malformed -> CredentialsError.EmailMalformed
}

/** A finished call as the result of a form: its value, or the failure the screen explains. */
private fun <T> Result<T>.toSubmitResult(): SubmitResult<T, Nothing> = fold(
    onSuccess = { SubmitResult.Success(it) },
    onFailure = { SubmitResult.Failure(it) },
)
