package org.matchlab.academic_net.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Space between the track and the pill, on every side. */
private val TrackPadding = 4.dp

/** The pill slides and the labels change color for as long as the "Smart animate" of the design. */
private const val SlideDurationMillis = 250

/**
 * The standard Material curve (FastOutSlowIn). The transition of the design says "ease in-out"; this curve
 * is close to it, only a little quicker at the start, and it is the one the Toggle of Material uses.
 */
private fun <T> slideSpec(): TweenSpec<T> = tween(durationMillis = SlideDurationMillis, easing = FastOutSlowInEasing)

/**
 * "Segmented control" (the "search-scope" of the feed): a full-round track with equal segments and one
 * white pill under the label of the selected segment. When the selection changes the pill slides to
 * the new segment while the label colors cross-fade, both over 250 ms (FastOutSlowIn, see [slideSpec]),
 * as the "Smart animate" of the design.
 *
 * Sizes (Figma, 370dp wide): the track is 42dp tall (`surfaceContainer`, 4dp inside), a segment is
 * 34dp tall (8dp above and below the 18dp line of the 13sp label), the pill is `surface` and exactly
 * as big as one segment: 34dp tall and `(width - 2 * 4dp) / options.size` wide. The selected label is
 * `primary`, the others `onSurfaceVariant`. The height follows the label line, so the pill grows with a
 * larger system font instead of being cut by the segment.
 *
 * Works for any number of segments and for right-to-left layouts: there the first option is on the
 * right and the pill slides the other way (see [segmentedPillSpan]). A tap that comes while the pill is
 * still moving sends it from where it is to the new segment, so quick taps never make it jump. Taps are
 * not debounced: this is a selection, not an action.
 *
 * For accessibility every segment is a selectable tab of one selectable group.
 *
 * @param options the labels, one line each; a label that does not fit is ellipsized
 * @param selectedIndex the index of the selected option. It should be a valid index; an index outside
 * the options is limited to the nearest one instead of failing, and with no options only the empty track is
 * drawn, without a pill
 * @param onSelect called with the index of the tapped option, also for the one already selected
 */
@Composable
fun AppSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val selectedSegment = markedSegment(selectedIndex, options.size)
    // The position of the pill is the index of the segment it stands on; a fraction while it moves.
    // The state is read only when drawing, so a slide redraws the track but does not recompose it.
    val pillPosition by animateFloatAsState(targetValue = selectedSegment.toFloat(), animationSpec = slideSpec())
    val pillColor = colors.surface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, CircleShape)
            .drawBehind {
                // Whole pixels, like `padding(TrackPadding)` below: the pill must sit exactly on the segments.
                val padding = TrackPadding.roundToPx().toFloat()
                val span = segmentedPillSpan(size.width, padding, options.size, pillPosition, isRtl)
                if (span.width > 0f) {
                    val height = size.height - 2 * padding
                    drawRoundRect(
                        color = pillColor,
                        topLeft = Offset(span.left, padding),
                        size = Size(span.width, height),
                        cornerRadius = CornerRadius(height / 2),
                    )
                }
            }
            .padding(TrackPadding)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, title ->
            Segment(
                title = title,
                selected = index == selectedSegment,
                onClick = { onSelect(index) },
            )
        }
    }
}

/** One segment: transparent, the pill under it belongs to the track. */
@Composable
private fun RowScope.Segment(title: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onSurfaceVariant,
        animationSpec = slideSpec(),
    )
    Box(
        modifier = Modifier
            .weight(1f)
            // Keeps the ripple of a press inside the round segment.
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Where the pill of [AppSegmentedControl] is, in pixels from the left edge of the track.
 *
 * @property left x of the left edge of the pill
 * @property width width of the pill; `0` when there is nothing to draw
 */
internal data class PillSpan(val left: Float, val width: Float)

/**
 * The index of the segment [AppSegmentedControl] marks as selected: [selectedIndex] limited to the
 * existing segments `0 until segmentCount`, and `0` when there are none.
 */
internal fun markedSegment(selectedIndex: Int, segmentCount: Int): Int =
    selectedIndex.coerceIn(0, (segmentCount - 1).coerceAtLeast(0))

/**
 * Placement of the pill of [AppSegmentedControl] while it stands on [position].
 *
 * The room for the segments is the track without [padding] on both sides, split into [segmentCount]
 * equal parts, and the pill is as wide as one part: `(trackWidth - 2 * padding) / segmentCount`. With
 * the 370dp track of the design and two segments that is 181dp, at x = 4dp for the first one and
 * 185dp for the second.
 *
 * [position] is the index of the segment the pill is on; between two indexes (the pill is moving) it
 * is a fraction, and the pill is between the segments linearly, so animating [position] from one index
 * to another slides the pill. A [position] outside `0..segmentCount - 1` is limited to that range.
 * In a right-to-left layout the first segment is the rightmost, so the pill is mirrored: index `0` is
 * at the right end and growing indexes go left.
 *
 * Degenerate input does not fail: a track not wider than its paddings, or no segments, give a pill of
 * width `0` (nothing to draw).
 *
 * @param trackWidth width of the whole track, paddings included
 * @param padding space between the track and the segments on each side
 * @param segmentCount number of segments
 * @param position index of the segment under the pill, may be fractional
 * @param isRtl whether the segments go from right to left
 */
internal fun segmentedPillSpan(
    trackWidth: Float,
    padding: Float,
    segmentCount: Int,
    position: Float,
    isRtl: Boolean,
): PillSpan {
    if (segmentCount <= 0) return PillSpan(left = padding, width = 0f)
    val segmentWidth = (trackWidth - 2 * padding).coerceAtLeast(0f) / segmentCount
    val lastIndex = (segmentCount - 1).toFloat()
    val index = position.coerceIn(0f, lastIndex)
    val visualIndex = if (isRtl) lastIndex - index else index
    return PillSpan(left = padding + visualIndex * segmentWidth, width = segmentWidth)
}
