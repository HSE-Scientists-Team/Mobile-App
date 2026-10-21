package org.matchlab.academic_net.gateway

import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.request.HttpRequestData

// What the tests of the project calls share: the answers of the API in the shape the spec gives them (schemas Project
// and ProjectList) and a Gateway with one session over a MockEngine.

/** The projects as the server writes them (protobuf JSON: snake_case, an `int64` as a string or a number). */
internal object ProjectSpec {
    const val OwnerId = "owner-1"

    /** A role with every field the client reads. */
    const val FullRole = """{"id":"r-1","title":"ML Engineer","skills":["Python","PyTorch"],"slots":2,"occupied":1}"""

    /**
     * A `Project` with every field the client knows (the texts of `docs/api-values.md` in `type`, `field`, `status`, ISO
     * dates in `planned_*`) and a key it has never heard of.
     */
    const val FullProject =
        """{"id":"p-1","owner_user_id":"$OwnerId","details":{"title":"AI-навигация","type":"scientific","field":"Computer Science","problem":"GPS не работает","goal":"Маршруты внутри","tags":["ML","Robotics"],"planned_start":"2026-10-01","planned_end":"2027-03-31"},"status":"recruiting","revision":7,"roles":[$FullRole],"future_field":{"nested":[1]}}"""

    /** A `Project` with only the id and the owner. */
    fun project(id: String, owner: String = OwnerId, title: String = "Проект $id"): String =
        """{"id":"$id","owner_user_id":"$owner","details":{"title":"$title","field":"Биология"},"revision":1}"""

    /** A `ProjectList`; [total] is the JSON of the field (`"42"` with the quotes, `42`) or `null` for an answer without it. */
    fun list(total: String?, vararg projects: String): String {
        val items = """"items":[${projects.joinToString(",")}]"""
        return if (total == null) "{$items}" else "{$items,\"total\":$total}"
    }
}

/** A Gateway with one session open over a MockEngine, the way the data layers get it. */
internal class ProjectSessionFixture(handler: MockRequestHandler) {
    val fixture = GatewayFixture(handler = handler)

    /** The tokens the server refused (the 401 reports of the session). */
    val rejected = mutableListOf<String>()

    val session: GatewaySession = fixture.gateway.openSession(Token) { rejected += it }

    val requests: List<HttpRequestData> get() = fixture.requests

    companion object {
        const val Token = "project-session-token"
    }
}
