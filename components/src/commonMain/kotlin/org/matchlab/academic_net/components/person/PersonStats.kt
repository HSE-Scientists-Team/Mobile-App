package org.matchlab.academic_net.components.person

import academicnet.components.generated.resources.Res
import academicnet.components.generated.resources.components_person_completed_projects
import academicnet.components.generated.resources.components_person_rating
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The line of numbers under a person's name on their card: "★ 4.9 · 12 завершённых проектов" |
 * "★ 4.9 · 12 completed projects". Without a [rating] (no reviews yet) only the count of
 * [completedProjects] is shown. The count has the right plural form for the app language.
 *
 * @param rating the average of reviews, 0..5, or `null` while there are none
 */
@Composable
fun personStatsText(rating: Double?, completedProjects: Int): String {
    val completed = pluralStringResource(
        Res.plurals.components_person_completed_projects,
        completedProjects,
        completedProjects,
    )
    return if (rating != null) {
        ratingText(rating) + " · " + completed
    } else {
        completed
    }
}

/**
 * The average of reviews on its own, with a star and one decimal digit: "★ 4.8". It is the text
 * of a person's stats line and of the heading of their reviews.
 *
 * @param rating the average of reviews, 0..5; a value outside is cut to the nearest bound
 */
@Composable
fun ratingText(rating: Double): String = stringResource(Res.string.components_person_rating, formatRating(rating))

/**
 * The average of reviews as a number alone, with one decimal digit and a point: "4.8". It is the big figure on the
 * summary of the reviews, where the stars stand under it; [ratingText] is the same with a star in front, for a line of
 * text. A value outside 0..5 is cut to the nearest bound.
 *
 * @param rating the average of reviews, 0..5
 */
fun formatRating(rating: Double): String {
    val tenths = (rating * 10).roundToInt().coerceIn(0, 50)
    return "${tenths / 10}.${tenths % 10}"
}
