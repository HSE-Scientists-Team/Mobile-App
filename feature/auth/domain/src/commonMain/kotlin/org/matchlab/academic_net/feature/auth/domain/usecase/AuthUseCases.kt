package org.matchlab.academic_net.feature.auth.domain.usecase

import kotlinx.coroutines.flow.StateFlow
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.AuthRepository
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.CodeError
import org.matchlab.academic_net.feature.auth.domain.CodeRequestOutcome
import org.matchlab.academic_net.feature.auth.domain.CodeVerificationOutcome
import org.matchlab.academic_net.feature.auth.domain.EmailError
import org.matchlab.academic_net.feature.auth.domain.EmailRules
import org.matchlab.academic_net.feature.auth.domain.VerificationChallenge
import org.matchlab.academic_net.feature.auth.domain.VerificationCodeRules
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
 * "Получить код": sends a code to the university e-mail.
 *
 * An address that fails [EmailRules] does not reach the server ([SubmitResult.Invalid]); the
 * registry check is the server's, its verdict is the [CodeRequestOutcome]. A network failure is
 * [SubmitResult.Failure].
 */
class RequestEmailCodeUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String): SubmitResult<CodeRequestOutcome, EmailError> {
        val normalized = EmailRules.normalize(email)
        EmailRules.validate(normalized)?.let { return SubmitResult.Invalid(setOf(it)) }

        return suspendRunCatching { repository.requestCode(normalized) }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}

/**
 * "Подтвердить": checks the code from the e-mail. A code that is not all [VerificationCodeRules.LENGTH]
 * digits does not reach the server ([SubmitResult.Invalid]).
 */
class VerifyEmailCodeUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, code: String): SubmitResult<CodeVerificationOutcome, CodeError> {
        VerificationCodeRules.validate(code)?.let { return SubmitResult.Invalid(setOf(it)) }

        return suspendRunCatching {
            repository.verifyCode(EmailRules.normalize(email), VerificationCodeRules.sanitize(code))
        }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}

/** The code last sent to [email] with the time left until the next one may be requested; `null` if none. */
class GetLastChallengeUseCase(private val repository: AuthRepository) {
    operator fun invoke(email: String): VerificationChallenge? =
        repository.lastChallenge(EmailRules.normalize(email))
}

/** "Выйти из аккаунта". */
class SignOutUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.signOut()
}
