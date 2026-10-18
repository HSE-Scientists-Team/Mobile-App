package org.matchlab.academic_net.di

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.matchlab.academic_net.Backend
import org.matchlab.academic_net.feature.auth.domain.AuthState
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.gateway.Gateway
import org.matchlab.academic_net.securestore.InMemorySecureStore
import org.matchlab.academic_net.securestore.SecureStore
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.assertIs

// What the tests of the session graph share: a server that plays the Gateway over a MockEngine, and a process (the
// component of the app) built over it. A test changes only the answers it is about; everything else is the happy path.

internal const val ServerUserId = "4f9a4c95-6144-4ec8-89e8-3866207d7561"
internal const val StudentEmail = "student@hse.ru"
internal const val StudentPassword = "correct-horse-battery"

internal typealias Responder = suspend MockRequestHandleScope.(FakeServer.Arrival) -> HttpResponseData

/** The answers of the Gateway the sign-in and the session need, and the log of what reached it. */
@OptIn(ExperimentalAtomicApi::class)
internal class FakeServer(var userId: String = ServerUserId) {
    /** A request as the server saw it. */
    class Arrival(
        val method: String,
        val path: String,
        val authorization: List<String>,
        val body: String,
        /** The query as sent (`limit=20&offset=0`), without the `?`; empty when there is none. */
        val query: String = "",
    ) {
        val call: String get() = "$method $path"

        /** The names of the query parameters, each once. */
        val queryNames: Set<String> get() = if (query.isEmpty()) emptySet() else query.split('&').map { it.substringBefore('=') }.toSet()

        /** The values of the query parameter [name], in the order sent, as they were written (not decoded). */
        fun queryValues(name: String): List<String> =
            if (query.isEmpty()) emptyList() else query.split('&').filter { it.substringBefore('=') == name }.map { it.substringAfter('=', "") }
    }

    private val log = AtomicReference<List<Arrival>>(emptyList())
    private val made = AtomicInt(0)
    private val tokens = AtomicInt(0)

    /** Every request that reached the server, in the order it came, also those that were never answered. */
    val arrivals: List<Arrival> get() = log.load()

    val calls: List<String> get() = arrivals.map { it.call }

    /** How many Gateway clients [factory] made. */
    val clients: Int get() = made.load()

    var onRegister: Responder = { json("""{"status":"pending"}""", HttpStatusCode.Created) }
    var onConfirm: Responder = { respond("", HttpStatusCode.NoContent) }
    var onLogin: Responder = { json("""{"user_id":"$userId","access_token":"token-${tokens.addAndFetch(1)}"}""") }
    var onCurrentSession: Responder = { json("""{"user_id":"$userId"}""") }
    var onRevoke: Responder = { respond("", HttpStatusCode.NoContent) }
    var onOther: Responder = { json("""{"error":"not found"}""", HttpStatusCode.NotFound) }

    private val handler: MockRequestHandler = { request ->
        val arrival = Arrival(
            method = request.method.value,
            path = request.url.encodedPath,
            authorization = request.headers.getAll(HttpHeaders.Authorization).orEmpty(),
            body = request.body.toByteArray().decodeToString(),
            query = request.url.encodedQuery,
        )
        record(arrival)
        val responder: Responder = when (arrival.call) {
            "POST /api/v1/auth/register" -> onRegister
            "POST /api/v1/auth/email/confirm" -> onConfirm
            "POST /api/v1/auth/login" -> onLogin
            "GET /api/v1/sessions/current" -> onCurrentSession
            "DELETE /api/v1/sessions/current" -> onRevoke
            else -> onOther
        }
        this.responder(arrival)
    }

    private fun record(arrival: Arrival) {
        while (true) {
            val current = log.load()
            if (log.compareAndSet(current, current + arrival)) return
        }
    }

    /** The Gateway the process makes: over an engine of this server; counts the clients. */
    val factory: GatewayFactory = GatewayFactory { config ->
        made.fetchAndAdd(1)
        Gateway(config, MockEngine(handler))
    }

    fun requests(call: String): List<Arrival> = arrivals.filter { it.call == call }
}

internal fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

internal fun MockRequestHandleScope.jsonError(status: HttpStatusCode, message: String = "refused"): HttpResponseData =
    json("""{"error":"$message"}""", status)

/** A launch of the app (a process) over the server [server] and the secure store [store]: what one launch of the app builds. */
internal class AppLaunch(
    val server: FakeServer = FakeServer(),
    val store: SecureStore = InMemorySecureStore(),
) {
    val app: AppComponent = createAppComponent(store, Backend.Gateway(), server.factory)

    val state: AuthState get() = app.authRepository.authState.value

    /** The session of a process that is signed in. */
    val session: Session get() = assertIs<AuthState.SignedIn>(state).session

    /** Signs in by the typed address and password; the state follows. */
    suspend fun signIn(email: String = StudentEmail, password: String = StudentPassword) {
        app.auth.signIn(email, password)
    }

    /** The graph of the signed-in user, as the root builds it. */
    fun sessionGraph(session: Session = this.session): SessionComponent = createSessionComponent(app, session)
}

/** A store that remembers everything written to it, to show what reached the disk. */
internal class RecordingStore(private val inner: SecureStore = InMemorySecureStore()) : SecureStore by inner {
    val writes = mutableListOf<Pair<String, String>>()

    override fun write(key: String, value: String) {
        writes += key to value
        inner.write(key, value)
    }
}
