package org.matchlab.academic_net.gateway

import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.jsonPrimitive
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.UserReviewInputDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** `POST /api/v1/users/me/reviews` over a mock engine: the request, the 201 answer and every refusal of the server. */
class ReviewWriteApiTest {
    private val input = UserReviewInputDto(
        projectId = "p-1",
        targetUserId = ProfileSpec.OtherId,
        title = "Отлично",
        text = "Довёл до демо",
        rating = 5,
    )

    private fun created(id: String = "rev-9") = ProfileSpec.review(id)

    private fun server(body: String = created(), status: HttpStatusCode = HttpStatusCode.Created) =
        SessionFixture { json(body, status) }

    // ---- the request ----

    @Test
    fun aReviewIsAPostOfTheWritePathWithTheBearerTokenAndAJsonBody() = runTest {
        val server = server()

        server.session.reviews.create(input)

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/users/me/reviews", request.url.encodedPath)
        assertEquals(listOf("Bearer ${SessionFixture.Token}"), request.authorizations)
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
        assertEquals("", request.url.encodedQuery)
    }

    @Test
    fun theBodyHasTheFiveFieldsUnderTheirApiNamesAndNothingElse() = runTest {
        val server = server()

        server.session.reviews.create(input)

        val body = server.requests.single().bodyObject()
        assertEquals(setOf("project_id", "target_user_id", "title", "text", "rating"), body.keys)
        assertEquals("p-1", body.getValue("project_id").jsonPrimitive.content)
        assertEquals(ProfileSpec.OtherId, body.getValue("target_user_id").jsonPrimitive.content)
        assertEquals("Отлично", body.getValue("title").jsonPrimitive.content)
        assertEquals("Довёл до демо", body.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun theRatingGoesAsANumberNotAsAString() = runTest {
        val server = server()

        server.session.reviews.create(input.copy(rating = 3))

        val rating = server.requests.single().bodyObject().getValue("rating").jsonPrimitive
        assertFalse(rating.isString, "the API wants an integer")
        assertEquals("3", rating.content)
    }

    @Test
    fun quotesNewlinesAndEmojiOfTheTextArriveAsTyped() = runTest {
        val server = server()
        val text = "Он сказал: \"супер\"\nи ещё\tстрока \\ 😀 ✓"

        server.session.reviews.create(input.copy(title = "«Заголовок» 😀", text = text))

        val body = server.requests.single().bodyObject()
        assertEquals("«Заголовок» 😀", body.getValue("title").jsonPrimitive.content)
        assertEquals(text, body.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun theIdsAreSentAsGivenWithoutAnyEscapingOfThePath() = runTest {
        val server = server()

        server.session.reviews.create(input.copy(projectId = "a/b?c", targetUserId = "u 1"))

        val request = server.requests.single()
        assertEquals("/api/v1/users/me/reviews", request.url.encodedPath, "the ids are in the body, not in the URL")
        assertEquals("a/b?c", request.bodyObject().getValue("project_id").jsonPrimitive.content)
        assertEquals("u 1", request.bodyObject().getValue("target_user_id").jsonPrimitive.content)
    }

    // ---- the answer ----

    @Test
    fun theCreatedReviewOf201IsReadWithEveryField() = runTest {
        val server = server(created("rev-9"))

        val review = server.session.reviews.create(input)

        assertEquals("rev-9", review.id)
        assertEquals(ProfileSpec.OtherId, review.authorUserId)
        assertEquals("p-1", review.projectId)
        assertEquals("Цифровой двойник", review.projectTitle)
        assertEquals(ProfileSpec.MeId, review.targetUserId)
        assertEquals("Отлично", review.title)
        assertEquals("Довёл до демо", review.text)
        assertEquals(5, review.rating)
        assertEquals(1_760_000_000L, review.createdAtUnix)
        assertEquals(1_750_000_000L, review.projectCompletedAtUnix)
    }

    @Test
    fun theTimesOfTheAnswerComeAsNumbersOrStrings() = runTest {
        val asNumbers = server(ProfileSpec.review("a", "1760000000", "1750000000"))
        val asStrings = server(ProfileSpec.review("b", "\"1\"", "\"2\""))

        val first = asNumbers.session.reviews.create(input)
        val second = asStrings.session.reviews.create(input)

        assertEquals(1_760_000_000L to 1_750_000_000L, first.createdAtUnix to first.projectCompletedAtUnix)
        assertEquals(1L to 2L, second.createdAtUnix to second.projectCompletedAtUnix)
    }

    @Test
    fun anAnswerWithFewFieldsIsReadWithDefaults() = runTest {
        val server = server("""{"id":"r"}""")

        val review = server.session.reviews.create(input)

        assertEquals("r", review.id)
        assertEquals(0, review.rating)
        assertEquals(0L, review.createdAtUnix)
    }

    @Test
    fun a200AnswerIsAsGoodAs201() = runTest {
        val server = server(created("rev-200"), HttpStatusCode.OK)

        assertEquals("rev-200", server.session.reviews.create(input).id)
    }

    @Test
    fun anAnswerThatIsNotAReviewIsMalformedAndTheRequestIsNotRepeated() = runTest {
        for (answer in listOf("[]", "not json", "", """{"rating":"five"}""")) {
            val server = server(answer)

            failureOf<RemoteFailure.Malformed> { server.session.reviews.create(input) }

            assertEquals(1, server.requests.size, "answer '$answer': a repeat would make a second review")
        }
    }

    // ---- the refusals ----

    @Test
    fun everyErrorStatusIsAnHttpFailureWithItsStatusAndTheTextOfTheServer() = runTest {
        for (status in listOf(400, 401, 403, 404, 409, 500, 502, 503)) {
            val server = SessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина $status") }

            val failure = failureOf<RemoteFailure.Http> { server.session.reviews.create(input) }

            assertEquals(status, failure.status)
            assertEquals("причина $status", failure.serverMessage)
            assertEquals(1, server.requests.size, "status $status: the POST is never repeated")
        }
    }

    @Test
    fun aRefusalWithoutABodyIsStillAnHttpFailure() = runTest {
        val server = SessionFixture { respond("", HttpStatusCode.Conflict) }

        val failure = failureOf<RemoteFailure.Http> { server.session.reviews.create(input) }

        assertEquals(409, failure.status)
        assertEquals(null, failure.serverMessage)
    }

    @Test
    fun aRefusedTokenIsReportedOnceAndOnlyThen() = runTest {
        val refused = SessionFixture { jsonError(HttpStatusCode.Unauthorized, "expired") }
        val conflict = SessionFixture { jsonError(HttpStatusCode.Conflict, "duplicate") }

        failureOf<RemoteFailure.Http> { refused.session.reviews.create(input) }
        failureOf<RemoteFailure.Http> { conflict.session.reviews.create(input) }

        assertEquals(listOf(SessionFixture.Token), refused.rejected)
        assertTrue(conflict.rejected.isEmpty(), "a 409 does not end the session")
    }

    @Test
    fun noConnectionIsNoConnectionAndTheCauseIsKept() = runTest {
        val server = SessionFixture { throw IOException("connection reset") }

        val failure = failureOf<RemoteFailure.NoConnection> { server.session.reviews.create(input) }

        assertIs<IOException>(failure.cause)
    }
}
