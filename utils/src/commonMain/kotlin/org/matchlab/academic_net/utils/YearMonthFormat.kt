package org.matchlab.academic_net.utils

import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth

/**
 * Short month names as they are written after a day ("9 мая"; the app is Russian-only).
 * Only May differs from the name of the month on its own.
 */
internal val RussianShortMonths = listOf(
    "янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек",
)

private const val MayIndex = 4

/** "окт", "май": the month on its own, as on the chips of a month picker or before a year. */
fun Month.shortName(): String = if (ordinal == MayIndex) "май" else RussianShortMonths[ordinal]

/** "окт 2026": a month in a line of text, such as a project card's footer. */
fun YearMonth.shortLabel(): String = "${month.shortName()} $year"

/** "Окт 2026": a month as the value of a field. */
fun YearMonth.fieldLabel(): String = shortLabel().replaceFirstChar { it.uppercase() }

/** "окт 2026 — мар 2027": the months a project runs. */
fun formatMonthRange(start: YearMonth, end: YearMonth): String = "${start.shortLabel()} — ${end.shortLabel()}"
