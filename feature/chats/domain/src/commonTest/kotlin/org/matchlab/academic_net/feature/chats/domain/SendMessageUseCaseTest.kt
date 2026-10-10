package org.matchlab.academic_net.feature.chats.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.chats.domain.usecase.OpenConversationUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.SendMessageUseCase

private class RecordingRepository : ChatsRepository {
    val sent = mutableListOf<Pair<ConversationId, String>>()
    var failure: Throwable? = null

    override fun observeConversations(): Flow<List<Conversation>> = emptyFlow()

    override fun observeConversation(id: ConversationId): Flow<Conversation?> = emptyFlow()

    override fun observeMessages(id: ConversationId): Flow<List<ChatMessage>> = emptyFlow()

    override suspend fun openConversationWith(peer: UserSummary): ConversationId {
        failure?.let { throw it }
        return ConversationId("c-${peer.id.value}")
    }

    override suspend fun sendMessage(id: ConversationId, text: String): ChatMessage {
        failure?.let { throw it }
        sent += id to text
        return ChatMessage(MessageId("m${sent.size}"), text, Instant.fromEpochSeconds(0), isOutgoing = true)
    }

    override suspend fun markAsRead(id: ConversationId) = Unit
}

class SendMessageUseCaseTest {
    private val repository = RecordingRepository()
    private val send = SendMessageUseCase(repository)
    private val chat = ConversationId("c1")

    @Test
    fun textIsTrimmedBeforeSending() = runTest {
        val result = send(chat, "  Привет!  \n")

        assertEquals("Привет!", assertIs<SubmitResult.Success<ChatMessage>>(result).value.text)
        assertEquals(listOf(chat to "Привет!"), repository.sent)
    }

    @Test
    fun blankTextIsRejectedAndNotSent() = runTest {
        val result = send(chat, "   \n ")

        assertEquals(setOf(MessageError.Empty), assertIs<SubmitResult.Invalid<MessageError>>(result).errors)
        assertEquals(emptyList(), repository.sent)
    }

    @Test
    fun tooLongTextIsRejected() = runTest {
        val result = send(chat, "а".repeat(ChatLimits.MaxMessageLength + 1))

        assertEquals(setOf(MessageError.TooLong), assertIs<SubmitResult.Invalid<MessageError>>(result).errors)
    }

    @Test
    fun textOfMaxLengthIsSent() = runTest {
        val result = send(chat, "а".repeat(ChatLimits.MaxMessageLength))

        assertIs<SubmitResult.Success<ChatMessage>>(result)
    }

    @Test
    fun repositoryErrorBecomesFailure() = runTest {
        val error = IllegalStateException("offline")
        repository.failure = error

        assertSame(error, assertIs<SubmitResult.Failure>(send(chat, "Привет")).cause)
    }

    @Test
    fun openConversationWrapsErrors() = runTest {
        val open = OpenConversationUseCase(repository)
        val peer = UserSummary(org.matchlab.academic_net.common.model.UserId("u1"), "Name")

        assertEquals(ConversationId("c-u1"), open(peer).getOrNull())

        val error = IllegalStateException("offline")
        repository.failure = error
        assertSame(error, open(peer).exceptionOrNull())
    }
}
