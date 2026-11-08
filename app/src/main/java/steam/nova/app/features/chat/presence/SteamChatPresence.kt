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
    private val load: (List<String>, Boolean) -> P.Fields,
    private val loadGames: (List<Long>) -> P.Fields,
    loadRichPresence: (Long, String) -> P.Fields,
    private val publish: (JSONObject) -> Unit,
) : AutoCloseable {
    private data class Entry(
        var revision: Long = 0,
        var statusRevision: Long = 0,
        var gameRevision: Long = 0,
        var flagsRevision: Long = 0,
        var deviceRevision: Long = 0,
        var state: Int? = null,
        var flags: Long? = null,
        var device: Int? = null,
        var deck: Boolean? = null,
        var game: String = "",
        var gameId: Long = 0,
        var appId: Long = 0,
        var richPresence: Map<String, String> = emptyMap(),
    )
    data class Stamp(val epoch: Long, val revision: Long)
    private val lock = Any()
    private val entries = mutableMapOf<String, Entry>()
    private val queried = mutableMapOf<String, Long>()
    private val pending = mutableSetOf<String>()
    private val gameNames = mutableMapOf<Long, String>()
    private val gamesQueried = mutableMapOf<Long, Long>()
    private val richPresence = SteamChatRichPresence(loadRichPresence)
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "SteamNovaPresence").apply { isDaemon = true }
    }
    private var epoch = epochs.incrementAndGet()
    private var revision = 0L
    private var closed = false

    fun generation(): Long = synchronized(lock) { epoch }
    fun stamp(): Stamp = synchronized(lock) { Stamp(epoch, revision) }

    fun reset(): Long = synchronized(lock) {
        epoch = epochs.incrementAndGet()
        entries.clear(); queried.clear(); pending.clear(); gameNames.clear(); gamesQueried.clear()
        richPresence.reset()
        epoch
    }

    fun snapshot(): JSONObject = synchronized(lock) {
        JSONObject().put("epoch", epoch).put("values", JSONArray(entries.map { (id, entry) -> json(id, entry) }))
    }

    fun profile(id: String): JSONObject = synchronized(lock) { json(id, entries[id] ?: Entry()) }

    /** Fresh HTTP summaries can repair cached state, but never overwrite a later CM update. */
    fun mergeProfile(player: JSONObject, started: Stamp) = synchronized(lock) {
        val id = player.optString("steamid")
        if (closed || epoch != started.epoch || !id.matches(STEAM_ID) || !player.has("personastate")) return@synchronized
        val state = player.optInt("personastate", -1)
        if (state !in 0..7) return@synchronized
        val entry = entries.getOrPut(id) { Entry() }
        if (entry.statusRevision > started.revision) return@synchronized
        entry.revision = ++revision
        entry.state = state
        entry.statusRevision = entry.revision
        if (entry.gameRevision <= started.revision) {
            val gameId = player.optString("gameid").toULongOrNull()?.toLong() ?: 0L
            updateGame(entry, gameId, appId(gameId), player.optString("gameextrainfo"))
        }
        publishValues(JSONArray().put(json(id, entry)))
        resolveGameNames()
        resolveRichPresence()
    }

    fun receive(fields: P.Fields) = synchronized(lock) {
        if (closed) return@synchronized
        val mask = fields.number(1)
        val status = mask and 1L != 0L
        val hasRichPresence = mask and 4096L != 0L
        val changed = JSONArray()
        fields.messages(2).forEach { friend ->
            val id = friend.number(1).toString()
            if (!id.matches(STEAM_ID)) return@forEach
            // RequestFriendData(815) can advertise Status while returning only name/avatar fields.
            // Normal Chat.RequestFriendPersonaStates offline updates explicitly contain state=0.
            val hasStatus = status && friend.has(2) && friend.number(2) in 0L..7L
            if (status && !friend.has(2)) SteamChatDiagnostics.info(
                "presence.partial", "peer=${id.takeLast(6)} mask=$mask missingState=true", quiet = true,
            )
            if (!hasStatus && !hasRichPresence && !friend.has(6) && !friend.has(81) && !friend.has(83)) return@forEach
            val entry = entries.getOrPut(id) { Entry() }
            entry.revision = ++revision
            if (hasStatus) {
                entry.state = friend.number(2).toInt()
                entry.statusRevision = entry.revision
                if ((entry.state ?: 0) !in 1..6) updateGame(entry, 0, 0, "")
                else if (friend.has(3) || friend.has(55) || friend.has(56)) {
                    val gameId = when {
                        friend.has(56) -> friend.number(56)
                        friend.has(3) -> friend.number(3)
                        else -> entry.gameId
                    }
                    val appId = if (friend.has(3)) friend.number(3) else appId(gameId)
                    updateGame(entry, gameId, appId, friend.text(55))
                }
            }
            if (friend.has(6)) { entry.flags = friend.number(6); entry.flagsRevision = entry.revision }
            if (friend.has(81)) { entry.deck = friend.number(81) != 0L; entry.deviceRevision = entry.revision }
            if (friend.has(83)) { entry.device = friend.number(83).toInt(); entry.deviceRevision = entry.revision }
            // The 4096 mask describes a complete KV replacement, including an empty list to clear it.
            // A Rich Presence-only update must not infer an offline state from missing field 2.
            if (hasRichPresence) entry.richPresence = if (entry.state == null || (entry.state ?: 0) in 1..6) {
                friend.messages(71).associate { kv -> kv.text(1).lowercase(java.util.Locale.ROOT) to kv.text(2) }
            } else emptyMap()
            changed.put(json(id, entry))
        }
        publishValues(changed)
        resolveGameNames()
        resolveRichPresence()
    }

    /** Batch and throttle snapshots; never block chat history or the CM receive thread. */
    fun refresh(ids: List<String>, force: Boolean = false) {
        synchronized(lock) {
            if (closed) return
            val now = SystemClock.elapsedRealtime()
            val batch = ids.distinct().filter {
                it.matches(STEAM_ID) && it !in pending && (force || now - (queried[it] ?: -60_000L) >= 30_000L)
            }
            if (batch.isEmpty()) return
            val tickets = batch.associateWith { id ->
                queried[id] = now
                pending.add(id)
                Stamp(epoch, entries[id]?.revision ?: 0)
            }
            worker.execute {
                batch.chunked(100).forEachIndexed { index, part ->
                    val expectedEpoch = tickets.getValue(part.first()).epoch
                    if (synchronized(lock) { closed || expectedEpoch != epoch }) return@forEachIndexed
                    val response = runCatching { load(part, force && index == 0) }
                        .onFailure {
                            if (!synchronized(lock) { closed }) SteamChatDiagnostics.failure("presence.snapshot", it)
                            synchronized(lock) { if (expectedEpoch == epoch) part.forEach { id -> queried.remove(id) } }
                        }.getOrNull()
                    synchronized(lock) { if (expectedEpoch == epoch) pending.removeAll(part.toSet()) }
                    if (response == null) return@forEachIndexed
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
                            // LinkDetails is a complete snapshot: a real offline account can omit state=0.
                            // Sparse 766 packets never advance statusRevision, so they cannot block repair.
                            val state = privateData.number(1).toInt()
                            if (state !in 0..7) return@account
                            entry.revision = ++revision
                            if (entry.statusRevision <= ticket.revision) {
                                entry.state = state
                                entry.statusRevision = entry.revision
                            }
                            if (entry.gameRevision <= ticket.revision) {
                                val gameId = privateData.number(4)
                                updateGame(entry, gameId, appId(gameId), privateData.text(8))
                            }
                            if (entry.flagsRevision <= ticket.revision && (privateData.has(2) || state !in 1..6)) {
                                entry.flags = privateData.number(2)
                                entry.flagsRevision = entry.revision
                            }
                            if (entry.deviceRevision <= ticket.revision && (privateData.has(20) || state !in 1..6)) {
                                entry.device = privateData.number(20).toInt()
                                entry.deck = entry.device == 544
                                entry.deviceRevision = entry.revision
                            }
                            changed.put(json(id, entry))
                        }
                        publishValues(changed)
                        resolveGameNames()
                        resolveRichPresence()
                    }
                }
            }
        }
    }

    private fun updateGame(entry: Entry, gameId: Long, appId: Long, name: String) {
        val online = (entry.state ?: 0) in 1..6
        val sameGame = entry.gameId == gameId && entry.appId == appId
        if (!sameGame || !online) entry.richPresence = emptyMap()
        entry.game = if (!online) "" else name.ifBlank {
            if (sameGame && (gameId != 0L || appId != 0L)) entry.game else ""
        }
        entry.gameId = if (online) gameId else 0
        entry.appId = if (online) appId else 0
        entry.gameRevision = entry.revision
    }

    /** Called under lock; network work stays on the presence executor, never the CM receive thread. */
    private fun resolveGameNames() {
        val now = SystemClock.elapsedRealtime()
        val ids = entries.values.filter { (it.state ?: 0) in 1..6 && it.game.isBlank() }.map { it.appId }
            .distinct().filter { it > 0 && it !in gameNames && now - (gamesQueried[it] ?: -60_000L) >= 30_000L }
        if (ids.isEmpty()) return
        ids.forEach { gamesQueried[it] = now }
        val expectedEpoch = epoch
        worker.execute {
            ids.chunked(50).forEach { part ->
                if (synchronized(lock) { closed || expectedEpoch != epoch }) return@forEach
                val response = runCatching { loadGames(part) }
                    .onFailure { if (!synchronized(lock) { closed }) SteamChatDiagnostics.failure("presence.games", it) }
                    .getOrNull() ?: return@forEach
                synchronized(lock) {
                    if (closed || expectedEpoch != epoch) return@synchronized
                    val loaded = mutableSetOf<Long>()
                    response.messages(1).forEach { app ->
                        val id = app.number(1)
                        val name = app.text(2).trim().take(256)
                        if (id in part && name.isNotBlank()) { gameNames[id] = name; loaded.add(id) }
                    }
                    val changed = JSONArray()
                    entries.forEach { (id, entry) ->
                        if ((entry.state ?: 0) in 1..6 && entry.appId in loaded && entry.game.isBlank()) {
                            entry.revision = ++revision
                            changed.put(json(id, entry))
                        }
                    }
                    publishValues(changed)
                }
            }
        }
    }

    private fun resolveRichPresence() {
        val requests = richPresence.requests(entries.values.filter {
            (it.state ?: 0) in 1..6 && it.richPresence.containsKey("steam_display")
        }.map { it.appId })
        if (requests.isEmpty()) return
        val expectedEpoch = epoch
        worker.execute {
            requests.forEach { request ->
                if (synchronized(lock) { closed || expectedEpoch != epoch }) return@forEach
                val catalog = runCatching { richPresence.fetch(request) }
                    .onFailure { if (!synchronized(lock) { closed }) SteamChatDiagnostics.failure("presence.rich", it) }
                    .getOrNull()
                synchronized(lock) {
                    if (closed || expectedEpoch != epoch) return@synchronized
                    richPresence.complete(request, catalog)
                    if (catalog == null) return@synchronized
                    val changed = JSONArray()
                    entries.forEach { (id, entry) ->
                        if ((entry.state ?: 0) in 1..6 && entry.appId == request.appId && entry.richPresence.containsKey("steam_display")) {
                            entry.revision = ++revision
                            changed.put(json(id, entry))
                        }
                    }
                    publishValues(changed)
                }
            }
        }
    }

    private fun publishValues(values: JSONArray) {
        if (values.length() > 0) publish(JSONObject().put("type", "presence").put("epoch", epoch).put("values", values))
    }

    private fun json(id: String, entry: Entry): JSONObject = JSONObject().put("id", id)
        .put("epoch", epoch).put("revision", entry.revision).put("known", entry.state != null)
        .put("personaState", entry.state ?: 0).put("online", (entry.state ?: 0) in 1..6)
        .put("playing", (entry.state ?: 0) in 1..6 && (entry.gameId != 0L || entry.appId != 0L || entry.game.isNotBlank()))
        .put("gameId", java.lang.Long.toUnsignedString(entry.gameId)).put("appId", entry.appId)
        .put("game", if ((entry.state ?: 0) in 1..6) entry.game.ifBlank { gameNames[entry.appId].orEmpty() } else "")
        .put("richPresence", if ((entry.state ?: 0) in 1..6) richPresence.text(entry.appId, entry.richPresence) else "")
        .put("flags", entry.flags ?: JSONObject.NULL).put("device", entry.device ?: JSONObject.NULL)
        .put("deck", entry.deck ?: false)

    override fun close() {
        synchronized(lock) {
            closed = true; entries.clear(); queried.clear(); pending.clear(); gameNames.clear(); gamesQueried.clear()
            richPresence.reset()
        }
        worker.shutdownNow()
    }

    companion object {
        private val epochs = AtomicLong(System.currentTimeMillis())
        private val STEAM_ID = Regex("7656119\\d{10}")
        private fun appId(gameId: Long): Long = if ((gameId ushr 24) and 255L == 0L) gameId and 0xffffffL else 0L
    }
}
