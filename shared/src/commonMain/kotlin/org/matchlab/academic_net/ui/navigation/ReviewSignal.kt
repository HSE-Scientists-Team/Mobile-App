package org.matchlab.academic_net.ui.navigation

/**
 * Whether the profile of [profileId] has to show the banner "Отзыв отправлен" (09s) and read its reviews again: a review
 * was sent after this profile was opened ([reviewsSent] has grown past [reviewsSentAtOpen], the count of reviews sent
 * when the profile was opened) and the last one sent is about this person ([lastReviewedId]). A review of somebody else,
 * sent from a profile further up the stack, tells this profile nothing.
 */
internal fun isReviewJustSentTo(profileId: String, reviewsSent: Int, reviewsSentAtOpen: Int, lastReviewedId: String?): Boolean =
    reviewsSent > reviewsSentAtOpen && lastReviewedId == profileId
