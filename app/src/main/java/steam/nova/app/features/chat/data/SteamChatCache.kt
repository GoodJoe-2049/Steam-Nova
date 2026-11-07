package steam.nova.app.features.chat.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Private, account-separated storage. Credentials are never written here or included in backups. */
internal class SteamChatCache(context: Context, private val account: String) {
    private val directory = File(context.noBackupFilesDir, "millennium-chat/$account").apply { mkdirs() }
    private val lock = Any()
    private fun file(key: String) = File(directory, MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) } + ".json")
    private fun read(key: String): JSONObject = runCatching {
        val target = file(key)
        require(target.length() <= 8 * 1024 * 1024)
        AtomicFile(target).openRead().bufferedReader().use { JSONObject(it.readText()) }
    }.getOrDefault(JSONObject())
    private fun write(key: String, data: JSONObject) {
        val bytes = data.toString().toByteArray()
        require(bytes.size <= 8 * 1024 * 1024)
        val atomic = AtomicFile(file(key)); val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
    }
    fun summary(): JSONObject = synchronized(lock) {
        read("summary").put("preferences", read("preferences"))
    }
    fun saveSummary(value: JSONObject) = synchronized(lock) { write("summary", value) }
    fun readReceipts(): JSONObject = synchronized(lock) { read("read-receipts") }
    fun saveReadReceipts(value: JSONObject) = synchronized(lock) { write("read-receipts", value) }
    fun catalog(): JSONObject = synchronized(lock) { read("catalog") }
    fun saveCatalog(value: JSONObject) = synchronized(lock) { write("catalog", value) }
    fun preferences(): JSONObject = synchronized(lock) { read("preferences") }
    fun preference(key: String, value: JSONObject): JSONObject = synchronized(lock) {
        val prefs = read("preferences")
        prefs.put(key, JSONObject().put("pinned", value.optBoolean("pinned")).put("muted", value.optBoolean("muted")))
        write("preferences", prefs); prefs
    }
    fun thread(key: String): JSONObject = synchronized(lock) {
        val value = read("thread:$key")
        if (!value.has("messages")) value.put("messages", JSONArray())
        value.put("hidden", read("hidden-media").optJSONObject(key) ?: JSONObject())
        value
    }
    fun deleteLocal(key: String, id: String): JSONObject = synchronized(lock) {
        require(id.length in 1..180)
        val all = read("hidden-media")
        val hidden = all.optJSONObject(key) ?: JSONObject()
        hidden.put(id, true)
        all.put(key, hidden)
        write("hidden-media", all)
        merge(key, JSONArray())
        JSONObject()
    }
    fun state(key: String, value: JSONObject): JSONObject = synchronized(lock) {
        val data = thread(key)
        if (value.has("draft")) data.put("draft", value.optString("draft").take(8000))
        if (value.has("position")) data.put("position", value.getJSONObject("position"))
        write("thread:$key", data); JSONObject()
    }
    fun merge(key: String, incoming: JSONArray, more: Boolean? = null, remove: String? = null): JSONObject = synchronized(lock) {
        val data = thread(key)
        val messages = linkedMapOf<String, JSONObject>()
        val hidden = read("hidden-media").optJSONObject(key) ?: JSONObject()
        fun add(items: JSONArray) {
            for (index in 0 until items.length()) {
                val row = items.getJSONObject(index)
                if (hidden.optBoolean(row.optString("id"))) row.put("locallyHidden", true).put("text", "").put("reactions", JSONArray())
                if (row.optString("id") != remove) messages[row.getString("id")] = row
            }
        }
        add(data.getJSONArray("messages")); add(incoming)
        // Text alone is not a delivery receipt. Keep uncertain sends until the user resolves them.
        val confirmed = messages.values.filter { !it.optBoolean("pending") && !it.optBoolean("failed") }
        val pending = messages.values.filter { it.optBoolean("pending") || it.optBoolean("failed") }
        pending.groupBy { it.optString("text") }.forEach { (text, candidates) ->
            if (candidates.size == 1) {
                val local = candidates.single()
                val matches = confirmed.filter { it.optString("sender") == account && it.optString("text") == text &&
                        it.optLong("timestamp") in (local.optLong("timestamp") - 5)..(local.optLong("timestamp") + 120) }
                if (matches.size == 1) local.put("possibleMatch", matches.single().getString("id"))
            }
        }
        val sorted = messages.values.sortedWith(compareByDescending<JSONObject> { it.optLong("timestamp") }.thenByDescending { it.optLong("ordinal") })
        data.put("messages", JSONArray(sorted.take(600)))
        if (more != null) data.put("more", more)
        write("thread:$key", data)
        trim()
        data
    }
    fun recover(key: String): JSONObject = synchronized(lock) {
        val data = thread(key); val messages = data.getJSONArray("messages")
        var changed = false
        for (index in 0 until messages.length()) {
            val row = messages.getJSONObject(index)
            if (row.optBoolean("pending")) { row.put("pending", false).put("failed", true).put("error", "DELIVERY_UNKNOWN"); changed = true }
        }
        if (changed) write("thread:$key", data)
        data
    }
    private fun trim() {
        val protected = setOf(file("summary").name, file("preferences").name, file("catalog").name, file("read-receipts").name, file("hidden-media").name)
        directory.listFiles()?.filter { it.extension == "json" && it.name !in protected }
            ?.sortedByDescending { it.lastModified() }?.drop(60)?.forEach { AtomicFile(it).delete() }
    }
}
