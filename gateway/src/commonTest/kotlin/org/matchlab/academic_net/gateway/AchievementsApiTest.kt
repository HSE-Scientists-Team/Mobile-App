package org.matchlab.academic_net.gateway

import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.UserAchievementInputDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The works, the diplomas and the certificates: the lists with their page limits, the calls of the owner, and the
 * read-modify-write of a change that must not lose the proof file or the keys the API may add.
 */
class AchievementsApiTest {
    private val mine = "/api/v1/users/me/achievements"
    private val record = "$mine/ach-1"

    private fun list(vararg items: String) = ProfileSpec.achievements(*items)

    // ---- the lists ----

    @Test
    fun theListOfAPersonAsksTheLargestPageFromTheStart() = runTest {
        val fixture = SessionFixture { json(list(ProfileSpec.achievement())) }

        val page = fixture.session.achievements.userAchievements(ProfileSpec.OtherId)

        val request = fixture.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/users/${ProfileSpec.OtherId}/achievements", request.url.encodedPath)
        assertEquals("100", request.url.parameters["limit"])
        assertEquals("0", request.url.parameters["offset"])
        assertEquals(listOf("ach-1"), page.items.map { it.id })
    }

    @Test
    fun theListOfTheOwnerIsAnotherPath() = runTest {
        val fixture = SessionFixture { json(list()) }

        val page = fixture.session.achievements.myAchievements(limit = 50, offset = 100)

        val request = fixture.requests.single()
        assertEquals(mine, request.url.encodedPath)
        assertEquals("50", request.url.parameters["limit"])
        assertEquals("100", request.url.parameters["offset"])
        assertEquals(emptyList(), page.items)
    }

    @Test
    fun theIdOfAPersonIsOneSegmentOfThePath() = runTest {
        val fixture = SessionFixture { json(list()) }

        fixture.session.achievements.userAchievements("a/b?x=1#c")

        val request = fixture.requests.single()
        val path = request.url.encodedPath
        // "", api, v1, users, the id, achievements: the slash of the id did not make a segment of its own.
        assertEquals(6, path.split('/').size, path)
        assertTrue('?' !in path && '#' !in path, path)
        assertEquals(setOf("limit", "offset"), request.url.parameters.names())
    }

    @Test
    fun aPageOutsideTheRangeOfTheApiIsRefusedBeforeAnyRequest() = runTest {
        val fixture = SessionFixture { json(list()) }
        val api = fixture.session.achievements

        assertFailsWith<IllegalArgumentException> { api.userAchievements("u", limit = 0) }
        assertFailsWith<IllegalArgumentException> { api.userAchievements("u", limit = 101) }
        assertFailsWith<IllegalArgumentException> { api.userAchievements("u", offset = -1) }
        assertFailsWith<IllegalArgumentException> { api.userAchievements("u", offset = 10_001) }
        assertFailsWith<IllegalArgumentException> { api.myAchievements(limit = 0) }
        assertFailsWith<IllegalArgumentException> { api.myAchievements(limit = 101) }
        assertFailsWith<IllegalArgumentException> { api.myAchievements(offset = -1) }
        assertFailsWith<IllegalArgumentException> { api.myAchievements(offset = 10_001) }

        assertEquals(emptyList(), fixture.requests)
    }

    @Test
    fun theLimitsThemselvesAreAllowed() = runTest {
        val fixture = SessionFixture { json(list()) }
        val api = fixture.session.achievements

        api.userAchievements("u", limit = 1, offset = 0)
        api.myAchievements(limit = 100, offset = 10_000)

        assertEquals(2, fixture.requests.size)
    }

    // ---- one record ----

    @Test
    fun oneRecordOfTheOwnerIsReadByItsId() = runTest {
        val fixture = SessionFixture { json(ProfileSpec.achievement()) }

        val achievement = fixture.session.achievements.myAchievement("ach-1")

        assertEquals("GET $record", fixture.calls.single())
        assertEquals("Статья о графах", achievement.details.title)
        assertEquals(3, achievement.revision)
        assertEquals("Нужна ссылка", achievement.moderationComment)
        assertEquals(1_760_000_000L, achievement.createdAtUnix)
    }

