package org.matchlab.academic_net.common.model

/** The part of a user's profile shown next to their name: authors, project members, reviewers. */
data class UserSummary(
    val id: UserId,
    val name: String,
    /** Remote image; `null` until the user uploads one. */
    val avatarUrl: String? = null,
    /** The user's education documents and works have been checked by a moderator. */
    val isVerified: Boolean = false,
)
