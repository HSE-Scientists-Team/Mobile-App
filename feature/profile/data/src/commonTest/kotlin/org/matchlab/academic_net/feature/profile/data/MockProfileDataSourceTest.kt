package org.matchlab.academic_net.feature.profile.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.profile.data.mock.MockModerator
import org.matchlab.academic_net.feature.profile.data.mock.MockProfileDataSource
import org.matchlab.academic_net.feature.profile.domain.AboutDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.ReviewerRole
import org.matchlab.academic_net.feature.profile.domain.VerificationOutcome
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.mockserver.MockCheckState
import org.matchlab.academic_net.mockserver.MockPeople
import org.matchlab.academic_net.mockserver.MockServerDocument
import org.matchlab.academic_net.mockserver.MockServerPerson
import org.matchlab.academic_net.mockserver.MockServerReview
import org.matchlab.academic_net.mockserver.MockServerWork
import org.matchlab.academic_net.utils.IdGenerator

private val Me = UserId("user-me")
private val Other = UserId("user-other")
private val Stranger = UserId("user-stranger")
private val Now = Instant.parse("2026-10-12T10:00:00Z")
private val ModerationDelay = 8.seconds

private object FixedClock : Clock {
    override fun now(): Instant = Now
}

private class SequentialIds : IdGenerator {
    private var next = 0
    override fun newId(): String = "id-${++next}"
}

private fun person(id: UserId, name: String = id.value) = MockServerPerson(
    id = id,
    name = name,
    university = "University",
    studyInfo = "study",
    field = "field",
    about = "About $name",
    interestTypes = setOf(ProjectType.Practical),
    interests = listOf("Interest"),
    skills = listOf("Skill"),
)

private fun work(
    id: String,
    owner: UserId,
    state: MockCheckState,
    link: String? = null,
    proof: Boolean = false,
    title: String = "Work $id",
) = MockServerWork(id, owner, ProjectType.Scientific, title, "Description $id", link, proof, state)

private fun document(
    id: String,
    owner: UserId,
    state: MockCheckState,
    education: Boolean = true,
    year: Int? = 2024,
) = MockServerDocument(id, owner, education, "Document $id", "Issuer", year, state)

private fun review(
    id: String,
    subject: UserId,
    rating: Int,
    finished: YearMonth,
    author: UserId = Stranger,
    asInitiator: Boolean = true,
) = MockServerReview(id, author, subject, asInitiator, "Project $id", finished, "Title $id", rating, "Text $id")

private fun TestScope.source(
    people: MockPeople = MockPeople.default,
    latencyMillis: Long = 0,
    moderator: MockModerator = MockModerator(),
) = MockProfileDataSource(
    scope = backgroundScope,
    people = people,
    moderator = moderator,
    moderationDelay = ModerationDelay,
    clock = FixedClock,
    ids = SequentialIds(),
    viewerId = Me,
    latencyMillis = latencyMillis,
)

