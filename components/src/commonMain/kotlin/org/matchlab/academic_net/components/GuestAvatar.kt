package org.matchlab.academic_net.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The head against the side of the avatar: its radius, and where its centre sits from the top. */
private const val HeadRadius = 0.17f
private const val HeadCentreY = 0.38f

/** The shoulders are the top of a circle of this radius whose centre is below the disc, so the disc cuts it. */
private const val ShouldersRadius = 0.32f
private const val ShouldersCentreY = 0.98f

/**
 * "Avatar/Guest": what stands in the place of the avatar for a visitor who has not signed in: a head and shoulders on a
 * `primaryContainer` disc, in `onPrimaryContainer`, the colours of the initials avatar. Drawn from the side of the
 * avatar, so it keeps its proportions at every size (the design uses 24 to 64dp). Tapping it, where the screen makes it
 * tappable, opens the sign-in.
 */
@Composable
fun GuestAvatar(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val colors = MaterialTheme.colorScheme
    val background = colors.primaryContainer
    val content = colors.onPrimaryContainer
    val painter = remember(background, content) { GuestPainter(background, content) }
    Avatar(painter, contentDescription, modifier, size)
}

private class GuestPainter(private val background: Color, private val content: Color) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val side = size.minDimension
        drawCircle(color = background, radius = side / 2f, center = center)
        drawCircle(
            color = content,
            radius = side * HeadRadius,
            center = Offset(size.width / 2f, side * HeadCentreY),
        )
        val disc = Path().apply { addOval(Rect(center = center, radius = side / 2f)) }
        clipPath(disc) {
            drawCircle(
                color = content,
                radius = side * ShouldersRadius,
                center = Offset(size.width / 2f, side * ShouldersCentreY),
            )
        }
    }
}
