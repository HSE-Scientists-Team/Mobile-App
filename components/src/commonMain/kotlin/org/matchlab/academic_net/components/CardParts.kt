package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_accept
import academicnet.components.generated.resources.components_decline
import academicnet.components.generated.resources.components_message
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/**
 * Container shared by the "Card/…" components: surface with a 1dp outline. The paddings are the
 * Figma ones; as in Figma the outline adds to the size, so 1dp is added to them.
 */
@Composable
internal fun CardSurface(
    modifier: Modifier,
    spacing: Dp,
    onClick: (() -> Unit)? = null,
    dashed: Boolean = false,
    cornerRadius: Dp = 16.dp,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = horizontalPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(cornerRadius)
    val outline = if (dashed) {
        Modifier.drawWithContent {
            drawContent()
            val width = 1.dp.toPx()
            val radius = cornerRadius.toPx() - width / 2
            drawRoundRect(
                color = colors.outlineVariant,
                topLeft = Offset(width / 2, width / 2),
                size = Size(size.width - width, size.height - width),
                cornerRadius = CornerRadius(radius),
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

/** Round 36dp button with a 20dp icon used inside cards. */
@Composable
internal fun CardIconButton(
    icon: DrawableResource,
    contentDescription: String?,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(containerColor)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
    ) {
        AppIcon(icon, contentDescription, size = 20.dp, tint = contentColor)
    }
}

/** Accept / decline on the left and message on the right, as on project and candidate cards. */
@Composable
internal fun CardDecisionActions(
    onAccept: (() -> Unit)?,
    onDecline: (() -> Unit)?,
    onMessage: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onAccept != null) {
            CardIconButton(
                AppIcons.Check,
                stringResource(Res.string.components_accept),
                colors.tertiaryContainer,
                colors.onTertiaryContainer,
                onAccept,
            )
        }
        if (onDecline != null) {
            CardIconButton(
                AppIcons.X,
                stringResource(Res.string.components_decline),
                colors.errorContainer,
                colors.error,
                onDecline,
            )
        }
        Spacer(Modifier.weight(1f))
        if (onMessage != null) MessageIconButton(onMessage)
    }
}

@Composable
internal fun MessageIconButton(onClick: () -> Unit) {
    CardIconButton(
        AppIcons.Message,
        stringResource(Res.string.components_message),
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primary,
        onClick,
    )
}
