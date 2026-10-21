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
 * What the profile feature can do with the server it talks to. A screen reads the flags and hides, or switches off
 * with a word of explanation, what the server cannot give, instead of showing made-up data. Every flag is closed by
 * one gap of `docs/api-gaps.md`: when the backend fills the gap, the data source sets the flag and implements the call.
 *
 * @property verificationStatuses API-GAP G12: the `status` of an achievement has no list of values, so the state of a
 * work or a document in the moderation is unknown (`null`). When `false` there are no status chips and banners, no
 * "Верификация профиля" queue, no results of the verification and no dot on the profile tab.
 * @property interestTypes the project types a person is interested in are shown and chosen (the chips of the editor):
 * the `project_types` of a profile hold the codes of `ProjectType` (`docs/api-values.md`). The entries the app does not
 * know are not shown but are kept when the profile is saved. When `false` the chips are hidden and the server's value
 * is kept as it is.
 */
data class ProfileCapabilities(
    val verificationStatuses: Boolean,
    val interestTypes: Boolean,
)

/**
 * Moderation state of a work or of a document, when the server tells it.
 *
 * The models carry `VerificationStatus?` where `null` is "unknown" (a text the app has no state for, or a source without
 * [ProfileCapabilities.verificationStatuses]). The Gateway maps the four statuses of the backend to these four: `unverified`,
 * `pending_review`, `verified`, `needs_evidence`.
 */
enum class VerificationStatus {
    /** Added without a link or a proof: nobody checks it. */
    Unconfirmed,

    /** Sent to a moderator, no decision yet. */
    Pending,

    /** A moderator confirmed it. */
    Verified,

    /** A moderator returned it for more proof (`needs_evidence`); the reason is the moderator's comment. */
    Rejected,
}

/**
 * The proof of a document: a link to it. The app has no file picker yet, so the proof is the `url` of the record,
 * which is public (API-GAP G17): whoever opens the profile can read it. When files can be uploaded the attachment
 * gets its own upload and this class keeps the address.
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
    /** The label of where the work lives, derived on the client from [link]: "DOI 10.1234/cv", "GitHub". */
    val source: String?,
    /** Where the work is in the moderation; `null` when the server does not tell (API-GAP G12). */
    val status: VerificationStatus?,
    /** The moderator's reason; only the owner gets it. */
    val moderationComment: String? = null,
    /** A proof file is attached on the server (uploaded elsewhere); an edit keeps it. */
    val hasEvidenceFile: Boolean = false,
    /** The version of the record the server holds: a change names it, and a stale one is refused. */
    val revision: Int = 0,
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
    /** The proof as a link (public, API-GAP G17); `null` when the proof is a file or there is none. */
    val proof: ProofAttachment?,
    /** Where the document is in the moderation; `null` when the server does not tell (API-GAP G12). */
    val status: VerificationStatus?,
    /** The moderator's reason; only the owner gets it. */
    val moderationComment: String? = null,
    /** A proof file is attached on the server (uploaded elsewhere); an edit keeps it. */
    val hasEvidenceFile: Boolean = false,
    /** The version of the record the server holds: a change names it, and a stale one is refused. */
    val revision: Int = 0,
)

/**
 * What a person is interested in: the project types (the chips of the screen) and free topics.
 *
 * The project types are only the ones the app knows (`ProjectType.fromCode`); they are empty when
 * [ProfileCapabilities.interestTypes] is `false`.
 */
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
    /**
     * API-GAP G15: the API does not say whether the author is verified, so `isVerified` is `false`. API-GAP G55: the
     * server may leave out the id of the author, and then `author.id.value` is empty: the name is there, but there is
     * no profile to open and nobody to write to, so the screens do not make the author tappable.
     */
    val author: UserSummary,
    /** `null` when the server's value is unknown to the app (API-GAP G13: the Gateway gives no list of roles). */
    val authorRole: ReviewerRole?,
    val projectTitle: String,
    /** The month the project ended; `null` when the server did not say. */
    val projectFinished: YearMonth?,
    /** Short summary: "Довёл модели до демо". */
    val title: String,
    /** 1 to 5 stars. */
    val rating: Int,
    val text: String,
    /** The viewer wrote the review: there is nobody to message. */
    val isByViewer: Boolean = false,
)

/**
 * Numbers over all reviews of a person, counted by the server.
 *
 * @property average mean rating, `null` while there are no reviews (API-GAP G20).
 * @property positiveCount how many reviews have a rating of 4 or more.
 */
data class ReviewsSummary(
    val average: Double?,
    val count: Int,
    val positiveCount: Int,
)

/**
 * A page of reviews, newest first (API-GAP G21: the API does not describe the order of the list; the app takes it as
 * it comes and shows it as the newest first).
 *
 * @property hasMore the page was full, so the next one may have reviews: the API gives no total (API-GAP G16).
 */
