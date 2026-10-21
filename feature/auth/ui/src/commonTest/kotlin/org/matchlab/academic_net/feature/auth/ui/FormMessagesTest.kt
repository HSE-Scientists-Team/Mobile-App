package org.matchlab.academic_net.feature.auth.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.auth.domain.CredentialsError

/**
 * Which place of which screen tells what: the rule of the screens that the design draws in its frames (the error of
 * a field under the field, what has no field under the button), as the states of the screens give it. The screens
 * only put the messages into words.
 */
class FormMessagesTest {
    private fun field(error: CredentialsError) = FormMessage.Field(error)

    private fun problem(problem: AuthProblem) = FormMessage.Problem(problem)

    private val noConnection = AuthProblem.CallFailed(RemoteFailure.NoConnection())

    // Sign in (frames 19, 19b)

    @Test
    fun aFreshSignInFormTellsNothing() {
        val state = SignInUiState()

        assertNull(state.emailMessage)
        assertNull(state.passwordMessage)
        assertNull(state.buttonMessage)
    }

    @Test
    fun signInProblemsOfTheAddressStandUnderTheAddress() {
        listOf(CredentialsError.EmailEmpty, CredentialsError.EmailMalformed).forEach { error ->
            val state = SignInUiState(errors = setOf(error))

            assertEquals(field(error), state.emailMessage)
            assertNull(state.passwordMessage)
            assertNull(state.buttonMessage)
        }
    }

    @Test
    fun anEmptyPasswordStandsUnderThePassword() {
        val state = SignInUiState(errors = setOf(CredentialsError.PasswordEmpty))

        assertEquals(field(CredentialsError.PasswordEmpty), state.passwordMessage)
        assertNull(state.emailMessage)
        assertNull(state.buttonMessage)
    }

    @Test
    fun bothProblemsOfTheFormAreToldAtOnceEachUnderItsField() {
        val state = SignInUiState(errors = setOf(CredentialsError.EmailEmpty, CredentialsError.PasswordEmpty))

        assertEquals(field(CredentialsError.EmailEmpty), state.emailMessage)
        assertEquals(field(CredentialsError.PasswordEmpty), state.passwordMessage)
    }

    @Test
    fun wrongCredentialsStandUnderThePasswordAndNotUnderTheButton() {
        // Frame 19b: the red box and the line "Неверная почта или пароль" under the password.
        val state = SignInUiState(problem = AuthProblem.WrongCredentials)

        assertEquals(problem(AuthProblem.WrongCredentials), state.passwordMessage)
        assertNull(state.buttonMessage)
        assertNull(state.emailMessage)
    }

    @Test
    fun aRefusedAccountAndAFailedCallStandUnderTheButton() {
        listOf(AuthProblem.AccountUnavailable, noConnection, AuthProblem.CallFailed(RemoteFailure.Timeout())).forEach {
            val state = SignInUiState(problem = it)

            assertEquals(problem(it), state.buttonMessage)
            // They are not about the password: its box stays as it is.
            assertNull(state.passwordMessage)
            assertNull(state.emailMessage)
        }
    }

    // Registration (frames 20, 20b, 20c, 20d)

    @Test
    fun aFreshRegistrationFormTellsNothingAndOffersNoWayOut() {
        val state = RegisterUiState()

        assertNull(state.emailMessage)
        assertNull(state.passwordMessage)
        assertNull(state.repeatedPasswordMessage)
        assertNull(state.buttonMessage)
        assertFalse(state.offersSignIn)
    }

    @Test
    fun theProblemsOfThePasswordsStandUnderTheirOwnFields() {
        // Frame 20d: "От 8 символов" under the password and "Пароли не совпадают" under the repeated one.
        val state = RegisterUiState(errors = setOf(CredentialsError.PasswordTooShort, CredentialsError.PasswordsDiffer))

        assertEquals(field(CredentialsError.PasswordTooShort), state.passwordMessage)
        assertEquals(field(CredentialsError.PasswordsDiffer), state.repeatedPasswordMessage)
        assertNull(state.emailMessage)
        assertNull(state.buttonMessage)
    }

    @Test
    fun everyProblemOfAPasswordStandsUnderThePassword() {
        listOf(
            CredentialsError.PasswordEmpty,
            CredentialsError.PasswordTooShort,
            CredentialsError.PasswordTooLong,
        ).forEach { error ->
            val state = RegisterUiState(errors = setOf(error))

            assertEquals(field(error), state.passwordMessage)
            assertNull(state.repeatedPasswordMessage)
        }
    }

    @Test
    fun aDomainThatCannotRegisterIsToldUnderTheAddressWithoutAWayOut() {
        // Frame 20b.
        val state = RegisterUiState(problem = AuthProblem.DomainNotAllowed)

        assertEquals(problem(AuthProblem.DomainNotAllowed), state.emailMessage)
        assertFalse(state.offersSignIn)
        assertNull(state.buttonMessage)
    }

    @Test
    fun anAddressWithAnAccountIsToldUnderTheAddressWithTheWayToSignIn() {
        // Frame 20c: the line and "Войти" at its end.
        val state = RegisterUiState(problem = AuthProblem.AlreadyRegistered)

        assertEquals(problem(AuthProblem.AlreadyRegistered), state.emailMessage)
        assertTrue(state.offersSignIn)
        assertNull(state.buttonMessage)
    }

    @Test
    fun theProblemOfTheFormAddressWinsOverTheRefusalOfTheServer() {
        val state = RegisterUiState(
            errors = setOf(CredentialsError.EmailMalformed),
            problem = AuthProblem.AlreadyRegistered,
        )

        assertEquals(field(CredentialsError.EmailMalformed), state.emailMessage)
    }

    @Test
    fun theLetterThatWasNotSentAndAFailedCallStandUnderTheButton() {
        listOf(AuthProblem.LetterNotSent, noConnection).forEach {
            val state = RegisterUiState(problem = it)

            assertEquals(problem(it), state.buttonMessage)
            assertNull(state.emailMessage)
            assertFalse(state.offersSignIn)
        }
    }

    // Confirmation (frames 21 to 21e)

    @Test
    fun theLetterCanBeSentAgainOnlyWhenThePauseIsOver() {
        assertTrue(ConfirmUiState(resendInSeconds = 45).isResendWaiting)
        assertTrue(ConfirmUiState(resendInSeconds = 1).isResendWaiting)
        assertFalse(ConfirmUiState(resendInSeconds = 0).isResendWaiting)
        assertFalse(ConfirmUiState().isResendWaiting)
    }

    // The note of the sign-in screen (frames 19, 19c)

    @Test
    fun noFlagsNoNote() {
        assertNull(signInBanner(sessionExpired = false, emailConfirmed = false))
    }

    @Test
    fun anEndedSessionIsToldWithTheNoteOfThatSession() {
        assertEquals(SignInBanner.SessionEnded, signInBanner(sessionExpired = true, emailConfirmed = false))
    }

    @Test
    fun aConfirmedAddressIsToldWithItsOwnNote() {
        assertEquals(SignInBanner.EmailConfirmed, signInBanner(sessionExpired = false, emailConfirmed = true))
    }

    @Test
    fun theEndOfTheSessionComesBeforeTheConfirmedAddress() {
        assertEquals(SignInBanner.SessionEnded, signInBanner(sessionExpired = true, emailConfirmed = true))
    }
}
