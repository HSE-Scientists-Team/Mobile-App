package org.matchlab.academic_net.gateway

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * The viewer's profile: `GET`, and the one way to `PUT` it, a read-modify-write that must not lose what the typed form
 * does not know (the avatar, the contacts, the strings without a list of values, the keys the API may add).
 */
class ProfilesApiTest {
    private val path = "/api/v1/users/me/profile"

    /** One stored profile, answered like the API: `GET` gives it, `PUT` replaces its `details`. */
    private class ProfileServer(var stored: String? = ProfileSpec.Profile) {
        var failGet: HttpStatusCode? = null
        var failPut: HttpStatusCode? = null
        var beforePut: suspend (index: Int) -> Unit = {}
        var puts = 0

        /** The requests that reached the server, in the order they came, also those that have not been answered yet. */
        val arrived = mutableListOf<String>()

        val fixture = SessionFixture { request ->
            arrived += request.call
            when (request.method) {
                HttpMethod.Get -> failGet?.let { jsonError(it, "refused") }
                    ?: stored?.let { json(it) }
                    ?: jsonError(HttpStatusCode.NotFound, "no profile")
                HttpMethod.Put -> {
                    beforePut(++puts)
                    val refusal = failPut
                    if (refusal != null) {
                        jsonError(refusal, "refused")
                    } else {
                        val answer = """{"user_id":"${ProfileSpec.MeId}","details":${request.bodyText()},"review_count":0}"""
                        stored = answer
                        json(answer)
                    }
                }
                else -> respond("", HttpStatusCode.MethodNotAllowed)
            }
        }
        val profiles get() = fixture.session.profiles
    }

    // ---- reading ----

    @Test
    fun myProfileReadsTheProfileOfTheSessionWithItsToken() = runTest {
        val server = ProfileServer()

        val profile = server.profiles.myProfile()

        assertEquals("Арджун Мехта", profile.details.displayName)
        assertEquals(listOf("GET $path"), server.fixture.calls)
        assertEquals(listOf("Bearer ${SessionFixture.Token}"), server.fixture.requests.single().authorizations)
    }

    @Test
    fun anAccountWithoutAProfileGetsA404AsIs() = runTest {
        val server = ProfileServer(stored = null)

        val failure = failureOf<RemoteFailure.Http> { server.profiles.myProfile() }

        assertEquals(404, failure.status)
    }

    // ---- writing: what goes back ----

    @Test
    fun anUpdateReadsTheProfileFirstAndThenReplacesIt() = runTest {
        val server = ProfileServer()

        val saved = server.profiles.updateMyProfile { it.copy(about = "Новый текст") }

        assertEquals(listOf("GET $path", "PUT $path"), server.fixture.calls)
        assertEquals("Новый текст", saved.details.about)
        assertEquals("Новый текст", server.fixture.requests[1].bodyObject().getValue("about").jsonPrimitive.content)
    }

    @Test
    fun whatTheFormDoesNotChangeGoesBackAsItCame() = runTest {
        val server = ProfileServer()

        server.profiles.updateMyProfile { it.copy(about = "Новый текст") }

        val body = server.fixture.requests[1].bodyObject()
        assertEquals("Арджун Мехта", body.getValue("display_name").jsonPrimitive.content)
        assertEquals("file-1", body.getValue("avatar_file_id").jsonPrimitive.content)
        assertEquals("arjun@hse.ru", body.getValue("contact_email").jsonPrimitive.content)
        assertEquals("@arjun", body.getValue("telegram").jsonPrimitive.content)
        assertEquals("бакалавриат", body.getValue("study_level").jsonPrimitive.content)
        assertEquals("3", body.getValue("study_year").jsonPrimitive.content)
        assertTrue(body.getValue("open_to_collaboration").jsonPrimitive.boolean)
    }

    @Test
    fun theProjectTypesWithoutAListOfValuesGoBackUntouched() = runTest {
        val server = ProfileServer()

        // API-GAP G11: the client does not know what the strings mean, and must not drop or rewrite them.
        server.profiles.updateMyProfile { it.copy(about = "x") }

        val types = server.fixture.requests[1].bodyObject().getValue("project_types") as JsonArray
        assertEquals(listOf(JsonPrimitive("something-the-client-does-not-know")), types.toList())
    }

