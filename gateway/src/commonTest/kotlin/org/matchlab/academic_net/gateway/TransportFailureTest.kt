package org.matchlab.academic_net.gateway

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutCapability
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.ConfirmEmailRequestDto
import org.matchlab.academic_net.gateway.dto.LoginResultDto
import org.matchlab.academic_net.gateway.internal.GatewayJson
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

/** Everything that goes wrong before there is an answer, and answers that cannot be read. */
class TransportFailureTest {
    private class NoRouteToHost : IOException("No route to host")

    // ---- no connection ----

    @Test
    fun anIoErrorIsNoConnection() = runTest {
        val fixture = GatewayFixture { throw IOException("Unable to resolve host \"api.matchlab.space\"") }

        val failure = failureOf<RemoteFailure.NoConnection> { fixture.account.login(Spec.credentials) }

        assertIs<IOException>(failure.cause)
        assertTrue("Unable to resolve host" in failure.cause?.message.orEmpty())
    }

    @Test
    fun anySubclassOfIoErrorIsNoConnection() = runTest {
        val fixture = GatewayFixture { throw NoRouteToHost() }

        val failure = failureOf<RemoteFailure.NoConnection> { fixture.account.register(Spec.credentials) }

        assertIs<NoRouteToHost>(failure.cause)
    }

    @Test
    fun everyCallMapsAnIoErrorTheSameWay() = runTest {
        val fixture = GatewayFixture { throw IOException("connection reset") }

        failureOf<RemoteFailure.NoConnection> { fixture.account.register(Spec.credentials) }
        failureOf<RemoteFailure.NoConnection> { fixture.account.confirmEmail(ConfirmEmailRequestDto("t")) }
        failureOf<RemoteFailure.NoConnection> { fixture.account.login(Spec.credentials) }
        failureOf<RemoteFailure.NoConnection> { fixture.account.currentSession("t") }
        failureOf<RemoteFailure.NoConnection> { fixture.account.revokeSession("t") }
    }

    // ---- timeouts ----

    @Test
    fun theTimeoutOfARequestIsATimeoutNotAConnectionError() = runTest {
        // HttpRequestTimeoutException is an IOException, so the order of the checks matters.
        val fixture = GatewayFixture { throw HttpRequestTimeoutException("https://api.matchlab.space", 20_000) }

        val failure = failureOf<RemoteFailure.Timeout> { fixture.account.login(Spec.credentials) }

        assertIs<HttpRequestTimeoutException>(failure.cause)
    }

    @Test
    fun aConnectTimeoutIsATimeout() = runTest {
        val fixture = GatewayFixture { throw ConnectTimeoutException("Connect timeout has expired") }

        val failure = failureOf<RemoteFailure.Timeout> { fixture.account.login(Spec.credentials) }

        assertIs<ConnectTimeoutException>(failure.cause)
    }

    @Test
    fun aSocketTimeoutIsATimeout() = runTest {
        val fixture = GatewayFixture { throw SocketTimeoutException("Read timed out") }

        val failure = failureOf<RemoteFailure.Timeout> { fixture.account.currentSession("t") }

        assertIs<SocketTimeoutException>(failure.cause)
    }

    @Test
    fun aServerThatDoesNotAnswerHitsTheRequestTimeoutOfTheClient() = runTest {
        val fixture = GatewayFixture(GatewayConfig(requestTimeout = 100.milliseconds)) {
            delay(30.seconds)
            json(Spec.LoginAnswer)
        }

        val failure = failureOf<RemoteFailure.Timeout> { fixture.account.login(Spec.credentials) }

        assertIs<HttpRequestTimeoutException>(failure.cause)
    }

    @Test
    fun aSlowAnswerWithinTheTimeoutIsAnAnswer() = runTest {
        val fixture = GatewayFixture(GatewayConfig(requestTimeout = 5.seconds)) {
            delay(50.milliseconds)
            json(Spec.LoginAnswer)
        }

        assertEquals(Spec.UserId, fixture.account.login(Spec.credentials).userId)
    }

    @Test
    fun theTimeoutsOfTheConfigReachTheEngine() = runTest {
        val defaults = GatewayFixture { json(Spec.LoginAnswer) }
        val custom = GatewayFixture(GatewayConfig(connectTimeout = 3.seconds, requestTimeout = 7.seconds)) {
            json(Spec.LoginAnswer)
        }

        defaults.account.login(Spec.credentials)
        custom.account.login(Spec.credentials)

        val byDefault = assertNotNull(defaults.requests.single().getCapabilityOrNull(HttpTimeoutCapability))
        assertEquals(10_000L, byDefault.connectTimeoutMillis)
        assertEquals(20_000L, byDefault.requestTimeoutMillis)
        assertEquals(20_000L, byDefault.socketTimeoutMillis)
        val configured = assertNotNull(custom.requests.single().getCapabilityOrNull(HttpTimeoutCapability))
        assertEquals(3_000L, configured.connectTimeoutMillis)
        assertEquals(7_000L, configured.requestTimeoutMillis)
        assertEquals(7_000L, configured.socketTimeoutMillis)
    }

