package org.matchlab.academic_net.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private const val DisabledAlpha = 0.38f

/**
 * "Switch": 44x24 track with an 18dp thumb. Pass `onCheckedChange = null` when a parent (e.g. a
 * list row) handles the toggle.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val thumbOffset by animateDpAsState(if (checked) 23.dp else 3.dp)
    val trackColor by animateColorAsState(if (checked) colors.primary else colors.outline)
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = null,
            indication = ripple(bounded = false, radius = 24.dp),
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .then(toggle)
            .size(width = 44.dp, height = 24.dp)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .background(trackColor, CircleShape),
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset, y = 3.dp)
                .size(18.dp)
                .background(colors.surface, CircleShape),
        )
    }
}

/**
 * "Radio": 20dp ring with a 10dp dot. Pass `onClick = null` when a parent (e.g. a list row)
 * handles the selection.
 */
@Composable
fun AppRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val color by animateColorAsState(if (selected) colors.primary else colors.outline)
    val dotRadius by animateDpAsState(if (selected) 5.dp else 0.dp)
    val select = if (onClick != null) {
        Modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            interactionSource = null,
            indication = ripple(bounded = false, radius = 20.dp),
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Canvas(
        modifier
            .then(select)
            .size(20.dp)
            .alpha(if (enabled) 1f else DisabledAlpha),
    ) {
        val stroke = 2.dp.toPx()
        drawCircle(color, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
        if (dotRadius > 0.dp) drawCircle(color, radius = dotRadius.toPx())
    }
}

/**
 * "Checkbox": a 20dp rounded square with a 2dp edge, the size and the edge of [AppRadioButton]. Unchecked it is an empty
 * box with an `outline` edge; checked it is filled with `primary` and holds a white check mark (the [AppIcons.Check] at
 * 14dp). The corners are the 4dp radius token of the design (`shapes.extraSmall`), and the change is animated like that of
 * the switch and the radio.
 *
 * The design system has no checkbox yet: this one is built from the numbers of the radio and the tokens of the theme.
 *
 * It only draws the state and takes no taps. A 20dp box would be a poor touch target and a second stop for a screen
 * reader, so the touch target and the accessibility node are those of the row around it, see [AppCheckboxRow].
 *
 * @param checked whether the box is ticked
 * @param enabled `false` dims the box to 38% as a disabled switch is
 */
@Composable
fun AppCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraSmall
    // The fill fades from the same color with no alpha, not from transparent black, which would pass through grey.
    val fill by animateColorAsState(if (checked) colors.primary else colors.primary.copy(alpha = 0f))
    val edge by animateColorAsState(if (checked) colors.primary else colors.outline)
    Box(
        modifier
            .size(20.dp)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .background(fill, shape)
            .border(2.dp, edge, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) AppIcon(AppIcons.Check, contentDescription = null, size = 14.dp, tint = colors.onPrimary)
    }
}

/**
 * A line with an [AppCheckbox] and what it stands for, the way a form asks for an agreement or a choice that may be
 * left as it is: "Отправлять статистику использования". The whole row is the control: a tap anywhere on it toggles the
 * box, it is one node for a screen reader (the role is a checkbox, the state is read together with [text] and [note]),
 * and it is at least 48dp tall, which is the touch target of the platforms.
 *
 * The box stands by the first line of the text, 12dp from it; the optional [note] is a quieter second line under the
 * text. There is no card around the row: it sits on the background of the form like a field does. A toggle is meant to
 * be tapped as often as the user likes, so the row takes every tap (no debounce, unlike an action row).
 *
 * @param text what is chosen, the first line (`bodyLarge`, `onSurface`)
 * @param checked whether the box is ticked
 * @param onCheckedChange the new state the user asked for; the caller owns the state
 * @param note a short explanation under [text] (`labelSmall`, `onSurfaceVariant`), e.g. where the choice can be changed
 * later; `null` for none
 * @param enabled `false` takes no taps and dims the box and the texts to 38%
 */
@Composable
fun AppCheckboxRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val textAlpha = if (enabled) 1f else DisabledAlpha
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .heightIn(min = CheckboxRowMinHeight),
        // A one-line row is centered in the touch target, a two-line one fills it.
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            // The box is as tall as one line of the text (20dp) and stands by the first one when the text wraps.
            verticalAlignment = Alignment.Top,
        ) {
            AppCheckbox(checked = checked, enabled = enabled)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface.copy(alpha = textAlpha),
                )
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant.copy(alpha = textAlpha),
                    )
                }
            }
        }
    }
}

/** The touch target of [AppCheckboxRow]. */
private val CheckboxRowMinHeight = 48.dp
