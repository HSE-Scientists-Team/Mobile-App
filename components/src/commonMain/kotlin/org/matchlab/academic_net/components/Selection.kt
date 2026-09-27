package org.matchlab.academic_net.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
