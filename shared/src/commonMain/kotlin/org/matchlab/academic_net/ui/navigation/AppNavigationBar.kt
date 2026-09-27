package org.matchlab.academic_net.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom navigation bar from the design ("Navigation bar" component).
 *
 * Material 3's NavigationBar can't be reused as is: it is 80dp tall with 8dp between items and a
 * 56dp indicator, while the design uses a 78dp bar, flush items and a 64dp indicator.
 */
@Composable
fun AppNavigationBar(
    selectedDestination: TopLevelDestination,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.navigationBars,
) {
    Row(
        modifier = modifier
            // Insets go outside the background: in the design the area under the bar
            // (home indicator) shows the screen background, not the bar.
            .windowInsetsPadding(windowInsets)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape = CircleShape)
            .selectableGroup(),
    ) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationItem(
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
                icon = painterResource(destination.icon),
                label = stringResource(destination.label),
            )
        }
    }
}

@Composable
private fun RowScope.NavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: Painter,
    label: String,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val indicatorProgress by animateFloatAsState(if (selected) 1f else 0f)
    val iconColor by animateColorAsState(
        if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
    )
    val labelColor by animateColorAsState(
        if (selected) colors.onSurface else colors.onSurfaceVariant,
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .padding(top = 12.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 32.dp)
                .clip(CircleShape)
                .indication(interactionSource, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            // Active indicator grows from the center, like the Material 3 one.
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(indicatorProgress)
                    .graphicsLayer { alpha = indicatorProgress }
                    .background(colors.primaryContainer, CircleShape),
            )
            Icon(icon, contentDescription = null, tint = iconColor)
        }
        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
