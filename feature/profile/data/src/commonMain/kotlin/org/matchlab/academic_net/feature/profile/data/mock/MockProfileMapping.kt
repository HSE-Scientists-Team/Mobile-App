package org.matchlab.academic_net.feature.profile.data.mock

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.MyProfile
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.ProfileInterests
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.ReviewerRole
import org.matchlab.academic_net.feature.profile.domain.ReviewsSummary
import org.matchlab.academic_net.feature.profile.domain.UserProfile
import org.matchlab.academic_net.feature.profile.domain.UserReviews
import org.matchlab.academic_net.feature.profile.domain.VerificationItem
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.mockserver.MockCheckState
import org.matchlab.academic_net.mockserver.MockPeople
import org.matchlab.academic_net.mockserver.MockServerDocument
import org.matchlab.academic_net.mockserver.MockServerReview
import org.matchlab.academic_net.mockserver.MockServerWork

// Rows of the mock server's tables (`:mockserver`) as the profile feature's domain model.

/** How many of the newest works and reviews a profile page previews. */
internal const val PreviewWorksCount = 3
internal const val PreviewReviewsCount = 2

/**
 * The moderation state as the viewer may know it. The owner sees the real one; other people see
 * only whether the thing is confirmed: a pending or rejected work looks just unconfirmed.
 */
internal fun MockCheckState.toStatus(forOwner: Boolean): VerificationStatus = when (this) {
    MockCheckState.Verified -> VerificationStatus.Verified
    MockCheckState.Unconfirmed -> VerificationStatus.Unconfirmed
    MockCheckState.Pending -> if (forOwner) VerificationStatus.Pending else VerificationStatus.Unconfirmed
    MockCheckState.Rejected -> if (forOwner) VerificationStatus.Rejected else VerificationStatus.Unconfirmed
}

internal fun MockServerWork.toDomain(forOwner: Boolean) = PortfolioWork(
    id = WorkId(id),
    type = type,
    title = title,
    description = description,
    link = link,
    source = linkSource(link),
    status = state.toStatus(forOwner),
)

internal fun MockServerDocument.documentKind() = if (isEducation) DocumentKind.Education else DocumentKind.Certificate

/** What a verification result or a queue line points at. */
internal fun MockServerWork.subject() = VerificationSubject.Work(WorkId(id))

internal fun MockServerDocument.subject() = VerificationSubject.Document(DocumentId(id), documentKind())

internal fun MockServerDocument.toDomain(forOwner: Boolean) = ProfileDocument(
    id = DocumentId(id),
    kind = documentKind(),
    title = title,
    issuer = issuer,
    year = year,
    status = state.toStatus(forOwner),
)

/** Education first, inside a kind the newest year first, documents without a year last. */
private val DocumentOrder: Comparator<MockServerDocument> =
    compareBy<MockServerDocument> { !it.isEducation }.thenByDescending { it.year ?: Int.MIN_VALUE }

internal fun MockServerReview.toDomain(people: MockPeople) = Review(
    id = ReviewId(id),
    author = people.summary(authorId) ?: unknownPerson(authorId),
    authorRole = if (authorIsInitiator) ReviewerRole.Initiator else ReviewerRole.Participant,
    projectTitle = projectTitle,
    projectFinished = projectFinished,
    title = title,
    rating = rating,
    text = text,
)

private fun unknownPerson(id: UserId): Nothing =
    error("The mock people tables are inconsistent: no person $id")

/** The numbers over all reviews about [id], counted from the list. */
internal fun MockPeople.reviewsSummary(id: UserId): ReviewsSummary {
    val reviews = reviewsAbout(id)
    return ReviewsSummary(
        average = rating(id),
        count = reviews.size,
        positiveCount = reviews.count { it.rating >= PositiveRating },
    )
}

private const val PositiveRating = 4

internal fun MockPeople.userReviews(id: UserId): UserReviews =
    UserReviews(reviewsSummary(id), reviewsAbout(id).map { it.toDomain(this) })

internal fun MockPeople.userWorks(id: UserId, forOwner: Boolean): List<PortfolioWork> =
    worksOf(id).map { it.toDomain(forOwner) }

/**
 * The profile of [id]; `null` when the server does not know the person. The viewer's own profile
 * ([viewerId]) shows the real moderation states, everybody else's only confirmed or not.
 */
internal fun MockPeople.userProfile(id: UserId, viewerId: UserId): UserProfile? {
    val person = person(id) ?: return null
    val forOwner = id == viewerId
    val works = userWorks(id, forOwner)
    val reviews = reviewsAbout(id)
    return UserProfile(
        person = Collaborator(
            user = UserSummary(person.id, person.name, isVerified = isVerified(id)),
            university = person.university,
            studyInfo = person.studyInfo,
            field = person.field,
        ),
        about = person.about,
        interests = ProfileInterests(person.interestTypes, person.interests),
        skills = person.skills,
        documents = documentsOf(id).sortedWith(DocumentOrder).map { it.toDomain(forOwner) },
        works = works.take(PreviewWorksCount),
        worksCount = works.size,
        reviews = reviewsSummary(id),
        latestReviews = reviews.take(PreviewReviewsCount).map { it.toDomain(this) },
        isViewer = forOwner,
    )
}

/** Documents and works of [id] that are pending or were rejected, documents first. */
internal fun MockPeople.verificationQueue(id: UserId): List<VerificationItem> {
    val documents = documentsOf(id).sortedWith(DocumentOrder)
        .filter { it.state.needsAttention() }
        .map {
            VerificationItem(
                subject = it.subject(),
                title = it.title,
                status = it.state.toStatus(forOwner = true),
            )
        }
    val works = worksOf(id)
        .filter { it.state.needsAttention() }
        .map {
            VerificationItem(
                subject = it.subject(),
                title = it.title,
                status = it.state.toStatus(forOwner = true),
            )
        }
    return documents + works
}

private fun MockCheckState.needsAttention() = this == MockCheckState.Pending || this == MockCheckState.Rejected

internal fun MockPeople.myProfile(viewerId: UserId): MyProfile = MyProfile(
    profile = userProfile(viewerId, viewerId) ?: unknownPerson(viewerId),
    verificationQueue = verificationQueue(viewerId),
)
