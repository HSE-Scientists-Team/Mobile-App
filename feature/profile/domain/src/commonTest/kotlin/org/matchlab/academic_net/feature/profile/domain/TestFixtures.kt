package org.matchlab.academic_net.feature.profile.domain

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary

internal val SomePerson = Collaborator(
    user = UserSummary(UserId("u-1"), "Арджун Мехта", isVerified = true),
    university = "Stanford",
    studyInfo = "3 курс бакалавриата",
    field = "Computer Science",
)

internal val SomeWork = PortfolioWork(
    id = WorkId("w-1"),
    type = ProjectType.Scientific,
    title = "Title",
    description = "Description",
    link = "https://doi.org/10.1234/cv",
    source = "DOI 10.1234/cv",
    status = VerificationStatus.Verified,
)

internal val SomeDocument = ProfileDocument(
    id = DocumentId("d-1"),
    kind = DocumentKind.Education,
    title = "B.S. Computer Science",
    issuer = "Stanford University",
    year = 2027,
    status = VerificationStatus.Pending,
)

internal val SomeReview = Review(
    id = ReviewId("r-1"),
    author = UserSummary(UserId("u-2"), "Лина Ковач"),
    authorRole = ReviewerRole.Participant,
    projectTitle = "AI-навигация по кампусу",
    projectFinished = YearMonth(2026, 3),
    title = "Помогал команде с данными",
    rating = 5,
    text = "Сильный инженер.",
)

internal val NoReviews = ReviewsSummary(average = null, count = 0, positiveCount = 0)

internal val SomeProfile = UserProfile(
    person = SomePerson,
    about = "About",
    interests = ProfileInterests(setOf(ProjectType.Scientific), listOf("Edge AI")),
    skills = listOf("Python"),
    documents = listOf(SomeDocument),
    works = listOf(SomeWork),
    worksCount = 1,
    reviews = NoReviews,
    latestReviews = emptyList(),
    isViewer = false,
)

internal val SomeMyProfile = MyProfile(SomeProfile.copy(isViewer = true), verificationQueue = emptyList())

internal fun result(id: String, isSeen: Boolean = false) = VerificationResult(
    id = VerificationResultId(id),
    subject = VerificationSubject.Work(WorkId("w-$id")),
    subjectTitle = "Work $id",
    outcome = VerificationOutcome.Verified,
    decidedAt = Instant.fromEpochSeconds(0),
    isSeen = isSeen,
)

/** Answers with the fields, throws [failure] when it is set; remembers what it was asked. */
internal class FakeProfileRepository : ProfileRepository {
    var profile: UserProfile? = SomeProfile
    var works: List<PortfolioWork>? = listOf(SomeWork)
    var reviews: UserReviews? = UserReviews(NoReviews, emptyList())
    var review: Review? = SomeReview
    var failure: Throwable? = null

    val requestedUsers = mutableListOf<UserId>()
    val requestedReviews = mutableListOf<ReviewId>()

    override suspend fun getProfile(id: UserId): UserProfile? {
        requestedUsers += id
        failure?.let { throw it }
        return profile
    }

    override suspend fun getWorks(id: UserId): List<PortfolioWork>? {
        requestedUsers += id
        failure?.let { throw it }
        return works
    }

    override suspend fun getReviews(id: UserId): UserReviews? {
        requestedUsers += id
        failure?.let { throw it }
        return reviews
    }

    override suspend fun getReview(id: ReviewId): Review? {
        requestedReviews += id
        failure?.let { throw it }
        return review
    }
}

/** Remembers the drafts it got; throws [failure] when it is set. */
internal class FakeMyProfileRepository : MyProfileRepository {
    val profile = MutableStateFlow(SomeMyProfile)
    val results = MutableStateFlow<List<VerificationResult>>(emptyList())
    var failure: Throwable? = null

    val aboutDrafts = mutableListOf<AboutDraft>()
    val workDrafts = mutableListOf<WorkDraft>()
    val documentDrafts = mutableListOf<DocumentDraft>()
    val seen = mutableListOf<Set<VerificationResultId>>()

    override fun observeMyProfile(): Flow<MyProfile> = profile

    override suspend fun updateAbout(draft: AboutDraft): MyProfile {
        failure?.let { throw it }
        aboutDrafts += draft
        return profile.value
    }

    override suspend fun addWork(draft: WorkDraft): PortfolioWork {
        failure?.let { throw it }
        workDrafts += draft
        return SomeWork
    }

    override suspend fun addDocument(draft: DocumentDraft): ProfileDocument {
        failure?.let { throw it }
        documentDrafts += draft
        return SomeDocument
    }

    override fun observeVerificationResults(): Flow<List<VerificationResult>> = results

    override suspend fun markResultsSeen(ids: Set<VerificationResultId>) {
        failure?.let { throw it }
        seen += ids
    }
}
