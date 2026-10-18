package org.matchlab.academic_net.feature.chats.data.mock

import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.chats.data.ChatsDataSource
import org.matchlab.academic_net.feature.chats.domain.ChatMessage
import org.matchlab.academic_net.feature.chats.domain.Conversation
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.chats.domain.ConversationNotFoundException
import org.matchlab.academic_net.feature.chats.domain.MessageId
import org.matchlab.academic_net.utils.IdGenerator
import org.matchlab.academic_net.utils.UuidIdGenerator

/**
 * In-memory stand-in for the backend (API-GAP G50: the API has no chats). Starts empty and waits [latencyMillis]
 * before answering. Nobody answers: incoming messages need the real server.
 */
class MockChatsDataSource(
    private val clock: Clock = Clock.System,
    private val idGenerator: IdGenerator = UuidIdGenerator,
    private val latencyMillis: Long = DefaultLatencyMillis,
) : ChatsDataSource {
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val messages = MutableStateFlow<Map<ConversationId, List<ChatMessage>>>(emptyMap())

    override fun observeConversations(): Flow<List<Conversation>> = conversations

    override fun observeMessages(id: ConversationId): Flow<List<ChatMessage>> =
        messages.map { it[id].orEmpty() }

    override suspend fun openConversationWith(peer: UserSummary): ConversationId {
        simulateNetwork()
        conversations.value.firstOrNull { it.peer.id == peer.id }?.let { return it.id }

        val created = Conversation(id = ConversationId(idGenerator.newId()), peer = peer)
        conversations.update { it + created }
        return created.id
    }

    override suspend fun sendMessage(id: ConversationId, text: String): ChatMessage {
        simulateNetwork()
        if (conversations.value.none { it.id == id }) throw ConversationNotFoundException(id)

        val message = ChatMessage(
            id = MessageId(idGenerator.newId()),
            text = text,
            sentAt = clock.now(),
            isOutgoing = true,
        )
        messages.update { it + (id to it[id].orEmpty() + message) }
        conversations.update { all ->
            all.map { if (it.id == id) it.copy(lastMessage = message) else it }
        }
        return message
    }

    override suspend fun markAsRead(id: ConversationId) {
        conversations.update { all ->
            all.map { if (it.id == id) it.copy(unreadCount = 0) else it }
        }
    }

    private suspend fun simulateNetwork() {
        if (latencyMillis > 0) delay(latencyMillis)
    }

    private companion object {
        const val DefaultLatencyMillis = 300L
    }
}
