package org.matchlab.academic_net.di

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.feature.profile.domain.MyProfile
import org.matchlab.academic_net.feature.profile.domain.PortfolioChange
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDraft
import org.matchlab.academic_net.feature.profile.domain.UserProfile
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The profile feature from its screens' use cases down to the HTTP calls of the Gateway (a mock engine): the wiring of the
 * session graph that no layer's own tests see. The answers are the shapes `docs/openapi.yaml` gives.
 */
class ProfileGraphTest {
    private val other = "a1b2c3d4-0000-4000-8000-000000000002"
    private val me = ServerUserId
    private val session = Session(UserId(me), StudentEmail, AccessToken("token-1"))

    private val otherProfile =
        """{"user_id":"$other","details":{"display_name":"Елена Смирнова","university":"НИУ ВШЭ","study_level":"магистратура","study_year":2,"specialization":"Biology","about":"О себе","skills":["R"]},"verified":true,"rating":4.5,"review_count":2,"positive_review_count":2}"""

    private val otherAchievements =
        """{"items":[{"id":"w1","details":{"kind":"scientific","title":"Статья","description":"Описание","url":"https://doi.org/10.1/x"},"revision":1,"created_at_unix":"1760000000"},{"id":"d1","details":{"kind":"education","title":"Диплом","institution":"ВШЭ","graduation_year":2024,"url":"https://x.org/d.pdf"},"revision":1,"created_at_unix":"1750000000"}]}"""

    private val otherReviews =
        """{"items":[{"id":"r1","author_user_id":"$me","author_display_name":"Я","rating":5,"title":"Отлично","text":"Текст","project_title":"Проект","project_completed_at_unix":"1750000000"}]}"""

    /** A server that knows the profile of [other] and nobody else. */
    private fun serverWithOther() = FakeServer().apply {
        onOther = { arrival ->
            when (arrival.call) {
                "GET /api/v1/users/$other/profile" -> json(otherProfile)
                "GET /api/v1/users/$other/achievements" -> json(otherAchievements)
                "GET /api/v1/users/$other/reviews" -> json(otherReviews)
                else -> jsonError(HttpStatusCode.NotFound, "not found")
            }
        }
    }

    private fun dependenciesOf(server: FakeServer) = AppLaunch(server).sessionGraph(session).dependencies

    // ---- the profile of somebody else ----

    @Test
    fun theProfileOfAPersonIsAssembledFromThreeCallsOfTheServer() = runTest {
        val server = serverWithOther()

        val result = dependenciesOf(server).profile.getUserProfile(UserId(other))

        val profile = assertIs<LoadResult.Found<UserProfile>>(result).value
        assertEquals("Елена Смирнова", profile.person.user.name)
        assertTrue(profile.person.user.isVerified)
        assertEquals(Study(level = "магистратура", year = 2), profile.person.study)
        assertEquals(listOf("w1"), profile.works.map { it.id.value })
        assertEquals(listOf("d1"), profile.documents.map { it.id.value })
        assertEquals(4.5, profile.reviews.average)
        assertEquals(listOf(true), profile.latestReviews.map { it.isByViewer }, "the viewer wrote the review")
        assertEquals(
            setOf("GET /api/v1/users/$other/profile", "GET /api/v1/users/$other/achievements", "GET /api/v1/users/$other/reviews"),
            server.calls.toSet(),
        )
        assertTrue(server.arrivals.all { it.authorization == listOf("Bearer token-1") })
    }

    @Test
    fun aPersonTheServerDoesNotKnowIsNotFound() = runTest {
        val server = FakeServer() // every unknown path is a 404

        val result = dependenciesOf(server).profile.getUserProfile(UserId(other))

        assertEquals(LoadResult.NotFound, result)
    }

    @Test
    fun aServerErrorIsAFailureWithItsStatus() = runTest {
        val server = FakeServer().apply { onOther = { jsonError(HttpStatusCode.InternalServerError) } }

        val result = dependenciesOf(server).profile.getUserProfile(UserId(other))

        assertEquals(500, assertIs<RemoteFailure.Http>(assertIs<LoadResult.Failure>(result).cause).status)
    }

    // ---- the viewer's own profile ----

