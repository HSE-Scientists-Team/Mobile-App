package org.matchlab.academic_net.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** How many stars of [RatingStars] are filled, and the two sizes of the design. */
class RatingStarsTest {
    @Test
    fun aMarkFromOneToFiveFillsThatManyStars() {
        assertEquals(listOf(1, 2, 3, 4, 5), (1..5).map(::filledStars))
    }

    @Test
    fun zeroFillsNoStar() {
        assertEquals(0, filledStars(0))
    }

    @Test
    fun aMarkAboveFiveIsLimitedToFive() {
        assertEquals(5, filledStars(6))
        assertEquals(5, filledStars(100))
        assertEquals(5, filledStars(Int.MAX_VALUE))
    }

    @Test
    fun aNegativeMarkIsLimitedToZero() {
        assertEquals(0, filledStars(-1))
        assertEquals(0, filledStars(Int.MIN_VALUE))
    }

    @Test
    fun theSmallStarsAreEighteenWithTwoBetweenThem() {
        // 5 * 18 + 4 * 2 = 98: the width of "Rating" size=sm in Figma.
        val size = RatingStarsSize.Small

        assertEquals(18f, size.star.value)
        assertEquals(2f, size.gap.value)
        assertEquals(98f, 5 * size.star.value + 4 * size.gap.value)
    }

    @Test
    fun theMediumStarsAreTwentyFourWithFourBetweenThem() {
        // 5 * 24 + 4 * 4 = 136: the width of "Rating" size=md in Figma.
        val size = RatingStarsSize.Medium

        assertEquals(24f, size.star.value)
        assertEquals(4f, size.gap.value)
        assertEquals(136f, 5 * size.star.value + 4 * size.gap.value)
    }
}
