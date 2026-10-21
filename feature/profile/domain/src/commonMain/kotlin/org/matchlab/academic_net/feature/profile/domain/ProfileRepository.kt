package org.matchlab.academic_net.feature.profile.domain

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.UserId

/**
 * Reading the profile of any person, the viewer or someone else. The calls throw on failure (no network, a server
 * error) and the use cases turn that into a result; a person the server does not know is `null`.
 */
interface ProfileRepository {
    /** What the server behind the profiles can give; the screens hide the rest (see [ProfileCapabilities]). */
    val capabilities: ProfileCapabilities

    /**
     * "09 Профиль": the profile of [id] with the newest works and reviews, or `null` when there is no such person.
     * The three parts are asked at once. For the viewer's own id it is the profile as the owner sees it.
     */
    suspend fun getProfile(id: UserId): UserProfile?

    /** "09e Работы": the works of [id], newest first (at most 100), or `null` when there is no such person. */
    suspend fun getWorks(id: UserId): UserWorks?

    /**
     * "09b Отзывы": the numbers over the reviews of [id] and the first page of them, or `null` when there is no
     * such person. The following pages come from [getReviewsPage].
     */
    suspend fun getUserReviews(id: UserId): UserReviews?

    /**
     * One more page of the reviews about [id], starting at [offset] (the number of reviews already shown), or `null`
     * when there is no such person. A page past the end is empty, with [ReviewsPage.hasMore] `false`.
     */
    suspend fun getReviewsPage(id: UserId, offset: Int): ReviewsPage?

    /**
     * "09d Отзыв": one review about [userId]. API-GAP G14: the API has no request for a single review, so it is
     * taken from the pages this repository has loaded, and when it is not there (the screen was restored) from the
     * list, page by page, up to the 500 newest reviews. `null` when it is not found.
     */
    suspend fun getReview(userId: UserId, reviewId: ReviewId): Review?
}

/**
 * The viewer's own profile: changing it, adding, editing and deleting its works and documents, and the results of
 * their moderation. The profile screens observe it, so a change appears there without asking again. Calls throw on
 * failure; the use cases turn that into a result.
 */
interface MyProfileRepository {
    /**
     * The viewer's profile with the verification queue. It is loaded when the first collector comes (a failure of
     * that load is thrown to the collector) and emits again after every change made through this repository and
     * after every [refreshMyProfile].
     */
    fun observeMyProfile(): Flow<MyProfile>

    /**
     * Asks the server for the profile again: when the screen is shown, and when the app comes back. A call that comes
     * while another one is on its way shares it (one set of requests, one result or failure). The answer is not put
     * over a change made through this repository after the request began (a save, an added, edited, sent or deleted
     * record): it is older than that change and is dropped, and the call still returns normally.
     */
    suspend fun refreshMyProfile()

    /**
     * Saves the form of "10b" and returns the profile after it. An account without a profile gets one with this
     * (API-GAP G19). The fields the form does not have (the avatar, the contacts, what the server holds that the
     * app does not understand) are not touched.
     */
    suspend fun updateProfile(draft: ProfileDraft): MyProfile

    /**
     * Makes [photo] the avatar of the viewer: the file is uploaded as a public image and the profile is saved with it
     * (the other fields of the profile are not touched); the file of the previous avatar is removed. The pictures of the
     * viewer on the screens are read again. Throws [ProfileRequiredException] for an account whose profile has no name yet.
     */
    suspend fun changeAvatar(photo: UploadFile)

    /**
     * Adds a work to the profile: the server gets it unconfirmed, and when [draft] has a link or a proof file
     * ([WorkDraft.sendsForVerification]) it is also sent for verification, as the two calls of the API. If the
     * sending fails the work is still added: [PortfolioChange.Saved] with `sent = false`, and it can be sent again.
     * If adding fails, nothing is added and the call throws.
     */
    suspend fun createWork(draft: WorkDraft): PortfolioChange<PortfolioWork>

    /** Like [createWork], for a document; it is sent when it has a proof ([DocumentDraft.sendsForVerification]). */
    suspend fun createDocument(draft: DocumentDraft): PortfolioChange<ProfileDocument>

    /** A work of the viewer as the server has it now, or `null` when it is gone. */
    suspend fun getMyWork(id: WorkId): PortfolioWork?

    /** A document of the viewer as the server has it now, or `null` when it is gone. */
    suspend fun getMyDocument(id: DocumentId): ProfileDocument?

    /**
     * Replaces the fields of a work that the form was based on at [expectedRevision]. The server resets the
     * confirmation of a changed record. [EditOutcome.Conflict] when the work has another revision now.
     */
    suspend fun updateWork(id: WorkId, draft: WorkDraft, expectedRevision: Int): EditOutcome<PortfolioWork>

    /** Like [updateWork], for a document. */
    suspend fun updateDocument(id: DocumentId, draft: DocumentDraft, expectedRevision: Int): EditOutcome<ProfileDocument>

    /** Sends a work or a document for verification. The server decides whether it can be sent again after a decision. */
    suspend fun submitForVerification(subject: VerificationSubject): EditOutcome<Unit>

    /** Deletes a work or a document. A record that is gone already counts as deleted. */
    suspend fun delete(subject: VerificationSubject): EditOutcome<Unit>

    /**
     * Decisions of the moderators, newest first; emits again when a new one arrives or is seen. API-GAP G12: the
     * Gateway has no statuses and no notifications, so it never emits anything but an empty list.
     */
    fun observeVerificationResults(): Flow<List<VerificationResult>>

    /** Marks the results as shown to the viewer; unknown ids are ignored. */
    suspend fun markResultsSeen(ids: Set<VerificationResultId>)
}
