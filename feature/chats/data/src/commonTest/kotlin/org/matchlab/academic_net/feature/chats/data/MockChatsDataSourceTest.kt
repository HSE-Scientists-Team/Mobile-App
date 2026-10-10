package org.matchlab.academic_net.feature.chats.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.chats.data.mock.MockChatPeers
import org.matchlab.academic_net.feature.chats.data.mock.MockChatsDataSource
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.chats.domain.ConversationNotFoundException
import org.matchlab.academic_net.utils.IdGenerator

class MockChatsDataSourceTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private var counter = 0
    private val ids = IdGenerator { "id-${counter++}" }

    private fun repository(seed: Boolean = true) =
        ChatsRepositoryImpl(MockChatsDataSource(clock, ids, latencyMillis = 0, seed = seed))

    private val stranger = UserSummary(UserId("user-new"), "Новый Человек")

    @Test
    fun seededChatsAreSortedByLatestMessage() = runTest {
        val chats = repository().observeConversations().first()

        assertEquals(
            listOf(MockChatPeers.elena.id, MockChatPeers.maria.id, MockChatPeers.daniil.id),
            chats.map { it.peer.id },
        )
        assertEquals(2, chats.first().unreadCount)
        assertEquals("Отлично. Готовы созвониться завтра в 15:00?", chats.first().lastMessage?.text)
    }

    @Test
    fun messagesOfAConversationAreOldestFirst() = runTest {
        val repository = repository()
        val chat = repository.observeConversations().first().first()

        val messages = repository.observeMessages(chat.id).first()

        assertEquals(4, messages.size)
        assertEquals(messages.sortedBy { it.sentAt }, messages)
        assertEquals(listOf(false, false, true, false), messages.map { it.isOutgoing })
    }

    @Test
    fun openingExistingChatReturnsIt() = runTest {
        val repository = repository()
        val existing = repository.observeConversations().first().first { it.peer.id == MockChatPeers.maria.id }

        assertEquals(existing.id, repository.openConversationWith(MockChatPeers.maria))
        assertEquals(3, repository.observeConversations().first().size)
    }

    @Test
    fun newChatStaysOutOfTheListUntilFirstMessage() = runTest {
        val repository = repository()

        val id = repository.openConversationWith(stranger)

        assertEquals(3, repository.observeConversations().first().size)
        assertEquals(stranger, repository.observeConversation(id).first()?.peer)
        assertEquals(emptyList(), repository.observeMessages(id).first())

        repository.sendMessage(id, "Здравствуйте!")

        val chats = repository.observeConversations().first()
        assertEquals(4, chats.size)
        assertEquals(id, chats.first { it.peer == stranger }.id)
    }

    @Test
    fun openingTheSamePersonTwiceGivesOneChat() = runTest {
        val repository = repository(seed = false)

        val first = repository.openConversationWith(stranger)
        val second = repository.openConversationWith(stranger)

        assertEquals(first, second)
    }

    @Test
    fun sentMessageIsOutgoingTimestampedAndBecomesLast() = runTest {
        val repository = repository()
        val chat = repository.observeConversations().first().first()

        val message = repository.sendMessage(chat.id, "Да, готов")

        assertTrue(message.isOutgoing)
        assertEquals(now, message.sentAt)
        assertEquals(message, repository.observeConversation(chat.id).first()?.lastMessage)
        assertEquals(message, repository.observeMessages(chat.id).first().last())
        assertEquals(chat.id, repository.observeConversations().first().first().id)
    }

    @Test
    fun sentMessagesGetDistinctIds() = runTest {
        val repository = repository()
        val chat = repository.observeConversations().first().first()

        val first = repository.sendMessage(chat.id, "1")
        val second = repository.sendMessage(chat.id, "2")

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun sendingToUnknownChatFails() = runTest {
        assertFailsWith<ConversationNotFoundException> {
            repository().sendMessage(ConversationId("missing"), "Привет")
        }
    }

    @Test
    fun markAsReadClearsUnreadCounter() = runTest {
        val repository = repository()
        val chat = repository.observeConversations().first().first()
        assertEquals(2, chat.unreadCount)

        repository.markAsRead(chat.id)

        assertEquals(0, repository.observeConversation(chat.id).first()?.unreadCount)
    }
}
