package org.matchlab.academic_net.feature.profile.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.gateway.UserDirectory
import org.matchlab.academic_net.gateway.dto.UserAchievementListDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserReviewDto
import org.matchlab.academic_net.gateway.dto.UserReviewListDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [ProfileRepositoryImpl] over the real [GatewayProfileDataSource] and the real mapping, with fakes of the Gateway calls
 * underneath: what the screens of the profile (09 the profile of another person, 10 and 10b the viewer's own, 09d one
 * review, the list of the reviews) receive when the server writes a number the calendar cannot hold (API-GAP G57) or
 * leaves out an id (API-GAP G55). The schema forbids neither, and one such record must not fail the whole page.
 */
class ProfileOverGatewayTest {
    private class Rig(scope: CoroutineScope) {
        val profiles = FakeProfilesApi()
        val achievements = FakeAchievementsApi()
        val reviews = FakeReviewsApi()
        val people = mutableMapOf<String, UserProfileDto>()

        private val directory = UserDirectory(
            fetch = { id -> people[id] ?: throw RemoteFailure.Http(404) },
            scope = scope,
        )
        private val source = GatewayProfileDataSource(profiles, achievements, reviews, directory, FakeMediaApi()) { TimeZone.UTC }
        val repository = ProfileRepositoryImpl(source, UserId(ViewerRaw))

        init {
            people[OtherRaw] = profileDto(userId = OtherRaw, name = "Елена")
            profiles.onMyProfile = { profileDto(userId = ViewerRaw, name = "Арджун") }
        }

        /** Every reviews call answers with [records], whoever is asked about. */
        fun reviewsAre(vararg records: UserReviewDto) {
            reviews.onUserReviews = { _, _, _ -> UserReviewListDto(records.toList()) }
        }
    }

    private fun TestScope.rig() = Rig(backgroundScope)

    private val other = UserId(OtherRaw)
    private val viewer = UserId(ViewerRaw)

    // ---- an end time the calendar cannot hold (API-GAP G57) ----

    @Test
    fun theProfileOfAnotherPersonLoadsWhenAReviewHasAnImpossibleEndTime() = runTest {
        val rig = rig()
        rig.reviewsAre(reviewDto(id = "r1", completedAt = Long.MAX_VALUE), reviewDto(id = "r2", completedAt = 1_751_313_600))

        val page = rig.repository.getProfile(other)

        assertNotNull(page, "the page loads")
        assertEquals("Елена", page.person.user.name)
        assertEquals(listOf("r1", "r2"), page.latestReviews.map { it.id.value })
        assertEquals(listOf<YearMonth?>(null, YearMonth(2025, Month.JUNE)), page.latestReviews.map { it.projectFinished })
    }

    @Test
    fun theViewersOwnProfileLoadsForTheScreenAndTheEditorWhenAReviewHasAnImpossibleEndTime() = runTest {
        val rig = rig()
        rig.reviewsAre(reviewDto(id = "r1", completedAt = Long.MAX_VALUE))

        val mine = rig.repository.observeMyProfile().first()
        val owner = rig.repository.getProfile(viewer)

        assertEquals("Арджун", mine.profile.person.user.name)
        assertEquals(listOf<YearMonth?>(null), mine.profile.latestReviews.map { it.projectFinished })
        assertNotNull(owner)
        assertEquals(listOf<YearMonth?>(null), owner.latestReviews.map { it.projectFinished })
    }

    @Test
    fun theListsOfReviewsAndOneReviewLoadWhenARecordHasAnImpossibleEndTime() = runTest {
        val rig = rig()
        rig.reviewsAre(reviewDto(id = "r1", completedAt = Long.MAX_VALUE), reviewDto(id = "r2", completedAt = Long.MAX_VALUE - 1))

        val first = rig.repository.getUserReviews(other)
        val next = rig.repository.getReviewsPage(other, offset = 2)
        val one = rig.repository.getReview(other, ReviewId("r2"))

        assertNotNull(first)
        assertEquals(listOf("r1", "r2"), first.firstPage.items.map { it.id.value })
        assertEquals(listOf<YearMonth?>(null, null), first.firstPage.items.map { it.projectFinished })
        assertEquals(listOf("r1", "r2"), next?.items?.map { it.id.value })
        assertNull(one?.projectFinished)
        assertEquals("r2", one?.id?.value)
    }

