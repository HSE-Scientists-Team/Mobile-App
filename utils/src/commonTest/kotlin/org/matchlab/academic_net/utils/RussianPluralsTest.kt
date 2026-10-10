package org.matchlab.academic_net.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class RussianPluralsTest {
    private fun forms(vararg counts: Int) = counts.map { russianPluralForm(it) }.toSet()

    @Test
    fun oneForNumbersEndingInOneExceptEleven() {
        assertEquals(setOf(RussianPluralForm.One), forms(1, 21, 31, 101, 1001))
    }

    @Test
    fun fewForNumbersEndingInTwoToFourExceptTwelveToFourteen() {
        assertEquals(setOf(RussianPluralForm.Few), forms(2, 3, 4, 22, 24, 102, 134))
    }

    @Test
    fun manyForZeroFiveToTwentyAndTheirRepeats() {
        assertEquals(
            setOf(RussianPluralForm.Many),
            forms(0, 5, 9, 10, 11, 12, 13, 14, 15, 19, 20, 25, 111, 112, 114, 120),
        )
    }

    @Test
    fun negativeNumbersFollowTheirAbsoluteValue() {
        assertEquals(RussianPluralForm.One, russianPluralForm(-1))
        assertEquals(RussianPluralForm.Few, russianPluralForm(-3))
    }
}
