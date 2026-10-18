package org.matchlab.academic_net.components.paging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When [LoadMoreEffect] asks for the next page: [isNearEnd], the rule of "no more than `threshold` items
 * are left after the last visible one".
 */
class LoadMoreTest {
    // --- the border of the threshold ---

    @Test
    fun theEndIsNearWhenExactlyThresholdItemsAreLeft() {
        // 20 items, indexes 0..19: after the item 14 the items 15..19 are left, five of them.
        assertTrue(isNearEnd(lastVisibleIndex = 14, itemCount = 20, threshold = 5))
    }

    @Test
    fun theEndIsNotNearWhenOneMoreThanThresholdItemsAreLeft() {
        assertFalse(isNearEnd(lastVisibleIndex = 13, itemCount = 20, threshold = 5))
    }

    @Test
    fun theEndIsNearAtTheLastItem() {
        assertTrue(isNearEnd(lastVisibleIndex = 19, itemCount = 20, threshold = 5))
        assertTrue(isNearEnd(lastVisibleIndex = 19, itemCount = 20, threshold = 0))
    }

    @Test
    fun thresholdZeroWantsTheLastItemVisible() {
        assertFalse(isNearEnd(lastVisibleIndex = 18, itemCount = 20, threshold = 0))
        assertTrue(isNearEnd(lastVisibleIndex = 19, itemCount = 20, threshold = 0))
    }

    @Test
    fun aLargerThresholdLoadsEarlier() {
        assertFalse(isNearEnd(lastVisibleIndex = 9, itemCount = 20, threshold = 5))
        assertTrue(isNearEnd(lastVisibleIndex = 9, itemCount = 20, threshold = 10))
    }

    @Test
    fun aNegativeThresholdCountsAsZero() {
        assertTrue(isNearEnd(lastVisibleIndex = 19, itemCount = 20, threshold = -3))
        assertFalse(isNearEnd(lastVisibleIndex = 18, itemCount = 20, threshold = -3))
    }

    // --- lists that are short ---

    @Test
    fun aListShorterThanThePageFillingTheScreenIsNearItsEndAtOnce() {
        // A page of 3 items that does not fill the screen: all of them are visible, the last one is the end.
        assertTrue(isNearEnd(lastVisibleIndex = 2, itemCount = 3, threshold = 5))
    }

    @Test
    fun aListOfOneItemIsNearItsEnd() {
        assertTrue(isNearEnd(lastVisibleIndex = 0, itemCount = 1, threshold = 5))
        assertTrue(isNearEnd(lastVisibleIndex = 0, itemCount = 1, threshold = 0))
    }

    @Test
    fun aListWithNoItemsHasNoEndToBeNear() {
        assertFalse(isNearEnd(lastVisibleIndex = -1, itemCount = 0, threshold = 5))
        assertFalse(isNearEnd(lastVisibleIndex = 0, itemCount = 0, threshold = 5))
    }

    @Test
    fun aListWithNoItemsHasNoEndWhateverTheThresholdIs() {
        assertFalse(isNearEnd(lastVisibleIndex = -1, itemCount = 0, threshold = 0))
        assertFalse(isNearEnd(lastVisibleIndex = -1, itemCount = 0, threshold = Int.MAX_VALUE))
        assertFalse(isNearEnd(lastVisibleIndex = -1, itemCount = -1, threshold = 5))
    }

    // --- nothing visible yet ---

    @Test
    fun noVisibleItemIsNearTheEndOnlyOfAListNoLongerThanThreshold() {
        // Before the first measuring a list shows no item: the end is not near for a long list ...
        assertFalse(isNearEnd(lastVisibleIndex = -1, itemCount = 30, threshold = 5))
        // ... and near for a short one, which would be shown whole.
        assertTrue(isNearEnd(lastVisibleIndex = -1, itemCount = 5, threshold = 5))
        assertFalse(isNearEnd(lastVisibleIndex = -1, itemCount = 6, threshold = 5))
    }

    // --- input outside the list ---

    @Test
    fun aLastVisibleIndexBeyondTheListIsNearTheEnd() {
        assertTrue(isNearEnd(lastVisibleIndex = 25, itemCount = 20, threshold = 5))
    }

    @Test
    fun extremeNumbersDoNotOverflow() {
        assertTrue(isNearEnd(lastVisibleIndex = Int.MAX_VALUE, itemCount = Int.MAX_VALUE, threshold = 0))
        assertFalse(isNearEnd(lastVisibleIndex = Int.MIN_VALUE, itemCount = Int.MAX_VALUE, threshold = 5))
        assertTrue(isNearEnd(lastVisibleIndex = 0, itemCount = Int.MAX_VALUE, threshold = Int.MAX_VALUE))
    }

    // --- walking down a list ---

    @Test
    fun scrollingDownAListOfTwentyAsksFromTheFifteenthItemOn() {
        val asked = (0 until 20).filter { last -> isNearEnd(last, itemCount = 20, threshold = 5) }

        assertEquals((14..19).toList(), asked)
    }

    @Test
    fun aPageThatArrivesMovesTheEndAwayAgain() {
        // The reader stays at the item 17 of 20: near the end. A page of 10 more arrives and the end is far again.
        assertTrue(isNearEnd(lastVisibleIndex = 17, itemCount = 20, threshold = 5))
        assertFalse(isNearEnd(lastVisibleIndex = 17, itemCount = 30, threshold = 5))
    }
}
