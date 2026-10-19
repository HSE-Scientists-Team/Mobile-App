package org.matchlab.academic_net.feature.chats.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppBottomSheet
import org.matchlab.academic_net.components.AppIconButton
import org.matchlab.academic_net.components.AppIcon
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.PersonName
import org.matchlab.academic_net.components.ReportListItem
import org.matchlab.academic_net.components.SecurityNote
import org.matchlab.academic_net.components.SheetHeader
import org.matchlab.academic_net.components.interaction.onceClickable
import org.matchlab.academic_net.components.person.nameOrPlaceholder
import org.matchlab.academic_net.feature.chats.domain.ChatLimits
import org.matchlab.academic_net.feature.chats.domain.ConversationId
import org.matchlab.academic_net.feature.chats.ui.ChatBubble
import org.matchlab.academic_net.feature.chats.ui.ChatBubbleUiModel
import org.matchlab.academic_net.feature.chats.ui.ChatComposer
import org.matchlab.academic_net.feature.chats.ui.ChatComposerUiModel
import org.matchlab.academic_net.feature.chats.ui.ChatsUiDependencies
import org.matchlab.academic_net.feature.chats.ui.generated.resources.Res
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chat_empty_text
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chat_empty_title
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chat_message_too_long
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chat_more
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chat_not_found
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chat_security_note
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chat_send_error
import org.matchlab.academic_net.feature.chats.ui.generated.resources.chats_back

/**
 * "13 Чат с коллаборатором". The "⋮" of the app bar opens the sheet 13b with "Пожаловаться" (blocking is a later sprint
 * and is not offered); there is no "⋮" for a peer who is not known or has no id. The attach button belongs to sending
 * files and is not shown.
 *
 * @param onPeerClick the avatar and the name in the app bar, which lead to the profile of the peer
 * @param onReportPeer "Пожаловаться" of 13b: the complaint about the peer (the host opens the form)
 */
@Composable
fun ChatRoute(
    dependencies: ChatsUiDependencies,
    conversationId: ConversationId,
    onNavigateBack: () -> Unit,
    onPeerClick: (UserId) -> Unit,
    onReportPeer: (UserId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = conversationId.value) {
        ChatViewModel(
            conversationId = conversationId,
            observeConversation = dependencies.observeConversation,
            observeMessages = dependencies.observeMessages,
            sendMessage = dependencies.sendMessage,
            markConversationRead = dependencies.markConversationRead,
            timeZone = dependencies.timeZone,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onPeerClick = onPeerClick,
        onReportPeer = onReportPeer,
        onDraftChange = viewModel::onDraftChange,
        onSendClick = viewModel::onSendClick,
        modifier = modifier,
    )
}

@Composable
internal fun ChatScreen(
    state: ChatUiState,
    onNavigateBack: () -> Unit,
    onPeerClick: (UserId) -> Unit,
    onReportPeer: (UserId) -> Unit,
    onDraftChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow)
            .imePadding(),
    ) {
        ChatTopBar(state.peer, onNavigateBack, onPeerClick, onMoreClick = { menuOpen = true })
        val peer = state.peer
        when {
            peer != null -> {
                // What the app guarantees about the conversation: only its two members read it.
                SecurityNote(
                    text = stringResource(Res.string.chat_security_note),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Messages(state.messages, Modifier.weight(1f))
                val problem = when (state.sendProblem) {
                    SendProblem.TooLong ->
                        stringResource(Res.string.chat_message_too_long, ChatLimits.MaxMessageLength)
                    SendProblem.Failed -> stringResource(Res.string.chat_send_error)
                    null -> null
                }
                Column(Modifier.background(colors.surface).navigationBarsPadding()) {
                    if (problem != null) {
                        Text(
                            text = problem,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                        )
                    }
                    ChatComposer(
                        model = ChatComposerUiModel(state.draft),
                        onMessageChange = onDraftChange,
                        onSendClick = onSendClick,
                    )
                }
            }
            state.isLoading -> LoadingState()
            else -> MessageState(title = stringResource(Res.string.chat_not_found))
        }
    }
    val peerToReport = state.peer?.takeIf { menuOpen && it.canBeReported() }
    if (peerToReport != null) {
        PeerMenuSheet(
            peerName = peerToReport.name,
            onReport = {
                menuOpen = false
                onReportPeer(peerToReport.userId)
            },
            onDismiss = { menuOpen = false },
        )
    }
}

/** The peer can be reported: they are known and have an id (a complaint names them by it, API-GAP G55). */
private fun ChatPeer.canBeReported(): Boolean = userId.value.isNotBlank()

/**
 * 13b: the sheet of the "⋮" of the chat. It is titled with the name of the peer and has one row, "Пожаловаться"; the
 * row "Заблокировать" of the design is a later sprint.
 */
@Composable
private fun PeerMenuSheet(peerName: String, onReport: () -> Unit, onDismiss: () -> Unit) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(title = nameOrPlaceholder(peerName))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
            ReportListItem(onClick = onReport)
        }
    }
}

@Composable
private fun Messages(messages: List<MessageItem>, modifier: Modifier = Modifier) {
    if (messages.isEmpty()) {
        Column(
            modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(Res.string.chat_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.chat_empty_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val listState = rememberLazyListState()
    // The newest message is at the bottom and is shown when the chat opens and when one arrives.
    LaunchedEffect(messages.size) {
        listState.scrollToItem(messages.lastIndex)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(messages, key = { it.id.value }) { message ->
            ChatBubble(ChatBubbleUiModel(message.text, message.time, message.isOutgoing))
        }
    }
}

/**
 * "app-bar" of the chat: back, the peer's avatar and name; the last two are one tap target that opens the peer's
 * profile.
 */
@Composable
private fun ChatTopBar(
    peer: ChatPeer?,
    onNavigateBack: () -> Unit,
    onPeerClick: (UserId) -> Unit,
    onMoreClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .onceClickable(role = Role.Button, onClick = onNavigateBack),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(
                icon = AppIcons.ArrowLeft,
                contentDescription = stringResource(Res.string.chats_back),
                tint = colors.onSurface,
            )
        }
        if (peer != null) {
            Row(
                Modifier
                    .weight(1f)
                    .onceClickable(role = Role.Button) { onPeerClick(peer.userId) },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(AppAvatars.forKey(peer.avatarKey), contentDescription = null, size = 40.dp)
                PersonName(
                    name = peer.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            // The menu is for a peer who can be reported (13b).
            if (peer.canBeReported()) {
                AppIconButton(
                    icon = AppIcons.MoreVertical,
                    contentDescription = stringResource(Res.string.chat_more),
                    onClick = onMoreClick,
                )
            }
        }
    }
}
