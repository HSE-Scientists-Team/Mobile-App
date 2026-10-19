package org.matchlab.academic_net.gateway

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.contract.ProjectActions
import org.matchlab.academic_net.gateway.contract.UnverifiedContract
import org.matchlab.academic_net.gateway.dto.ProjectRoleInputDto
import org.matchlab.academic_net.gateway.dto.ProjectUpdateDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `PUT /projects/{id}`, `PUT` and `DELETE /projects/{id}/roles/{role_id}`, `DELETE /projects/{id}/members/{user_id}` and
 * `DELETE /projects/{id}/membership` (blocks 0 and 4), and how the project of the answer reads the team.
 */
class ProjectEditApiTest {
    private fun server(body: String = ProjectSpec.FullProject) = ProjectSessionFixture { json(body) }

    private fun update(tags: List<String> = listOf("ML")) = ProjectUpdateDto(
        title = "AI-навигация",
        type = "scientific",
        field = "Инженерия",
        problem = "GPS не работает",
        goal = "Маршруты",
        tags = tags,
        plannedStart = "2026-10-01",
        plannedEnd = "2027-03-31",
        expectedRevision = 7,
    )

    // ---- PUT /projects/{id} ----

    @Test
    fun anUpdateIsAPutOfTheProjectPathWithAJsonBodyAndTheBearerToken() = runTest {
        val server = server()

        server.session.projects.update("p-1", update())

        val request = server.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/api/v1/projects/p-1", request.url.encodedPath)
        assertEquals("", request.url.encodedQuery)
        assertEquals(listOf("Bearer ${ProjectSessionFixture.Token}"), request.authorizations)
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
    }

    @Test
    fun theUpdateIdIsEscapedSoItCannotReachAnotherResource() = runTest {
        val server = server()

        server.session.projects.update("a/b?c#d e", update())

        assertEquals("/api/v1/projects/a%2Fb%3Fc%23d%20e", server.requests.single().url.encodedPath)
    }

    @Test
    fun theUpdateBodyIsSnakeCaseWithoutRolesAndId() = runTest {
        val server = server()

        server.session.projects.update("p-1", update())

        val body = server.requests.single().bodyObject()
        assertEquals(
            setOf("title", "type", "field", "problem", "goal", "tags", "planned_start", "planned_end", "expected_revision"),
            body.keys,
        )
        assertFalse("roles" in body)
        assertFalse("id" in body)
        assertEquals("scientific", body.getValue("type").jsonPrimitive.content)
        assertTrue(body.getValue("type").jsonPrimitive.isString)
        assertEquals("Инженерия", body.getValue("field").jsonPrimitive.content)
        assertEquals("2026-10-01", body.getValue("planned_start").jsonPrimitive.content)
        assertEquals("2027-03-31", body.getValue("planned_end").jsonPrimitive.content)
        assertEquals(7, body.getValue("expected_revision").jsonPrimitive.int)
        assertFalse(body.getValue("expected_revision").jsonPrimitive.isString)
    }

    @Test
    fun noTagsIsAnEmptyArraySoThePutClearsThem() = runTest {
        val server = server()

        server.session.projects.update("p-1", update(tags = emptyList()))

        assertEquals(JsonArray(emptyList()), server.requests.single().bodyObject().getValue("tags"))
    }

    @Test
    fun tagsGoInTheirOrder() = runTest {
        val server = server()

        server.session.projects.update("p-1", update(tags = listOf("b", "a")))

        assertEquals(
            JsonArray(listOf(JsonPrimitive("b"), JsonPrimitive("a"))),
            server.requests.single().bodyObject().getValue("tags"),
        )
    }

    @Test
    fun theUpdateAnswerIsTheWholeProject() = runTest {
        val project = server().session.projects.update("p-1", update())

        assertEquals("p-1", project.id)
        assertEquals(7, project.revision)
    }

