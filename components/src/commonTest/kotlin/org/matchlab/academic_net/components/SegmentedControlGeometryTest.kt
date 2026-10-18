package org.matchlab.academic_net.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The geometry of the sliding pill of [AppSegmentedControl]. The numbers of the first tests are the
 * ones of the Figma component: a 370dp track, 4dp padding, two segments of 181dp, the pill at x = 4
 * (left selected) or x = 185 (right selected).
 */
class SegmentedControlGeometryTest {
    private fun span(
        trackWidth: Float = 370f,
        padding: Float = 4f,
        segments: Int = 2,
        position: Float = 0f,
        rtl: Boolean = false,
    ) = segmentedPillSpan(trackWidth, padding, segments, position, rtl)

    private fun assertSpan(left: Float, width: Float, actual: PillSpan) {
        assertEquals(left, actual.left, 0.001f, "left of $actual")
        assertEquals(width, actual.width, 0.001f, "width of $actual")
    }

    // --- two segments, the Figma component ---

    @Test
    fun leftSegmentOfTheFigmaTrack() = assertSpan(left = 4f, width = 181f, span(position = 0f))

    @Test
    fun rightSegmentOfTheFigmaTrack() = assertSpan(left = 185f, width = 181f, span(position = 1f))

    @Test
    fun pillWidthDoesNotChangeWhileItSlides() {
        val widths = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { span(position = it).width }

        assertEquals(listOf(181f, 181f, 181f, 181f, 181f), widths)
    }

    @Test
    fun pillSlidesLinearlyBetweenTheSegments() {
        val lefts = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { span(position = it).left }

        assertEquals(listOf(4f, 49.25f, 94.5f, 139.75f, 185f), lefts)
    }

    @Test
    fun pillStaysInsideThePaddingsAtBothEnds() {
        val first = span(position = 0f)
        val last = span(position = 1f)

        assertEquals(4f, first.left)
        assertEquals(370f - 4f, last.left + last.width)
    }

    // --- three and more segments ---

    @Test
    fun threeSegmentsAreThirdsOfTheRoomInsideThePaddings() {
        // 368 - 2 * 4 = 360, a third is 120.
        assertSpan(left = 4f, width = 120f, span(trackWidth = 368f, segments = 3, position = 0f))
        assertSpan(left = 124f, width = 120f, span(trackWidth = 368f, segments = 3, position = 1f))
        assertSpan(left = 244f, width = 120f, span(trackWidth = 368f, segments = 3, position = 2f))
    }

    @Test
    fun jumpAcrossTwoSegmentsPassesTheMiddleOne() {
        // The animated position of a jump from the first to the third segment goes through 1.
        assertSpan(left = 124f, width = 120f, span(trackWidth = 368f, segments = 3, position = 1f))
        assertSpan(left = 64f, width = 120f, span(trackWidth = 368f, segments = 3, position = 0.5f))
    }

    @Test
    fun fourSegmentsOfAnotherPadding() {
        // (300 - 2 * 6) / 4 = 72.
        assertSpan(left = 6f + 72f * 3, width = 72f, span(trackWidth = 300f, padding = 6f, segments = 4, position = 3f))
    }

    @Test
    fun oneSegmentFillsTheRoom() {
        assertSpan(left = 4f, width = 362f, span(segments = 1, position = 0f))
    }

    // --- right to left ---

    @Test
    fun rtlMirrorsTwoSegments() {
        assertSpan(left = 185f, width = 181f, span(position = 0f, rtl = true))
        assertSpan(left = 4f, width = 181f, span(position = 1f, rtl = true))
    }

    @Test
    fun rtlMirrorsThreeSegments() {
        assertSpan(left = 244f, width = 120f, span(trackWidth = 368f, segments = 3, position = 0f, rtl = true))
        assertSpan(left = 124f, width = 120f, span(trackWidth = 368f, segments = 3, position = 1f, rtl = true))
        assertSpan(left = 4f, width = 120f, span(trackWidth = 368f, segments = 3, position = 2f, rtl = true))
    }

    @Test
    fun rtlPillSlidesTheOtherWay() {
        val lefts = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { span(position = it, rtl = true).left }

        assertEquals(listOf(185f, 139.75f, 94.5f, 49.25f, 4f), lefts)
    }

    @Test
    fun rtlPillIsTheMirrorImageOfTheLtrOne() {
        listOf(0f, 0.3f, 1f, 1.5f, 2f).forEach { position ->
            val ltr = span(trackWidth = 368f, segments = 3, position = position)
            val rtl = span(trackWidth = 368f, segments = 3, position = position, rtl = true)

            // The distance from the left edge of one equals the distance from the right edge of the other.
            assertEquals(368f - (ltr.left + ltr.width), rtl.left, 0.001f, "position $position")
            assertEquals(ltr.width, rtl.width, 0.001f)
        }
    }

    @Test
    fun oneSegmentIsTheSameInBothDirections() {
        assertEquals(span(segments = 1, position = 0f), span(segments = 1, position = 0f, rtl = true))
    }

    // --- positions outside the segments ---

    @Test
    fun negativePositionIsLimitedToTheFirstSegment() {
        assertEquals(span(position = 0f), span(position = -1f))
        assertEquals(span(position = 0f, rtl = true), span(position = -3f, rtl = true))
    }

    @Test
    fun positionPastTheLastSegmentIsLimitedToTheLastSegment() {
        assertEquals(span(position = 1f), span(position = 2f))
        assertEquals(span(segments = 3, position = 2f), span(segments = 3, position = 7.5f))
        assertEquals(span(position = 1f, rtl = true), span(position = 5f, rtl = true))
    }

    // --- degenerate input ---

    @Test
    fun zeroWidthTrackGivesAnEmptyPill() {
        val pill = span(trackWidth = 0f, position = 1f)

        assertEquals(0f, pill.width)
        assertEquals(4f, pill.left)
    }

    @Test
    fun trackNarrowerThanItsPaddingsGivesAnEmptyPill() {
        assertEquals(0f, span(trackWidth = 6f).width)
        assertEquals(0f, span(trackWidth = 8f).width)
    }

    @Test
    fun noPaddingMeansSegmentsOfTheWholeTrack() {
        assertSpan(left = 0f, width = 185f, span(padding = 0f, position = 0f))
        assertSpan(left = 185f, width = 185f, span(padding = 0f, position = 1f))
    }

    @Test
    fun noSegmentsGiveAnEmptyPill() {
        assertEquals(0f, span(segments = 0).width)
        assertEquals(0f, span(segments = -1).width)
    }

    // --- the selected segment ---

    @Test
    fun selectedIndexInsideTheSegmentsIsKept() {
        assertEquals(0, markedSegment(0, segmentCount = 2))
        assertEquals(1, markedSegment(1, segmentCount = 2))
        assertEquals(2, markedSegment(2, segmentCount = 3))
    }

    @Test
    fun selectedIndexOutsideTheSegmentsIsLimitedToTheNearestOne() {
        assertEquals(0, markedSegment(-1, segmentCount = 2))
        assertEquals(1, markedSegment(2, segmentCount = 2))
        assertEquals(2, markedSegment(Int.MAX_VALUE, segmentCount = 3))
        assertEquals(0, markedSegment(Int.MIN_VALUE, segmentCount = 3))
    }

    @Test
    fun noSegmentsMarkTheFirstIndex() {
        assertEquals(0, markedSegment(0, segmentCount = 0))
        assertEquals(0, markedSegment(5, segmentCount = 0))
        assertEquals(0, markedSegment(-5, segmentCount = 0))
    }
}
