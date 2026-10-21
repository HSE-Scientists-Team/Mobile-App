package org.matchlab.academic_net.feature.auth.domain

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.matchlab.academic_net.common.model.UserId

val TestSession = Session(UserId("user-me"), "student@hse.ru", AccessToken("secret-token"))

val TestPending = PendingRegistration("student@hse.ru", 60.seconds, hasPassword = true)

/** In-memory [AuthRepository] that records what the use cases pass to it. */
class FakeAuthRepository : AuthRepository {
    private val state = MutableStateFlow<AuthState>(AuthState.Restoring)
    override val authState: StateFlow<AuthState> = state

    /** Thrown by every call that reaches the "server", to play a network failure. */
    var failure: Throwable? = null
    var registrationOutcome: RegistrationOutcome = RegistrationOutcome.LetterSent(TestPending)
    var confirmationOutcome: ConfirmationOutcome = ConfirmationOutcome.Confirmed(signedIn = true)
    var signInOutcome: SignInOutcome = SignInOutcome.SignedIn
    var signInPendingOutcome: SignInOutcome? = SignInOutcome.SignedIn
    var resendOutcome: RegistrationOutcome? = RegistrationOutcome.LetterSent(TestPending)
    var pending: PendingRegistration? = null

    val registered = mutableListOf<Credentials>()
    val confirmations = mutableListOf<Pair<String, String>>()
    val signIns = mutableListOf<Credentials>()
    val pendingSignIns = mutableListOf<String>()
    val resends = mutableListOf<String>()
    val pendingLookups = mutableListOf<String>()
    var restoreCalls = 0
    var signOutCalls = 0

    override suspend fun restoreSession() {
        restoreCalls++
        state.value = AuthState.SignedOut()
    }

    override suspend fun register(credentials: Credentials): RegistrationOutcome {
        registered += credentials
        failure?.let { throw it }
        return registrationOutcome
    }

    override fun pendingRegistration(email: String): PendingRegistration? {
        pendingLookups += email
        return pending
    }

    override suspend fun resendLetter(email: String): RegistrationOutcome? {
        resends += email
        failure?.let { throw it }
        return resendOutcome
    }

    override suspend fun confirmEmail(email: String, linkOrToken: String): ConfirmationOutcome {
        confirmations += email to linkOrToken
        failure?.let { throw it }
        return confirmationOutcome
    }

    override suspend fun signIn(credentials: Credentials): SignInOutcome {
        signIns += credentials
        failure?.let { throw it }
        return signInOutcome
    }

    override suspend fun signInPending(email: String): SignInOutcome? {
        pendingSignIns += email
        failure?.let { throw it }
        return signInPendingOutcome
    }

    override suspend fun expireSession(token: AccessToken) {
        state.value = AuthState.SignedOut(sessionExpired = true)
    }

    override suspend fun signOut() {
        signOutCalls++
        state.value = AuthState.SignedOut()
    }
}
