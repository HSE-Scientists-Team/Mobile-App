package org.matchlab.academic_net.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth

class YearMonthFormatTest {
    @Test
    fun shortLabelIsLowerCaseMonthAndYear() {
        assertEquals("окт 2026", YearMonth(2026, 10).shortLabel())
        assertEquals("май 2027", YearMonth(2027, 5).shortLabel())
    }

    @Test
    fun fieldLabelStartsWithACapital() {
        assertEquals("Окт 2026", YearMonth(2026, 10).fieldLabel())
        assertEquals("Янв 2027", YearMonth(2027, 1).fieldLabel())
    }

    @Test
    fun everyMonthHasItsName() {
        val names = (1..12).map { YearMonth(2026, it).shortLabel().substringBefore(' ') }

        assertEquals(12, names.toSet().size)
        assertEquals("дек", names.last())
    }

    @Test
    fun monthShortName() {
        assertEquals("янв", Month.JANUARY.shortName())
        assertEquals("май", Month.MAY.shortName())
        assertEquals("дек", Month.DECEMBER.shortName())
    }

    @Test
    fun rangeIsWrittenWithADash() {
        assertEquals("окт 2026 — мар 2027", formatMonthRange(YearMonth(2026, 10), YearMonth(2027, 3)))
    }
}
