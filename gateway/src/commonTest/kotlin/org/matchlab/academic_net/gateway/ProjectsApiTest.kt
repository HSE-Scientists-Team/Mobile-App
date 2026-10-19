package org.matchlab.academic_net.gateway

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.contract.ProjectActions
import org.matchlab.academic_net.gateway.contract.UnverifiedContract
import org.matchlab.academic_net.gateway.dto.ProjectCreateDto
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectInitialRoleDto
import org.matchlab.academic_net.gateway.dto.ProjectInputDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleInputDto
import org.matchlab.academic_net.gateway.dto.ProjectTransitionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `GET /api/v1/projects/{id}`, `POST /api/v1/projects/{id}/roles`, `POST /api/v1/projects` and
 * `POST /api/v1/projects/{id}/transitions`: the calls of the viewer's own project (05, 07, 07b, 07c). The values of
 * `type`, `field`, `planned_*` and `action` are the codes of `docs/api-values.md`.
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
                    type = "1",
                    field = "1",
                    problem = "GPS не работает",
                    goal = "Маршруты внутри",
                    tags = listOf("ML", "Robotics"),
                    plannedStart = "2026-10-01",
                    plannedEnd = "2027-03-31",
                ),
                status = "1",
                revision = 7,
                roles = listOf(ProjectRoleDto("r-1", "ML Engineer", skills = listOf("Python", "PyTorch"), slots = 2, occupied = 1)),
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

    // ---- POST /projects ----

    private fun creation(
        roles: List<ProjectInitialRoleDto> = listOf(ProjectInitialRoleDto("ML Engineer", skills = listOf("Python", "PyTorch"), slots = 1)),
        tags: List<String> = listOf("ML", "Robotics"),
    ) = ProjectCreateDto(
        title = "AI-навигация",
        type = "1",
        field = "1",
        problem = "GPS не работает",
        goal = "Маршруты внутри",
        tags = tags,
        plannedStart = "2026-10-01",
        plannedEnd = "2027-03-31",
        roles = roles,
    )

    @Test
    fun aCreationIsAPostOfTheProjectsPathWithAJsonBodyAndTheBearerToken() = runTest {
        val server = ProjectSessionFixture { json(ProjectSpec.FullProject, HttpStatusCode.Created) }

        server.session.projects.create(creation())

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/projects", request.url.encodedPath)
        assertEquals("", request.url.encodedQuery)
        assertEquals(listOf("Bearer ${ProjectSessionFixture.Token}"), request.authorizations)
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
    }

    @Test
    fun theCreationBodyHasEveryFieldTheApiRequiresUnderItsOwnName() = runTest {
        val server = ProjectSessionFixture { json(ProjectSpec.FullProject, HttpStatusCode.Created) }

        server.session.projects.create(creation())

        val body = server.requests.single().bodyObject()
        assertEquals(
            setOf("title", "type", "field", "problem", "goal", "tags", "planned_start", "planned_end", "roles"),
            body.keys,
        )
        assertEquals("AI-навигация", body.getValue("title").jsonPrimitive.content)
        assertEquals("1", body.getValue("type").jsonPrimitive.content)
        assertEquals("1", body.getValue("field").jsonPrimitive.content)
        assertEquals("GPS не работает", body.getValue("problem").jsonPrimitive.content)
        assertEquals("Маршруты внутри", body.getValue("goal").jsonPrimitive.content)
        assertEquals(JsonArray(listOf(JsonPrimitive("ML"), JsonPrimitive("Robotics"))), body.getValue("tags"))
        assertEquals("2026-10-01", body.getValue("planned_start").jsonPrimitive.content)
        assertEquals("2027-03-31", body.getValue("planned_end").jsonPrimitive.content)
    }

    @Test
    fun theTypeAndTheFieldAreStringsOfDigitsNotNumbers() = runTest {
        val server = ProjectSessionFixture { json(ProjectSpec.FullProject, HttpStatusCode.Created) }

        server.session.projects.create(creation())

        val body = server.requests.single().bodyObject()
        assertTrue(body.getValue("type").jsonPrimitive.isString)
        assertTrue(body.getValue("field").jsonPrimitive.isString)
    }

    @Test
    fun aRoleOfTheCreationHasTitleSkillsAndOneSlot() = runTest {
        val server = ProjectSessionFixture { json(ProjectSpec.FullProject, HttpStatusCode.Created) }

        server.session.projects.create(
            creation(
                roles = listOf(
                    ProjectInitialRoleDto("ML Engineer", skills = listOf("Python", "PyTorch"), slots = 1),
                    ProjectInitialRoleDto("Дизайнер", slots = 1),
                ),
            ),
        )

        val roles = server.requests.single().bodyObject().getValue("roles").jsonArray.map { it.jsonObject }
        assertEquals(2, roles.size)
        assertEquals("ML Engineer", roles[0].getValue("title").jsonPrimitive.content)
        assertEquals(1, roles[0].getValue("slots").jsonPrimitive.int)
        assertEquals(JsonArray(listOf(JsonPrimitive("Python"), JsonPrimitive("PyTorch"))), roles[0].getValue("skills"))
        assertEquals("Дизайнер", roles[1].getValue("title").jsonPrimitive.content)
        assertEquals(JsonArray(emptyList()), roles[1].getValue("skills"))
        assertEquals(1, roles[1].getValue("slots").jsonPrimitive.int)
        // The description is left out until there is one.
        assertFalse("description" in roles[0])
        assertEquals(setOf("title", "skills", "slots"), roles[0].keys)
    }

    @Test
    fun noTagsIsAnEmptyArray() = runTest {
        val server = ProjectSessionFixture { json(ProjectSpec.FullProject, HttpStatusCode.Created) }

        server.session.projects.create(creation(tags = emptyList()))

        assertEquals(JsonArray(emptyList()), server.requests.single().bodyObject().getValue("tags"))
    }

    @Test
    fun theAnswerOf201IsTheWholeProject() = runTest {
        val server = ProjectSessionFixture { json(ProjectSpec.FullProject, HttpStatusCode.Created) }

        val project = server.session.projects.create(creation())

        assertEquals("p-1", project.id)
        assertEquals(7, project.revision)
        assertEquals(listOf("r-1"), project.roles.map { it.id })
    }

    @Test
    fun anAnswerOf200IsReadToo() = runTest {
        val project = server().session.projects.create(creation())

        assertEquals("p-1", project.id)
    }

    @Test
    fun aFailedCreationIsAnHttpFailureWithItsStatusAndIsNotRepeated() = runTest {
        for (status in listOf(400, 403, 409, 500, 503)) {
            val server = ProjectSessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина") }

            val failure = failureOf<RemoteFailure.Http> { server.session.projects.create(creation()) }

            assertEquals(status, failure.status)
            assertEquals(1, server.requests.size, "a creation is not idempotent: no retry")
        }
    }

    @Test
    fun aCreationAnswerThatIsNotAProjectIsMalformed() = runTest {
        failureOf<RemoteFailure.Malformed> {
            ProjectSessionFixture { json("[]", HttpStatusCode.Created) }.session.projects.create(creation())
        }
    }

    @Test
    fun aRefusedTokenOfACreationIsReportedOnce() = runTest {
        val server = ProjectSessionFixture { jsonError(HttpStatusCode.Unauthorized, "expired") }

        failureOf<RemoteFailure.Http> { server.session.projects.create(creation()) }

        assertEquals(listOf(ProjectSessionFixture.Token), server.rejected)
    }

    // ---- POST /projects/{id}/transitions ----

    @Test
    fun aTransitionIsAPostOfTheTransitionsPathWithAJsonBodyAndTheBearerToken() = runTest {
        val server = server()

        server.session.projects.transition("p-1", ProjectTransitionDto(action = "2", expectedRevision = 7))

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/projects/p-1/transitions", request.url.encodedPath)
        assertEquals("", request.url.encodedQuery)
        assertEquals(listOf("Bearer ${ProjectSessionFixture.Token}"), request.authorizations)
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
    }

    @Test
    fun theTransitionBodyIsTheActionAsAStringAndTheRevisionAsANumber() = runTest {
        val server = server()

        server.session.projects.transition("p-1", ProjectTransitionDto(action = "2", expectedRevision = 7))

        val body: JsonObject = server.requests.single().bodyObject()
        assertEquals(setOf("action", "expected_revision"), body.keys)
        assertEquals("2", body.getValue("action").jsonPrimitive.content)
        assertTrue(body.getValue("action").jsonPrimitive.isString)
        assertEquals(7, body.getValue("expected_revision").jsonPrimitive.int)
        assertFalse(body.getValue("expected_revision").jsonPrimitive.isString)
    }

    @Test
    fun theRevisionZeroOfATransitionIsSentAndNotLeftOut() = runTest {
        val server = server()

        server.session.projects.transition("p-1", ProjectTransitionDto(action = "2", expectedRevision = 0))

        assertEquals(0, server.requests.single().bodyObject().getValue("expected_revision").jsonPrimitive.int)
    }

    @Test
    fun theIdOfTheTransitionedProjectIsEscaped() = runTest {
        val server = server()

        server.session.projects.transition("a/b?c", ProjectTransitionDto(action = "2", expectedRevision = 1))

        assertEquals("/api/v1/projects/a%2Fb%3Fc/transitions", server.requests.single().url.encodedPath)
    }

    @Test
    fun theAnswerOfATransitionIsTheWholeProject() = runTest {
        val project = server().session.projects.transition("p-1", ProjectTransitionDto(action = "2", expectedRevision = 7))

        assertEquals("p-1", project.id)
        assertEquals(7, project.revision)
    }

    @Test
    fun aStaleRevisionOfATransitionIsA409AndAGoneProjectA404() = runTest {
        for (status in listOf(409, 404, 403, 400, 500)) {
            val server = ProjectSessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина") }

            val failure = failureOf<RemoteFailure.Http> {
                server.session.projects.transition("p-1", ProjectTransitionDto(action = "2", expectedRevision = 1))
            }

            assertEquals(status, failure.status)
            assertEquals(1, server.requests.size, "no retry by the client")
        }
    }

    @Test
    @OptIn(UnverifiedContract::class)
    fun theActionOfTheAppIsTheCodeOfTheContract() {
        assertEquals("1", ProjectActions.PUBLISH)
        assertEquals("2", ProjectActions.PAUSE)
    }
}
