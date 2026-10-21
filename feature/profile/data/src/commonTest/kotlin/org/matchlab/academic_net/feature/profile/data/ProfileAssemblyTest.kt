package org.matchlab.academic_net.feature.profile.data

import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.VerificationItem
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

private fun workOf(id: String, status: VerificationStatus? = null, comment: String? = null): PortfolioWork =
    achievementDto(id = id, moderationComment = comment).toWork()!!.copy(status = status)

private fun documentOf(
    id: String,
    kind: DocumentKind = DocumentKind.Education,
    year: Int? = 2020,
    status: VerificationStatus? = null,
): ProfileDocument =
    achievementDto(
        id = id,
        kind = if (kind == DocumentKind.Education) "education" else "certificate",
        graduationYear = year,
    ).toDocument()!!.copy(status = status)

private fun reviewOf(id: String): Review = reviewDto(id = id).toReview(kotlinx.datetime.TimeZone.UTC)!!

/** What the repository makes of the parts of a profile: the previews, the order of the documents, the queue of the moderator. */
class ProfileAssemblyTest {
    private val record = profileDto().toRecord()

    // ---- the page ----

    @Test
    fun theProfilePageTakesTheRecordAsItIs() {
        val page = assembleProfile(record, Portfolio.Empty, emptyList(), isViewer = false)

        assertEquals(record.person, page.person)
        assertEquals(record.about, page.about)
        assertEquals(record.interests, page.interests)
        assertEquals(record.skills, page.skills)
        assertEquals(record.reviews, page.reviews)
        assertEquals(false, page.isViewer)
    }

    @Test
    fun theWorksArePreviewedByThreeAndCountedAll() {
        val works = (1..5).map { workOf("w$it") }

        val page = assembleProfile(record, Portfolio(works, emptyList()), emptyList(), isViewer = false)

        assertEquals(listOf("w1", "w2", "w3"), page.works.map { it.id.value })
        assertEquals(5, page.worksCount)
    }

    @Test
    fun fewWorksAreAllPreviewed() {
        val page = assembleProfile(record, Portfolio(listOf(workOf("w1"), workOf("w2")), emptyList()), emptyList(), isViewer = false)

        assertEquals(2, page.works.size)
        assertEquals(2, page.worksCount)
    }

    @Test
    fun theReviewsArePreviewedByTwo() {
        val reviews = (1..4).map { reviewOf("r$it") }

        val page = assembleProfile(record, Portfolio.Empty, reviews, isViewer = false)

        assertEquals(listOf("r1", "r2"), page.latestReviews.map { it.id.value })
    }

    @Test
    fun educationComesBeforeCertificatesAndTheNewestYearComesFirst() {
        val documents = listOf(
            documentOf("cert-old", DocumentKind.Certificate, year = 2019),
            documentOf("edu-old", DocumentKind.Education, year = 2018),
            documentOf("cert-new", DocumentKind.Certificate, year = 2025),
            documentOf("edu-new", DocumentKind.Education, year = 2024),
        )

        val page = assembleProfile(record, Portfolio(emptyList(), documents), emptyList(), isViewer = false)

        assertEquals(listOf("edu-new", "edu-old", "cert-new", "cert-old"), page.documents.map { it.id.value })
    }

    @Test
    fun documentsWithoutAYearComeLastInsideTheirKind() {
        val documents = listOf(
            documentOf("edu-none", DocumentKind.Education, year = null),
            documentOf("edu-2020", DocumentKind.Education, year = 2020),
            documentOf("cert-none", DocumentKind.Certificate, year = null),
            documentOf("cert-2021", DocumentKind.Certificate, year = 2021),
        )

        val page = assembleProfile(record, Portfolio(emptyList(), documents), emptyList(), isViewer = false)

        assertEquals(listOf("edu-2020", "edu-none", "cert-2021", "cert-none"), page.documents.map { it.id.value })
    }

    @Test
    fun documentsOfTheSameYearKeepTheOrderOfTheServer() {
        val documents = listOf(documentOf("b", year = 2020), documentOf("a", year = 2020), documentOf("c", year = 2020))

        val page = assembleProfile(record, Portfolio(emptyList(), documents), emptyList(), isViewer = false)

        assertEquals(listOf("b", "a", "c"), page.documents.map { it.id.value })
    }

    // ---- the viewer's page ----