    @Test
    fun aNewAccountSavesItsFirstProfileThroughAReadAndAWrite() = runTest {
        var saved: String? = null
        val server = FakeServer().apply {
            onOther = { arrival ->
                when (arrival.call) {
                    "GET /api/v1/users/me/profile" ->
                        saved?.let { json("""{"user_id":"$me","details":$it}""") } ?: jsonError(HttpStatusCode.NotFound, "no profile")
                    "PUT /api/v1/users/me/profile" -> {
                        saved = arrival.body
                        json("""{"user_id":"$me","details":${arrival.body}}""")
                    }
                    else -> jsonError(HttpStatusCode.NotFound, "not found")
                }
            }
        }

        val result = dependenciesOf(server).profile.updateProfile(ProfileDraft(name = "Имя", university = "ВШЭ"))

        val mine = assertIs<SubmitResult.Success<MyProfile>>(result).value
        assertEquals("Имя", mine.profile.person.user.name)
        assertEquals("ВШЭ", mine.profile.person.university)
        // The profile is read before it is written (a 404 is an empty one), and the written one is read back.
        assertEquals(listOf("GET /api/v1/users/me/profile", "PUT /api/v1/users/me/profile"), server.calls.take(2))
        val body = server.requests("PUT /api/v1/users/me/profile").single().body
        assertTrue("\"display_name\":\"Имя\"" in body, body)
        assertTrue("\"university\":\"ВШЭ\"" in body, body)
    }

    @Test
    fun theProjectTypesOfTheProfileAreReadAndWrittenAsTextsAndTheUnknownEntriesStay() = runTest {
        var saved: String? = null
        val server = FakeServer().apply {
            onOther = { arrival ->
                when (arrival.call) {
                    "GET /api/v1/users/me/profile" ->
                        json("""{"user_id":"$me","details":${saved ?: """{"display_name":"Я","project_types":["scientific","from-another-client"]}"""}}""")
                    "PUT /api/v1/users/me/profile" -> {
                        saved = arrival.body
                        json("""{"user_id":"$me","details":${arrival.body}}""")
                    }
                    else -> jsonError(HttpStatusCode.NotFound, "not found")
                }
            }
        }
        val dependencies = dependenciesOf(server)

        val result = dependencies.profile.updateProfile(ProfileDraft(name = "Я", interestTypes = setOf(ProjectType.Practical)))

        val mine = assertIs<SubmitResult.Success<MyProfile>>(result).value
        assertEquals(setOf(ProjectType.Practical), mine.profile.interests.projectTypes)
        val body = assertNotNull(saved)
        assertTrue("\"project_types\":[\"from-another-client\",\"practical\"]" in body, body)
        assertTrue(dependencies.profile.capabilities.interestTypes)
        assertTrue(dependencies.profile.capabilities.verificationStatuses)
    }

    @Test
    fun aWorkWithALinkIsAddedAndSentForVerificationAsTwoCalls() = runTest {
        val record = """{"id":"new-1","user_id":"$me","details":{"kind":"scientific","title":"Статья","description":"О графах","url":"https://doi.org/10.1/x"},"revision":1,"created_at_unix":"1760000000"}"""
        val server = FakeServer().apply {
            onOther = { arrival ->
                when (arrival.call) {
                    "POST /api/v1/users/me/achievements" -> json(record, HttpStatusCode.Created)
                    "POST /api/v1/users/me/achievements/new-1/submit" -> json(record)
                    else -> jsonError(HttpStatusCode.NotFound, "not found")
                }
            }
        }

        val result = dependenciesOf(server).profile.addWork(
            WorkDraft(title = "Статья", description = "О графах", link = "https://doi.org/10.1/x"),
        )

        val saved = assertIs<PortfolioChange.Saved<PortfolioWork>>(assertIs<SubmitResult.Success<PortfolioChange<PortfolioWork>>>(result).value)
        assertTrue(saved.sent)
        assertEquals(WorkId("new-1"), saved.value.id)
        assertEquals(
            listOf("POST /api/v1/users/me/achievements", "POST /api/v1/users/me/achievements/new-1/submit"),
            server.calls.take(2),
        )
        assertEquals(
            """{"kind":"scientific","title":"Статья","description":"О графах","url":"https://doi.org/10.1/x"}""",
            server.requests("POST /api/v1/users/me/achievements").single().body,
        )
        assertEquals("", server.requests("POST /api/v1/users/me/achievements/new-1/submit").single().body, "the send has no body")
    }

    @Test
    fun aWorkWithoutALinkIsAddedAndNotSent() = runTest {
        val record = """{"id":"new-2","user_id":"$me","details":{"kind":"project","title":"Проект","description":"О нём"},"revision":1}"""
        val server = FakeServer().apply {
            onOther = { arrival ->
                if (arrival.call == "POST /api/v1/users/me/achievements") {
                    json(record, HttpStatusCode.Created)
                } else {
                    jsonError(HttpStatusCode.NotFound, "not found")
                }
            }
        }

        val result = dependenciesOf(server).profile.addWork(WorkDraft(title = "Проект", description = "О нём"))

        val saved = assertIs<PortfolioChange.Saved<PortfolioWork>>(assertIs<SubmitResult.Success<PortfolioChange<PortfolioWork>>>(result).value)
        assertEquals(false, saved.sent)
        assertEquals(emptyList(), server.requests("POST /api/v1/users/me/achievements/new-2/submit"))
    }
}
