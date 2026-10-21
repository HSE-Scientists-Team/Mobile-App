package org.matchlab.academic_net.feature.auth.data

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.matchlab.academic_net.feature.auth.domain.AuthRepository
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.auth.domain.SignInOutcome
import org.matchlab.academic_net.securestore.InMemorySecureStore
import org.matchlab.academic_net.securestore.SecureStore

/**
 * What the tests of [AuthRepositoryImpl] share: the clock they move, the secure store they keep
 * on the "disk", and the scripted server. A test makes repositories over them with [repository],
 * several of them for the launches of the app that share one disk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class AuthRepositoryTestBase {
    protected val time = TestTimeSource()
    protected val store = InMemorySecureStore()
    protected val source = FakeAuthDataSource()

    protected fun TestScope.repository(
        server: AuthDataSource = source,
        secureStore: SecureStore = store,
        io: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler),
        resendInterval: Duration = 60.seconds,
        revokeTimeout: Duration = 5.seconds,
        verifyTimeout: Duration = 5.seconds,
    ) = AuthRepositoryImpl(
        source = server,
        storage = SessionStorage(secureStore),
        io = io,
        timeSource = time,
        resendInterval = resendInterval,
        revokeTimeout = revokeTimeout,
        verifyTimeout = verifyTimeout,
    )

    /** A repository that has restored a store with no session: signed out. */
    protected suspend fun TestScope.signedOutRepository(
        server: AuthDataSource = source,
        secureStore: SecureStore = store,
    ) = repository(server = server, secureStore = secureStore).also { it.restoreSession() }

    /** Signs in as [Hunter]; the session. */
    protected suspend fun AuthRepository.signInAsHunter(): Session {
        assertEquals(SignInOutcome.SignedIn, signIn(Hunter))
        return session()
    }

    /** The session of a repository that is signed in. */
    protected fun AuthRepository.session(): Session = assertIs<AuthState.SignedIn>(authState.value).session
}
