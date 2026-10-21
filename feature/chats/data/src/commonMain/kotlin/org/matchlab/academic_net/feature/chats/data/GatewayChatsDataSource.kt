package org.matchlab.academic_net.feature.chats.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.chats.domain.ChatMessage
import org.matchlab.academic_net.feature.chats.domain.Conversation
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.chats.domain.ConversationNotFoundException
import org.matchlab.academic_net.feature.chats.domain.MessageId
import org.matchlab.academic_net.gateway.ChatApi
import org.matchlab.academic_net.gateway.UserDirectory
import org.matchlab.academic_net.gateway.dto.ChatConversationDto
import org.matchlab.academic_net.gateway.dto.ChatMessageDto
import org.matchlab.academic_net.utils.IdGenerator
import org.matchlab.academic_net.utils.UuidIdGenerator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * [ChatsDataSource] over the text chat of the Gateway (`/chat/conversations…`, OpenAPI v2.6.0).
 *
 * **No push.** The server cannot tell the app about a new message, so a flow that somebody collects asks again every
 * [pollInterval]: the list of conversations for [observeConversations] (also what the header of an open chat reads), the messages
 * after the last known `seq` for [observeMessages]. The asking stops when nobody collects. The first read of a flow
 * that fails ends the flow with the failure (the screen shows its error); a failure of a later read is skipped and
 * the next round tries again, the last data staying on the screen.
 *
 * - **Conversations:** the latest activity first, as the server sorts them, up to [MaxConversationPages] pages; the
 *   peer of each is read through the session's [users] directory (a person who cannot be read has an empty name; the UI
 *   writes the placeholder). An empty conversation is kept: the repository hides it until somebody writes.
 * - **Messages:** the latest [HistorySize] on the first read, then only what is newer than the last known `seq`; a
 *   message is kept once (by `seq`). Older history is not read yet.
 * - **Sending:** the text goes with a `client_message_id` that stays the same for as long as a send of the same text to
 *   the same conversation has not succeeded, so a repeat after a lost answer does not make the message twice. A 404 is
 *   [ConversationNotFoundException]; any other failure is thrown as the `RemoteFailure` it is.
 * - **Read mark:** [markAsRead] sends the `seq` of the last message the app knows, only when it is newer than the mark
 *   the server has.
 *
 * @param viewerId the user of the session: the messages they wrote are outgoing
 * @param pollInterval how often a collected flow asks the server again
 */
