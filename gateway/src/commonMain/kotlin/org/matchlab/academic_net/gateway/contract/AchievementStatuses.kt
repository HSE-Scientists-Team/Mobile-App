package org.matchlab.academic_net.gateway.contract

/**
 * The values of `UserAchievement.status`, as the backend lists them in its OpenAPI (`AchievementStatus`, matchlab-backend
 * PR #16). The server writes the status itself; the app only reads it.
 */
object AchievementStatuses {
    /** Not sent for a check yet, or changed after the previous one. */
    const val Unverified = "unverified"

    /** Sent and waiting for a moderator. */
    const val PendingReview = "pending_review"

    /** Confirmed by a moderator. */
    const val Verified = "verified"

    /** Returned for more proof; the reason is in `moderation_comment`. */
    const val NeedsEvidence = "needs_evidence"
}
