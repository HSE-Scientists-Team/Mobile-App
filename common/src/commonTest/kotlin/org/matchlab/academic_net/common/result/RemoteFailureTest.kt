package org.matchlab.academic_net.common.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class RemoteFailureTest {
    @Test
    fun httpKeepsStatusAndServerMessage() {
        val failure = RemoteFailure.Http(409, "email уже зарегистрирован")

        assertEquals(409, failure.status)
        assertEquals("email уже зарегистрирован", failure.serverMessage)
    }

    @Test
    fun httpWithoutServerMessageHasNone() {
        val failure = RemoteFailure.Http(503)

        assertNull(failure.serverMessage)
        assertNull(failure.cause)
    }

    @Test
    fun httpMessageNamesTheStatusButNeverRepeatsTheServerText() {
        val failure = RemoteFailure.Http(401, "неверный email или пароль")

        assertEquals("HTTP 401", failure.message)
        assertFalse("неверный" in failure.toString())
    }

    @Test
    fun transportFailuresKeepTheirCause() {
        val cause = IllegalStateException("socket closed")

        assertSame(cause, RemoteFailure.NoConnection(cause).cause)
        assertSame(cause, RemoteFailure.Timeout(cause).cause)
        assertSame(cause, RemoteFailure.Malformed(cause).cause)
    }

    @Test
    fun causeIsOptional() {
        assertNull(RemoteFailure.NoConnection().cause)
        assertNull(RemoteFailure.Timeout().cause)
        assertNull(RemoteFailure.Malformed().cause)
    }

    @Test
    fun everyCaseHasADiagnosticMessage() {
        assertEquals("No connection to the server", RemoteFailure.NoConnection().message)
        assertEquals("The server did not answer in time", RemoteFailure.Timeout().message)
        assertEquals("Unexpected answer from the server", RemoteFailure.Malformed().message)
    }

    @Test
    fun failureIsAnExceptionSoItCanBeThrownAndCaught() {
        val caught = try {
            throw RemoteFailure.Http(500)
        } catch (failure: Exception) {
            failure
        }

        assertIs<RemoteFailure.Http>(caught)
    }

    @Test
    fun theFourCasesAreExhaustive() {
        fun kind(failure: RemoteFailure): String = when (failure) {
            is RemoteFailure.NoConnection -> "no-connection"
            is RemoteFailure.Timeout -> "timeout"
            is RemoteFailure.Http -> "http"
            is RemoteFailure.Malformed -> "malformed"
        }

        assertEquals("no-connection", kind(RemoteFailure.NoConnection()))
        assertEquals("timeout", kind(RemoteFailure.Timeout()))
        assertEquals("http", kind(RemoteFailure.Http(404)))
        assertEquals("malformed", kind(RemoteFailure.Malformed()))
    }
}
