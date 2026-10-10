package org.matchlab.academic_net.feature.chats.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.chats.domain.ChatLimits
import org.matchlab.academic_net.feature.chats.domain.ChatMessage
import org.matchlab.academic_net.feature.chats.domain.ChatsRepository
import org.matchlab.academic_net.feature.chats.domain.Conversation
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.chats.domain.MessageError
import org.matchlab.academic_net.utils.suspendRunCatching

/** The chat list. */
class ObserveConversationsUseCase(private val repository: ChatsRepository) {
    operator fun invoke(): Flow<List<Conversation>> = repository.observeConversations()
}

/** One conversation (its peer is the screen's title). */
class ObserveConversationUseCase(private val repository: ChatsRepository) {
    operator fun invoke(id: ConversationId): Flow<Conversation?> = repository.observeConversation(id)
}

class ObserveMessagesUseCase(private val repository: ChatsRepository) {
    operator fun invoke(id: ConversationId): Flow<List<ChatMessage>> = repository.observeMessages(id)
}

/** "Общаться с кандидатом в чате": the conversation with a person, created on first use. */
class OpenConversationUseCase(private val repository: ChatsRepository) {
    suspend operator fun invoke(peer: UserSummary): Result<ConversationId> =
        suspendRunCatching { repository.openConversationWith(peer) }
}

class SendMessageUseCase(private val repository: ChatsRepository) {
    suspend operator fun invoke(
        id: ConversationId,
        text: String,
    ): SubmitResult<ChatMessage, MessageError> {
        val trimmed = text.trim()
        val errors = buildSet {
            if (trimmed.isEmpty()) add(MessageError.Empty)
            if (trimmed.length > ChatLimits.MaxMessageLength) add(MessageError.TooLong)
        }
        if (errors.isNotEmpty()) return SubmitResult.Invalid(errors)

        return suspendRunCatching { repository.sendMessage(id, trimmed) }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}

/** Called when the user opens the chat: the unread counter goes to zero. */
class MarkConversationReadUseCase(private val repository: ChatsRepository) {
    suspend operator fun invoke(id: ConversationId): Result<Unit> =
        suspendRunCatching { repository.markAsRead(id) }
}
