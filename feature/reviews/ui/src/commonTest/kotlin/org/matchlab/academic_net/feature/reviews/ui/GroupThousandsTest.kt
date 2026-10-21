package org.matchlab.academic_net.feature.reviews.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** The counters under the fields write a number as the design does: groups of three apart by a no-break space. */
class GroupThousandsTest {
    private val nbsp = " "

    @Test
    fun aNumberUnderAThousandIsAsItIs() {
        assertEquals("0", groupThousands(0))
        assertEquals("7", groupThousands(7))
        assertEquals("38", groupThousands(38))
        assertEquals("255", groupThousands(255))
        assertEquals("999", groupThousands(999))
    }

    @Test
    fun theLimitOfTheCommentIsTenThousandWithANoBreakSpace() {
        assertEquals("10${nbsp}000", groupThousands(10_000))
    }

    @Test
    fun theGroupsAreCountedFromTheRight() {
        assertEquals("1${nbsp}000", groupThousands(1_000))
        assertEquals("1${nbsp}234", groupThousands(1_234))
        assertEquals("12${nbsp}345", groupThousands(12_345))
        assertEquals("123${nbsp}456", groupThousands(123_456))
        assertEquals("1${nbsp}234${nbsp}567", groupThousands(1_234_567))
        assertEquals("2${nbsp}147${nbsp}483${nbsp}647", groupThousands(Int.MAX_VALUE))
    }

    @Test
    fun theSeparatorIsTheNoBreakSpaceNotAnOrdinaryOne() {
        assertEquals(1, groupThousands(10_000).count { it == ' ' })
        assertEquals(0, groupThousands(10_000).count { it == ' ' })
    }
}
