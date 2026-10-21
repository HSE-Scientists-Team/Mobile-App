package org.matchlab.academic_net.common.paging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The bounds of a page request and the rules by which a [Page] says whether the list goes on. */
class PageTest {
    private fun page(
        offset: Int = 0,
        limit: Int = 20,
        items: Int = limit,
        total: Int,
        received: Int = items,
    ): Page<Int> = Page(List(items) { offset + it }, total, PageRequest(offset, limit), received)

    // ---- PageRequest ----

    @Test
    fun aRequestWithoutArgumentsIsTheFirstPageOfTheDefaultSize() {
        assertEquals(PageRequest(offset = 0, limit = 20), PageRequest())
        assertEquals(PageRequest(0, PageRequest.DefaultLimit), PageRequest.First)
        assertEquals(20, PageRequest.DefaultLimit)
    }

    @Test
    fun theBoundsOfTheServerAreAllowed() {
        PageRequest(offset = 0, limit = 1)
        PageRequest(offset = PageRequest.MaxOffset, limit = PageRequest.MaxLimit)

        assertEquals(10_000, PageRequest.MaxOffset)
        assertEquals(100, PageRequest.MaxLimit)
    }

    @Test
    fun anOffsetOutsideZeroToTenThousandIsRefused() {
        assertFailsWith<IllegalArgumentException> { PageRequest(offset = -1) }
        assertFailsWith<IllegalArgumentException> { PageRequest(offset = 10_001) }
        assertFailsWith<IllegalArgumentException> { PageRequest(offset = Int.MIN_VALUE) }
        assertFailsWith<IllegalArgumentException> { PageRequest(offset = Int.MAX_VALUE) }
    }

    @Test
    fun aLimitOutsideOneToAHundredIsRefused() {
        assertFailsWith<IllegalArgumentException> { PageRequest(limit = 0) }
        assertFailsWith<IllegalArgumentException> { PageRequest(limit = -5) }
        assertFailsWith<IllegalArgumentException> { PageRequest(limit = 101) }
        assertFailsWith<IllegalArgumentException> { PageRequest(limit = Int.MAX_VALUE) }
    }

    // ---- Page: what it is made of ----

    @Test
    fun theNumberReceivedIsTheNumberOfItemsUnlessTold() {
        val page = Page(listOf("a", "b"), total = 10, request = PageRequest(0, 20))

        assertEquals(2, page.received)
    }

    @Test
    fun aNegativeTotalIsRefused() {
        assertFailsWith<IllegalArgumentException> { Page(listOf(1), total = -1, request = PageRequest.First) }
    }

    @Test
    fun fewerReceivedThanItemsIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            Page(listOf(1, 2, 3), total = 10, request = PageRequest.First, received = 2)
        }
    }

    @Test
    fun moreReceivedThanItemsIsAllowed() {
        // Two records the app could not read: they are not items but they were sent.
        val page = Page(listOf(1, 2, 3), total = 10, request = PageRequest.First, received = 5)

        assertEquals(3, page.items.size)
        assertEquals(5, page.received)
    }

    // ---- Page.hasMore ----

    @Test
    fun aPageInTheMiddleOfTheListHasMore() {
        assertTrue(page(offset = 0, items = 20, total = 45).hasMore)
    }

    @Test
    fun theListEndsWhenTheNextOffsetReachesTheTotal() {
        assertFalse(page(offset = 40, limit = 20, items = 5, total = 45).hasMore)
        assertFalse(page(offset = 0, limit = 20, items = 20, total = 20).hasMore)
    }

    @Test
    fun theListGoesOnWhileOneRecordIsLeft() {
        assertTrue(page(offset = 0, limit = 20, items = 20, total = 21).hasMore)
    }

    @Test
    fun anEmptyPageEndsTheListWhateverTheTotalSays() {
        assertFalse(page(offset = 0, items = 0, total = 50).hasMore)
        assertFalse(page(offset = 60, items = 0, total = 500).hasMore)
    }

    @Test
    fun recordsTheAppCouldNotReadStillMoveTheNextOffset() {
        // 3 items are readable, 20 were sent: the next page starts at 20, not at 3.
        val page = page(offset = 0, limit = 20, items = 3, total = 45, received = 20)

        assertEquals(20, page.nextOffset)
        assertTrue(page.hasMore)
        assertEquals(PageRequest(20, 20), page.next())
    }

    @Test
    fun aPageOfOnlyUnreadableRecordsIsNotAnEndOfTheList() {
        val page = page(offset = 0, limit = 20, items = 0, total = 45, received = 20)

        assertTrue(page.hasMore)
        assertEquals(20, page.nextOffset)
    }

    @Test
    fun unreadableRecordsCanEndTheListToo() {
        // 1 readable item, 4 sent, 4 in all: nothing is left.
        assertFalse(page(offset = 0, limit = 20, items = 1, total = 4, received = 4).hasMore)
    }

    @Test
    fun theListEndsWhereTheServerAcceptsNoMoreOffsets() {
        // The next offset would be 10 020, but the server takes 10 000 at most.
        assertFalse(page(offset = 10_000, limit = 20, items = 20, total = 50_000).hasMore)
        assertNull(page(offset = 10_000, limit = 20, items = 20, total = 50_000).next())
    }

    @Test
    fun theLastOffsetTheServerAcceptsIsStillAsked() {
        // 9 980 + 20 = 10 000 exactly: allowed.
        val page = page(offset = 9_980, limit = 20, items = 20, total = 50_000)

        assertTrue(page.hasMore)
        assertEquals(PageRequest(10_000, 20), page.next())
    }

    @Test
    fun oneOffsetBeyondTheLastAcceptedOneEndsTheList() {
        // 9 981 + 20 = 10 001: one too many.
        assertFalse(page(offset = 9_981, limit = 20, items = 20, total = 50_000).hasMore)
    }

    // ---- Page.next ----

    @Test
    fun theNextPageKeepsTheSizeOfThisOne() {
        assertEquals(PageRequest(offset = 5, limit = 5), page(offset = 0, limit = 5, items = 5, total = 12).next())
        assertEquals(PageRequest(offset = 100, limit = 100), page(offset = 0, limit = 100, items = 100, total = 300).next())
        assertEquals(PageRequest(offset = 1, limit = 1), page(offset = 0, limit = 1, items = 1, total = 3).next())
    }

    @Test
    fun thereIsNoNextPageAtTheEnd() {
        assertNull(page(offset = 0, limit = 20, items = 20, total = 20).next())
        assertNull(page(offset = 0, items = 0, total = 5).next())
    }

    // ---- Page.map ----

    @Test
    fun mappingKeepsTheTotalTheRequestAndTheNumberReceived() {
        val page = page(offset = 20, limit = 20, items = 3, total = 90, received = 7)

        val mapped = page.map { "id-$it" }

        assertEquals(listOf("id-20", "id-21", "id-22"), mapped.items)
        assertEquals(90, mapped.total)
        assertEquals(PageRequest(20, 20), mapped.request)
        assertEquals(7, mapped.received)
        assertEquals(page.hasMore, mapped.hasMore)
        assertEquals(page.next(), mapped.next())
    }
}
