package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_back
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.interaction.onceClickable
import org.matchlab.academic_net.components.interaction.rememberOnceClick

/**
 * "Navigation bar": 78dp bar with flush items and a 64x32 pill indicator.
 *
 * Material 3's NavigationBar can't be reused as is: it is 80dp tall with 8dp between items and a
 * 56dp indicator.
 */
@Composable
fun AppNavigationBar(
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.navigationBars,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            // Insets go outside the background: in the design the area under the bar
            // (home indicator) shows the screen background, not the bar.
            .windowInsetsPadding(windowInsets)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape = CircleShape)
            .selectableGroup(),
        content = content,
    )
}

/** Item of [AppNavigationBar]. [badge] shows the "Badge/Dot" at the indicator's top-right. */
@Composable
fun RowScope.AppNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: DrawableResource,
    label: String,
    modifier: Modifier = Modifier,
    badge: Boolean = false,
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
        modifier = modifier
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
        Box(Modifier.size(width = 64.dp, height = 32.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .matchParentSize()
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
            }
            AppIcon(icon, contentDescription = null, tint = iconColor)
            if (badge) AppBadgeDot(Modifier.align(Alignment.TopStart).offset(x = 50.dp))
        }
        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/**
 * "App bar": back arrow (or [navigationIcon]), title with an optional subtitle and up to three
 * actions (use [AppIconButton] in [actions]). The white background extends under the status bar.
 *
 * A burst of taps on the arrow runs [onNavigateBack] once, so a double tap does not leave two
 * screens at once.
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.statusBars,
    // Screens that are closed rather than left (e.g. a form) show an X instead of the arrow.
    navigationIcon: DrawableResource = AppIcons.ArrowLeft,
    navigationContentDescription: String = stringResource(Res.string.components_back),
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .background(colors.surface)
            .windowInsetsPadding(windowInsets)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onNavigateBack != null) {
            Box(
                Modifier
                    .size(width = 24.dp, height = 40.dp)
                    .clickable(
                        interactionSource = null,
                        indication = ripple(bounded = false, radius = 20.dp),
                        role = Role.Button,
                        onClick = rememberOnceClick(onNavigateBack),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    icon = navigationIcon,
                    contentDescription = navigationContentDescription,
                    tint = colors.onSurface,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
    }
}

/** "Tabs/Primary": equal-width tabs, each with a 3dp bar underneath. */
@Composable
fun AppTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().selectableGroup()) {
        tabs.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            val textColor by animateColorAsState(if (selected) colors.primary else colors.onSurfaceVariant)
            val barColor by animateColorAsState(if (selected) colors.primary else colors.outlineVariant)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(index) })
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = textColor, maxLines = 1)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(barColor, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

/**
 * "Section header": section title with an optional "see all" style action. A burst of taps on the
 * action runs [onAction] once.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    HeaderRow(modifier.fillMaxWidth(), action, onAction) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "Sheet header": bottom sheet handle, title and an optional action (e.g. "Сбросить"). */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().background(colors.surface)) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .background(colors.outline, RoundedCornerShape(4.dp)),
            )
        }
        HeaderRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            action,
            onAction,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
            )
        }
    }
}

@Composable
private fun HeaderRow(
    modifier: Modifier,
    action: String?,
    onAction: (() -> Unit)?,
    title: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        title()
        if (action != null) {
            Text(
                text = action,
                modifier = if (onAction != null) {
                    Modifier.onceClickable(role = Role.Button, onClick = onAction)
                } else {
                    Modifier
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
