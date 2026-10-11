package org.matchlab.academic_net.feature.projects.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppBottomSheet
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppIconButton
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.SheetHeader
import org.matchlab.academic_net.components.format.monthShortName
import org.matchlab.academic_net.feature.projects.ui.generated.resources.Res
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_next_year
import org.matchlab.academic_net.feature.projects.ui.generated.resources.projects_prev_year

/**
 * Choice of a month: the year with arrows and the twelve months below it. The design shows only
 * the fields, so the sheet is put together from the sheet header, icon buttons and filter chips.
 */
@Composable
internal fun MonthPickerSheet(
    title: String,
    selected: YearMonth?,
    initialYear: Int,
    minYear: Int,
    maxYear: Int,
    onSelect: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var year by rememberSaveable { mutableIntStateOf(initialYear.coerceIn(minYear, maxYear)) }
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        SheetHeader(title)
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconButton(
                    icon = AppIcons.ArrowLeft,
                    contentDescription = stringResource(Res.string.projects_prev_year),
                    onClick = { year -= 1 },
                    enabled = year > minYear,
                )
                Text(
                    text = year.toString(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                AppIconButton(
                    icon = AppIcons.ArrowRight,
                    contentDescription = stringResource(Res.string.projects_next_year),
                    onClick = { year += 1 },
                    enabled = year < maxYear,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3,
            ) {
                for (number in 1..12) {
                    val month = YearMonth(year, number)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        AppFilterChip(
                            text = monthShortName(month.month).replaceFirstChar { it.uppercase() },
                            selected = month == selected,
                            onClick = { onSelect(month) },
                        )
                    }
                }
            }
        }
    }
}
