package org.matchlab.academic_net.feature.chats.ui

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import org.matchlab.academic_net.feature.chats.domain.usecase.MarkConversationReadUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveConversationUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveConversationsUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveMessagesUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.OpenConversationUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.SendMessageUseCase

/**
 * What the screens of the chats feature need from the outside; the app creates it once and hands
 * it to every `…Route`. [openConversation] is for other features' screens that start a chat.
 */
class ChatsUiDependencies(
    val observeConversations: ObserveConversationsUseCase,
    val observeConversation: ObserveConversationUseCase,
    val observeMessages: ObserveMessagesUseCase,
    val openConversation: OpenConversationUseCase,
    val sendMessage: SendMessageUseCase,
    val markConversationRead: MarkConversationReadUseCase,
    val clock: Clock = Clock.System,
    val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
)