    @Test
    fun aRecordThatIsNotTheOwnersIsA404() = runTest {
        val fixture = SessionFixture { jsonError(HttpStatusCode.NotFound, "not found") }

        assertEquals(404, failureOf<RemoteFailure.Http> { fixture.session.achievements.myAchievement("x") }.status)
    }

    // ---- adding, sending, deleting ----

    @Test
    fun aNewRecordIsPostedAsSnakeCaseWithoutNulls() = runTest {
        val fixture = SessionFixture { json(ProfileSpec.achievement(), HttpStatusCode.Created) }

        val created = fixture.session.achievements.create(
            UserAchievementInputDto(kind = "certificate", title = "Сертификат", institution = "Coursera", graduationYear = 2025),
        )

        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(mine, request.url.encodedPath)
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
        assertEquals(
            """{"kind":"certificate","title":"Сертификат","institution":"Coursera","graduation_year":2025}""",
            request.bodyText(),
        )
        assertEquals("ach-1", created.id)
    }

    @Test
    fun aNewRecordNeverCarriesARevision() = runTest {
        val fixture = SessionFixture { json(ProfileSpec.achievement(), HttpStatusCode.Created) }

        fixture.session.achievements.create(UserAchievementInputDto(kind = "project", title = "Проект"))

        assertFalse("expected_revision" in fixture.requests.single().bodyObject())
    }

    @Test
    fun aRejectedNewRecordIsThrownWithItsStatus() = runTest {
        val fixture = SessionFixture { jsonError(HttpStatusCode.BadRequest, "title is required") }

        val failure = failureOf<RemoteFailure.Http> {
            fixture.session.achievements.create(UserAchievementInputDto(kind = "project", title = ""))
        }

        assertEquals(400, failure.status)
    }

    @Test
    fun sendingForVerificationPostsWithoutABody() = runTest {
        val fixture = SessionFixture { json(ProfileSpec.achievement()) }

        val sent = fixture.session.achievements.submit("ach-1")

        val request = fixture.requests.single()
        assertEquals("POST $record/submit", request.call)
        assertEquals("", request.bodyText())
        assertNull(request.body.contentType)
        assertEquals("ach-1", sent.id)
    }

    @Test
    fun deletingSendsADeleteAndExpectsNoContent() = runTest {
        val fixture = SessionFixture { noContent() }

        fixture.session.achievements.delete("ach-1")

        assertEquals("DELETE $record", fixture.calls.single())
    }

    @Test
    fun deletingARecordThatIsGoneIsA404ForTheCaller() = runTest {
        val fixture = SessionFixture { jsonError(HttpStatusCode.NotFound, "gone") }

        assertEquals(404, failureOf<RemoteFailure.Http> { fixture.session.achievements.delete("ach-1") }.status)
    }

    @Test
    fun theIdOfARecordIsOneSegmentOfThePath() = runTest {
        val fixture = SessionFixture { noContent() }

        fixture.session.achievements.delete("../users/me")

        val path = fixture.requests.single().url.encodedPath
        // "", api, v1, users, me, achievements, the id: it did not climb out of the list of the viewer.
        assertEquals(7, path.split('/').size, path)
        assertFalse("/../" in path, path)
    }

    // ---- changing ----

    /** The record is stored on the "server"; a `PUT` replaces its `details` and bumps the revision. */
    private class RecordServer(var stored: String = ProfileSpec.achievement(revision = 3)) {
        var failPut: HttpStatusCode? = null
        val arrived = mutableListOf<String>()

        val fixture = SessionFixture { request ->
            arrived += request.call
            when (request.method) {
                HttpMethod.Get -> json(stored)
                HttpMethod.Put -> {
                    val refusal = failPut
                    if (refusal != null) {
                        jsonError(refusal, "refused")
                    } else {
                        stored = ProfileSpec.achievement(revision = 4, details = request.bodyText())
                        json(stored)
                    }
                }
                else -> respond("", HttpStatusCode.MethodNotAllowed)
            }
        }
        val api get() = fixture.session.achievements
    }

