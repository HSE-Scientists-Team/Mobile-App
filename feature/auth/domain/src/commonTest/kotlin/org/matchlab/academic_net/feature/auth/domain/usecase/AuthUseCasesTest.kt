package org.matchlab.academic_net.feature.auth.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.CodeError
import org.matchlab.academic_net.feature.auth.domain.CodeRequestOutcome
import org.matchlab.academic_net.feature.auth.domain.CodeVerificationOutcome
import org.matchlab.academic_net.feature.auth.domain.EmailError
import org.matchlab.academic_net.feature.auth.domain.FakeAuthRepository
import org.matchlab.academic_net.feature.auth.domain.VerificationChallenge

class AuthUseCasesTest {
    private val repository = FakeAuthRepository()

    @Test
    fun invalidEmailDoesNotReachTheServer() = runTest {
        val empty = RequestEmailCodeUseCase(repository)("   ")
        val malformed = RequestEmailCodeUseCase(repository)("student.hse.ru")

        assertEquals(setOf(EmailError.Empty), assertIs<SubmitResult.Invalid<EmailError>>(empty).errors)
        assertEquals(setOf(EmailError.Malformed), assertIs<SubmitResult.Invalid<EmailError>>(malformed).errors)
        assertEquals(emptyList(), repository.requestedEmails)
    }

    @Test
    fun emailIsNormalizedBeforeRequest() = runTest {
        val result = RequestEmailCodeUseCase(repository)("  Student@HSE.ru ")

        assertIs<SubmitResult.Success<CodeRequestOutcome>>(result)
        assertEquals(listOf("student@hse.ru"), repository.requestedEmails)
    }

    @Test
    fun serverVerdictIsPassedAsIs() = runTest {
        repository.requestOutcome = CodeRequestOutcome.UntrustedDomain

        val result = RequestEmailCodeUseCase(repository)("student@gmail.com")

        assertSame(CodeRequestOutcome.UntrustedDomain, assertIs<SubmitResult.Success<CodeRequestOutcome>>(result).value)
    }

    @Test
    fun networkFailureOfRequestIsFailure() = runTest {
        val error = IllegalStateException("no network")
        repository.failure = error

        val result = RequestEmailCodeUseCase(repository)("student@hse.ru")

        assertSame(error, assertIs<SubmitResult.Failure>(result).cause)
    }

    @Test
    fun cancellationOfRequestIsNotSwallowed() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { RequestEmailCodeUseCase(repository)("student@hse.ru") }
    }

    @Test
    fun incompleteCodeDoesNotReachTheServer() = runTest {
        val result = VerifyEmailCodeUseCase(repository)("student@hse.ru", "123")

        assertEquals(setOf(CodeError.Incomplete), assertIs<SubmitResult.Invalid<CodeError>>(result).errors)
        assertEquals(emptyList(), repository.verified)
    }

    @Test
    fun codeAndEmailAreNormalizedBeforeVerification() = runTest {
        val result = VerifyEmailCodeUseCase(repository)(" Student@HSE.ru", "123 456")

        assertIs<SubmitResult.Success<CodeVerificationOutcome>>(result)
        assertEquals(listOf("student@hse.ru" to "123456"), repository.verified)
    }

    @Test
    fun wrongCodeIsAnOutcomeNotAnError() = runTest {
        repository.verifyOutcome = CodeVerificationOutcome.WrongCode(attemptsLeft = 3)

        val result = VerifyEmailCodeUseCase(repository)("student@hse.ru", "000000")

        val outcome = assertIs<SubmitResult.Success<CodeVerificationOutcome>>(result).value
        assertEquals(CodeVerificationOutcome.WrongCode(3), outcome)
    }

    @Test
    fun networkFailureOfVerificationIsFailure() = runTest {
        val error = IllegalStateException("no network")
        repository.failure = error

        val result = VerifyEmailCodeUseCase(repository)("student@hse.ru", "123456")

        assertSame(error, assertIs<SubmitResult.Failure>(result).cause)
    }

    @Test
    fun cancellationOfVerificationIsNotSwallowed() = runTest {
        repository.failure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { VerifyEmailCodeUseCase(repository)("student@hse.ru", "123456") }
    }

    @Test
    fun lastChallengeAsksForTheNormalizedAddress() {
        val challenge = VerificationChallenge("student@hse.ru", 30.seconds)
        repository.challenge = challenge

        assertEquals(challenge, GetLastChallengeUseCase(repository)(" Student@HSE.ru "))
        assertEquals(listOf("student@hse.ru"), repository.challengeLookups)
    }

    @Test
    fun noChallengeIsNull() {
        assertNull(GetLastChallengeUseCase(repository)("student@hse.ru"))
    }

    @Test
    fun restoreAndSignOutGoToTheRepository() = runTest {
        RestoreSessionUseCase(repository)()
        assertEquals(1, repository.restoreCalls)
        assertEquals(AuthState.SignedOut, ObserveAuthStateUseCase(repository)().value)

        SignOutUseCase(repository)()
        assertEquals(1, repository.signOutCalls)
    }
}
