package org.matchlab.academic_net.feature.profile.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.EditOutcome
import org.matchlab.academic_net.feature.profile.domain.MyProfile
import org.matchlab.academic_net.feature.profile.domain.PortfolioChange
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileCapabilities
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.ProfileDraft
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [ProfileRepositoryImpl] over a scripted [ProfileDataSource]: what it asks, in which order, and what it makes of the answers. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryImplTest {
    private val viewer = UserId(ViewerRaw)
    private val other = UserId(OtherRaw)

    private class FakeSource : ProfileDataSource {
        override var capabilities = ProfileCapabilities(verificationStatuses = false, interestTypes = false)
        val calls = mutableListOf<String>()

        var onGetProfile: suspend (UserId) -> ProfileRecord? = { record(it.value) }
        var onGetPortfolio: suspend (UserId) -> Portfolio? = { Portfolio.Empty }
        var onGetReviews: suspend (UserId, Int, Int) -> List<Review>? = { _, _, _ -> emptyList() }
        var onGetMyProfile: suspend () -> ProfileRecord? = { record(ViewerRaw) }
        var onUpdateMyProfile: suspend (ProfileDraft) -> ProfileRecord = { record(ViewerRaw, name = it.name) }
        var onGetMyPortfolio: suspend () -> Portfolio = { Portfolio.Empty }
        var onGetMyWork: suspend (WorkId) -> PortfolioWork? = { null }
        var onGetMyDocument: suspend (DocumentId) -> ProfileDocument? = { null }
        var onCreateWork: suspend (WorkDraft) -> PortfolioWork = { achievementDto(id = "new-w", title = it.title).toWork()!! }
        var onCreateDocument: suspend (DocumentDraft) -> ProfileDocument =
            { achievementDto(id = "new-d", kind = "education", title = it.title).toDocument()!! }
        var onUpdateWork: suspend (WorkId, WorkDraft, Int) -> EditOutcome<PortfolioWork> =
            { id, draft, _ -> EditOutcome.Done(achievementDto(id = id.value, title = draft.title).toWork()!!) }
        var onUpdateDocument: suspend (DocumentId, DocumentDraft, Int) -> EditOutcome<ProfileDocument> =
            { id, draft, _ -> EditOutcome.Done(achievementDto(id = id.value, kind = "education", title = draft.title).toDocument()!!) }
        var onSubmit: suspend (VerificationSubject) -> EditOutcome<Unit> = { EditOutcome.Done(Unit) }
        var onDelete: suspend (VerificationSubject) -> EditOutcome<Unit> = { EditOutcome.Done(Unit) }

        val submitted = mutableListOf<VerificationSubject>()
        val reviewRequests = mutableListOf<Triple<UserId, Int, Int>>()

        override suspend fun getProfile(id: UserId): ProfileRecord? {
            calls += "getProfile"
            return onGetProfile(id)
        }

        override suspend fun getPortfolio(id: UserId): Portfolio? {
            calls += "getPortfolio"
            return onGetPortfolio(id)
        }

        override suspend fun getReviews(id: UserId, offset: Int, limit: Int): List<Review>? {
            calls += "getReviews"
            reviewRequests += Triple(id, offset, limit)
            return onGetReviews(id, offset, limit)
        }

        override suspend fun getMyProfile(): ProfileRecord? {
            calls += "getMyProfile"
            return onGetMyProfile()
        }

        override suspend fun updateMyProfile(draft: ProfileDraft): ProfileRecord {
            calls += "updateMyProfile"
            return onUpdateMyProfile(draft)
        }

        override suspend fun getMyPortfolio(): Portfolio {
            calls += "getMyPortfolio"
            return onGetMyPortfolio()
        }

        override suspend fun getMyWork(id: WorkId): PortfolioWork? = onGetMyWork(id)

        override suspend fun getMyDocument(id: DocumentId): ProfileDocument? = onGetMyDocument(id)

        override suspend fun createWork(draft: WorkDraft): PortfolioWork {
            calls += "createWork"
            return onCreateWork(draft)
        }

        override suspend fun createDocument(draft: DocumentDraft): ProfileDocument {
            calls += "createDocument"
            return onCreateDocument(draft)
        }

        override suspend fun updateWork(id: WorkId, draft: WorkDraft, expectedRevision: Int): EditOutcome<PortfolioWork> {
            calls += "updateWork"
            return onUpdateWork(id, draft, expectedRevision)
        }

        override suspend fun updateDocument(id: DocumentId, draft: DocumentDraft, expectedRevision: Int): EditOutcome<ProfileDocument> {
            calls += "updateDocument"
            return onUpdateDocument(id, draft, expectedRevision)
        }

        override suspend fun submit(subject: VerificationSubject): EditOutcome<Unit> {
            calls += "submit"
            submitted += subject
            return onSubmit(subject)
        }

        override suspend fun delete(subject: VerificationSubject): EditOutcome<Unit> {
            calls += "delete"
            return onDelete(subject)
        }
    }

    private val source = FakeSource()

    private fun repository(pageSize: Int = 20) = ProfileRepositoryImpl(source, viewer, pageSize)

    private fun reviews(count: Int, from: Int = 1, author: String = OtherRaw): List<Review> =
        List(count) { reviewDto(id = "r${from + it}", authorId = author).toReview(TimeZone.UTC)!! }

    // ---- the profile page of somebody else ----

    @Test
    fun aProfilePageAsksItsThreePartsAtOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        source.onGetProfile = { gate.await(); record(it.value) }
        source.onGetPortfolio = { gate.await(); Portfolio.Empty }
        source.onGetReviews = { _, _, _ -> gate.await(); emptyList() }

        val page = async { repository().getProfile(other) }
        runCurrent()

        assertEquals(setOf("getProfile", "getPortfolio", "getReviews"), source.calls.toSet(), "none waits for another")
        gate.complete(Unit)
        assertEquals(OtherRaw, page.await()?.person?.user?.id?.value)
    }

    @Test
    fun theReviewsOfAPageAreAskedByTwoFromTheStart() = runTest {
        repository().getProfile(other)

        assertEquals(listOf(Triple(other, 0, 2)), source.reviewRequests)
    }

    @Test
    fun aPageIsAssembledFromItsParts() = runTest {
        source.onGetPortfolio = {
            Portfolio(
                works = (1..5).map { achievementDto(id = "w$it").toWork()!! },
                documents = listOf(achievementDto(id = "d1", kind = "education", graduationYear = 2024).toDocument()!!),
            )
        }
        source.onGetReviews = { _, _, _ -> reviews(2) }

        val page = repository().getProfile(other)!!

        assertEquals(3, page.works.size)
        assertEquals(5, page.worksCount)
        assertEquals(listOf("d1"), page.documents.map { it.id.value })
        assertEquals(listOf("r1", "r2"), page.latestReviews.map { it.id.value })
        assertFalse(page.isViewer)
    }

    @Test
    fun aPersonTheSourceDoesNotKnowIsNullAndTheOtherRequestsAreCancelled() = runTest {
        var portfolioCancelled = false
        var reviewsCancelled = false
        source.onGetProfile = { null }
        source.onGetPortfolio = {
            try {
                awaitCancellation()
            } catch (cancellation: CancellationException) {
                portfolioCancelled = true
                throw cancellation
            }
        }
        source.onGetReviews = { _, _, _ ->
            try {
                awaitCancellation()
            } catch (cancellation: CancellationException) {
                reviewsCancelled = true
                throw cancellation
            }
        }

        assertNull(repository().getProfile(other))

        assertTrue(portfolioCancelled)
        assertTrue(reviewsCancelled)
    }

    @Test
    fun aMissingPortfolioOrMissingReviewsAreEmptyLists() = runTest {
        source.onGetPortfolio = { null }
        source.onGetReviews = { _, _, _ -> null }

        val page = repository().getProfile(other)!!

        assertEquals(emptyList(), page.works)
        assertEquals(0, page.worksCount)
        assertEquals(emptyList(), page.documents)
        assertEquals(emptyList(), page.latestReviews)
    }

    @Test
    fun theReviewsTheViewerWroteAreMarked() = runTest {
        source.onGetReviews = { _, _, _ -> reviews(1, 1, ViewerRaw) + reviews(1, 2, OtherRaw) }

        val page = repository().getProfile(other)!!

        assertEquals(listOf(true, false), page.latestReviews.map { it.isByViewer })
    }

    @Test
    fun aFailureOfOnePartFailsThePage() = runTest {
        source.onGetPortfolio = { throw RemoteFailure.Http(500) }

        assertEquals(500, assertFailsWith<RemoteFailure.Http> { repository().getProfile(other) }.status)
    }

    // ---- the person that was asked for (API-GAP G55) ----

    @Test
    fun theProfilePageOfAPersonIsThatOfTheOneAskedForWhateverIdTheAnswerHasOrLacks() = runTest {
        for (answered in listOf("", "   ", "someone-else-entirely")) {
            source.onGetProfile = { record(answered, name = "Елена") }

            val page = repository().getProfile(other)!!

            assertEquals(other, page.person.user.id, "the answer said '$answered'")
            assertEquals("Елена", page.person.user.name, "only the id is the repository's, the rest is the answer's")
        }
    }

    @Test
    fun aReviewWhoseAuthorHasNoIdIsNotTakenForTheViewersAndKeepsItsName() = runTest {
        source.onGetReviews = { _, _, _ ->
            listOf(
                reviewDto(id = "r1", authorId = "", authorName = "Аноним").toReview(TimeZone.UTC)!!,
                reviewDto(id = "r2", authorId = ViewerRaw).toReview(TimeZone.UTC)!!,
            )
        }

        val page = repository().getProfile(other)!!

        assertEquals(listOf(false, true), page.latestReviews.map { it.isByViewer })
        assertEquals("", page.latestReviews.first().author.id.value)
        assertEquals("Аноним", page.latestReviews.first().author.name)
    }

    // ---- the viewer's own page ----

    @Test
    fun theViewersPageIsTheOwnersView() = runTest {
        source.onGetMyPortfolio = { Portfolio(listOf(achievementDto(id = "w1").toWork()!!), emptyList()) }

        val page = repository().getProfile(viewer)!!

        assertTrue(page.isViewer)
        assertEquals(setOf("getMyProfile", "getMyPortfolio", "getReviews"), source.calls.toSet())
        assertEquals(listOf(Triple(viewer, 0, 2)), source.reviewRequests)
        assertEquals(listOf("w1"), page.works.map { it.id.value })
    }

    @Test
    fun theViewersPageIsTheirsWhateverIdTheAnswerHas() = runTest {
        source.onGetMyProfile = { record("someone-else-entirely") }

        val page = repository().getProfile(viewer)!!

        assertEquals(viewer, page.person.user.id)
    }

    @Test
    fun anAccountWithoutAProfileHasAnEmptyPageThatNeedsAName() = runTest {
        source.onGetMyProfile = { null }
        val repository = repository()

        val mine = repository.observeMyProfile().first()

        assertTrue(mine.needsName)
        assertEquals("", mine.profile.person.user.name)
        assertEquals(viewer, mine.profile.person.user.id)
        assertEquals(emptyList(), mine.profile.documents)
    }

    @Test
    fun theViewersWorksAreTheOwnersListAndTheirs() = runTest {
        source.onGetMyPortfolio = { Portfolio(listOf(achievementDto(id = "w1").toWork()!!), emptyList()) }

        val works = repository().getWorks(viewer)!!

        assertTrue(works.isViewer)
        assertEquals(listOf("w1"), works.items.map { it.id.value })
        assertEquals(listOf("getMyPortfolio"), source.calls)
    }

    @Test
    fun theWorksOfSomebodyElseAreTheirPublicList() = runTest {
        source.onGetPortfolio = { Portfolio(listOf(achievementDto(id = "w9").toWork()!!), emptyList()) }

        val works = repository().getWorks(other)!!

        assertFalse(works.isViewer)
        assertEquals(listOf("w9"), works.items.map { it.id.value })
        assertEquals(listOf("getPortfolio"), source.calls)
    }

    @Test
    fun theWorksOfAPersonTheSourceDoesNotKnowAreNull() = runTest {
        source.onGetPortfolio = { null }

        assertNull(repository().getWorks(other))
    }

    // ---- the reviews ----

    @Test
    fun theReviewsScreenGetsTheNumbersFromTheProfileAndTheFirstPage() = runTest {
        source.onGetProfile = { profileDto(userId = it.value, rating = 4.5, reviewCount = 7, positiveReviewCount = 6).toRecord() }
        source.onGetReviews = { _, _, limit -> reviews(limit) }

        val result = repository(pageSize = 5).getUserReviews(other)!!

        assertEquals(4.5, result.summary.average)
        assertEquals(7, result.summary.count)
        assertEquals(6, result.summary.positiveCount)
        assertEquals(5, result.firstPage.items.size)
        assertTrue(result.firstPage.hasMore, "a full page may be followed by another")
        assertEquals(listOf(Triple(other, 0, 5)), source.reviewRequests)
    }

    @Test
    fun aShortFirstPageIsTheLastOne() = runTest {
        source.onGetReviews = { _, _, _ -> reviews(3) }

        val result = repository(pageSize = 5).getUserReviews(other)!!

        assertFalse(result.firstPage.hasMore)
    }

    @Test
    fun noReviewsIsAnEmptyFirstPage() = runTest {
        source.onGetReviews = { _, _, _ -> null }

        val result = repository().getUserReviews(other)!!

        assertEquals(emptyList(), result.firstPage.items)
        assertFalse(result.firstPage.hasMore)
    }

    @Test
    fun reviewsOfAPersonTheSourceDoesNotKnowAreNullAndTheirPageIsCancelled() = runTest {
        var pageCancelled = false
        source.onGetProfile = { null }
        source.onGetReviews = { _, _, _ ->
            try {
                awaitCancellation()
            } catch (cancellation: CancellationException) {
                pageCancelled = true
                throw cancellation
            }
        }

        assertNull(repository().getUserReviews(other))
        assertTrue(pageCancelled)
    }

    @Test
    fun theViewersReviewsUseTheOwnProfileOrAnEmptyOne() = runTest {
        source.onGetMyProfile = { null }
        source.onGetReviews = { _, _, _ -> reviews(2) }

        val result = repository().getUserReviews(viewer)!!

        assertEquals(0, result.summary.count)
        assertNull(result.summary.average)
        assertEquals(2, result.firstPage.items.size)
        assertEquals(setOf("getMyProfile", "getReviews"), source.calls.toSet())
    }

    @Test
    fun aPageOfReviewsStartsWhereTheShownOnesEnd() = runTest {
        source.onGetReviews = { _, offset, limit -> reviews(limit, from = offset + 1) }

        val page = repository(pageSize = 4).getReviewsPage(other, offset = 8)!!

        assertEquals(listOf(Triple(other, 8, 4)), source.reviewRequests)
        assertEquals(listOf("r9", "r10", "r11", "r12"), page.items.map { it.id.value })
        assertTrue(page.hasMore)
    }

    @Test
    fun theLastPageOfReviewsIsShortAndAPagePastTheEndIsEmpty() = runTest {
        source.onGetReviews = { _, offset, limit -> reviews(maxOf(0, minOf(limit, 6 - offset)), from = offset + 1) }
        val repository = repository(pageSize = 4)

        val last = repository.getReviewsPage(other, offset = 4)!!
        val past = repository.getReviewsPage(other, offset = 8)!!

        assertEquals(2, last.items.size)
        assertFalse(last.hasMore)
        assertEquals(emptyList(), past.items)
        assertFalse(past.hasMore)
    }

    @Test
    fun theApiCannotBeAskedPastItsLargestOffset() = runTest {
        source.onGetReviews = { _, _, limit -> reviews(limit) }
        val repository = repository(pageSize = 100)

        // A full page at offset 9 900 is followed by the page at 10 000, which the API still takes; the one after it is too far.
        assertTrue(repository.getReviewsPage(other, offset = 9_900)!!.hasMore)
        assertFalse(repository.getReviewsPage(other, offset = 10_000)!!.hasMore)
    }

    @Test
    fun aPageOfReviewsOfAPersonTheSourceDoesNotKnowIsNull() = runTest {
        source.onGetReviews = { _, _, _ -> null }

        assertNull(repository().getReviewsPage(other, offset = 0))
    }

    @Test
    fun theReviewsOfAPageAreMarkedWhenTheViewerWroteThem() = runTest {
        source.onGetReviews = { _, _, _ -> reviews(1, 1, ViewerRaw) }

        assertTrue(repository().getReviewsPage(other, 0)!!.items.single().isByViewer)
    }

    // ---- one review ----

    @Test
    fun aReviewThatWasLoadedIsFoundWithoutAskingAgain() = runTest {
        source.onGetReviews = { _, _, _ -> reviews(3) }
        val repository = repository()
        repository.getReviewsPage(other, 0)
        source.reviewRequests.clear()

        val found = repository.getReview(other, ReviewId("r2"))

        assertEquals("r2", found?.id?.value)
        assertEquals(emptyList(), source.reviewRequests)
    }

    @Test
    fun aReviewOfTheProfilePreviewIsFoundToo() = runTest {
        source.onGetReviews = { _, _, _ -> reviews(2) }
        val repository = repository()
        repository.getProfile(other)
        source.reviewRequests.clear()

        assertEquals("r1", repository.getReview(other, ReviewId("r1"))?.id?.value)
        assertEquals(emptyList(), source.reviewRequests)
    }

    @Test
    fun aReviewThatWasNotLoadedIsLookedForPageByPageStartingFromTheNewest() = runTest {
        // The screen was restored: nothing is loaded. The review is the 150th.
        source.onGetReviews = { _, offset, limit -> reviews(limit, from = offset + 1) }

        val found = repository().getReview(other, ReviewId("r150"))

        assertEquals("r150", found?.id?.value)
        assertEquals(listOf(Triple(other, 0, 100), Triple(other, 100, 100)), source.reviewRequests)
    }

    @Test
    fun theSearchForAReviewStopsAtTheFiveHundredthAndSaysNotFound() = runTest {
        source.onGetReviews = { _, offset, limit -> reviews(limit, from = offset + 1) }

        val found = repository().getReview(other, ReviewId("r9999"))

        assertNull(found)
        assertEquals(listOf(0, 100, 200, 300, 400), source.reviewRequests.map { it.second })
    }

    @Test
    fun theLastReviewThatMaySitInTheSearchIsFound() = runTest {
        source.onGetReviews = { _, offset, limit -> reviews(limit, from = offset + 1) }

        assertEquals("r500", repository().getReview(other, ReviewId("r500"))?.id?.value)
        assertNull(repository().getReview(other, ReviewId("r501")))
    }

    @Test
    fun theSearchForAReviewStopsAtAShortPage() = runTest {
        source.onGetReviews = { _, offset, limit -> reviews(maxOf(0, minOf(limit, 130 - offset)), from = offset + 1) }

        assertNull(repository().getReview(other, ReviewId("r9999")))

        assertEquals(listOf(0, 100), source.reviewRequests.map { it.second })
    }

    @Test
    fun theSearchForAReviewOfAPersonTheSourceDoesNotKnowIsNull() = runTest {
        source.onGetReviews = { _, _, _ -> null }

        assertNull(repository().getReview(other, ReviewId("r1")))
    }

    @Test
    fun aReviewFoundBySearchingIsMarkedAndKept() = runTest {
        source.onGetReviews = { _, _, limit -> reviews(limit, author = ViewerRaw) }
        val repository = repository()

        assertTrue(repository.getReview(other, ReviewId("r3"))!!.isByViewer)
        source.reviewRequests.clear()
        assertEquals("r4", repository.getReview(other, ReviewId("r4"))?.id?.value)
        assertEquals(emptyList(), source.reviewRequests, "the whole page was kept")
    }

    // ---- the viewer's profile as state ----

    @Test
    fun theFirstCollectorLoadsTheProfileAndOthersShareIt() = runTest {
        val repository = repository()

        val first = repository.observeMyProfile().first()
        val second = repository.observeMyProfile().first()

        assertEquals(first, second)
        assertEquals(1, source.calls.count { it == "getMyProfile" }, "the second collector does not load again")
    }

    @Test
    fun aRefreshAsksAgainAndEmitsWhatChanged() = runTest {
        val repository = repository()
        val emitted = mutableListOf<MyProfile>()
        backgroundScope.launch { repository.observeMyProfile().collect { emitted += it } }
        runCurrent()
        assertEquals(1, emitted.size)

        source.onGetMyProfile = { record(ViewerRaw, name = "Новое имя") }
        repository.refreshMyProfile()
        runCurrent()

        assertEquals(listOf("Имя viewer", "Новое имя"), emitted.map { it.profile.person.user.name })
    }

    @Test
    fun aFailureOfTheFirstLoadGoesToTheCollectorAndALaterCollectorTriesAgain() = runTest {
        val repository = repository()
        source.onGetMyProfile = { throw RemoteFailure.NoConnection() }

        val failure = runCatching { repository.observeMyProfile().first() }.exceptionOrNull()
        assertIs<RemoteFailure.NoConnection>(failure)

        source.onGetMyProfile = { record(ViewerRaw) }
        assertEquals(viewer, repository.observeMyProfile().first().profile.person.user.id)
    }

    /**
     * A refresh that began before a save asked the server before it, so its older answer must not be laid over the saved
     * profile (the screen would show the old name again until the next refresh).
     */
    @Test
    fun aRefreshThatBeganBeforeASaveDoesNotOverwriteIt() = runTest {
        source.onGetMyProfile = { record(ViewerRaw, name = "Старое") }
        val repository = repository()
        repository.observeMyProfile().first()
        val gate = CompletableDeferred<Unit>()
        source.onGetMyProfile = {
            val old = record(ViewerRaw, name = "Старое")
            gate.await()
            old
        }
        val refresh = async { repository.refreshMyProfile() }
        runCurrent()
        source.onUpdateMyProfile = { record(ViewerRaw, name = "Новое") }
        repository.updateProfile(ProfileDraft(name = "Новое"))

        gate.complete(Unit)
        refresh.await()

        assertEquals("Новое", repository.observeMyProfile().first().profile.person.user.name)
    }

    @Test
    fun aFailedRefreshIsThrownAndKeepsThePreviousProfile() = runTest {
        val repository = repository()
        val before = repository.observeMyProfile().first()
        source.onGetMyProfile = { throw RemoteFailure.Timeout() }

        assertFailsWith<RemoteFailure.Timeout> { repository.refreshMyProfile() }

        assertEquals(before, repository.observeMyProfile().first())
    }

    // ---- the refreshes of the viewer's profile are shared ----

    /** How many times the viewer's own record was asked for: once for each set of requests of a load. */
    private fun FakeSource.loads(): Int = calls.count { it == "getMyProfile" }

    @Test
    fun twoRefreshesAtOnceAreOneSetOfThreeRequestsAndNotTwo() = runTest {
        val gate = CompletableDeferred<Unit>()
        source.onGetMyProfile = { gate.await(); record(ViewerRaw, name = "Загружено") }
        val repository = repository()

        val first = async { repository.refreshMyProfile() }
        val second = async { repository.refreshMyProfile() }
        runCurrent()
        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("getMyPortfolio", "getMyProfile", "getReviews"), source.calls.sorted(), "three requests, not six")
        assertEquals("Загружено", repository.observeMyProfile().first().profile.person.user.name)
    }

    @Test
    fun manyRefreshesAtOnceAreStillOneLoad() = runTest {
        val gate = CompletableDeferred<Unit>()
        source.onGetMyProfile = { gate.await(); record(ViewerRaw) }
        val repository = repository()

        val callers = List(5) { async { repository.refreshMyProfile() } }
        runCurrent()
        gate.complete(Unit)
        callers.awaitAll()

        assertEquals(1, source.loads())
        assertEquals(1, source.calls.count { it == "getMyPortfolio" })
        assertEquals(1, source.calls.count { it == "getReviews" })
    }

    @Test
    fun refreshesOneAfterAnotherEachAskTheServerAgain() = runTest {
        val repository = repository()

        repository.refreshMyProfile()
        repository.refreshMyProfile()

        assertEquals(2, source.loads(), "only the refreshes that overlap are shared")
    }

    @Test
    fun twoCollectorsThatComeTogetherShareTheFirstLoad() = runTest {
        val gate = CompletableDeferred<Unit>()
        source.onGetMyProfile = { gate.await(); record(ViewerRaw) }
        val repository = repository()

        val first = async { repository.observeMyProfile().first() }
        val second = async { repository.observeMyProfile().first() }
        runCurrent()
        gate.complete(Unit)

        assertEquals(first.await(), second.await())
        assertEquals(1, source.loads())
    }

    @Test
    fun theFailureOfASharedLoadIsTheFailureOfEveryCallerThatWaitedForItAndTheNextRefreshTriesAgain() = runTest {
        val gate = CompletableDeferred<Unit>()
        source.onGetMyProfile = { gate.await(); throw RemoteFailure.Timeout() }
        val repository = repository()
        val callers = List(3) { async { runCatching { repository.refreshMyProfile() }.exceptionOrNull() } }
        runCurrent()

        gate.complete(Unit)
        val failures = callers.awaitAll()

        assertTrue(failures.all { it is RemoteFailure.Timeout }, "every caller got the failure, was $failures")
        assertEquals(1, source.loads(), "one set of requests for the three callers")

        // The failed load does not stay in the way.
        source.onGetMyProfile = { record(ViewerRaw, name = "Снова") }
        repository.refreshMyProfile()
        assertEquals(2, source.loads())
        assertEquals("Снова", repository.observeMyProfile().first().profile.person.user.name)
    }

    @Test
    fun whenTheCallerThatRunsASharedLoadIsCancelledTheOneThatWaitsRunsALoadOfItsOwn() = runTest {
        val gate = CompletableDeferred<Unit>()
        var asked = 0
        source.onGetMyProfile = {
            if (++asked == 1) {
                gate.await()
                record(ViewerRaw, name = "Первая")
            } else {
                record(ViewerRaw, name = "Вторая")
            }
        }
        val repository = repository()
        val leader = async { repository.refreshMyProfile() }
        runCurrent()
        val waiter = async { repository.refreshMyProfile() }
        runCurrent()

        leader.cancel()
        waiter.await() // it neither inherits the cancellation nor waits for ever

        assertTrue(leader.isCancelled)
        assertEquals(2, source.loads())
        assertEquals("Вторая", repository.observeMyProfile().first().profile.person.user.name)
    }

    @Test
    fun aWaiterThatGivesUpCancelsNeitherTheLoadNorTheOtherCallers() = runTest {
        val gate = CompletableDeferred<Unit>()
        source.onGetMyProfile = { gate.await(); record(ViewerRaw) }
        val repository = repository()
        val leader = async { repository.refreshMyProfile() }
        runCurrent()
        val impatient = async { repository.refreshMyProfile() }
        val patient = async { repository.refreshMyProfile() }
        runCurrent()

        impatient.cancel()
        gate.complete(Unit)
        leader.await()
        patient.await()

        assertTrue(impatient.isCancelled)
        assertEquals(1, source.loads())
        assertNotNull(repository.observeMyProfile().first())
    }

    // ---- a load never overwrites a change that is newer than it ----

    /**
     * Starts a refresh that the server answers late, with the old name; runs [write] while it waits; lets it finish.
     * Whatever asks the server after the write gets the new name. Returns the name the state shows in the end.
     */
    private suspend fun kotlinx.coroutines.test.TestScope.nameAfterAStaleRefreshMeets(
        write: suspend (ProfileRepositoryImpl, FakeSource) -> Unit,
    ): String {
        val source = FakeSource()
        val repository = ProfileRepositoryImpl(source, viewer)
        source.onGetMyProfile = { record(ViewerRaw, name = "Старое") }
        repository.observeMyProfile().first()
        val gate = CompletableDeferred<Unit>()
        var asked = 0
        source.onGetMyProfile = {
            if (++asked == 1) {
                val old = record(ViewerRaw, name = "Старое")
                gate.await()
                old
            } else {
                record(ViewerRaw, name = "Новое")
            }
        }
        val stale = async { repository.refreshMyProfile() }
        runCurrent()

        write(repository, source)
        gate.complete(Unit)
        stale.await()

        return repository.observeMyProfile().first().profile.person.user.name
    }

    @Test
    fun aRefreshThatBeganBeforeAnAddedWorkOrDocumentDoesNotBringBackTheOldProfile() = runTest {
        assertEquals("Новое", nameAfterAStaleRefreshMeets { repository, _ ->
            repository.createWork(WorkDraft(title = "Статья", description = "О графах"))
        })
        assertEquals("Новое", nameAfterAStaleRefreshMeets { repository, _ ->
            repository.createDocument(DocumentDraft(title = "Диплом", issuer = "ВШЭ", proof = ProofAttachment("https://x.org/d.pdf")))
        })
    }

    @Test
    fun aRefreshThatBeganBeforeAnAddedAndSentWorkDoesNotBringBackTheOldProfile() = runTest {
        // Two changes in one call: the add and the send. Both count.
        assertEquals("Новое", nameAfterAStaleRefreshMeets { repository, source ->
            repository.createWork(WorkDraft(title = "Статья", description = "О графах", link = "https://doi.org/10.1/x"))
            assertEquals(1, source.submitted.size)
        })
    }

    @Test
    fun aRefreshThatBeganBeforeAnEditedWorkOrDocumentDoesNotBringBackTheOldProfile() = runTest {
        assertEquals("Новое", nameAfterAStaleRefreshMeets { repository, _ ->
            repository.updateWork(WorkId("w1"), WorkDraft(title = "Новое", description = "О"), expectedRevision = 1)
        })
        assertEquals("Новое", nameAfterAStaleRefreshMeets { repository, _ ->
            repository.updateDocument(DocumentId("d1"), DocumentDraft(title = "Новое", issuer = "ВШЭ", hasEvidenceFile = true), expectedRevision = 1)
        })
    }

    @Test
    fun aRefreshThatBeganBeforeASendOrADeleteDoesNotBringBackTheOldProfile() = runTest {
        assertEquals("Новое", nameAfterAStaleRefreshMeets { repository, _ ->
            repository.submitForVerification(VerificationSubject.Work(WorkId("w1")))
        })
        assertEquals("Новое", nameAfterAStaleRefreshMeets { repository, _ ->
            repository.delete(VerificationSubject.Document(DocumentId("d1"), DocumentKind.Education))
        })
    }

    @Test
    fun aRefreshThatBeganBeforeASaveOfTheProfileDoesNotBringBackTheOldName() = runTest {
        assertEquals("Новое", nameAfterAStaleRefreshMeets { repository, _ ->
            repository.updateProfile(ProfileDraft(name = "Новое"))
        })
    }

    @Test
    fun aChangeThatFailedDoesNotOutdateARefreshThatIsOnItsWay() = runTest {
        val repository = repository()
        source.onGetMyProfile = { record(ViewerRaw, name = "Старое") }
        repository.observeMyProfile().first()
        val gate = CompletableDeferred<Unit>()
        source.onGetMyProfile = { gate.await(); record(ViewerRaw, name = "Свежее") }
        val refresh = async { repository.refreshMyProfile() }
        runCurrent()
        source.onDelete = { throw RemoteFailure.Http(500) }

        assertFailsWith<RemoteFailure.Http> { repository.delete(VerificationSubject.Work(WorkId("w1"))) }
        gate.complete(Unit)
        refresh.await()

        assertEquals("Свежее", repository.observeMyProfile().first().profile.person.user.name)
    }

    @Test
    fun aRefreshAfterAChangeIsANewLoadAndDoesNotJoinTheOldOneThatIsStillOnItsWay() = runTest {
        val repository = repository()
        source.onGetMyProfile = { record(ViewerRaw, name = "Старое") }
        repository.observeMyProfile().first()
        source.calls.clear()
        val gate = CompletableDeferred<Unit>()
        var asked = 0
        source.onGetMyProfile = {
            if (++asked == 1) {
                val old = record(ViewerRaw, name = "Старое")
                gate.await()
                old
            } else {
                record(ViewerRaw, name = "Свежее")
            }
        }
        val stale = async { repository.refreshMyProfile() }
        runCurrent()
        assertEquals(1, source.loads())

        repository.updateProfile(ProfileDraft(name = "Сохранённое"))
        repository.refreshMyProfile() // it does not wait for the old one: that load is older than the save

        assertEquals(2, source.loads())
        assertEquals("Свежее", repository.observeMyProfile().first().profile.person.user.name)
        gate.complete(Unit)
        stale.await()
        assertEquals("Свежее", repository.observeMyProfile().first().profile.person.user.name, "the old answer is dropped")
    }

    @Test
    fun theFirstLoadIsAppliedEvenIfAChangeWentThroughMeanwhileBecauseThereIsNoStateYet() = runTest {
        val gate = CompletableDeferred<Unit>()
        var asked = 0
        source.onGetMyProfile = {
            if (++asked == 1) {
                val old = record(ViewerRaw, name = "Старое")
                gate.await()
                old
            } else {
                throw RemoteFailure.NoConnection() // the reload after the change fails: nothing is shown yet
            }
        }
        val repository = repository()
        val first = async { repository.refreshMyProfile() }
        runCurrent()

        repository.delete(VerificationSubject.Work(WorkId("w1")))
        gate.complete(Unit)
        first.await()

        assertEquals("Старое", repository.observeMyProfile().first().profile.person.user.name, "better than nothing")
        assertEquals(2, source.loads(), "showing it asks for nothing more")
    }

    @Test
    fun aSaveBeforeAnythingWasLoadedLoadsTheProfileAfterTheSaveAndNotTheLoadThatWasOnItsWay() = runTest {
        val gate = CompletableDeferred<Unit>()
        var asked = 0
        source.onGetMyProfile = {
            if (++asked == 1) {
                val old = record(ViewerRaw, name = "Старое")
                gate.await()
                old
            } else {
                record(ViewerRaw, name = "Новое")
            }
        }
        val repository = repository()
        val firstLoad = async { repository.refreshMyProfile() }
        runCurrent()

        val saved = repository.updateProfile(ProfileDraft(name = "Новое"))
        gate.complete(Unit)
        firstLoad.await()

        assertEquals("Новое", saved.profile.person.user.name)
        assertEquals("Новое", repository.observeMyProfile().first().profile.person.user.name)
    }

    @Test
    fun aSaveOfTheProfileKeepsTheWorksThatTheStateGotAfterAnAdd() = runTest {
        val repository = repository()
        repository.observeMyProfile().first()
        source.onGetMyPortfolio = { Portfolio(listOf(achievementDto(id = "w-new").toWork()!!), emptyList()) }
        repository.createWork(WorkDraft(title = "Статья", description = "О графах"))
        source.onUpdateMyProfile = { record(ViewerRaw, name = "Новое имя") }

        repository.updateProfile(ProfileDraft(name = "Новое имя"))

        val mine = repository.observeMyProfile().first()
        assertEquals("Новое имя", mine.profile.person.user.name)
        assertEquals(listOf("w-new"), mine.allWorks.map { it.id.value }, "the save lays only the form over the state")
    }

    // ---- saving the profile ----

    @Test
    fun savingBeforeAnythingWasLoadedLoadsTheWholeProfile() = runTest {
        val repository = repository()
        source.onUpdateMyProfile = { record(ViewerRaw, name = "Сохранённое") }
        source.onGetMyProfile = { record(ViewerRaw, name = "Сохранённое") }

        val saved = repository.updateProfile(ProfileDraft(name = "Сохранённое"))

        assertEquals("Сохранённое", saved.profile.person.user.name)
        assertTrue("getMyPortfolio" in source.calls)
    }

    @Test
    fun savingChangesTheFormPartsAndKeepsThePortfolioAndTheReviews() = runTest {
        val works = listOf(achievementDto(id = "w1").toWork()!!)
        source.onGetMyPortfolio = { Portfolio(works, emptyList()) }
        source.onGetReviews = { _, _, _ -> reviews(2) }
        val repository = repository()
        repository.observeMyProfile().first()
        source.calls.clear()
        source.onUpdateMyProfile = { profileDto(userId = "whatever", name = "Новое имя", about = "Новый текст", skills = listOf("Rust"), reviewCount = 3, rating = 4.0).toRecord() }

        val saved = repository.updateProfile(ProfileDraft(name = "Новое имя"))

        assertEquals(listOf("updateMyProfile"), source.calls, "the portfolio and the reviews are not asked for again")
        assertEquals("Новое имя", saved.profile.person.user.name)
        assertEquals(viewer, saved.profile.person.user.id)
        assertEquals("Новый текст", saved.profile.about)
        assertEquals(listOf("Rust"), saved.profile.skills)
        assertEquals(3, saved.profile.reviews.count)
        assertEquals(listOf("w1"), saved.allWorks.map { it.id.value })
        assertEquals(listOf("r1", "r2"), saved.profile.latestReviews.map { it.id.value })
        assertEquals(saved, repository.observeMyProfile().first())
    }

    @Test
    fun aFailedSaveIsThrownAndChangesNothing() = runTest {
        val repository = repository()
        val before = repository.observeMyProfile().first()
        source.onUpdateMyProfile = { throw RemoteFailure.Http(409) }

        assertFailsWith<RemoteFailure.Http> { repository.updateProfile(ProfileDraft(name = "Имя")) }

        assertEquals(before, repository.observeMyProfile().first())
    }

    // ---- adding ----

    @Test
    fun aWorkWithALinkIsAddedAndSent() = runTest {
        val result = repository().createWork(WorkDraft(title = "Статья", description = "О графах", link = "https://doi.org/10.1/x"))

        val saved = assertIs<PortfolioChange.Saved<PortfolioWork>>(result)
        assertTrue(saved.sent)
        assertEquals(WorkId("new-w"), saved.value.id)
        assertEquals(listOf<VerificationSubject>(VerificationSubject.Work(WorkId("new-w"))), source.submitted)
    }

    @Test
    fun aWorkWithoutALinkOrAFileIsAddedAndNotSent() = runTest {
        val result = repository().createWork(WorkDraft(title = "Статья", description = "О графах"))

        assertFalse(assertIs<PortfolioChange.Saved<PortfolioWork>>(result).sent)
        assertEquals(emptyList(), source.submitted)
    }

    @Test
    fun aWorkWithAProofFileIsSentWithoutALink() = runTest {
        val result = repository().createWork(WorkDraft(title = "Статья", description = "О графах", hasEvidenceFile = true))

        assertTrue(assertIs<PortfolioChange.Saved<PortfolioWork>>(result).sent)
    }

    @Test
    fun aSendThatFailsLeavesTheWorkAddedButNotSent() = runTest {
        val draft = WorkDraft(title = "Статья", description = "О графах", link = "https://doi.org/10.1/x")

        source.onSubmit = { throw RemoteFailure.NoConnection() }
        val failed = repository().createWork(draft)
        assertFalse(assertIs<PortfolioChange.Saved<PortfolioWork>>(failed).sent)

        source.onSubmit = { EditOutcome.Conflict }
        val conflict = repository().createWork(draft)
        assertFalse(assertIs<PortfolioChange.Saved<PortfolioWork>>(conflict).sent)
    }

    @Test
    fun aCancelledSendIsNotSwallowed() = runTest {
        source.onSubmit = { throw CancellationException("screen left") }

        assertFailsWith<CancellationException> {
            repository().createWork(WorkDraft(title = "Статья", description = "О графах", link = "https://doi.org/10.1/x"))
        }
    }

    @Test
    fun aFailureToAddIsThrownAndNothingIsSentOrReloaded() = runTest {
        source.onCreateWork = { throw RemoteFailure.Http(400) }

        assertFailsWith<RemoteFailure.Http> {
            repository().createWork(WorkDraft(title = "Статья", description = "О графах", link = "https://doi.org/10.1/x"))
        }

        assertEquals(listOf("createWork"), source.calls)
    }

    @Test
    fun afterAnAddTheViewersProfileIsReloaded() = runTest {
        val repository = repository()

        repository.createWork(WorkDraft(title = "Статья", description = "О графах"))

        assertTrue("getMyPortfolio" in source.calls)
    }

    @Test
    fun aFailedReloadAfterAnAddDoesNotFailTheAdd() = runTest {
        source.onGetMyPortfolio = { throw RemoteFailure.NoConnection() }

        val result = repository().createWork(WorkDraft(title = "Статья", description = "О графах"))

        assertIs<PortfolioChange.Saved<PortfolioWork>>(result)
    }

    @Test
    fun aDocumentIsAddedAndSentWithItsKind() = runTest {
        val result = repository().createDocument(
            DocumentDraft(kind = DocumentKind.Education, title = "Диплом", issuer = "ВШЭ", proof = ProofAttachment("https://x.org/d.pdf")),
        )

        val saved = assertIs<PortfolioChange.Saved<ProfileDocument>>(result)
        assertTrue(saved.sent)
        assertEquals(listOf<VerificationSubject>(VerificationSubject.Document(DocumentId("new-d"), DocumentKind.Education)), source.submitted)
    }

    @Test
    fun aDocumentWhoseSendFailsIsStillAdded() = runTest {
        source.onSubmit = { throw RemoteFailure.Timeout() }

        val result = repository().createDocument(
            DocumentDraft(title = "Диплом", issuer = "ВШЭ", proof = ProofAttachment("https://x.org/d.pdf")),
        )

        assertFalse(assertIs<PortfolioChange.Saved<ProfileDocument>>(result).sent)
    }

    // ---- editing ----

    @Test
    fun aChangeIsPassedOnAndTheProfileIsReloaded() = runTest {
        val repository = repository()
        val work = WorkId("w1")

        val outcome = repository.updateWork(work, WorkDraft(title = "Новое", description = "О"), expectedRevision = 3)

        assertEquals("Новое", assertIs<EditOutcome.Done<PortfolioWork>>(outcome).value.title)
        assertEquals(listOf("updateWork", "getMyProfile", "getMyPortfolio", "getReviews").toSet(), source.calls.toSet())
    }

    @Test
    fun aConflictOrAGoneRecordIsPassedOnToo() = runTest {
        source.onUpdateWork = { _, _, _ -> EditOutcome.Conflict }
        source.onUpdateDocument = { _, _, _ -> EditOutcome.NotFound }
        source.onSubmit = { EditOutcome.Conflict }
        source.onDelete = { EditOutcome.NotFound }
        val repository = repository()

        assertEquals(EditOutcome.Conflict, repository.updateWork(WorkId("w"), WorkDraft(title = "T", description = "D"), 1))
        assertEquals(EditOutcome.NotFound, repository.updateDocument(DocumentId("d"), DocumentDraft(title = "T", issuer = "I"), 1))
        assertEquals(EditOutcome.Conflict, repository.submitForVerification(VerificationSubject.Work(WorkId("w"))))
        assertEquals(EditOutcome.NotFound, repository.delete(VerificationSubject.Work(WorkId("w"))))
    }

    @Test
    fun aFailedReloadAfterAChangeDoesNotFailTheChange() = runTest {
        source.onGetMyPortfolio = { throw RemoteFailure.NoConnection() }
        val repository = repository()

        assertIs<EditOutcome.Done<*>>(repository.submitForVerification(VerificationSubject.Work(WorkId("w"))))
        assertEquals(EditOutcome.Done(Unit), repository.delete(VerificationSubject.Work(WorkId("w"))))
    }

    @Test
    fun aFailedChangeIsThrown() = runTest {
        source.onDelete = { throw RemoteFailure.Http(500) }

        assertFailsWith<RemoteFailure.Http> { repository().delete(VerificationSubject.Work(WorkId("w"))) }
    }

    @Test
    fun theRecordsOfTheViewerAreReadStraightFromTheSource() = runTest {
        val work = achievementDto(id = "w1").toWork()
        source.onGetMyWork = { work }

        assertEquals(work, repository().getMyWork(WorkId("w1")))
        assertNull(repository().getMyDocument(DocumentId("none")))
    }

    // ---- what there is no data for ----

    @Test
    fun thereAreNoResultsOfTheVerificationAndMarkingThemSeenDoesNothing() = runTest {
        val repository = repository()

        assertEquals(emptyList(), repository.observeVerificationResults().first())
        repository.markResultsSeen(setOf(VerificationResultId("x")))
        assertEquals(emptyList(), source.calls)
    }

    @Test
    fun theCapabilitiesAreTheSources() {
        source.capabilities = ProfileCapabilities(verificationStatuses = true, interestTypes = true)

        assertEquals(ProfileCapabilities(verificationStatuses = true, interestTypes = true), repository().capabilities)
    }

    @Test
    fun aPageOfReviewsHasOneToOneHundred() {
        assertFailsWith<IllegalArgumentException> { repository(pageSize = 0) }
        assertFailsWith<IllegalArgumentException> { repository(pageSize = 101) }
        repository(pageSize = 1)
        repository(pageSize = 100)
    }
}

/** A profile record of the person [id]. */
private fun record(id: String, name: String = "Имя $id"): ProfileRecord = profileDto(userId = id, name = name).toRecord()
