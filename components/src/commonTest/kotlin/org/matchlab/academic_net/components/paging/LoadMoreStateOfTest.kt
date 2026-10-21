package org.matchlab.academic_net.components.paging

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [loadMoreStateOf]: the end of a list from the three facts a view model keeps. A failure beats a page on its way, which
 * beats whether the server may have more; with none of the three the list is over.
 */
class LoadMoreStateOfTest {
    @Test
    fun aFailureWinsWhateverTheOtherTwoSay() {
        for (isLoadingMore in listOf(false, true)) {
            for (hasMore in listOf(false, true)) {
                assertEquals(
                    LoadMoreState.Failed,
                    loadMoreStateOf(isLoadingMore = isLoadingMore, failed = true, hasMore = hasMore),
                    "isLoadingMore=$isLoadingMore hasMore=$hasMore",
                )
            }
        }
    }

    @Test
    fun aPageOnItsWayWinsOverTheEndWhenNothingFailed() {
        assertEquals(LoadMoreState.Loading, loadMoreStateOf(isLoadingMore = true, failed = false, hasMore = true))
        assertEquals(LoadMoreState.Loading, loadMoreStateOf(isLoadingMore = true, failed = false, hasMore = false))
    }

    @Test
    fun withNothingOnTheWayMoreMeansIdle() {
        assertEquals(LoadMoreState.Idle, loadMoreStateOf(isLoadingMore = false, failed = false, hasMore = true))
    }

    @Test
    fun withNothingOnTheWayAndNoMoreTheListIsOver() {
        assertEquals(LoadMoreState.End, loadMoreStateOf(isLoadingMore = false, failed = false, hasMore = false))
    }

    @Test
    fun everyOneOfTheEightCombinationsHasTheStateOfTheTable() {
        val table = mapOf(
            Triple(true, true, true) to LoadMoreState.Failed,
            Triple(true, true, false) to LoadMoreState.Failed,
            Triple(true, false, true) to LoadMoreState.Failed,
            Triple(true, false, false) to LoadMoreState.Failed,
            Triple(false, true, true) to LoadMoreState.Loading,
            Triple(false, true, false) to LoadMoreState.Loading,
            Triple(false, false, true) to LoadMoreState.Idle,
            Triple(false, false, false) to LoadMoreState.End,
        )

        for ((facts, expected) in table) {
            val (failed, isLoadingMore, hasMore) = facts
            assertEquals(expected, loadMoreStateOf(isLoadingMore, failed, hasMore), "failed=$failed isLoadingMore=$isLoadingMore hasMore=$hasMore")
        }
    }

    @Test
    fun theArgumentsAreNotMixedUp() {
        // The three are Booleans of one type: the names, not the order, carry the meaning.
        assertEquals(LoadMoreState.Failed, loadMoreStateOf(isLoadingMore = false, failed = true, hasMore = false))
        assertEquals(LoadMoreState.Loading, loadMoreStateOf(isLoadingMore = true, failed = false, hasMore = false))
        assertEquals(LoadMoreState.Idle, loadMoreStateOf(isLoadingMore = false, failed = false, hasMore = true))
    }
}
