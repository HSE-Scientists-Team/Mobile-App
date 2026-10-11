package org.matchlab.academic_net.feature.profile.data

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.profile.domain.AboutDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.MyProfile
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.UserProfile
import org.matchlab.academic_net.feature.profile.domain.UserReviews
import org.matchlab.academic_net.feature.profile.domain.VerificationResult
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.WorkDraft

/**
 * Where the profiles come from: the methods of both repositories of the feature. The server
 * decides what the viewer may see and who the viewer is; the only implementation for now is
 * [org.matchlab.academic_net.feature.profile.data.mock.MockProfileDataSource], which plays the
 * server. A remote one (with DTOs and their mappers) replaces it when the backend appears.
 */
interface ProfileDataSource {
    /** The profile of [id], or `null` when there is no such person. */
    suspend fun getProfile(id: UserId): UserProfile?

    /** All works of [id], newest first, or `null` when there is no such person. */
    suspend fun getWorks(id: UserId): List<PortfolioWork>?

    /** All reviews about [id] with the numbers, newest first, or `null` when there is no such person. */
    suspend fun getReviews(id: UserId): UserReviews?

    /** One review, or `null` when there is no review with this [id]. */
    suspend fun getReview(id: ReviewId): Review?

    /** The viewer's profile with the verification queue; emits again after every change. */
    fun observeMyProfile(): Flow<MyProfile>

    /** Saves the text about the viewer, the interests and the skills; returns the updated profile. */
    suspend fun updateAbout(draft: AboutDraft): MyProfile

    /** Adds a work of the viewer; moderation of it starts at once when it has a link or a proof. */
    suspend fun addWork(draft: WorkDraft): PortfolioWork

    /** Adds a document of the viewer and sends it to moderation. */
    suspend fun addDocument(draft: DocumentDraft): ProfileDocument

    /** Decisions of the moderators about the viewer's works and documents, newest first. */
    fun observeVerificationResults(): Flow<List<VerificationResult>>

    /** Marks the results as shown to the viewer; unknown ids are ignored. */
    suspend fun markResultsSeen(ids: Set<VerificationResultId>)
}
