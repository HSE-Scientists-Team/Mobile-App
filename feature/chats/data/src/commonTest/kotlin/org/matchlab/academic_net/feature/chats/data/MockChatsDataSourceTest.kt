package org.matchlab.academic_net.feature.chats.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.chats.data.mock.MockChatsDataSource
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.chats.domain.ConversationNotFoundException
import org.matchlab.academic_net.utils.IdGenerator

/** The local chats (API-GAP G50): they start empty, there are no demo chats. */
class MockChatsDataSourceTest {
    private var now = Instant.parse("2026-10-10T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private var counter = 0
    private val ids = IdGenerator { "id-${counter++}" }

    private fun repository() = ChatsRepositoryImpl(MockChatsDataSource(clock, ids, latencyMillis = 0))

    private val anna = UserSummary(UserId("user-anna"), "Анна Иванова")
    private val boris = UserSummary(UserId("user-boris"), "Борис Петров")
    private val stranger = UserSummary(UserId("user-new"), "Новый Человек")

    @Test
    fun thereAreNoChatsAtTheStart() = runTest {
        assertEquals(emptyList(), repository().observeConversations().first())
    }

    @Test
    fun chatsAreSortedByLatestMessage() = runTest {
        val repository = repository()
        val withAnna = repository.openConversationWith(anna)
        val withBoris = repository.openConversationWith(boris)
        repository.sendMessage(withAnna, "первое")
        now += 5.minutes
        repository.sendMessage(withBoris, "второе")

        assertEquals(listOf(boris.id, anna.id), repository.observeConversations().first().map { it.peer.id })

        now += 5.minutes
        repository.sendMessage(withAnna, "третье")

        assertEquals(listOf(anna.id, boris.id), repository.observeConversations().first().map { it.peer.id })
    }

    @Test
    fun messagesOfAConversationAreOldestFirst() = runTest {
        val repository = repository()
        val id = repository.openConversationWith(anna)
        repository.sendMessage(id, "один")
        now += 1.minutes
        repository.sendMessage(id, "два")
        now += 1.minutes
        repository.sendMessage(id, "три")

        val messages = repository.observeMessages(id).first()

        assertEquals(listOf("один", "два", "три"), messages.map { it.text })
        assertEquals(messages.sortedBy { it.sentAt }, messages)
        assertTrue(messages.all { it.isOutgoing })
    }

    @Test
    fun openingExistingChatReturnsIt() = runTest {
        val repository = repository()
        val id = repository.openConversationWith(anna)
        repository.sendMessage(id, "Здравствуйте!")

        assertEquals(id, repository.openConversationWith(anna))
        assertEquals(1, repository.observeConversations().first().size)
    }

    @Test
    fun newChatStaysOutOfTheListUntilFirstMessage() = runTest {
        val repository = repository()

        val id = repository.openConversationWith(stranger)

        assertEquals(emptyList(), repository.observeConversations().first())
        assertEquals(stranger, repository.observeConversation(id).first()?.peer)
        assertEquals(emptyList(), repository.observeMessages(id).first())

        repository.sendMessage(id, "Здравствуйте!")

        val chats = repository.observeConversations().first()
        assertEquals(1, chats.size)
        assertEquals(id, chats.first { it.peer == stranger }.id)
    }

    @Test
    fun openingTheSamePersonTwiceGivesOneChat() = runTest {
        val repository = repository()

        val first = repository.openConversationWith(stranger)
        val second = repository.openConversationWith(stranger)

        assertEquals(first, second)
    }

    @Test
    fun sentMessageIsOutgoingTimestampedAndBecomesLast() = runTest {
        val repository = repository()
        val id = repository.openConversationWith(anna)
        repository.sendMessage(id, "Привет")

        val message = repository.sendMessage(id, "Да, готов")

        assertTrue(message.isOutgoing)
        assertEquals(now, message.sentAt)
        assertEquals(message, repository.observeConversation(id).first()?.lastMessage)
        assertEquals(message, repository.observeMessages(id).first().last())
        assertEquals(id, repository.observeConversations().first().first().id)
    }

    @Test
    fun sentMessagesGetDistinctIds() = runTest {
        val repository = repository()
        val id = repository.openConversationWith(anna)

        val first = repository.sendMessage(id, "1")
        val second = repository.sendMessage(id, "2")

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun sendingToUnknownChatFails() = runTest {
        assertFailsWith<ConversationNotFoundException> {
            repository().sendMessage(ConversationId("missing"), "Привет")
        }
    }

    @Test
    fun nobodyAnswersSoNothingIsUnread() = runTest {
        // API-GAP G50: incoming messages need the real server, so a local chat has only outgoing ones and no unread.
        val repository = repository()
        val id = repository.openConversationWith(anna)
        repository.sendMessage(id, "Есть кто?")

        repository.markAsRead(id)
        repository.markAsRead(ConversationId("missing"))

        assertEquals(0, repository.observeConversation(id).first()?.unreadCount)
        assertNull(repository.observeConversation(ConversationId("missing")).first())
    }
}
