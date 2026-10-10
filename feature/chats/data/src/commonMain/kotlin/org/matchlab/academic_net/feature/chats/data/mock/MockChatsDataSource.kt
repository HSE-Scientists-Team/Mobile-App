package org.matchlab.academic_net.feature.chats.data.mock

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.matchlab.academic_net.common.model.UserId
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
 * In-memory stand-in for the backend. Starts with the chats from the Figma screens (their times
 * are relative to [clock], so "Вчера" stays yesterday) and waits [latencyMillis] before answering.
 * Nobody answers: incoming messages need the real server.
 */
class MockChatsDataSource(
    private val clock: Clock = Clock.System,
    private val idGenerator: IdGenerator = UuidIdGenerator,
    private val latencyMillis: Long = DefaultLatencyMillis,
    seed: Boolean = true,
) : ChatsDataSource {
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val messages = MutableStateFlow<Map<ConversationId, List<ChatMessage>>>(emptyMap())

    init {
        if (seed) seedFromDesign()
    }

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

    private fun seedFromDesign() {
        val now = clock.now()
        fun message(id: String, text: String, ago: Duration, outgoing: Boolean) =
            ChatMessage(MessageId(id), text, now - ago, outgoing)

        val elena = conversation(
            id = "chat-elena",
            peer = MockChatPeers.elena,
            unread = 2,
            history = listOf(
                message("m-e1", "Здравствуйте, Арджун! Спасибо за отклик на роль ML Engineer — профиль очень подходящий.", 80.minutes, false),
                message("m-e2", "Расскажите про опыт обучения моделей компьютерного зрения?", 79.minutes, false),
                message("m-e3", "Здравствуйте! Обучал MobileNetV3 для сегментации коридоров в Stanford AI Lab, разворачивал на Raspberry Pi.", 68.minutes, true),
                message("m-e4", "Отлично. Готовы созвониться завтра в 15:00?", 3.minutes, false),
            ),
        )
        val maria = conversation(
            id = "chat-maria",
            peer = MockChatPeers.maria,
            unread = 0,
            history = listOf(
                message("m-m1", "Мы приглашаем вас в проект «AI-навигация по кампусу»", 1.days + 40.minutes, true),
                message("m-m2", "Спасибо за приглашение! Посмотрю роль", 1.days, false),
            ),
        )
        val daniil = conversation(
            id = "chat-daniil",
            peer = MockChatPeers.daniil,
            unread = 0,
            history = listOf(
                message("m-d1", "Привет! Ищу проект по робототехнике", 32.days, false),
            ),
        )
        listOf(elena, maria, daniil).forEach { (conversation, history) ->
            conversations.update { it + conversation }
            messages.update { it + (conversation.id to history) }
        }
    }

    private fun conversation(
        id: String,
        peer: UserSummary,
        unread: Int,
        history: List<ChatMessage>,
    ): Pair<Conversation, List<ChatMessage>> =
        Conversation(ConversationId(id), peer, history.last(), unread) to history

    private companion object {
        const val DefaultLatencyMillis = 300L
    }
}

/** People of the seeded chats; the ids match the collaborators of the mock search. */
object MockChatPeers {
    val elena = UserSummary(UserId("user-elena"), "Проф. Елена Васкес", isVerified = true)
    val maria = UserSummary(UserId("user-maria"), "Мария Соколова", isVerified = true)
    val daniil = UserSummary(UserId("user-daniil"), "Даниил Крюков", isVerified = true)
}
