package org.matchlab.academic_net.feature.profile.data

import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.MyProfile
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.UserProfile
import org.matchlab.academic_net.feature.profile.domain.VerificationItem
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject

// What the repository makes of the answers of the source: a profile page out of the parts of it. Pure functions, so that
// the rules (how many to preview, in which order, what waits for a decision) are checked without any request.

/** How many of the newest works a profile page previews. */
internal const val PreviewWorksCount = 3

/** How many of the newest reviews a profile page previews. */
internal const val PreviewReviewsCount = 2

/** Education first, inside a kind the newest year first, documents without a year last. */
private val DocumentOrder: Comparator<ProfileDocument> =
    compareBy<ProfileDocument> { it.kind != DocumentKind.Education }.thenByDescending { it.year ?: Int.MIN_VALUE }

/**
 * The profile page of [record] with the [portfolio] and the [latestReviews] (as many as the page previews): the
 * documents in the order of [DocumentOrder], the newest [PreviewWorksCount] works and [PreviewReviewsCount] reviews,
 * and the number of the works the list gave.
 */
internal fun assembleProfile(
    record: ProfileRecord,
    portfolio: Portfolio,
    latestReviews: List<Review>,
    isViewer: Boolean,
): UserProfile = UserProfile(
    person = record.person,
    about = record.about,
    interests = record.interests,
    skills = record.skills,
    documents = portfolio.documents.sortedWith(DocumentOrder),
    works = portfolio.works.take(PreviewWorksCount),
    worksCount = portfolio.works.size,
    reviews = record.reviews,
    latestReviews = latestReviews.take(PreviewReviewsCount),
    isViewer = isViewer,
    openToCollaboration = record.openToCollaboration,
)

/** The viewer's profile: the page as the owner sees it, every work, and what is waiting for a moderator. */
internal fun assembleMyProfile(
    record: ProfileRecord,
    portfolio: Portfolio,
    latestReviews: List<Review>,
): MyProfile = MyProfile(
    profile = assembleProfile(record, portfolio, latestReviews, isViewer = true),
    allWorks = portfolio.works,
    verificationQueue = verificationQueue(portfolio),
)

/**
 * The records that are pending or were rejected, documents first (in the order of the profile page), then the works.
 * A record whose status is unknown (`null`, API-GAP G12) is not in the queue, so the queue of a source without
 * statuses is empty.
 */
internal fun verificationQueue(portfolio: Portfolio): List<VerificationItem> {
    val documents = portfolio.documents.sortedWith(DocumentOrder).mapNotNull { document ->
        document.status.takeIf { it.needsAttention() }?.let { status ->
            VerificationItem(
                subject = VerificationSubject.Document(document.id, document.kind),
                title = document.title,
                status = status,
                moderationComment = document.moderationComment,
            )
        }
    }
    val works = portfolio.works.mapNotNull { work ->
        work.status.takeIf { it.needsAttention() }?.let { status ->
            VerificationItem(
                subject = VerificationSubject.Work(work.id),
                title = work.title,
                status = status,
                moderationComment = work.moderationComment,
            )
        }
    }
    return documents + works
}

private fun VerificationStatus?.needsAttention(): Boolean =
    this == VerificationStatus.Pending || this == VerificationStatus.Rejected
