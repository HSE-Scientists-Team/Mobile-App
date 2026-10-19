package org.matchlab.academic_net.feature.profile.data

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.EditOutcome
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileCapabilities
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.ProfileDraft
import org.matchlab.academic_net.feature.profile.domain.ProfileInterests
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewsSummary
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkId

/**
 * A person's profile without the portfolio and the reviews: what one request for the profile gives.
 *
 * @property reviews the numbers over the reviews, as the server counts them.
 * @property openToCollaboration the server's `open_to_collaboration`; a missing field or `null` is `false`.
 */
data class ProfileRecord(
    val person: Collaborator,
    val about: String,
    val interests: ProfileInterests,
    val skills: List<String>,
    val reviews: ReviewsSummary,
    val openToCollaboration: Boolean = false,
) {
    companion object {
        /**
         * The profile of an account that has not saved one yet (API-GAP G19): no name, nothing else. The first
         * save of the profile form creates the real one.
         */
        fun empty(id: UserId): ProfileRecord = ProfileRecord(
            person = Collaborator(UserSummary(id, name = ""), university = "", study = Study(), field = ""),
            about = "",
            interests = ProfileInterests(projectTypes = emptySet(), topics = emptyList()),
            skills = emptyList(),
            reviews = ReviewsSummary(average = null, count = 0, positiveCount = 0),
        )
    }
}

/**
 * The works (newest first) and the documents of a person, as the server lists them. The lists are one request
 * (at most 100 records, API-GAP G16), split into works and documents by the kind of the record.
 */
data class Portfolio(
    val works: List<PortfolioWork>,
    val documents: List<ProfileDocument>,
) {
    companion object {
        /** No works and no documents: what a person the server has nothing of shows. */
        val Empty = Portfolio(works = emptyList(), documents = emptyList())
    }
}

/**
 * The server of the profiles: its resources and the translation of its answers into the models of the feature, and
 * nothing else. What a profile page is made of, how the reviews are paged and what the screens observe is the
 * business of [ProfileRepositoryImpl], so it works the same over every source.
 *
 * A call throws when it fails: `RemoteFailure.NoConnection`, `Timeout`, `Malformed`, or `Http` for a status that has no
 * answer of its own. A person the server does not know, or a record that is gone, is `null`, or an
 * [EditOutcome] that says so. A `CancellationException` is never turned into anything.
 */
interface ProfileDataSource {
    /** What this server can give; the screens hide the rest. */
    val capabilities: ProfileCapabilities

    /** The profile of [id] without the portfolio, or `null` when there is no such person. */
    suspend fun getProfile(id: UserId): ProfileRecord?

    /** What anyone sees of the works and documents of [id], or `null` when there is no such person. */
    suspend fun getPortfolio(id: UserId): Portfolio?

    /**
     * The reviews about [id]: [limit] of them from [offset], newest first; `null` when there is no such person. A
     * review the source cannot show is not among them (API-GAP G55: one without an `id`), so a page may be shorter
     * than [limit] though the server had more.
     */
    suspend fun getReviews(id: UserId, offset: Int, limit: Int): List<Review>?

    /** The viewer's own profile, or `null` when the account has no profile yet (API-GAP G19). */
    suspend fun getMyProfile(): ProfileRecord?

    /**
     * Saves the form of the profile over the profile the server has, so that what the form does not have stays, and
     * returns the profile after it.
     */
    suspend fun updateMyProfile(draft: ProfileDraft): ProfileRecord

    /** The viewer's works and documents as the owner sees them: every record, with the moderator's comment. */
    suspend fun getMyPortfolio(): Portfolio

    /** A work of the viewer, or `null` when there is none with this id. */
    suspend fun getMyWork(id: WorkId): PortfolioWork?

    /** A document of the viewer, or `null` when there is none with this id. */
    suspend fun getMyDocument(id: DocumentId): ProfileDocument?

    /** Adds a work; the server keeps it unconfirmed until it is sent. */
    suspend fun createWork(draft: WorkDraft): PortfolioWork

    /** Adds a document; the server keeps it unconfirmed until it is sent. */
    suspend fun createDocument(draft: DocumentDraft): ProfileDocument

    /** Replaces the fields of a work that [draft] was based on at [expectedRevision]. */
    suspend fun updateWork(id: WorkId, draft: WorkDraft, expectedRevision: Int): EditOutcome<PortfolioWork>

    /** Replaces the fields of a document that [draft] was based on at [expectedRevision]. */
    suspend fun updateDocument(id: DocumentId, draft: DocumentDraft, expectedRevision: Int): EditOutcome<ProfileDocument>

    /** Sends a work or a document for verification. */
    suspend fun submit(subject: VerificationSubject): EditOutcome<Unit>

    /** Deletes a work or a document; a record that is gone is deleted ([EditOutcome.Done]). */
    suspend fun delete(subject: VerificationSubject): EditOutcome<Unit>
}
