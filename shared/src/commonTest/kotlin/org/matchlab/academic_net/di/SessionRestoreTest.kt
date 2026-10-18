package org.matchlab.academic_net.di

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.ConfirmationOutcome
import org.matchlab.academic_net.feature.auth.domain.RegistrationOutcome
import org.matchlab.academic_net.feature.auth.domain.SignInOutcome
import org.matchlab.academic_net.securestore.InMemorySecureStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * "The sign-in is not lost when the app is opened again", over the real graph and the HTTP calls of the Gateway (a mock
 * engine): the session outlives the process, is seen before the server is asked, survives a missing network, and ends only
 * by the user's choice or by the server's refusal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRestoreTest {
    private val store = InMemorySecureStore()

    /** A launch of the app on the store of this test. */
    private fun launchApp(server: FakeServer = FakeServer()) = AppLaunch(server, store)

    /**
     * Runs a test in real time. The sign-in and the session talk to a mock engine on threads of their own, and the
     * repository waits for the server with timeouts (the check of a token, the revoke at sign-out); the virtual time of
     * `runTest` would run those timeouts out the moment the test body waits for the answer.
     */
    private fun realTime(body: suspend RealTimeScope.() -> Unit) = runTest(timeout = 90.seconds) {
        val test = this
        withContext(Dispatchers.Default) { RealTimeScope(this, test).body() }
    }

    private class RealTimeScope(scope: CoroutineScope, val test: TestScope) : CoroutineScope by scope

    private suspend fun signedInLaunch(): AppLaunch = launchApp().also {
        it.signIn()
        assertIs<AuthState.SignedIn>(it.state)
    }

    // ---- the sign-in over HTTP ----

    @Test
    fun theSignInIsALoginCallAndTheStateFollowsIt() = realTime {
        val launch = launchApp()
        launch.app.auth.restoreSession()
        assertIs<AuthState.SignedOut>(launch.state)

        val result = launch.app.auth.signIn(StudentEmail, StudentPassword)

        assertEquals(SubmitResult.Success(SignInOutcome.SignedIn), result)
        assertEquals(UserId(ServerUserId), launch.session.userId)
        assertEquals(StudentEmail, launch.session.email)
        val login = launch.server.requests("POST /api/v1/auth/login").single()
        assertEquals(emptyList(), login.authorization, "the login carries no token")
        assertTrue(StudentPassword in login.body)
    }

    @Test
    fun aWrongPasswordIsRefusedAndNothingIsSaved() = realTime {
        val server = FakeServer().apply { onLogin = { jsonError(HttpStatusCode.Unauthorized, "bad credentials") } }
        val launch = launchApp(server)
        launch.app.auth.restoreSession()

        val result = launch.app.auth.signIn(StudentEmail, "wrong-password-1")

        assertEquals(SubmitResult.Success(SignInOutcome.WrongCredentials), result)
        assertIs<AuthState.SignedOut>(launch.state)
        assertNull(store.read("session.v1"))
    }

    @Test
    fun anAccountTheServerDisabledGetsTheForbiddenOutcome() = realTime {
        val server = FakeServer().apply { onLogin = { jsonError(HttpStatusCode.Forbidden, "not confirmed") } }
        val launch = launchApp(server)
        launch.app.auth.restoreSession()

        val result = launch.app.auth.signIn(StudentEmail, StudentPassword)

        assertEquals(SubmitResult.Success(SignInOutcome.AccountUnavailable), result)
        assertIs<AuthState.SignedOut>(launch.state)
    }

    @Test
    fun registeringConfirmingAndBeingSignedInByTheRememberedPassword() = realTime {
        val launch = launchApp()
        launch.app.auth.restoreSession()

        val registered = launch.app.auth.register(StudentEmail, StudentPassword, StudentPassword)
        assertIs<RegistrationOutcome.LetterSent>(assertIs<SubmitResult.Success<RegistrationOutcome>>(registered).value)
        assertIs<AuthState.SignedOut>(launch.state, "the account does not exist before the link is opened")

        val confirmed = launch.app.auth.confirmEmail(StudentEmail, "ABC123")

        val outcome = assertIs<SubmitResult.Success<ConfirmationOutcome>>(confirmed).value
        assertEquals(ConfirmationOutcome.Confirmed(signedIn = true), outcome)
        assertEquals(UserId(ServerUserId), launch.session.userId)
        assertEquals(
            listOf("POST /api/v1/auth/register", "POST /api/v1/auth/email/confirm", "POST /api/v1/auth/login"),
            launch.server.calls,
        )
        assertEquals("""{"token":"ABC123"}""", launch.server.requests("POST /api/v1/auth/email/confirm").single().body)
    }

    @Test
    fun anAddressOutsideTheRegistryIsNotAllowedToRegister() = realTime {
        val server = FakeServer().apply { onRegister = { jsonError(HttpStatusCode.Forbidden, "domain is not allowed") } }
        val launch = launchApp(server)
        launch.app.auth.restoreSession()

        val registered = launch.app.auth.register("student@gmail.com", StudentPassword, StudentPassword)

        assertEquals(SubmitResult.Success(RegistrationOutcome.DomainNotAllowed), registered)
        assertIs<AuthState.SignedOut>(launch.state)
    }

    // ---- surviving the process ----

    @Test
    fun aNewProcessOnTheSameStoreFindsTheSameSession() = realTime {
        val first = signedInLaunch()

        val second = launchApp()
        second.app.auth.restoreSession()

        assertEquals(AuthState.SignedIn(first.session), second.state)
    }

    @Test
    fun theUserIsSignedInBeforeTheServerAnswersAndStaysSoWhileItDoesNot() = realTime {
        val first = signedInLaunch()
        val second = launchApp(FakeServer().apply { onCurrentSession = { awaitCancellation() } })

        val started = AppGraphs(second.app, ComponentSessionGraphs(second.app)).start(test.backgroundScope)
        val state = second.app.authRepository.authState.first { it !is AuthState.Restoring }

        assertEquals(AuthState.SignedIn(first.session), state)
        assertTrue(started.isActive)
        // The question about the token follows the state; it is on its way and nobody answers it.
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) { while (second.server.arrivals.isEmpty()) delay(5.milliseconds) }
        }
        assertEquals(listOf("GET /api/v1/sessions/current"), second.server.calls)
        assertEquals(listOf("Bearer token-1"), second.server.arrivals.single().authorization)
        assertIs<AuthState.SignedIn>(second.state)
    }

    @Test
    fun theSessionIsAskedAboutOnceWithItsToken() = realTime {
        val first = signedInLaunch()
        val second = launchApp()

        second.app.auth.restoreSession()
        second.app.auth.restoreSession()

        assertEquals(first.session, second.session)
        assertEquals(1, second.server.requests("GET /api/v1/sessions/current").size, "it is restored once")
    }

    // ---- a network that is not there ----

    @Test
    fun aStartWithoutTheNetworkKeepsTheSessionAndSoDoesTheNextOne() = realTime {
        val first = signedInLaunch()

        repeat(3) {
            val offline = launchApp(FakeServer().apply { onCurrentSession = { throw IOException("no route to host") } })
            offline.app.auth.restoreSession()
            assertEquals(AuthState.SignedIn(first.session), offline.state, "start number ${it + 1}")
        }

        val online = launchApp()
        online.app.auth.restoreSession()
        assertEquals(AuthState.SignedIn(first.session), online.state)
    }

    @Test
    fun aServerThatDoesNotAnswerInTimeKeepsTheSession() = realTime {
        val first = signedInLaunch()
        val slow = launchApp(FakeServer().apply { onCurrentSession = { throw HttpRequestTimeoutException("https://api", 20_000) } })

        slow.app.auth.restoreSession()

        assertEquals(AuthState.SignedIn(first.session), slow.state)
    }

    @Test
    fun aServerErrorOnTheCheckKeepsTheSession() = realTime {
        val first = signedInLaunch()
        val broken = launchApp(FakeServer().apply { onCurrentSession = { jsonError(HttpStatusCode.InternalServerError) } })

        broken.app.auth.restoreSession()

        assertEquals(AuthState.SignedIn(first.session), broken.state)
    }

    // ---- the server's word ----

    @Test
    fun aRefusedTokenEndsTheSessionErasesItAndKeepsTheAddress() = realTime {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden)) {
            val store = InMemorySecureStore()
            AppLaunch(FakeServer(), store).signIn()
            val refused = AppLaunch(FakeServer().apply { onCurrentSession = { jsonError(status) } }, store)

            refused.app.auth.restoreSession()

            assertEquals(AuthState.SignedOut(sessionExpired = true, lastEmail = StudentEmail), refused.state, "$status")
            // The next launch has no session to find, only the address.
            val next = AppLaunch(FakeServer(), store)
            next.app.auth.restoreSession()
            assertEquals(AuthState.SignedOut(sessionExpired = false, lastEmail = StudentEmail), next.state, "$status")
            assertEquals(emptyList(), next.server.calls, "$status: nobody is asked when there is nothing to ask about")
        }
    }

    @Test
    fun aTokenOfAnotherUserEndsTheSession() = realTime {
        signedInLaunch()
        val stranger = launchApp(FakeServer().apply { onCurrentSession = { json("""{"user_id":"someone-else"}""") } })

        stranger.app.auth.restoreSession()

        assertEquals(AuthState.SignedOut(sessionExpired = true, lastEmail = StudentEmail), stranger.state)
    }

    @Test
    fun aClearedStoreIsSignedOut() = realTime {
        signedInLaunch()
        store.wipe()
        val next = launchApp()

        next.app.auth.restoreSession()

        assertEquals(AuthState.SignedOut(), next.state)
        assertEquals(emptyList(), next.server.calls)
    }

    // ---- a 401 on a call of the session ----

    @Test
    fun aRefusedCallOfTheSessionEndsItWhenTheServerRefusesTheTokenToo() = realTime {
        val server = FakeServer().apply {
            onOther = { jsonError(HttpStatusCode.Unauthorized, "expired") }
            onLogin = { json("""{"user_id":"$userId","access_token":"token-1"}""") }
        }
        val launch = launchApp(server)
        launch.signIn()
        val graph = launch.sessionGraph()
        server.onCurrentSession = { jsonError(HttpStatusCode.Unauthorized, "expired") }

        val result = graph.dependencies.settings.getOpenToCollaboration()

        assertTrue(result.isFailure)
        assertEquals(AuthState.SignedOut(sessionExpired = true, lastEmail = StudentEmail), launch.state)
        assertEquals(listOf("Bearer token-1"), server.requests("GET /api/v1/sessions/current").map { it.authorization }.single())
    }

    @Test
    fun aRefusedCallDoesNotEndASessionTheServerStillAccepts() = realTime {
        val server = FakeServer().apply { onOther = { jsonError(HttpStatusCode.Unauthorized, "hiccup") } }
        val launch = launchApp(server)
        launch.signIn()
        val graph = launch.sessionGraph()

        val result = graph.dependencies.settings.getOpenToCollaboration()

        assertTrue(result.isFailure)
        assertIs<AuthState.SignedIn>(launch.state, "one 401 is a reason to ask, not to sign out")
        assertEquals(1, server.requests("GET /api/v1/sessions/current").size)
    }

    // ---- signing out ----

    @Test
    fun signingOutErasesTheSessionKeepsTheAddressAndRevokesTheToken() = realTime {
        val launch = signedInLaunch()

        launch.app.auth.signOut()

        assertEquals(AuthState.SignedOut(sessionExpired = false, lastEmail = StudentEmail), launch.state)
        val revoke = launch.server.requests("DELETE /api/v1/sessions/current").single()
        assertEquals(listOf("Bearer token-1"), revoke.authorization)
        val next = launchApp()
        next.app.auth.restoreSession()
        assertEquals(AuthState.SignedOut(lastEmail = StudentEmail), next.state)
    }

    @Test
    fun aRevokeThatHangsDoesNotKeepTheUserWaiting() = realTime {
        val launch = signedInLaunch()
        launch.server.onRevoke = { awaitCancellation() }

        val signOut = async { launch.app.auth.signOut() }
        // The user is out the moment they ask, whatever the server does about the token.
        launch.app.authRepository.authState.first { it is AuthState.SignedOut }
        assertTrue(signOut.isActive, "the revoke is still hanging")
        assertEquals(AuthState.SignedOut(lastEmail = StudentEmail), launch.state)

        // And the call itself ends after the five seconds the repository waits for the server, not never.
        val waited = measureTime { signOut.await() }
        assertTrue(waited < 15.seconds, "waited $waited")
        assertEquals(1, launch.server.requests("DELETE /api/v1/sessions/current").size)
    }

    @Test
    fun aRevokeThatFailsStillSignsTheUserOut() = realTime {
        val launch = signedInLaunch()
        launch.server.onRevoke = { throw IOException("no network") }

        launch.app.auth.signOut()

        assertIs<AuthState.SignedOut>(launch.state)
        val next = launchApp()
        next.app.auth.restoreSession()
        assertIs<AuthState.SignedOut>(next.state)
    }

    @Test
    fun signingInAgainAfterSigningOutIsAnotherSession() = realTime {
        val launch = signedInLaunch()
        val first = launch.session
        launch.app.auth.signOut()

        launch.signIn()

        assertEquals(first.userId, launch.session.userId)
        assertNotEquals(first.accessToken, launch.session.accessToken)
    }

    // ---- the password ----

    @Test
    fun thePasswordNeverReachesTheSecureStore() = realTime {
        val recording = RecordingStore()
        val launch = AppLaunch(FakeServer(), recording)
        launch.app.auth.restoreSession()

        launch.app.auth.register(StudentEmail, StudentPassword, StudentPassword)
        launch.app.auth.confirmEmail(StudentEmail, "ABC123")
        assertIs<AuthState.SignedIn>(launch.state)
        launch.app.auth.signOut()
        launch.signIn()
        launch.app.auth.signOut()

        assertTrue(recording.writes.isNotEmpty(), "the session and the address were written")
        assertFalse(recording.writes.any { (_, value) -> StudentPassword in value }, "nothing written holds the password")
        assertEquals(setOf("session.v1", "session.last_email"), recording.writes.map { it.first }.toSet())
    }
}
