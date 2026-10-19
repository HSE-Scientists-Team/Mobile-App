package org.matchlab.academic_net.gateway

import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.CreateMediaUploadDto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The files of OpenAPI v2.6.0 over a mock engine: the API calls carry the token, the storage links never do. */
class MediaApiTest {
    private val storage = "https://storage.example/matchlab-media/users/u/file?X-Amz-Signature=sig"
    private val pending = """{"file":{"id":"f-1","owner_user_id":"u","original_name":"a.jpg","content_type":"image/jpeg","status":"pending","created_at_unix":"1","is_public":true},"upload_url":"$storage","method":"PUT","headers":{"Content-Type":"image/jpeg","Content-Length":"3","x-amz-acl":"public-read"},"expires_at_unix":"1760000900"}"""
    private val ready = """{"id":"f-1","owner_user_id":"u","original_name":"a.jpg","content_type":"image/jpeg","size_bytes":"3","status":"ready","created_at_unix":"1","is_public":true}"""

    private fun server() = ProjectSessionFixture { request ->
        when {
            request.url.host == "storage.example" && request.method == HttpMethod.Put -> respond("", HttpStatusCode.OK)
            request.url.host == "storage.example" -> respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK)
            request.url.encodedPath == "/api/v1/media/files" -> json(pending, HttpStatusCode.Created)
            request.url.encodedPath.endsWith("/complete") -> json(ready)
            request.url.encodedPath.endsWith("/download-url") -> json("""{"download_url":"$storage","expires_at_unix":"1760000900"}""")
            else -> noContent()
        }
    }

    @Test
    fun anUploadIsAskedForWithTheSizeAndThePublicFlagAndReadsTheSignedRequest() = runTest {
        val server = server()

        val upload = server.session.media.createUpload(CreateMediaUploadDto("a.jpg", "image/jpeg", 3, isPublic = true))

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(listOf("Bearer ${ProjectSessionFixture.Token}"), request.authorizations)
        val body = request.bodyObject()
        assertEquals("a.jpg", body.getValue("original_name").jsonPrimitive.content)
        assertEquals("image/jpeg", body.getValue("content_type").jsonPrimitive.content)
        assertEquals("3", body.getValue("size_bytes").jsonPrimitive.content)
        assertEquals("true", body.getValue("is_public").jsonPrimitive.content)
        assertEquals("f-1", upload.file.id)
        assertEquals(storage, upload.uploadUrl)
        assertEquals("public-read", upload.headers["x-amz-acl"])
    }

    @Test
    fun theBytesGoToTheStorageWithTheSignedHeadersAndWithoutTheToken() = runTest {
        val server = server()
        val upload = server.session.media.createUpload(CreateMediaUploadDto("a.jpg", "image/jpeg", 3, true))

        server.session.media.putContent(upload, byteArrayOf(1, 2, 3))

        val put = server.requests.last()
        assertEquals(HttpMethod.Put, put.method)
        assertEquals("storage.example", put.url.host)
        assertEquals("sig", put.url.parameters["X-Amz-Signature"])
        assertTrue(put.authorizations.isEmpty(), "the token stays out of the storage")
        assertEquals("public-read", put.headers["x-amz-acl"])
        assertEquals("image/jpeg", put.body.contentType?.withoutParameters()?.toString())
        assertContentEquals(byteArrayOf(1, 2, 3), put.body.toByteArray())
    }

    @Test
    fun uploadFileDoesThreeCallsInOrderAndGivesTheReadyFile() = runTest {
        val server = server()

        val file = server.session.media.uploadFile("a.jpg", "image/jpeg", byteArrayOf(1, 2, 3), isPublic = true)

        assertEquals("ready", file.status)
        assertEquals(
            listOf("api.matchlab.space/api/v1/media/files", "storage.example/matchlab-media/users/u/file", "api.matchlab.space/api/v1/media/files/f-1/complete"),
            server.requests.map { it.url.host + it.url.encodedPath },
        )
        assertEquals("3", server.requests.first().bodyObject().getValue("size_bytes").jsonPrimitive.content)
        assertEquals(listOf(true, false, true), server.requests.map { it.authorizations.isNotEmpty() })
    }

    @Test
    fun theDownloadLinkIsAskedForAndTheContentIsReadWithoutTheToken() = runTest {
        val server = server()

        val link = server.session.media.downloadUrl("f/1")
        val bytes = server.session.media.fetch(link.downloadUrl)

        assertEquals(storage, link.downloadUrl)
        assertEquals(1760000900L, link.expiresAtUnix)
        assertContentEquals(byteArrayOf(1, 2, 3), bytes)
        val (api, get) = server.requests
        assertEquals("/api/v1/media/files/f%2F1/download-url", api.url.encodedPath)
        assertTrue(get.authorizations.isEmpty())
    }

    @Test
    fun aRefusedUploadIsAnHttpFailureWithItsStatus() = runTest {
        for (status in listOf(400, 403, 409, 503)) {
            val server = ProjectSessionFixture { jsonError(HttpStatusCode.fromValue(status), "нет") }

            val failure = failureOf<RemoteFailure.Http> {
                server.session.media.createUpload(CreateMediaUploadDto("a.jpg", "image/jpeg", 3))
            }

            assertEquals(status, failure.status)
        }
    }

    @Test
    fun aRefusalOfTheStorageIsAnHttpFailureToo() = runTest {
        val server = ProjectSessionFixture { request ->
            if (request.url.host == "storage.example") respond("<Error/>", HttpStatusCode.Forbidden) else json(pending, HttpStatusCode.Created)
        }
        val upload = server.session.media.createUpload(CreateMediaUploadDto("a.jpg", "image/jpeg", 3))

        val failure = failureOf<RemoteFailure.Http> { server.session.media.putContent(upload, byteArrayOf(1)) }

        assertEquals(403, failure.status)
        assertNull(failure.serverMessage)
    }

    @Test
    fun aClosedSessionSendsNothingToTheStorage() = runTest {
        val server = server()
        val upload = server.session.media.createUpload(CreateMediaUploadDto("a.jpg", "image/jpeg", 3))
        server.session.close()
        val before = server.requests.size

        assertFailsWith<kotlin.coroutines.cancellation.CancellationException> { server.session.media.putContent(upload, byteArrayOf(1)) }

        assertEquals(before, server.requests.size)
    }
}
