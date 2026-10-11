package org.matchlab.academic_net.feature.chats.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.format.chatListTimeText
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.chats.ui.ChatListItem
import org.matchlab.academic_net.feature.chats.ui.ChatListItemUiModel
import org.matchlab.academic_net.feature.chats.ui.ChatsUiDependencies
import org.matchlab.academic_net.feature.chats.ui.generated.resources.Res
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chats_empty_text
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chats_empty_title
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chats_load_error
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chats_title

/** "12 Чаты". The search action of the design is a story of a later sprint. */
@Composable
fun ChatsRoute(
    dependencies: ChatsUiDependencies,
    onChatClick: (ConversationId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel {
        ChatsViewModel(dependencies.observeConversations, dependencies.clock, dependencies.timeZone)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatsScreen(state, onChatClick, modifier)
}

@Composable
internal fun ChatsScreen(
    state: ChatsUiState,
    onChatClick: (ConversationId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(colors.surface)) {
        AppTopBar(title = stringResource(Res.string.chats_title))
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            state.hasError -> CenteredMessage(stringResource(Res.string.chats_load_error))
            state.chats.isEmpty() -> CenteredMessage(
                title = stringResource(Res.string.chats_empty_title),
                text = stringResource(Res.string.chats_empty_text),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.chats, key = { it.id.value }) { chat ->
                    ChatListItem(
                        model = ChatListItemUiModel(
                            name = chat.name,
                            lastMessage = chat.lastMessage,
                            time = chatListTimeText(chat.time),
                            avatar = AppAvatars.forKey(chat.avatarKey),
                            unreadCount = chat.unreadCount,
                        ),
                        onClick = { onChatClick(chat.id) },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(title: String, text: String? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
