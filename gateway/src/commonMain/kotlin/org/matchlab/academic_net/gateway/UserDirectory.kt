package org.matchlab.academic_net.gateway

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Public profiles by user id, shared by every feature of one session (the profile screens, the feed's header, the
 * owner of a project): the API has no list of profiles by ids (API-GAP G47), so each person is a request of their
 * own and the directory keeps the number of them down.
 *
 * - **One request per id at a time.** Callers that ask for a person while a request for them is running wait for
 *   that answer. The request runs in [scope], not in the caller: a caller that gives up (its screen was left) does
 *   not take the answer away from the others.
 * - **At most [maxParallel] requests in flight** across all people.
 * - **Answers are kept for [ttl].** "There is no such person" (a 400, 403 or 404) is an answer too and is kept the
 *   same way: a profile created in the last minutes may show as missing to others until the time is up. A failure
 *   (no connection, a server error) is thrown to the callers that waited for it and is not kept.
 * - **[invalidate]** forgets a person, for when the profile was changed by this client.
 *
 * The constructor is public so that the features' data layers can test against a fake [fetch] without Ktor.
 *
 * @param fetch the `GET /api/v1/users/{user_id}/profile` call; throws `RemoteFailure`. In the app: the client of
 * the session.
 * @param scope runs the requests; cancelling it fails the waiting callers with a `CancellationException`.
 * @param timeSource the clock of [ttl]; a test passes a controllable one.
 */
class UserDirectory(
    private val fetch: suspend (userId: String) -> UserProfileDto,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val ttl: Duration = 5.minutes,
    maxParallel: Int = DefaultMaxParallel,
) {
    init {
        require(ttl.isPositive()) { "The time to keep an answer must be positive: $ttl" }
        require(maxParallel > 0) { "At least one request must be allowed at a time: $maxParallel" }
    }

    /** An answer (`null`: no such person) and when it arrived. */
    private class Answer(val profile: UserProfileDto?, val arrivedAt: TimeMark)

    /**
     * A running request that the callers for the same person wait for. It ends with the [Result] of the request,
     * never with its failure: a failed request must not be a failed child of [scope] and take the other people's
     * requests down with it, whatever kind of job the scope has.
     */
    private class Flight(val outcome: Deferred<Result<UserProfileDto?>>)

    // All the state below is touched only under [lock]; a request runs outside it.
    private val lock = Mutex()
    private val answers = mutableMapOf<String, Answer>()
    private val flights = mutableMapOf<String, Flight>()

    // Bumped by [invalidate]: an answer to a request that began before it is older than the change and is not kept.
    private val versions = mutableMapOf<String, Int>()
    private val permits = Semaphore(maxParallel)

    /**
     * The public profile of [userId], or `null` when there is no such person (the API answers 400, 403 or 404 for
     * it) or [userId] is blank (no request is made). Throws the `RemoteFailure` of a failed request.
     */
    suspend fun profile(userId: String): UserProfileDto? {
        if (userId.isBlank()) return null
        val result = lock.withLock {
            val kept = answers[userId]
            if (kept != null && kept.arrivedAt.elapsedNow() < ttl) return kept.profile
            answers.remove(userId)
            flights.getOrPut(userId) { startFlight(userId) }.outcome
        }
        return result.await().getOrThrow()
    }

    /**
     * The profiles of [userIds], each distinct id once and at most [maxParallel] at a time: a map with the given
     * ids as keys, `null` for the people who do not exist and for the blank ids. Throws the first failure; the
     * answers that had already arrived stay kept.
     */
    suspend fun profiles(userIds: Collection<String>): Map<String, UserProfileDto?> = coroutineScope {
        userIds.distinct().map { id -> async { id to profile(id) } }.awaitAll().toMap()
    }

    /**
     * Forgets [userId]: the next [profile] asks the server again. A request that is running for them while this is
     * called still answers its callers, but its answer is not kept, as it may be older than the change.
     */
    suspend fun invalidate(userId: String) {
        lock.withLock {
            answers.remove(userId)
            flights.remove(userId)
            versions[userId] = (versions[userId] ?: 0) + 1
        }
    }

    /** Starts the request for [userId]; the caller holds [lock] and registers the flight. */
    private fun startFlight(userId: String): Flight {
        val version = versions[userId] ?: 0
        lateinit var flight: Flight
        // Lazy, so that the flight is registered before the request can finish and look for itself in the map.
        val outcome = scope.async(start = CoroutineStart.LAZY) {
            val result = try {
                Result.success(permits.withPermit { lookup(userId) })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
            lock.withLock {
                if (flights[userId] === flight) flights.remove(userId)
                if (result.isSuccess && (versions[userId] ?: 0) == version) {
                    answers[userId] = Answer(result.getOrNull(), timeSource.markNow())
                }
            }
            result
        }
        flight = Flight(outcome)
        outcome.start()
        return flight
    }

    /** One request; the answers that mean "no such person" are `null`. */
    private suspend fun lookup(userId: String): UserProfileDto? =
        try {
            fetch(userId)
        } catch (failure: RemoteFailure.Http) {
            if (failure.status in NoSuchPerson) null else throw failure
        }

    private companion object {
        const val DefaultMaxParallel = 4

        /** 400 (not a user id), 403 (not visible to the viewer) and 404 (unknown): the person is not there for the viewer. */
        val NoSuchPerson = setOf(400, 403, 404)
    }
}
