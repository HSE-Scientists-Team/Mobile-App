package org.matchlab.academic_net.feature.auth.ui

import androidx.lifecycle.SavedStateHandle
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Duration
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.auth.data.AuthDataSource
import org.matchlab.academic_net.feature.auth.data.AuthRepositoryImpl
import org.matchlab.academic_net.feature.auth.data.ConfirmReply
import org.matchlab.academic_net.feature.auth.data.RegisterReply
import org.matchlab.academic_net.feature.auth.data.SessionCheck
import org.matchlab.academic_net.feature.auth.data.SessionStorage
import org.matchlab.academic_net.feature.auth.data.SignInReply
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.AuthRepository
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.Credentials
import org.matchlab.academic_net.feature.auth.domain.Password
import org.matchlab.academic_net.feature.auth.domain.usecase.ConfirmEmailUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.GetPendingRegistrationUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.RegisterUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.ResendLetterUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.SignInPendingUseCase
import org.matchlab.academic_net.feature.auth.domain.usecase.SignInUseCase
import org.matchlab.academic_net.securestore.InMemorySecureStore
import org.matchlab.academic_net.securestore.SecureStore

/** The address of the tests: of a university the played server trusts. */
internal const val TrustedEmail = "anna@hse.ru"

/** An address of a domain that is not in the registry of the played server (it answers 403 to it). */
internal const val UntrustedEmail = "anna@example.com"

/** The password of the tests; no state, text or saved state may ever hold it. */
internal const val TestPassword = "correct-horse-1"

/** Another one, for "wrong password". */
internal const val OtherPassword = "battery-staple-2"

/**
 * The server of the tests: a [PlayedAccountServer] (it answers the way the Gateway does) behind a layer that counts
 * the calls and can hold them, fail them or answer instead of it. [server] is the played server itself, for what
 * happens outside the app (the user opens the link of the letter in a browser).
 */
internal class ScriptedAuthSource(val server: PlayedAccountServer) : AuthDataSource {
    var registers = 0
    var confirms = 0
    var signIns = 0

    /** The credentials of every sign-in the server was asked for, in order. */
    val signInAttempts = mutableListOf<Credentials>()

    /** The texts of every confirmation the server was asked for, in order. */
    val confirmedTexts = mutableListOf<String>()

    /** While set, every call fails with it, as a lost connection does. */
    var failure: Throwable? = null

    /** While set, the sign-in fails with it (the other calls do not). */
    var signInFailure: Throwable? = null

    /** While set, every call waits for it before the server answers: a request that is on its way. */
    var gate: CompletableDeferred<Unit>? = null

    /** What the server answers instead of the played one, while set. */
    var registerReply: RegisterReply? = null
    var confirmReply: ConfirmReply? = null
    var signInReply: SignInReply? = null

    /** The same as `failure = RemoteFailure.NoConnection()`. */
    var offline: Boolean
        get() = failure is RemoteFailure.NoConnection
        set(value) {
            failure = if (value) RemoteFailure.NoConnection() else null
        }

    /** Holds every call from now on until [release]; returns nothing, the calls are counted as they come. */
    fun hold() {
        gate = CompletableDeferred()
    }

    /** Lets the held calls through. */
    fun release() {
        gate?.complete(Unit)
        gate = null
    }

    private suspend fun beforeCall() {
        gate?.await()
        failure?.let { throw it }
    }

    override suspend fun register(credentials: Credentials): RegisterReply {
        registers++
        beforeCall()
        return registerReply ?: server.register(credentials)
    }

    override suspend fun confirmEmail(linkOrToken: String): ConfirmReply {
        confirms++
        confirmedTexts += linkOrToken
        beforeCall()
        return confirmReply ?: server.confirmEmail(linkOrToken)
    }

    override suspend fun signIn(credentials: Credentials): SignInReply {
        signIns++
        signInAttempts += credentials
        beforeCall()
        signInFailure?.let { throw it }
        return signInReply ?: server.signIn(credentials)
    }

    override suspend fun checkSession(token: AccessToken): SessionCheck = server.checkSession(token)

    override suspend fun revoke(token: AccessToken) = server.revoke(token)
}

