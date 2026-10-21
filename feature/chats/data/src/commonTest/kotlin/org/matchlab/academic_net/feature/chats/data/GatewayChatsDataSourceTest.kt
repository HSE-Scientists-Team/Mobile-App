package org.matchlab.academic_net.feature.chats.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.chats.domain.ChatMessage
import org.matchlab.academic_net.feature.chats.domain.Conversation
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.chats.domain.ConversationNotFoundException
import org.matchlab.academic_net.gateway.ChatApi
import org.matchlab.academic_net.gateway.UserDirectory
import org.matchlab.academic_net.gateway.dto.ChatConversationDto
import org.matchlab.academic_net.gateway.dto.ChatConversationListDto
import org.matchlab.academic_net.gateway.dto.ChatMessageDto
import org.matchlab.academic_net.gateway.dto.ChatMessageListDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.utils.IdGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private const val Me = "me"

private fun message(seq: Long, from: String = "u-2", conversation: String = "c-1", body: String = "m$seq") =
    ChatMessageDto("m-$seq", conversation, seq, from, "k-$seq", body, 1_760_000_000 + seq)

private fun conversation(
    id: String = "c-1",
    peer: String = "u-2",
    last: ChatMessageDto? = null,
    unread: Long = 0,
    lastRead: Long = 0,
) = ChatConversationDto(
    id = id,
    peerUserId = peer,
    lastMessageSeq = last?.seq ?: 0,
    lastReadSeq = lastRead,
    unreadCount = unread,
    lastMessageAtUnix = last?.createdAtUnix ?: 0,
    lastMessage = last,
)

/** A server in memory: the conversations and the messages it holds, and the calls it was asked for. */
private class ScriptedChatApi : ChatApi {
    val conversations = mutableListOf<ChatConversationDto>()
    val messagesOf = mutableMapOf<String, MutableList<ChatMessageDto>>()
    val calls = mutableListOf<String>()
    var failure: Throwable? = null
    var sendFailure: Throwable? = null
    val sentClientIds = mutableListOf<String>()
    val readMarks = mutableListOf<Pair<String, Long>>()

    override suspend fun open(userId: String): ChatConversationDto {
        calls += "open $userId"
        failure?.let { throw it }
        return conversations.firstOrNull { it.peerUserId == userId }
            ?: conversation("new-$userId", userId).also { conversations += it }
    }

    override suspend fun conversations(limit: Int, cursor: String?): ChatConversationListDto {
        calls += "conversations ${cursor.orEmpty()}"
        failure?.let { throw it }
        val perPage = 2
        val from = cursor?.toInt() ?: 0
        val next = if (from + perPage < conversations.size) (from + perPage).toString() else ""
        return ChatConversationListDto(conversations.drop(from).take(perPage), next)
    }

    override suspend fun messages(conversationId: String, limit: Int, beforeSeq: Long?, afterSeq: Long?): ChatMessageListDto {
        calls += "messages $conversationId after=$afterSeq"
        failure?.let { throw it }
        val all = messagesOf[conversationId].orEmpty()
        val items = if (afterSeq != null) all.filter { it.seq > afterSeq }.take(limit) else all.takeLast(limit)
        val hasMore = if (afterSeq != null) all.count { it.seq > afterSeq } > limit else all.size > limit
        return ChatMessageListDto(items, hasMore)
    }

    override suspend fun send(conversationId: String, clientMessageId: String, body: String): ChatMessageDto {
        calls += "send $conversationId"
        sentClientIds += clientMessageId
        sendFailure?.let { throw it }
        val existing = messagesOf.getOrPut(conversationId) { mutableListOf() }
        return existing.firstOrNull { it.clientMessageId == clientMessageId }
            ?: message((existing.maxOfOrNull { it.seq } ?: 0) + 1, Me, conversationId, body)
                .copy(clientMessageId = clientMessageId).also { existing += it }
    }

