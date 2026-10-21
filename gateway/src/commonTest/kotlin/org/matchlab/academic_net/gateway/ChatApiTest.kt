package org.matchlab.academic_net.gateway

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.matchlab.academic_net.common.result.RemoteFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The chat of OpenAPI v2.6.0 over a mock engine: the paths, the bodies, the int64 strings and the failures. */
class ChatApiTest {
    private val conversation = """{"id":"c-1","peer_user_id":"u-2","last_message_seq":"7","last_read_seq":"5","peer_last_read_seq":"6","unread_count":"2","created_at_unix":"1760000000","last_message_at_unix":"1760000500","last_message":{"id":"m-7","conversation_id":"c-1","seq":"7","sender_user_id":"u-2","client_message_id":"k-7","body":"Привет","created_at_unix":"1760000500"}}"""

    @Test
    fun openingAConversationPostsTheUserAndReadsTheInt64StringsOfTheAnswer() = runTest {
        val server = ProjectSessionFixture { json(conversation, HttpStatusCode.Created) }

        val opened = server.session.chat.open("u-2")

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/chat/conversations", request.url.encodedPath)
        assertEquals("u-2", request.bodyObject().getValue("user_id").jsonPrimitive.content)
        assertEquals("c-1", opened.id)
        assertEquals(7L, opened.lastMessageSeq)
        assertEquals(2L, opened.unreadCount)
        assertEquals("Привет", opened.lastMessage?.body)
        assertEquals(7L, opened.lastMessage?.seq)
    }

    @Test
    fun anEmptyConversationHasNoLastMessage() = runTest {
        val server = ProjectSessionFixture {
            json("""{"id":"c-1","peer_user_id":"u-2","last_message_seq":"0","last_read_seq":"0","peer_last_read_seq":"0","unread_count":"0","created_at_unix":"1760000000","last_message":null}""")
        }

        val opened = server.session.chat.open("u-2")

        assertNull(opened.lastMessage)
        assertEquals(0L, opened.lastMessageAtUnix)
    }

    @Test
    fun theListAsksForTheLimitAndTheCursorOnlyWhenThereIsOne() = runTest {
        val server = ProjectSessionFixture { json("""{"items":[$conversation],"next_cursor":"abc"}""") }

        val first = server.session.chat.conversations(limit = 50, cursor = null)
        server.session.chat.conversations(limit = 50, cursor = "abc")

        assertEquals("abc", first.nextCursor)
        assertEquals(1, first.items.size)
        val (one, two) = server.requests
        assertEquals("/api/v1/chat/conversations", one.url.encodedPath)
        assertEquals("50", one.url.parameters["limit"])
        assertFalse("cursor" in one.url.parameters.names())
        assertEquals("abc", two.url.parameters["cursor"])
    }

    @Test
    fun theMessagesAreAskedForBeforeOrAfterASeqButNotBoth() = runTest {
        val server = ProjectSessionFixture { json("""{"items":[],"has_more":true}""") }

        val latest = server.session.chat.messages("c/1", limit = 30)
        server.session.chat.messages("c/1", limit = 30, beforeSeq = 10)
        server.session.chat.messages("c/1", limit = 30, afterSeq = 12)
        assertFailsWith<IllegalArgumentException> { server.session.chat.messages("c/1", 30, beforeSeq = 1, afterSeq = 2) }

        assertTrue(latest.hasMore)
        val (a, b, c) = server.requests
        assertEquals("/api/v1/chat/conversations/c%2F1/messages", a.url.encodedPath)
        assertEquals(listOf("limit"), a.url.parameters.names().toList())
        assertEquals("10", b.url.parameters["before_seq"])
        assertEquals("12", c.url.parameters["after_seq"])
        assertEquals(3, server.requests.size, "the call with both filters sends nothing")
    }

    @Test
    fun sendingPostsTheClientIdAndTheText() = runTest {
        val server = ProjectSessionFixture {
            json("""{"id":"m-8","conversation_id":"c-1","seq":"8","sender_user_id":"me","client_message_id":"k-8","body":"Да","created_at_unix":"1760000600"}""", HttpStatusCode.Created)
        }

        val sent = server.session.chat.send("c-1", clientMessageId = "k-8", body = "Да")

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/chat/conversations/c-1/messages", request.url.encodedPath)
        assertEquals("k-8", request.bodyObject().getValue("client_message_id").jsonPrimitive.content)
        assertEquals("Да", request.bodyObject().getValue("body").jsonPrimitive.content)
        assertEquals(8L, sent.seq)
    }

    @Test
    fun theReadMarkIsAPutOfTheNumberAndItsEmptyAnswerIsFine() = runTest {
        val server = ProjectSessionFixture { noContent() }

        server.session.chat.markRead("c-1", 12)

        val request = server.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/api/v1/chat/conversations/c-1/read", request.url.encodedPath)
        assertEquals("12", request.bodyObject().getValue("last_read_seq").jsonPrimitive.content)
    }

    @Test
    fun anErrorStatusIsAnHttpFailureWithItsStatus() = runTest {
        for (status in listOf(400, 403, 404, 409, 503)) {
            val server = ProjectSessionFixture { jsonError(HttpStatusCode.fromValue(status), "причина") }

            val failure = failureOf<RemoteFailure.Http> { server.session.chat.send("c-1", "k", "x") }

            assertEquals(status, failure.status)
        }
    }
}
