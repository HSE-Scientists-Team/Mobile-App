package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_rating_value
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

/**
 * The two sizes of [RatingStars]; both draw five stars.
 *
 * @property star side of one star
 * @property gap space between two stars
 */
enum class RatingStarsSize(val star: Dp, val gap: Dp) {
    /** 18dp stars 2dp apart, 98dp wide in all: review cards and lists. */
    Small(18.dp, 2.dp),

    /** 24dp stars 4dp apart, 136dp wide in all: the page of a review and the summary of the reviews. */
    Medium(24.dp, 4.dp),
}

/** The stars in a rating: a rating is a mark from 0 to this. */
private const val StarCount = 5

/**
 * "Rating": the mark of a review as stars. [value] of the five are filled (`primary`, the filled
 * [AppIcons.StarFilled]), the rest are only outlined (`outline`, [AppIcons.Star]); both keep the 2dp stroke
 * at either size ([size] gives the star and the gap).
 *
 * A [value] outside 0..5 is limited to the nearest bound instead of failing, so a wrong number from the
 * server cannot draw six stars or none. For a screen reader the five stars are one element that says
 * "Оценка N из 5" with the limited number. The number itself ("5", "4.8") is not drawn here: the screen
 * puts it in a text next to the stars.
 *
 * @param value how many stars are filled: the mark, a whole number 0..5
 */
@Composable
fun RatingStars(
    value: Int,
    modifier: Modifier = Modifier,
    size: RatingStarsSize = RatingStarsSize.Small,
) {
    val colors = MaterialTheme.colorScheme
    val filled = filledStars(value)
    val description = stringResource(Res.string.components_rating_value, filled)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(size.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(StarCount) { index ->
            val earned = index < filled
            AppIcon(
                icon = if (earned) AppIcons.StarFilled else AppIcons.Star,
                contentDescription = null,
                size = size.star,
                tint = if (earned) colors.primary else colors.outline,
            )
        }
    }
}

/** How many of the stars of [RatingStars] are filled for [value]: the value limited to `0..5`. */
internal fun filledStars(value: Int): Int = value.coerceIn(0, StarCount)
