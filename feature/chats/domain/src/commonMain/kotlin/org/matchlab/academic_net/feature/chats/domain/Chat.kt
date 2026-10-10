package org.matchlab.academic_net.feature.chats.domain

import kotlin.jvm.JvmInline
import kotlin.time.Instant
import org.matchlab.academic_net.common.model.UserSummary

@JvmInline
value class ConversationId(val value: String)

@JvmInline
value class MessageId(val value: String)

data class ChatMessage(
    val id: MessageId,
    val text: String,
    val sentAt: Instant,
    /** Written by the current user. */
    val isOutgoing: Boolean,
)

/** A one-to-one chat. [lastMessage] is `null` until somebody writes. */
data class Conversation(
    val id: ConversationId,
    val peer: UserSummary,
    val lastMessage: ChatMessage? = null,
    val unreadCount: Int = 0,
)

object ChatLimits {
    const val MaxMessageLength = 2000
}

enum class MessageError {
    Empty,
    TooLong,
}
