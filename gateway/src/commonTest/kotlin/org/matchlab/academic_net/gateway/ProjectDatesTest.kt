package org.matchlab.academic_net.gateway

import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.gateway.mapping.periodOfPlanned
import org.matchlab.academic_net.gateway.mapping.plannedEnd
import org.matchlab.academic_net.gateway.mapping.plannedStart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `planned_start` and `planned_end` of `docs/api-values.md`: written as ISO dates, read by their year and month. */
class ProjectDatesTest {
    private fun period(startYear: Int, startMonth: Int, endYear: Int, endMonth: Int) =
        ProjectPeriod(YearMonth(startYear, startMonth), YearMonth(endYear, endMonth))

    // ---- writing ----

    @Test
    fun theStartIsTheFirstDayOfTheStartMonth() {
        assertEquals("2026-10-01", period(2026, 10, 2027, 3).plannedStart())
        assertEquals("2026-01-01", period(2026, 1, 2026, 1).plannedStart())
        assertEquals("2026-12-01", period(2026, 12, 2027, 1).plannedStart())
    }

    @Test
    fun theEndIsTheLastDayOfTheEndMonth() {
        assertEquals("2026-11-30", period(2026, 10, 2026, 11).plannedEnd())
        assertEquals("2026-10-31", period(2026, 10, 2026, 10).plannedEnd())
        assertEquals("2026-12-31", period(2026, 10, 2026, 12).plannedEnd())
    }

    @Test
    fun februaryEndsOnThe28thInACommonYearAndThe29thInALeapYear() {
        assertEquals("2027-02-28", period(2026, 10, 2027, 2).plannedEnd())
        assertEquals("2028-02-29", period(2026, 10, 2028, 2).plannedEnd())
        // 1900 is not a leap year, 2000 is.
        assertEquals("1900-02-28", period(1900, 1, 1900, 2).plannedEnd())
        assertEquals("2000-02-29", period(2000, 1, 2000, 2).plannedEnd())
    }

    @Test
    fun aMonthWithThirtyDaysEndsOnThe30th() {
        assertEquals("2026-04-30", period(2026, 1, 2026, 4).plannedEnd())
        assertEquals("2026-06-30", period(2026, 1, 2026, 6).plannedEnd())
        assertEquals("2026-09-30", period(2026, 1, 2026, 9).plannedEnd())
        assertEquals("2026-11-30", period(2026, 1, 2026, 11).plannedEnd())
    }

    @Test
    fun aWrittenPeriodIsReadBackAsItself() {
        for (period in listOf(period(2026, 10, 2027, 3), period(2026, 12, 2026, 12), period(2027, 2, 2028, 2))) {
            assertEquals(period, periodOfPlanned(period.plannedStart(), period.plannedEnd()))
        }
    }

    // ---- reading ----

    @Test
    fun aDateIsReadByItsYearAndMonth() {
        assertEquals(period(2026, 10, 2027, 3), periodOfPlanned("2026-10-01", "2027-03-31"))
    }

    @Test
    fun theDayIsIgnored() {
        assertEquals(period(2026, 10, 2026, 10), periodOfPlanned("2026-10-17", "2026-10-02"))
    }

    @Test
    fun aDateWithATimeIsReadByItsMonth() {
        assertEquals(
            period(2026, 10, 2027, 3),
            periodOfPlanned("2026-10-01T00:00:00Z", "2027-03-31T23:59:59+03:00"),
        )
    }

    @Test
    fun aMonthWithoutADayIsRead() {
        assertEquals(period(2026, 10, 2027, 3), periodOfPlanned("2026-10", "2027-03"))
    }

    @Test
    fun theOuterSpacesAreCutAtTheStart() {
        assertEquals(period(2026, 10, 2027, 3), periodOfPlanned("  2026-10-01", "\t2027-03-31"))
    }

    @Test
    fun theSameMonthIsAPeriodOfOneMonth() {
        assertEquals(period(2026, 10, 2026, 10), periodOfPlanned("2026-10-01", "2026-10-31"))
    }

    @Test
    fun anEndBeforeTheStartIsUnknown() {
        assertNull(periodOfPlanned("2026-10-01", "2026-09-30"))
        assertNull(periodOfPlanned("2027-01", "2026-12"))
    }

    @Test
    fun aMissingOrBlankDateIsUnknown() {
        assertNull(periodOfPlanned(null, "2027-03-31"))
        assertNull(periodOfPlanned("2026-10-01", null))
        assertNull(periodOfPlanned("", "2027-03-31"))
        assertNull(periodOfPlanned("2026-10-01", ""))
        assertNull(periodOfPlanned("   ", "   "))
        assertNull(periodOfPlanned(null, null))
    }

    @Test
    fun aTextThatIsNotADateIsUnknown() {
        for (garbage in listOf("garbage", "soon", "2026", "2026-", "26-10-01", "10-2026", "2026/10/01", "2026-1-01", "x2026-10-01")) {
            assertNull(periodOfPlanned(garbage, "2027-03-31"), "start '$garbage'")
            assertNull(periodOfPlanned("2026-10-01", garbage), "end '$garbage'")
        }
    }

    @Test
    fun aMonthOutsideOneToTwelveIsUnknown() {
        assertNull(periodOfPlanned("2026-00-01", "2027-03-31"))
        assertNull(periodOfPlanned("2026-10-01", "2027-13-01"))
        assertNull(periodOfPlanned("2026-99", "2027-03"))
    }
}