    @Test
    fun everyFailedUpdateIsAnHttpFailureAndIsNotRepeated() = runTest {
        for (status in listOf(400, 403, 404, 409, 500)) {
            val server = ProjectSessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина") }

            val failure = failureOf<RemoteFailure.Http> { server.session.projects.update("p-1", update()) }

            assertEquals(status, failure.status)
            assertEquals(1, server.requests.size)
        }
    }

    // ---- PUT /projects/{id}/roles/{role_id} ----

    @Test
    fun aRoleUpdateIsAPutOfTheRolePathWithBothIdsEscaped() = runTest {
        val server = server()

        server.session.projects.updateRole("a/b", "r 1?", ProjectRoleInputDto("Дизайнер", slots = 1, expectedRevision = 7))

        val request = server.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/api/v1/projects/a%2Fb/roles/r%201%3F", request.url.encodedPath)
        assertEquals(listOf("Bearer ${ProjectSessionFixture.Token}"), request.authorizations)
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
    }

    @Test
    fun theRoleUpdateBodyKeepsTheDescriptionOnlyWhenThereIsOne() = runTest {
        val server = server()

        server.session.projects.updateRole("p-1", "r-1", ProjectRoleInputDto("Дизайнер", slots = 2, expectedRevision = 7, skills = listOf("Figma")))
        server.session.projects.updateRole(
            "p-1",
            "r-1",
            ProjectRoleInputDto("Дизайнер", slots = 2, expectedRevision = 7, description = "Рисует"),
        )

        val first = server.requests[0].bodyObject()
        assertEquals(setOf("title", "slots", "expected_revision", "skills"), first.keys)
        assertEquals(2, first.getValue("slots").jsonPrimitive.int)
        val second = server.requests[1].bodyObject()
        assertEquals("Рисует", second.getValue("description").jsonPrimitive.content)
        assertEquals(JsonArray(emptyList()), second.getValue("skills"))
    }

    @Test
    fun aFailedRoleUpdateIsAnHttpFailure() = runTest {
        for (status in listOf(400, 403, 404, 409, 500)) {
            val server = ProjectSessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина") }

            val failure = failureOf<RemoteFailure.Http> {
                server.session.projects.updateRole("p-1", "r-1", ProjectRoleInputDto("Д", slots = 1, expectedRevision = 1))
            }

            assertEquals(status, failure.status)
            assertEquals(1, server.requests.size)
        }
    }

    // ---- DELETE /projects/{id}/roles/{role_id} ----

    @Test
    fun aRoleDeletionIsADeleteWithTheRevisionInTheQueryAndNoBody() = runTest {
        val server = server()

        val project = server.session.projects.deleteRole("a/b", "r/1", expectedRevision = 7)

        val request = server.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/v1/projects/a%2Fb/roles/r%2F1", request.url.encodedPath)
        assertEquals("expected_revision=7", request.url.encodedQuery)
        assertEquals("", request.bodyText())
        assertEquals(listOf("Bearer ${ProjectSessionFixture.Token}"), request.authorizations)
        assertEquals("p-1", project.id)
    }

    @Test
    fun theRevisionZeroOfARoleDeletionIsSent() = runTest {
        val server = server()

        server.session.projects.deleteRole("p-1", "r-1", expectedRevision = 0)

        assertEquals("expected_revision=0", server.requests.single().url.encodedQuery)
    }

    @Test
    fun aFailedRoleDeletionIsAnHttpFailureWithItsStatus() = runTest {
        for (status in listOf(403, 404, 409, 500)) {
            val server = ProjectSessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина") }

            val failure = failureOf<RemoteFailure.Http> { server.session.projects.deleteRole("p-1", "r-1", 1) }

            assertEquals(status, failure.status)
            assertEquals(1, server.requests.size)
        }
    }

    // ---- DELETE members / membership ----

    @Test
    fun removingAMemberIsADeleteOfTheMemberPathWithBothIdsEscaped() = runTest {
        val server = server()

        val project = server.session.projects.removeMember("a/b", "u 1/2")

        val request = server.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/v1/projects/a%2Fb/members/u%201%2F2", request.url.encodedPath)
        assertEquals("", request.url.encodedQuery)
        assertEquals("p-1", project.id)
    }

