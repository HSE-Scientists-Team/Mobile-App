package org.matchlab.academic_net.feature.reviews.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.reviews.domain.ReviewableProject
import org.matchlab.academic_net.gateway.dto.ProjectListDto
import org.matchlab.academic_net.gateway.dto.UserReviewListDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The projects a person can be reviewed in (16): the viewer's completed projects that the person shares, minus those the viewer
 * has already reviewed them in. Put together from three reads of the Gateway, on fakes that page like the server.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GatewayReviewableProjectsTest {
    private val catalog = FakeProjectCatalog()
    private val reviews = ScriptedReviewsApi()
    private val viewer = UserId(ViewerId)
    private val target = UserId(TargetId)
    private val source = GatewayReviewsDataSource(catalog, reviews, viewer)

    /**
     * The failure came through the coroutines of the reads, which may hand over a recovered copy of the exception (a copy of the
     * same class whose cause is the original); both are "the failure as it is".
     */
    private fun assertIsFailure(expected: Throwable, actual: Throwable) {
        assertTrue(actual === expected || actual.cause === expected, "expected $expected (or a recovered copy of it), got $actual")
    }

    private suspend fun ids(): List<String> = source.getReviewableProjects(target).map { it.id.value }

    // ---- which projects are shared ----

    @Test
    fun aProjectTheViewerOwnsAndTheTargetIsAnActiveMemberOfIsOffered() = runTest {
        catalog.owned += projectDto("p-1", title = "Цифровой двойник", completedAt = June2025)

        val projects = source.getReviewableProjects(target)

        assertEquals(listOf(ReviewableProject(ProjectId("p-1"), "Цифровой двойник", Instant.fromEpochSeconds(June2025))), projects)
    }

    @Test
    fun aProjectTheTargetOwnsAndTheViewerTakesPartInIsOfferedEvenWithoutMembers() = runTest {
        catalog.joined += projectDto("p-1", owner = TargetId, members = emptyList())

        assertEquals(listOf("p-1"), ids())
    }

    @Test
    fun aProjectOfAThirdPersonWithBothOnTheTeamIsOffered() = runTest {
        catalog.joined += projectDto("p-1", owner = "someone", members = listOf(member(ViewerId), member(TargetId)))

        assertEquals(listOf("p-1"), ids())
    }

    @Test
    fun aProjectOfAThirdPersonWithoutTheTargetOnTheTeamIsNotOffered() = runTest {
        catalog.joined += projectDto("p-1", owner = "someone", members = listOf(member(ViewerId), member("another")))

        assertEquals(emptyList(), ids())
    }

    @Test
    fun aProjectTheViewerOwnsWithoutTheTargetIsNotOffered() = runTest {
        catalog.owned += projectDto("p-1", members = listOf(member("another")))

        assertEquals(emptyList(), ids())
    }

    @Test
    fun aMemberWhoLeftIsNotOnTheTeam() = runTest {
        catalog.owned += projectDto("p-1", members = listOf(member(TargetId, "left")))

        assertEquals(emptyList(), ids())
    }

    @Test
    fun aMemberWhoWasRemovedIsNotOnTheTeam() = runTest {
        catalog.owned += projectDto("p-1", members = listOf(member(TargetId, "removed")))

        assertEquals(emptyList(), ids())
    }

    @Test
    fun aMemberWhoLeftDoesNotHideAnActiveEntryOfTheSameTarget() = runTest {
        catalog.owned += projectDto("p-1", members = listOf(member(TargetId, "left"), member(TargetId, "active")))

        assertEquals(listOf("p-1"), ids())
    }

    @Test
    fun aMemberWhoseStatusTheServerLeftOutCountsAsOnTheTeam() = runTest {
        // The list gives only active members (backend sanitize, PR #16); an entry without a status is not a "left" or "removed".
        catalog.owned += projectDto("p-1", members = listOf(member(TargetId, "")))

        assertEquals(listOf("p-1"), ids())
    }

    @Test
    fun theTargetBeingOwnerStillCountsWhenTheyAreAlsoListedAsLeft() = runTest {
        catalog.joined += projectDto("p-1", owner = TargetId, members = listOf(member(TargetId, "left")))

        assertEquals(listOf("p-1"), ids())
    }

    @Test
    fun anEmptyTeamOfAProjectOfSomeoneElseMeansNotOffered() = runTest {
        // Nothing says whether the target is on it (API-GAP: GET /projects/{id} is not asked), so the project is left out.
        catalog.joined += projectDto("p-1", owner = "someone", members = emptyList())
        catalog.owned += projectDto("p-2", members = emptyList())

        assertEquals(emptyList(), ids())
    }

    @Test
    fun anIdOfTheTargetIsComparedWholeAndExactly() = runTest {
        catalog.owned += projectDto("p-1", members = listOf(member("$TargetId-2"), member(TargetId.uppercase()), member(" $TargetId")))

        assertEquals(emptyList(), ids())
    }

    // ---- the reviews already written ----

    @Test
    fun aProjectTheViewerAlreadyReviewedTheTargetInIsNotOffered() = runTest {
        catalog.owned += projectDto("p-1")
        catalog.owned += projectDto("p-2")
        reviews.about[TargetId] = mutableListOf(reviewDto("p-1", author = ViewerId))

        assertEquals(listOf("p-2"), ids())
    }

    @Test
    fun aReviewOfAnotherAuthorDoesNotHideTheProject() = runTest {
        catalog.owned += projectDto("p-1")
        reviews.about[TargetId] = mutableListOf(reviewDto("p-1", author = "someone-else"))

        assertEquals(listOf("p-1"), ids())
    }

    @Test
    fun aReviewOfTheViewerInAnotherProjectHidesOnlyThatProject() = runTest {
        catalog.owned += projectDto("p-1")
        catalog.joined += projectDto("p-2", owner = TargetId)
        reviews.about[TargetId] = mutableListOf(reviewDto("p-2"), reviewDto("p-unrelated"))

        assertEquals(listOf("p-1"), ids())
    }

    @Test
    fun aReviewWithoutAProjectIdHidesNothing() = runTest {
        catalog.owned += projectDto("p-1")
        reviews.about[TargetId] = mutableListOf(reviewDto("", author = ViewerId))

        assertEquals(listOf("p-1"), ids())
    }

    @Test
    fun aReviewWithoutAnAuthorHidesNothing() = runTest {
        catalog.owned += projectDto("p-1")
        reviews.about[TargetId] = mutableListOf(reviewDto("p-1", author = ""))

        assertEquals(listOf("p-1"), ids())
    }

    @Test
    fun theReviewsAboutTheViewerOrSomeoneElseAreNotRead() = runTest {
        catalog.owned += projectDto("p-1")
        reviews.about[ViewerId] = mutableListOf(reviewDto("p-1"))

        assertEquals(listOf("p-1"), ids())

        assertEquals(listOf(TargetId), reviews.reads.map { it.first })
    }

    // ---- duplicates, ids, order ----

    @Test
    fun aProjectInBothListsIsOfferedOnce() = runTest {
        val shared = projectDto("p-1", owner = TargetId, members = listOf(member(ViewerId)))
        catalog.owned += shared
        catalog.joined += shared

        assertEquals(listOf("p-1"), ids())
    }

    @Test
    fun aProjectRepeatedInOneListIsOfferedOnceAndTheFirstEntryWins() = runTest {
        catalog.owned += projectDto("p-1", title = "First", completedAt = 100)
        catalog.owned += projectDto("p-1", title = "Second", completedAt = 900)

        val project = source.getReviewableProjects(target).single()

        assertEquals("First", project.title)
        assertEquals(Instant.fromEpochSeconds(100), project.completedAt)
    }

    @Test
    fun aProjectWithoutAnIdIsDropped() = runTest {
        catalog.owned += projectDto("")
        catalog.owned += projectDto("  ")
        catalog.owned += projectDto("p-1")

        assertEquals(listOf("p-1"), ids())
    }

    @Test
    fun theMostRecentlyCompletedComeFirstAndThoseWithoutADateLast() = runTest {
        catalog.owned += projectDto("old", completedAt = 100)
        catalog.owned += projectDto("undated", completedAt = 0)
        catalog.owned += projectDto("new", completedAt = 300)
        catalog.joined += projectDto("middle", owner = TargetId, completedAt = 200)

        assertEquals(listOf("new", "middle", "old", "undated"), ids())
    }

    @Test
    fun projectsOfTheSameDateStayInTheOrderOfTheServerOwnedBeforeJoined() = runTest {
        catalog.owned += projectDto("a", completedAt = 100)
        catalog.owned += projectDto("b", completedAt = 0)
        catalog.owned += projectDto("c", completedAt = 100)
        catalog.joined += projectDto("d", owner = TargetId, completedAt = 200)
        catalog.joined += projectDto("e", owner = TargetId, completedAt = 100)
        catalog.joined += projectDto("f", owner = TargetId, completedAt = 0)

        assertEquals(listOf("d", "a", "c", "e", "b", "f"), ids())
    }

    @Test
    fun projectsOfTheSameDateStayInTheOrderOfTheServerInsideOneList() = runTest {
        for (id in listOf("z", "y", "x", "w")) catalog.owned += projectDto(id, completedAt = 500)

        assertEquals(listOf("z", "y", "x", "w"), ids())
    }

    @Test
    fun aZeroOrNegativeCompletionTimeMeansNoDate() = runTest {
        catalog.owned += projectDto("zero", completedAt = 0)
        catalog.owned += projectDto("negative", completedAt = -5)
        catalog.owned += projectDto("one", completedAt = 1)

        val projects = source.getReviewableProjects(target).associateBy { it.id.value }

        assertNull(projects.getValue("zero").completedAt)
        assertNull(projects.getValue("negative").completedAt)
        assertEquals(Instant.fromEpochSeconds(1), projects.getValue("one").completedAt)
    }

    @Test
    fun theCompletionTimeIsInSecondsSinceTheEpoch() = runTest {
        catalog.owned += projectDto("p-1", completedAt = 1_750_000_000)

        assertEquals(Instant.parse("2025-06-15T15:06:40Z"), source.getReviewableProjects(target).single().completedAt)
    }

    @Test
    fun theTitleIsTheOneOfTheDetails() = runTest {
        catalog.owned += projectDto("p-1", title = "AI-навигация")

        assertEquals("AI-навигация", source.getReviewableProjects(target).single().title)
    }

    // ---- empty results, the viewer themselves ----

    @Test
    fun noProjectsAtAllIsAnEmptyList() = runTest {
        assertEquals(emptyList(), source.getReviewableProjects(target))
        assertEquals(1, catalog.callsOf("owner").size)
        assertEquals(1, catalog.callsOf("participant").size)
        assertEquals(1, reviews.reads.size)
    }

    @Test
    fun everyProjectReviewedAlreadyIsAnEmptyList() = runTest {
        catalog.owned += projectDto("p-1")
        catalog.joined += projectDto("p-2", owner = TargetId)
        reviews.about[TargetId] = mutableListOf(reviewDto("p-1"), reviewDto("p-2"))

        assertEquals(emptyList(), ids())
    }

    @Test
    fun theViewerThemselvesGetsAnEmptyListWithoutAnyRequest() = runTest {
        catalog.owned += projectDto("p-1", members = listOf(member(ViewerId)))

        assertEquals(emptyList(), source.getReviewableProjects(viewer))

        assertEquals(emptyList(), catalog.listCalls)
        assertEquals(emptyList(), reviews.reads)
    }

    // ---- the requests ----

    @Test
    fun theViewersCompletedProjectsAreAskedByOwnerAndByParticipantInPagesOf100() = runTest {
        ids()

        assertEquals(2, catalog.listCalls.size)
        for ((params, page) in catalog.listCalls) {
            assertEquals("completed", params.status)
            assertNull(params.q)
            assertNull(params.type)
            assertNull(params.field)
            assertNull(params.duration)
            assertEquals(emptyList(), params.tags)
            assertEquals(PageRequest(offset = 0, limit = 100), page)
        }
        assertEquals(setOf("owner", "participant"), catalog.listCalls.map { it.first.mine }.toSet())
    }

    @Test
    fun theReviewsOfTheTargetAreAskedByTheirIdFromTheFirstPageInPagesOf100() = runTest {
        ids()

        assertEquals(listOf(Triple(TargetId, 100, 0)), reviews.reads)
    }

    @Test
    fun theThreeReadsStartTogetherNotOneAfterAnother() = runTest {
        val gate = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        catalog.onList = { params, _ ->
            started += "projects:${params.mine}"
            gate.await()
            ProjectListDto()
        }
        reviews.onUserReviews = { id, _, _ ->
            started += "reviews:$id"
            gate.await()
            UserReviewListDto()
        }

        val result = backgroundScope.async { source.getReviewableProjects(target) }
        runCurrent()

        assertEquals(setOf("projects:owner", "projects:participant", "reviews:$TargetId"), started.toSet())
        assertFalse(result.isCompleted)
        gate.complete(Unit)
        assertEquals(emptyList(), result.await())
    }

    // ---- paging ----

    @Test
    fun aListOfMoreThan100ProjectsIsReadPageByPage() = runTest {
        repeat(250) { catalog.owned += projectDto("o-$it", completedAt = 1_000L + it) }
        repeat(130) { catalog.joined += projectDto("j-$it", owner = TargetId, completedAt = 5_000L + it) }

        val found = ids()

        assertEquals(380, found.size)
        assertEquals(listOf(0, 100, 200), catalog.callsOf("owner").map { it.offset })
        assertEquals(listOf(0, 100), catalog.callsOf("participant").map { it.offset })
        assertTrue(catalog.listCalls.all { it.second.limit == 100 })
        assertEquals("j-129", found.first(), "the most recently completed is first, wherever the page was")
        assertEquals("o-0", found.last())
    }

    @Test
    fun aListOfExactly100ProjectsIsOnePageWhenTheTotalSaysSo() = runTest {
        repeat(100) { catalog.owned += projectDto("o-$it") }

        assertEquals(100, ids().size)

        assertEquals(listOf(0), catalog.callsOf("owner").map { it.offset })
    }

    @Test
    fun aListOf101ProjectsTakesASecondPage() = runTest {
        repeat(101) { catalog.owned += projectDto("o-$it") }

        assertEquals(101, ids().size)

        assertEquals(listOf(0, 100), catalog.callsOf("owner").map { it.offset })
    }

    @Test
    fun aTotalTheServerOmitsEndsTheListAtTheFirstPage() = runTest {
        // pageOf trusts a missing total (0) as "what was received": the server always reports it, so this is the safe side.
        repeat(150) { catalog.owned += projectDto("o-$it") }
        catalog.reportedTotal = { 0 }

        assertEquals(100, ids().size)
        assertEquals(listOf(0), catalog.callsOf("owner").map { it.offset })
    }

    @Test
    fun aTotalThatIsTooSmallIsNotBelievedAbovePageSizeWhileEveryPageIsFull() = runTest {
        repeat(250) { catalog.owned += projectDto("o-$it") }
        catalog.reportedTotal = { 10 }

        // The total is raised to what was received: page one is full (100 of "10"), so the list ends there, as for the other features.
        assertEquals(100, ids().size)
    }

    @Test
    fun anEmptyPageEndsTheListEvenIfTheTotalPromisesMore() = runTest {
        repeat(100) { catalog.owned += projectDto("o-$it") }
        catalog.reportedTotal = { 500 }

        assertEquals(100, ids().size)

        assertEquals(listOf(0, 100), catalog.callsOf("owner").map { it.offset })
    }

    @Test
    fun atMostTenPagesOfProjectsAreReadPerList() = runTest {
        repeat(1_200) { catalog.owned += projectDto("o-$it", completedAt = it + 1L) }

        val found = ids()

        assertEquals((0..900 step 100).toList(), catalog.callsOf("owner").map { it.offset })
        assertEquals(1_000, found.size)
        assertEquals("o-999", found.first())
    }

    @Test
    fun theTenPageLimitIsPerListNotInTotal() = runTest {
        repeat(1_200) { catalog.owned += projectDto("o-$it") }
        repeat(1_200) { catalog.joined += projectDto("j-$it", owner = TargetId) }

        ids()

        assertEquals(10, catalog.callsOf("owner").size)
        assertEquals(10, catalog.callsOf("participant").size)
    }

    @Test
    fun theReviewsOfTheTargetAreReadPageByPageAndAnOldOneStillHidesItsProject() = runTest {
        catalog.owned += projectDto("p-old")
        catalog.owned += projectDto("p-new")
        val others = (0 until 230).map { reviewDto("x-$it", author = "other-$it") }.toMutableList()
        others[215] = reviewDto("p-old", author = ViewerId)
        reviews.about[TargetId] = others

        assertEquals(listOf("p-new"), ids())

        assertEquals(listOf(0, 100, 200), reviews.reads.map { it.third })
        assertTrue(reviews.reads.all { it.first == TargetId && it.second == 100 })
    }

    @Test
    fun aFullPageOfReviewsIsFollowedByAnotherRequestWhichMayBeEmpty() = runTest {
        reviews.about[TargetId] = (0 until 100).map { reviewDto("x-$it", author = "other") }.toMutableList()

        ids()

        assertEquals(listOf(0, 100), reviews.reads.map { it.third })
    }

    @Test
    fun aPageOfReviewsNotFullEndsTheReading() = runTest {
        reviews.about[TargetId] = (0 until 99).map { reviewDto("x-$it", author = "other") }.toMutableList()

        ids()

        assertEquals(listOf(0), reviews.reads.map { it.third })
    }

    @Test
    fun theReviewsAreReadNoFurtherThanTheOffsetTheApiAllows() = runTest {
        catalog.owned += projectDto("p-1")
        reviews.onUserReviews = { _, _, offset ->
            // Always a full page; the viewer's own review is on the very last page the API lets us ask for.
            UserReviewListDto(
                (0 until 100).map { reviewDto("x-$offset-$it", author = "other") } +
                    if (offset == 10_000) listOf(reviewDto("p-1", author = ViewerId)) else emptyList(),
            )
        }

        assertEquals(emptyList(), ids())

        assertEquals((0..10_000 step 100).toList(), reviews.reads.map { it.third })
        assertEquals(101, reviews.reads.size, "pages 0 to 10 000; a request beyond is refused by the API itself")
    }

    @Test
    fun aFullPageOfReviewsAtTheLastOffsetEndsTheReadingWithoutAnError() = runTest {
        catalog.owned += projectDto("p-1")
        reviews.onUserReviews = { _, _, offset -> UserReviewListDto((0 until 100).map { reviewDto("x-$offset-$it", author = "other") }) }

        assertEquals(listOf("p-1"), ids())
        assertEquals(10_000, reviews.reads.last().third)
    }

    // ---- failures ----

    @Test
    fun aFailureOfTheOwnedProjectsFailsTheCallAsItIs() = runTest {
        val failure = RemoteFailure.Http(500, "boom")
        catalog.onList = { params, _ -> if (params.mine == "owner") throw failure else ProjectListDto() }

        assertIsFailure(failure, assertFailsWith<RemoteFailure.Http> { source.getReviewableProjects(target) })
    }

    @Test
    fun aFailureOfTheJoinedProjectsFailsTheCallAsItIs() = runTest {
        val failure = RemoteFailure.NoConnection()
        catalog.onList = { params, _ -> if (params.mine == "participant") throw failure else ProjectListDto() }

        assertIsFailure(failure, assertFailsWith<RemoteFailure.NoConnection> { source.getReviewableProjects(target) })
    }

    @Test
    fun aFailureOfTheReviewsFailsTheCallAsItIs() = runTest {
        catalog.owned += projectDto("p-1")
        val failure = RemoteFailure.Timeout()
        reviews.onUserReviews = { _, _, _ -> throw failure }

        assertIsFailure(failure, assertFailsWith<RemoteFailure.Timeout> { source.getReviewableProjects(target) })
    }

    @Test
    fun aFailureOnALaterPageFailsTheCallAndNoPartialListIsReturned() = runTest {
        repeat(250) { catalog.owned += projectDto("o-$it") }
        val failure = RemoteFailure.Http(503)
        catalog.onList = { params, page ->
            if (params.mine == "owner" && page.offset == 100) throw failure
            ProjectListDto(catalog.owned.drop(page.offset).take(page.limit), catalog.owned.size.toLong())
        }

        assertIsFailure(failure, assertFailsWith<RemoteFailure.Http> { source.getReviewableProjects(target) })
    }

    @Test
    fun aFailureEndsTheOtherReadsInsteadOfWaitingForThem() = runTest {
        val failure = RemoteFailure.Http(500)
        val othersWaiting = CompletableDeferred<Unit>()
        var waiting = 0
        var cancelled = 0
        suspend fun waitUntilCancelled() {
            if (++waiting == 2) othersWaiting.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled++
            }
        }
        catalog.onList = { params, _ ->
            if (params.mine == "owner") {
                // Fails only when the other two are already waiting, so that they have something to cancel.
                othersWaiting.await()
                throw failure
            } else {
                waitUntilCancelled()
                ProjectListDto()
            }
        }
        reviews.onUserReviews = { _, _, _ ->
            waitUntilCancelled()
            UserReviewListDto()
        }

        assertIsFailure(failure, assertFailsWith<RemoteFailure.Http> { source.getReviewableProjects(target) })

        assertEquals(2, cancelled, "the reads that were still waiting are cancelled")
    }

    @Test
    fun aMalformedAnswerIsAFailureToo() = runTest {
        val failure = RemoteFailure.Malformed()
        catalog.onList = { _, _ -> throw failure }

        assertIsFailure(failure, assertFailsWith<RemoteFailure.Malformed> { source.getReviewableProjects(target) })
    }

    @Test
    fun aCancelledCallStaysCancelledAndCancelsItsReads() = runTest {
        val gate = CompletableDeferred<Unit>()
        var cancelled = 0
        catalog.onList = { _, _ ->
            try {
                gate.await()
                ProjectListDto()
            } finally {
                cancelled++
            }
        }
        reviews.onUserReviews = { _, _, _ ->
            try {
                gate.await()
                UserReviewListDto()
            } finally {
                cancelled++
            }
        }
        var outcome: Throwable? = null
        val job = backgroundScope.launch {
            try {
                source.getReviewableProjects(target)
            } catch (e: Throwable) {
                outcome = e
                throw e
            }
        }
        runCurrent()

        job.cancel()
        runCurrent()

        assertTrue(job.isCancelled)
        assertTrue(outcome is CancellationException, "cancellation is not turned into a failure: $outcome")
        assertEquals(3, cancelled)
    }

    @Test
    fun nothingIsRememberedBetweenCalls() = runTest {
        catalog.owned += projectDto("p-1")
        assertEquals(listOf("p-1"), ids())

        reviews.about[TargetId] = mutableListOf(reviewDto("p-1"))

        assertEquals(emptyList(), ids())
        assertEquals(4, catalog.listCalls.size, "two calls, two list reads each")
        assertEquals(2, reviews.reads.size, "two calls, one review read each")
    }
}
