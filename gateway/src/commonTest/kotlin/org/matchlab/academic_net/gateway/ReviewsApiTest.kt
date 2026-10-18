package org.matchlab.academic_net.gateway

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.RemoteFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The reviews about a person: one page at a time, by the id of the person, the viewer included. */
class ReviewsApiTest {
    @Test
    fun aPageOfReviewsIsAskedByTheIdOfThePersonWithItsLimitAndOffset() = runTest {
        val fixture = SessionFixture { json(ProfileSpec.reviews(ProfileSpec.review("rev-1"), ProfileSpec.review("rev-2"))) }

        val page = fixture.session.reviews.userReviews(ProfileSpec.OtherId, limit = 20, offset = 40)

        val request = fixture.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/users/${ProfileSpec.OtherId}/reviews", request.url.encodedPath)
        assertEquals("20", request.url.parameters["limit"])
        assertEquals("40", request.url.parameters["offset"])
        assertEquals(listOf("rev-1", "rev-2"), page.items.map { it.id })
    }

    @Test
    fun theReviewsOfTheViewerAreAskedByTheirIdToo() = runTest {
        val fixture = SessionFixture { json(ProfileSpec.reviews()) }

        fixture.session.reviews.userReviews(ProfileSpec.MeId, limit = 2, offset = 0)

        // The API has a path of its own for "my reviews"; the client does not use it.
        assertEquals("/api/v1/users/${ProfileSpec.MeId}/reviews", fixture.requests.single().url.encodedPath)
    }

    @Test
    fun aReviewIsReadWithEveryField() = runTest {
        val fixture = SessionFixture { json(ProfileSpec.reviews(ProfileSpec.review())) }

        val review = fixture.session.reviews.userReviews(ProfileSpec.MeId, limit = 20, offset = 0).items.single()

        assertEquals("rev-1", review.id)
        assertEquals(ProfileSpec.OtherId, review.authorUserId)
        assertEquals("Елена", review.authorDisplayName)
        assertEquals("initiator", review.authorRole)
        assertEquals("Цифровой двойник", review.projectTitle)
        assertEquals("Отлично", review.title)
        assertEquals("Довёл до демо", review.text)
        assertEquals(5, review.rating)
        assertEquals(1_760_000_000L, review.createdAtUnix)
        assertEquals(1_750_000_000L, review.projectCompletedAtUnix)
    }

    @Test
    fun theTimesComeAsStringsOrAsNumbers() = runTest {
        val fixture = SessionFixture {
            json(ProfileSpec.reviews(ProfileSpec.review("a", "1760000000", "1750000000"), ProfileSpec.review("b", "\"1\"", "\"2\"")))
        }

        val items = fixture.session.reviews.userReviews(ProfileSpec.MeId, limit = 20, offset = 0).items

        assertEquals(listOf(1_760_000_000L, 1L), items.map { it.createdAtUnix })
        assertEquals(listOf(1_750_000_000L, 2L), items.map { it.projectCompletedAtUnix })
    }

    @Test
    fun aReviewWithoutAnAuthorRoleHasNone() = runTest {
        val fixture = SessionFixture { json("""{"items":[{"id":"r","rating":4}]}""") }

        val review = fixture.session.reviews.userReviews(ProfileSpec.MeId, limit = 20, offset = 0).items.single()

        assertNull(review.authorRole)
        assertEquals(0L, review.createdAtUnix)
        assertEquals("", review.title)
    }

    @Test
    fun aPageOutsideTheRangeOfTheApiIsRefusedBeforeAnyRequest() = runTest {
        val fixture = SessionFixture { json(ProfileSpec.reviews()) }
        val api = fixture.session.reviews

        assertFailsWith<IllegalArgumentException> { api.userReviews("u", limit = 0, offset = 0) }
        assertFailsWith<IllegalArgumentException> { api.userReviews("u", limit = 101, offset = 0) }
        assertFailsWith<IllegalArgumentException> { api.userReviews("u", limit = 20, offset = -1) }
        assertFailsWith<IllegalArgumentException> { api.userReviews("u", limit = 20, offset = 10_001) }

        assertEquals(emptyList(), fixture.requests)
    }

    @Test
    fun theLimitsThemselvesAreAllowed() = runTest {
        val fixture = SessionFixture { json(ProfileSpec.reviews()) }

        fixture.session.reviews.userReviews("u", limit = 1, offset = 0)
        fixture.session.reviews.userReviews("u", limit = 100, offset = 10_000)

        assertEquals(2, fixture.requests.size)
    }

    @Test
    fun aPersonTheServerDoesNotKnowIsA404ForTheCaller() = runTest {
        val fixture = SessionFixture { jsonError(HttpStatusCode.NotFound, "no such user") }

        val failure = failureOf<RemoteFailure.Http> { fixture.session.reviews.userReviews("u", limit = 20, offset = 0) }

        assertEquals(404, failure.status)
    }

    @Test
    fun aRefusedTokenIsReportedByTheCallsOfReviewsToo() = runTest {
        val fixture = SessionFixture { jsonError(HttpStatusCode.Unauthorized, "expired") }

        failureOf<RemoteFailure.Http> { fixture.session.reviews.userReviews("u", limit = 20, offset = 0) }

        assertEquals(listOf(SessionFixture.Token), fixture.rejected)
    }
}
