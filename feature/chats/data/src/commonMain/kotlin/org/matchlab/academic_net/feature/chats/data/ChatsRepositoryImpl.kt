package org.matchlab.academic_net.feature.chats.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.chats.domain.ChatMessage
import org.matchlab.academic_net.feature.chats.domain.ChatsRepository
import org.matchlab.academic_net.feature.chats.domain.Conversation
import org.matchlab.academic_net.feature.chats.domain.ConversationId

class ChatsRepositoryImpl(
    private val dataSource: ChatsDataSource,
) : ChatsRepository {
    override fun observeConversations(): Flow<List<Conversation>> =
        dataSource.observeConversations().map { conversations ->
            conversations
                .filter { it.lastMessage != null }
                .sortedByDescending { it.lastMessage?.sentAt }
        }

    override fun observeConversation(id: ConversationId): Flow<Conversation?> =
        dataSource.observeConversations().map { all -> all.firstOrNull { it.id == id } }

    override fun observeMessages(id: ConversationId): Flow<List<ChatMessage>> =
        dataSource.observeMessages(id).map { messages -> messages.sortedBy { it.sentAt } }

    override suspend fun openConversationWith(peer: UserSummary): ConversationId =
        dataSource.openConversationWith(peer)

    override suspend fun sendMessage(id: ConversationId, text: String): ChatMessage =
        dataSource.sendMessage(id, text)

    override suspend fun markAsRead(id: ConversationId) = dataSource.markAsRead(id)
}
