package org.matchlab.academic_net.di

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.Session
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The graph of a signed-in user over the Gateway (a mock engine): it makes no request by being built, it has one session
 * client, and it lets go of everything when it is closed, leaving the process's Gateway and the sign-in alone.
 */
class SessionGraphTest {
    private val session = Session(UserId(ServerUserId), StudentEmail, AccessToken("token-1"))

    private fun launchApp(server: FakeServer = FakeServer()) = AppLaunch(server)

    // ---- the Gateway client of the process ----

    @Test
    fun theGatewayClientIsNotMadeUntilTheSignInIsUsed() {
        val launch = launchApp()

        assertEquals(0, launch.server.clients, "building the process makes no client")

        launch.app.auth

        assertEquals(1, launch.server.clients)
    }

    @Test
    fun theGatewayClientIsMadeOnceForTheWholeProcess() {
        val launch = launchApp()

        launch.app.auth
        launch.app.auth
        launch.app.authRepository
        launch.sessionGraph(session).dependencies
        launch.sessionGraph(session.copy(accessToken = AccessToken("token-2"))).dependencies

        assertEquals(1, launch.server.clients, "the sign-in and every session graph share one Gateway")
    }

    @Test
    fun theGatewayClientIsMadeForAGraphThatIsBuiltBeforeTheSignInWasUsed() {
        val launch = launchApp()

        launch.sessionGraph(session).dependencies

        assertEquals(1, launch.server.clients)
    }

    // ---- building a graph ----

    @Test
    fun buildingAGraphAndItsDependenciesSendsNoRequest() {
        val launch = launchApp()

        val component = launch.sessionGraph(session)
        component.dependencies
        component.gatewaySession

        assertEquals(emptyList(), launch.server.calls)
    }

    @Test
    fun everyFeatureGetsItsDependencies() {
        val dependencies = launchApp().sessionGraph(session).dependencies

        assertNotNull(dependencies.projects)
        assertNotNull(dependencies.chats)
        assertNotNull(dependencies.collaborators)
        assertNotNull(dependencies.feed)
        assertNotNull(dependencies.projectSearch)
        assertNotNull(dependencies.projectDetails)
        assertNotNull(dependencies.profile)
        assertNotNull(dependencies.settings)
    }

    @Test
    fun theDependenciesOfOneGraphAreOneInstance() {
        val component = launchApp().sessionGraph(session)

        assertSame(component.dependencies, component.dependencies)
    }

    @Test
    fun theSessionClientIsOneInstancePerGraph() {
        val launch = launchApp()
        val first = launch.sessionGraph(session)
        val second = launch.sessionGraph(session)

        assertSame(first.gatewaySession, first.gatewaySession)
        assertNotSame(first.gatewaySession, second.gatewaySession)
    }

    @Test
    fun twoGraphsOfOneProcessDoNotShareTheirState() {
        val launch = launchApp()

        assertNotSame(launch.sessionGraph(session).projectsRepository, launch.sessionGraph(session).projectsRepository)
    }

    @Test
    fun theChatsAreReadFromTheServerWithTheTokenOfTheSession() = runTest {
        val server = FakeServer().apply {
            onOther = { arrival ->
                if (arrival.call == "GET /api/v1/chat/conversations") json("""{"items":[],"next_cursor":""}""") else jsonError(HttpStatusCode.NotFound, "no")
            }
        }
        val chats = launchApp(server).sessionGraph(session).dependencies.chats

        assertEquals(0, chats.observeConversations().first().size)
        assertEquals(listOf(listOf("Bearer token-1")), server.requests("GET /api/v1/chat/conversations").map { it.authorization })
    }

    // ---- the token of the graph ----

    @Test
    fun theCallsOfAGraphCarryTheTokenOfItsSessionOnly() = runTest {
        val server = FakeServer().apply { onOther = { json("""{"details":{"display_name":"Имя"},"user_id":"$userId"}""") } }
        val launch = launchApp(server)
        val first = launch.sessionGraph(session)
        val second = launch.sessionGraph(session.copy(accessToken = AccessToken("token-2")))

        first.dependencies.settings.getOpenToCollaboration()
        second.dependencies.settings.getOpenToCollaboration()

        assertEquals(
            listOf(listOf("Bearer token-1"), listOf("Bearer token-2")),
            server.requests("GET /api/v1/users/me/profile").map { it.authorization },
        )
    }

    // ---- closing a graph ----

    @Test
    fun closingAGraphEndsItsResources() {
        val component = launchApp().sessionGraph(session)
        component.dependencies
        assertTrue(component.resources.openCount >= 1, "the session client waits to be closed")
        val scope = component.resources.scope.coroutineContext[kotlinx.coroutines.Job]!!

        component.close()

        assertTrue(component.resources.isClosed)
        assertEquals(0, component.resources.openCount)
        assertTrue(scope.isCancelled)
    }

    @Test
    fun closingTwiceIsHarmless() {
        val component = launchApp().sessionGraph(session)
        component.dependencies

        component.close()
        component.close()

        assertTrue(component.resources.isClosed)
    }

    @Test
    fun aClosedGraphRefusesRequestsAndSendsNone() = runTest {
        val launch = launchApp()
        val component = launch.sessionGraph(session)
        val sessionClient = component.gatewaySession

        component.close()

        assertFailsWith<CancellationException> { sessionClient.profiles.myProfile() }
        assertEquals(emptyList(), launch.server.calls)
    }

    @Test
    fun aSessionClientAskedForAfterTheCloseIsClosedAtOnce() = runTest {
        val launch = launchApp()
        val component = launch.sessionGraph(session)
        component.close()

        val late = component.gatewaySession

        assertEquals(0, component.resources.openCount, "a client nobody will close is not kept")
        assertFailsWith<CancellationException> { late.profiles.myProfile() }
        assertEquals(emptyList(), launch.server.calls)
    }

    @Test
    fun closingAGraphLeavesTheSignInAndTheGatewayWorking() = runTest {
        val launch = launchApp()
        launch.signIn()
        val component = launch.sessionGraph()
        component.dependencies

        component.close()

        assertFalse(launch.state is AuthState.SignedOut, "ending a graph is not signing out")
        // The account calls go through the Gateway of the process, which the graph did not close.
        launch.app.auth.signOut()
        launch.signIn()
        assertEquals(session.userId, launch.session.userId)
        assertEquals(2, launch.server.requests("POST /api/v1/auth/login").size)
    }

    @Test
    fun theNextGraphIsNotSpoiledByTheClosingOfTheLast() = runTest {
        val server = FakeServer().apply { onOther = { json("""{"details":{"display_name":"Имя"},"user_id":"$userId"}""") } }
        val launch = launchApp(server)
        val closed = launch.sessionGraph(session)
        closed.dependencies
        closed.close()

        val next = launch.sessionGraph(session.copy(accessToken = AccessToken("token-2")))
        val open = next.dependencies.settings.getOpenToCollaboration()

        assertEquals(false, open.getOrThrow())
        assertEquals(listOf(listOf("Bearer token-2")), server.requests("GET /api/v1/users/me/profile").map { it.authorization })
    }
}
