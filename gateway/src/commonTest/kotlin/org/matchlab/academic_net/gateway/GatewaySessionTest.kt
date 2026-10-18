package org.matchlab.academic_net.gateway

import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.CredentialsDto
import org.matchlab.academic_net.gateway.dto.UserIdDto
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The calls of a session: the token in every request, the 401 reported to the listener, and what closing does.
 * The session has no API of its own yet, so the tests make its calls through [GatewaySession.requests], the
 * helper that the APIs of the next blocks are built on.
 */
class GatewaySessionTest {
    private suspend fun GatewaySession.probe(path: String = "probe"): UserIdDto = requests.get(path)

    private fun fixtureAnswering(status: HttpStatusCode) = GatewayFixture {
        if (status.value in 200..299) json(Spec.UserIdAnswer, status) else jsonError(status, "refused")
    }

    private val ignoreRejection = SessionRejectionListener { }

    // ---- the token ----

    @Test
    fun everyRequestOfASessionCarriesTheBearerTokenOnce() = runTest {
        val fixture = GatewayFixture { json(Spec.UserIdAnswer) }
        val session = fixture.gateway.openSession("tok-1", ignoreRejection)

        session.probe()
        session.requests.post<CredentialsDto, UserIdDto>("probe", Spec.credentials)
        session.requests.delete("probe")
        session.requests.get<UserIdDto>("probe", query = mapOf("limit" to "100"))

        assertEquals(4, fixture.requests.size)
        assertTrue(fixture.requests.all { it.authorizations == listOf("Bearer tok-1") })
    }

    @Test
    fun aSessionSpeaksToTheApiPrefixOfTheConfiguredServer() = runTest {
        val fixture = GatewayFixture(GatewayConfig(baseUrl = "http://localhost:8080/")) { json(Spec.UserIdAnswer) }
        val session = fixture.gateway.openSession("tok-1", ignoreRejection)

        session.probe("users/me/profile")
        session.probe("/search/people")

        assertEquals("http://localhost:8080/api/v1/users/me/profile", fixture.requests[0].url.toString())
        assertEquals("http://localhost:8080/api/v1/search/people", fixture.requests[1].url.toString())
    }

    @Test
    fun theQueryOfACallIsEncodedAndNullsAreLeftOut() = runTest {
        val fixture = GatewayFixture { json(Spec.UserIdAnswer) }
        val session = fixture.gateway.openSession("tok-1", ignoreRejection)

        session.requests.get<UserIdDto>(
            "search/people",
            query = mapOf("q" to "машинное обучение & AI", "limit" to "10", "university" to null),
        )

        val url = fixture.requests.single().url
        assertEquals("машинное обучение & AI", url.parameters["q"])
        assertEquals("10", url.parameters["limit"])
        assertFalse(url.parameters.contains("university"))
    }

    @Test
    fun theAccountOfTheGatewayStaysWithoutAuthorizationWhileASessionIsOpen() = runTest {
        val fixture = GatewayFixture { json(Spec.LoginAnswer) }
        val session = fixture.gateway.openSession("tok-1", ignoreRejection)

        fixture.account.login(Spec.credentials)
        session.probe()

        assertTrue(fixture.requests[0].authorizations.isEmpty())
        assertEquals(listOf("Bearer tok-1"), fixture.requests[1].authorizations)
    }

    @Test
    fun aBlankTokenCannotOpenASession() {
        val fixture = GatewayFixture { json(Spec.UserIdAnswer) }

        assertFailsWith<IllegalArgumentException> { fixture.gateway.openSession("", ignoreRejection) }
        assertFailsWith<IllegalArgumentException> { fixture.gateway.openSession("  ", ignoreRejection) }
    }

