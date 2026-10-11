package org.matchlab.academic_net.feature.profile.domain

import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.UserSummary

/** Identifier of a work of a profile ([PortfolioWork]), issued by the server. */
@JvmInline
value class WorkId(val value: String)

/** Identifier of a document of a profile ([ProfileDocument]), issued by the server. */
@JvmInline
value class DocumentId(val value: String)

/** Identifier of a review ([Review]); it is what the screen of one review is opened by. */
@JvmInline
value class ReviewId(val value: String)

/**
 * Identifier of a decision of a moderator ([VerificationResult]); the viewer sends the ids of the
 * decisions they were shown to mark them seen.
 */
@JvmInline
value class VerificationResultId(val value: String)

/**
 * Moderation state of a work or of a document.
 *
 * The owner sees all four; other viewers get only [Verified] and [Unconfirmed]: what is being
 * checked or was rejected is the owner's business.
 */
enum class VerificationStatus {
    /** Added without a link or a proof: nobody checks it. */
    Unconfirmed,

    /** Sent to a moderator, no decision yet. */
    Pending,

    /** A moderator confirmed it. */
    Verified,

    /** A moderator did not confirm it ("Нужны доказательства"). */
    Rejected,
}

/**
 * Stand-in of an uploaded file in the first sprint: a link to it. The app has no file picker yet;
 * when it appears the attachment gets its own upload and this class keeps the address.
 */
data class ProofAttachment(val url: String)

/** A scientific or a practical work of a person: "Работы и публикации". */
data class PortfolioWork(
    val id: WorkId,
    val type: ProjectType,
    val title: String,
    val description: String,
    /** Where the work lives (publication, repository); `null` if the author gave no link. */
    val link: String?,
    /** The server's label of where the work lives, derived from [link]: "DOI 10.1234/cv", "GitHub". */
    val source: String?,
    val status: VerificationStatus,
)

enum class DocumentKind {
    /** A diploma or a certificate of study: a confirmed one makes the person verified. */
    Education,

    /** A course certificate or another document. */
    Certificate,
}

/** A document from "Образование и документы". */
data class ProfileDocument(
    val id: DocumentId,
    val kind: DocumentKind,
    val title: String,
    /** The university or organization that issued it. */
    val issuer: String,
    val year: Int?,
    val status: VerificationStatus,
)

/** What a person is interested in: the project types (the chips of the screen) and free topics. */
data class ProfileInterests(
    val projectTypes: Set<ProjectType>,
    val topics: List<String>,
)

/** Who the author of a review was in the project that is reviewed. */
enum class ReviewerRole {
    Initiator,
    Participant,
}

/** A review one person left about another after a finished project. */
data class Review(
    val id: ReviewId,
    val author: UserSummary,
    val authorRole: ReviewerRole,
    val projectTitle: String,
    /** The month the project ended. */
    val projectFinished: YearMonth,
    /** Short summary: "Довёл модели до демо". */
    val title: String,
    /** 1 to 5 stars. */
    val rating: Int,
    val text: String,
)

/**
 * Numbers over all reviews of a person, counted by the server from the real list.
 *
 * @property average mean rating, `null` while there are no reviews.
 * @property positiveCount how many reviews have a rating of 4 or more.
 */
data class ReviewsSummary(
    val average: Double?,
    val count: Int,
    val positiveCount: Int,
)

/** The screen "Отзывы": the numbers and the whole list, newest first. */
data class UserReviews(
    val summary: ReviewsSummary,
    val items: List<Review>,
)

/**
 * A profile page. The documents come whole (there are few of them), while the works and the
 * reviews are previews chosen by the server: the full lists are separate requests
 * ([UserReviews], and the works through the list of works of the person).
 */
data class UserProfile(
    /**
     * The name, the university and study info. `person.user.isVerified` is the person's
     * verification status: at least one education document has been confirmed.
     */
    val person: Collaborator,
    val about: String,
    val interests: ProfileInterests,
    val skills: List<String>,
    val documents: List<ProfileDocument>,
    /** The newest works only; [worksCount] says how many there are in all. */
    val works: List<PortfolioWork>,
    val worksCount: Int,
    val reviews: ReviewsSummary,
    /** The newest reviews only; the rest is in [UserReviews]. */
    val latestReviews: List<Review>,
    /** The viewer looks at their own profile. */
    val isViewer: Boolean,
)

/** What is under moderation or was rejected: a work or a document of the viewer. */
sealed interface VerificationSubject {
    data class Work(val id: WorkId) : VerificationSubject

    data class Document(val id: DocumentId, val kind: DocumentKind) : VerificationSubject
}

/** A line of "Верификация профиля" on the viewer's profile. */
data class VerificationItem(
    val subject: VerificationSubject,
    val title: String,
    /** [VerificationStatus.Pending] or [VerificationStatus.Rejected]. */
    val status: VerificationStatus,
)

/** The viewer's own profile and what is being checked in it. */
data class MyProfile(
    val profile: UserProfile,
    /** Works and documents that are pending or were rejected; empty when everything is settled. */
    val verificationQueue: List<VerificationItem>,
)

/** What a moderator decided. */
enum class VerificationOutcome {
    Verified,
    Rejected,
}

/**
 * A moderator's decision about one of the viewer's works or documents: the notification
 * "Подтверждено" / "Отклонено". It stays unseen until the viewer is shown it.
 */
data class VerificationResult(
    val id: VerificationResultId,
    val subject: VerificationSubject,
    val subjectTitle: String,
    val outcome: VerificationOutcome,
    val decidedAt: Instant,
    val isSeen: Boolean,
)
