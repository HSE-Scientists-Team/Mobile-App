package org.matchlab.academic_net.feature.profile.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary

// What the tests of the use cases share: models to build the answers of a repository from, and fake repositories that
// record every call and answer as a test says.

internal val ViewerId = UserId("viewer")

internal fun person(
    name: String = "Арджун Мехта",
    id: UserId = ViewerId,
    study: Study = Study(level = "Бакалавриат", year = 3),
): Collaborator = Collaborator(
    user = UserSummary(id, name),
    university = "НИУ ВШЭ",
    study = study,
    field = "Computer Science",
)

internal fun work(
    id: String = "w1",
    title: String = "Статья о графах",
    description: String = "Описание",
    link: String? = "https://doi.org/10.1/x",
    type: ProjectType = ProjectType.Scientific,
    hasEvidenceFile: Boolean = false,
    revision: Int = 3,
): PortfolioWork = PortfolioWork(
    id = WorkId(id),
    type = type,
    title = title,
    description = description,
    link = link,
    source = null,
    status = null,
    hasEvidenceFile = hasEvidenceFile,
    revision = revision,
)

internal fun document(
    id: String = "d1",
    kind: DocumentKind = DocumentKind.Education,
    title: String = "Диплом бакалавра",
    issuer: String = "НИУ ВШЭ",
    year: Int? = 2025,
    proof: ProofAttachment? = ProofAttachment("https://files.example.com/diploma.pdf"),
    hasEvidenceFile: Boolean = false,
    revision: Int = 2,
): ProfileDocument = ProfileDocument(
    id = DocumentId(id),
    kind = kind,
    title = title,
    issuer = issuer,
    year = year,
    proof = proof,
    status = null,
    hasEvidenceFile = hasEvidenceFile,
    revision = revision,
)

internal fun userProfile(
    person: Collaborator = person(),
    works: List<PortfolioWork> = emptyList(),
    documents: List<ProfileDocument> = emptyList(),
): UserProfile = UserProfile(
    person = person,
    about = "Люблю данные",
    interests = ProfileInterests(projectTypes = emptySet(), topics = listOf("ML")),
    skills = listOf("Kotlin"),
    documents = documents,
    works = works,
    worksCount = works.size,
    reviews = ReviewsSummary(average = null, count = 0, positiveCount = 0),
    latestReviews = emptyList(),
    isViewer = true,
)

internal fun myProfile(
    person: Collaborator = person(),
    works: List<PortfolioWork> = emptyList(),
    documents: List<ProfileDocument> = emptyList(),
): MyProfile = MyProfile(
    profile = userProfile(person, works, documents),
    allWorks = works,
    verificationQueue = emptyList(),
)

internal fun review(id: String = "r1", rating: Int = 5): Review = Review(
    id = ReviewId(id),
    author = UserSummary(UserId("author"), "Елена"),
    authorRole = null,
    projectTitle = "Цифровой двойник",
    projectFinished = null,
    title = "Отлично",
    rating = rating,
    text = "Довёл до демо",
)

/**
 * A [MyProfileRepository] that records its calls by name and answers them as the test scripts: by default everything
 * goes through, and an edit answers with the record the draft describes.
 */
internal class FakeMyProfileRepository : MyProfileRepository {
    val calls = mutableListOf<String>()

    var profile: MyProfile = myProfile()
    var onUpdateProfile: suspend (ProfileDraft) -> MyProfile = { profile }
    var onCreateWork: suspend (WorkDraft) -> PortfolioChange<PortfolioWork> =
        { draft -> PortfolioChange.Saved(work(title = draft.title), sent = draft.sendsForVerification) }
    var onCreateDocument: suspend (DocumentDraft) -> PortfolioChange<ProfileDocument> =
        { draft -> PortfolioChange.Saved(document(title = draft.title), sent = draft.sendsForVerification) }
    var onGetMyWork: suspend (WorkId) -> PortfolioWork? = { work(it.value) }
    var onGetMyDocument: suspend (DocumentId) -> ProfileDocument? = { document(it.value) }
    var onUpdateWork: suspend (WorkId, WorkDraft, Int) -> EditOutcome<PortfolioWork> =
        { id, draft, _ -> EditOutcome.Done(work(id.value, title = draft.title)) }
    var onUpdateDocument: suspend (DocumentId, DocumentDraft, Int) -> EditOutcome<ProfileDocument> =
        { id, draft, _ -> EditOutcome.Done(document(id.value, title = draft.title)) }
    var onSubmit: suspend (VerificationSubject) -> EditOutcome<Unit> = { EditOutcome.Done(Unit) }
    var onDelete: suspend (VerificationSubject) -> EditOutcome<Unit> = { EditOutcome.Done(Unit) }
    var onRefresh: suspend () -> Unit = {}
    var onChangeAvatar: suspend (UploadFile) -> Unit = {}
    var results: Flow<List<VerificationResult>> = MutableStateFlow(emptyList())

