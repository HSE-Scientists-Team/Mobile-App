package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_remove
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.theme.extendedColors

/** "Chip/Tag": read-only tag. */
@Composable
fun AppTagChip(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    ChipRow(modifier.background(colors.surfaceContainer)) {
        ChipText(text, colors.onSurfaceVariant)
    }
}

/** "Chip/Input": entered value with a leading remove icon; tapping the chip removes it. */
@Composable
fun AppInputChip(text: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    ChipRow(
        modifier
            .background(colors.surfaceContainer)
            .clickable(onClickLabel = stringResource(Res.string.components_remove), onClick = onRemove),
    ) {
        AppIcon(AppIcons.X, contentDescription = null, size = 16.dp, tint = colors.onSurfaceVariant)
        ChipText(text, colors.onSurfaceVariant)
    }
}

/**
 * "Chip/Filter". Pass `onClick = null` for a static label (e.g. the project type on a card).
 *
 * As in Figma, the unselected outline adds 1dp on each side, so it is 2dp larger than the selected chip.
 */
@Composable
fun AppFilterChip(
    text: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val container = if (selected) {
        Modifier.background(colors.primaryContainer, shape)
    } else {
        Modifier.border(1.dp, colors.outlineVariant, shape)
    }
    val interaction = if (onClick != null) {
        Modifier.selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier
            .clip(shape)
            .then(container)
            .then(interaction)
            .padding(horizontal = if (selected) 12.dp else 13.dp, vertical = if (selected) 6.dp else 7.dp),
    ) {
        ChipText(text, if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
    }
}

enum class AppStatusChipTone { Success, Pending, Off }

/** "Chip/Status" (success, off) and "Chip/Status/pending": status with an icon. */
@Composable
fun AppStatusChip(text: String, tone: AppStatusChipTone, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val (container, content) = when (tone) {
        AppStatusChipTone.Success -> colors.tertiaryContainer to colors.onTertiaryContainer
        AppStatusChipTone.Pending -> extended.warningContainer to extended.onWarningContainer
        AppStatusChipTone.Off -> colors.surfaceContainer to colors.onSurfaceVariant
    }
    val icon = if (tone == AppStatusChipTone.Pending) AppIcons.Clock else AppIcons.Check
    ChipRow(modifier.background(container)) {
        AppIcon(icon, contentDescription = null, size = 16.dp, tint = content)
        ChipText(text, content)
    }
}

@Composable
private fun ChipRow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .then(modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ChipText(text: String, color: Color) {
    Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
}
