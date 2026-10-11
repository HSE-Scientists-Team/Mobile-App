package org.matchlab.academic_net.feature.chats.ui.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.chats.domain.ChatMessage
import org.matchlab.academic_net.feature.chats.domain.Conversation
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.chats.domain.MessageError
import org.matchlab.academic_net.feature.chats.domain.MessageId
import org.matchlab.academic_net.feature.chats.domain.usecase.MarkConversationReadUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveConversationUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveMessagesUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.SendMessageUseCase
import org.matchlab.academic_net.utils.formatMessageTime

@Immutable
data class MessageItem(
    val id: MessageId,
    val text: String,
    val time: String,
    val isOutgoing: Boolean,
)

@Immutable
data class ChatPeer(
    /** Whose profile a tap on the header of the chat opens. */
    val userId: UserId,
    val name: String,
    val isVerified: Boolean,
    /** Key of the peer's avatar illustration. */
    val avatarKey: String,
)

enum class SendProblem { TooLong, Failed }

@Immutable
data class ChatUiState(
    val isLoading: Boolean = true,
    /** `null` while loading, or when the chat does not exist ([isLoading] is `false`). */
    val peer: ChatPeer? = null,
    val messages: List<MessageItem> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val sendProblem: SendProblem? = null,
)

/** "13 Чат с коллаборатором": "общаться с кандидатом в чате". */
class ChatViewModel(
    private val conversationId: ConversationId,
    observeConversation: ObserveConversationUseCase,
    observeMessages: ObserveMessagesUseCase,
    private val sendMessage: SendMessageUseCase,
    markConversationRead: MarkConversationReadUseCase,
    private val timeZone: () -> TimeZone,
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<ChatUiState> = combine(
        observeConversation(conversationId).catch { emit(null) },
        observeMessages(conversationId).catch { emit(emptyList()) },
        local,
    ) { conversation, messages, local ->
        val zone = timeZone()
        ChatUiState(
            isLoading = false,
            peer = conversation?.toPeer(),
            messages = messages.map { it.toItem(zone) },
            draft = local.draft,
            isSending = local.isSending,
            sendProblem = local.sendProblem,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), ChatUiState())

    init {
        // Opening the chat reads it.
        viewModelScope.launch { markConversationRead(conversationId) }
    }

    fun onDraftChange(text: String) = local.update { it.copy(draft = text, sendProblem = null) }

    fun onSendClick() {
        val state = local.value
        if (state.isSending || state.draft.isBlank()) return
        local.update { it.copy(isSending = true, sendProblem = null) }
        viewModelScope.launch {
            when (val result = sendMessage(conversationId, state.draft)) {
                is SubmitResult.Success -> local.update {
                    // Whatever was typed while the message was on its way stays in the field.
                    val typedMeanwhile = if (it.draft == state.draft) "" else it.draft
                    it.copy(draft = typedMeanwhile, isSending = false)
                }
                is SubmitResult.Invalid -> local.update {
                    val tooLong = MessageError.TooLong in result.errors
                    it.copy(isSending = false, sendProblem = if (tooLong) SendProblem.TooLong else null)
                }
                is SubmitResult.Failure -> local.update {
                    it.copy(isSending = false, sendProblem = SendProblem.Failed)
                }
            }
        }
    }

    private data class LocalState(
        val draft: String = "",
        val isSending: Boolean = false,
        val sendProblem: SendProblem? = null,
    )

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}

private fun Conversation.toPeer() = ChatPeer(
    userId = peer.id,
    name = peer.name,
    isVerified = peer.isVerified,
    avatarKey = peer.id.value,
)

private fun ChatMessage.toItem(zone: TimeZone) = MessageItem(
    id = id,
    text = text,
    time = formatMessageTime(sentAt, zone),
    isOutgoing = isOutgoing,
)
