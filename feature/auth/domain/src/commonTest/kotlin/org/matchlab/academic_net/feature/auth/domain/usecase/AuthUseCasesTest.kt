package org.matchlab.academic_net.feature.auth.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.ConfirmationInputError
import org.matchlab.academic_net.feature.auth.domain.ConfirmationOutcome
import org.matchlab.academic_net.feature.auth.domain.Credentials
import org.matchlab.academic_net.feature.auth.domain.CredentialsError
import org.matchlab.academic_net.feature.auth.domain.FakeAuthRepository
import org.matchlab.academic_net.feature.auth.domain.Password
import org.matchlab.academic_net.feature.auth.domain.PendingRegistration
import org.matchlab.academic_net.feature.auth.domain.RegistrationOutcome
import org.matchlab.academic_net.feature.auth.domain.SignInOutcome
import org.matchlab.academic_net.feature.auth.domain.TestPending

class AuthUseCasesTest {
    private val repository = FakeAuthRepository()

    private val signIn = SignInUseCase(repository)
    private val register = RegisterUseCase(repository)
    private val confirmEmail = ConfirmEmailUseCase(repository)

    private fun <E> SubmitResult<*, E>.invalid(): Set<E> = assertIs<SubmitResult.Invalid<E>>(this).errors

    // ---- sign in ----

    @Test
    fun signInWithoutAddressOrPasswordDoesNotReachTheServer() = runTest {
        assertEquals(setOf(CredentialsError.EmailEmpty, CredentialsError.PasswordEmpty), signIn("  ", "").invalid())

        assertEquals(emptyList(), repository.signIns)
    }

    @Test
    fun signInWithMalformedAddressDoesNotReachTheServer() = runTest {
        assertEquals(setOf(CredentialsError.EmailMalformed), signIn("student.hse.ru", "hunter2-hunter2").invalid())

        assertEquals(emptyList(), repository.signIns)
    }

    @Test
    fun signInWithEmptyPasswordDoesNotReachTheServer() = runTest {
        assertEquals(setOf(CredentialsError.PasswordEmpty), signIn("student@hse.ru", "").invalid())

        assertEquals(emptyList(), repository.signIns)
    }

    @Test
    fun signInDoesNotApplyTheLengthRulesOfARegistration() = runTest {
        // The account may have been made under other rules: the server is the judge of a wrong password.
        signIn("student@hse.ru", "short")
        signIn("student@hse.ru", "x".repeat(200))

        assertEquals(listOf("short", "x".repeat(200)), repository.signIns.map { it.password.value })
    }

    @Test
    fun signInPassesTheNormalizedAddressAndThePasswordAsTyped() = runTest {
        val result = signIn("  Student@HSE.ru ", "  Hunter2 hunter2  ")

        assertIs<SubmitResult.Success<SignInOutcome>>(result)
        assertEquals(
            listOf(Credentials("student@hse.ru", Password("  Hunter2 hunter2  "))),
            repository.signIns,
        )
    }

    @Test
    fun signInPasswordOfSpacesIsAPassword() = runTest {
        signIn("student@hse.ru", "   ")

        assertEquals(listOf("   "), repository.signIns.map { it.password.value })
    }

    @Test
    fun signInOutcomesAreNotErrors() = runTest {
        for (outcome in listOf(
            SignInOutcome.SignedIn,
            SignInOutcome.WrongCredentials,
            SignInOutcome.NotConfirmedYet,
            SignInOutcome.AccountUnavailable,
        )) {
            repository.signInOutcome = outcome

            val result = signIn("student@hse.ru", "hunter2-hunter2")

            assertSame(outcome, assertIs<SubmitResult.Success<SignInOutcome>>(result).value)
        }
    }

    @Test
    fun signInFailureIsFailure() = runTest {
        val error = RemoteFailure.NoConnection()
        repository.failure = error

        val result = signIn("student@hse.ru", "hunter2-hunter2")

        assertSame(error, assertIs<SubmitResult.Failure>(result).cause)
    }

