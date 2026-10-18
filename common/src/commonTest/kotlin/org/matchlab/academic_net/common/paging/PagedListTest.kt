package org.matchlab.academic_net.common.paging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pages of one list joined: no repeats by key, the server's order, the request for the next page. */
class PagedListTest {
    private data class Row(val id: String, val note: String = "")

    private val key: (Row) -> Any = { it.id }

    private fun rows(vararg ids: String): List<Row> = ids.map { Row(it) }

    private fun page(
        offset: Int,
        limit: Int,
        ids: List<String>,
        total: Int,
        received: Int = ids.size,
    ): Page<Row> = Page(rows(*ids.toTypedArray()), total, PageRequest(offset, limit), received)

    // ---- the start ----

    @Test
    fun aNewListIsEmptyAndAsksForTheFirstPage() {
        val list = PagedList<Row>()

        assertEquals(emptyList(), list.items)
        assertEquals(0, list.total)
        assertEquals(PageRequest.First, list.next)
        assertTrue(list.hasMore)
    }

    @Test
    fun theFirstPageBecomesTheListAndSaysWhatToAskNext() {
        val list = PagedList.of(page(offset = 0, limit = 3, ids = listOf("a", "b", "c"), total = 7), key)

        assertEquals(rows("a", "b", "c"), list.items)
        assertEquals(7, list.total)
        assertEquals(PageRequest(3, 3), list.next)
        assertTrue(list.hasMore)
    }

    @Test
    fun anEmptyFirstPageEndsTheListAndKeepsTheServersTotal() {
        val list = PagedList.of(page(offset = 0, limit = 3, ids = emptyList(), total = 5), key)

        assertEquals(emptyList(), list.items)
        assertEquals(5, list.total)
        assertNull(list.next)
        assertFalse(list.hasMore)
    }

    // ---- repeats ----

    @Test
    fun aRepeatInsideOnePageIsDroppedAndTheOrderStays() {
        val list = PagedList.of(page(offset = 0, limit = 5, ids = listOf("a", "b", "a", "c", "b"), total = 5), key)

        assertEquals(rows("a", "b", "c"), list.items)
    }

    @Test
    fun aRepeatFromAnEarlierPageIsDropped() {
        // The list shifted by one between the two requests: "c" came at the end of the first page and at the start of the second.
        val first = PagedList.of(page(offset = 0, limit = 3, ids = listOf("a", "b", "c"), total = 6), key)

        val second = first.append(page(offset = 3, limit = 3, ids = listOf("c", "d", "e"), total = 6), key)

        assertEquals(rows("a", "b", "c", "d", "e"), second.items)
    }

    @Test
    fun theNewRecordsFollowTheOldOnesInTheOrderTheyCame() {
        val first = PagedList.of(page(offset = 0, limit = 2, ids = listOf("z", "m"), total = 4), key)

        val second = first.append(page(offset = 2, limit = 2, ids = listOf("b", "a"), total = 4), key)

        assertEquals(rows("z", "m", "b", "a"), second.items)
    }

    @Test
    fun theKeyDecidesWhatARepeatIsAndTheFirstRecordStays() {
        val first = PagedList.of(Page(listOf(Row("a", "old")), 4, PageRequest(0, 2), received = 2), key)

        val second = first.append(Page(listOf(Row("a", "new"), Row("b")), 4, PageRequest(2, 2)), key)

        assertEquals(listOf(Row("a", "old"), Row("b")), second.items)
    }

    @Test
    fun aPageOfOnlyRepeatsStillMovesTheNextRequest() {
        val first = PagedList.of(page(offset = 0, limit = 2, ids = listOf("a", "b"), total = 6), key)

        val second = first.append(page(offset = 2, limit = 2, ids = listOf("a", "b"), total = 6), key)

        assertEquals(rows("a", "b"), second.items)
        assertEquals(PageRequest(4, 2), second.next)
        assertTrue(second.hasMore)
    }

    @Test
    fun aPageOfOnlyRepeatsAtTheEndEndsTheList() {
        val first = PagedList.of(page(offset = 0, limit = 2, ids = listOf("a", "b"), total = 4), key)

        val second = first.append(page(offset = 2, limit = 2, ids = listOf("a", "b"), total = 4), key)

        assertEquals(rows("a", "b"), second.items)
        assertNull(second.next)
    }

    // ---- the total ----

    @Test
    fun theTotalIsNeverLessThanTheRecordsHeld() {
        // The server said 1 but sent 3 different records: the list cannot be shorter than what it holds.
        val list = PagedList.of(Page(rows("a", "b", "c"), total = 1, request = PageRequest(0, 3), received = 3), key)

        assertEquals(3, list.total)
    }

    @Test
    fun theTotalIsWhatTheLastPageSaid() {
        val first = PagedList.of(page(offset = 0, limit = 2, ids = listOf("a", "b"), total = 50), key)

        val second = first.append(page(offset = 2, limit = 2, ids = listOf("c", "d"), total = 40), key)

        assertEquals(40, second.total)
    }

    // ---- the end ----

    @Test
    fun theLastPageLeavesNoNextRequest() {
        val first = PagedList.of(page(offset = 0, limit = 3, ids = listOf("a", "b", "c"), total = 4), key)

        val second = first.append(page(offset = 3, limit = 3, ids = listOf("d"), total = 4), key)

        assertEquals(rows("a", "b", "c", "d"), second.items)
        assertNull(second.next)
        assertFalse(second.hasMore)
    }

    @Test
    fun anEmptyPageInTheMiddleEndsTheList() {
        val first = PagedList.of(page(offset = 0, limit = 3, ids = listOf("a", "b", "c"), total = 9), key)

        val second = first.append(page(offset = 3, limit = 3, ids = emptyList(), total = 9), key)

        assertEquals(rows("a", "b", "c"), second.items)
        assertNull(second.next)
    }

    @Test
    fun unreadableRecordsMoveTheNextRequestPastThem() {
        // 3 records were sent, one of them could not be read: the next page starts at 3.
        val list = PagedList.of(page(offset = 0, limit = 3, ids = listOf("a", "c"), total = 9, received = 3), key)

        assertEquals(PageRequest(3, 3), list.next)
    }

    @Test
    fun theOffsetLimitOfTheServerEndsTheList() {
        val last = page(offset = 10_000, limit = 20, ids = (1..20).map { "r$it" }, total = 50_000)

        val list = PagedList<Row>().append(last, key)

        assertNull(list.next)
    }

    // ---- the list is a value ----

    @Test
    fun appendingLeavesTheOldListAsItWas() {
        val first = PagedList.of(page(offset = 0, limit = 2, ids = listOf("a", "b"), total = 4), key)

        first.append(page(offset = 2, limit = 2, ids = listOf("c", "d"), total = 4), key)

        assertEquals(rows("a", "b"), first.items)
        assertEquals(PageRequest(2, 2), first.next)
    }
}
