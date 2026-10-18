package org.matchlab.academic_net.gateway

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.internal.ApiRequests
import org.matchlab.academic_net.gateway.internal.KtorAchievementsApi
import org.matchlab.academic_net.gateway.internal.KtorPeopleSearchApi
import org.matchlab.academic_net.gateway.internal.KtorProfilesApi
import org.matchlab.academic_net.gateway.internal.KtorRecommendationsApi
import org.matchlab.academic_net.gateway.internal.KtorReviewsApi
import org.matchlab.academic_net.gateway.internal.pathSegment
import kotlin.coroutines.cancellation.CancellationException

/**
 * The calls of one signed-in user. The token the session was opened with goes into every request; a 401
 * answer to any of them is passed to the [SessionRejectionListener] given to [Gateway.openSession].
 *
 * A session is created when the user signs in (or the app finds the stored session) and closed when the user
 * signs out, so that nothing of the old user's requests is still running when the next one signs in. The
 * calls of the user's data are the properties: [profiles], [achievements], [reviews] and [users] (the
 * profile blocks); the people are found with [peopleSearch] and recommended by [recommendations]. The calls of
 * projects are added by the blocks that need them, each as a property with its own interface over [requests].
 *
 * Every call throws `RemoteFailure` when it fails and lets a `CancellationException` through, like
 * [AccountApi].
 */
class GatewaySession internal constructor(
    private val http: HttpClient,
    /** Where the calls of this session are made from: the client with the token, and the 401 reporting. */
    internal val requests: ApiRequests,
) : AutoCloseable {
    // Runs the requests of [users], which outlive the callers that asked for them; cancelled by [close].
    private val directoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The public profiles by user id, for every feature of the session; the profile of the viewer is dropped from
     * it after the viewer saves it through [profiles].
     */
    val users: UserDirectory = UserDirectory(
        fetch = { userId -> requests.get<UserProfileDto>("users/${pathSegment(userId)}/profile") },
        scope = directoryScope,
    )

    /** The viewer's own profile. */
    val profiles: ProfilesApi = KtorProfilesApi(requests, onProfileSaved = users::invalidate)

    /** The works, diplomas and certificates of the viewer and of other people. */
    val achievements: AchievementsApi = KtorAchievementsApi(requests)

    /** The reviews about a person. */
    val reviews: ReviewsApi = KtorReviewsApi(requests)

    /** The people search of "08". */
    val peopleSearch: PeopleSearchApi = KtorPeopleSearchApi(requests)

    /** What the server recommends: the people of the feed ("01c"). */
    val recommendations: RecommendationsApi = KtorRecommendationsApi(requests)

    /**
     * Cancels the requests of this session that are still running (they end with a `CancellationException`,
     * also those the [users] directory runs for the callers waiting on them) and makes it refuse new ones the same
     * way. The engine and the other sessions are not touched, and [Gateway.account] keeps working: the client of
     * the session is its own, only the engine is shared.
     *
     * Ktor cancels the running requests when the client has finished winding down, a moment after this call
     * returns, so an answer that arrives in that moment may still be delivered to its caller. The owner of the
     * session cancels the coroutines of its callers as well; this is the guarantee that nothing is left in the
     * engine.
     *
     * Calling it again does nothing.
     */
    override fun close() {
        // Cancel before closing: closing a client only lets its running requests finish.
        directoryScope.cancel(CancellationException("The session was closed"))
        http.cancel(CancellationException("The session was closed"))
        http.close()
    }
}
