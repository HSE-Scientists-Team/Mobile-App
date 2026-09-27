package org.matchlab.academic_net.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource

/** "Card/Project": project in the feed or search results. Actions show only when passed. */
@Composable
fun ProjectCard(
    title: String,
    author: String,
    authorAvatar: Painter,
    type: String,
    tags: List<String>,
    footer: String,
    modifier: Modifier = Modifier,
    authorVerified: Boolean = false,
    onClick: (() -> Unit)? = null,
    onAccept: (() -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
    onMessage: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    CardSurface(modifier, spacing = 12.dp, onClick = onClick) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(authorAvatar, contentDescription = null, size = 24.dp)
            Text(
                text = author,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (authorVerified) VerifiedMark()
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppFilterChip(type, selected = false, onClick = null)
            tags.forEach { AppTagChip(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIcons.Users, contentDescription = null, size = 16.dp, tint = colors.onSurfaceVariant)
            Text(footer, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (onAccept != null || onDecline != null || onMessage != null) {
            CardDecisionActions(onAccept, onDecline, onMessage)
        }
    }
}

/** "Card/Candidate": applicant, search result or invitee. Actions show only when passed. */
@Composable
fun CandidateCard(
    name: String,
    subtitle: String,
    avatar: Painter,
    tags: List<String>,
    modifier: Modifier = Modifier,
    note: String? = null,
    verified: Boolean = false,
    onClick: (() -> Unit)? = null,
    onAccept: (() -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
    onMessage: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    CardSurface(modifier, spacing = 12.dp, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(avatar, contentDescription = null, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NameRow(name, verified, MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.labelSmall, color = colors.primary)
                }
            }
        }
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { AppTagChip(it) }
            }
        }
        if (onAccept != null || onDecline != null || onMessage != null) {
            CardDecisionActions(onAccept, onDecline, onMessage)
        }
    }
}

/** "Card/Application": my application, an invitation or an archived record. */
@Composable
fun ApplicationCard(
    title: String,
    subtitle: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    onMessage: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    CardSurface(modifier, spacing = 8.dp, onClick = onClick) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppButton(actionText, onAction, Modifier.weight(1f), variant = AppButtonVariant.Outlined)
            if (onMessage != null) MessageIconButton(onMessage)
        }
    }
}

/** "Card/Review": review about a user. */
@Composable
fun ReviewCard(
    authorName: String,
    authorRole: String,
    authorAvatar: Painter,
    title: String,
    rating: Int,
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    CardSurface(modifier, spacing = 8.dp, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(authorAvatar, contentDescription = null, size = 32.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(authorName, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(authorRole, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Text("$rating★", style = MaterialTheme.typography.titleMedium, color = colors.primary)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** "Card/Info": icon with a value such as dates, place or format. */
@Composable
fun InfoCard(text: String, icon: DrawableResource, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    CardSurface(modifier, spacing = 0.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, contentDescription = null, size = 20.dp, tint = colors.onSurfaceVariant)
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        }
    }
}

/** "Card/Upload": dashed drop zone for attaching a file. */
@Composable
fun UploadCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    CardSurface(modifier, spacing = 0.dp, onClick = onClick, dashed = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIcons.Upload, contentDescription = null, size = 20.dp, tint = colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun NameRow(name: String, verified: Boolean, style: TextStyle) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            modifier = Modifier.weight(1f, fill = false),
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (verified) VerifiedMark()
    }
}

@Composable
internal fun VerifiedMark() {
    AppIcon(AppIcons.ShieldCheck, contentDescription = null, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
}