    // ---- ids the schema does not require (API-GAP G55) ----

    @Test
    fun theProfilePageIsThatOfTheAskedPersonWhenTheAnswerLeavesOutTheUserId() = runTest {
        val rig = rig()
        rig.people[OtherRaw] = profileDto(userId = "", name = "Елена")

        val page = rig.repository.getProfile(other)

        assertNotNull(page)
        assertEquals(other, page.person.user.id)
        assertEquals("Елена", page.person.user.name)
    }

    @Test
    fun aPageWithRecordsWithoutAnIdLoadsWithTheOthersAndTheAuthorWithoutAnIdStaysWithoutALink() = runTest {
        val rig = rig()
        rig.achievements.onUserAchievements = { _, _, _ ->
            UserAchievementListDto(
                listOf(
                    achievementDto(id = "", kind = "scientific", title = "Без номера"),
                    achievementDto(id = "w2", kind = "project", title = "С номером"),
                    achievementDto(id = " ", kind = "education"),
                ),
            )
        }
        rig.reviewsAre(
            reviewDto(id = "", authorId = "a1", completedAt = Long.MAX_VALUE),
            reviewDto(id = "r2", authorId = "", authorName = "Аноним"),
            reviewDto(id = "r3", authorId = ViewerRaw),
        )

        val page = rig.repository.getProfile(other)!!

        assertEquals(listOf("w2"), page.works.map { it.id.value })
        assertEquals(1, page.worksCount)
        assertEquals(emptyList(), page.documents)
        // The record without an id is gone; the one without an author id stays, with nobody behind the name.
        assertEquals(listOf("r2", "r3"), page.latestReviews.map { it.id.value })
        val anonymous = page.latestReviews.first()
        assertEquals("", anonymous.author.id.value)
        assertEquals("Аноним", anonymous.author.name)
        assertFalse(anonymous.isByViewer, "an author with no id is not the viewer")
        assertEquals(listOf(false, true), page.latestReviews.map { it.isByViewer })
    }

    @Test
    fun theViewersPortfolioAndReviewsLeaveOutTheRecordsWithoutAnId() = runTest {
        val rig = rig()
        rig.achievements.onMyAchievements = { _, _ ->
            UserAchievementListDto(
                listOf(
                    achievementDto(id = "", kind = "scientific"),
                    achievementDto(id = "w1", kind = "scientific"),
                    achievementDto(id = "", kind = "certificate"),
                    achievementDto(id = "d1", kind = "education"),
                ),
            )
        }
        rig.reviewsAre(reviewDto(id = ""), reviewDto(id = "r1"))

        val mine = rig.repository.observeMyProfile().first()

        assertEquals(listOf("w1"), mine.allWorks.map { it.id.value })
        assertEquals(listOf("d1"), mine.profile.documents.map { it.id.value })
        assertEquals(listOf("r1"), mine.profile.latestReviews.map { it.id.value })
    }

    @Test
    fun theListOfReviewsDoesNotFailWhenARecordHasNoId() = runTest {
        val rig = rig()
        rig.reviewsAre(reviewDto(id = ""), reviewDto(id = "r2"), reviewDto(id = "  "))

        val reviews = rig.repository.getUserReviews(other)

        assertNotNull(reviews)
        assertEquals(listOf("r2"), reviews.firstPage.items.map { it.id.value })
        assertNull(rig.repository.getReview(other, ReviewId("")), "a review without an id cannot be asked for by its id")
    }
}