    // ---- what is not a failure of the network ----

    @Test
    fun aBugIsNotDressedUpAsAFailureOfTheNetwork() = runTest {
        val fixture = GatewayFixture { throw IllegalStateException("handler bug") }

        // assertFailsWith checks the exact type: it is the handler's own exception, not a RemoteFailure.
        val thrown = assertFailsWith<IllegalStateException> { fixture.account.login(Spec.credentials) }

        assertEquals("handler bug", thrown.message)
    }

    @Test
    fun cancellingTheCallerEndsTheCallWithCancellationNotAFailure() = runTest {
        val started = CompletableDeferred<Unit>()
        val fixture = GatewayFixture {
            started.complete(Unit)
            CompletableDeferred<Unit>().await()
            json(Spec.LoginAnswer)
        }
        val outcome = CompletableDeferred<Throwable?>()
        val caller = backgroundScope.launch {
            try {
                fixture.account.login(Spec.credentials)
                outcome.complete(null)
            } catch (thrown: Throwable) {
                outcome.complete(thrown)
            }
        }
        started.await()

        caller.cancel()

        val thrown = outcome.await()
        assertFalse(thrown is RemoteFailure)
        assertIs<CancellationException>(thrown)
    }

    @Test
    fun aCallerCancelledBecauseOfAnIoErrorStaysCancelled() = runTest {
        // Ktor unwraps a cancellation to its cause: without a check the caller would see the IOException below
        // as a NoConnection, although the reason it stopped is that its own scope was cancelled.
        val started = CompletableDeferred<Unit>()
        val fixture = GatewayFixture {
            started.complete(Unit)
            CompletableDeferred<Unit>().await()
            json(Spec.LoginAnswer)
        }
        val outcome = CompletableDeferred<Throwable?>()
        val caller = backgroundScope.launch {
            try {
                fixture.account.login(Spec.credentials)
                outcome.complete(null)
            } catch (thrown: Throwable) {
                outcome.complete(thrown)
            }
        }
        started.await()

        caller.cancel(CancellationException("scope closed", IOException("socket closed")))

        val thrown = outcome.await()
        assertIs<CancellationException>(thrown)
    }

    // ---- unreadable answers ----

    @Test
    fun brokenJsonIsMalformedAndKeepsTheReason() = runTest {
        val fixture = GatewayFixture { json("""{"user_id": """) }

        val failure = failureOf<RemoteFailure.Malformed> { fixture.account.login(Spec.credentials) }

        assertIs<SerializationException>(failure.cause)
    }

    @Test
    fun theCauseOfMalformedNeverQuotesTheAnswer() = runTest {
        // kotlinx.serialization appends "JSON input: <piece of the text>" to a syntax error; the answer of login
        // holds the token, and causes end up in logs and crash reports.
        val brokenLogin = """{"user_id":"u1","access_token":"SECRET-TOKEN-1234",,}"""
        val raw = assertFailsWith<SerializationException> { GatewayJson.decodeFromString<LoginResultDto>(brokenLogin) }
        assertTrue("SECRET-TOKEN-1234" in raw.message.orEmpty(), "the precondition: the library quotes the text")
        val fixture = GatewayFixture { json(brokenLogin) }

        val failure = failureOf<RemoteFailure.Malformed> { fixture.account.login(Spec.credentials) }

        val cause = assertIs<SerializationException>(failure.cause)
        assertFalse("SECRET-TOKEN-1234" in cause.message.orEmpty())
        assertFalse("SECRET-TOKEN-1234" in failure.toString())
        assertFalse("SECRET-TOKEN-1234" in failure.stackTraceToString())
        assertTrue(cause.message.orEmpty().isNotBlank())
    }

    @Test
    fun aFailureOfTheShapeOfTheAnswerIsMalformedToo() = runTest {
        val fixture = GatewayFixture { json("""{"user_id":"u1","access_token":{"nested":true}}""") }

        val failure = failureOf<RemoteFailure.Malformed> { fixture.account.login(Spec.credentials) }

        assertIs<SerializationException>(failure.cause)
    }
}