    @Test
    fun aSessionHasTheTimeoutsOfTheGateway() = runTest {
        val fixture = GatewayFixture(GatewayConfig(connectTimeout = 3.seconds, requestTimeout = 7.seconds)) {
            json(Spec.UserIdAnswer)
        }
        val session = fixture.gateway.openSession("tok-1", ignoreRejection)

        session.probe()

        val timeouts = assertNotNull(fixture.requests.single().getCapabilityOrNull(HttpTimeoutCapability))
        assertEquals(3_000L, timeouts.connectTimeoutMillis)
        assertEquals(7_000L, timeouts.requestTimeoutMillis)
        assertEquals(7_000L, timeouts.socketTimeoutMillis)
    }

    @Test
    fun aSessionTimesOutLikeTheAccount() = runTest {
        val fixture = GatewayFixture(GatewayConfig(requestTimeout = 100.milliseconds)) {
            delay(30.seconds)
            json(Spec.UserIdAnswer)
        }
        val session = fixture.gateway.openSession("tok-1", ignoreRejection)

        failureOf<RemoteFailure.Timeout> { session.probe() }
    }

    @Test
    fun sessionsOfOneGatewayAreIndependent() = runTest {
        val fixture = GatewayFixture { json(Spec.UserIdAnswer) }
        val first = fixture.gateway.openSession("tok-1", ignoreRejection)
        val second = fixture.gateway.openSession("tok-2", ignoreRejection)

        first.probe()
        second.probe()
        first.probe()

        assertEquals(
            listOf("Bearer tok-1", "Bearer tok-2", "Bearer tok-1"),
            fixture.requests.map { it.authorizations.single() },
        )
    }

    // ---- 401 ----

    @Test
    fun aRejectedTokenIsReportedOnceAndTheCallStillFails() = runTest {
        val fixture = fixtureAnswering(HttpStatusCode.Unauthorized)
        val rejected = mutableListOf<String>()
        val session = fixture.gateway.openSession("tok-1") { rejected += it }

        val failure = failureOf<RemoteFailure.Http> { session.probe() }

        assertEquals(401, failure.status)
        assertEquals("refused", failure.serverMessage)
        assertEquals(listOf("tok-1"), rejected)
    }

    @Test
    fun theListenerHearsAboutItBeforeTheCallFails() = runTest {
        val fixture = fixtureAnswering(HttpStatusCode.Unauthorized)
        val events = mutableListOf<String>()
        val session = fixture.gateway.openSession("tok-1") { events += "listener" }

        failureOf<RemoteFailure.Http> { session.probe() }
        events += "failure"

        assertEquals(listOf("listener", "failure"), events)
    }

    @Test
    fun everyCallThatGetsA401IsReported() = runTest {
        val fixture = fixtureAnswering(HttpStatusCode.Unauthorized)
        val rejected = mutableListOf<String>()
        val session = fixture.gateway.openSession("tok-1") { rejected += it }

        failureOf<RemoteFailure.Http> { session.probe() }
        failureOf<RemoteFailure.Http> { session.probe() }

        assertEquals(listOf("tok-1", "tok-1"), rejected)
    }

    @Test
    fun onlyA401IsReported() = runTest {
        val statuses = listOf(
            HttpStatusCode.OK,
            HttpStatusCode.NoContent,
            HttpStatusCode.BadRequest,
            HttpStatusCode.Forbidden,
            HttpStatusCode.NotFound,
            HttpStatusCode.Conflict,
            HttpStatusCode.InternalServerError,
            HttpStatusCode.ServiceUnavailable,
        )
        for (status in statuses) {
            val fixture = GatewayFixture { respond("{}", status) }
            val rejected = mutableListOf<String>()
            val session = fixture.gateway.openSession("tok-1") { rejected += it }

            runCatching { session.requests.delete("probe") }

            assertTrue(rejected.isEmpty(), "status $status was reported")
        }
    }

