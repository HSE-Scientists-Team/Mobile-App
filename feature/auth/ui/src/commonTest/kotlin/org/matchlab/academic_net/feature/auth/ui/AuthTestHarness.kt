package org.matchlab.academic_net.feature.auth.ui

import kotlin.time.Duration
import kotlin.time.TestTimeSource
import kotlinx.coroutines.Dispatchers
import org.matchlab.academic_net.feature.auth.data.AuthRepositoryImpl
import org.matchlab.academic_net.feature.auth.data.SessionStorage
import org.matchlab.academic_net.feature.auth.data.mock.MockAuthDataSource
import org.matchlab.academic_net.feature.auth.domain.AuthRepository
import org.matchlab.academic_net.feature.auth.domain.CodeRequestOutcome
import org.matchlab.academic_net.feature.auth.domain.CodeVerificationOutcome
import org.matchlab.academic_net.feature.auth.domain.usecase.GetLastChallengeUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.RequestEmailCodeUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.VerifyEmailCodeUseCase
import org.matchlab.academic_net.securestore.InMemorySecureStore

/**
 * The sign-in flow as the app runs it today: the mock server and the real repository over an
 * in-memory store, with a clock the test moves by hand ([advance]).
 */
internal class AuthTestHarness {
    val time = TestTimeSource()
    val store = InMemorySecureStore()
    val source = MockAuthDataSource(latencyMillis = 0, timeSource = time)

    private val realRepository: AuthRepository = AuthRepositoryImpl(
        source = source,
        storage = SessionStorage(store),
        io = Dispatchers.Unconfined,
        timeSource = time,
    )

    /** While `true` the calls to the server fail like a lost connection. */
    var offline = false

    /** How many times the server was asked for a code / to check one. */
    var codeRequests = 0
    var codeChecks = 0

    val repository: AuthRepository = object : AuthRepository by realRepository {
        override suspend fun requestCode(email: String): CodeRequestOutcome {
            codeRequests++
            if (offline) error("no network")
            return realRepository.requestCode(email)
        }

        override suspend fun verifyCode(email: String, code: String): CodeVerificationOutcome {
            codeChecks++
            if (offline) error("no network")
            return realRepository.verifyCode(email, code)
        }
    }

    val requestCode = RequestEmailCodeUseCase(repository)
    val verifyCode = VerifyEmailCodeUseCase(repository)
    val getLastChallenge = GetLastChallengeUseCase(repository)

    fun signInViewModel() = SignInViewModel(requestCode)

    fun verifyViewModel(email: String) = VerifyEmailViewModel(email, verifyCode, requestCode, getLastChallenge)

    /** Moves the clock of the server and of the repository. */
    fun advance(duration: Duration) {
        time += duration
    }
}

internal const val TrustedEmail = "anna@hse.ru"
