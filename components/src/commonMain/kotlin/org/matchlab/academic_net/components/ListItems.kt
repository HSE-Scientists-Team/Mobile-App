package org.matchlab.academic_net.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.matchlab.academic_net.components.interaction.onceClickable

/** "List item/Action": action row in a list or a sheet. A burst of taps runs [onClick] once. */
@Composable
fun ActionListItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: DrawableResource = AppIcons.Check,
) {
    OutlinedRow(modifier.onceClickable(onClick = onClick), MaterialTheme.shapes.large) {
        AppIcon(icon, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        TitleWithSubtitle(title, subtitle)
        Chevron()
    }
}

/** "List item/Setting": setting row that opens another screen. A burst of taps runs [onClick] once. */
@Composable
fun SettingListItem(
    title: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    OutlinedRow(modifier.onceClickable(onClick = onClick), MaterialTheme.shapes.large) {
        AppIcon(icon, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        TitleWithSubtitle(title, subtitle)
        Chevron()
    }
}

/**
 * "List item/Toggle": row with a switch; the whole row toggles it. With [enabled] `false` the
 * row ignores taps and the switch is dimmed, e.g. while the new value is being saved.
 *
 * @param dimText whether the title and the subtitle are drawn at 38% alpha, as in the design's `state=disabled`; by
 *  default they follow [enabled]. A row that is only busy (a save is running) passes `false` so its text stays
 *  as it is while the switch is dimmed.
 */
@Composable
fun ToggleListItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    dimText: Boolean = !enabled,
) {
    OutlinedRow(
        modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        MaterialTheme.shapes.large,
    ) {
        TitleWithSubtitle(title, subtitle, dim = dimText)
        AppSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** "List item/Radio": one option of a single-choice list; the whole row selects it. */
@Composable
fun RadioListItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedRow(
        modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        MaterialTheme.shapes.medium,
        verticalPadding = 12.dp,
    ) {
        AppRadioButton(selected = selected, onClick = null)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * What [PersonListItem] shows.
 *
 * @property name the person's name; an empty one is shown as "Без имени" (see [PersonName])
 * @property subtitle the line under the name, e.g. "Stanford · 3 курс · Computer Science"
 * @property avatar the picture of the person
 */
@Immutable
data class PersonListItemUiModel(
    val name: String,
    val subtitle: String,
    val avatar: DrawableResource,
)

/**
 * "List item/Person": person in a list; clickable rows get a chevron. [outlined] puts the row in
 * a rounded card with an outline, as on the collaborators search screen. A burst of taps runs
 * [onClick] once.
 */
@Composable
fun PersonListItem(
    model: PersonListItemUiModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    outlined: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val clickable = if (onClick != null) Modifier.onceClickable(onClick = onClick) else Modifier
    val row = @Composable { content: @Composable RowScope.() -> Unit ->
        if (outlined) {
            // Figma padding 16x12 plus the 1dp outline.
            val shape = MaterialTheme.shapes.large
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface)
                    .border(1.dp, colors.outlineVariant, shape)
                    .then(modifier)
                    .then(clickable)
                    .padding(horizontal = 17.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        } else {
            PlainRow(modifier.then(clickable), content)
        }
    }
    row {
        Avatar(model.avatar, contentDescription = null, size = 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PersonName(model.name, MaterialTheme.typography.titleMedium)
            Text(
                text = model.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) Chevron()
    }
}

@Immutable
data class NotificationListItemUiModel(
    val title: String,
    val time: String,
    val icon: DrawableResource = AppIcons.Message,
)

/** "List item/Notification": notification with an icon and when it arrived. A burst of taps runs [onClick] once. */
@Composable
fun NotificationListItem(
    model: NotificationListItemUiModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    PlainRow(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.onceClickable(onClick = onClick) else Modifier),
    ) {
        Box(Modifier.background(colors.surface, CircleShape).padding(8.dp)) {
            AppIcon(model.icon, contentDescription = null, size = 20.dp, tint = colors.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = model.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(model.time, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

// Rows with an outline: as in Figma the 1dp outline adds to the size, so the padding is 1dp larger.
@Composable
private fun OutlinedRow(
    modifier: Modifier,
    shape: Shape,
    verticalPadding: Dp = 14.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant, shape)
            .then(modifier)
            .padding(horizontal = 17.dp, vertical = verticalPadding + 1.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PlainRow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .then(modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowScope.TitleWithSubtitle(title: String, subtitle: String?, dim: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val titleColor = if (dim) colors.onSurface.copy(alpha = DisabledTextAlpha) else colors.onSurface
    val subtitleColor = if (dim) colors.onSurfaceVariant.copy(alpha = DisabledTextAlpha) else colors.onSurfaceVariant
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = subtitleColor)
        }
    }
}

@Composable
private fun Chevron() {
    AppIcon(AppIcons.ChevronRight, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.outline)
}

/** The alpha of the text of a disabled row (Figma `List item/Toggle`, `state=disabled`). */
private const val DisabledTextAlpha = 0.38f