    @Test
    fun eachSessionReportsToItsOwnListener() = runTest {
        val fixture = GatewayFixture { request ->
            if (request.authorizations == listOf("Bearer tok-2")) {
                jsonError(HttpStatusCode.Unauthorized, "refused")
            } else {
                json(Spec.UserIdAnswer)
            }
        }
        val heardByFirst = mutableListOf<String>()
        val heardBySecond = mutableListOf<String>()
        val first = fixture.gateway.openSession("tok-1") { heardByFirst += it }
        val second = fixture.gateway.openSession("tok-2") { heardBySecond += it }

        first.probe()
        failureOf<RemoteFailure.Http> { second.probe() }

        assertTrue(heardByFirst.isEmpty())
        assertEquals(listOf("tok-2"), heardBySecond)
    }

    @Test
    fun aWrongPasswordDoesNotEndASession() = runTest {
        val fixture = fixtureAnswering(HttpStatusCode.Unauthorized)
        val rejected = mutableListOf<String>()
        fixture.gateway.openSession("tok-1") { rejected += it }

        val failure = failureOf<RemoteFailure.Http> { fixture.account.login(Spec.credentials) }

        assertEquals(401, failure.status)
        assertTrue(rejected.isEmpty())
    }

    @Test
    fun theCheckAndTheSignOutLeaveTheirRejectionToTheCaller() = runTest {
        val fixture = fixtureAnswering(HttpStatusCode.Unauthorized)
        val rejected = mutableListOf<String>()
        fixture.gateway.openSession("tok-1") { rejected += it }

        assertEquals(401, failureOf<RemoteFailure.Http> { fixture.account.currentSession("tok-1") }.status)
        assertEquals(401, failureOf<RemoteFailure.Http> { fixture.account.revokeSession("tok-1") }.status)

        assertTrue(rejected.isEmpty())
    }

    @Test
    fun aFailingListenerDoesNotHideTheFailureOfTheCall() = runTest {
        val fixture = fixtureAnswering(HttpStatusCode.Unauthorized)
        val storageError = IllegalStateException("storage is read-only")
        val session = fixture.gateway.openSession("tok-1") { throw storageError }

        val failure = failureOf<RemoteFailure.Http> { session.probe() }

        assertEquals(401, failure.status)
        // Coroutines may hand over a copy of the exception, so compare what it says, not the instance.
        val suppressed = failure.suppressedExceptions.single()
        assertIs<IllegalStateException>(suppressed)
        assertEquals(storageError.message, suppressed.message)
    }

    @Test
    fun theListenerFinishesEvenIfTheCallerIsCancelledMeanwhile() = runTest {
        val fixture = fixtureAnswering(HttpStatusCode.Unauthorized)
        val listenerStarted = CompletableDeferred<Unit>()
        val listenerFinished = CompletableDeferred<Unit>()
        val session = fixture.gateway.openSession("tok-1") {
            listenerStarted.complete(Unit)
            delay(100.milliseconds)
            listenerFinished.complete(Unit)
        }
        val outcome = CompletableDeferred<Throwable?>()
        val caller = backgroundScope.launch {
            try {
                session.probe()
                outcome.complete(null)
            } catch (thrown: Throwable) {
                outcome.complete(thrown)
            }
        }
        listenerStarted.await()

        caller.cancel()
        caller.join()

        assertTrue(listenerFinished.isCompleted, "the session must be told even when nobody waits for the answer")
        assertIs<CancellationException>(outcome.await())
    }

    // ---- closing ----

    @Test
    fun closingASessionCancelsItsRunningRequests() = runTest {
        val started = CompletableDeferred<Unit>()
        val fixture = GatewayFixture {
            started.complete(Unit)
            CompletableDeferred<Unit>().await()
            json(Spec.UserIdAnswer)
        }
        val session = fixture.gateway.openSession("tok-1", ignoreRejection)
        val outcome = CompletableDeferred<Throwable?>()
        backgroundScope.launch {
            try {
                session.probe()
                outcome.complete(null)
            } catch (thrown: Throwable) {
                outcome.complete(thrown)
            }
        }
        started.await()

        session.close()

        val thrown = outcome.await()
        assertFalse(thrown is RemoteFailure, "closing the session is not a failure of the network")
        assertIs<CancellationException>(thrown)
    }

