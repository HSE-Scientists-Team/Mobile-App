package org.matchlab.academic_net.components

import androidx.compose.foundation.layout.Arrangement
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
 * @property type the project type, the first chip, e.g. "Научный"; `null` leaves the chip out (API-GAP G40: the
 *  server gives no type)
 * @property footer the line with the team size and, when the months are known, the dates (see `projectCardFooter`)
 * @property tags the chips after the type, e.g. the status and the sphere; the card draws no row of chips when there
 *  is neither a [type] nor a tag
 */
@Immutable
data class ProjectCardUiModel(
    val title: String,
    /** Author line, e.g. "Проф. Елена Васкес · Stanford". */
    val author: String,
    val authorAvatar: DrawableResource,
    val type: String?,
    val footer: String,
    val tags: List<String> = emptyList(),
    val isAuthorVerified: Boolean = false,
    /** Accept, decline and chat buttons of an invitation to the project. */
    val showInvitationActions: Boolean = false,
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
        Text(model.title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(model.authorAvatar, contentDescription = null, size = 24.dp)
            Text(
                text = model.author,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (model.isAuthorVerified) VerifiedBadge()
        }
        if (model.type != null || model.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (model.type != null) AppFilterChip(model.type, selected = false, onClick = null)
                model.tags.forEach { AppTagChip(it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIcons.Users, contentDescription = null, size = 16.dp, tint = colors.onSurfaceVariant)
            Text(model.footer, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (model.showInvitationActions) {
            AcceptDeclineActions(onAcceptClick, onDeclineClick, onChatClick)
        }
    }
}
