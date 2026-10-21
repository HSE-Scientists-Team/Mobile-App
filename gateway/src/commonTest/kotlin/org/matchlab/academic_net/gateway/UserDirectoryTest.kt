package org.matchlab.academic_net.gateway

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * The directory of public profiles: one request per person at a time, a limit of requests in flight, answers kept for a
 * while, "no such person" as an answer, and the ways it lets go of a request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserDirectoryTest {
    private fun profileOf(id: String) = UserProfileDto(userId = id, details = UserProfileInputDto(displayName = "name-$id"))

    /** The `GET /users/{id}/profile` call: counts the requests and can hold or fail the answer of a person. */
    private class Fetcher {
        val calls = mutableListOf<String>()
        val failures = mutableMapOf<String, Throwable>()
        private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        private var gateOfAll: CompletableDeferred<Unit>? = null
        var inFlight = 0
        var maxInFlight = 0

        /** The answer of [id] waits for [release]. */
        fun hold(id: String) {
            gates[id] = CompletableDeferred()
        }

        fun release(id: String) {
            gates.getValue(id).complete(Unit)
        }

        /** Every answer waits for [releaseAll]. */
        fun holdAll() {
            gateOfAll = CompletableDeferred()
        }

        fun releaseAll() {
            gateOfAll?.complete(Unit)
        }

        suspend fun fetch(id: String): UserProfileDto {
            calls += id
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            try {
                gates[id]?.await()
                gateOfAll?.await()
                failures[id]?.let { throw it }
                return UserProfileDto(userId = id, details = UserProfileInputDto(displayName = "name-$id"))
            } finally {
                inFlight--
            }
        }
    }

    private fun TestScope.directory(
        fetcher: Fetcher,
        time: TestTimeSource = TestTimeSource(),
        ttl: Duration = 5.minutes,
        maxParallel: Int = 4,
    ) = UserDirectory(fetcher::fetch, backgroundScope, time, ttl, maxParallel)

    // ---- one request per person ----

    @Test
    fun callersAskingForTheSamePersonShareOneRequest() = runTest {
        val fetcher = Fetcher().apply { hold("a") }
        val directory = directory(fetcher)

        val callers = List(3) { async { directory.profile("a") } }
        runCurrent()
        assertEquals(listOf("a"), fetcher.calls, "the second and the third caller wait for the request of the first")
        fetcher.release("a")

        assertEquals(List(3) { profileOf("a") }, callers.awaitAll())
        assertEquals(listOf("a"), fetcher.calls)
    }

    @Test
    fun differentPeopleAreDifferentRequests() = runTest {
        val fetcher = Fetcher()
        val directory = directory(fetcher)

        assertEquals(profileOf("a"), directory.profile("a"))
        assertEquals(profileOf("b"), directory.profile("b"))

        assertEquals(listOf("a", "b"), fetcher.calls)
    }

    @Test
    fun aBlankIdIsNobodyAndAsksNothing() = runTest {
        val fetcher = Fetcher()
        val directory = directory(fetcher)

        assertNull(directory.profile(""))
        assertNull(directory.profile("   "))

        assertEquals(emptyList(), fetcher.calls)
    }

    // ---- how long an answer is kept ----

    @Test
    fun anAnswerIsKeptForTheTimeToLiveAndNotAMomentLonger() = runTest {
        val fetcher = Fetcher()
        val time = TestTimeSource()
        val directory = directory(fetcher, time)

        directory.profile("a")
        time += 4.minutes + 59.seconds
        directory.profile("a")
        assertEquals(1, fetcher.calls.size, "just before the end of the time the answer is still kept")

        time += 1.seconds
        directory.profile("a")
        assertEquals(2, fetcher.calls.size, "at the end of the time the person is asked for again")
    }

    @Test
    fun theTimeCountsFromTheAnswerNotFromTheAsk() = runTest {
        val fetcher = Fetcher().apply { hold("a") }
        val time = TestTimeSource()
        val directory = directory(fetcher, time, ttl = 1.minutes)
        val caller = async { directory.profile("a") }
        runCurrent()

        time += 90.seconds // longer than the time to live, but the answer is not there yet
        fetcher.release("a")
        caller.await()
        directory.profile("a")

        assertEquals(1, fetcher.calls.size)
    }

    @Test
    fun theTimeToLiveIsOnePersonAtATime() = runTest {
        val fetcher = Fetcher()
        val time = TestTimeSource()
        val directory = directory(fetcher, time, ttl = 1.minutes)

        directory.profile("a")
        time += 40.seconds
        directory.profile("b")
        time += 40.seconds
        directory.profile("a")
        directory.profile("b")

        // "a" is 80 seconds old and is asked for again; "b" is 40 seconds old and is kept.
        assertEquals(listOf("a", "b", "a"), fetcher.calls)
    }

    // ---- no such person ----

    @Test
    fun noSuchPersonIsAnAnswerAndIsKeptLikeAProfile() = runTest {
        for (status in listOf(400, 403, 404)) {
            val fetcher = Fetcher().apply { failures["a"] = RemoteFailure.Http(status) }
            val directory = directory(fetcher)

            assertNull(directory.profile("a"), "status $status")
            assertNull(directory.profile("a"), "status $status")

            assertEquals(listOf("a"), fetcher.calls, "status $status is not asked twice")
        }
    }

    @Test
    fun aServerErrorIsNotAnAnswerAboutAPerson() = runTest {
        for (status in listOf(401, 409, 500, 503)) {
            val fetcher = Fetcher().apply { failures["a"] = RemoteFailure.Http(status) }
            val directory = directory(fetcher)

            val failure = assertFailsWith<RemoteFailure.Http> { directory.profile("a") }

            assertEquals(status, failure.status)
        }
    }

    // ---- failures ----

    @Test
    fun aFailureIsThrownToEveryCallerThatWaitedForItAndIsNotKept() = runTest {
        val fetcher = Fetcher().apply {
            hold("a")
            failures["a"] = RemoteFailure.NoConnection()
        }
        val directory = directory(fetcher)
        val first = async { runCatching { directory.profile("a") } }
        val second = async { runCatching { directory.profile("a") } }
        runCurrent()
        fetcher.release("a")

        assertIs<RemoteFailure.NoConnection>(first.await().exceptionOrNull())
        assertIs<RemoteFailure.NoConnection>(second.await().exceptionOrNull())
        assertEquals(listOf("a"), fetcher.calls, "both callers waited for one request")

        // The failure was not kept: the next call asks again, and now the network is back.
        fetcher.failures.clear()
        assertEquals(profileOf("a"), directory.profile("a"))
        assertEquals(listOf("a", "a"), fetcher.calls)
    }

    @Test
    fun aFailureOfOnePersonDoesNotTakeAnotherDownWhateverTheJobOfTheScope() = runTest {
        // A plain Job, not a SupervisorJob: a child that failed would cancel its parent and the other children.
        val scope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        val fetcher = Fetcher().apply {
            hold("slow")
            failures["bad"] = IllegalStateException("bug in the fetch")
        }
        val directory = UserDirectory(fetcher::fetch, scope)
        val slow = async { directory.profile("slow") }
        runCurrent()

        assertFailsWith<IllegalStateException> { directory.profile("bad") }
        fetcher.release("slow")

        assertEquals(profileOf("slow"), slow.await())
        scope.cancel()
    }

    // ---- requests in flight ----

    @Test
    fun atMostFourRequestsAreInFlightAtATime() = runTest {
        val fetcher = Fetcher().apply { holdAll() }
        val directory = directory(fetcher)
        val ids = (1..10).map { "p$it" }

        val result = async { directory.profiles(ids) }
        runCurrent()
        assertEquals(4, fetcher.inFlight)
        assertEquals(4, fetcher.calls.size, "the other six wait for a place")

        fetcher.releaseAll()
        val profiles = result.await()

        assertEquals(4, fetcher.maxInFlight)
        assertEquals(ids.toSet(), profiles.keys)
        assertEquals(ids.toSet(), fetcher.calls.toSet())
    }

    @Test
    fun theLimitOfRequestsInFlightCanBeChosen() = runTest {
        val fetcher = Fetcher().apply { holdAll() }
        val directory = directory(fetcher, maxParallel = 1)

        val result = async { directory.profiles(listOf("a", "b", "c")) }
        runCurrent()
        assertEquals(1, fetcher.inFlight)
        fetcher.releaseAll()
        result.await()

        assertEquals(1, fetcher.maxInFlight)
    }

    // ---- several people ----

    @Test
    fun severalPeopleAreEachDistinctIdOnceAndBlankOnesAreNobody() = runTest {
        val fetcher = Fetcher()
        val directory = directory(fetcher)

        val result = directory.profiles(listOf("a", "b", "a", "", " "))

        assertEquals(setOf("a", "b"), fetcher.calls.toSet())
        assertEquals(2, fetcher.calls.size)
        assertEquals(setOf("a", "b", "", " "), result.keys)
        assertEquals(profileOf("a"), result["a"])
        assertNull(result[""])
        assertNull(result[" "])
    }

    @Test
    fun severalPeopleWhoDoNotExistAreNullsInTheMap() = runTest {
        val fetcher = Fetcher().apply { failures["gone"] = RemoteFailure.Http(404) }
        val directory = directory(fetcher)

        val result = directory.profiles(listOf("here", "gone"))

        assertEquals(profileOf("here"), result["here"])
        assertEquals(true, "gone" in result)
        assertNull(result["gone"])
    }

    @Test
    fun noPeopleAreAnEmptyMapAndNoRequest() = runTest {
        val fetcher = Fetcher()

        assertEquals(emptyMap(), directory(fetcher).profiles(emptyList()))
        assertEquals(emptyList(), fetcher.calls)
    }

    @Test
    fun severalPeopleThrowTheFailureAndKeepWhatHadArrived() = runTest {
        val fetcher = Fetcher().apply { failures["bad"] = RemoteFailure.Http(500) }
        val directory = directory(fetcher)

        assertFailsWith<RemoteFailure.Http> { directory.profiles(listOf("ok", "bad")) }
        advanceUntilIdle()
        val asked = fetcher.calls.size

        assertEquals(profileOf("ok"), directory.profile("ok"))
        assertEquals(asked, fetcher.calls.size, "the answer for \"ok\" arrived and is kept")
    }

    // ---- forgetting ----

    @Test
    fun forgettingAPersonMakesTheNextCallAskAgain() = runTest {
        val fetcher = Fetcher()
        val directory = directory(fetcher)
        directory.profile("a")

        directory.invalidate("a")
        directory.profile("a")

        assertEquals(listOf("a", "a"), fetcher.calls)
    }

    @Test
    fun forgettingOnePersonLeavesTheOthers() = runTest {
        val fetcher = Fetcher()
        val directory = directory(fetcher)
        directory.profile("a")
        directory.profile("b")

        directory.invalidate("a")
        directory.profile("b")

        assertEquals(listOf("a", "b"), fetcher.calls)
    }

    @Test
    fun forgettingAPersonNobodyAskedForIsHarmless() = runTest {
        val fetcher = Fetcher()
        val directory = directory(fetcher)

        directory.invalidate("never-asked")

        assertEquals(profileOf("a"), directory.profile("a"))
    }

    @Test
    fun theAnswerOfARequestThatWasRunningWhenThePersonWasForgottenIsNotKept() = runTest {
        val fetcher = Fetcher().apply { hold("a") }
        val directory = directory(fetcher)
        val caller = async { directory.profile("a") }
        runCurrent()

        directory.invalidate("a")
        fetcher.release("a")
        // The caller that asked before the change still gets what the request brought...
        assertEquals(profileOf("a"), caller.await())
        // ...but it may be older than the change, so the next call asks again.
        fetcher.hold("a")
        val next = async { directory.profile("a") }
        runCurrent()
        assertEquals(listOf("a", "a"), fetcher.calls)
        fetcher.release("a")
        next.await()
    }

    @Test
    fun aPersonAskedForAfterBeingForgottenGetsARequestOfTheirOwn() = runTest {
        val fetcher = Fetcher().apply { hold("a") }
        val directory = directory(fetcher)
        val before = async { directory.profile("a") }
        runCurrent()

        directory.invalidate("a")
        val after = async { directory.profile("a") }
        runCurrent()

        assertEquals(listOf("a", "a"), fetcher.calls, "the first request may be older than the change")
        fetcher.release("a")
        before.await()
        after.await()
        // The answer of the request that began after the change is the one that is kept.
        directory.profile("a")
        assertEquals(2, fetcher.calls.size)
    }

    // ---- letting go ----

    @Test
    fun aCallerThatGivesUpDoesNotTakeTheAnswerAwayFromTheOthers() = runTest {
        val fetcher = Fetcher().apply { hold("a") }
        val directory = directory(fetcher)
        val leaving = async { directory.profile("a") }
        val staying = async { directory.profile("a") }
        runCurrent()

        leaving.cancel()
        runCurrent()
        fetcher.release("a")

        assertEquals(profileOf("a"), staying.await())
        assertEquals(listOf("a"), fetcher.calls)
        // The request ran to the end and its answer is kept.
        directory.profile("a")
        assertEquals(1, fetcher.calls.size)
    }

    @Test
    fun aRequestWhoseCallersAllGaveUpStillKeepsItsAnswer() = runTest {
        val fetcher = Fetcher().apply { hold("a") }
        val directory = directory(fetcher)
        val leaving = async { directory.profile("a") }
        runCurrent()

        leaving.cancel()
        runCurrent()
        fetcher.release("a")
        advanceUntilIdle()

        assertEquals(profileOf("a"), directory.profile("a"))
        assertEquals(1, fetcher.calls.size)
    }

    @Test
    fun cancellingTheScopeEndsTheWaitingCallersWithACancellation() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val fetcher = Fetcher().apply { hold("a") }
        val directory = UserDirectory(fetcher::fetch, scope)
        val first = async { runCatching { directory.profile("a") } }
        val second = async { runCatching { directory.profile("a") } }
        runCurrent()

        scope.cancel()

        assertIs<CancellationException>(first.await().exceptionOrNull())
        assertIs<CancellationException>(second.await().exceptionOrNull())
        assertEquals(0, fetcher.inFlight, "the request itself was cancelled too")
    }

    // ---- construction ----

    @Test
    fun theTimeToLiveAndTheLimitMustBePositive() = runTest {
        val fetcher = Fetcher()

        assertFailsWith<IllegalArgumentException> { directory(fetcher, ttl = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { directory(fetcher, ttl = (-1).minutes) }
        assertFailsWith<IllegalArgumentException> { directory(fetcher, maxParallel = 0) }
        assertFailsWith<IllegalArgumentException> { directory(fetcher, maxParallel = -3) }
    }
}
