package org.matchlab.academic_net.gateway

import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.request.HttpRequestData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// What the tests of the profile calls share: the answers of the API in the shape the spec gives them, a Gateway with a
// session over a MockEngine, and a way to read the bodies the client sent.

/** The profile, the achievements and the reviews as the server writes them (protobuf JSON: snake_case, 64-bit numbers as strings). */
internal object ProfileSpec {
    const val MeId = Spec.UserId
    const val OtherId = "a1b2c3d4-0000-4000-8000-000000000002"

    /** `details` of a profile with every key the client knows and one it does not (`future_field`). */
    const val Details =
        """{"display_name":"Арджун Мехта","avatar_file_id":"file-1","university":"НИУ ВШЭ","study_level":"бакалавриат","study_year":3,"specialization":"Computer Science","about":"Люблю данные","open_to_collaboration":true,"project_types":["something-the-client-does-not-know"],"interests":["ML"],"skills":["Kotlin","SQL"],"contact_email":"arjun@hse.ru","telegram":"@arjun","future_field":{"nested":[1,2,3]}}"""

    const val Profile =
        """{"user_id":"$MeId","details":$Details,"verified":true,"rating":4.8,"review_count":5,"completed_project_count":2,"positive_review_count":4}"""

    /** `details` of an achievement with a proof file and a key the client does not know. */
    const val AchievementDetails =
        """{"id":"ach-1","kind":"scientific","title":"Статья о графах","description":"Описание","institution":"ВШЭ","graduation_year":2025,"url":"https://doi.org/10.1/x","evidence_file_id":"file-9","expected_revision":3,"future_field":"keep me"}"""

    fun achievement(
        id: String = "ach-1",
        revision: Int = 3,
        details: String = AchievementDetails,
        createdAt: String = "\"1760000000\"",
    ): String =
        """{"id":"$id","user_id":"$MeId","details":$details,"status":"whatever","revision":$revision,"moderation_comment":"Нужна ссылка","created_at_unix":$createdAt}"""

    fun achievements(vararg items: String): String = """{"items":[${items.joinToString(",")}]}"""

    fun review(id: String = "rev-1", createdAt: String = "\"1760000000\"", completedAt: String = "\"1750000000\""): String =
        """{"id":"$id","author_user_id":"$OtherId","author_display_name":"Елена","author_role":"initiator","project_id":"p-1","project_title":"Цифровой двойник","target_user_id":"$MeId","title":"Отлично","text":"Довёл до демо","rating":5,"created_at_unix":$createdAt,"project_completed_at_unix":$completedAt}"""

    fun reviews(vararg items: String): String = """{"items":[${items.joinToString(",")}]}"""
}

/** The JSON object in [text]. */
internal fun parseObject(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

/** The JSON object the client sent as the body of this request. */
internal suspend fun HttpRequestData.bodyObject(): JsonObject = parseObject(bodyText())

/** `GET /api/v1/users/me/profile` and the like: the method and the path, for the order of the calls. */
internal val HttpRequestData.call: String get() = "${method.value} ${url.encodedPath}"

/** A Gateway over a MockEngine with one session open, the way the data layers get it. */
internal class SessionFixture(
    config: GatewayConfig = GatewayConfig(),
    handler: MockRequestHandler,
) {
    val fixture = GatewayFixture(config, handler)

    /** The tokens the server refused (the 401 reports of the session). */
    val rejected = mutableListOf<String>()

    val session: GatewaySession = fixture.gateway.openSession(Token) { rejected += it }

    val requests: List<HttpRequestData> get() = fixture.requests

    /** The `METHOD path` of every request so far, in order. */
    val calls: List<String> get() = requests.map { it.call }

    companion object {
        const val Token = "session-token"
    }
}
