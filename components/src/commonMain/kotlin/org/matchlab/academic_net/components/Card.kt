package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_accept
import academicnet.components.generated.resources.components_decline
import academicnet.components.generated.resources.components_verified
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/**
 * Base of the "Card/…" components: surface with a 1dp outline. The paddings are the Figma ones;
 * as in Figma the outline adds to the size, so 1dp is added to them.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    spacing: Dp = 0.dp,
    cornerRadius: Dp = 16.dp,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = horizontalPadding,
    dashed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(cornerRadius)
    val outline = if (dashed) {
        Modifier.drawWithContent {
            drawContent()
            val width = 1.dp.toPx()
            drawRoundRect(
                color = colors.outlineVariant,
                topLeft = Offset(width / 2, width / 2),
                size = Size(size.width - width, size.height - width),
                cornerRadius = CornerRadius(cornerRadius.toPx() - width / 2),
                style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        }
    } else {
        Modifier.border(1.dp, colors.outlineVariant, shape)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .then(outline)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = horizontalPadding + 1.dp, vertical = verticalPadding + 1.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Accept and decline on the left, chat on the right, as on invitations and candidates. */
@Composable
fun AcceptDeclineActions(
    onAcceptClick: () -> Unit,
    onDeclineClick: () -> Unit,
    onChatClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardIconButton(
            AppIcons.Check,
            stringResource(Res.string.components_accept),
            onAcceptClick,
            colors.tertiaryContainer,
            colors.onTertiaryContainer,
        )
        CardIconButton(
            AppIcons.X,
            stringResource(Res.string.components_decline),
            onDeclineClick,
            colors.errorContainer,
            colors.error,
        )
        Spacer(Modifier.weight(1f))
        ChatIconButton(onChatClick)
    }
}

/** Verification mark next to a name: shield with a check. */
@Composable
fun VerifiedBadge(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    AppIcon(
        icon = AppIcons.ShieldCheck,
        contentDescription = stringResource(Res.string.components_verified),
        modifier = modifier,
        size = size,
        tint = MaterialTheme.colorScheme.primary,
    )
}

/** One-line name followed by the [VerifiedBadge] when [verified]. */
@Composable
fun PersonName(
    name: String,
    verified: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f, fill = false),
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (verified) VerifiedBadge()
    }
}

/** "Card/Info": icon with a value such as dates, place or format. */
@Composable
fun InfoCard(text: String, icon: DrawableResource, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    AppCard(modifier) {
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
    AppCard(modifier, onClick = onClick, dashed = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIcons.Upload, contentDescription = null, size = 20.dp, tint = colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