/** A store that remembers every write, to show what reached the device. */
internal class RecordingStore(private val inner: SecureStore = InMemorySecureStore()) : SecureStore by inner {
    /** The key and the value of every write, in order. */
    val writes = mutableListOf<Pair<String, String>>()

    override fun write(key: String, value: String) {
        writes += key to value
        inner.write(key, value)
    }
}

/**
 * The sign-in flow as the app runs it today: the real repository over the [ScriptedAuthSource] (a played server
 * with no delay) and a store of the device (in memory), with a clock the test moves by hand ([advance]).
 *
 * [restart] is the app started again: a new repository and new view models over the same device storage and the
 * same server, which is what a user who comes back to the app meets. The harness of a restart shares [time], [store]
 * and the server with the one it came from, and nothing else (the counters of the calls start from zero, nothing is
 * remembered in memory).
 */
internal class AuthTestHarness(
    val time: TestTimeSource = TestTimeSource(),
    val store: SecureStore = InMemorySecureStore(),
    private val server: PlayedAccountServer = PlayedAccountServer(time),
) {
    val source = ScriptedAuthSource(server)

    val repository: AuthRepository = AuthRepositoryImpl(
        source = source,
        storage = SessionStorage(store),
        io = Dispatchers.Unconfined,
        timeSource = time,
    )

    val signIn = SignInUseCase(repository)
    val register = RegisterUseCase(repository)
    val confirmEmail = ConfirmEmailUseCase(repository)
    val signInPending = SignInPendingUseCase(repository)
    val resendLetter = ResendLetterUseCase(repository)
    val getPendingRegistration = GetPendingRegistrationUseCase(repository)

    fun signInViewModel(initialEmail: String = "", savedState: SavedStateHandle = SavedStateHandle()) =
        SignInViewModel(initialEmail, signIn, savedState)

    fun registerViewModel(initialEmail: String = "", savedState: SavedStateHandle = SavedStateHandle()) =
        RegisterViewModel(initialEmail, register, savedState)

    fun confirmViewModel(email: String = TrustedEmail) =
        ConfirmEmailViewModel(email, confirmEmail, signInPending, resendLetter, getPendingRegistration)

    fun confirmLinkViewModel(email: String = TrustedEmail, link: String = LetterLink) =
        ConfirmLinkViewModel(email, link, confirmEmail)

    /** The state of the session in the repository. */
    val authState: AuthState get() = repository.authState.value

    /** The app started again, see the class. */
    fun restart() = AuthTestHarness(time, store, server)

    /** The server forgets [token]: the next question about it is answered with a refusal. */
    suspend fun revokeOnTheServer(token: AccessToken) {
        server.revoke(token)
    }

    /**
     * An account that exists on the server and is not signed in: the address was registered and its link opened
     * outside the app, so the app remembers nothing of it.
     */
    suspend fun createAccount(email: String = TrustedEmail, password: String = TestPassword) {
        server.register(Credentials(email, Password(password)))
        server.confirmEmail(PlayedAccountServer.LetterToken)
    }

    /**
     * The user has registered in this run of the app and waits for the link: the letter was sent and the app
     * remembers the password.
     */
    suspend fun registerAndWait(email: String = TrustedEmail, password: String = TestPassword) {
        repository.register(Credentials(email, Password(password)))
    }

    /**
     * The server has a registration that waits for its link, but the app remembers nothing of it: it was made in a
     * run of the app that was killed since.
     */
    suspend fun registerOutsideTheApp(email: String = TrustedEmail, password: String = TestPassword) {
        server.register(Credentials(email, Password(password)))
    }

    /** The user opens the link of the letter in the browser: the server confirms the address, the app is not told. */
    suspend fun openLinkInBrowser() {
        server.confirmEmail(PlayedAccountServer.LetterToken)
    }

    /** Moves the clock of the server and of the repository. */
    fun advance(duration: Duration) {
        time += duration
    }
}

/** What the view model tests have in common: the main dispatcher of the coroutines and a harness. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class AuthViewModelTest {
    internal val harness = AuthTestHarness()

    @BeforeTest
    fun setUpMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDownMain() = Dispatchers.resetMain()
}

/** The link of the letter as the system hands it over; the token is the one the played server knows. */
internal const val LetterLink = "https://matchlab.space/verify-email?token=" + PlayedAccountServer.LetterToken
