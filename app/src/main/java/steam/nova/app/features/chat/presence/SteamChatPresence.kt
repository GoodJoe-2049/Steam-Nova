package steam.nova.app.features.chat.presence

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import steam.nova.app.features.chat.protocol.SteamChatProtocol as P
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Account-scoped, memory-only presence. Disk profiles are never an online-status authority. */
internal class SteamChatPresence(
    private val load: (List<String>) -> P.Fields,
    private val publish: (JSONObject) -> Unit,
) : AutoCloseable {
    private data class Entry(
        var revision: Long = 0,
        var statusRevision: Long = 0,
        var flagsRevision: Long = 0,
        var deviceRevision: Long = 0,
        var state: Int? = null,
        var flags: Long? = null,
        var device: Int? = null,
        var deck: Boolean? = null,
        var game: String = "",
    )
    private data class Ticket(val epoch: Long, val revision: Long)
    private val lock = Any()
    private val entries = mutableMapOf<String, Entry>()
    private val queried = mutableMapOf<String, Long>()
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "SteamNovaPresence").apply { isDaemon = true }
    }
    private var epoch = epochs.incrementAndGet()
    private var revision = 0L
    private var closed = false

    fun generation(): Long = synchronized(lock) { epoch }

    fun reset(): Long = synchronized(lock) {
        epoch = epochs.incrementAndGet()
        entries.clear(); queried.clear()
        epoch
    }

    fun snapshot(): JSONObject = synchronized(lock) {
        JSONObject().put("epoch", epoch).put("values", JSONArray(entries.map { (id, entry) -> json(id, entry) }))
    }

    fun profile(id: String): JSONObject = synchronized(lock) { json(id, entries[id] ?: Entry()) }

    /** HTTP supplies a first status while the asynchronous CM snapshot is in flight. */
    fun seed(player: JSONObject, expectedEpoch: Long) = synchronized(lock) {
        val id = player.optString("steamid")
        if (closed || epoch != expectedEpoch || !id.matches(STEAM_ID) || !player.has("personastate")) return@synchronized
        val entry = entries.getOrPut(id) { Entry() }
        if (entry.state != null) return@synchronized
        entry.state = player.optInt("personastate")
        entry.game = player.optString("gameextrainfo")
        entry.revision = ++revision
        entry.statusRevision = entry.revision
    }

    fun receive(fields: P.Fields) = synchronized(lock) {
        if (closed) return@synchronized
        val status = fields.number(1) and 1L != 0L
        val changed = JSONArray()
        fields.messages(2).forEach { friend ->
            val id = friend.number(1).toString()
            if (!id.matches(STEAM_ID)) return@forEach
            if (!status && !friend.has(6) && !friend.has(81) && !friend.has(83)) return@forEach
            val entry = entries.getOrPut(id) { Entry() }
            entry.revision = ++revision
            // status_flags advertises a full status section; other sections must not reset it.
            if (status) {
                entry.state = friend.number(2).toInt()
                entry.game = friend.text(55)
                entry.statusRevision = entry.revision
            }
            if (friend.has(6)) { entry.flags = friend.number(6); entry.flagsRevision = entry.revision }
            if (friend.has(81)) { entry.deck = friend.number(81) != 0L; entry.deviceRevision = entry.revision }
            if (friend.has(83)) { entry.device = friend.number(83).toInt(); entry.deviceRevision = entry.revision }
            changed.put(json(id, entry))
        }
        if (changed.length() > 0) publish(JSONObject().put("type", "presence").put("epoch", epoch).put("values", changed))
    }

    /** Batch and throttle snapshots; never block chat history or the CM receive thread. */
    fun refresh(ids: List<String>) {
        synchronized(lock) {
            if (closed) return
            val now = SystemClock.elapsedRealtime()
            val batch = ids.distinct().filter { it.matches(STEAM_ID) && now - (queried[it] ?: -60_000L) >= 30_000L }
            if (batch.isEmpty()) return
            val tickets = batch.associateWith { id ->
                queried[id] = now
                Ticket(epoch, entries[id]?.revision ?: 0)
            }
            worker.execute {
                batch.chunked(100).forEach { part ->
                    if (synchronized(lock) { closed || tickets.getValue(part.first()).epoch != epoch }) return@forEach
                    val response = runCatching { load(part) }
                        .onFailure { if (!synchronized(lock) { closed }) SteamChatDiagnostics.failure("presence.snapshot", it) }
                        .getOrNull() ?: return@forEach
                    synchronized(lock) responseLock@{
                        if (closed) return@responseLock
                        val changed = JSONArray()
                        response.messages(1).forEach account@{ account ->
                            val publicData = account.messages(1).firstOrNull() ?: return@account
                            val id = publicData.number(1).toString()
                            val ticket = tickets[id] ?: return@account
                            val privateData = account.messages(2).firstOrNull() ?: return@account
                            if (ticket.epoch != epoch) return@account
                            val entry = entries.getOrPut(id) { Entry() }
                            // A newer live field wins; an unrelated avatar/status delta cannot discard flags.
                            // Unlike a CM delta, private_data is a complete snapshot with proto defaults.
                            if (entry.statusRevision <= ticket.revision) {
                                entry.state = privateData.number(1).toInt()
                                entry.game = privateData.text(8)
                            }
                            if (entry.flagsRevision <= ticket.revision) entry.flags = privateData.number(2)
                            if (entry.deviceRevision <= ticket.revision) {
                                entry.device = privateData.number(20).toInt()
                                entry.deck = entry.device == 544
                            }
                            entry.revision = ++revision
                            changed.put(json(id, entry))
                        }
                        if (changed.length() > 0) publish(JSONObject().put("type", "presence").put("epoch", epoch).put("values", changed))
                    }
                }
            }
        }
    }

    private fun json(id: String, entry: Entry): JSONObject = JSONObject().put("id", id)
        .put("epoch", epoch).put("revision", entry.revision).put("known", entry.state != null)
        .put("personaState", entry.state ?: 0).put("online", (entry.state ?: 0) in 1..6)
        .put("game", if ((entry.state ?: 0) in 1..6) entry.game else "")
        .put("flags", entry.flags ?: JSONObject.NULL).put("device", entry.device ?: JSONObject.NULL)
        .put("deck", entry.deck ?: false)

    override fun close() {
        synchronized(lock) { closed = true; entries.clear(); queried.clear() }
        worker.shutdownNow()
    }

    companion object {
        private val epochs = AtomicLong(System.currentTimeMillis())
        private val STEAM_ID = Regex("7656119\\d{10}")
    }
}
