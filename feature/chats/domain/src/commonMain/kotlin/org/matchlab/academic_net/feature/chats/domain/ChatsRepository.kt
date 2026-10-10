package org.matchlab.academic_net.feature.chats.domain

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.UserSummary

/** Requested conversation does not exist. */
class ConversationNotFoundException(val conversationId: ConversationId) :
    NoSuchElementException("Conversation ${conversationId.value} not found")

/** Suspending functions throw on failure (e.g. no network); the use cases turn that into results. */
interface ChatsRepository {
    /** Conversations that have messages, the most recently active first. */
    fun observeConversations(): Flow<List<Conversation>>

    /** Emits `null` if there is no such conversation. */
    fun observeConversation(id: ConversationId): Flow<Conversation?>

    /** Messages of the conversation, oldest first. */
    fun observeMessages(id: ConversationId): Flow<List<ChatMessage>>

    /** The chat with [peer]; created empty if the two have not talked yet. */
    suspend fun openConversationWith(peer: UserSummary): ConversationId

    /** @throws ConversationNotFoundException */
    suspend fun sendMessage(id: ConversationId, text: String): ChatMessage

    suspend fun markAsRead(id: ConversationId)
}
