package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.chats.data.ChatsDataSource
import org.matchlab.academic_net.feature.chats.data.ChatsRepositoryImpl
import org.matchlab.academic_net.feature.chats.domain.ChatsRepository
import org.matchlab.academic_net.feature.chats.domain.usecase.MarkConversationReadUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveConversationUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveConversationsUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.ObserveMessagesUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.OpenConversationUseCase
import org.matchlab.academic_net.feature.chats.domain.usecase.SendMessageUseCase
import org.matchlab.academic_net.feature.chats.ui.ChatsUiDependencies

/** The chats feature: its repository, use cases and what its screens receive. */
internal interface ChatsModule {
    @Provides
    @SessionScope
    fun chatsRepository(source: ChatsDataSource): ChatsRepository = ChatsRepositoryImpl(source)

    @Provides
    fun chatsUiDependencies(repository: ChatsRepository): ChatsUiDependencies = ChatsUiDependencies(
        observeConversations = ObserveConversationsUseCase(repository),
        observeConversation = ObserveConversationUseCase(repository),
        observeMessages = ObserveMessagesUseCase(repository),
        openConversation = OpenConversationUseCase(repository),
        sendMessage = SendMessageUseCase(repository),
        markConversationRead = MarkConversationReadUseCase(repository),
    )
}
