package org.matchlab.academic_net.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource

/**
 * What [ProjectCard] shows.
 *
 * @property author the author line, e.g. "Проф. Елена Васкес · Stanford"; the author has no verification mark
 * @property type the project type, the first chip, e.g. "Научный"; `null` leaves the chip out (the type is unknown,
 *  see `docs/api-values.md`)
 * @property footer the line with the team size and, when the months are known, the dates (see `projectCardFooter`)
 * @property statusChip the status of the project as a "Chip/Status" without an icon, right after the type ("В работе",
 *  "Опубликован"); `null` leaves it out (the status is unknown, or it is among the [tags])
 * @property tags the chips after the type and the status, e.g. the sphere; the card draws no row of chips when there
 *  is neither a [type], a [statusChip] nor a tag
 * @property showInvitationActions accept, decline and chat buttons of an invitation to the project
 * @property badgeCount the number of new applications of the initiator's project (06e): a "Badge/Count" at the end of
 *  the title row while it is more than 0 (the design's `showBadge`)
 * @property invitationRole the role of an invitation, "Роль: Data Analyst" (15l, the design's `showInvite`), drawn
 *  between the last line and the buttons; `null` for none
 * @property invitationMessage the text the initiator wrote with the invitation, under [invitationRole]; `null` for none
 * @property invitationDeciding the decision on the invitation is on its way: an arc stands in place of accept and decline
 */
@Immutable
data class ProjectCardUiModel(
    val title: String,
    val author: String,
    val authorAvatar: DrawableResource,
    val type: String?,
    val footer: String,
    val tags: List<String> = emptyList(),
    val statusChip: StatusChipUiModel? = null,
    val showInvitationActions: Boolean = false,
    val badgeCount: Int = 0,
    val invitationRole: String? = null,
    val invitationMessage: String? = null,
    val invitationDeciding: Boolean = false,
)

/** "Card/Project": project in the feed, search, the user's projects and invitations. */
@Composable
fun ProjectCard(
    model: ProjectCardUiModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onAcceptClick: () -> Unit = {},
    onDeclineClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    AppCard(modifier, onClick = onClick, spacing = 12.dp) {
        if (model.badgeCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                AppBadgeCount(model.badgeCount)
            }
        } else {
            Text(model.title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(model.authorAvatar, contentDescription = null, size = 24.dp)
            Text(
                text = model.author,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (model.type != null || model.statusChip != null || model.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (model.type != null) AppFilterChip(model.type, selected = false, onClick = null)
                model.statusChip?.let { AppStatusChip(it.text, it.tone, showIcon = false) }
                model.tags.forEach { AppTagChip(it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIcons.Users, contentDescription = null, size = 16.dp, tint = colors.onSurfaceVariant)
            Text(model.footer, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (model.invitationRole != null || model.invitationMessage != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (model.invitationRole != null) {
                    Text(model.invitationRole, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                }
                if (model.invitationMessage != null) {
                    Text(
                        model.invitationMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        if (model.showInvitationActions) {
            AcceptDeclineActions(onAcceptClick, onDeclineClick, onChatClick, loading = model.invitationDeciding)
        }
    }
}
