package org.matchlab.academic_net.feature.auth.ui

import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.auth.domain.CredentialsError

/**
 * Why an action of the sign-in screens did not get the user further, as far as the screens tell the cases
 * apart. The view models report one of these and never a text: the words depend on the language and are chosen
 * by the screen ([text]); the table of what the user reads for each answer of the server is in
 * `docs/gateway-integration.md`.
 *
 * A form that is not filled in properly is not a problem of this kind: it is a set of
 * [org.matchlab.academic_net.feature.auth.domain.CredentialsError] in the state of the screen, one per field.
 */
sealed interface AuthProblem {
    /** The address or the password is wrong (the sign-in form); the address may have no account at all. */
    data object WrongCredentials : AuthProblem

    /** The server refuses the account: the address is not confirmed or the account is disabled. */
    data object AccountUnavailable : AuthProblem

    /** "I've confirmed" was refused: the credentials are right, so the link of the letter was not opened yet. */
    data object NotConfirmedYet : AuthProblem

    /** The domain of the address does not belong to an organization that may register or be confirmed. */
    data object DomainNotAllowed : AuthProblem

    /** The address has an account already (the registration was answered with 409). */
    data object AlreadyRegistered : AuthProblem

    /** The server could not send the letter: its mail service is not available (503 of the registration). */
    data object LetterNotSent : AuthProblem

    /** The text of the link field holds no token, or the server does not know the token or it was used already. */
    data object LinkInvalid : AuthProblem

    /** The link lives 30 minutes and they are over. */
    data object LinkExpired : AuthProblem

    /** Nothing was pasted into the link field. */
    data object LinkEmpty : AuthProblem

    /**
     * The call did not get an answer that has a meaning of its own: there is no network, the server did not
     * answer in time or is down, or the answer is not what the API promises. The screen words it by the kind
     * of [cause] (`failureText`).
     */
    class CallFailed(val cause: Throwable) : AuthProblem {
        // The cause may carry text of its own; a log line gets only its kind.
        override fun toString(): String = "CallFailed(${cause::class.simpleName})"
    }
}

/**
 * What a place of a form tells the user (under a field, or under a button), before it is put into words: a problem
 * of the form itself ([Field]) or an answer of the server and the network ([Problem]). The states of the screens
 * decide which place tells what, so that the decision can be tested; the screens only turn the message into a text
 * ([text]).
 */
internal sealed interface FormMessage {
    /** The form is not filled in properly: [error] belongs to the field the message stands under. */
    data class Field(val error: CredentialsError) : FormMessage

    /** The call was made and did not get the user further, or was not made at all. */
    data class Problem(val problem: AuthProblem) : FormMessage
}

/**
 * Whether the problem is the address's, told under the address field ("не из вуза", "аккаунт уже есть"), and not
 * what came of a button, told under the button.
 */
internal val AuthProblem.isAboutEmail: Boolean
    get() = this == AuthProblem.DomainNotAllowed || this == AuthProblem.AlreadyRegistered

/** A failed call as a problem. */
internal fun Throwable.asProblem(): AuthProblem = AuthProblem.CallFailed(this)

/** The HTTP status of the answer to a registration that means "the mail service is down". */
private const val MailServiceUnavailable = 503

/**
 * A failure of a call that sends the letter (the registration, "send the letter again") as a problem: a 503 is
 * the mail service, which the user is told about in words of its own ("Не удалось отправить письмо"), the rest
 * are told as any failed call is.
 */
internal fun Throwable.asLetterProblem(): AuthProblem =
    if (this is RemoteFailure.Http && status == MailServiceUnavailable) AuthProblem.LetterNotSent else asProblem()

/** The fields of the forms that a [CredentialsError] belongs to. */
internal enum class CredentialsField { Email, Password, RepeatedPassword }

/** The field that [this] error is shown under. */
internal fun CredentialsError.field(): CredentialsField = when (this) {
    CredentialsError.EmailEmpty, CredentialsError.EmailMalformed -> CredentialsField.Email
    CredentialsError.PasswordEmpty, CredentialsError.PasswordTooShort, CredentialsError.PasswordTooLong ->
        CredentialsField.Password
    CredentialsError.PasswordsDiffer -> CredentialsField.RepeatedPassword
}

/** The errors of [this] set that are not shown under [field]: what is left after the user edits that field. */
internal fun Set<CredentialsError>.without(field: CredentialsField): Set<CredentialsError> =
    filterNot { it.field() == field }.toSet()

/** The first error of [this] set that belongs to [field], or `null`: a field shows one line at a time. */
internal fun Set<CredentialsError>.of(field: CredentialsField): CredentialsError? = firstOrNull { it.field() == field }
