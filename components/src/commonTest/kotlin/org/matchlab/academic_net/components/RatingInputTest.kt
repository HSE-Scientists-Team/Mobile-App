package org.matchlab.academic_net.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** What [RatingInput] reports for a tap on a star: the number of the star, or 0 for the star that is the mark. */
class RatingInputTest {
    @Test
    fun aTapOnAStarSetsItsNumberWhenNothingIsChosen() {
        assertEquals(listOf(1, 2, 3, 4, 5), (1..5).map { ratingAfterTap(value = 0, tapped = it) })
    }

    @Test
    fun aTapOnAnotherStarMovesTheMarkUpOrDown() {
        assertEquals(5, ratingAfterTap(value = 2, tapped = 5))
        assertEquals(1, ratingAfterTap(value = 4, tapped = 1))
        assertEquals(3, ratingAfterTap(value = 4, tapped = 3))
    }

    @Test
    fun aTapOnTheChosenStarTakesTheMarkBack() {
        assertEquals(listOf(0, 0, 0, 0, 0), (1..5).map { ratingAfterTap(value = it, tapped = it) })
    }

    @Test
    fun aTapOnAFilledStarBelowTheMarkSetsThatStarNotZero() {
        // Only the last filled star is "the chosen one"; the ones before it are just filled.
        assertEquals(2, ratingAfterTap(value = 4, tapped = 2))
    }

    @Test
    fun twoTapsOnOneStarSetAndThenClear() {
        val first = ratingAfterTap(value = 0, tapped = 3)
        val second = ratingAfterTap(value = first, tapped = 3)

        assertEquals(3, first)
        assertEquals(0, second)
    }

    @Test
    fun aMarkAboveFiveIsDrawnAsFiveSoTheFifthStarClearsIt() {
        assertEquals(0, ratingAfterTap(value = 7, tapped = 5))
        assertEquals(3, ratingAfterTap(value = 7, tapped = 3))
        assertEquals(5, filledStars(7), "five stars are filled for 7")
    }

    @Test
    fun aNegativeMarkIsDrawnAsNoneSoAnyStarSetsItsNumber() {
        assertEquals(1, ratingAfterTap(value = -2, tapped = 1))
        assertEquals(5, ratingAfterTap(value = Int.MIN_VALUE, tapped = 5))
    }
}
