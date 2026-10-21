package org.matchlab.academic_net.feature.reviews.domain

import kotlin.time.Instant
import org.matchlab.academic_net.common.model.ProjectId

/**
 * A project the viewer and another person completed together and the viewer has not reviewed that person in yet: one
 * line of the project picker of the review form (16).
 *
 * @property id the project the review is about; it goes to the server as `project_id`.
 * @property title the project's name as its initiator wrote it.
 * @property completedAt when the project was completed; `null` when the server did not say (the form then shows the
 *  project without a date). The date is for the UI to format, no text is made here.
 */
data class ReviewableProject(val id: ProjectId, val title: String, val completedAt: Instant?)

/**
 * Why the server refused a review that passed [ReviewDraft.validate]. The screen shows a message for each case; the text
 * the server sent with the refusal is never part of it, because it is a diagnostic for developers.
 *
 * Any other failure of sending (no connection, a timeout, a 5xx, ...) is not a refusal: it comes as the
 * [org.matchlab.academic_net.common.result.RemoteFailure] it is, and sending can be repeated.
 */
sealed class ReviewRefusal(message: String, cause: Throwable?) : Exception(message, cause) {
    /** The viewer has already reviewed this person for this project (HTTP 409): one review per project, author and target. */
    class AlreadyReviewed(cause: Throwable? = null) : ReviewRefusal("The person is already reviewed for this project", cause)

    /**
     * The server does not let the viewer review this person for this project (HTTP 403, 404, or 400): the project is
     * not completed (yet), one of them was not on its team, or the project or the person is not available to the viewer.
     */
    class NotAllowed(cause: Throwable? = null) : ReviewRefusal("The review is not allowed for this project and person", cause)
}
