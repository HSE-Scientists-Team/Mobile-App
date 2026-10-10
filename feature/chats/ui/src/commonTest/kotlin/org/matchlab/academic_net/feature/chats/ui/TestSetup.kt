package org.matchlab.academic_net.feature.chats.ui

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.matchlab.academic_net.feature.chats.data.ChatsDataSource
import org.matchlab.academic_net.feature.chats.data.ChatsRepositoryImpl
import org.matchlab.academic_net.feature.chats.data.mock.MockChatsDataSource
import org.matchlab.academic_net.feature.chats.domain.ChatMessage
import org.matchlab.academic_net.feature.chats.domain.ConversationId
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

/** Use cases over the mock data source with no latency and a fixed clock. */
class TestGraph(dataSource: ChatsDataSource = MockChatsDataSource(FixedClock, latencyMillis = 0)) {
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
    private val delegate: ChatsDataSource = MockChatsDataSource(FixedClock, latencyMillis = 0),
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