    @Test
    fun aKeyTheClientDoesNotKnowGoesBackToo() = runTest {
        val server = ProfileServer()

        server.profiles.updateMyProfile { it.copy(about = "x") }

        val body = server.fixture.requests[1].bodyObject()
        assertEquals("""{"nested":[1,2,3]}""", body.getValue("future_field").toString())
    }

    @Test
    fun aFieldTheChangeClearedIsGoneAndNotBroughtBackByTheOldAnswer() = runTest {
        val server = ProfileServer()

        server.profiles.updateMyProfile { it.copy(about = null, telegram = null) }

        val body = server.fixture.requests[1].bodyObject()
        assertFalse("about" in body)
        assertFalse("telegram" in body)
        assertEquals("arjun@hse.ru", body.getValue("contact_email").jsonPrimitive.content)
    }

    @Test
    fun theChangeGetsTheTypedDetailsOfTheAnswer() = runTest {
        val server = ProfileServer()
        var seen: UserProfileInputDto? = null

        server.profiles.updateMyProfile { details -> details.also { seen = it } }

        val details = seen!!
        assertEquals("Арджун Мехта", details.displayName)
        assertEquals(listOf("Kotlin", "SQL"), details.skills)
        assertEquals(3, details.studyYear)
    }

    @Test
    fun theBodyIsSnakeCaseJsonWithoutNulls() = runTest {
        val server = ProfileServer(stored = """{"user_id":"${ProfileSpec.MeId}","details":{"display_name":"Имя"}}""")

        server.profiles.updateMyProfile { it.copy(university = "ВШЭ") }

        val body = server.fixture.requests[1].bodyText()
        assertEquals("""{"display_name":"Имя","university":"ВШЭ","project_types":[],"interests":[],"skills":[]}""", body)
    }

    // ---- writing: when nothing is sent ----

    @Test
    fun a404OnTheReadIsAnEmptyProfileToStartFrom() = runTest {
        val server = ProfileServer(stored = null)
        var seen: UserProfileInputDto? = null

        val saved = server.profiles.updateMyProfile { details ->
            seen = details
            details.copy(displayName = "Первое имя")
        }

        assertEquals(UserProfileInputDto(), seen)
        assertEquals(listOf("GET $path", "PUT $path"), server.fixture.calls)
        assertEquals("Первое имя", saved.details.displayName)
    }

    @Test
    fun aBlankNameIsRefusedAndNothingIsWritten() = runTest {
        val server = ProfileServer()

        assertFailsWith<IllegalArgumentException> { server.profiles.updateMyProfile { it.copy(displayName = "  ") } }
        assertFailsWith<IllegalArgumentException> { server.profiles.updateMyProfile { it.copy(displayName = "") } }

        assertEquals(listOf("GET $path", "GET $path"), server.fixture.calls)
    }

    @Test
    fun anAccountWithoutAProfileCannotSaveWithoutAName() = runTest {
        val server = ProfileServer(stored = null)

        assertFailsWith<IllegalArgumentException> { server.profiles.updateMyProfile { it.copy(about = "x") } }

        assertEquals(listOf("GET $path"), server.fixture.calls)
    }

    @Test
    fun aFailedReadWritesNothing() = runTest {
        val server = ProfileServer().apply { failGet = HttpStatusCode.InternalServerError }
        var changed = false

        val failure = failureOf<RemoteFailure.Http> { server.profiles.updateMyProfile { changed = true; it } }

        assertEquals(500, failure.status)
        assertFalse(changed, "the change must not run on a profile that was not read")
        assertEquals(listOf("GET $path"), server.fixture.calls)
    }

    @Test
    fun aFailureOfTheChangeItselfWritesNothingAndIsNotWrapped() = runTest {
        val server = ProfileServer()

        val thrown = assertFailsWith<IllegalStateException> {
            server.profiles.updateMyProfile { throw IllegalStateException("no profile to put it in") }
        }

        assertEquals("no profile to put it in", thrown.message)
        assertEquals(listOf("GET $path"), server.fixture.calls)
    }

