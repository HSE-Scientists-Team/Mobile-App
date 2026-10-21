package org.matchlab.academic_net.gateway

import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.request.HttpRequestData

// What the tests of the people search and of the recommendations share: the answers of the API in the shape the spec gives
// them (schemas Person and PeopleList) and a Gateway with one session over a MockEngine.

/** The people calls as the server writes them (protobuf JSON: snake_case, an `int64` as a string or a number). */
internal object PeopleSpec {
    /** A `Person` with every field the client knows. */
    const val FullPerson =
        """{"user_id":"u-1","display_name":"  Анна Иванова ","avatar_file_id":"file-1","university":" МГУ ","study_level":"магистратура","study_year":2,"specialization":"Physics","skills":["Python","Math"],"interests":["ML"],"open_to_collaboration":true,"verified":true,"rating":4.5,"completed_project_count":3}"""

    /** A `Person` that has only the id and a name. */
    fun person(id: String, name: String = "Имя $id"): String = """{"user_id":"$id","display_name":"$name"}"""

    /**
     * A `PeopleList`. [total] is the JSON of the field as the server might write it (`"42"` with the quotes, `42`),
     * or `null` for an answer without the field.
     */
    fun list(total: String?, vararg people: String): String {
        val items = """"items":[${people.joinToString(",")}]"""
        return if (total == null) "{$items}" else "{$items,\"total\":$total}"
    }
}

/** A Gateway with one session open over a MockEngine, the way a data layer gets it. */
internal class PeopleSessionFixture(handler: MockRequestHandler) {
    val fixture = GatewayFixture(handler = handler)

    /** The tokens the server refused (the 401 reports of the session). */
    val rejected = mutableListOf<String>()

    val session: GatewaySession = fixture.gateway.openSession(Token) { rejected += it }

    val requests: List<HttpRequestData> get() = fixture.requests

    companion object {
        const val Token = "people-session-token"
    }
}