/** A viewer without anything in the tables, so a test sees only what it adds. */
private fun emptyViewer(vararg more: MockServerPerson) = MockPeople(
    persons = listOf(person(Me, "Me"), person(Stranger, "Stranger")) + more,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MockProfileDataSourceReadingTest {
    @Test
    fun unknownPersonOrReviewIsNull() = runTest {
        val source = source()

        assertNull(source.getProfile(UserId("user-nobody")))
        assertNull(source.getWorks(UserId("user-nobody")))
        assertNull(source.getReviews(UserId("user-nobody")))
        assertNull(source.getReview(ReviewId("review-nobody")))
    }

    @Test
    fun profileHasThePersonAndTheTextsOfTheTable() = runTest {
        val profile = source().getProfile(UserId("user-arjun"))

        assertNotNull(profile)
        assertEquals("Арджун Мехта", profile.person.user.name)
        assertEquals("Stanford", profile.person.university)
        assertEquals("3 курс бакалавриата", profile.person.studyInfo)
        assertEquals("Computer Science", profile.person.field)
        assertEquals(listOf("Computer Vision", "Edge AI", "Робототехника"), profile.interests.topics)
        assertEquals(setOf(ProjectType.Scientific), profile.interests.projectTypes)
        assertEquals(listOf("Python", "TensorFlow", "PyTorch", "OpenCV"), profile.skills)
        assertFalse(profile.isViewer)
    }

    @Test
    fun profilePreviewsTheThreeNewestWorksAndCountsAll() = runTest {
        val profile = source().getProfile(UserId("user-arjun"))

        assertNotNull(profile)
        assertEquals(
            listOf(
                "Localized CV Pipelines on Edge Devices",
                "Сегментация коридоров на Raspberry Pi",
                "Прототип AR-навигации",
            ),
            profile.works.map { it.title },
        )
        assertEquals(4, profile.worksCount)
    }

    @Test
    fun profileWithFewWorksHasAllOfThemAndTheSameCount() = runTest {
        val people = emptyViewer().let {
            MockPeople(it.persons, works = listOf(work("w1", Stranger, MockCheckState.Verified)))
        }

        val profile = source(people).getProfile(Stranger)

        assertEquals(1, profile?.works?.size)
        assertEquals(1, profile?.worksCount)
    }

    @Test
    fun profilePreviewsTheTwoNewestReviewsAndSummarisesAll() = runTest {
        val profile = source().getProfile(UserId("user-arjun"))

        assertNotNull(profile)
        assertEquals(
            listOf("Довёл модели до демо", "Помогал команде с данными"),
            profile.latestReviews.map { it.title },
        )
        assertEquals(12, profile.reviews.count)
        assertEquals(11, profile.reviews.positiveCount)
        assertEquals(4.75, profile.reviews.average)
    }

    @Test
    fun reviewSummaryIsCountedFromTheRealList() = runTest {
        val people = MockPeople(
            persons = emptyViewer().persons,
            reviews = listOf(
                review("r1", Stranger, rating = 5, finished = YearMonth(2026, 1)),
                review("r2", Stranger, rating = 3, finished = YearMonth(2026, 2)),
                review("r3", Stranger, rating = 4, finished = YearMonth(2026, 3)),
            ),
        )

        val reviews = source(people).getReviews(Stranger)

        assertNotNull(reviews)
        assertEquals(3, reviews.summary.count)
        assertEquals(2, reviews.summary.positiveCount)
        assertEquals(4.0, reviews.summary.average)
    }

    @Test
    fun personWithoutReviewsHasNoAverage() = runTest {
        val profile = source(emptyViewer()).getProfile(Stranger)

        assertNotNull(profile)
        assertNull(profile.reviews.average)
        assertEquals(0, profile.reviews.count)
        assertTrue(profile.latestReviews.isEmpty())
        assertEquals(0, source(emptyViewer()).getReviews(Stranger)?.items?.size)
    }

    @Test
    fun reviewsAreNewestFirstAndCarryTheirAuthor() = runTest {
        val people = MockPeople(
            persons = emptyViewer(person(Other, "Other")).persons,
            reviews = listOf(
                review("old", Stranger, 5, YearMonth(2025, 5), author = Other, asInitiator = false),
                review("new", Stranger, 4, YearMonth(2026, 2), author = Other),
            ),
        )

        val items = source(people).getReviews(Stranger)?.items.orEmpty()

        assertEquals(listOf("new", "old"), items.map { it.id.value })
        assertEquals("Other", items.first().author.name)
        assertEquals(ReviewerRole.Initiator, items.first().authorRole)
        assertEquals(ReviewerRole.Participant, items.last().authorRole)
        assertEquals(YearMonth(2026, 2), items.first().projectFinished)
    }

    @Test
    fun reviewIsFoundByIdWithItsFullText() = runTest {
        val review = source().getReview(ReviewId("review-me-1"))

        assertNotNull(review)
        assertEquals("Проф. Елена Васкес", review.author.name)
        assertEquals(ReviewerRole.Initiator, review.authorRole)
        assertEquals("AI-навигация по кампусу", review.projectTitle)
        assertEquals(YearMonth(2026, 3), review.projectFinished)
        assertEquals(5, review.rating)
        assertTrue(review.text.endsWith("с самостоятельной инженерной позицией."))
    }

    @Test
    fun otherPeopleSeeOnlyConfirmedOrUnconfirmed() = runTest {
        val people = MockPeople(
            persons = emptyViewer().persons,
            works = listOf(
                work("w-pending", Stranger, MockCheckState.Pending, link = "https://github.com/a"),
                work("w-rejected", Stranger, MockCheckState.Rejected),
                work("w-verified", Stranger, MockCheckState.Verified),
                work("w-none", Stranger, MockCheckState.Unconfirmed),
            ),
            documents = listOf(
                document("d-pending", Stranger, MockCheckState.Pending, education = false),
                document("d-rejected", Stranger, MockCheckState.Rejected, education = false),
            ),
        )

        val works = source(people).getWorks(Stranger).orEmpty()
        val profile = source(people).getProfile(Stranger)

        assertEquals(
            listOf(
                VerificationStatus.Unconfirmed,
                VerificationStatus.Unconfirmed,
                VerificationStatus.Verified,
                VerificationStatus.Unconfirmed,
            ),
            works.map { it.status },
        )
        assertEquals(
            listOf(VerificationStatus.Unconfirmed, VerificationStatus.Unconfirmed),
            profile?.documents?.map { it.status },
        )
    }

    @Test
    fun theOwnerSeesTheRealStates() = runTest {
        val people = MockPeople(
            persons = emptyViewer().persons,
            works = listOf(
                work("w-pending", Me, MockCheckState.Pending, link = "https://github.com/a"),
                work("w-rejected", Me, MockCheckState.Rejected),
            ),
            documents = listOf(document("d-pending", Me, MockCheckState.Pending, education = false)),
        )

        val profile = source(people).getProfile(Me)

        assertNotNull(profile)
        assertTrue(profile.isViewer)
        assertEquals(
            listOf(VerificationStatus.Pending, VerificationStatus.Rejected),
            profile.works.map { it.status },
        )
        assertEquals(listOf(VerificationStatus.Pending), profile.documents.map { it.status })
    }

    @Test
    fun viewersSeededWorkIsRejectedForThemAndUnconfirmedForOthers() = runTest {
        val source = source()

        val mine = source.getWorks(Me).orEmpty().first { it.title == "Прототип AR-навигации" }
        val arjuns = source.getWorks(UserId("user-arjun")).orEmpty().first { it.title == "Прототип AR-навигации" }

        assertEquals(VerificationStatus.Rejected, mine.status)
        assertEquals(VerificationStatus.Unconfirmed, arjuns.status)
    }

    @Test
    fun worksAreNewestFirstAndFullListHasAllOfThem() = runTest {
        val works = source().getWorks(UserId("user-arjun")).orEmpty()

        assertEquals(4, works.size)
        assertEquals("Учёт оборудования лаборатории", works.last().title)
    }

    @Test
    fun documentsAreEducationFirstThenTheNewestYear() = runTest {
        val people = MockPeople(
            persons = emptyViewer().persons,
            documents = listOf(
                document("cert-2025", Stranger, MockCheckState.Verified, education = false, year = 2025),
                document("edu-2020", Stranger, MockCheckState.Verified, year = 2020),
                document("edu-none", Stranger, MockCheckState.Verified, year = null),
                document("edu-2023", Stranger, MockCheckState.Verified, year = 2023),
                document("cert-2021", Stranger, MockCheckState.Verified, education = false, year = 2021),
            ),
        )

        val documents = source(people).getProfile(Stranger)?.documents.orEmpty()

        assertEquals(
            listOf("edu-2023", "edu-2020", "edu-none", "cert-2025", "cert-2021"),
            documents.map { it.id.value },
        )
        assertEquals(DocumentKind.Education, documents.first().kind)
        assertEquals(DocumentKind.Certificate, documents.last().kind)
        assertEquals(2023, documents.first().year)
    }

    @Test
    fun sourceOfAWorkIsDerivedFromItsLink() = runTest {
        val people = MockPeople(
            persons = emptyViewer().persons,
            works = listOf(
                work("doi", Stranger, MockCheckState.Verified, link = "https://doi.org/10.1234/cv"),
                work("git", Stranger, MockCheckState.Verified, link = "https://github.com/a/b"),
                work("arx", Stranger, MockCheckState.Verified, link = "https://arxiv.org/abs/1"),
                work("site", Stranger, MockCheckState.Verified, link = "https://www.lab.example.com/x"),
                work("none", Stranger, MockCheckState.Unconfirmed),
            ),
        )

        val works = source(people).getWorks(Stranger).orEmpty()

        assertEquals(listOf("DOI 10.1234/cv", "GitHub", "arXiv", "lab.example.com", null), works.map { it.source })
        assertEquals("https://github.com/a/b", works[1].link)
        assertNull(works.last().link)
    }

    @Test
    fun personIsVerifiedByAConfirmedEducationDocument() = runTest {
        val people = MockPeople(
            persons = emptyViewer(person(Other, "Other")).persons,
            documents = listOf(
                document("d1", Stranger, MockCheckState.Verified),
                document("d2", Other, MockCheckState.Verified, education = false),
            ),
        )
        val source = source(people)

        assertEquals(true, source.getProfile(Stranger)?.person?.user?.isVerified)
        assertEquals(false, source.getProfile(Other)?.person?.user?.isVerified)
    }

    @Test
    fun seededPeopleKeepTheStatusOfTheOtherMocks() = runTest {
        val source = source()

        assertEquals(true, source.getProfile(UserId("user-elena"))?.person?.user?.isVerified)
        assertEquals(false, source.getProfile(UserId("user-ilyas"))?.person?.user?.isVerified)
        assertEquals(true, source.getProfile(Me)?.person?.user?.isVerified)
    }

    @Test
    fun answersAfterTheLatency() = runTest {
        source(latencyMillis = 250).getProfile(Me)

        assertEquals(250, testScheduler.currentTime)
    }

    @Test
    fun unknownPersonWaitsForTheLatencyToo() = runTest {
        source(latencyMillis = 100).getReviews(UserId("user-nobody"))

        assertEquals(100, testScheduler.currentTime)
    }

    @Test
    fun zeroLatencyDoesNotWait() = runTest {
        source().getProfile(Me)

        assertEquals(0, testScheduler.currentTime)
    }

    @Test
    fun defaultsAreTheSeededPeopleOfTheViewerAndADemoDelay() = runTest {
        val defaults = MockProfileDataSource(scope = backgroundScope)

        val profile = defaults.getProfile(Me)

        assertEquals("Алексей Морозов", profile?.person?.user?.name)
        assertEquals(300, testScheduler.currentTime)
        // The seeded certificate waits for the moderator for 8 seconds by default.
        assertEquals(1, defaults.observeVerificationResults().first().size)
        advanceTimeBy(8.seconds)
        runCurrent()
        assertEquals(2, defaults.observeVerificationResults().first().size)
    }

    @Test
    fun myProfileAppearsAfterTheLatency() = runTest {
        source(latencyMillis = 300).observeMyProfile().first()

        assertEquals(300, testScheduler.currentTime)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MockProfileDataSourceMyProfileTest {
    private suspend fun MockProfileDataSource.myProfile() = observeMyProfile().first()

    private suspend fun TestScope.finishModeration() {
        advanceTimeBy(ModerationDelay)
        runCurrent()
    }

    @Test
    fun myProfileIsTheViewersOwnWithTheQueueOfWhatIsChecked() = runTest {
        val mine = source().myProfile()

        assertTrue(mine.profile.isViewer)
        assertEquals("Алексей Морозов", mine.profile.person.user.name)
        assertEquals(
            listOf("Сертификат Deep Learning" to VerificationStatus.Pending, "Прототип AR-навигации" to VerificationStatus.Rejected),
            mine.verificationQueue.map { it.title to it.status },
        )
        assertIs<VerificationSubject.Document>(mine.verificationQueue.first().subject)
        assertIs<VerificationSubject.Work>(mine.verificationQueue.last().subject)
    }

    @Test
    fun viewerStartsWithAnUnseenRejectionAndNothingElse() = runTest {
        val results = source().observeVerificationResults().first()

        assertEquals(1, results.size)
        assertEquals("Прототип AR-навигации", results.single().subjectTitle)
        assertEquals(VerificationOutcome.Rejected, results.single().outcome)
        assertFalse(results.single().isSeen)
        assertEquals(Now - 1.hours, results.single().decidedAt)
    }

    @Test
    fun updateAboutReplacesTheTextInterestsAndSkillsOfTheViewerOnly() = runTest {
        val source = source()
        val before = source.getProfile(UserId("user-arjun"))

        val updated = source.updateAbout(
            AboutDraft(
                about = "New text",
                interestTypes = setOf(ProjectType.Practical),
                interests = listOf("Edge AI"),
                skills = listOf("Kotlin", "Python"),
            ),
        )

        assertEquals("New text", updated.profile.about)
        assertEquals(setOf(ProjectType.Practical), updated.profile.interests.projectTypes)
        assertEquals(listOf("Edge AI"), updated.profile.interests.topics)
        assertEquals(listOf("Kotlin", "Python"), updated.profile.skills)
        assertEquals(updated, source.myProfile())
        assertEquals("New text", source.getProfile(Me)?.about)
        assertEquals(before, source.getProfile(UserId("user-arjun")))
    }

    @Test
    fun updateAboutKeepsDocumentsAndWorks() = runTest {
        val source = source()
        val before = source.myProfile()

        val updated = source.updateAbout(AboutDraft(about = "x"))

        assertEquals(before.profile.documents, updated.profile.documents)
        assertEquals(before.profile.works, updated.profile.works)
        assertEquals(before.verificationQueue, updated.verificationQueue)
    }

    @Test
    fun workWithoutLinkAndProofIsUnconfirmedAtOnceAndNobodyChecksIt() = runTest {
        val source = source(emptyViewer())

        val added = source.addWork(WorkDraft(title = "Title", description = "Text"))
        finishModeration()

        assertEquals(VerificationStatus.Unconfirmed, added.status)
        assertNull(added.link)
        assertNull(added.source)
        assertTrue(source.myProfile().verificationQueue.isEmpty())
        assertTrue(source.observeVerificationResults().first().isEmpty())
    }

    @Test
    fun addedWorkComesFirstOfTheWorks() = runTest {
        val source = source(emptyViewer())
        source.addWork(WorkDraft(type = ProjectType.Practical, title = "First", description = "Text"))
        source.addWork(WorkDraft(title = "Second", description = "Text"))

        val works = source.getWorks(Me).orEmpty()

        assertEquals(listOf("Second", "First"), works.map { it.title })
        assertEquals(ProjectType.Practical, works.last().type)
        assertEquals(2, source.getProfile(Me)?.worksCount)
    }

    @Test
    fun workWithATrustedLinkIsPendingThenConfirmed() = runTest {
        val source = source(emptyViewer())

        val added = source.addWork(
            WorkDraft(title = "Title", description = "Text", link = "https://doi.org/10.1/x"),
        )

        assertEquals(VerificationStatus.Pending, added.status)
        assertEquals("DOI 10.1/x", added.source)
        assertEquals(listOf(VerificationStatus.Pending), source.myProfile().verificationQueue.map { it.status })

        advanceTimeBy(ModerationDelay - 1.seconds)
        runCurrent()
        assertEquals(VerificationStatus.Pending, source.getWorks(Me)?.single()?.status)
        assertTrue(source.observeVerificationResults().first().isEmpty())

        finishModeration()
        assertEquals(VerificationStatus.Verified, source.getWorks(Me)?.single()?.status)
        assertTrue(source.myProfile().verificationQueue.isEmpty())
        val result = source.observeVerificationResults().first().single()
        assertEquals(VerificationOutcome.Verified, result.outcome)
        assertEquals(VerificationSubject.Work(WorkId(added.id.value)), result.subject)
        assertEquals("Title", result.subjectTitle)
        assertFalse(result.isSeen)
        assertEquals(Now, result.decidedAt)
    }

    @Test
    fun workWithAnUntrustedLinkAndNoProofIsRejected() = runTest {
        val source = source(emptyViewer())

        source.addWork(WorkDraft(title = "Title", description = "Text", link = "https://drive.example.com/a"))
        finishModeration()

        assertEquals(VerificationStatus.Rejected, source.getWorks(Me)?.single()?.status)
        assertEquals(
            listOf(VerificationStatus.Rejected),
            source.myProfile().verificationQueue.map { it.status },
        )
        assertEquals(
            VerificationOutcome.Rejected,
            source.observeVerificationResults().first().single().outcome,
        )
    }

    @Test
    fun workWithAProofIsConfirmedEvenWithAnUntrustedLink() = runTest {
        val source = source(emptyViewer())

        source.addWork(
            WorkDraft(
                title = "Title",
                description = "Text",
                link = "https://drive.example.com/a",
                proof = ProofAttachment("https://files.example.com/p.pdf"),
            ),
        )
        finishModeration()

        assertEquals(VerificationStatus.Verified, source.getWorks(Me)?.single()?.status)
    }

    @Test
    fun workWithOnlyAProofIsSentForVerification() = runTest {
        val source = source(emptyViewer())

        val added = source.addWork(
            WorkDraft(title = "Title", description = "Text", proof = ProofAttachment("https://files.example.com/p.pdf")),
        )

        assertEquals(VerificationStatus.Pending, added.status)
        assertNull(added.link)
    }

    @Test
    fun documentIsPendingThenConfirmedAndVerifiesThePerson() = runTest {
        val source = source(emptyViewer())
        assertEquals(false, source.myProfile().profile.person.user.isVerified)

        val added = source.addDocument(
            DocumentDraft(
                kind = DocumentKind.Education,
                title = "Диплом",
                issuer = "МГУ",
                year = "2025",
                proof = ProofAttachment("https://files.example.com/d.pdf"),
            ),
        )

        assertEquals(VerificationStatus.Pending, added.status)
        assertEquals(DocumentKind.Education, added.kind)
        assertEquals(2025, added.year)
        assertEquals("МГУ", added.issuer)
        assertEquals(false, source.myProfile().profile.person.user.isVerified)
        assertEquals(listOf(VerificationStatus.Pending), source.myProfile().verificationQueue.map { it.status })

        finishModeration()

        val mine = source.myProfile()
        assertEquals(true, mine.profile.person.user.isVerified)
        assertEquals(VerificationStatus.Verified, mine.profile.documents.single().status)
        assertTrue(mine.verificationQueue.isEmpty())
        val result = source.observeVerificationResults().first().single()
        assertEquals(VerificationOutcome.Verified, result.outcome)
        assertEquals(
            VerificationSubject.Document(DocumentId(added.id.value), DocumentKind.Education),
            result.subject,
        )
    }

    @Test
    fun certificateDoesNotVerifyThePerson() = runTest {
        val source = source(emptyViewer())

        source.addDocument(
            DocumentDraft(
                kind = DocumentKind.Certificate,
                title = "Сертификат",
                issuer = "Coursera",
                proof = ProofAttachment("https://files.example.com/c.pdf"),
            ),
        )
        finishModeration()

        val mine = source.myProfile()
        assertEquals(VerificationStatus.Verified, mine.profile.documents.single().status)
        assertNull(mine.profile.documents.single().year)
        assertEquals(false, mine.profile.person.user.isVerified)
    }

    @Test
    fun seededCertificateIsConfirmedAfterTheDelayWithAnUnseenResult() = runTest {
        val source = source()

        advanceTimeBy(ModerationDelay - 1.seconds)
        runCurrent()
        assertEquals(1, source.observeVerificationResults().first().size)

        advanceTimeBy(1.seconds)
        runCurrent()

        val results = source.observeVerificationResults().first()
        assertEquals(listOf("Сертификат Deep Learning", "Прототип AR-навигации"), results.map { it.subjectTitle })
        assertEquals(VerificationOutcome.Verified, results.first().outcome)
        assertFalse(results.first().isSeen)
        assertEquals(
            listOf("Прототип AR-навигации"),
            source.myProfile().verificationQueue.map { it.title },
        )
    }

    @Test
    fun resultsAreNewestFirst() = runTest {
        val source = source(emptyViewer())
        source.addWork(WorkDraft(title = "A", description = "t", link = "https://github.com/a"))
        advanceTimeBy(1.seconds)
        source.addWork(WorkDraft(title = "B", description = "t", link = "https://github.com/b"))
        advanceTimeBy(ModerationDelay)
        runCurrent()

        assertEquals(listOf("B", "A"), source.observeVerificationResults().first().map { it.subjectTitle })
    }

    @Test
    fun markingSeenChangesOnlyThoseResults() = runTest {
        val source = source(emptyViewer())
        source.addWork(WorkDraft(title = "A", description = "t", link = "https://github.com/a"))
        source.addWork(WorkDraft(title = "B", description = "t", link = "https://github.com/b"))
        finishModeration()
        val results = source.observeVerificationResults().first()
        val first = results.first().id

        source.markResultsSeen(setOf(first, VerificationResultId("unknown")))

        val after = source.observeVerificationResults().first()
        assertEquals(results.map { it.id }, after.map { it.id })
        assertEquals(listOf(true, false), after.map { it.isSeen })
    }

    @Test
    fun viewerWithoutAnyProfileRowFailsToObserve() = runTest {
        val source = source(MockPeople(persons = listOf(person(Stranger))))

        val failure = runCatching { source.myProfile() }.exceptionOrNull()

        assertNotNull(failure)
    }
}