    @Test
    fun aClosedSessionRefusesNewRequests() = runTest {
        val fixture = GatewayFixture { json(Spec.UserIdAnswer) }
        val session = fixture.gateway.openSession("tok-1", ignoreRejection)
        session.probe()

        session.close()

        assertFailsWith<CancellationException> { session.probe() }
        assertEquals(1, fixture.requests.size, "nothing is sent after the close")
    }

    @Test
    fun closingTwiceIsHarmless() = runTest {
        val fixture = GatewayFixture { json(Spec.UserIdAnswer) }
        val session = fixture.gateway.openSession("tok-1", ignoreRejection)

        session.close()
        session.close()

        assertTrue(fixture.engineIsOpen)
    }

    @Test
    fun closingASessionLeavesTheEngineAndTheAccountAlone() = runTest {
        val fixture = GatewayFixture { request ->
            if (request.url.encodedPath.endsWith("login")) json(Spec.LoginAnswer) else json(Spec.UserIdAnswer)
        }
        val session = fixture.gateway.openSession("tok-1", ignoreRejection)
        session.close()

        assertTrue(fixture.engineIsOpen, "the engine belongs to the Gateway, not to the session")
        assertEquals(Spec.UserId, fixture.account.login(Spec.credentials).userId)
        assertEquals(Spec.UserId, fixture.account.currentSession("tok-2").userId)
    }

    @Test
    fun closingASessionLeavesOtherSessionsAlone() = runTest {
        val fixture = GatewayFixture { json(Spec.UserIdAnswer) }
        val closed = fixture.gateway.openSession("tok-1", ignoreRejection)
        val open = fixture.gateway.openSession("tok-2", ignoreRejection)
        closed.close()

        assertEquals(Spec.UserId, open.probe().userId)
        assertEquals(listOf("Bearer tok-2"), fixture.requests.single().authorizations)
    }

    @Test
    fun closingASessionDoesNotCancelTheRunningRequestsOfAnother() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fixture = GatewayFixture { request ->
            val started = if (request.authorizations == listOf("Bearer tok-1")) firstStarted else secondStarted
            started.complete(Unit)
            release.await()
            json(Spec.UserIdAnswer)
        }
        val closing = fixture.gateway.openSession("tok-1", ignoreRejection)
        val staying = fixture.gateway.openSession("tok-2", ignoreRejection)
        val closingOutcome = CompletableDeferred<Throwable?>()
        val stayingOutcome = CompletableDeferred<Throwable?>()
        backgroundScope.launch {
            closingOutcome.complete(runCatching { closing.probe() }.exceptionOrNull())
        }
        backgroundScope.launch {
            stayingOutcome.complete(runCatching { staying.probe() }.exceptionOrNull())
        }
        firstStarted.await()
        secondStarted.await()

        closing.close()
        // The handler is still held, so this request can only end because the session was closed.
        assertIs<CancellationException>(closingOutcome.await())
        release.complete(Unit)

        assertEquals(null, stayingOutcome.await())
    }

    @Test
    fun aSessionKeepsWorkingUntilItIsClosed() = runTest {
        val fixture = GatewayFixture { request ->
            if (request.method == HttpMethod.Get) json(Spec.UserIdAnswer) else respond("", HttpStatusCode.NoContent)
        }
        val session = fixture.gateway.openSession("tok-1", ignoreRejection)

        repeat(3) { session.probe() }
        session.requests.delete("probe")

        assertEquals(4, fixture.requests.size)
        assertTrue(fixture.engineIsOpen)
    }

    @Test
    fun theTransportFailuresOfASessionAreMappedAsEverywhere() = runTest {
        val fixture = GatewayFixture { throw IOException("connection reset") }
        val session = fixture.gateway.openSession("tok-1", ignoreRejection)

        failureOf<RemoteFailure.NoConnection> { session.probe() }
    }
}
