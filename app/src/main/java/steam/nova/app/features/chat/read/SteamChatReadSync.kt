package steam.nova.app.features.chat.read

import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Retries durable read intent using the existing CM connection, independently of the chat screen. */
internal class SteamChatReadSync(
    private val state: SteamChatReadState,
    private val validate: () -> Unit,
    private val send: (String, Long) -> Unit,
    private val verify: (Set<String>) -> Map<String, Long>,
) : AutoCloseable {
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "MillenniumChatReadSync").apply { isDaemon = true }
    }
    private var scheduled: ScheduledFuture<*>? = null
    private var attempts = 0
    @Volatile private var closed = false

    @Synchronized
    fun wake() {
        if (closed || state.pending().isEmpty()) return
        scheduled?.cancel(false)
        scheduled = worker.schedule(::flush, 150, TimeUnit.MILLISECONDS)
    }

    private fun flush() {
        synchronized(this) { scheduled = null }
        if (closed) return
        try {
            validate()
            val pending = state.pending()
            if (pending.isEmpty()) return
            SteamChatDiagnostics.info("read.sync.begin", "count=${pending.size}")
            pending.forEach { (key, timestamp) ->
                if (closed) return
                validate()
                // An echo can confirm a position while this batch is queued.
                if (state.pending().containsKey(key)) send(key, timestamp)
            }
            if (closed) return
            validate()
            // AckMessage has no RPC response and need not echo to the sending connection.
            // Query the actual server cursors instead of treating socket acceptance as confirmation.
            val confirmed = verify(pending.keys)
            if (closed) return
            validate()
            confirmed.forEach { (key, timestamp) -> if (timestamp > 0) state.mark(key, timestamp, confirmed = true) }
            SteamChatDiagnostics.info("read.sync.checked", "remaining=${state.pending().size}")
        } catch (error: Exception) {
            if (!closed) SteamChatDiagnostics.failure("read.sync.failed", error)
        } finally {
            synchronized(this) {
                if (!closed && state.pending().isNotEmpty()) {
                    val delay = RETRY_SECONDS[attempts.coerceAtMost(RETRY_SECONDS.lastIndex)]
                    attempts = (attempts + 1).coerceAtMost(RETRY_SECONDS.lastIndex)
                    if (scheduled == null) scheduled = worker.schedule(::flush, delay, TimeUnit.SECONDS)
                } else attempts = 0
            }
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        scheduled?.cancel(false)
        scheduled = null
        worker.shutdownNow()
    }

    companion object {
        private val RETRY_SECONDS = longArrayOf(2, 5, 15, 30, 60)
    }
}
