package org.matchlab.academic_net.gateway

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.ProjectJoinInputDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The six calls of applications and invitations (`ProjectJoinsApi`, block 0) over a mock engine. */
class ProjectJoinsApiTest {
    private val joinJson =
        """{"id":"j-1","project_id":"p-1","role_id":"r-1","candidate_user_id":"u-1","type":"application","status":"pending","message":"Привет","created_at_unix":"1760000000"}"""

    private fun server(body: String = joinJson, status: HttpStatusCode = HttpStatusCode.OK) =
        ProjectSessionFixture { json(body, status) }

    private val page = PageRequest(limit = 20, offset = 40)

    // ---- POST applications / invitations ----

    @Test
    fun anApplicationIsAPostOfTheApplicationsPathWithTheNonNullFieldsOnly() = runTest {
        val server = server(status = HttpStatusCode.Created)

        val join = server.session.projectJoins.apply("a/b", ProjectJoinInputDto(roleId = "r-1", message = "Привет"))

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/projects/a%2Fb/applications", request.url.encodedPath)
        assertEquals(listOf("Bearer ${ProjectSessionFixture.Token}"), request.authorizations)
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
        val body = request.bodyObject()
        assertEquals(setOf("role_id", "message"), body.keys)
        assertEquals("r-1", body.getValue("role_id").jsonPrimitive.content)
        assertEquals("j-1", join.id)
    }

    @Test
    fun anInvitationIsAPostOfTheInvitationsPathWithTheCandidate() = runTest {
        val server = server(status = HttpStatusCode.Created)

        server.session.projectJoins.invite("p-1", ProjectJoinInputDto(roleId = "r-1", candidateUserId = "u 1"))

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/projects/p-1/invitations", request.url.encodedPath)
        assertEquals(setOf("role_id", "candidate_user_id"), request.bodyObject().keys)
    }

    @Test
    fun anEmptyInputIsAnEmptyObject() = runTest {
        val server = server()

        server.session.projectJoins.apply("p-1", ProjectJoinInputDto())

        assertEquals(emptySet(), server.requests.single().bodyObject().keys)
    }

    // ---- GET lists ----

    @Test
    fun theListOfAProjectSendsLimitAndOffsetAndOnlyTheFiltersThatAreSet() = runTest {
        val server = server("""{"items":[$joinJson],"total":"1"}""")

        val list = server.session.projectJoins.listForProject("a/b", ProjectJoinFilter(), page)

        val request = server.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/projects/a%2Fb/joins", request.url.encodedPath)
        assertEquals(setOf("limit", "offset"), request.url.parameters.names())
        assertEquals("20", request.url.parameters["limit"])
        assertEquals("40", request.url.parameters["offset"])
        assertEquals("", request.bodyText())
        assertEquals(1, list.items.size)
        assertEquals(1L, list.total)
    }

    @Test
    fun theFiltersGoWhenTheyAreFilled() = runTest {
        val server = server("""{"items":[]}""")

        server.session.projectJoins.listForProject("p-1", ProjectJoinFilter(roleId = "r-1", status = "accepted"), page)

        val parameters = server.requests.single().url.parameters
        assertEquals("r-1", parameters["role_id"])
        assertEquals("accepted", parameters["status"])
    }

    @Test
    fun nullAndBlankFiltersAreNotSent() = runTest {
        val server = server("""{"items":[]}""")

        server.session.projectJoins.listMine(ProjectJoinFilter(roleId = null, status = null), page)
        server.session.projectJoins.listMine(ProjectJoinFilter(roleId = "", status = "  "), page)

        for (request in server.requests) {
            assertFalse("role_id" in request.url.parameters.names())
            assertFalse("status" in request.url.parameters.names())
            assertEquals("/api/v1/project-joins", request.url.encodedPath)
        }
    }

    @Test
    fun theTotalIsReadAsAStringOrANumberAndMissingIsZero() = runTest {
        assertEquals(42L, server("""{"items":[],"total":"42"}""").session.projectJoins.listMine(ProjectJoinFilter(), page).total)
        assertEquals(42L, server("""{"items":[],"total":42}""").session.projectJoins.listMine(ProjectJoinFilter(), page).total)
        assertEquals(0L, server("""{"items":[]}""").session.projectJoins.listMine(ProjectJoinFilter(), page).total)
    }

    @Test
    fun createdAtIsReadAsAStringOrANumber() = runTest {
        val asNumber = joinJson.replace("\"1760000000\"", "1760000000")

        val fromString = server().session.projectJoins.withdraw("j-1")
        val fromNumber = server(asNumber).session.projectJoins.withdraw("j-1")

        assertEquals(1_760_000_000L, fromString.createdAtUnix)
        assertEquals(1_760_000_000L, fromNumber.createdAtUnix)
    }

    // ---- decision / withdraw ----

    @Test
    fun aDecisionIsAPostOfTheDecisionPathWithTheFlag() = runTest {
        val server = server()

        server.session.projectJoins.decide("j/1", accept = true)
        server.session.projectJoins.decide("j/1", accept = false)

        val first = server.requests[0]
        assertEquals(HttpMethod.Post, first.method)
        assertEquals("/api/v1/project-joins/j%2F1/decision", first.url.encodedPath)
        assertEquals("""{"accept":true}""", first.bodyText())
        assertEquals("""{"accept":false}""", server.requests[1].bodyText())
    }

    @Test
    fun withdrawingIsADeleteWhoseAnswerIsRead() = runTest {
        val server = server()

        val join = server.session.projectJoins.withdraw("j/1")

        val request = server.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/v1/project-joins/j%2F1", request.url.encodedPath)
        assertEquals("", request.url.encodedQuery)
        assertEquals("p-1", join.projectId)
        assertEquals("Привет", join.message)
    }

    // ---- failures ----

    @Test
    fun everyErrorStatusIsAnHttpFailureWithItsStatusAndNothingIsRepeated() = runTest {
        for (status in listOf(400, 403, 404, 409, 500)) {
            val server = ProjectSessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина") }

            assertEquals(status, failureOf<RemoteFailure.Http> { server.session.projectJoins.apply("p", ProjectJoinInputDto()) }.status)
            assertEquals(status, failureOf<RemoteFailure.Http> { server.session.projectJoins.decide("j", true) }.status)
            assertEquals(status, failureOf<RemoteFailure.Http> { server.session.projectJoins.withdraw("j") }.status)
            assertEquals(3, server.requests.size)
        }
    }

    @Test
    fun aRefusedTokenIsReportedOnce() = runTest {
        val server = ProjectSessionFixture { jsonError(HttpStatusCode.Unauthorized, "expired") }

        failureOf<RemoteFailure.Http> { server.session.projectJoins.listMine(ProjectJoinFilter(), page) }

        assertEquals(listOf(ProjectSessionFixture.Token), server.rejected)
    }

    @Test
    fun anAnswerThatIsNotAJoinIsMalformed() = runTest {
        failureOf<RemoteFailure.Malformed> { server("[]").session.projectJoins.withdraw("j") }
        assertTrue(server("{}").session.projectJoins.withdraw("j").id.isEmpty())
    }
}
