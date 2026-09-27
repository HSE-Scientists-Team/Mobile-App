package org.matchlab.academic_net.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource

/** Verification status of a work or a document, shown as a shield icon. */
enum class VerificationStatus { Verified, Rejected }

/** "Card/Profile header": own or another user's profile header. */
@Composable
fun ProfileHeaderCard(
    name: String,
    subtitle: String,
    avatar: Painter,
    modifier: Modifier = Modifier,
    verified: Boolean = false,
    statuses: (@Composable FlowRowScope.() -> Unit)? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    onMessage: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    CardSurface(modifier, spacing = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(avatar, contentDescription = null, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurface,
                    )
                    if (verified) {
                        AppIcon(AppIcons.ShieldCheck, contentDescription = null, size = 18.dp, tint = colors.primary)
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        if (statuses != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = statuses,
            )
        }
        if ((actionText != null && onAction != null) || onMessage != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (actionText != null && onAction != null) {
                    AppButton(actionText, onAction, Modifier.weight(1f))
                }
                if (onMessage != null) MessageIconButton(onMessage)
            }
        }
    }
}

/** "Card/Initiator": project initiator with rating and a message action. */
@Composable
fun InitiatorCard(
    name: String,
    subtitle: String,
    stats: String,
    avatar: Painter,
    modifier: Modifier = Modifier,
    verified: Boolean = false,
    onMessage: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    CardSurface(modifier, spacing = 0.dp, onClick = onClick) {
        PersonSummary(name, subtitle, stats, avatar, verified, onMessage)
    }
}

/** "Card/Participant manage": project member on the management screen, with chat and kick. */
@Composable
fun ParticipantManageCard(
    name: String,
    subtitle: String,
    stats: String,
    avatar: Painter,
    kickText: String,
    onKick: () -> Unit,
    modifier: Modifier = Modifier,
    verified: Boolean = false,
    onMessage: (() -> Unit)? = null,
) {
    CardSurface(modifier, spacing = 12.dp) {
        PersonSummary(name, subtitle, stats, avatar, verified, onMessage)
        AppButton(kickText, onKick, Modifier.fillMaxWidth(), variant = AppButtonVariant.OutlinedError)
    }
}

/** "Card/Person": person as the subject of a report or the author of a review. */
@Composable
fun PersonCard(
    name: String,
    subtitle: String,
    avatar: Painter,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    CardSurface(modifier, spacing = 0.dp, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(avatar, contentDescription = null, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** "Card/Education": education document with its verification status. */
@Composable
fun EducationCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    status: VerificationStatus? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    CardSurface(modifier, spacing = 0.dp, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIcons.GraduationCap, contentDescription = null, size = 20.dp, tint = colors.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (status != null) VerificationIcon(status)
        }
    }
}

/** "Card/Work": scientific or project work in the portfolio with its verification status. */
@Composable
fun WorkCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: DrawableResource = AppIcons.FileText,
    status: VerificationStatus? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    CardSurface(modifier, spacing = 0.dp, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            AppIcon(icon, contentDescription = null, size = 20.dp, tint = colors.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    if (status != null) VerificationIcon(status)
                }
                Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

// Avatar, name with the verified mark, subtitle and stats, with a message button at the bottom-right.
@Composable
private fun PersonSummary(
    name: String,
    subtitle: String,
    stats: String,
    avatar: Painter,
    verified: Boolean,
    onMessage: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        Avatar(avatar, contentDescription = null, size = 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            NameRow(name, verified, MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(stats, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        if (onMessage != null) MessageIconButton(onMessage)
    }
}

@Composable
private fun VerificationIcon(status: VerificationStatus) {
    val colors = MaterialTheme.colorScheme
    when (status) {
        VerificationStatus.Verified ->
            AppIcon(AppIcons.ShieldCheck, contentDescription = null, size = 18.dp, tint = colors.primary)
        VerificationStatus.Rejected ->
            AppIcon(AppIcons.ShieldX, contentDescription = null, size = 18.dp, tint = colors.error)
    }
}
