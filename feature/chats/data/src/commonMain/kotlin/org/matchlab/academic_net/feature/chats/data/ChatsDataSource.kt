package org.matchlab.academic_net.feature.chats.data

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.chats.domain.ChatMessage
import org.matchlab.academic_net.feature.chats.domain.Conversation
import org.matchlab.academic_net.feature.chats.domain.ConversationId

/** Where the chats come from: [GatewayChatsDataSource], the text chat of the Gateway. */
interface ChatsDataSource {
    fun observeConversations(): Flow<List<Conversation>>

    fun observeMessages(id: ConversationId): Flow<List<ChatMessage>>

    suspend fun openConversationWith(peer: UserSummary): ConversationId

    suspend fun sendMessage(id: ConversationId, text: String): ChatMessage

    suspend fun markAsRead(id: ConversationId)
}
