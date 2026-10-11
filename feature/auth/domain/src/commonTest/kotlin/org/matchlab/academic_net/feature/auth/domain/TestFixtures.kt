package org.matchlab.academic_net.feature.auth.domain

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.matchlab.academic_net.common.model.UserId

val TestSession = Session(UserId("user-me"), "student@hse.ru", AccessToken("secret-token"))

/** In-memory [AuthRepository] that records what the use cases pass to it. */
class FakeAuthRepository : AuthRepository {
    private val state = MutableStateFlow<AuthState>(AuthState.Restoring)
    override val authState: StateFlow<AuthState> = state

    var failure: Throwable? = null
    var requestOutcome: CodeRequestOutcome =
        CodeRequestOutcome.Sent(VerificationChallenge("student@hse.ru", 60.seconds))
    var verifyOutcome: CodeVerificationOutcome = CodeVerificationOutcome.Verified(TestSession)
    var challenge: VerificationChallenge? = null

    val requestedEmails = mutableListOf<String>()
    val verified = mutableListOf<Pair<String, String>>()
    val challengeLookups = mutableListOf<String>()
    var restoreCalls = 0
    var signOutCalls = 0

    override suspend fun restoreSession() {
        restoreCalls++
        state.value = AuthState.SignedOut
    }

    override suspend fun requestCode(email: String): CodeRequestOutcome {
        requestedEmails += email
        failure?.let { throw it }
        return requestOutcome
    }

    override fun lastChallenge(email: String): VerificationChallenge? {
        challengeLookups += email
        return challenge
    }

    override suspend fun verifyCode(email: String, code: String): CodeVerificationOutcome {
        verified += email to code
        failure?.let { throw it }
        return verifyOutcome
    }

    override suspend fun signOut() {
        signOutCalls++
        state.value = AuthState.SignedOut
    }
}