data class ReviewsPage(
    val items: List<Review>,
    val hasMore: Boolean,
)

/** The screen "Отзывы": the numbers (from the profile) and the first page of the list. */
data class UserReviews(
    val summary: ReviewsSummary,
    val firstPage: ReviewsPage,
)

/**
 * "Работы и публикации" of a person: the works, newest first.
 *
 * @property isViewer the works are the viewer's own; they open for editing.
 */
data class UserWorks(
    val items: List<PortfolioWork>,
    val isViewer: Boolean,
)

/**
 * A profile page. The documents come whole (there are few of them), while the works and the
 * reviews are previews: the full lists are separate requests ([UserWorks], [UserReviews]).
 */
data class UserProfile(
    /**
     * The name, the university and the study. `person.user.isVerified` is the server's verification of the
     * person. The name is empty for an account that has not filled the profile yet.
     */
    val person: Collaborator,
    val about: String,
    val interests: ProfileInterests,
    val skills: List<String>,
    val documents: List<ProfileDocument>,
    /** The newest works only; [worksCount] says how many there are. */
    val works: List<PortfolioWork>,
    /** The number of the works the list gave: at most 100 (API-GAP G16: the API has no total). */
    val worksCount: Int,
    val reviews: ReviewsSummary,
    /** The newest reviews only; the rest is in [UserReviews]. */
    val latestReviews: List<Review>,
    /** The viewer looks at their own profile. */
    val isViewer: Boolean,
    /** The person said they are open to collaboration (`open_to_collaboration`; missing means no). */
    val openToCollaboration: Boolean = false,
)

/**
 * A work or a document of the viewer: what a request to check, send or delete names. The kind of a document
 * is part of it, because the server keeps works and documents in one list.
 */
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
    /** Why it was rejected, when the moderator said. */
    val moderationComment: String? = null,
)

/**
 * The viewer's own profile and what is being checked in it.
 *
 * API-GAP G19: an account that has never saved a profile has no name; the profile is empty and [needsName] is
 * `true` until the viewer saves the form with a name.
 */
data class MyProfile(
    val profile: UserProfile,
    /** Every work of the viewer, newest first; [UserProfile.works] previews some. */
    val allWorks: List<PortfolioWork>,
    /**
     * Works and documents that are pending or were rejected; empty when everything is settled, and always
     * empty while [ProfileCapabilities.verificationStatuses] is `false`.
     */
    val verificationQueue: List<VerificationItem>,
) {
    /** A new account: no name yet. */
    val needsName: Boolean get() = profile.person.user.name.isBlank()
}

/** What a moderator decided. */
enum class VerificationOutcome {
    Verified,
    Rejected,
}

/**
 * A moderator's decision about one of the viewer's works or documents: the notification
 * "Подтверждено" / "Отклонено". It stays unseen until the viewer is shown it.
 *
 * API-GAP G12: the API gives neither the statuses nor the time of a decision nor a notification, so the Gateway
 * source has no results; this is what the screens show once it does.
 */
data class VerificationResult(
    val id: VerificationResultId,
    val subject: VerificationSubject,
    val subjectTitle: String,
    val outcome: VerificationOutcome,
    val decidedAt: Instant,
    val isSeen: Boolean,
)

/** The answer to a change of a record that exists: a save of its fields, a send for verification, a delete. */
sealed interface EditOutcome<out T> {
    /** The server accepted the change; [value] is the record after it (`Unit` for a delete). */
    data class Done<out T>(val value: T) : EditOutcome<T>

    /** The record was changed since it was loaded (409, or a revision that is not the current one). */
    data object Conflict : EditOutcome<Nothing>

    /** The record is gone (404). */
    data object NotFound : EditOutcome<Nothing>
}

/** What adding or saving a work or a document ended with. */
sealed interface PortfolioChange<out T> {
    /**
     * The record is saved on the server as [value].
     *
     * @property sent it was also sent for verification. `false` when the author asked only to save, when the
     * record has nothing to verify (no link, no file), or when sending failed after the record was saved: the
     * record is there, unconfirmed, and can be sent again.
     */
    data class Saved<out T>(val value: T, val sent: Boolean) : PortfolioChange<T>

    /** The record was changed since it was loaded; nothing was saved. */
    data object Conflict : PortfolioChange<Nothing>

    /** The record is gone; nothing was saved. */
    data object NotFound : PortfolioChange<Nothing>
}

/**
 * A file the user chose, ready to be uploaded: the photo of the avatar now, the proof of a work or a document later.
 * [bytes] is the whole file; they are not printed.
 */
class UploadFile(
    val bytes: ByteArray,
    val contentType: String,
    val fileName: String,
) {
    override fun toString(): String = "UploadFile($fileName, ${bytes.size} bytes, $contentType)"
}

/** The avatar belongs to a profile, and the account has none yet (no name): the profile has to be saved first. */
class ProfileRequiredException : IllegalStateException("The profile has no name yet")
