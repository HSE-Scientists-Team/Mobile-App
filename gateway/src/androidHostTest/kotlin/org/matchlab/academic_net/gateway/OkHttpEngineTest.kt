package org.matchlab.academic_net.gateway

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttpEngine
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.ConfirmEmailRequestDto
import org.matchlab.academic_net.gateway.dto.LoginResultDto
import org.matchlab.academic_net.gateway.dto.UserIdDto
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The same calls over the real engine of Android, OkHttp, against a server on the loopback interface of this
 * machine. The tests with MockEngine cannot show what OkHttp throws for a refused connection or a silent
 * server; these can. Nothing leaves the machine.
 */
class OkHttpEngineTest {
    private sealed interface Reply {
        class Http(val status: String, val body: String) : Reply
        data object Silent : Reply
        data object HangUp : Reply
    }

    private class Received(val line: String, val headers: Map<String, String>, val body: String)

    /** Speaks just enough HTTP/1.1 to be a server for one call per connection. */
    private class LoopbackServer(private val reply: (Received) -> Reply) : AutoCloseable {
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val clients = CopyOnWriteArrayList<Socket>()
        val received = CopyOnWriteArrayList<Received>()
        val port: Int get() = server.localPort

        init {
            thread(isDaemon = true, name = "loopback-accept") {
                while (true) {
                    val client = try {
                        server.accept()
                    } catch (_: IOException) {
                        return@thread
                    }
                    clients += client
                    thread(isDaemon = true, name = "loopback-client") { serve(client) }
                }
            }
        }

        private fun serve(client: Socket) {
            try {
                val input = client.getInputStream()
                val line = readLine(input) ?: return
                val headers = HashMap<String, String>()
                while (true) {
                    val header = readLine(input) ?: return
                    if (header.isEmpty()) break
                    headers[header.substringBefore(':').lowercase()] = header.substringAfter(':').trim()
                }
                val length = headers["content-length"]?.toInt() ?: 0
                val request = Received(line, headers, String(input.readNBytes(length), Charsets.UTF_8))
                received += request

                when (val answer = reply(request)) {
                    is Reply.Http -> {
                        val body = answer.body.toByteArray(Charsets.UTF_8)
                        val head = "HTTP/1.1 ${answer.status}\r\nContent-Type: application/json\r\n" +
                            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                        client.getOutputStream().apply {
                            write(head.toByteArray(Charsets.UTF_8) + body)
                            flush()
                        }
                        client.close()
                    }
                    Reply.HangUp -> client.close()
                    Reply.Silent -> Unit // the socket stays open until the server is closed
                }
            } catch (_: IOException) {
                // The client went away.
            }
        }

        private fun readLine(input: InputStream): String? {
            val line = StringBuilder()
            while (true) {
                val next = input.read()
                if (next < 0) return if (line.isEmpty()) null else line.toString()
                if (next == '\n'.code) return line.toString().trimEnd('\r')
                line.append(next.toChar())
            }
        }

        override fun close() {
            clients.forEach { runCatching { it.close() } }
            server.close()
        }
    }

    /**
     * A server that keeps its connection open after the first answer (HTTP/1.1 keep-alive) and from then on takes every
     * request it gets and hangs up without an answer. To a client that reuses the connection from its pool, this is
     * what a connection that died while it was idle looks like: the request goes out, and the answer never comes.
     */
    private class DroppingServer : AutoCloseable {
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val clients = CopyOnWriteArrayList<Socket>()
        private val answeredOnce = AtomicBoolean(false)

        /** The request line of every request that arrived, in the order of arrival (kept before the hang-up). */
        val requests = CopyOnWriteArrayList<String>()
        val port: Int get() = server.localPort

        init {
            thread(isDaemon = true, name = "dropping-accept") {
                while (true) {
                    val client = try {
                        server.accept()
                    } catch (_: IOException) {
                        return@thread
                    }
                    clients += client
                    thread(isDaemon = true, name = "dropping-client") { serve(client) }
                }
            }
        }

        private fun serve(client: Socket) {
            try {
                val input = client.getInputStream()
                while (true) {
                    val line = readLine(input) ?: return
                    var length = 0
                    while (true) {
                        val header = readLine(input) ?: return
                        if (header.isEmpty()) break
                        if (header.substringBefore(':').equals("content-length", ignoreCase = true)) {
                            length = header.substringAfter(':').trim().toInt()
                        }
                    }
                    input.readNBytes(length)
                    requests += line
                    if (!answeredOnce.compareAndSet(false, true)) {
                        client.close() // the request is taken and the connection dropped, no answer
                        return
                    }
                    val body = Spec.UserIdAnswer.toByteArray(Charsets.UTF_8)
                    val head = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\n\r\n"
                    client.getOutputStream().apply {
                        write(head.toByteArray(Charsets.UTF_8) + body)
                        flush()
                    }
                }
            } catch (_: IOException) {
                // The client went away.
            }
        }

