package net.s4v3.gateway

import reactor.core.publisher.Mono
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer
import java.util.concurrent.CopyOnWriteArrayList

/** What a stub downstream service received. Header names are lowercase. */
data class Received(
    val method: String,
    val uri: String,
    val headers: Map<String, List<String>>,
    val body: String,
) {
    val path: String get() = uri.substringBefore('?')
    val query: String? get() = uri.substringAfter('?', "").ifEmpty { null }
    fun header(name: String): String? = headers[name.lowercase()]?.firstOrNull()
    fun headerValues(name: String): List<String> = headers[name.lowercase()].orEmpty()
}

data class Reply(
    val status: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val delayMillis: Long = 0,
)

/** A stand-in for `backend` or `auth`: a Reactor Netty server on a random port that records what it receives. */
class StubService(private val name: String) {
    val received = CopyOnWriteArrayList<Received>()

    @Volatile
    var handler: (Received) -> Reply = { Reply(body = """{"stub":"$name"}""") }

    private val connections = java.util.concurrent.ConcurrentLinkedQueue<reactor.netty.Connection>()

    private var server: DisposableServer = start(0)

    val port: Int get() = server.port()
    val uri: String get() = "http://localhost:$port"

    private fun start(port: Int): DisposableServer =
        HttpServer.create().host("localhost").port(port)
            // remember accepted connections so that stop() really cuts them, as a replaced pod would
            .doOnConnection { connection -> connections += connection }
            .handle { request, response ->
                request.receive().aggregate().asString().defaultIfEmpty("").flatMap { body ->
                    val headers = request.requestHeaders().names().associate { it.lowercase() to request.requestHeaders().getAll(it) }
                    val call = Received(request.method().name(), request.uri(), headers, body)
                    received += call
                    val reply = handler(call)
                    reply.headers.forEach { (k, v) -> response.addHeader(k, v) }
                    val mono = response.status(reply.status).sendString(Mono.just(reply.body)).then()
                    if (reply.delayMillis > 0) {
                        Mono.delay(java.time.Duration.ofMillis(reply.delayMillis)).then(mono)
                    } else {
                        mono
                    }
                }
            }
            .bindNow()

    fun reset() {
        received.clear()
        handler = { Reply(body = """{"stub":"$name"}""") }
    }

    /** Stops listening; the port stays reserved for [restart]. */
    fun stop() {
        server.disposeNow()
        connections.forEach { it.disposeNow() }
        connections.clear()
    }

    /** Starts again on the same port (a replaced pod at the same address). */
    fun restart() {
        if (!server.isDisposed) return
        val previous = port
        server = start(previous)
    }

    fun close() = stop()
}

/** Shared stubs, so one gateway context serves all routing tests. */
object Stubs {
    val backend = StubService("backend")
    val auth = StubService("auth")

    init {
        Runtime.getRuntime().addShutdownHook(Thread { backend.close(); auth.close() })
    }

    fun reset() {
        backend.reset()
        auth.reset()
    }
}
