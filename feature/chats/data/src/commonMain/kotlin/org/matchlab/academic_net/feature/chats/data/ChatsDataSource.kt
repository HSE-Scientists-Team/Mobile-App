package org.matchlab.academic_net.feature.chats.data

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.chats.domain.ChatMessage
import org.matchlab.academic_net.feature.chats.domain.Conversation
import org.matchlab.academic_net.feature.chats.domain.ConversationId

/**
 * Where the chats come from. The only implementation is
 * [org.matchlab.academic_net.feature.chats.data.mock.MockChatsDataSource]; a remote one (with DTOs
 * and a socket for incoming messages) replaces it when the backend appears.
 */
interface ChatsDataSource {
    fun observeConversations(): Flow<List<Conversation>>

    fun observeMessages(id: ConversationId): Flow<List<ChatMessage>>

    suspend fun openConversationWith(peer: UserSummary): ConversationId

    suspend fun sendMessage(id: ConversationId, text: String): ChatMessage

    suspend fun markAsRead(id: ConversationId)
}
