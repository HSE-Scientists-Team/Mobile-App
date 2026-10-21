package org.matchlab.academic_net.feature.chats.ui

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.matchlab.academic_net.feature.chats.data.ChatsDataSource
import org.matchlab.academic_net.feature.chats.data.ChatsRepositoryImpl
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.chats.domain.ChatMessage
import org.matchlab.academic_net.feature.chats.domain.Conversation
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.chats.domain.ConversationNotFoundException
import org.matchlab.academic_net.feature.chats.domain.MessageId
import org.matchlab.academic_net.feature.chats.domain.usecase.MarkConversationReadUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveConversationUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveConversationsUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveMessagesUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.OpenConversationUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.SendMessageUseCase

val TestNow: Instant = Instant.parse("2026-10-10T12:00:00Z")
val TestZone: TimeZone = TimeZone.UTC

private val FixedClock = object : Clock {
    override fun now(): Instant = TestNow
}

/** People of the chats of the tests. */
object TestPeers {
    val elena = UserSummary(UserId("user-elena"), "Проф. Елена Васкес", isVerified = true)
    val maria = UserSummary(UserId("user-maria"), "Мария Соколова", isVerified = true)
    val daniil = UserSummary(UserId("user-daniil"), "Даниил Крюков", isVerified = true)
}

/**
 * Chats that already have a history, which the real local source never has (the API has no chats, API-GAP G50, and
 * nobody answers there): three conversations with incoming messages and an unread counter, times relative to
 * [FixedClock]. Sending, opening and marking as read work as in the local source, without delay.
 */
class SeededChatsDataSource : ChatsDataSource {
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val messages = MutableStateFlow<Map<ConversationId, List<ChatMessage>>>(emptyMap())
    private var counter = 0

    init {
        val now = FixedClock.now()
        fun message(id: String, text: String, ago: Duration, outgoing: Boolean) =
            ChatMessage(MessageId(id), text, now - ago, outgoing)

        seed(
            "chat-elena", TestPeers.elena, unread = 2,
            listOf(
                message("m-e1", "Здравствуйте, Арджун! Спасибо за отклик на роль ML Engineer — профиль очень подходящий.", 80.minutes, false),
                message("m-e2", "Расскажите про опыт обучения моделей компьютерного зрения?", 79.minutes, false),
                message("m-e3", "Здравствуйте! Обучал MobileNetV3 для сегментации коридоров в Stanford AI Lab, разворачивал на Raspberry Pi.", 68.minutes, true),
                message("m-e4", "Отлично. Готовы созвониться завтра в 15:00?", 3.minutes, false),
            ),
        )
        seed(
            "chat-maria", TestPeers.maria, unread = 0,
            listOf(
                message("m-m1", "Мы приглашаем вас в проект «AI-навигация по кампусу»", 1.days + 40.minutes, true),
                message("m-m2", "Спасибо за приглашение! Посмотрю роль", 1.days, false),
            ),
        )
        seed(
            "chat-daniil", TestPeers.daniil, unread = 0,
            listOf(message("m-d1", "Привет! Ищу проект по робототехнике", 32.days, false)),
        )
    }

    private fun seed(id: String, peer: UserSummary, unread: Int, history: List<ChatMessage>) {
        val conversation = Conversation(ConversationId(id), peer, history.last(), unread)
        conversations.update { it + conversation }
        messages.update { it + (conversation.id to history) }
    }

    override fun observeConversations(): Flow<List<Conversation>> = conversations

    override fun observeMessages(id: ConversationId): Flow<List<ChatMessage>> = messages.map { it[id].orEmpty() }

    override suspend fun openConversationWith(peer: UserSummary): ConversationId {
        conversations.value.firstOrNull { it.peer.id == peer.id }?.let { return it.id }
        val created = Conversation(id = ConversationId("new-${counter++}"), peer = peer)
        conversations.update { it + created }
        return created.id
    }

    override suspend fun sendMessage(id: ConversationId, text: String): ChatMessage {
        if (conversations.value.none { it.id == id }) throw ConversationNotFoundException(id)
        val message = ChatMessage(MessageId("sent-${counter++}"), text, FixedClock.now(), isOutgoing = true)
        messages.update { it + (id to it[id].orEmpty() + message) }
        conversations.update { all -> all.map { if (it.id == id) it.copy(lastMessage = message) else it } }
        return message
    }

    override suspend fun markAsRead(id: ConversationId) {
        conversations.update { all -> all.map { if (it.id == id) it.copy(unreadCount = 0) else it } }
    }
}

/** Use cases over [SeededChatsDataSource] (a fixed clock, no latency). */
class TestGraph(dataSource: ChatsDataSource = SeededChatsDataSource()) {
    private val repository = ChatsRepositoryImpl(dataSource)

    val observeConversations = ObserveConversationsUseCase(repository)
    val observeConversation = ObserveConversationUseCase(repository)
    val observeMessages = ObserveMessagesUseCase(repository)
    val openConversation = OpenConversationUseCase(repository)
    val sendMessage = SendMessageUseCase(repository)
    val markRead = MarkConversationReadUseCase(repository)

    val elenaChat = ConversationId("chat-elena")
}

/** Data source whose sending fails like a dead network. */
class FailingSendDataSource(
    private val delegate: ChatsDataSource = SeededChatsDataSource(),
) : ChatsDataSource by delegate {
    override suspend fun sendMessage(id: ConversationId, text: String): ChatMessage =
        throw IllegalStateException("network is down")
}

fun <T> TestScope.keepCollecting(flow: Flow<T>) {
    @OptIn(ExperimentalCoroutinesApi::class)
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun installMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

@OptIn(ExperimentalCoroutinesApi::class)
fun uninstallMainDispatcher() = Dispatchers.resetMain()

fun (() -> Instant).asClock(): Clock = object : Clock {
    override fun now(): Instant = this@asClock()
}
