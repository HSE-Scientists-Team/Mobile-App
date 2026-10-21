package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.mapping.pageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [pageOf]: the size of the whole list is what the server said, but never less than what has been received. */
class PageMappingTest {
    @Test
    fun theItemsTheRequestAndTheNumberReceivedAreKept() {
        val request = PageRequest(offset = 20, limit = 20)

        val page = pageOf(items = listOf("a", "b"), received = 5, reportedTotal = 100, request = request)

        assertEquals(listOf("a", "b"), page.items)
        assertEquals(5, page.received)
        assertEquals(request, page.request)
        assertEquals(100, page.total)
    }

    @Test
    fun theReportedTotalIsUsedWhenItIsAtLeastWhatHasBeenReceived() {
        assertEquals(45, pageOf(List(20) { it }, 20, 45, PageRequest(0, 20)).total)
        // Exactly the end of this page.
        assertEquals(40, pageOf(List(20) { it }, 20, 40, PageRequest(20, 20)).total)
    }

    @Test
    fun aMissingTotalIsTheEndOfThePage() {
        // Protobuf JSON leaves a zero out: the page is all that is known.
        assertEquals(20, pageOf(List(20) { it }, 20, 0, PageRequest(0, 20)).total)
        assertEquals(40, pageOf(List(20) { it }, 20, 0, PageRequest(20, 20)).total)
    }

    @Test
    fun aNegativeTotalIsTheEndOfThePage() {
        assertEquals(23, pageOf(List(3) { it }, 3, -1, PageRequest(20, 20)).total)
        assertEquals(23, pageOf(List(3) { it }, 3, Long.MIN_VALUE, PageRequest(20, 20)).total)
    }

    @Test
    fun aTotalSmallerThanWhatHasBeenReceivedIsTheEndOfThePage() {
        assertEquals(23, pageOf(List(3) { it }, 3, 2, PageRequest(20, 20)).total)
    }

    @Test
    fun theRecordsTheAppCouldNotReadAreCountedInTheTotal() {
        // 2 readable, 7 received at offset 20: at least 27 exist.
        val page = pageOf(items = listOf("a", "b"), received = 7, reportedTotal = 0, request = PageRequest(20, 20))

        assertEquals(27, page.total)
        assertEquals(27, page.nextOffset)
        assertFalse(page.hasMore)
    }

    @Test
    fun aTotalBeyondIntIsCutToIt() {
        assertEquals(Int.MAX_VALUE, pageOf(listOf(1), 1, Int.MAX_VALUE.toLong() + 1, PageRequest.First).total)
        assertEquals(Int.MAX_VALUE, pageOf(listOf(1), 1, Long.MAX_VALUE, PageRequest.First).total)
        assertEquals(Int.MAX_VALUE, pageOf(listOf(1), 1, Int.MAX_VALUE.toLong(), PageRequest.First).total)
    }

    @Test
    fun theSumOfTheOffsetAndTheReceivedIsCountedInLong() {
        // 10 000 + 2 147 483 000 does not fit into an Int; the total must not become negative.
        val page = pageOf(emptyList<Int>(), received = 2_147_483_000, reportedTotal = 0, request = PageRequest(10_000, 20))

        assertEquals(Int.MAX_VALUE, page.total)
        assertTrue(page.total >= 0)
    }

    @Test
    fun fewerReceivedThanItemsIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            pageOf(items = listOf(1, 2, 3), received = 2, reportedTotal = 10, request = PageRequest.First)
        }
    }

    @Test
    fun anEmptyPageAtTheStartHasNothing() {
        val page = pageOf(emptyList<Int>(), 0, 0, PageRequest.First)

        assertEquals(0, page.total)
        assertFalse(page.hasMore)
    }

    @Test
    fun aPageInTheMiddleOfTheListGoesOnToTheNextOne() {
        val page = pageOf(List(20) { it }, 20, 45, PageRequest(20, 20))

        assertTrue(page.hasMore)
        assertEquals(PageRequest(40, 20), page.next())
    }
}
