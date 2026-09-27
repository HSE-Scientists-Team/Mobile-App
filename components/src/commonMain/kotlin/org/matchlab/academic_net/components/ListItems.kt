package org.matchlab.academic_net.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource

/** "List item/Action": action row in a list or a sheet. */
@Composable
fun ActionListItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: DrawableResource = AppIcons.Check,
) {
    OutlinedRow(modifier.clickable(onClick = onClick), MaterialTheme.shapes.large) {
        AppIcon(icon, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        TitleWithSubtitle(title, subtitle)
        Chevron()
    }
}

/** "List item/Setting": setting row that opens another screen. */
@Composable
fun SettingListItem(
    title: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    OutlinedRow(modifier.clickable(onClick = onClick), MaterialTheme.shapes.large) {
        AppIcon(icon, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        TitleWithSubtitle(title, subtitle)
        Chevron()
    }
}

/** "List item/Toggle": row with a switch; the whole row toggles it. */
@Composable
fun ToggleListItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    OutlinedRow(
        modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        MaterialTheme.shapes.large,
    ) {
        TitleWithSubtitle(title, subtitle)
        AppSwitch(checked = checked, onCheckedChange = null)
    }
}

/** "List item/Radio": one option of a single-choice list; the whole row selects it. */
@Composable
fun RadioListItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedRow(
        modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        MaterialTheme.shapes.medium,
        verticalPadding = 12.dp,
    ) {
        AppRadioButton(selected = selected, onClick = null)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "List item/Person": avatar, name with an optional verified mark and a subtitle. */
@Composable
fun PersonListItem(
    name: String,
    subtitle: String,
    avatar: Painter,
    modifier: Modifier = Modifier,
    verified: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    PlainRow(modifier.clickableIf(onClick)) {
        Avatar(avatar, contentDescription = null, size = 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (verified) {
                    AppIcon(AppIcons.ShieldCheck, contentDescription = null, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
                }
            }
            SecondaryText(subtitle, MaterialTheme.typography.bodySmall)
        }
        if (onClick != null) Chevron()
    }
}

/** "List item/Chat": chat in the chat list, with an unread counter. */
@Composable
fun ChatListItem(
    name: String,
    lastMessage: String,
    time: String,
    avatar: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unreadCount: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    PlainRow(modifier.clickable(onClick = onClick)) {
        Avatar(avatar, contentDescription = null, size = 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(time, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            SecondaryText(lastMessage, MaterialTheme.typography.bodyMedium)
        }
        if (unreadCount > 0) AppBadgeCount(unreadCount)
    }
}

/** "List item/Notification": notification with an icon and when it arrived. */
@Composable
fun NotificationListItem(
    title: String,
    time: String,
    modifier: Modifier = Modifier,
    icon: DrawableResource = AppIcons.Message,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    PlainRow(modifier.clip(MaterialTheme.shapes.medium).clickableIf(onClick)) {
        Box(Modifier.background(colors.surface, CircleShape).padding(8.dp)) {
            AppIcon(icon, contentDescription = null, size = 20.dp, tint = colors.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(time, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

// Rows with an outline: as in Figma the 1dp outline adds to the size, so the padding is 1dp larger.
@Composable
private fun OutlinedRow(
    modifier: Modifier,
    shape: Shape,
    verticalPadding: Dp = 14.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant, shape)
            .then(modifier)
            .padding(horizontal = 17.dp, vertical = verticalPadding + 1.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PlainRow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RectangleShape)
            .then(modifier)
            .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowScope.TitleWithSubtitle(title: String, subtitle: String?) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SecondaryText(text: String, style: TextStyle, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Chevron() {
    AppIcon(AppIcons.ChevronRight, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.outline)
}

private fun Modifier.clickableIf(onClick: (() -> Unit)?): Modifier =
    if (onClick != null) clickable(onClick = onClick) else this