    @Test
    fun theViewersPageHasEveryWorkAndIsTheirs() {
        val works = (1..5).map { workOf("w$it") }

        val mine = assembleMyProfile(record, Portfolio(works, emptyList()), emptyList())

        assertEquals(5, mine.allWorks.size)
        assertEquals(3, mine.profile.works.size)
        assertEquals(true, mine.profile.isViewer)
    }

    @Test
    fun withoutStatesTheQueueIsEmpty() {
        // API-GAP G12: a record whose state the server does not tell (null) is never waiting for a decision.
        val portfolio = Portfolio(listOf(workOf("w1", status = null)), listOf(documentOf("d1", status = null)))

        assertEquals(emptyList(), verificationQueue(portfolio))
        assertEquals(emptyList(), assembleMyProfile(record, portfolio, emptyList()).verificationQueue)
    }

    @Test
    fun pendingAndRejectedRecordsWaitInTheQueueDocumentsFirst() {
        val portfolio = Portfolio(
            works = listOf(
                workOf("w-pending", VerificationStatus.Pending),
                workOf("w-verified", VerificationStatus.Verified),
                workOf("w-rejected", VerificationStatus.Rejected, comment = "Нужна ссылка"),
                workOf("w-new", VerificationStatus.Unconfirmed),
            ),
            documents = listOf(
                documentOf("d-old", year = 2018, status = VerificationStatus.Rejected),
                documentOf("d-new", year = 2024, status = VerificationStatus.Pending),
                documentOf("d-ok", year = 2025, status = VerificationStatus.Verified),
            ),
        )

        val queue = verificationQueue(portfolio)

        assertEquals(
            listOf(
                VerificationItem(VerificationSubject.Document(DocumentId("d-new"), DocumentKind.Education), "Статья о графах", VerificationStatus.Pending),
                VerificationItem(VerificationSubject.Document(DocumentId("d-old"), DocumentKind.Education), "Статья о графах", VerificationStatus.Rejected),
                VerificationItem(VerificationSubject.Work(WorkId("w-pending")), "Статья о графах", VerificationStatus.Pending),
                VerificationItem(VerificationSubject.Work(WorkId("w-rejected")), "Статья о графах", VerificationStatus.Rejected, "Нужна ссылка"),
            ),
            queue,
        )
    }

    // ---- the reviews seen so far ----

    @Test
    fun aReviewThatWasSeenIsFoundAgainByThePersonAndTheId() = runTest {
        val cache = ReviewsCache()
        val seen = reviewOf("r1")

        cache.addAll(UserId("p1"), listOf(seen, reviewOf("r2")))

        assertSame(seen, cache.find(UserId("p1"), ReviewId("r1")))
        assertEquals("r2", cache.find(UserId("p1"), ReviewId("r2"))?.id?.value)
    }

    @Test
    fun theReviewsOfOnePersonAreNotFoundUnderAnother() = runTest {
        val cache = ReviewsCache()
        cache.addAll(UserId("p1"), listOf(reviewOf("r1")))

        assertNull(cache.find(UserId("p2"), ReviewId("r1")))
        assertNull(cache.find(UserId("p1"), ReviewId("unknown")))
        assertNull(cache.find(UserId("nobody"), ReviewId("r1")))
    }

    @Test
    fun aReviewSeenAgainReplacesTheOldCopy() = runTest {
        val cache = ReviewsCache()
        cache.addAll(UserId("p1"), listOf(reviewOf("r1").copy(text = "старый")))

        cache.addAll(UserId("p1"), listOf(reviewOf("r1").copy(text = "новый")))

        assertEquals("новый", cache.find(UserId("p1"), ReviewId("r1"))?.text)
    }

    @Test
    fun noReviewsAddNothing() = runTest {
        val cache = ReviewsCache()

        cache.addAll(UserId("p1"), emptyList())

        assertNull(cache.find(UserId("p1"), ReviewId("r1")))
    }

    @Test
    fun theViewersMarkIsKeptWithTheReview() = runTest {
        val cache = ReviewsCache()
        val mine = reviewOf("r1").copy(author = UserSummary(UserId("viewer"), "Я"), isByViewer = true)

        cache.addAll(UserId("p1"), listOf(mine))

        assertEquals(true, cache.find(UserId("p1"), ReviewId("r1"))?.isByViewer)
    }
}
