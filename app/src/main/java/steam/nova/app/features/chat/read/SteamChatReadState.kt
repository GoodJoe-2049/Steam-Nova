package steam.nova.app.features.chat.read

import org.json.JSONArray
import org.json.JSONObject

/** Durable, account-owned read positions. A queued socket write is not a server confirmation. */
internal class SteamChatReadState(initial: JSONObject, private val persist: (JSONObject) -> Unit) {
    private data class Position(
        val read: Long, val confirmed: Long, val seenTimestamp: Long = read,
        val seenOrdinal: Long = MAX_TIME,
    )
    private val positions = linkedMapOf<String, Position>()
    private val updates = linkedSetOf<String>()

    init {
        initial.keys().forEach { key ->
            val value = initial.optJSONObject(key) ?: return@forEach
            val read = value.optLong("read").coerceIn(0, MAX_TIME)
            val confirmed = value.optLong("confirmed").coerceIn(0, MAX_TIME)
            val cursor = maxOf(read, confirmed)
            if (validKey(key) && cursor > 0) {
                val seen = value.optLong("seenTimestamp", cursor).coerceIn(cursor, MAX_TIME)
                positions[key] = Position(cursor, confirmed, seen,
                    if (seen == cursor) MAX_TIME else value.optLong("seenOrdinal", MAX_TIME).coerceIn(0, MAX_TIME))
            }
        }
    }

    @Synchronized
    fun mark(key: String, timestamp: Long, confirmed: Boolean = false,
             seenTimestamp: Long = timestamp, seenOrdinal: Long = MAX_TIME): JSONObject {
        require(validKey(key) && timestamp in 1..MAX_TIME)
        require(seenTimestamp in timestamp..MAX_TIME && seenOrdinal in 0..MAX_TIME)
        val old = positions[key] ?: Position(0, 0)
        val read = maxOf(old.read, timestamp)
        val suppliedOrdinal = if (seenTimestamp == read) MAX_TIME else seenOrdinal
        val advances = seenTimestamp > old.seenTimestamp || seenTimestamp == old.seenTimestamp && suppliedOrdinal >= old.seenOrdinal
        val seen = maxOf(read, old.seenTimestamp, seenTimestamp)
        val ordinal = if (seen == read) MAX_TIME else if (advances) suppliedOrdinal else old.seenOrdinal
        val next = Position(read, if (confirmed) maxOf(old.confirmed, timestamp) else old.confirmed, seen, ordinal)
        if (next != old) {
            positions[key] = next
            try { persist(snapshot()) }
            catch (error: Exception) {
                if (old.read == 0L) positions.remove(key) else positions[key] = old
                throw error
            }
            updates.add(key)
        }
        return json(key, next)
    }

    @Synchronized
    fun readThrough(key: String): Long = positions[key]?.read ?: 0

    @Synchronized
    fun pending(): Map<String, Long> = positions.filterValues { it.read > it.confirmed }.mapValues { it.value.read }

    @Synchronized
    fun snapshot(): JSONObject = JSONObject().also { result ->
        positions.forEach { (key, value) -> result.put(key, json(key, value)) }
    }

    @Synchronized
    fun drain(): JSONArray = JSONArray().also { result ->
        updates.forEach { key -> result.put(json(key, positions.getValue(key)).put("type", "readState")) }
        updates.clear()
    }

    /** Server cursors may advance, never retreat when an older request arrives late. */
    fun observe(rows: JSONArray) {
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val timestamp = row.optLong("lastRead")
            if (timestamp in 1..MAX_TIME) mark(row.getString("key"), timestamp, confirmed = true)
        }
    }

    @Synchronized
    fun project(rows: JSONArray): JSONArray {
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val point = positions[row.optString("key")] ?: continue
            if (point.read > 0 && row.optLong("timestamp") <= point.read) row.put("unread", 0)
        }
        return rows
    }

    fun summary(value: JSONObject): JSONObject {
        value.optJSONArray("sessions")?.let(::project)
        value.optJSONArray("groups")?.let { groups ->
            for (index in 0 until groups.length()) groups.getJSONObject(index).optJSONArray("rooms")?.let(::project)
        }
        return value.put("readState", snapshot())
    }

    private fun json(key: String, value: Position) = JSONObject().put("key", key)
        .put("read", value.read).put("confirmed", value.confirmed)
        .put("seenTimestamp", value.seenTimestamp).put("seenOrdinal", value.seenOrdinal)

    companion object {
        private const val MAX_TIME = 0xffffffffL
        private val KEY = Regex("p:7656119\\d{10}|g:[1-9]\\d{0,19}:[1-9]\\d{0,19}")
        private fun validKey(key: String) = KEY.matches(key)
    }
}
