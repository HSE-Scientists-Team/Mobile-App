package org.matchlab.academic_net.gateway

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectInputDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleInputDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `GET /api/v1/projects/{id}` and `POST /api/v1/projects/{id}/roles`: the calls of the viewer's own project (07, 07b).
 * What is not here is the API's gap: no `POST /projects` (G40, G43), no `GET /projects?mine=` (G44), no transitions (G42).
 */
class ProjectsApiTest {
    private fun server(body: String = ProjectSpec.FullProject) = ProjectSessionFixture { json(body) }

    // ---- GET /projects/{id} ----

    @Test
    fun aProjectIsAGetOfItsPathWithTheBearerTokenAndNothingElse() = runTest {
        val server = server()

        server.session.projects.get("p-1")

        val request = server.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/projects/p-1", request.url.encodedPath)
        assertEquals("", request.url.encodedQuery)
        assertEquals(listOf("Bearer ${ProjectSessionFixture.Token}"), request.authorizations)
        assertEquals("", request.bodyText())
    }

    @Test
    fun theIdGoesIntoThePathEscapedSoItCannotReachAnotherResource() = runTest {
        val server = server()

        server.session.projects.get("a/b?c#d e")

        val request = server.requests.single()
        assertEquals("/api/v1/projects/a%2Fb%3Fc%23d%20e", request.url.encodedPath)
        assertEquals("", request.url.encodedQuery)
    }

    @Test
    fun aProjectIsReadWithEveryFieldTheClientKnows() = runTest {
        val project = server().session.projects.get("p-1")

        assertEquals(
            ProjectDto(
                id = "p-1",
                ownerUserId = ProjectSpec.OwnerId,
                details = ProjectInputDto(
                    title = "AI-навигация",
                    type = "scientific",
                    field = "Computer Science",
                    problem = "GPS не работает",
                    goal = "Маршруты внутри",
                    tags = listOf("ML", "Robotics"),
                    plannedStart = "2026-10",
                    plannedEnd = "2027-03",
                ),
                status = "published",
                revision = 7,
                roles = listOf(ProjectRoleDto("r-1", "ML Engineer", listOf("Python", "PyTorch"), slots = 2, occupied = 1)),
            ),
            project,
        )
    }

    @Test
    fun anEmptyObjectIsAProjectWithDefaults() = runTest {
        assertEquals(ProjectDto(), server("{}").session.projects.get("p-1"))
    }

    @Test
    fun nullInsteadOfAnArrayOrAnObjectIsTheDefault() = runTest {
        val project = server(
            """{"id":"p-1","owner_user_id":"o","details":{"title":"T","tags":null},"roles":[{"id":"r","title":"X","skills":null,"slots":null,"occupied":null}]}""",
        ).session.projects.get("p-1")

        assertEquals(emptyList(), project.details.tags)
        assertEquals(ProjectRoleDto(id = "r", title = "X", skills = emptyList(), slots = 0, occupied = 0), project.roles.single())
    }

    @Test
    fun aMissingRoleListIsNoRoles() = runTest {
        assertEquals(emptyList(), server("""{"id":"p-1","owner_user_id":"o"}""").session.projects.get("p-1").roles)
    }

    @Test
    fun anAnswerThatIsNotAProjectIsMalformed() = runTest {
        failureOf<RemoteFailure.Malformed> { server("[]").session.projects.get("p-1") }
        failureOf<RemoteFailure.Malformed> { server("""{"roles":{"r-1":true}}""").session.projects.get("p-1") }
        failureOf<RemoteFailure.Malformed> { server("not json").session.projects.get("p-1") }
    }

    @Test
    fun everyErrorStatusIsAnHttpFailureWithItsStatus() = runTest {
        for (status in listOf(400, 403, 404, 500, 503)) {
            val server = ProjectSessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина") }

            val failure = failureOf<RemoteFailure.Http> { server.session.projects.get("p-1") }

            assertEquals(status, failure.status)
            assertEquals(1, server.requests.size)
        }
    }

    @Test
    fun aRefusedTokenIsReportedExactlyOnce() = runTest {
        val server = ProjectSessionFixture { jsonError(HttpStatusCode.Unauthorized, "expired") }

        val failure = failureOf<RemoteFailure.Http> { server.session.projects.get("p-1") }

        assertEquals(401, failure.status)
        assertEquals(listOf(ProjectSessionFixture.Token), server.rejected)
    }

