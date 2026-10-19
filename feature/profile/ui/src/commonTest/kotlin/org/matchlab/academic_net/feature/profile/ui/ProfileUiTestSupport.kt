package org.matchlab.academic_net.feature.profile.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.EditOutcome
import org.matchlab.academic_net.feature.profile.domain.MyProfile
import org.matchlab.academic_net.feature.profile.domain.MyProfileRepository
import org.matchlab.academic_net.feature.profile.domain.PortfolioChange
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileCapabilities
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.ProfileDraft
import org.matchlab.academic_net.feature.profile.domain.ProfileInterests
import org.matchlab.academic_net.feature.profile.domain.ProfileRepository
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.ReviewsPage
import org.matchlab.academic_net.feature.profile.domain.ReviewsSummary
import org.matchlab.academic_net.feature.profile.domain.UserProfile
import org.matchlab.academic_net.feature.profile.domain.UserReviews
import org.matchlab.academic_net.feature.profile.domain.UserWorks
import org.matchlab.academic_net.feature.profile.domain.VerificationItem
import org.matchlab.academic_net.feature.profile.domain.VerificationResult
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.feature.profile.domain.usecase.AddDocumentUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.AddWorkUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.DeletePortfolioItemUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetMyDocumentUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetMyWorkUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetReviewUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetReviewsPageUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserReviewsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserWorksUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.MarkResultsSeenUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveHasUnseenResultsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveMyProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveVerificationResultsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.RefreshMyProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.SaveDocumentUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.SaveWorkUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.UpdateProfileUseCase

// What the view-model tests of the profile screens share: the main dispatcher of a test, models to build answers from, and
// fake repositories on the interfaces of the domain (the screens are tested against the domain, never against the data layer).

@OptIn(ExperimentalCoroutinesApi::class)
fun installMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

@OptIn(ExperimentalCoroutinesApi::class)
fun uninstallMainDispatcher() = Dispatchers.resetMain()

/** Keeps the flow hot, so `stateIn(WhileSubscribed)` states are live; stops when the test ends. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> TestScope.keepCollecting(flow: Flow<T>) {
    val scope: CoroutineScope = backgroundScope
    scope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}

val Viewer = UserId("viewer")
val SomeoneElse = UserId("someone")

fun person(
    name: String = "Арджун Мехта",
    id: UserId = Viewer,
    study: Study = Study(level = "Бакалавриат", year = 3),
): Collaborator = Collaborator(
    user = UserSummary(id, name),
    university = "НИУ ВШЭ",
    study = study,
    field = "Computer Science",
)

fun work(
    id: String = "w1",
    title: String = "Статья о графах",
    description: String = "Описание",
    link: String? = "https://doi.org/10.1/x",
    type: ProjectType = ProjectType.Scientific,
    status: VerificationStatus? = null,
    hasEvidenceFile: Boolean = false,
    revision: Int = 3,
): PortfolioWork = PortfolioWork(
    id = WorkId(id),
    type = type,
    title = title,
    description = description,
    link = link,
    source = null,
    status = status,
    hasEvidenceFile = hasEvidenceFile,
    revision = revision,
)

fun document(
    id: String = "d1",
    kind: DocumentKind = DocumentKind.Education,
    title: String = "Диплом бакалавра",
    issuer: String = "НИУ ВШЭ",
    year: Int? = 2025,
    proof: ProofAttachment? = ProofAttachment("https://files.example.com/diploma.pdf"),
    status: VerificationStatus? = null,
    hasEvidenceFile: Boolean = false,
    revision: Int = 2,
): ProfileDocument = ProfileDocument(
    id = DocumentId(id),
    kind = kind,
    title = title,
    issuer = issuer,
    year = year,
    proof = proof,
    status = status,
    hasEvidenceFile = hasEvidenceFile,
    revision = revision,
)

fun review(id: String = "r1", rating: Int = 5, text: String = "Довёл до демо"): Review = Review(
    id = ReviewId(id),
    author = UserSummary(UserId("author"), "Елена"),
    authorRole = null,
    projectTitle = "Цифровой двойник",
    projectFinished = null,
    title = "Отлично",
    rating = rating,
    text = text,
)

fun userProfile(
    person: Collaborator = person(),
    works: List<PortfolioWork> = emptyList(),
    worksCount: Int = works.size,
    documents: List<ProfileDocument> = emptyList(),
    isViewer: Boolean = true,
    reviews: ReviewsSummary = ReviewsSummary(average = null, count = 0, positiveCount = 0),
    latestReviews: List<Review> = emptyList(),
    openToCollaboration: Boolean = false,
): UserProfile = UserProfile(
    person = person,
    about = "Люблю данные",
    interests = ProfileInterests(projectTypes = emptySet(), topics = listOf("ML")),
    skills = listOf("Kotlin"),
    documents = documents,
    works = works,
    worksCount = worksCount,
    reviews = reviews,
    latestReviews = latestReviews,
    isViewer = isViewer,
    openToCollaboration = openToCollaboration,
)

fun myProfile(
    person: Collaborator = person(),
    works: List<PortfolioWork> = emptyList(),
    documents: List<ProfileDocument> = emptyList(),
    queue: List<VerificationItem> = emptyList(),
): MyProfile = MyProfile(
    profile = userProfile(person, works, documents = documents),
    allWorks = works,
    verificationQueue = queue,
)

/** A [MyProfileRepository] that records what it is asked and answers as the test says; the profile is state, like the real one. */
class FakeMyProfileRepository : MyProfileRepository {
    val profile = MutableStateFlow(myProfile())
    val calls = mutableListOf<String>()

