package org.matchlab.academic_net.feature.auth.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.feature.auth.domain.CredentialsError
import org.matchlab.academic_net.feature.auth.ui.generated.resources.Res
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_account_exists
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_account_unavailable
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_domain_not_allowed
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_email_empty
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_email_malformed
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_letter_not_sent
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_link_empty
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_link_expired
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_link_invalid
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_not_confirmed_yet
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_password_empty
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_password_long
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_password_short
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_passwords_differ
import org.matchlab.academic_net.feature.auth.ui.generated.resources.auth_error_wrong_credentials

/**
 * What the user reads for [this] problem, in the language of the app. A failed call
 * ([AuthProblem.CallFailed]) is told by [failureText]: "Нет связи с сервером", "Сервер не ответил", "Сервис
 * временно недоступен", "Что-то пошло не так". The text the server sent with an error is never shown.
 */
@Composable
internal fun AuthProblem.text(): String = when (this) {
    AuthProblem.WrongCredentials -> stringResource(Res.string.auth_error_wrong_credentials)
    AuthProblem.AccountUnavailable -> stringResource(Res.string.auth_error_account_unavailable)
    AuthProblem.NotConfirmedYet -> stringResource(Res.string.auth_error_not_confirmed_yet)
    AuthProblem.DomainNotAllowed -> stringResource(Res.string.auth_error_domain_not_allowed)
    AuthProblem.AlreadyRegistered -> stringResource(Res.string.auth_error_account_exists)
    AuthProblem.LetterNotSent -> stringResource(Res.string.auth_error_letter_not_sent)
    AuthProblem.LinkInvalid -> stringResource(Res.string.auth_error_link_invalid)
    AuthProblem.LinkExpired -> stringResource(Res.string.auth_error_link_expired)
    AuthProblem.LinkEmpty -> stringResource(Res.string.auth_error_link_empty)
    is AuthProblem.CallFailed -> failureText(cause)
}

/** What the user reads under the field that [this] error belongs to, in the language of the app. */
@Composable
internal fun CredentialsError.text(): String = when (this) {
    CredentialsError.EmailEmpty -> stringResource(Res.string.auth_error_email_empty)
    CredentialsError.EmailMalformed -> stringResource(Res.string.auth_error_email_malformed)
    CredentialsError.PasswordEmpty -> stringResource(Res.string.auth_error_password_empty)
    CredentialsError.PasswordTooShort -> stringResource(Res.string.auth_error_password_short)
    CredentialsError.PasswordTooLong -> stringResource(Res.string.auth_error_password_long)
    CredentialsError.PasswordsDiffer -> stringResource(Res.string.auth_error_passwords_differ)
}

/** What the user reads for [this] message of a form, in the language of the app. */
@Composable
internal fun FormMessage.text(): String = when (this) {
    is FormMessage.Field -> error.text()
    is FormMessage.Problem -> problem.text()
}