        private fun readLine(input: InputStream): String? {
            val line = StringBuilder()
            while (true) {
                val next = input.read()
                if (next < 0) return if (line.isEmpty()) null else line.toString()
                if (next == '\n'.code) return line.toString().trimEnd('\r')
                line.append(next.toChar())
            }
        }

        override fun close() {
            clients.forEach { runCatching { it.close() } }
            server.close()
        }
    }

    private fun json(body: String, status: String = "200 OK") = Reply.Http(status, body)

    /** Runs [block] with a Gateway over the real engine, pointed at [port] of this machine. */
    private suspend fun withRealGateway(
        port: Int,
        config: GatewayConfig = GatewayConfig(),
        block: suspend (Gateway) -> Unit,
    ) {
        val real = config.copy(baseUrl = "http://127.0.0.1:$port")
        val engine: HttpClientEngine = platformHttpEngine(real)
        try {
            block(Gateway(real, engine))
        } finally {
            engine.close()
        }
    }

    @Test
    fun theEngineOfAndroidIsOkHttp() {
        val engine = platformHttpEngine(GatewayConfig())
        try {
            assertIs<OkHttpEngine>(engine)
        } finally {
            engine.close()
        }
    }

    @Test
    fun withoutAnEngineTheGatewayUsesTheOneOfThePlatform() = runTest {
        LoopbackServer { json(Spec.LoginAnswer) }.use { server ->
            // The engine made here is the Gateway's to keep: it is never closed, like the one of the app.
            val gateway = Gateway(GatewayConfig(baseUrl = "http://127.0.0.1:${server.port}"))

            assertEquals(Spec.UserId, gateway.account.login(Spec.credentials).userId)

            assertEquals("POST /api/v1/auth/login HTTP/1.1", server.received.single().line)
        }
    }

    @Test
    fun aLoginGoesOutAsAnHttpRequestAndTheAnswerIsRead() = runTest {
        LoopbackServer { json(Spec.LoginAnswer) }.use { server ->
            withRealGateway(server.port) { gateway ->
                val result = gateway.account.login(Spec.credentials)

                assertEquals(LoginResultDto(Spec.UserId, Spec.AccessToken), result)
            }

            val request = server.received.single()
            assertEquals("POST /api/v1/auth/login HTTP/1.1", request.line)
            assertEquals("""{"email":"student@hse.ru","password":"example-password-123"}""", request.body)
            assertTrue(request.headers.getValue("content-type").startsWith("application/json"))
            assertEquals("application/json", request.headers["accept"])
            assertNull(request.headers["authorization"], "login must not carry a token")
        }
    }

    @Test
    fun aSessionSendsItsTokenAndTheCheckSendsTheGivenOne() = runTest {
        LoopbackServer { json(Spec.UserIdAnswer) }.use { server ->
            withRealGateway(server.port) { gateway ->
                val session = gateway.openSession("tok-1") { }
                session.requests.get<UserIdDto>("probe")
                gateway.account.currentSession("tok-2")
                session.close()
            }

            assertEquals(
                listOf("Bearer tok-1", "Bearer tok-2"),
                server.received.map { it.headers.getValue("authorization") },
            )
            assertEquals(listOf("GET /api/v1/probe HTTP/1.1", "GET /api/v1/sessions/current HTTP/1.1"),
                server.received.map { it.line })
        }
    }

    @Test
    fun aSignOutIsADeleteWithTheTokenAndNoBody() = runTest {
        LoopbackServer { Reply.Http("204 No Content", "") }.use { server ->
            withRealGateway(server.port) { gateway ->
                gateway.account.revokeSession("tok-1")
            }

            val request = server.received.single()
            assertEquals("DELETE /api/v1/sessions/current HTTP/1.1", request.line)
            assertEquals("Bearer tok-1", request.headers["authorization"])
            assertEquals("", request.body)
        }
    }

    @Test
    fun aConfirmationIsAPostWithTheTokenAndNoAuthorization() = runTest {
        LoopbackServer { Reply.Http("204 No Content", "") }.use { server ->
            withRealGateway(server.port) { gateway ->
                gateway.account.confirmEmail(ConfirmEmailRequestDto(Spec.LetterToken))
            }

            val request = server.received.single()
            assertEquals("POST /api/v1/auth/email/confirm HTTP/1.1", request.line)
            assertEquals("""{"token":"ТОКЕН_ИЗ_ПИСЬМА"}""", request.body)
            assertNull(request.headers["authorization"])
        }
    }

