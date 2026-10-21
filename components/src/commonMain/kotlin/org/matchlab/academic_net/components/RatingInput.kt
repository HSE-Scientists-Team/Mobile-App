package org.matchlab.academic_net.components

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_rating_value
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

/** The side of the cell of one star of [RatingInput]: the touch target of a star. */
private val RatingInputCell = 40.dp

/** The side of a star of [RatingInput]; the cell leaves 4dp of room around it. */
private val RatingInputStar = 32.dp

/**
 * "Rating/Input": the stars that take a mark, for the form of a review (the display-only stars are [RatingStars]).
 * Five cells of 40dp in a row with no gap, a star of 32dp (2dp stroke, see [AppIcon]) in the middle of each. The first
 * [value] stars are filled and the rest are only outlined, both in `primary`.
 *
 * A tap on a star sets the mark to its number; a tap on the star that is already the mark clears it (the mark goes back
 * to 0, "not given"). The stars are meant to be tapped quickly one after another, so a burst of taps is not
 * debounced. [value] is limited to 0..5 like in [RatingStars].
 *
 * The row is 200dp wide and each star sits 4dp inside its cell, so a screen that lines the first star up with the
 * left edge of the fields shifts the row by `Modifier.offset(x = (-4).dp)` (the design does the same).
 *
 * For a screen reader every cell is a radio button of a group, the chosen one is selected, and each says
 * "Оценка N из 5" with its own number. The error "Поставьте оценку" is not drawn here: the screen puts it under the
 * stars.
 *
 * @param value the current mark, a whole number 0..5; 0 for none
 * @param onValueChange the new mark: the number of the tapped star, or 0 for a tap on the chosen one
 * @param enabled `false` while the form is busy: the stars take no taps and are drawn as they are (the screen dims
 * them if it wants to)
 */
@Composable
fun RatingInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tint = MaterialTheme.colorScheme.primary
    val filled = filledStars(value)
    Row(modifier.selectableGroup()) {
        repeat(StarCount) { index ->
            val mark = index + 1
            val chosen = mark == filled
            val description = stringResource(Res.string.components_rating_value, mark)
            Box(
                Modifier
                    .size(RatingInputCell)
                    .clip(CircleShape)
                    .selectable(
                        selected = chosen,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onValueChange(ratingAfterTap(value, mark)) },
                    )
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    icon = if (mark <= filled) AppIcons.StarFilled else AppIcons.Star,
                    contentDescription = null,
                    size = RatingInputStar,
                    tint = tint,
                )
            }
        }
    }
}

/**
 * The mark [RatingInput] reports for a tap on the star number [tapped] (1..5) while the mark is [value]: [tapped], or 0
 * for a tap on the star that is already the mark (the mark is taken back). A [value] outside 0..5 is limited first, as the
 * stars are drawn: with 7 the fifth star is the chosen one.
 */
internal fun ratingAfterTap(value: Int, tapped: Int): Int = if (tapped == filledStars(value)) 0 else tapped
