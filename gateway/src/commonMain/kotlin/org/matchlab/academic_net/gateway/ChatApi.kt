package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.dto.ChatConversationDto
import org.matchlab.academic_net.gateway.dto.ChatConversationListDto
import org.matchlab.academic_net.gateway.dto.ChatMessageDto
import org.matchlab.academic_net.gateway.dto.ChatMessageListDto

/**
 * The one-to-one text chat (`/api/v1/chat/...`, OpenAPI v2.6.0). The server has no push: new messages are read by
 * asking again with `after_seq`. Every call throws `RemoteFailure` when it fails and lets a `CancellationException`
 * through, like [AccountApi]. Ids go into paths escaped.
 */
interface ChatApi {
    /** `POST /chat/conversations` `{"user_id": …}` → 200 (the pair has a conversation already) or 201 (a new one). */
    suspend fun open(userId: String): ChatConversationDto

    /** `GET /chat/conversations`: the viewer's conversations, the latest activity first; [cursor] is the `next_cursor` of the previous page. */
    suspend fun conversations(limit: Int, cursor: String?): ChatConversationListDto

    /**
     * `GET /chat/conversations/{id}/messages`: without [beforeSeq] and [afterSeq] the latest [limit] messages; with
     * [beforeSeq] the older ones, with [afterSeq] the next newer ones (the two cannot be given together). Oldest first.
     */
    suspend fun messages(conversationId: String, limit: Int, beforeSeq: Long? = null, afterSeq: Long? = null): ChatMessageListDto

    /**
     * `POST /chat/conversations/{id}/messages` → 201, or 200 for a repeat of the same [clientMessageId] with the same
     * [body] (409 if the text differs).
     */
    suspend fun send(conversationId: String, clientMessageId: String, body: String): ChatMessageDto

    /** `PUT /chat/conversations/{id}/read` `{"last_read_seq": …}` → 204; the mark only grows. */
    suspend fun markRead(conversationId: String, lastReadSeq: Long)
}