    /** When set, observing fails with it, as a first load that failed does. */
    var observeFailure: Throwable? = null
    var onRefresh: suspend () -> Unit = {}
    var onUpdateProfile: suspend (ProfileDraft) -> MyProfile = { profile.value }
    var onCreateWork: suspend (WorkDraft) -> PortfolioChange<PortfolioWork> =
        { PortfolioChange.Saved(work(title = it.title), sent = it.sendsForVerification) }
    var onCreateDocument: suspend (DocumentDraft) -> PortfolioChange<ProfileDocument> =
        { PortfolioChange.Saved(document(title = it.title), sent = it.sendsForVerification) }
    var onGetMyWork: suspend (WorkId) -> PortfolioWork? = { work(it.value) }
    var onGetMyDocument: suspend (DocumentId) -> ProfileDocument? = { document(it.value) }
    var onUpdateWork: suspend (WorkId, WorkDraft, Int) -> EditOutcome<PortfolioWork> =
        { id, draft, _ -> EditOutcome.Done(work(id.value, title = draft.title)) }
    var onUpdateDocument: suspend (DocumentId, DocumentDraft, Int) -> EditOutcome<ProfileDocument> =
        { id, draft, _ -> EditOutcome.Done(document(id.value, title = draft.title)) }
    var onSubmit: suspend (VerificationSubject) -> EditOutcome<Unit> = { EditOutcome.Done(Unit) }
    var onDelete: suspend (VerificationSubject) -> EditOutcome<Unit> = { EditOutcome.Done(Unit) }
    var results: Flow<List<VerificationResult>> = flowOf(emptyList())
    var onMarkSeen: suspend (Set<VerificationResultId>) -> Unit = {}

    val updatedProfiles = mutableListOf<ProfileDraft>()
    val createdWorks = mutableListOf<WorkDraft>()
    val createdDocuments = mutableListOf<DocumentDraft>()
    val updatedWorks = mutableListOf<Triple<WorkId, WorkDraft, Int>>()
    val updatedDocuments = mutableListOf<Triple<DocumentId, DocumentDraft, Int>>()
    val submitted = mutableListOf<VerificationSubject>()
    val deleted = mutableListOf<VerificationSubject>()
    val seen = mutableListOf<Set<VerificationResultId>>()

    override fun observeMyProfile(): Flow<MyProfile> {
        calls += "observe"
        val failure = observeFailure
        return if (failure != null) flow { throw failure } else profile
    }

    override suspend fun refreshMyProfile() {
        calls += "refresh"
        onRefresh()
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
        onMarkSeen(ids)
    }
}

/** A [ProfileRepository] that answers as the test says. */
class FakeProfileRepository : ProfileRepository {
    override var capabilities = ProfileCapabilities(verificationStatuses = false, interestTypes = false)

    val calls = mutableListOf<String>()
    var onGetProfile: suspend (UserId) -> UserProfile? = { userProfile(isViewer = false) }
    var onGetWorks: suspend (UserId) -> UserWorks? = { UserWorks(emptyList(), isViewer = false) }
    var onGetUserReviews: suspend (UserId) -> UserReviews? =
        { UserReviews(ReviewsSummary(null, 0, 0), ReviewsPage(emptyList(), hasMore = false)) }
    var onGetReviewsPage: suspend (UserId, Int) -> ReviewsPage? = { _, _ -> ReviewsPage(emptyList(), hasMore = false) }
    var onGetReview: suspend (UserId, ReviewId) -> Review? = { _, id -> review(id.value) }

    val reviewPageOffsets = mutableListOf<Int>()

    override suspend fun getProfile(id: UserId): UserProfile? {
        calls += "getProfile"
        return onGetProfile(id)
    }

    override suspend fun getWorks(id: UserId): UserWorks? {
        calls += "getWorks"
        return onGetWorks(id)
    }

    override suspend fun getUserReviews(id: UserId): UserReviews? {
        calls += "getUserReviews"
        return onGetUserReviews(id)
    }

    override suspend fun getReviewsPage(id: UserId, offset: Int): ReviewsPage? {
        calls += "getReviewsPage"
        reviewPageOffsets += offset
        return onGetReviewsPage(id, offset)
    }

    override suspend fun getReview(userId: UserId, reviewId: ReviewId): Review? {
        calls += "getReview"
        return onGetReview(userId, reviewId)
    }
}

/** The dependencies of the screens over the fakes, like the app wires them. */
fun profileUiDependencies(
    mine: FakeMyProfileRepository = FakeMyProfileRepository(),
    others: FakeProfileRepository = FakeProfileRepository(),
    capabilities: ProfileCapabilities = ProfileCapabilities(verificationStatuses = false, interestTypes = false),
): ProfileUiDependencies = ProfileUiDependencies(
    getUserProfile = GetUserProfileUseCase(others),
    getUserWorks = GetUserWorksUseCase(others),
    getUserReviews = GetUserReviewsUseCase(others),
    getReviewsPage = GetReviewsPageUseCase(others),
    getReview = GetReviewUseCase(others),
    observeMyProfile = ObserveMyProfileUseCase(mine),
    refreshMyProfile = RefreshMyProfileUseCase(mine),
    updateProfile = UpdateProfileUseCase(mine),
    addWork = AddWorkUseCase(mine),
    addDocument = AddDocumentUseCase(mine),
    getMyWork = GetMyWorkUseCase(mine),
    getMyDocument = GetMyDocumentUseCase(mine),
    saveWork = SaveWorkUseCase(mine),
    saveDocument = SaveDocumentUseCase(mine),
    deletePortfolioItem = DeletePortfolioItemUseCase(mine),
    observeVerificationResults = ObserveVerificationResultsUseCase(mine),
    observeHasUnseenResults = ObserveHasUnseenResultsUseCase(mine),
    markResultsSeen = MarkResultsSeenUseCase(mine),
    capabilities = capabilities,
)
