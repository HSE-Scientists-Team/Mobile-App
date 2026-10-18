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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

    /** [Text] in the error color, e.g. "Удалить работу" under a form or "Удалить" in a confirmation dialog. */
    TextError,
}

/**
 * "Button/Filled", "Button/Tonal", "Button/Outlined" and "Button/Text" (and their variants in the
 * error color): 40dp, full-round.
 *
 * A burst of taps runs [onClick] once (see [rememberOnceClick]): a double tap cannot start the
 * action twice.
 *
 * With [enabled] `false` the button is dimmed and takes no taps. A [Text][AppButtonVariant.Text] button
 * then writes its label in `onSurface` at 38% ("Отправить через 0:45" while the countdown runs).
 *
 * With [loading] the label is replaced by an arc that turns (2dp thick with round ends, 16dp across its
 * center line as in the design, so 18dp with the stroke) in the color of the label of the variant
 * (`onPrimary` on [Filled][AppButtonVariant.Filled]), and the taps do not pass. The button keeps its size,
 * as the label stays in the layout invisible, and its colors: it is not dimmed like a disabled one, as it has
 * not given up, it is busy (the "Войти" of a form while the request is on its way). The label stays for a
 * screen reader, which also hears the progress.
 *
 * @param loading whether the action started by the button is in progress; it wins over [enabled]
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Filled,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val onceClick = rememberOnceClick(onClick)
    // A loading button is switched off for taps, but keeps the colors of an enabled one: they are given
    // to the disabled state too. Otherwise the default dimmed colors of the disabled state stay.
    val canClick = enabled && !loading
    val label: @Composable RowScope.() -> Unit = { ButtonLabel(text, loading) }
    when (variant) {
        AppButtonVariant.Filled -> Button(
            onClick = onceClick,
            modifier = modifier,
            enabled = canClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                disabledContainerColor = colors.primary.keptWhile(loading),
                disabledContentColor = colors.onPrimary.keptWhile(loading),
            ),
            content = label,
        )
        AppButtonVariant.Tonal -> FilledTonalButton(
            onClick = onceClick,
            modifier = modifier,
            enabled = canClick,
            shape = CircleShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                disabledContainerColor = colors.primaryContainer.keptWhile(loading),
                disabledContentColor = colors.onPrimaryContainer.keptWhile(loading),
            ),
            content = label,
        )
        AppButtonVariant.Outlined, AppButtonVariant.OutlinedError -> {
            val isError = variant == AppButtonVariant.OutlinedError
            val content = if (isError) colors.error else colors.primary
            OutlinedButton(
                onClick = onceClick,
                modifier = modifier,
                enabled = canClick,
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = content,
                    disabledContentColor = content.keptWhile(loading),
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = when {
                        !enabled && !loading -> colors.outlineVariant
                        isError -> colors.error
                        else -> colors.outline
                    },
                ),
                content = label,
            )
        }
        AppButtonVariant.Text, AppButtonVariant.TextError -> {
            val content = if (variant == AppButtonVariant.TextError) colors.error else colors.primary
            TextButton(
                onClick = onceClick,
                modifier = modifier,
                enabled = canClick,
                shape = CircleShape,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = content,
                    disabledContentColor = if (loading) {
                        content
                    } else {
                        colors.onSurface.copy(alpha = DisabledContentAlpha)
                    },
                ),
                // Same 24dp side padding as the other buttons in the design.
                contentPadding = ButtonDefaults.ContentPadding,
                content = label,
            )
        }
    }
}

/** The opacity of the content of a disabled component in Material 3 and in the design ("Button/Text", disabled). */
private const val DisabledContentAlpha = 0.38f

/**
 * This color for the disabled state of a button while [loading], and "no color" (the button then falls
 * back to the dimmed colors of Material 3) otherwise.
 */
private fun Color.keptWhile(loading: Boolean): Color = if (loading) this else Color.Unspecified

/**
 * The label of [AppButton], or the arc that replaces it while [loading]. The label is always laid out, in
 * the loading state transparent, so the button is as wide as with the label and does not jump.
 */
@Composable
private fun ButtonLabel(text: String, loading: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = if (loading) Color.Transparent else Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The color of the label, whatever the variant and the state of the button are.
        if (loading) InlineSpinner(color = LocalContentColor.current)
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
