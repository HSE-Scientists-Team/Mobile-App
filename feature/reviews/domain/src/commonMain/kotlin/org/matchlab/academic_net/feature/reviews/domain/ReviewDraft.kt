package org.matchlab.academic_net.feature.reviews.domain

import org.matchlab.academic_net.common.model.ProjectId

/**
 * The limits of a review. They are the backend's (`POST /users/me/reviews`): the title is 1..255 and the text 1..10000
 * characters (counted in Unicode code points, as the server counts them), the rating 1..5.
 */
object ReviewLimits {
    const val MaxTitle = 255
    const val MaxText = 10_000
    const val MinRating = 1
    const val MaxRating = 5
}

/** What is wrong with a [ReviewDraft]; each case is a field of the form (16). */
enum class ReviewDraftError {
    /** No project is chosen. */
    ProjectMissing,

    /** The rating is not in [ReviewLimits.MinRating]..[ReviewLimits.MaxRating] (0 is "not given yet"). */
    RatingMissing,

    /** The title is empty or blank. */
    TitleBlank,

    /** The title is longer than [ReviewLimits.MaxTitle]. */
    TitleTooLong,

    /** The comment is empty or blank. */
    TextBlank,

    /** The comment is longer than [ReviewLimits.MaxText]. */
    TextTooLong,
}

/**
 * The review form as the user filled it. Every field is required by the API.
 *
 * @property projectId the chosen common project; `null` while none is chosen.
 * @property rating the stars, [ReviewLimits.MinRating]..[ReviewLimits.MaxRating]; 0 while not given.
 * @property title the heading of the review.
 * @property text the comment.
 */
data class ReviewDraft(
    val projectId: ProjectId? = null,
    val rating: Int = 0,
    val title: String = "",
    val text: String = "",
) {
    /** The draft with the title and the text trimmed: what is validated and what is sent. */
    fun normalized(): ReviewDraft = copy(title = title.trim(), text = text.trim())

    /**
     * The length of the [normalized] title in Unicode code points, as the server counts it (an emoji outside the Basic
     * Multilingual Plane is one, not two); compared with [ReviewLimits.MaxTitle].
     */
    val titleLength: Int get() = title.trim().codePointCount()

    /**
     * The length of the [normalized] text in Unicode code points, as the server counts it; compared with
     * [ReviewLimits.MaxText].
     */
    val textLength: Int get() = text.trim().codePointCount()

    /** The problems of the [normalized] draft; an empty set means it can be sent. */
    fun validate(): Set<ReviewDraftError> {
        val draft = normalized()
        return buildSet {
            if (draft.projectId == null) add(ReviewDraftError.ProjectMissing)
            if (draft.rating !in ReviewLimits.MinRating..ReviewLimits.MaxRating) add(ReviewDraftError.RatingMissing)
            if (draft.title.isEmpty()) add(ReviewDraftError.TitleBlank)
            else if (draft.titleLength > ReviewLimits.MaxTitle) add(ReviewDraftError.TitleTooLong)
            if (draft.text.isEmpty()) add(ReviewDraftError.TextBlank)
            else if (draft.textLength > ReviewLimits.MaxText) add(ReviewDraftError.TextTooLong)
        }
    }
}

/**
 * The length in Unicode code points: a character outside the Basic Multilingual Plane (an emoji) is two UTF-16 units of a
 * Kotlin string but one character for the server, which counts runes. Counting units would refuse a text it accepts.
 */
private fun String.codePointCount(): Int {
    var count = 0
    var index = 0
    while (index < length) {
        val isPair = this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()
        index += if (isPair) 2 else 1
        count++
    }
    return count
}
