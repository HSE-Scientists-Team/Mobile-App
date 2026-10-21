package org.matchlab.academic_net.components.format

import kotlin.test.Test
import kotlin.test.assertEquals
import org.matchlab.academic_net.common.result.RemoteFailure

/** Which of the four lines of [failureText] a cause gets. */
class FailureTextTest {
    @Test
    fun noConnectionIsTold() {
        assertEquals(FailureKind.NoConnection, failureKind(RemoteFailure.NoConnection()))
    }

    @Test
    fun timeoutIsTold() {
        assertEquals(FailureKind.Timeout, failureKind(RemoteFailure.Timeout()))
    }

    @Test
    fun aStatusOf500OrMoreMeansTheServiceIsUnavailable() {
        listOf(500, 502, 503, 504, 599).forEach { status ->
            assertEquals(FailureKind.Unavailable, failureKind(RemoteFailure.Http(status)), "status $status")
        }
    }

    @Test
    fun aClientErrorOrARedirectIsSomethingElse() {
        listOf(301, 400, 401, 403, 404, 409, 410, 422, 429, 499).forEach { status ->
            assertEquals(FailureKind.Other, failureKind(RemoteFailure.Http(status)), "status $status")
        }
    }

    @Test
    fun theBorderOfTheServerErrorsIs500() {
        assertEquals(FailureKind.Other, failureKind(RemoteFailure.Http(499)))
        assertEquals(FailureKind.Unavailable, failureKind(RemoteFailure.Http(500)))
    }

    @Test
    fun theTextOfTheServerDoesNotChangeTheChoice() {
        assertEquals(FailureKind.Unavailable, failureKind(RemoteFailure.Http(503, "database is down")))
        assertEquals(FailureKind.Other, failureKind(RemoteFailure.Http(404, "service unavailable")))
        assertEquals(FailureKind.Other, failureKind(RemoteFailure.Http(409, "no connection")))
    }

    @Test
    fun anAnswerThatIsNotWhatTheApiPromisesIsSomethingElse() {
        assertEquals(FailureKind.Other, failureKind(RemoteFailure.Malformed()))
    }

    @Test
    fun anotherExceptionIsSomethingElse() {
        assertEquals(FailureKind.Other, failureKind(IllegalStateException("boom")))
        assertEquals(FailureKind.Other, failureKind(RuntimeException()))
    }

    @Test
    fun noCauseIsSomethingElse() {
        assertEquals(FailureKind.Other, failureKind(null))
    }

    @Test
    fun theCauseOfAnExceptionIsNotLookedAt() {
        // The gateway throws RemoteFailure itself; a wrapped one is not unwrapped, so the choice stays obvious.
        assertEquals(FailureKind.Other, failureKind(RuntimeException(RemoteFailure.Timeout())))
    }

    @Test
    fun aFailureWithACauseOfItsOwnIsToldByItsKind() {
        val socket = IllegalStateException("socket closed")

        assertEquals(FailureKind.NoConnection, failureKind(RemoteFailure.NoConnection(socket)))
        assertEquals(FailureKind.Timeout, failureKind(RemoteFailure.Timeout(socket)))
    }

    @Test
    fun everyKindOfFailureIsReached() {
        val kinds = listOf(
            RemoteFailure.NoConnection(),
            RemoteFailure.Timeout(),
            RemoteFailure.Http(500),
            RemoteFailure.Http(404),
            RemoteFailure.Malformed(),
        ).map(::failureKind).toSet()

        assertEquals(FailureKind.entries.toSet(), kinds)
    }
}