    @Test
    fun leavingIsADeleteOfTheMembershipPath() = runTest {
        val server = server()

        val project = server.session.projects.leave("a/b")

        val request = server.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/v1/projects/a%2Fb/membership", request.url.encodedPath)
        assertEquals("", request.url.encodedQuery)
        assertEquals(listOf("Bearer ${ProjectSessionFixture.Token}"), request.authorizations)
        assertEquals("p-1", project.id)
    }

    @Test
    fun aRefusedTokenOfAnEditIsReportedOnce() = runTest {
        val server = ProjectSessionFixture { jsonError(HttpStatusCode.Unauthorized, "expired") }

        failureOf<RemoteFailure.Http> { server.session.projects.leave("p-1") }

        assertEquals(listOf(ProjectSessionFixture.Token), server.rejected)
    }

    @Test
    fun anAnswerThatIsNotAProjectIsMalformed() = runTest {
        failureOf<RemoteFailure.Malformed> { server("[]").session.projects.removeMember("p-1", "u") }
        failureOf<RemoteFailure.Malformed> { server("not json").session.projects.deleteRole("p-1", "r", 1) }
    }

    // ---- reading the answer ----

    @Test
    fun theMembersThePendingCountAndTheDescriptionOfARoleAreRead() = runTest {
        val project = server(
            """{"id":"p-1","owner_user_id":"o","revision":3,"pending_join_count":4,
               "roles":[{"id":"r-1","title":"Dev","description":"Пишет код","slots":2,"occupied":1}],
               "members":[{"user_id":"u1","role_id":"r-1","role_title":"Dev","status":"whatever"},{"user_id":"u2"}]}""",
        ).session.projects.get("p-1")

        assertEquals(4, project.pendingJoinCount)
        assertEquals("Пишет код", project.roles.single().description)
        assertEquals(listOf("u1", "u2"), project.members.map { it.userId })
        assertEquals("r-1", project.members[0].roleId)
        assertEquals("Dev", project.members[0].roleTitle)
        assertEquals("", project.members[1].roleId)
    }

    @Test
    fun withoutMembersAndPendingCountTheDefaultsAreEmptyAndZero() = runTest {
        val project = server("""{"id":"p-1","owner_user_id":"o","members":null}""").session.projects.get("p-1")

        assertEquals(emptyList(), project.members)
        assertEquals(0, project.pendingJoinCount)
    }

    @Test
    @OptIn(UnverifiedContract::class)
    fun theLifecycleActionsAreTheTextsOfTheContract() {
        assertEquals("start", ProjectActions.START)
        assertEquals("complete", ProjectActions.COMPLETE)
        assertEquals("cancel", ProjectActions.CANCEL)
        assertEquals(
            5,
            setOf(
                ProjectActions.PUBLISH,
                ProjectActions.PAUSE,
                ProjectActions.START,
                ProjectActions.COMPLETE,
                ProjectActions.CANCEL,
            ).size,
        )
    }
}

/** `details.open_to_collaboration` of a public profile as the DTO reads it (block 1). */
class OpenToCollaborationDtoTest {
    private fun read(details: String) = org.matchlab.academic_net.gateway.internal.GatewayJson.decodeFromString(
        org.matchlab.academic_net.gateway.dto.UserProfileDto.serializer(),
        """{"user_id":"u","details":$details}""",
    ).details.openToCollaboration

    @Test
    fun aMissingKeyAndAnExplicitNullAreNull() {
        assertEquals(null, read("""{"display_name":"A"}"""))
        assertEquals(null, read("""{"display_name":"A","open_to_collaboration":null}"""))
    }

    @Test
    fun trueAndFalseAreRead() {
        assertEquals(true, read("""{"open_to_collaboration":true}"""))
        assertEquals(false, read("""{"open_to_collaboration":false}"""))
    }
}
