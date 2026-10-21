package org.matchlab.academic_net.ui.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The banner "Отзыв отправлен" (09s) and the new reading of a profile come only for a review sent to that person after the profile was opened. */
class ReviewSignalTest {
    @Test
    fun aReviewSentToThisPersonAfterTheProfileWasOpenedIsSignalled() {
        assertTrue(isReviewJustSentTo("anna", reviewsSent = 1, reviewsSentAtOpen = 0, lastReviewedId = "anna"))
    }

    @Test
    fun noReviewSentSinceOpeningIsNoSignal() {
        assertFalse(isReviewJustSentTo("anna", reviewsSent = 0, reviewsSentAtOpen = 0, lastReviewedId = null))
        // A review of this person sent before the profile was opened is old news.
        assertFalse(isReviewJustSentTo("anna", reviewsSent = 3, reviewsSentAtOpen = 3, lastReviewedId = "anna"))
    }

    @Test
    fun aReviewOfSomebodyElseTellsThisProfileNothing() {
        assertFalse(isReviewJustSentTo("anna", reviewsSent = 2, reviewsSentAtOpen = 1, lastReviewedId = "boris"))
        assertFalse(isReviewJustSentTo("anna", reviewsSent = 2, reviewsSentAtOpen = 1, lastReviewedId = null))
    }

    @Test
    fun aProfileOpenedAfterSeveralReviewsHearsOnlyOfTheNextOne() {
        assertFalse(isReviewJustSentTo("anna", reviewsSent = 5, reviewsSentAtOpen = 5, lastReviewedId = "anna"))
        assertTrue(isReviewJustSentTo("anna", reviewsSent = 6, reviewsSentAtOpen = 5, lastReviewedId = "anna"))
    }

    @Test
    fun theIdsAreComparedExactly() {
        assertFalse(isReviewJustSentTo("anna", reviewsSent = 1, reviewsSentAtOpen = 0, lastReviewedId = "Anna"))
        assertFalse(isReviewJustSentTo("anna", reviewsSent = 1, reviewsSentAtOpen = 0, lastReviewedId = "anna "))
    }
}
