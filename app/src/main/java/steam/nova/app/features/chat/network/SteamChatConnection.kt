package steam.nova.app.features.chat.network

import steam.nova.app.features.chat.protocol.SteamChatProtocol as Proto
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import steam.nova.app.features.chat.presence.SteamChatLocalPresence
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** One account-scoped CM socket. All blocking waits run on the bridge's IO executor. */
internal class SteamChatConnection(
    val account: SteamChatAccount,
    private val onEvent: (Proto.Packet) -> Unit,
    private val onState: (Boolean) -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private val jobs = ConcurrentHashMap<Long, CompletableFuture<Proto.Packet>>()
    private val packets = ConcurrentHashMap<Int, CompletableFuture<Proto.Packet>>()
    private val nextJob = AtomicLong(1)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var session = 0
    @Volatile private var closed = false
    private var connecting: CompletableFuture<Unit>? = null
    private var heartbeat: ScheduledFuture<*>? = null
    private val presence = SteamChatLocalPresence()

    fun ensureConnected() {
        var owner = false
        val waiter = synchronized(lock) {
            check(!closed)
            if (session != 0 && socket != null) return
            connecting ?: CompletableFuture<Unit>().also { connecting = it; owner = true }
        }
        if (owner) {
            try {
                SteamChatDiagnostics.info("cm.connect.begin")
                val token = SteamChatHttp.chatToken(account)
                val endpoints = SteamChatHttp.servers()
                SteamChatDiagnostics.info("cm.directory.ready", "count=${endpoints.size}")
                var failure: Throwable = SteamChatFailure("NETWORK_ERROR")
                for ((index, endpoint) in endpoints.withIndex()) {
                    check(!closed)
                    val attempt = CompletableFuture<Unit>()
                    val listener = Listener(token, attempt, index + 1)
                    SteamChatDiagnostics.info("cm.socket.begin", "attempt=${index + 1}")
                    val opened = synchronized(lock) {
                        check(!closed)
                        client.newWebSocket(Request.Builder().url("wss://$endpoint/cmsocket/")
                            .header("Origin", "https://steamcommunity.com").build(), listener)
                            .also { socket = it }
                    }
                    try {
                        attempt.get(12, TimeUnit.SECONDS)
                        SteamChatDiagnostics.info("cm.connect.ready", "attempt=${index + 1}")
                        waiter.complete(Unit)
                        return
                    } catch (error: Exception) {
                        SteamChatDiagnostics.failure("cm.socket.attempt${index + 1}.failed", error)
                        failure = error.cause ?: error
                        synchronized(lock) { if (socket === opened) { socket = null; session = 0 } }
                        opened.cancel()
                        if (failure is SteamChatFailure && failure.code == "AUTH_EXPIRED") break
                    }
                }
                throw failure
            } catch (error: Throwable) {
                SteamChatDiagnostics.failure("cm.connect.failed", error)
                waiter.completeExceptionally(error)
            } finally {
                synchronized(lock) { if (connecting === waiter) connecting = null }
            }
        }
        try { waiter.get(55, TimeUnit.SECONDS) }
        catch (error: ExecutionException) { throw error.cause ?: error }
    }

    fun call(method: String, body: ByteArray): Proto.Fields = SteamChatDiagnostics.trace("cm.rpc.${SteamChatDiagnostics.operation(method)}") {
        ensureConnected()
        val job = nextJob.getAndIncrement()
        val future = CompletableFuture<Proto.Packet>()
        check(jobs.size < 64)
        jobs[job] = future
        try {
            send(151, body, job, method)
            val response = future.get(18, TimeUnit.SECONDS)
            val result = response.header.number(13, 1)
            val transport = response.header.number(17, 1)
            SteamChatDiagnostics.info("cm.rpc.${SteamChatDiagnostics.operation(method)}.result", "result=$result transport=$transport")
            if (result != 1L || transport != 1L) {
                throw SteamChatFailure(if (result == 84L || result == 25L) "RATE_LIMITED" else "REQUEST_REJECTED")
            }
            Proto.read(response.body)
        } catch (error: ExecutionException) { throw error.cause ?: error }
        finally { jobs.remove(job) }
    }

    fun notify(method: String, body: ByteArray) {
        ensureConnected()
        // AckMessage returns NoResponse; waiting for a response here would create a false timeout.
        send(151, body, method = method)
    }

    /** Nonblocking activity update; background refresh must not announce an active user. */
    fun foreground(value: Boolean) {
        synchronized(lock) {
            if (!presence.foreground(value) || closed || session == 0 || socket == null) return
            runCatching { send(716, presence.packet()) }
                .onFailure { SteamChatDiagnostics.failure("presence.publish", it) }
        }
    }

    fun requestFriendData(ids: List<String>) {
        ensureConnected()
        val body = Proto.Writer().number(1, 339)
        ids.forEach { body.fixed64(2, it.toLong()) }
        send(815, body.build())
    }

    fun catalog(): Proto.Fields {
        ensureConnected()
        val future = CompletableFuture<Proto.Packet>()
        val existing = packets.putIfAbsent(9331, future)
        if (existing != null) return Proto.read(existing.get(18, TimeUnit.SECONDS).body)
        try {
            send(9330, byteArrayOf())
            return Proto.read(future.get(18, TimeUnit.SECONDS).body)
        }
        finally { packets.remove(9331, future) }
    }

    private fun send(type: Int, body: ByteArray, job: Long = -1, method: String? = null) {
        val active = socket ?: throw SteamChatFailure("OFFLINE")
        if (!active.send(Proto.packet(type, account.id.toLong(), session, body, job, method).toByteString())) {
            throw SteamChatFailure("OFFLINE")
        }
    }

    private inner class Listener(private val token: String, private val ready: CompletableFuture<Unit>, private val attempt: Int) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) {
                if (closed || socket !== webSocket) { webSocket.cancel(); return }
                SteamChatDiagnostics.info("cm.socket.open", "attempt=$attempt status=${response.code}")
                val sent = webSocket.send(Proto.packet(5514, account.id.toLong(), 0, Proto.logon(token)).toByteString())
                SteamChatDiagnostics.info("cm.logon.sent", "attempt=$attempt accepted=$sent")
            }
        }
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (closed || socket !== webSocket) return
            runCatching {
                Proto.decode(bytes.toByteArray()).forEach { packet ->
                    when (packet.type) {
                        751 -> {
                            val fields = Proto.read(packet.body)
                            SteamChatDiagnostics.info("cm.logon.result", "attempt=$attempt result=${fields.number(1)} sessionPresent=${packet.header.number(2) != 0L}")
                            if (fields.number(1) != 1L) throw SteamChatFailure("AUTH_EXPIRED")
                            synchronized(lock) {
                                if (closed || socket !== webSocket) return
                                session = packet.header.number(2).toInt()
                                check(session != 0)
                                heartbeat?.cancel(false)
                                val seconds = fields.number(3, fields.number(2, 30)).coerceIn(5, 120)
                                heartbeat = clock.scheduleWithFixedDelay({
                                    if (socket === webSocket && !closed) runCatching { send(703, byteArrayOf()) }
                                }, seconds, seconds, TimeUnit.SECONDS)
                            }
                            runCatching { onState(true) }
                            synchronized(lock) {
                                if (closed || socket !== webSocket) return
                                send(716, presence.packet())
                            }
                            ready.complete(Unit)
                        }
                        757 -> throw SteamChatFailure("OFFLINE")
                        else -> {
                            if (packet.type == 766) synchronized(lock) {
                                presence.receive(account.id, Proto.read(packet.body))
                            }
                            val job = packet.header.number(11, -1)
                            val pending = jobs.remove(job) ?: packets.remove(packet.type)
                            if (pending != null) pending.complete(packet)
                            else runCatching { onEvent(packet) }
                        }
                    }
                }
            }.onFailure { fail(webSocket, it) }
        }
        override fun onMessage(webSocket: WebSocket, text: String) = fail(webSocket, SteamChatFailure("NETWORK_ERROR"))
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            SteamChatDiagnostics.info("cm.socket.failureStatus", "attempt=$attempt status=${response?.code ?: -1}")
            fail(webSocket, t)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = fail(webSocket, SteamChatFailure("OFFLINE"))
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = fail(webSocket, SteamChatFailure("OFFLINE"))
        private fun fail(webSocket: WebSocket, error: Throwable) {
            SteamChatDiagnostics.failure("cm.socket.listener$attempt.failed", error)
            ready.completeExceptionally(error)
            synchronized(lock) {
                if (socket !== webSocket) return
                socket = null; session = 0
                heartbeat?.cancel(false); heartbeat = null
            }
            jobs.values.forEach { it.completeExceptionally(SteamChatFailure("OFFLINE")) }; jobs.clear()
            packets.values.forEach { it.completeExceptionally(SteamChatFailure("OFFLINE")) }; packets.clear()
            webSocket.cancel()
            if (!closed) runCatching { onState(false) }
        }
    }

    override fun close() {
        SteamChatDiagnostics.info("cm.close")
        synchronized(lock) {
            closed = true
            heartbeat?.cancel(false); heartbeat = null
            socket?.cancel(); socket = null; session = 0
            connecting?.completeExceptionally(SteamChatFailure("ACCOUNT_CHANGED")); connecting = null
        }
        jobs.values.forEach { it.completeExceptionally(SteamChatFailure("ACCOUNT_CHANGED")) }; jobs.clear()
        packets.values.forEach { it.completeExceptionally(SteamChatFailure("ACCOUNT_CHANGED")) }; packets.clear()
    }

    companion object {
        private val clock = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "MillenniumChatClock").apply { isDaemon = true } }
        private val client: OkHttpClient = SteamChatHttp.client.newBuilder().readTimeout(0, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS).pingInterval(25, TimeUnit.SECONDS).build()
    }
}