    /** The drafts and the revisions the repository was asked to save. */
    val updatedProfiles = mutableListOf<ProfileDraft>()
    val createdWorks = mutableListOf<WorkDraft>()
    val createdDocuments = mutableListOf<DocumentDraft>()
    val updatedWorks = mutableListOf<Triple<WorkId, WorkDraft, Int>>()
    val updatedDocuments = mutableListOf<Triple<DocumentId, DocumentDraft, Int>>()
    val submitted = mutableListOf<VerificationSubject>()
    val deleted = mutableListOf<VerificationSubject>()
    val seen = mutableListOf<Set<VerificationResultId>>()

    override fun observeMyProfile(): Flow<MyProfile> = MutableStateFlow(profile)

    override suspend fun refreshMyProfile() {
        calls += "refresh"
        onRefresh()
    }

    val changedAvatars = mutableListOf<UploadFile>()

    override suspend fun changeAvatar(photo: UploadFile) {
        calls += "changeAvatar"
        changedAvatars += photo
        onChangeAvatar(photo)
    }

    override suspend fun updateProfile(draft: ProfileDraft): MyProfile {
        calls += "updateProfile"
        updatedProfiles += draft
        return onUpdateProfile(draft)
    }

    override suspend fun createWork(draft: WorkDraft): PortfolioChange<PortfolioWork> {
        calls += "createWork"
        createdWorks += draft
        return onCreateWork(draft)
    }

    override suspend fun createDocument(draft: DocumentDraft): PortfolioChange<ProfileDocument> {
        calls += "createDocument"
        createdDocuments += draft
        return onCreateDocument(draft)
    }

    override suspend fun getMyWork(id: WorkId): PortfolioWork? {
        calls += "getMyWork"
        return onGetMyWork(id)
    }

    override suspend fun getMyDocument(id: DocumentId): ProfileDocument? {
        calls += "getMyDocument"
        return onGetMyDocument(id)
    }

    override suspend fun updateWork(id: WorkId, draft: WorkDraft, expectedRevision: Int): EditOutcome<PortfolioWork> {
        calls += "updateWork"
        updatedWorks += Triple(id, draft, expectedRevision)
        return onUpdateWork(id, draft, expectedRevision)
    }

    override suspend fun updateDocument(
        id: DocumentId,
        draft: DocumentDraft,
        expectedRevision: Int,
    ): EditOutcome<ProfileDocument> {
        calls += "updateDocument"
        updatedDocuments += Triple(id, draft, expectedRevision)
        return onUpdateDocument(id, draft, expectedRevision)
    }

    override suspend fun submitForVerification(subject: VerificationSubject): EditOutcome<Unit> {
        calls += "submit"
        submitted += subject
        return onSubmit(subject)
    }

    override suspend fun delete(subject: VerificationSubject): EditOutcome<Unit> {
        calls += "delete"
        deleted += subject
        return onDelete(subject)
    }

    override fun observeVerificationResults(): Flow<List<VerificationResult>> = results

    override suspend fun markResultsSeen(ids: Set<VerificationResultId>) {
        calls += "markSeen"
        seen += ids
    }
}

/** A [ProfileRepository] that answers as the test scripts. */
internal class FakeProfileRepository : ProfileRepository {
    override var capabilities = ProfileCapabilities(verificationStatuses = false, interestTypes = false)

    var onGetProfile: suspend (UserId) -> UserProfile? = { userProfile() }
    var onGetWorks: suspend (UserId) -> UserWorks? = { UserWorks(emptyList(), isViewer = false) }
    var onGetUserReviews: suspend (UserId) -> UserReviews? =
        { UserReviews(ReviewsSummary(null, 0, 0), ReviewsPage(emptyList(), hasMore = false)) }
    var onGetReviewsPage: suspend (UserId, Int) -> ReviewsPage? = { _, _ -> ReviewsPage(emptyList(), hasMore = false) }
    var onGetReview: suspend (UserId, ReviewId) -> Review? = { _, id -> review(id.value) }

    override suspend fun getProfile(id: UserId): UserProfile? = onGetProfile(id)

    override suspend fun getWorks(id: UserId): UserWorks? = onGetWorks(id)

    override suspend fun getUserReviews(id: UserId): UserReviews? = onGetUserReviews(id)

    override suspend fun getReviewsPage(id: UserId, offset: Int): ReviewsPage? = onGetReviewsPage(id, offset)

    override suspend fun getReview(userId: UserId, reviewId: ReviewId): Review? = onGetReview(userId, reviewId)
}