    @Test
    fun signInCancellationIsNotSwallowed() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { signIn("student@hse.ru", "hunter2-hunter2") }
    }

    // ---- register ----

    @Test
    fun registrationWithAnEmptyFormReportsAddressAndPassword() = runTest {
        val errors = register("", "", "").invalid()

        assertEquals(setOf(CredentialsError.EmailEmpty, CredentialsError.PasswordEmpty), errors)
        assertEquals(emptyList(), repository.registered)
    }

    @Test
    fun registrationWithMalformedAddressDoesNotReachTheServer() = runTest {
        assertEquals(
            setOf(CredentialsError.EmailMalformed),
            register("student@hse", "hunter2-hunter2", "hunter2-hunter2").invalid(),
        )

        assertEquals(emptyList(), repository.registered)
    }

    @Test
    fun registrationWithShortPasswordDoesNotReachTheServer() = runTest {
        assertEquals(
            setOf(CredentialsError.PasswordTooShort),
            register("student@hse.ru", "1234567", "1234567").invalid(),
        )

        assertEquals(emptyList(), repository.registered)
    }

    @Test
    fun registrationWithLongPasswordDoesNotReachTheServer() = runTest {
        val password = "a".repeat(73)

        assertEquals(
            setOf(CredentialsError.PasswordTooLong),
            register("student@hse.ru", password, password).invalid(),
        )

        assertEquals(emptyList(), repository.registered)
    }

    @Test
    fun passwordLengthIsCountedInBytesNotCharacters() = runTest {
        // 37 Cyrillic letters are 37 characters but 74 bytes.
        val password = "ж".repeat(37)

        assertEquals(
            setOf(CredentialsError.PasswordTooLong),
            register("student@hse.ru", password, password).invalid(),
        )
        // Four letters are only four characters, but eight bytes.
        assertIs<SubmitResult.Success<RegistrationOutcome>>(register("student@hse.ru", "жуки", "жуки"))
    }

    @Test
    fun registrationWithDifferentPasswordsDoesNotReachTheServer() = runTest {
        assertEquals(
            setOf(CredentialsError.PasswordsDiffer),
            register("student@hse.ru", "hunter2-hunter2", "hunter2-hunter3").invalid(),
        )

        assertEquals(emptyList(), repository.registered)
    }

    @Test
    fun repeatedPasswordIsComparedAsTypedNotTrimmed() = runTest {
        assertEquals(
            setOf(CredentialsError.PasswordsDiffer),
            register("student@hse.ru", "hunter2-hunter2", "hunter2-hunter2 ").invalid(),
        )
        assertEquals(
            setOf(CredentialsError.PasswordsDiffer),
            register("student@hse.ru", "hunter2-hunter2", "Hunter2-hunter2").invalid(),
        )
    }

    @Test
    fun registrationReportsEveryProblemAtOnce() = runTest {
        val errors = register("nobody", "abc", "abd").invalid()

        assertEquals(
            setOf(CredentialsError.EmailMalformed, CredentialsError.PasswordTooShort, CredentialsError.PasswordsDiffer),
            errors,
        )
    }

    @Test
    fun emptyPasswordTwiceIsOnlyEmptyNotDifferent() = runTest {
        assertEquals(setOf(CredentialsError.PasswordEmpty), register("student@hse.ru", "", "").invalid())
    }

    @Test
    fun emptyRepeatedPasswordIsDifferent() = runTest {
        val errors = register("student@hse.ru", "hunter2-hunter2", "").invalid()

        assertEquals(setOf(CredentialsError.PasswordsDiffer), errors)
    }

    @Test
    fun registrationPassesTheNormalizedAddressAndThePasswordAsTyped() = runTest {
        val result = register(" Student@HSE.ru", " pass word ", " pass word ")

        assertIs<SubmitResult.Success<RegistrationOutcome>>(result)
        assertEquals(listOf(Credentials("student@hse.ru", Password(" pass word "))), repository.registered)
    }

    @Test
    fun registrationOutcomesAreNotErrors() = runTest {
        for (outcome in listOf(
            RegistrationOutcome.LetterSent(TestPending),
            RegistrationOutcome.DomainNotAllowed,
            RegistrationOutcome.AlreadyRegistered,
        )) {
            repository.registrationOutcome = outcome

            val result = register("student@hse.ru", "hunter2-hunter2", "hunter2-hunter2")

            assertSame(outcome, assertIs<SubmitResult.Success<RegistrationOutcome>>(result).value)
        }
    }

    @Test
    fun registrationFailureIsFailure() = runTest {
        val error = RemoteFailure.Http(503)
        repository.failure = error

        val result = register("student@hse.ru", "hunter2-hunter2", "hunter2-hunter2")

        assertSame(error, assertIs<SubmitResult.Failure>(result).cause)
    }

    @Test
    fun registrationCancellationIsNotSwallowed() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { register("student@hse.ru", "hunter2-hunter2", "hunter2-hunter2") }
    }

    // ---- confirm ----

    @Test
    fun emptyLinkDoesNotReachTheServer() = runTest {
        for (text in listOf("", "   ", "\n\t ")) {
            assertEquals(setOf(ConfirmationInputError.Empty), confirmEmail("student@hse.ru", text).invalid())
        }

        assertEquals(emptyList(), repository.confirmations)
    }

    @Test
    fun confirmationPassesTheNormalizedAddressAndTheTextAsPasted() = runTest {
        val result = confirmEmail(" Student@HSE.ru ", " https://matchlab.space/confirm?token=abc \n")

        assertIs<SubmitResult.Success<ConfirmationOutcome>>(result)
        assertEquals(
            listOf("student@hse.ru" to " https://matchlab.space/confirm?token=abc \n"),
            repository.confirmations,
        )
    }

    @Test
    fun whatTheServerSaysAboutTheLinkIsNotAnError() = runTest {
        for (outcome in listOf(
            ConfirmationOutcome.Confirmed(signedIn = true),
            ConfirmationOutcome.Confirmed(signedIn = false),
            ConfirmationOutcome.InvalidLink,
            ConfirmationOutcome.LinkExpired,
            ConfirmationOutcome.DomainNotAllowed,
        )) {
            repository.confirmationOutcome = outcome

            val result = confirmEmail("student@hse.ru", "abc")

            assertEquals(outcome, assertIs<SubmitResult.Success<ConfirmationOutcome>>(result).value)
        }
    }

    @Test
    fun confirmationFailureIsFailure() = runTest {
        val error = RemoteFailure.Timeout()
        repository.failure = error

        val result = confirmEmail("student@hse.ru", "abc")

        assertSame(error, assertIs<SubmitResult.Failure>(result).cause)
    }

    @Test
    fun confirmationCancellationIsNotSwallowed() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { confirmEmail("student@hse.ru", "abc") }
    }

    // ---- "I've confirmed" and "send the letter again" ----

    @Test
    fun signInPendingAsksForTheNormalizedAddress() = runTest {
        repository.signInPendingOutcome = SignInOutcome.NotConfirmedYet

        val result = SignInPendingUseCase(repository)(" Student@HSE.ru ")

        assertEquals(SignInOutcome.NotConfirmedYet, result.getOrThrow())
        assertEquals(listOf("student@hse.ru"), repository.pendingSignIns)
    }

    @Test
    fun signInPendingWithoutAPasswordIsNull() = runTest {
        repository.signInPendingOutcome = null

        val result = SignInPendingUseCase(repository)("student@hse.ru")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun signInPendingFailureIsAFailedResult() = runTest {
        val error = RemoteFailure.NoConnection()
        repository.failure = error

        assertSame(error, SignInPendingUseCase(repository)("student@hse.ru").exceptionOrNull())
    }

    @Test
    fun signInPendingCancellationIsNotSwallowed() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { SignInPendingUseCase(repository)("student@hse.ru") }
    }

    @Test
    fun resendAsksForTheNormalizedAddress() = runTest {
        repository.resendOutcome = RegistrationOutcome.AlreadyRegistered

        val result = ResendLetterUseCase(repository)(" Student@HSE.ru ")

        assertEquals(RegistrationOutcome.AlreadyRegistered, result.getOrThrow())
        assertEquals(listOf("student@hse.ru"), repository.resends)
    }

    @Test
    fun resendWithoutAPasswordIsNull() = runTest {
        repository.resendOutcome = null

        val result = ResendLetterUseCase(repository)("student@hse.ru")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun resendFailureIsAFailedResult() = runTest {
        val error = RemoteFailure.Http(503)
        repository.failure = error

        assertSame(error, ResendLetterUseCase(repository)("student@hse.ru").exceptionOrNull())
    }

    @Test
    fun resendCancellationIsNotSwallowed() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { ResendLetterUseCase(repository)("student@hse.ru") }
    }

    // ---- the pending registration, restore, sign out ----

    @Test
    fun pendingRegistrationAsksForTheNormalizedAddress() {
        val pending = PendingRegistration("student@hse.ru", 30.seconds, hasPassword = true)
        repository.pending = pending

        assertEquals(pending, GetPendingRegistrationUseCase(repository)(" Student@HSE.ru "))
        assertEquals(listOf("student@hse.ru"), repository.pendingLookups)
    }

    @Test
    fun noPendingRegistrationIsNull() {
        assertNull(GetPendingRegistrationUseCase(repository)("student@hse.ru"))
    }

    @Test
    fun restoreAndSignOutGoToTheRepository() = runTest {
        RestoreSessionUseCase(repository)()
        assertEquals(1, repository.restoreCalls)
        assertEquals(AuthState.SignedOut(), ObserveAuthStateUseCase(repository)().value)

        SignOutUseCase(repository)()
        assertEquals(1, repository.signOutCalls)
    }

    @Test
    fun observingGivesTheStateOfTheRepository() {
        assertSame(repository.authState, ObserveAuthStateUseCase(repository)())
        assertEquals(AuthState.Restoring, ObserveAuthStateUseCase(repository)().value)
    }
}