class GatewayChatsDataSource(
    private val chat: ChatApi,
    private val users: UserDirectory,
    private val viewerId: UserId,
    private val idGenerator: IdGenerator = UuidIdGenerator,
    private val pollInterval: Duration = DefaultPollInterval,
) : ChatsDataSource {
    // Everything below changes under [lock]; the requests run inside it too, so a refresh and a send do not interleave.
    private val lock = Mutex()

    private val conversationDtos = MutableStateFlow<Map<String, ChatConversationDto>?>(null)
    private val conversations = MutableStateFlow<List<Conversation>?>(null)
    private val peers = HashMap<String, UserSummary>()
    private val messages = MutableStateFlow<Map<String, List<ChatMessageDto>>>(emptyMap())

    /** The id of a send that did not succeed yet, by the conversation and the text. */
    private val pendingSends = HashMap<Pair<String, String>, String>()

    override fun observeConversations(): Flow<List<Conversation>> = channelFlow {
        val poller = launch { poll { refreshConversations() } }
        launch { conversations.filterNotNull().collect { send(it) } }
        awaitClose { poller.cancel() }
    }

    override fun observeMessages(id: ConversationId): Flow<List<ChatMessage>> = channelFlow {
        val poller = launch { poll { refreshMessages(id.value) } }
        launch {
            messages.map { all -> all[id.value]?.map { it.toDomain() } }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { send(it) }
        }
        awaitClose { poller.cancel() }
    }

    override suspend fun openConversationWith(peer: UserSummary): ConversationId = lock.withLock {
        val dto = chat.open(peer.id.value)
        peers[peer.id.value] = peer
        conversationDtos.value = (conversationDtos.value.orEmpty() + (dto.id to dto))
        publishConversations()
        ConversationId(dto.id)
    }

    override suspend fun sendMessage(id: ConversationId, text: String): ChatMessage = lock.withLock {
        val key = id.value to text
        val clientMessageId = pendingSends.getOrPut(key) { idGenerator.newId() }
        val sent = try {
            chat.send(id.value, clientMessageId, text)
        } catch (failure: RemoteFailure.Http) {
            if (failure.status == NOT_FOUND) throw ConversationNotFoundException(id) else throw failure
        }
        pendingSends.remove(key)
        remember(id.value, listOf(sent))
        conversationDtos.value?.get(id.value)?.let { known ->
            conversationDtos.value = conversationDtos.value.orEmpty() + (id.value to known.afterOwnMessage(sent))
            publishConversations()
        }
        sent.toDomain()
    }

    override suspend fun markAsRead(id: ConversationId): Unit = lock.withLock {
        val known = conversationDtos.value?.get(id.value)
        val last = maxOf(known?.lastMessageSeq ?: 0, messages.value[id.value]?.maxOfOrNull { it.seq } ?: 0)
        if (last <= 0 || (known != null && known.lastReadSeq >= last)) return@withLock
        try {
            chat.markRead(id.value, last)
        } catch (failure: RemoteFailure.Http) {
            if (failure.status == NOT_FOUND) throw ConversationNotFoundException(id) else throw failure
        }
        if (known != null) {
            conversationDtos.value = conversationDtos.value.orEmpty() + (id.value to known.copy(lastReadSeq = last, unreadCount = 0))
            publishConversations()
        }
    }

    /** Asks again every [pollInterval] while the collector lives; the first failure ends the flow, later ones are skipped. */
    private suspend fun poll(refresh: suspend () -> Unit) {
        refresh()
        while (true) {
            delay(pollInterval)
            try {
                refresh()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (ignored: Exception) {
                // The screen keeps what it has; the next round tries again.
            }
        }
    }

    private suspend fun refreshConversations() = lock.withLock {
        val dtos = ArrayList<ChatConversationDto>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = chat.conversations(PageSize, cursor)
            dtos += page.items.filter { it.id.isNotBlank() }
            cursor = page.nextCursor.takeIf { it.isNotEmpty() }
            pages++
        } while (cursor != null && pages < MaxConversationPages)
        loadPeers(dtos.map { it.peerUserId })
        conversationDtos.value = dtos.distinctBy { it.id }.associateBy { it.id }
        publishConversations()
    }

    private suspend fun refreshMessages(conversationId: String) = lock.withLock {
        val known = messages.value[conversationId].orEmpty()
        if (known.isEmpty()) {
            val page = try {
                chat.messages(conversationId, HistorySize)
            } catch (failure: RemoteFailure.Http) {
                if (failure.status == NOT_FOUND) throw ConversationNotFoundException(ConversationId(conversationId)) else throw failure
            }
            // An empty conversation has a (still empty) list, so the screen knows that the read happened.
            if (page.items.isEmpty()) messages.value = messages.value + (conversationId to emptyList()) else remember(conversationId, page.items)
            return@withLock
        }
        var lastSeq = known.maxOf { it.seq }
        repeat(MaxCatchUpPages) {
            val page = chat.messages(conversationId, PageSize, afterSeq = lastSeq)
            if (page.items.isEmpty()) return@withLock
            remember(conversationId, page.items)
            lastSeq = page.items.maxOf { it.seq }
            if (!page.hasMore) return@withLock
        }
    }

    /** Puts [added] into the messages of the conversation: once by `seq`, oldest first. */
    private fun remember(conversationId: String, added: List<ChatMessageDto>) {
        val merged = (messages.value[conversationId].orEmpty() + added).distinctBy { it.seq }.sortedBy { it.seq }
        messages.value = messages.value + (conversationId to merged)
    }

    private suspend fun loadPeers(ids: List<String>) {
        val missing = ids.distinct().filter { it !in peers }
        if (missing.isEmpty()) return
        users.profiles(missing).forEach { (id, profile) ->
            peers[id] = UserSummary(
                id = UserId(id),
                name = profile?.details?.displayName?.trim().orEmpty(),
                avatarUrl = null,
                isVerified = profile?.verified ?: false,
            )
        }
    }

    private fun publishConversations() {
        conversations.value = conversationDtos.value.orEmpty().values
            .map { dto -> dto.toDomain(peers[dto.peerUserId] ?: UserSummary(UserId(dto.peerUserId), "")) }
            .sortedWith(compareByDescending<Conversation> { it.lastMessage?.sentAt }.thenBy { it.id.value })
    }

    private fun ChatMessageDto.toDomain() = ChatMessage(
        id = MessageId(id),
        text = body,
        sentAt = Instant.fromEpochSeconds(createdAtUnix.coerceIn(0, MaxUnixSeconds)),
        isOutgoing = senderUserId == viewerId.value,
    )

    private fun ChatConversationDto.toDomain(peer: UserSummary) = Conversation(
        id = ConversationId(id),
        peer = peer,
        lastMessage = lastMessage?.toDomain(),
        unreadCount = unreadCount.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
    )

    /** The conversation after the viewer wrote [sent]: it is the last message when it is the newest one. */
    private fun ChatConversationDto.afterOwnMessage(sent: ChatMessageDto) = copy(
        lastMessageSeq = maxOf(lastMessageSeq, sent.seq),
        lastMessageAtUnix = sent.createdAtUnix,
        lastMessage = sent.takeIf { it.seq >= lastMessageSeq } ?: lastMessage,
    )

    private companion object {
        val DefaultPollInterval = 5.seconds
        const val PageSize = 100
        const val HistorySize = 100
        const val MaxConversationPages = 3
        const val MaxCatchUpPages = 5
        const val NOT_FOUND = 404
        const val MaxUnixSeconds = 253_402_300_799L
    }
}
