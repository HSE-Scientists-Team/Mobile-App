package org.matchlab.academic_net.gateway

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.internal.asRemoteFailure
import org.matchlab.academic_net.gateway.internal.httpFailure
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The translation of an exception into what the caller sees, without a client in between. */
class FailureMappingTest {
    @Test
    fun aRemoteFailureStaysWhatItIs() {
        val failure = RemoteFailure.Http(409, "x")

        assertSame(failure, failure.asRemoteFailure())
    }

    @Test
    fun aCancellationStaysWhatItIs() {
        val cancellation = CancellationException("cancelled")

        assertSame(cancellation, cancellation.asRemoteFailure())
    }

    @Test
    fun aCancellationWithAnIoCauseIsStillACancellation() {
        val cancellation = CancellationException("scope closed", IOException("socket closed"))

        assertSame(cancellation, cancellation.asRemoteFailure())
    }

    @Test
    fun theThreeTimeoutsOfKtorAreTimeouts() {
        val timeouts = listOf(
            HttpRequestTimeoutException("https://api.matchlab.space", 20_000),
            ConnectTimeoutException("connect"),
            SocketTimeoutException("read"),
        )
        for (timeout in timeouts) {
            val mapped = timeout.asRemoteFailure()

            assertIs<RemoteFailure.Timeout>(mapped)
            assertSame(timeout, mapped.cause)
        }
    }

    @Test
    fun otherIoErrorsAreNoConnection() {
        val io = IOException("connection reset")

        val mapped = io.asRemoteFailure()

        assertIs<RemoteFailure.NoConnection>(mapped)
        assertSame(io, mapped.cause)
    }

    @Test
    fun aSerializationErrorIsMalformed() {
        assertIs<RemoteFailure.Malformed>(SerializationException("bad field").asRemoteFailure())
    }

    @Test
    fun aSerializationErrorWithoutAMessageIsMalformedToo() {
        val mapped = SerializationException(null as String?).asRemoteFailure()

        assertIs<RemoteFailure.Malformed>(mapped)
        assertNull(mapped.cause?.message)
    }

    @Test
    fun aSerializationErrorWithoutTheQuotedInputKeepsItsOwnException() {
        val error = SerializationException("Field 'user_id' is required at path: $")

        val mapped = error.asRemoteFailure()

        assertIs<RemoteFailure.Malformed>(mapped)
        assertSame(error, mapped.cause)
    }

    @Test
    fun theQuotedInputIsCutOutOfTheMessage() {
        val error = SerializationException("Unexpected token at path: \$.x\nJSON input: {\"access_token\":\"secret\"}")

        val mapped = error.asRemoteFailure()

        assertIs<RemoteFailure.Malformed>(mapped)
        assertEquals("Unexpected token at path: \$.x", mapped.cause?.message)
    }

    @Test
    fun anythingElseIsABugAndIsReturnedAsItIs() {
        val bug = IllegalStateException("bug")
        val argument = IllegalArgumentException("bad argument")
        val error = AssertionError("an Error is not a failure of the network either")

        assertSame(bug, bug.asRemoteFailure())
        assertSame(argument, argument.asRemoteFailure())
        assertSame(error, error.asRemoteFailure())
    }

    @Test
    fun anHttpFailureHasNoServerMessageWhenTheBodyCouldNotBeRead() {
        val failure = httpFailure(500, null)

        assertEquals(500, failure.status)
        assertNull(failure.serverMessage)
    }
}
