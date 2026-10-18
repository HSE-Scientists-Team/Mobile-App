package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_message
import academicnet.components.generated.resources.components_report
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.interaction.onceClickable
import org.matchlab.academic_net.components.interaction.rememberOnceClick

enum class AppButtonVariant {
    Filled,
    Tonal,
    Outlined,

    /** Outlined in the error color, e.g. "Выгнать" on participant cards. */
    OutlinedError,
    Text,
}

/**
 * "Button/Filled", "Button/Tonal", "Button/Outlined" and "Button/Text": 40dp, full-round.
 *
 * A burst of taps runs [onClick] once (see [rememberOnceClick]): a double tap cannot start the
 * action twice.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Filled,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val onceClick = rememberOnceClick(onClick)
    val label: @Composable RowScope.() -> Unit = {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when (variant) {
        AppButtonVariant.Filled -> Button(
            onClick = onceClick,
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
            onClick = onceClick,
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
                onClick = onceClick,
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
            onClick = onceClick,
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

/** "FAB": 56dp primary circle with a soft shadow. A burst of taps runs [onClick] once, like [AppButton]. */
@Composable
fun AppFab(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    icon: DrawableResource = AppIcons.Plus,
) {
    FloatingActionButton(
        onClick = rememberOnceClick(onClick),
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

/**
 * "Icon button": 40dp circle with a 24dp icon.
 *
 * A burst of taps runs [onClick] once, like [AppButton]: the actions of a bar (back, filters, edit)
 * open something and must not run twice. Pass `repeatable = true` for a button that is meant to be
 * tapped quickly several times, e.g. the arrows of a stepper.
 */
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
    repeatable: Boolean = false,
) {
    val onceClick = rememberOnceClick(onClick)
    val handleClick = if (repeatable) onClick else onceClick
    when (variant) {
        AppIconButtonVariant.Standard -> IconButton(
            onClick = handleClick,
            modifier = modifier,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor),
            shape = CircleShape,
        ) {
            AppIcon(icon, contentDescription)
        }
        AppIconButtonVariant.Tonal -> FilledTonalIconButton(
            onClick = handleClick,
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

/** "Пожаловаться" action (flag), e.g. in the app bar of a project or a profile. */
@Composable
fun ReportIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.error,
) {
    AppIconButton(
        icon = AppIcons.Flag,
        contentDescription = stringResource(Res.string.components_report),
        onClick = onClick,
        modifier = modifier,
        contentColor = contentColor,
    )
}

/** Round 36dp button with a 20dp icon, used for actions inside cards. A burst of taps runs [onClick] once. */
@Composable
fun CardIconButton(
    icon: DrawableResource,
    contentDescription: String?,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(containerColor)
            .onceClickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
    ) {
        AppIcon(icon, contentDescription, size = 20.dp, tint = contentColor)
    }
}

/**
 * Chat ("Написать") button of cards. [contentDescription] is what a screen reader says; a card
 * that knows whom it writes to can make it more precise ("Написать инициатору").
 */
@Composable
fun ChatIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(Res.string.components_message),
) {
    CardIconButton(
        icon = AppIcons.Message,
        contentDescription = contentDescription,
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}
