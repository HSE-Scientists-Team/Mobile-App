package org.matchlab.academic_net.gateway

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.PeopleListDto
import org.matchlab.academic_net.gateway.dto.PersonDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `GET /api/v1/recommendations/people?limit`: the people the server recommends. API-GAP G33: the spec gives `limit` no
 * bounds, so the client refuses only a number that asks for nothing and invents no upper bound.
 */
class RecommendationsApiTest {
    private fun server(body: String = PeopleSpec.list(total = null)) = PeopleSessionFixture { json(body) }

    // ---- the request ----

    @Test
    fun theRecommendationsAreAGetWithTheBearerTokenAndTheLimitOnly() = runTest {
        val server = server()

        server.session.recommendations.people(10)

        val request = server.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/recommendations/people", request.url.encodedPath)
        assertEquals("limit=10", request.url.encodedQuery)
        assertEquals(listOf("Bearer ${PeopleSessionFixture.Token}"), request.authorizations)
        assertEquals("", request.bodyText())
    }

    @Test
    fun aLimitThatAsksForNothingIsRefusedBeforeAnyRequest() = runTest {
        val server = server()

        assertFailsWith<IllegalArgumentException> { server.session.recommendations.people(0) }
        assertFailsWith<IllegalArgumentException> { server.session.recommendations.people(-1) }
        assertFailsWith<IllegalArgumentException> { server.session.recommendations.people(Int.MIN_VALUE) }

        assertEquals(emptyList(), server.requests)
    }

    @Test
    fun noUpperBoundIsInvented() = runTest {
        val server = server()

        server.session.recommendations.people(1)
        server.session.recommendations.people(1000)

        assertEquals("1", server.requests[0].url.parameters["limit"])
        assertEquals("1000", server.requests[1].url.parameters["limit"])
    }

    // ---- the answer ----

    @Test
    fun thePeopleAreReadInTheOrderOfTheServer() = runTest {
        val server = server(PeopleSpec.list("\"3\"", PeopleSpec.person("c"), PeopleSpec.FullPerson, PeopleSpec.person("a")))

        val answer = server.session.recommendations.people(10)

        assertEquals(listOf("c", "u-1", "a"), answer.items.map { it.userId })
        assertEquals(3L, answer.total)
        assertEquals(
            PersonDto(
                userId = "u-1",
                displayName = "  Анна Иванова ",
                avatarFileId = "file-1",
                university = " МГУ ",
                studyLevel = "магистратура",
                studyYear = 2,
                specialization = "Physics",
                skills = listOf("Python", "Math"),
                interests = listOf("ML"),
                openToCollaboration = true,
                verified = true,
                rating = 4.5,
                completedProjectCount = 3,
            ),
            answer.items[1],
        )
    }

    @Test
    fun anEmptyObjectIsNobody() = runTest {
        val answer = server("{}").session.recommendations.people(10)

        assertEquals(PeopleListDto(items = emptyList(), total = 0), answer)
    }

    @Test
    fun anAnswerThatIsNotAPeopleListIsMalformed() = runTest {
        val server = server("""{"items":{"u-1":true}}""")

        failureOf<RemoteFailure.Malformed> { server.session.recommendations.people(10) }
    }

    // ---- failures ----

    @Test
    fun everyErrorStatusIsAnHttpFailureWithItsStatus() = runTest {
        for (status in listOf(400, 403, 404, 500, 503)) {
            val server = PeopleSessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина") }

            val failure = failureOf<RemoteFailure.Http> { server.session.recommendations.people(10) }

            assertEquals(status, failure.status)
            assertEquals(1, server.requests.size)
        }
    }

    @Test
    fun aRefusedTokenIsReportedExactlyOnce() = runTest {
        val server = PeopleSessionFixture { jsonError(HttpStatusCode.Unauthorized, "expired") }

        val failure = failureOf<RemoteFailure.Http> { server.session.recommendations.people(10) }

        assertEquals(401, failure.status)
        assertEquals(listOf(PeopleSessionFixture.Token), server.rejected)
    }
}