    @Test
    fun aRejectedWriteIsThrownAndTheNextUpdateStillWorks() = runTest {
        val server = ProfileServer().apply { failPut = HttpStatusCode.Conflict }

        assertEquals(409, failureOf<RemoteFailure.Http> { server.profiles.updateMyProfile { it.copy(about = "a") } }.status)
        server.failPut = null
        val saved = server.profiles.updateMyProfile { it.copy(about = "b") }

        // The lock of the writes was let go of the failure.
        assertEquals("b", saved.details.about)
        assertEquals(4, server.fixture.requests.size)
    }

    @Test
    fun aSessionRefusedOnTheReadIsReportedToTheListener() = runTest {
        val server = ProfileServer().apply { failGet = HttpStatusCode.Unauthorized }

        assertEquals(401, failureOf<RemoteFailure.Http> { server.profiles.updateMyProfile { it } }.status)

        assertEquals(listOf(SessionFixture.Token), server.fixture.rejected)
    }

    // ---- writing: one at a time ----

    @Test
    fun twoUpdatesRunOneAfterTheOther() = runTest {
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val server = ProfileServer().apply {
            beforePut = { index ->
                if (index == 1) {
                    firstWriteStarted.complete(Unit)
                    releaseFirstWrite.await()
                }
            }
        }
        val first = async { server.profiles.updateMyProfile { it.copy(about = "первый") } }
        firstWriteStarted.await()
        val second = async { server.profiles.updateMyProfile { it.copy(skills = listOf("Rust")) } }
        // Real time, on purpose: a second update that did not wait would show up in the history in this pause.
        withContext(Dispatchers.Default) { delay(100.milliseconds) }
        assertEquals(listOf("GET $path", "PUT $path"), server.arrived, "the second update must wait for the first write")

        releaseFirstWrite.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("GET $path", "PUT $path", "GET $path", "PUT $path"), server.fixture.calls)
        // The second update started from what the first one wrote.
        val secondBody = server.fixture.requests[3].bodyObject()
        assertEquals("первый", secondBody.getValue("about").jsonPrimitive.content)
        assertEquals(listOf(JsonPrimitive("Rust")), (secondBody.getValue("skills") as JsonArray).toList())
    }

    // ---- the directory ----

    @Test
    fun aSavedProfileIsForgottenByTheDirectory() = runTest {
        val publicPath = "/api/v1/users/${ProfileSpec.MeId}/profile"
        var publicReads = 0
        val fixture = SessionFixture { request ->
            when {
                request.url.encodedPath == publicPath -> {
                    publicReads++
                    json(ProfileSpec.Profile)
                }
                request.method == HttpMethod.Get -> json(ProfileSpec.Profile)
                else -> json(request.bodyText().let { """{"user_id":"${ProfileSpec.MeId}","details":$it}""" })
            }
        }
        fixture.session.users.profile(ProfileSpec.MeId)
        fixture.session.users.profile(ProfileSpec.MeId)
        assertEquals(1, publicReads, "the second read is served from the directory")

        fixture.session.profiles.updateMyProfile { it.copy(about = "Новое") }
        fixture.session.users.profile(ProfileSpec.MeId)

        assertEquals(2, publicReads, "after a save the next read asks the server again")
    }

    @Test
    fun theDirectoryFallsBackToTheIdOfTheReadWhenTheWriteAnswerHasNone() = runTest {
        val publicPath = "/api/v1/users/${ProfileSpec.MeId}/profile"
        var publicReads = 0
        val fixture = SessionFixture { request ->
            when {
                request.url.encodedPath == publicPath -> {
                    publicReads++
                    json(ProfileSpec.Profile)
                }
                request.method == HttpMethod.Get -> json(ProfileSpec.Profile)
                else -> json("""{"details":{"display_name":"Имя"}}""")
            }
        }
        fixture.session.users.profile(ProfileSpec.MeId)

        fixture.session.profiles.updateMyProfile { it.copy(about = "Новое") }
        fixture.session.users.profile(ProfileSpec.MeId)

        assertEquals(2, publicReads)
    }
}
