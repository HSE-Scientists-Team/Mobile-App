package org.matchlab.academic_net.feature.chats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.matchlab.academic_net.components.AppBadgeCount
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.interaction.onceClickable
import org.matchlab.academic_net.components.person.nameOrPlaceholder

@Immutable
data class ChatListItemUiModel(
    val name: String,
    val lastMessage: String,
    val time: String,
    val avatar: DrawableResource,
    val unreadCount: Int = 0,
)

/** "List item/Chat": chat in the chat list, with an unread counter. A burst of taps runs [onClick] once. */
@Composable
fun ChatListItem(
    model: ChatListItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .onceClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(model.avatar, contentDescription = null, size = 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = nameOrPlaceholder(model.name),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(model.time, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Text(
                text = model.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (model.unreadCount > 0) AppBadgeCount(model.unreadCount)
    }
}
