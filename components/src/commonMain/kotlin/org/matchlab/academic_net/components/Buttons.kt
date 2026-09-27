package org.matchlab.academic_net.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource

enum class AppButtonVariant {
    Filled,
    Tonal,
    Outlined,

    /** Outlined in the error color, e.g. "Выгнать" on participant cards. */
    OutlinedError,
    Text,
}

/** "Button/Filled", "Button/Tonal", "Button/Outlined" and "Button/Text": 40dp, full-round. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Filled,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val label: @Composable RowScope.() -> Unit = {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when (variant) {
        AppButtonVariant.Filled -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
            content = label,
        )
        AppButtonVariant.Tonal -> FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
            ),
            content = label,
        )
        AppButtonVariant.Outlined, AppButtonVariant.OutlinedError -> {
            val isError = variant == AppButtonVariant.OutlinedError
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isError) colors.error else colors.primary,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = when {
                        !enabled -> colors.outlineVariant
                        isError -> colors.error
                        else -> colors.outline
                    },
                ),
                content = label,
            )
        }
        AppButtonVariant.Text -> TextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.primary),
            // Same 24dp side padding as the other buttons in the design.
            contentPadding = ButtonDefaults.ContentPadding,
            content = label,
        )
    }
}

/** "FAB": 56dp primary circle with a soft shadow. */
@Composable
fun AppFab(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    icon: DrawableResource = AppIcons.Plus,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.dropShadow(CircleShape, FabShadow),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        AppIcon(icon, contentDescription)
    }
}

// Figma: drop shadow 0 4 12, #0F1729 at 20%.
private val FabShadow = Shadow(
    radius = 12.dp,
    color = Color(0x330F1729),
    offset = DpOffset(0.dp, 4.dp),
)

enum class AppIconButtonVariant { Standard, Tonal }

/** "Icon button": 40dp circle with a 24dp icon. */
@Composable
fun AppIconButton(
    icon: DrawableResource,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppIconButtonVariant = AppIconButtonVariant.Standard,
    enabled: Boolean = true,
    contentColor: Color = when (variant) {
        AppIconButtonVariant.Standard -> MaterialTheme.colorScheme.onSurfaceVariant
        AppIconButtonVariant.Tonal -> MaterialTheme.colorScheme.onSecondaryContainer
    },
) {
    when (variant) {
        AppIconButtonVariant.Standard -> IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor),
            shape = CircleShape,
        ) {
            AppIcon(icon, contentDescription)
        }
        AppIconButtonVariant.Tonal -> FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = contentColor,
            ),
        ) {
            AppIcon(icon, contentDescription)
        }
    }
}
