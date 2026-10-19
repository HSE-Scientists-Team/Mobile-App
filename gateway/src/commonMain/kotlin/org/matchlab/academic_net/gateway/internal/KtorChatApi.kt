package org.matchlab.academic_net.gateway.internal

import org.matchlab.academic_net.gateway.ChatApi
import org.matchlab.academic_net.gateway.dto.ChatConversationDto
import org.matchlab.academic_net.gateway.dto.ChatConversationListDto
import org.matchlab.academic_net.gateway.dto.ChatMessageDto
import org.matchlab.academic_net.gateway.dto.ChatMessageListDto
import org.matchlab.academic_net.gateway.dto.ChatOpenInputDto
import org.matchlab.academic_net.gateway.dto.ChatReadInputDto
import org.matchlab.academic_net.gateway.dto.ChatSendInputDto

/** [ChatApi] over the client of a session. */
internal class KtorChatApi(private val requests: ApiRequests) : ChatApi {
    override suspend fun open(userId: String): ChatConversationDto =
        requests.post("chat/conversations", ChatOpenInputDto(userId))

    override suspend fun conversations(limit: Int, cursor: String?): ChatConversationListDto =
        requests.get("chat/conversations", query = mapOf("limit" to limit.toString(), "cursor" to cursor?.takeIf { it.isNotEmpty() }))

    override suspend fun messages(
        conversationId: String,
        limit: Int,
        beforeSeq: Long?,
        afterSeq: Long?,
    ): ChatMessageListDto {
        require(beforeSeq == null || afterSeq == null) { "before_seq and after_seq cannot be given together" }
        return requests.get(
            "chat/conversations/${pathSegment(conversationId)}/messages",
            query = mapOf(
                "limit" to limit.toString(),
                "before_seq" to beforeSeq?.toString(),
                "after_seq" to afterSeq?.toString(),
            ),
        )
    }

    override suspend fun send(conversationId: String, clientMessageId: String, body: String): ChatMessageDto =
        requests.post("chat/conversations/${pathSegment(conversationId)}/messages", ChatSendInputDto(clientMessageId, body))

    override suspend fun markRead(conversationId: String, lastReadSeq: Long) {
        requests.putWithoutAnswer("chat/conversations/${pathSegment(conversationId)}/read", ChatReadInputDto(lastReadSeq))
    }
}
