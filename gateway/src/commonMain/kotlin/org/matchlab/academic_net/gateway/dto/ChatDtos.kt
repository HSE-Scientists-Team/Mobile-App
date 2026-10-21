package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shapes of the chat of the backend's OpenAPI v2.6.0 (schemas ChatConversation, ChatMessage, ChatOpenInput,
// ChatSendInput). The int64 fields (`seq`, times, counters) come as decimal strings (ProtoJSON), like the other int64s.

/** `ChatMessage`: [seq] numbers the messages of one conversation from 1, without gaps. */
@Serializable
data class ChatMessageDto(
    val id: String = "",
    @SerialName("conversation_id") val conversationId: String = "",
    @Serializable(with = FlexibleLongSerializer::class) val seq: Long = 0,
    @SerialName("sender_user_id") val senderUserId: String = "",
    @SerialName("client_message_id") val clientMessageId: String = "",
    val body: String = "",
    @SerialName("created_at_unix") @Serializable(with = FlexibleLongSerializer::class) val createdAtUnix: Long = 0,
)

/**
 * `ChatConversation`. [lastMessage] and [lastMessageAtUnix] are missing in an empty conversation; [unreadCount] counts
 * the incoming messages after [lastReadSeq].
 */
@Serializable
data class ChatConversationDto(
    val id: String = "",
    @SerialName("peer_user_id") val peerUserId: String = "",
    @SerialName("last_message_seq") @Serializable(with = FlexibleLongSerializer::class) val lastMessageSeq: Long = 0,
    @SerialName("last_read_seq") @Serializable(with = FlexibleLongSerializer::class) val lastReadSeq: Long = 0,
    @SerialName("peer_last_read_seq") @Serializable(with = FlexibleLongSerializer::class) val peerLastReadSeq: Long = 0,
    @SerialName("unread_count") @Serializable(with = FlexibleLongSerializer::class) val unreadCount: Long = 0,
    @SerialName("created_at_unix") @Serializable(with = FlexibleLongSerializer::class) val createdAtUnix: Long = 0,
    @SerialName("last_message_at_unix") @Serializable(with = FlexibleLongSerializer::class) val lastMessageAtUnix: Long = 0,
    @SerialName("last_message") val lastMessage: ChatMessageDto? = null,
)

/** One page of conversations; [nextCursor] is empty at the end of the list. */
@Serializable
data class ChatConversationListDto(
    val items: List<ChatConversationDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String = "",
)

/** One page of messages, oldest first; [hasMore] says that there are older (or, with `after_seq`, newer) ones. */
@Serializable
data class ChatMessageListDto(
    val items: List<ChatMessageDto> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)

/** `ChatOpenInput`. */
@Serializable
data class ChatOpenInputDto(@SerialName("user_id") val userId: String)

/** `ChatSendInput`: [clientMessageId] is a new UUID per message and stays the same on a repeat of the same send. */
@Serializable
data class ChatSendInputDto(
    @SerialName("client_message_id") val clientMessageId: String,
    val body: String,
)

/** The body of `PUT /chat/conversations/{id}/read`. */
@Serializable
data class ChatReadInputDto(@SerialName("last_read_seq") val lastReadSeq: Long)