    // ---- POST /projects/{id}/roles ----

    @Test
    fun aRoleIsAPostOfTheRolesPathWithAJsonBodyAndTheBearerToken() = runTest {
        val server = server()

        server.session.projects.addRole("p-1", ProjectRoleInputDto("Дизайнер", slots = 1, expectedRevision = 7))

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/projects/p-1/roles", request.url.encodedPath)
        assertEquals(listOf("Bearer ${ProjectSessionFixture.Token}"), request.authorizations)
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
        assertTrue(request.headers[HttpHeaders.Accept].orEmpty().contains("application/json"))
    }

    @Test
    fun theBodyIsSnakeCaseWithTheRequiredFieldsAndNoNull() = runTest {
        val server = server()

        server.session.projects.addRole("p-1", ProjectRoleInputDto("Дизайнер", slots = 1, expectedRevision = 7, skills = listOf("Figma", "UX")))

        val body = server.requests.single().bodyObject()
        assertEquals(setOf("title", "slots", "expected_revision", "skills"), body.keys)
        assertEquals("Дизайнер", body.getValue("title").jsonPrimitive.content)
        assertEquals(1, body.getValue("slots").jsonPrimitive.int)
        assertEquals(7, body.getValue("expected_revision").jsonPrimitive.int)
        assertEquals(
            JsonArray(listOf(JsonPrimitive("Figma"), JsonPrimitive("UX"))),
            body.getValue("skills"),
        )
    }

    @Test
    fun theRevisionZeroIsSentAndNotLeftOut() = runTest {
        val server = server()

        server.session.projects.addRole("p-1", ProjectRoleInputDto("Дизайнер", slots = 1, expectedRevision = 0))

        val body = server.requests.single().bodyObject()
        assertEquals(0, body.getValue("expected_revision").jsonPrimitive.int)
    }

    @Test
    fun noSkillsIsAnEmptyArrayAndTheDescriptionIsLeftOutUntilThereIsOne() = runTest {
        val server = server()

        server.session.projects.addRole("p-1", ProjectRoleInputDto("Дизайнер", slots = 1, expectedRevision = 1))
        server.session.projects.addRole("p-1", ProjectRoleInputDto("Дизайнер", slots = 1, expectedRevision = 1, description = "Рисует"))

        val first = server.requests[0].bodyObject()
        assertEquals(JsonArray(emptyList()), first.getValue("skills"))
        assertFalse("description" in first)
        assertEquals("Рисует", server.requests[1].bodyObject().getValue("description").jsonPrimitive.content)
    }

    @Test
    fun theIdOfTheProjectOfARoleIsEscapedToo() = runTest {
        val server = server()

        server.session.projects.addRole("a/b", ProjectRoleInputDto("Дизайнер", slots = 1, expectedRevision = 1))

        assertEquals("/api/v1/projects/a%2Fb/roles", server.requests.single().url.encodedPath)
    }

    @Test
    fun theAnswerOfARoleIsTheWholeProject() = runTest {
        val project = server().session.projects.addRole("p-1", ProjectRoleInputDto("ML Engineer", slots = 1, expectedRevision = 7))

        assertEquals("p-1", project.id)
        assertEquals(7, project.revision)
        assertEquals(listOf("r-1"), project.roles.map { it.id })
    }

    @Test
    fun aStaleRevisionIsA409AndAGoneProjectA404() = runTest {
        for (status in listOf(409, 404, 403, 400, 500)) {
            val server = ProjectSessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина") }

            val failure = failureOf<RemoteFailure.Http> {
                server.session.projects.addRole("p-1", ProjectRoleInputDto("Дизайнер", slots = 1, expectedRevision = 1))
            }

            assertEquals(status, failure.status)
            assertEquals(1, server.requests.size, "no retry by the client")
        }
    }

    @Test
    fun aRefusedTokenOfARoleIsReportedOnce() = runTest {
        val server = ProjectSessionFixture { jsonError(HttpStatusCode.Unauthorized, "expired") }

        failureOf<RemoteFailure.Http> {
            server.session.projects.addRole("p-1", ProjectRoleInputDto("Дизайнер", slots = 1, expectedRevision = 1))
        }

        assertEquals(listOf(ProjectSessionFixture.Token), server.rejected)
    }
}
