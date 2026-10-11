package org.matchlab.academic_net.components.format

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.date_day_month
import academicnet.components.generated.resources.date_month_range
import academicnet.components.generated.resources.date_months_short
import academicnet.components.generated.resources.date_months_short_day
import academicnet.components.generated.resources.date_year_month
import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * Abbreviated name of [month] on its own: "окт" | "Oct". The text follows the app language and is
 * not capitalized; capitalize it at the call site where it starts a label ("Окт" on a month chip).
 * For a month that follows a day number use [dayMonthText], which declines the name where the
 * language needs it ("9 мая").
 */
@Composable
fun monthShortName(month: Month): String = stringArrayResource(Res.array.date_months_short)[month.ordinal]

/** A month with its year, as in a project card's footer: "окт 2026" | "Oct 2026". */
@Composable
fun yearMonthText(value: YearMonth): String = stringResource(
    Res.string.date_year_month,
    monthShortName(value.month),
    value.year,
)

/**
 * The months a project runs, from [start] to [end]: "окт 2026 — мар 2027" | "Oct 2026 – Mar 2027".
 * The dash differs by language, so the pair is never joined by hand.
 */
@Composable
fun monthRangeText(start: YearMonth, end: YearMonth): String = stringResource(
    Res.string.date_month_range,
    yearMonthText(start),
    yearMonthText(end),
)

/**
 * A day without a year, as for the last message of a chat: "8 сен" | "Sep 8". The month is in the
 * form that follows a day number ("9 мая", not "9 май").
 */
@Composable
fun dayMonthText(date: LocalDate): String = stringResource(
    Res.string.date_day_month,
    date.day,
    stringArrayResource(Res.array.date_months_short_day)[date.month.ordinal],
)
