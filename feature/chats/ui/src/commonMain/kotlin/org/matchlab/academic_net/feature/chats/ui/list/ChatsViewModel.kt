package org.matchlab.academic_net.feature.chats.ui.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import org.matchlab.academic_net.feature.chats.domain.Conversation
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveConversationsUseCase
import org.matchlab.academic_net.utils.ChatListTime
import org.matchlab.academic_net.utils.chatListTime

@Immutable
data class ChatItem(
    val id: ConversationId,
    val name: String,
    val lastMessage: String,
    /** Which day the last message belongs to; the text of it depends on the app language. */
    val time: ChatListTime,
    /** Key of the peer's picture: the user id. */
    val avatarKey: String,
    val unreadCount: Int,
)

@Immutable
data class ChatsUiState(
    val isLoading: Boolean = true,
    val chats: List<ChatItem> = emptyList(),
    val hasError: Boolean = false,
)

/** "12 Чаты": the root screen of the "Чаты" tab. */
class ChatsViewModel(
    observeConversations: ObserveConversationsUseCase,
    clock: Clock,
    timeZone: () -> TimeZone,
) : ViewModel() {
    val uiState: StateFlow<ChatsUiState> = observeConversations()
        .map { conversations ->
            val now = clock.now()
            val zone = timeZone()
            ChatsUiState(isLoading = false, chats = conversations.mapNotNull { it.toItem(now, zone) })
        }
        .catch { emit(ChatsUiState(isLoading = false, hasError = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), ChatsUiState())

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}

private fun Conversation.toItem(now: kotlin.time.Instant, zone: TimeZone): ChatItem? {
    val last = lastMessage ?: return null
    return ChatItem(
        id = id,
        name = peer.name,
        lastMessage = last.text,
        time = chatListTime(last.sentAt, now, zone),
        avatarKey = peer.id.value,
        unreadCount = unreadCount,
    )
}
