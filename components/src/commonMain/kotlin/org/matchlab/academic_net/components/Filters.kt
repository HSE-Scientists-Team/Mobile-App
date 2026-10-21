package org.matchlab.academic_net.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The button that opens a filters sheet. While filters are applied ([active]) the icon is
 * primary and a dot sits on its corner, so the user sees that the list is filtered.
 *
 * @param contentDescription what a screen reader says, e.g. "Фильтры"
 */
@Composable
fun FiltersButton(
    active: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier) {
        AppIconButton(
            icon = AppIcons.Sliders,
            contentDescription = contentDescription,
            onClick = onClick,
            contentColor = if (active) colors.primary else colors.onSurfaceVariant,
        )
        if (active) {
            AppBadgeDot(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
        }
    }
}

/**
 * A bottom sheet of filters: the [title] (with an optional text action in the header, usually
 * "Сбросить"), the [content] that scrolls when it does not fit, and two buttons at the bottom.
 *
 * [content] is laid out in a column with 20dp between its groups; use [FilterChipGroup] for
 * chips and the field components for text. The sheet keeps the default insets of
 * [AppBottomSheet], so the footer stays above the keyboard.
 *
 * @param onDismiss swiping the sheet away or tapping outside it
 * @param secondaryText the outlined button on the left, e.g. "Отмена"
 * @param primaryText the filled button on the right, e.g. "Применить"
 * @param headerActionText the action in the sheet header, shown only together with [onHeaderAction]
 */
@Composable
fun FiltersSheet(
    title: String,
    onDismiss: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit,
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    headerActionText: String? = null,
    onHeaderAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        SheetHeader(title = title, action = headerActionText, onAction = onHeaderAction)
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppButton(
                text = secondaryText,
                onClick = onSecondary,
                modifier = Modifier.weight(1f),
                variant = AppButtonVariant.Outlined,
            )
            AppButton(
                text = primaryText,
                onClick = onPrimary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A titled group of filter chips that wrap onto several lines. Put [AppFilterChip]s into
 * [content]; chips of one group combine with "or", groups with "and".
 */
@Composable
fun FilterChipGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}
