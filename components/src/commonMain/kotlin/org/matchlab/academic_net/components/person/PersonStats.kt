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
        stringResource(Res.string.components_person_rating, formatRating(rating)) + " · " + completed
    } else {
        completed
    }
}

/** One decimal digit, with a point: 4.8. A value outside 0..5 is cut to the nearest bound. */
internal fun formatRating(rating: Double): String {
    val tenths = (rating * 10).roundToInt().coerceIn(0, 50)
    return "${tenths / 10}.${tenths % 10}"
}