    override suspend fun markRead(conversationId: String, lastReadSeq: Long) {
        calls += "read $conversationId $lastReadSeq"
        failure?.let { throw it }
        readMarks += conversationId to lastReadSeq
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GatewayChatsDataSourceTest {
    private val api = ScriptedChatApi()
    private val names = mutableMapOf("u-2" to "Анна", "u-3" to "Борис")
    private var ids = 0

    private fun TestScope.source(): GatewayChatsDataSource {
        val users = UserDirectory(
            fetch = { id ->
                val name = names[id] ?: throw RemoteFailure.Http(404)
                UserProfileDto(userId = id, details = UserProfileInputDto(displayName = name), verified = id == "u-3")
            },
            scope = backgroundScope,
        )
        return GatewayChatsDataSource(api, users, viewerId = UserId(Me), idGenerator = { "id-${++ids}" }, pollInterval = 5.seconds)
    }

    private fun TestScope.collectConversations(source: GatewayChatsDataSource): Pair<MutableList<List<Conversation>>, Job> {
        val seen = mutableListOf<List<Conversation>>()
        val job = backgroundScope.launch { source.observeConversations().collect { seen += it } }
        runCurrent()
        return seen to job
    }

    private fun TestScope.collectMessages(source: GatewayChatsDataSource, id: String = "c-1"): Pair<MutableList<List<ChatMessage>>, Job> {
        val seen = mutableListOf<List<ChatMessage>>()
        val job = backgroundScope.launch { source.observeMessages(ConversationId(id)).collect { seen += it } }
        runCurrent()
        return seen to job
    }

    // ---- conversations ----

    @Test
    fun theConversationsComeNewestFirstWithTheirPeersAndLastMessages() = runTest {
        api.conversations += conversation("c-2", "u-3", last = message(1, from = Me, conversation = "c-2"))
        api.conversations += conversation("c-1", "u-2", last = message(3), unread = 2)

        val list = source().observeConversations().first()

        assertEquals(listOf("c-1", "c-2"), list.map { it.id.value })
        val first = list[0]
        assertEquals(UserSummary(UserId("u-2"), "Анна", null, false), first.peer)
        assertEquals("m3", first.lastMessage?.text)
        assertFalse(first.lastMessage!!.isOutgoing)
        assertEquals(2, first.unreadCount)
        assertTrue(list[1].lastMessage!!.isOutgoing)
        assertTrue(list[1].peer.isVerified)
    }

    @Test
    fun everyPageOfTheListIsRead() = runTest {
        for (n in 1..3) api.conversations += conversation("c-$n", "u-2", last = message(n.toLong(), conversation = "c-$n"))

        val list = source().observeConversations().first()

        assertEquals(setOf("c-1", "c-2", "c-3"), list.map { it.id.value }.toSet())
        assertEquals(listOf("conversations ", "conversations 2"), api.calls.filter { it.startsWith("conversations") })
    }

    @Test
    fun aPeerWhoCannotBeReadHasAnEmptyName() = runTest {
        api.conversations += conversation("c-1", "u-404", last = message(1))

        val peer = source().observeConversations().first().single().peer

        assertEquals("", peer.name)
        assertEquals(UserId("u-404"), peer.id)
    }

    @Test
    fun aCollectedListAsksAgainEveryIntervalAndStopsWhenNobodyCollects() = runTest {
        val source = source()
        api.conversations += conversation("c-1", "u-2", last = message(1))
        val (seen, job) = collectConversations(source)
        assertEquals(1, seen.last().size)

        api.conversations += conversation("c-3", "u-3", last = message(9, conversation = "c-3"))
        advanceTimeBy(5.seconds + 1.seconds)
        runCurrent()
        assertEquals(2, seen.last().size)

        job.cancel()
        runCurrent()
        val callsAfterStop = api.calls.size
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(callsAfterStop, api.calls.size, "nobody collects, nobody asks")
    }

    @Test
    fun theFirstFailureEndsTheFlowAndALaterOneKeepsTheData() = runTest {
        api.failure = RemoteFailure.NoConnection()
        assertFailsWith<RemoteFailure.NoConnection> { source().observeConversations().first() }

        api.failure = null
        api.conversations += conversation("c-1", "u-2", last = message(1))
        val source = source()
        val (seen, _) = collectConversations(source)
        api.failure = RemoteFailure.Http(503)
        advanceTimeBy(6.seconds)
        runCurrent()

        assertEquals(1, seen.last().size, "the list stays")
    }

    // ---- messages ----

    @Test
    fun theLatestMessagesAreReadFirstAndThenOnlyTheNewOnes() = runTest {
        api.messagesOf["c-1"] = mutableListOf(message(1), message(2, from = Me))
        val (seen, _) = collectMessages(source())

        assertEquals(listOf("m1", "m2"), seen.last().map { it.text })
        assertEquals(listOf(false, true), seen.last().map { it.isOutgoing })

        api.messagesOf.getValue("c-1") += message(3)
        advanceTimeBy(6.seconds)
        runCurrent()

        assertEquals(listOf("m1", "m2", "m3"), seen.last().map { it.text })
        assertTrue("messages c-1 after=2" in api.calls, api.calls.toString())
    }

    @Test
    fun aMessageIsKeptOnceAndAnUnchangedListIsNotEmittedAgain() = runTest {
        api.messagesOf["c-1"] = mutableListOf(message(1))
        val (seen, _) = collectMessages(source())

        advanceTimeBy(16.seconds)
        runCurrent()

        assertEquals(1, seen.size)
    }

    @Test
    fun anEmptyConversationIsAnEmptyListOnceRead() = runTest {
        val (seen, _) = collectMessages(source())

        assertEquals(listOf(emptyList()), seen)
    }

    // ---- sending ----

    @Test
    fun aSentMessageIsOutgoingAndShowsInTheMessagesAndTheConversation() = runTest {
        api.conversations += conversation("c-1", "u-2")
        val source = source()
        val (conversations, _) = collectConversations(source)
        val (messages, _) = collectMessages(source)

        val sent = source.sendMessage(ConversationId("c-1"), "Привет")
        runCurrent()

        assertTrue(sent.isOutgoing)
        assertEquals("Привет", sent.text)
        assertEquals(listOf("Привет"), messages.last().map { it.text })
        assertEquals("Привет", conversations.last().single().lastMessage?.text)
        assertEquals(listOf("id-1"), api.sentClientIds)
    }

    @Test
    fun aRepeatOfAFailedSendKeepsTheClientIdAndANewTextGetsANewOne() = runTest {
        val source = source()
        api.sendFailure = RemoteFailure.Timeout()

        assertFailsWith<RemoteFailure.Timeout> { source.sendMessage(ConversationId("c-1"), "Привет") }
        assertFailsWith<RemoteFailure.Timeout> { source.sendMessage(ConversationId("c-1"), "Привет") }
        api.sendFailure = null
        source.sendMessage(ConversationId("c-1"), "Привет")
        source.sendMessage(ConversationId("c-1"), "Привет")

        assertEquals(listOf("id-1", "id-1", "id-1", "id-2"), api.sentClientIds)
    }

    @Test
    fun aMissingConversationIsNotFound() = runTest {
        api.sendFailure = RemoteFailure.Http(404)

        assertFailsWith<ConversationNotFoundException> { source().sendMessage(ConversationId("c-9"), "x") }
    }

    @Test
    fun otherFailuresOfASendAreThrownAsTheyAre() = runTest {
        api.sendFailure = RemoteFailure.Http(403)

        assertEquals(403, assertFailsWith<RemoteFailure.Http> { source().sendMessage(ConversationId("c-1"), "x") }.status)
    }

    // ---- reading ----

    @Test
    fun theReadMarkIsTheLastSeqAndIsSentOnlyWhenItIsNewer() = runTest {
        api.conversations += conversation("c-1", "u-2", last = message(5), unread = 3, lastRead = 2)
        val source = source()
        val (conversations, _) = collectConversations(source)

        source.markAsRead(ConversationId("c-1"))
        source.markAsRead(ConversationId("c-1"))
        runCurrent()

        assertEquals(listOf("c-1" to 5L), api.readMarks)
        assertEquals(0, conversations.last().single().unreadCount)
    }

    @Test
    fun nothingIsMarkedWhenNothingIsKnown() = runTest {
        source().markAsRead(ConversationId("c-1"))

        assertTrue(api.readMarks.isEmpty())
    }

    @Test
    fun aMissingConversationCannotBeMarked() = runTest {
        api.conversations += conversation("c-1", "u-2", last = message(5))
        val source = source()
        collectConversations(source)
        api.failure = RemoteFailure.Http(404)

        assertFailsWith<ConversationNotFoundException> { source.markAsRead(ConversationId("c-1")) }
    }

    // ---- opening ----

    @Test
    fun openingAConversationGivesItsIdAndKeepsItInTheList() = runTest {
        val source = source()
        val peer = UserSummary(UserId("u-3"), "Борис")

        val id = source.openConversationWith(peer)

        assertEquals(ConversationId("new-u-3"), id)
        assertEquals(listOf("open u-3"), api.calls)
        val conversation = source.observeConversations().first().single { it.id == id }
        assertEquals("Борис", conversation.peer.name)
        assertNull(conversation.lastMessage)
    }

    @Test
    fun aFailureOfOpeningIsThrown() = runTest {
        api.failure = RemoteFailure.Http(403)

        assertEquals(403, assertFailsWith<RemoteFailure.Http> { source().openConversationWith(UserSummary(UserId("u-3"), "Б")) }.status)
    }
}
