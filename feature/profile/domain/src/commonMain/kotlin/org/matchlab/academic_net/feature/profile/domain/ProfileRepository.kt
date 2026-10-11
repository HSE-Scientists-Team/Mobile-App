package org.matchlab.academic_net.feature.profile.domain

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.UserId

/**
 * Reading the profile of any person, the viewer or someone else. The server decides what the
 * viewer may see (for example, other people's pending and rejected works look unconfirmed); calls
 * throw on failure (e.g. no network) and the use cases turn that into a result.
 */
interface ProfileRepository {
    /** The profile of [id], or `null` when there is no such person. */
    suspend fun getProfile(id: UserId): UserProfile?

    /** All works of [id], newest first, or `null` when there is no such person. */
    suspend fun getWorks(id: UserId): List<PortfolioWork>?

    /** All reviews about [id] with the numbers, newest first, or `null` when there is no such person. */
    suspend fun getReviews(id: UserId): UserReviews?

    /** One review, or `null` when there is no review with this [id]. */
    suspend fun getReview(id: ReviewId): Review?
}

/**
 * The viewer's own profile: changing it and the results of its moderation. The profile screens
 * observe it, so edits and decisions appear there without asking again.
 */
interface MyProfileRepository {
    /** The viewer's profile with the verification queue; emits again after every change. */
    fun observeMyProfile(): Flow<MyProfile>

    /** Saves the text about the person, the interests and the skills; returns the updated profile. */
    suspend fun updateAbout(draft: AboutDraft): MyProfile

    /**
     * Adds a work. With a link or a proof it is sent to moderation, otherwise it is
     * [VerificationStatus.Unconfirmed] at once.
     */
    suspend fun addWork(draft: WorkDraft): PortfolioWork

    /** Adds a document and sends it to moderation. */
    suspend fun addDocument(draft: DocumentDraft): ProfileDocument

    /** Decisions of the moderators, newest first; emits again when a new one arrives or is seen. */
    fun observeVerificationResults(): Flow<List<VerificationResult>>

    /** Marks the results as shown to the viewer; unknown ids are ignored. */
    suspend fun markResultsSeen(ids: Set<VerificationResultId>)
}
