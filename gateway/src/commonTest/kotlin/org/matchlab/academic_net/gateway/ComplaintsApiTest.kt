package org.matchlab.academic_net.gateway

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.CreateComplaintDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** `POST /complaints` over a mock engine: the body, the answer that is never read, and the failures. */
class ComplaintsApiTest {
    private fun server(body: String = """{"id":"c-1","status":"1","created_at":"2026-10-19T10:00:00Z"}""", status: HttpStatusCode = HttpStatusCode.Created) =
        ProjectSessionFixture { json(body, status) }

    @Test
    fun aComplaintAboutAPersonIsAPostInSnakeCaseWithOneTargetAndNoFiles() = runTest {
        val server = server()

        server.session.complaints.create(CreateComplaintDto(subject = "3", description = "Подделал диплом", targetUserId = "u/1"))

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/complaints", request.url.encodedPath)
        assertEquals(listOf("Bearer ${ProjectSessionFixture.Token}"), request.authorizations)
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
        val body = request.bodyObject()
        assertEquals("3", body.getValue("subject").jsonPrimitive.content)
        assertEquals("Подделал диплом", body.getValue("description").jsonPrimitive.content)
        assertEquals("u/1", body.getValue("target_user_id").jsonPrimitive.content)
        assertFalse("target_project_id" in body.keys, "the other target is not sent")
        assertEquals(JsonArray(emptyList()), body.getValue("file_ids"))
    }

    @Test
    fun aComplaintAboutAProjectSendsTheProjectTargetOnly() = runTest {
        val server = server()

        server.session.complaints.create(CreateComplaintDto(subject = "2", description = "Ложь", targetProjectId = "p-1"))

        val body = server.requests.single().bodyObject()
        assertEquals("p-1", body.getValue("target_project_id").jsonPrimitive.content)
        assertFalse("target_user_id" in body.keys)
        assertEquals("2", body.getValue("subject").jsonPrimitive.content)
    }

    @Test
    fun theAnswerIsNotReadSoAnUnreadableOneIsNotAFailure() = runTest {
        for (answer in listOf("[]", "not json", "", "{}")) {
            val server = server(answer)

            server.session.complaints.create(CreateComplaintDto("1", "x", targetUserId = "u"))

            assertEquals(1, server.requests.size, "answer '$answer': no repeat")
        }
    }

    @Test
    fun anErrorStatusIsAnHttpFailureWithItsStatusAndIsNeverRepeated() = runTest {
        for (status in listOf(400, 403, 404, 409, 500)) {
            val server = ProjectSessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина") }

            val failure = failureOf<RemoteFailure.Http> {
                server.session.complaints.create(CreateComplaintDto("1", "x", targetUserId = "u"))
            }

            assertEquals(status, failure.status)
            assertEquals(1, server.requests.size, "a repeat would make a second complaint (G48)")
        }
    }

    @Test
    fun aRefusedTokenIsReported() = runTest {
        val server = ProjectSessionFixture { jsonError(HttpStatusCode.Unauthorized, "expired") }

        failureOf<RemoteFailure.Http> { server.session.complaints.create(CreateComplaintDto("1", "x", targetUserId = "u")) }

        assertEquals(listOf(ProjectSessionFixture.Token), server.rejected)
    }
}
