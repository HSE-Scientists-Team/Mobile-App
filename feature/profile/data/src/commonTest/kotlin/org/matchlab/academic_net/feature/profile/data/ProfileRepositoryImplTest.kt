package org.matchlab.academic_net.feature.profile.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.profile.domain.AboutDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.MyProfile
import org.matchlab.academic_net.feature.profile.domain.MyProfileRepository
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.ProfileInterests
import org.matchlab.academic_net.feature.profile.domain.ProfileRepository
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.ReviewerRole
import org.matchlab.academic_net.feature.profile.domain.ReviewsSummary
import org.matchlab.academic_net.feature.profile.domain.UserProfile
import org.matchlab.academic_net.feature.profile.domain.UserReviews
import org.matchlab.academic_net.feature.profile.domain.VerificationOutcome
import org.matchlab.academic_net.feature.profile.domain.VerificationResult
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkId

private val User = UserSummary(UserId("u-1"), "Name")
private val Summary = ReviewsSummary(average = null, count = 0, positiveCount = 0)
private val Profile = UserProfile(
    person = Collaborator(User, "University", "study", "field"),
    about = "About",
    interests = ProfileInterests(emptySet(), emptyList()),
    skills = emptyList(),
    documents = emptyList(),
    works = emptyList(),
    worksCount = 0,
    reviews = Summary,
    latestReviews = emptyList(),
    isViewer = false,
)
private val Work = PortfolioWork(WorkId("w"), ProjectType.Practical, "T", "D", null, null, VerificationStatus.Unconfirmed)
private val Document = ProfileDocument(DocumentId("d"), DocumentKind.Education, "T", "I", 2024, VerificationStatus.Pending)
private val SomeReview = Review(ReviewId("r"), User, ReviewerRole.Participant, "P", YearMonth(2026, 1), "T", 5, "X")
private val Mine = MyProfile(Profile.copy(isViewer = true), emptyList())
private val Result = VerificationResult(
    VerificationResultId("v"),
    VerificationSubject.Work(WorkId("w")),
    "T",
    VerificationOutcome.Verified,
    Instant.fromEpochSeconds(0),
    isSeen = false,
)

/** Answers with canned values and records what it was asked. */
private class RecordingDataSource : ProfileDataSource {
    var failure: Throwable? = null
    val calls = mutableListOf<String>()
    val my = MutableStateFlow(Mine)
    val results = MutableStateFlow(listOf(Result))

    private fun record(call: String) {
        calls += call
        failure?.let { throw it }
    }

    override suspend fun getProfile(id: UserId): UserProfile? = record("getProfile ${id.value}").let { Profile }

    override suspend fun getWorks(id: UserId): List<PortfolioWork>? =
        record("getWorks ${id.value}").let { listOf(Work) }

    override suspend fun getReviews(id: UserId): UserReviews? =
        record("getReviews ${id.value}").let { UserReviews(Summary, listOf(SomeReview)) }

    override suspend fun getReview(id: ReviewId): Review? {
        record("getReview ${id.value}")
        return null
    }

    override fun observeMyProfile(): Flow<MyProfile> = my

    override suspend fun updateAbout(draft: AboutDraft): MyProfile =
        record("updateAbout ${draft.about}").let { Mine }

    override suspend fun addWork(draft: WorkDraft): PortfolioWork = record("addWork ${draft.title}").let { Work }

    override suspend fun addDocument(draft: DocumentDraft): ProfileDocument =
        record("addDocument ${draft.title}").let { Document }

    override fun observeVerificationResults(): Flow<List<VerificationResult>> = results

    override suspend fun markResultsSeen(ids: Set<VerificationResultId>) {
        record("markResultsSeen ${ids.map { it.value }.sorted()}")
    }
}

class ProfileRepositoryImplTest {
    private val source = RecordingDataSource()
    private val impl = ProfileRepositoryImpl(source)

    @Test
    fun oneInstanceIsBothRepositories() {
        val reading: ProfileRepository = impl
        val mine: MyProfileRepository = impl

        assertSame<Any>(reading, mine)
    }

    @Test
    fun readingPassesTheRequestsAndTheAnswers() = runTest {
        assertSame(Profile, impl.getProfile(UserId("u-1")))
        assertEquals(listOf(Work), impl.getWorks(UserId("u-2")))
        assertEquals(UserReviews(Summary, listOf(SomeReview)), impl.getReviews(UserId("u-3")))
        assertNull(impl.getReview(ReviewId("r-4")))

        assertEquals(
            listOf("getProfile u-1", "getWorks u-2", "getReviews u-3", "getReview r-4"),
            source.calls,
        )
    }

    @Test
    fun editingPassesTheDraftsAndTheAnswers() = runTest {
        assertSame(Mine, impl.updateAbout(AboutDraft(about = "text")))
        assertSame(Work, impl.addWork(WorkDraft(title = "Work")))
        assertSame(Document, impl.addDocument(DocumentDraft(title = "Doc")))
        impl.markResultsSeen(setOf(VerificationResultId("b"), VerificationResultId("a")))

        assertEquals(
            listOf("updateAbout text", "addWork Work", "addDocument Doc", "markResultsSeen [a, b]"),
            source.calls,
        )
    }

    @Test
    fun observingPassesTheFlowsOfTheSource() = runTest {
        assertEquals(Mine, impl.observeMyProfile().first())
        assertEquals(listOf(Result), impl.observeVerificationResults().first())

        source.results.value = emptyList()

        assertEquals(emptyList(), impl.observeVerificationResults().first())
    }

    @Test
    fun errorsOfTheDataSourceGoThrough() = runTest {
        source.failure = IllegalStateException("offline")

        val error = assertFailsWith<IllegalStateException> { impl.getProfile(UserId("u-1")) }

        assertEquals("offline", error.message)
        assertFailsWith<IllegalStateException> { impl.addWork(WorkDraft(title = "Work")) }
    }
}
