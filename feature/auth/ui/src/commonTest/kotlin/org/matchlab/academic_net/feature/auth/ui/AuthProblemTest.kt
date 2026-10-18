package org.matchlab.academic_net.feature.auth.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.auth.domain.CredentialsError

class AuthProblemTest {
    @Test
    fun everyErrorOfTheFormBelongsToTheFieldItIsTypedIn() {
        assertEquals(CredentialsField.Email, CredentialsError.EmailEmpty.field())
        assertEquals(CredentialsField.Email, CredentialsError.EmailMalformed.field())
        assertEquals(CredentialsField.Password, CredentialsError.PasswordEmpty.field())
        assertEquals(CredentialsField.Password, CredentialsError.PasswordTooShort.field())
        assertEquals(CredentialsField.Password, CredentialsError.PasswordTooLong.field())
        assertEquals(CredentialsField.RepeatedPassword, CredentialsError.PasswordsDiffer.field())
    }

    @Test
    fun withoutAFieldTheSetKeepsTheErrorsOfTheOthers() {
        val errors = setOf(
            CredentialsError.EmailMalformed,
            CredentialsError.PasswordTooShort,
            CredentialsError.PasswordsDiffer,
        )

        assertEquals(
            setOf(CredentialsError.PasswordTooShort, CredentialsError.PasswordsDiffer),
            errors.without(CredentialsField.Email),
        )
        assertEquals(
            setOf(CredentialsError.EmailMalformed, CredentialsError.PasswordsDiffer),
            errors.without(CredentialsField.Password),
        )
        assertEquals(
            setOf(CredentialsError.EmailMalformed, CredentialsError.PasswordTooShort),
            errors.without(CredentialsField.RepeatedPassword),
        )
        assertTrue(emptySet<CredentialsError>().without(CredentialsField.Email).isEmpty())
    }

    @Test
    fun aFieldShowsItsOwnErrorOrNone() {
        val errors = setOf(CredentialsError.PasswordTooLong, CredentialsError.PasswordsDiffer)

        assertNull(errors.of(CredentialsField.Email))
        assertEquals(CredentialsError.PasswordTooLong, errors.of(CredentialsField.Password))
        assertEquals(CredentialsError.PasswordsDiffer, errors.of(CredentialsField.RepeatedPassword))
        assertNull(emptySet<CredentialsError>().of(CredentialsField.Password))
    }

    @Test
    fun aMailServiceThatIsDownIsTheOnlyFailureOfTheLetterWithWordsOfItsOwn() {
        assertEquals(AuthProblem.LetterNotSent, RemoteFailure.Http(503, "smtp").asLetterProblem())

        listOf(
            RemoteFailure.Http(500, null),
            RemoteFailure.Http(502, null),
            RemoteFailure.Http(504, null),
            RemoteFailure.Http(400, null),
            RemoteFailure.Timeout(),
            RemoteFailure.NoConnection(),
            RemoteFailure.Malformed(),
            IllegalStateException("anything else"),
        ).forEach { cause ->
            val problem = assertIs<AuthProblem.CallFailed>(cause.asLetterProblem())
            assertSame(cause, problem.cause)
        }
    }

    @Test
    fun aFailureOfAnyOtherCallKeepsItsCauseEvenWhenItIsA503() {
        val cause = RemoteFailure.Http(503, "unavailable")

        assertSame(cause, assertIs<AuthProblem.CallFailed>(cause.asProblem()).cause)
    }

    @Test
    fun onlyTheRefusalsOfTheAddressAreTheAddressesProblem() {
        assertTrue(AuthProblem.DomainNotAllowed.isAboutEmail)
        assertTrue(AuthProblem.AlreadyRegistered.isAboutEmail)

        listOf(
            AuthProblem.WrongCredentials,
            AuthProblem.AccountUnavailable,
            AuthProblem.NotConfirmedYet,
            AuthProblem.LetterNotSent,
            AuthProblem.LinkInvalid,
            AuthProblem.LinkExpired,
            AuthProblem.LinkEmpty,
            AuthProblem.CallFailed(RemoteFailure.NoConnection()),
        ).forEach { assertFalse(it.isAboutEmail, "$it") }
    }

    @Test
    fun aFailedCallShowsOnlyItsKindInLogs() {
        val problem = AuthProblem.CallFailed(RemoteFailure.Http(503, "secret words of the server"))

        assertEquals("CallFailed(Http)", problem.toString())
    }
}