    @Test
    fun aChangeIsReadFirstAndWrittenWithTheRevisionItWasBasedOn() = runTest {
        val server = RecordServer()

        val saved = server.api.updateMine("ach-1", expectedRevision = 3) { it.copy(title = "Новое название") }

        assertEquals(listOf("GET $record", "PUT $record"), server.fixture.calls)
        val body = server.fixture.requests[1].bodyObject()
        assertEquals("Новое название", body.getValue("title").jsonPrimitive.content)
        assertEquals("3", body.getValue("expected_revision").jsonPrimitive.content)
        assertEquals("Новое название", saved.details.title)
        assertEquals(4, saved.revision)
    }

    @Test
    fun theProofFileAndTheKeysTheClientDoesNotKnowGoBackAsTheyCame() = runTest {
        val server = RecordServer()

        server.api.updateMine("ach-1", expectedRevision = 3) { it.copy(title = "Другое") }

        val body = server.fixture.requests[1].bodyObject()
        assertEquals("file-9", body.getValue("evidence_file_id").jsonPrimitive.content)
        assertEquals("keep me", body.getValue("future_field").jsonPrimitive.content)
        assertEquals("scientific", body.getValue("kind").jsonPrimitive.content)
        assertEquals("ВШЭ", body.getValue("institution").jsonPrimitive.content)
    }

    @Test
    fun aClearedFieldIsGoneFromTheWrite() = runTest {
        val server = RecordServer()

        server.api.updateMine("ach-1", expectedRevision = 3) { it.copy(description = null, url = null) }

        val body = server.fixture.requests[1].bodyObject()
        assertFalse("description" in body)
        assertFalse("url" in body)
        assertTrue("evidence_file_id" in body)
    }

    @Test
    fun theRevisionIsTheOneOfTheCallerWhateverTheChangeSays() = runTest {
        val server = RecordServer()

        server.api.updateMine("ach-1", expectedRevision = 3) { it.copy(expectedRevision = 99) }

        assertEquals("3", server.fixture.requests[1].bodyObject().getValue("expected_revision").jsonPrimitive.content)
    }

    @Test
    fun aStaleRevisionIsAConflictAndNothingIsWritten() = runTest {
        val server = RecordServer()
        var changed = false

        val failure = failureOf<RemoteFailure.Http> {
            server.api.updateMine("ach-1", expectedRevision = 2) { changed = true; it }
        }

        assertEquals(409, failure.status)
        assertFalse(changed)
        assertEquals(listOf("GET $record"), server.arrived)
    }

    @Test
    fun aRecordThatIsGoneIsA404WithoutAWrite() = runTest {
        val fixture = SessionFixture { jsonError(HttpStatusCode.NotFound, "gone") }

        val failure = failureOf<RemoteFailure.Http> {
            fixture.session.achievements.updateMine("ach-1", expectedRevision = 3) { it }
        }

        assertEquals(404, failure.status)
        assertEquals(listOf("GET $record"), fixture.calls)
    }

    @Test
    fun aChangeThatFailsWritesNothing() = runTest {
        val server = RecordServer()

        assertFailsWith<IllegalStateException> {
            server.api.updateMine("ach-1", expectedRevision = 3) { throw IllegalStateException("boom") }
        }

        assertEquals(listOf("GET $record"), server.arrived)
    }

    @Test
    fun aWriteTheServerRefusesWith409IsThrown() = runTest {
        val server = RecordServer().apply { failPut = HttpStatusCode.Conflict }

        val failure = failureOf<RemoteFailure.Http> {
            server.api.updateMine("ach-1", expectedRevision = 3) { it.copy(title = "x") }
        }

        assertEquals(409, failure.status)
        assertEquals(listOf("GET $record", "PUT $record"), server.arrived)
    }

    @Test
    fun aRecordAnsweredWithoutDetailsIsStillWritten() = runTest {
        // Protobuf JSON leaves empty objects out; the typed details are empty and the change fills them in.
        val server = RecordServer(stored = """{"id":"ach-1","user_id":"u","revision":3}""")

        server.api.updateMine("ach-1", expectedRevision = 3) { it.copy(kind = "project", title = "Проект") }

        val body = server.fixture.requests[1].bodyObject()
        assertEquals("project", body.getValue("kind").jsonPrimitive.content)
        assertEquals("3", body.getValue("expected_revision").jsonPrimitive.content)
    }
}