    @Test
    fun anErrorAnswerKeepsItsStatusAndText() = runTest {
        val unauthorized = json("""{"error":"неверный email или пароль"}""", "401 Unauthorized")
        LoopbackServer { unauthorized }.use { server ->
            withRealGateway(server.port) { gateway ->
                val failure = failureOf<RemoteFailure.Http> { gateway.account.login(Spec.credentials) }

                assertEquals(401, failure.status)
                assertEquals("неверный email или пароль", failure.serverMessage)
            }
        }
    }

    @Test
    fun aRefusedConnectionIsNoConnection() = runTest {
        val closedPort = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

        withRealGateway(closedPort) { gateway ->
            val failure = failureOf<RemoteFailure.NoConnection> { gateway.account.login(Spec.credentials) }

            assertIs<IOException>(failure.cause)
        }
    }

    @Test
    fun aServerThatHangsUpIsNoConnection() = runTest {
        LoopbackServer { Reply.HangUp }.use { server ->
            withRealGateway(server.port) { gateway ->
                failureOf<RemoteFailure.NoConnection> { gateway.account.login(Spec.credentials) }
            }
        }
    }

    @Test
    fun aServerThatNeverAnswersIsATimeout() = runTest {
        LoopbackServer { Reply.Silent }.use { server ->
            withRealGateway(server.port, GatewayConfig(requestTimeout = 400.milliseconds)) { gateway ->
                val failure = failureOf<RemoteFailure.Timeout> { gateway.account.login(Spec.credentials) }

                val cause = failure.cause
                assertTrue(
                    cause is HttpRequestTimeoutException || cause is SocketTimeoutException,
                    "the timeout comes from Ktor or from OkHttp, was $cause",
                )
            }
        }
    }

    @Test
    fun infiniteTimeoutsDoNotBreakTheEngine() = runTest {
        LoopbackServer { json(Spec.LoginAnswer) }.use { server ->
            val config = GatewayConfig(connectTimeout = Duration.INFINITE, requestTimeout = Duration.INFINITE)
            withRealGateway(server.port, config) { gateway ->
                assertEquals(Spec.UserId, gateway.account.login(Spec.credentials).userId)
            }
        }
    }

    @Test
    fun hugeTimeoutsDoNotBreakTheEngine() = runTest {
        LoopbackServer { json(Spec.LoginAnswer) }.use { server ->
            val config = GatewayConfig(connectTimeout = 10_000.seconds * 1_000, requestTimeout = 10_000.seconds * 1_000)
            withRealGateway(server.port, config) { gateway ->
                assertEquals(Spec.UserId, gateway.account.login(Spec.credentials).userId)
            }
        }
    }

    @Test
    fun closingASessionDoesNotCloseTheOkHttpEngine() = runTest {
        LoopbackServer { json(Spec.UserIdAnswer) }.use { server ->
            withRealGateway(server.port) { gateway ->
                gateway.openSession("tok-1") { }.close()

                assertEquals(Spec.UserId, gateway.account.currentSession("tok-1").userId)
            }
        }
    }

    // ---- a POST is never sent twice behind the back of the caller (API-GAP G48) ----

    @Test
    fun aPostOnAKeptConnectionThatTheServerDropsIsSentOnceAndNotAgainBehindTheBack() = runTest {
        DroppingServer().use { server ->
            withRealGateway(server.port) { gateway ->
                // The first call leaves its connection in the pool of the engine.
                gateway.account.currentSession("tok-1")

                // The login goes out on that connection; the server takes it and hangs up. OkHttp, left to itself, would
                // send it again on a new connection, and a second sign-in (a second session) would be made.
                failureOf<RemoteFailure.NoConnection> { gateway.account.login(Spec.credentials) }
            }

            assertEquals(
                listOf("GET /api/v1/sessions/current HTTP/1.1", "POST /api/v1/auth/login HTTP/1.1"),
                server.requests,
                "the POST arrived once",
            )
        }
    }

    @Test
    fun aPostToAServerThatHangsUpOnTheNewConnectionIsSentOnce() = runTest {
        LoopbackServer { Reply.HangUp }.use { server ->
            withRealGateway(server.port) { gateway ->
                failureOf<RemoteFailure.NoConnection> { gateway.account.login(Spec.credentials) }
            }

            assertEquals(listOf("POST /api/v1/auth/login HTTP/1.1"), server.received.map { it.line })
        }
    }

    @Test
    fun theRetryOfTheUserIsAnotherRequestAndGoesOut() = runTest {
        // The refusal to repeat is the engine's; a second try of the user (the button "Повторить") is a new call.
        val attempts = AtomicInteger()
        LoopbackServer { if (attempts.incrementAndGet() == 1) Reply.HangUp else json(Spec.LoginAnswer) }.use { server ->
            withRealGateway(server.port) { gateway ->
                failureOf<RemoteFailure.NoConnection> { gateway.account.login(Spec.credentials) }

                assertEquals(Spec.UserId, gateway.account.login(Spec.credentials).userId)
            }

            assertEquals(2, server.received.size)
        }
    }
}
