package org.matchlab.academic_net.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import org.matchlab.academic_net.components.person.initialsOf

/** The height of the letters against the side of the avatar: two capitals fit well inside the disc at this size. */
private const val LetterSizeToDisc = 0.4f

/** The line is as tall as the letters and they sit in the middle of it, so that the initials are centered in the disc. */
private val CenteredLine = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)

/**
 * What [Avatar] draws for a person who has no picture, while it loads and when it fails: the initials of [name]
 * ([initialsOf]) on a `primaryContainer` disc, in `onPrimaryContainer` and the `titleMedium` font, the letters
 * [LetterSizeToDisc] of the size of the avatar. A blank name gives the empty disc.
 *
 * It is a painter, not a composable, so that it is the placeholder of the picture loader and the picture replaces it
 * with no extra state.
 */
@Composable
internal fun rememberInitialsPainter(name: String): Painter {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    val initials = remember(name) { initialsOf(name) }
    val background = colors.primaryContainer
    val content = colors.onPrimaryContainer
    return remember(initials, background, content, style, measurer) {
        InitialsPainter(initials, background, content, style, measurer)
    }
}

private class InitialsPainter(
    private val initials: String,
    private val background: Color,
    private val content: Color,
    private val style: TextStyle,
    private val measurer: TextMeasurer,
) : Painter() {
    // No size of its own: the avatar sets it, and the painter fills it.
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawCircle(color = background, radius = size.minDimension / 2f, center = center)
        if (initials.isEmpty()) return
        // The letters keep their size whatever the font scale of the system is, as the avatar itself keeps its size.
        val fontSize = (size.minDimension * LetterSizeToDisc).toSp()
        val layout = measurer.measure(
            text = initials,
            style = style.copy(fontSize = fontSize, lineHeight = fontSize, lineHeightStyle = CenteredLine),
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
            density = this,
        )
        drawText(
            textLayoutResult = layout,
            color = content,
            topLeft = Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f),
        )
    }
}
