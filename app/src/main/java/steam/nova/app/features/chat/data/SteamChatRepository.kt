package steam.nova.app.features.chat.data

import androidx.core.net.toUri
import steam.nova.app.features.chat.network.SteamChatAccount
import steam.nova.app.features.chat.network.SteamChatConnection
import steam.nova.app.features.chat.network.SteamChatFailure
import steam.nova.app.features.chat.network.SteamChatHttp
import steam.nova.app.features.chat.protocol.SteamChatProtocol as P
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Account-scoped chat operations; IDs stay strings across the JavaScript boundary. */
internal class SteamChatRepository(
    val account: SteamChatAccount,
    private val validate: () -> Unit = { if (SteamChatAccount.current(account.id) != account) throw SteamChatFailure("ACCOUNT_CHANGED") },
    private val onEvent: (JSONObject) -> Unit = {},
    private val onVoice: (String, P.Fields) -> Unit = { _, _ -> },
) : AutoCloseable {
    private val events = ArrayDeque<JSONObject>().apply { add(JSONObject().put("type", "resync")) }
    private val profiles = ConcurrentHashMap<String, JSONObject>()
    @Volatile private var closed = false
    @Volatile private var activeGroups: String? = null
    private val cm = SteamChatConnection(account, ::receive) { connected ->
        if (connected) activeGroups = null
        enqueue(JSONObject().put("type", if (connected) "connected" else "disconnected"))
    }
    private val nicknames = SteamChatNicknames(::checkAccount, { SteamChatHttp.nicknames(account) }, { call("Player.GetNicknameList") }) { names ->
        enqueue(JSONObject().put("type", "nicknames").put("names", names))
    }

    fun checkAccount() {
        if (closed) throw SteamChatFailure("ACCOUNT_CHANGED")
        validate()
    }

    internal fun call(method: String, body: P.Writer = P.Writer()): P.Fields {
        checkAccount()
        return cm.call("$method#1", body.build()).also { checkAccount() }
    }

    internal fun voiceNotify(method: String, body: P.Writer) {
        checkAccount()
        cm.notify("$method#1", body.build())
    }

    internal fun voiceProfiles(ids: List<String>): JSONArray {
        loadProfiles(ids)
        checkAccount()
        return JSONArray(ids.map(::profile))
    }

    fun snapshot(): JSONObject {
        checkAccount()
        val sessions = SteamChatHttp.friendMessages(account, "GetActiveMessageSessions", P.Writer().number(1, 0).number(2, 1))
            .messages(1).map { item ->
                val peer = steamId(item.number(1))
                JSONObject().put("key", "p:$peer").put("peer", peer).put("kind", "private")
                    .put("timestamp", item.number(2)).put("unread", item.number(4))
            }
        runCatching { loadProfiles(sessions.map { it.getString("peer") } + account.id) }
        checkAccount()
        sessions.forEach { row -> row.put("profile", profile(row.getString("peer"))) }
        return JSONObject().put("sessions", JSONArray(sessions)).put("self", profile(account.id)).put("nicknames", nicknames.snapshot())
    }

    fun groups(): JSONArray {
        val groups = call("ChatRoom.GetMyChatRoomGroups").messages(1).mapNotNull { pair ->
            val group = pair.messages(2).firstOrNull() ?: return@mapNotNull null
            val id = unsigned(group.number(1))
            // Seed voice membership before joining, so existing participants do not sound like arrivals.
            onVoice("ChatRoom.GetMyChatRoomGroups", group)
            val ack = pair.messages(1).firstOrNull()?.messages(3).orEmpty()
                .associate { unsigned(it.number(1)) to it.number(3) }
            val rooms = group.messages(6).sortedBy { it.number(6) }.map { room ->
                val roomId = unsigned(room.number(1))
                JSONObject().put("key", "g:$id:$roomId").put("kind", "group").put("group", id)
                    .put("room", roomId).put("title", room.text(2)).put("groupTitle", group.text(2))
                    .put("timestamp", room.number(5)).put("preview", room.text(7))
                    .put("unread", if (room.number(5) > (ack[roomId] ?: 0)) 1 else 0)
            }
            JSONObject().put("id", id).put("title", group.text(2)).put("name", group.text(2))
                .put("avatar", avatar(group.bytes(21)).ifBlank { avatar(group.bytes(11)) }).put("tagline", group.text(8))
                .put("owner", steamId(group.number(9))).put("rank", group.number(12)).put("rooms", JSONArray(rooms))
        }
        val signature = groups.map { it.getString("id") }.sorted().joinToString(",")
        if (activeGroups != signature) {
            val active = P.Writer().number(3, 100)
            groups.forEach { active.number(1, it.getString("id").toULong().toLong()) }
            call("ChatRoom.SetSessionActiveChatRoomGroups", active)
            activeGroups = signature
        }
        return JSONArray(groups)
    }

    fun tradeUrl(): JSONObject {
        // Read the current token; never rotate it merely by opening the share menu.
        val token = call("Econ.GetTradeOfferAccessToken", P.Writer().number(1, 0)).text(1)
        require(token.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        val partner = account.id.toULong() and 0xffffffffuL
        return JSONObject().put("url", "https://steamcommunity.com/tradeoffer/new/?partner=$partner&token=$token")
    }

    fun profileAppearance(id: String): JSONObject {
        require(id.matches(STEAM_ID))
        val equipped = call("Player.GetProfileItemsEquipped", P.Writer().fixed64(1, id.toLong()))
        fun image(field: Int): String {
            val item = equipped.messages(field).firstOrNull() ?: return ""
            val path = item.text(3).ifBlank { item.text(2) }
            if (path.isBlank()) return ""
            val url = if (path.startsWith("https://")) path else "https://shared.akamai.steamstatic.com/community_assets/" + path.trimStart('/')
            val uri = url.toUri()
            val host = uri.host.orEmpty()
            return url.takeIf { uri.scheme == "https" && (host == "steamstatic.com" || host.endsWith(".steamstatic.com") || host.endsWith(".steamusercontent.com")) }.orEmpty()
        }
        return JSONObject().put("avatarFrame", image(3)).put("animatedAvatar", image(4))
    }

    fun friends(): JSONArray {
        checkAccount()
        val root = SteamChatHttp.api(account, "/ISteamUserOAuth/GetFriendList/v1/", mapOf("steamid" to account.id, "relationship" to "friend"))
        val list = (root.optJSONObject("friendslist") ?: root.optJSONObject("response") ?: root).optJSONArray("friends") ?: JSONArray()
        val ids = (0 until list.length()).mapNotNull { i ->
            val row = list.optJSONObject(i) ?: return@mapNotNull null
            val relation = row.optString("relationship", row.optString("efriendrelationship"))
            row.optString("steamid", row.optString("ulfriendid"))
                .takeIf { it.matches(STEAM_ID) && (relation == "friend" || relation == "3") }
        }
        loadProfiles(ids)
        checkAccount()
        return JSONArray(ids.map(::profile).sortedBy { it.optString("name").lowercase() })
    }

    private fun profile(id: String): JSONObject {
        val raw = profiles[id]
        val personaName = raw?.optString("personaName").orEmpty().ifBlank { id }
        val nickname = nicknames.name(id)
        return (raw?.let { JSONObject(it.toString()) } ?: JSONObject().put("id", id).put("personaName", personaName))
            .put("nickname", nickname).put("name", nickname.ifBlank { personaName })
    }

    private fun loadProfiles(ids: List<String>) {
        nicknames.refresh()
        ids.distinct().filter { it.matches(STEAM_ID) }.chunked(100).forEach { batch ->
            checkAccount()
            val payload = SteamChatHttp.api(account, "/ISteamUserOAuth/GetUserSummaries/v1/", mapOf("steamids" to batch.joinToString(",")))
            val players = (payload.optJSONObject("response") ?: payload).optJSONArray("players") ?: JSONArray()
            for (i in 0 until players.length()) {
                val player = players.getJSONObject(i)
                val id = player.optString("steamid")
                if (id.matches(STEAM_ID)) profiles[id] = JSONObject().put("id", id)
                    .put("personaName", player.optString("personaname", id)).put("name", player.optString("personaname", id))
                    .put("avatar", player.optString("avatarmedium", player.optString("avatar")))
                    .put("personaState", player.optInt("personastate")).put("online", player.optInt("personastate") != 0).put("game", player.optString("gameextrainfo"))
            }
        }
    }

    fun history(args: JSONObject): JSONObject {
        val target = Target.parse(args)
        val before = args.optJSONObject("before")
        val request = if (target.peer != null) P.Writer().fixed64(1, account.id.toLong()).fixed64(2, target.peer.toLong())
            .number(3, 50).number(4, if (before == null) 1 else 0).number(6, 1).apply {
                before?.let { fixed32(5, it.getLong("timestamp")); number(7, it.getLong("ordinal")) }
            } else target.writer().number(7, 50).apply {
            before?.let { number(3, it.getLong("timestamp")); number(4, it.getLong("ordinal")) }
        }
        checkAccount()
        val fields = if (target.peer != null) SteamChatHttp.friendMessages(account, "GetRecentMessages", request)
        else call("ChatRoom.GetMessageHistory", request)
        val messages = fields.messages(1).map { item ->
            val event = if (target.peer == null) item.messages(5).firstOrNull() else null
            message(target.key, steamId(item.number(1)), item.number(2), item.number(4), item.text(3).ifBlank { event?.text(2).orEmpty() })
                .put("deleted", target.peer == null && item.number(6) != 0L)
                .put("system", item.number(1) == 0L)
                .put("eventType", event?.number(1) ?: 0)
                .put("reactions", JSONArray(item.messages(if (target.peer == null) 7 else 5).map { reaction ->
                    val people = if (target.peer != null) reaction.numbers(3).map(::steamId) else emptyList()
                    JSONObject().put("type", reaction.number(1)).put("name", reaction.text(2).trim(':'))
                        .put("count", if (target.peer == null) reaction.number(3) else people.size.toLong())
                        .put("selected", if (target.peer == null) reaction.number(4) != 0L else account.id in people)
                        .put("reactors", JSONArray(people))
                }))
        }.sortedWith(compareBy<JSONObject> { it.getLong("timestamp") }.thenBy { it.getLong("ordinal") })
        runCatching { loadProfiles(messages.map { it.getString("sender") } + listOfNotNull(account.id, target.peer)) }
        checkAccount()
        messages.forEach { it.put("profile", profile(it.getString("sender"))) }
        return JSONObject().put("messages", JSONArray(messages)).put("more", fields.number(4) != 0L).put("self", profile(account.id))
            .put("peerProfile", target.peer?.let(::profile)).put("nicknames", nicknames.snapshot())
    }

    fun send(args: JSONObject): JSONObject {
        val target = Target.parse(args)
        val text = args.getString("text").trim()
        require(text.isNotBlank() && text.toByteArray().size <= 8000)
        val request = if (target.peer != null) P.Writer().fixed64(1, target.peer.toLong()).number(2, 1)
            .text(3, text.replace("[", "\\[")).number(4, 1).number(5, 1)
        else target.writer().text(3, text.replace("[", "\\[")).number(4, 1)
        // Sending is never retried automatically: a lost reply does not mean the message was not delivered.
        val fields = call(if (target.peer != null) "FriendMessages.SendMessage" else "ChatRoom.SendChatMessage", request)
        return message(target.key, account.id, fields.number(2), fields.number(3), fields.text(1).ifBlank { text })
            .put("profile", profile(account.id))
    }

    fun acknowledge(args: JSONObject): JSONObject {
        val target = Target.parse(args)
        val time = args.getLong("timestamp")
        require(time > 0)
        checkAccount()
        val body = if (target.peer != null) P.Writer().fixed64(1, target.peer.toLong()).number(2, time)
        else target.writer().number(3, time)
        if (target.peer != null) SteamChatHttp.friendMessages(account, "AckMessage", body)
        else cm.notify("ChatRoom.AckChatMessage#1", body.build())
        checkAccount()
        return JSONObject()
    }

    fun catalog(): JSONObject {
        checkAccount()
        val fields = cm.catalog()
        checkAccount()
        fun items(field: Int) = JSONArray(fields.messages(field).mapNotNull {
            val name = it.text(1).trim(':')
            name.takeIf { value -> value.isNotBlank() && value.length <= 160 }?.let { value -> JSONObject().put("name", value) }
        })
        return JSONObject().put("emoticons", items(1)).put("stickers", items(2))
            .put("effects", JSONArray(fields.messages(3).map { JSONObject().put("name", it.text(1))
                .put("count", it.number(2, 1)).put("infinite", it.number(4) != 0L) }))
    }

    fun drain(): JSONArray = synchronized(events) {
        checkAccount()
        JSONArray(events.toList()).also { events.clear() }
    }

    private fun enqueue(value: JSONObject) {
        if (closed) return
        synchronized(events) {
            if (events.size >= 500) { events.clear(); events.add(JSONObject().put("type", "resync")) }
            events.add(value)
        }
        onEvent(value)
    }

    private fun receive(packet: P.Packet) {
        if (closed) return
        val method = packet.header.text(12).substringBefore('#')
        val fields = P.read(packet.body)
        if (steam.nova.app.features.chat.voice.SteamChatVoiceProtocol.owns(method)) {
            onVoice(method, fields)
            return
        }
        // The web plays group enter/exit sounds from room-state changes, not voice-status refreshes.
        // Keep the normal chat resync below as well.
        if (method == "ChatRoomClient.NotifyChatRoomGroupRoomsChange") onVoice(method, fields)
        when (method) {
            "FriendMessagesClient.IncomingMessage" -> {
                val peer = fields.number(1).toString()
                if (!peer.matches(STEAM_ID)) return
                if (fields.number(2) == 2L) {
                    if (fields.number(7) == 0L) enqueue(JSONObject().put("type", "typing").put("key", "p:$peer"))
                    return
                }
                if (fields.number(2) !in setOf(1L, 3L, 4L, 11L, 14L)) return
                val sender = if (fields.number(7) != 0L) account.id else peer
                val body = fields.text(4).ifBlank { fields.text(8) }
                enqueue(JSONObject().put("type", "message").put("message",
                    message("p:$peer", sender, fields.number(5), fields.number(6), if (fields.number(2) == 4L && !body.startsWith("/me ")) "/me $body" else body).put("profile", profile(sender))))
            }
            "FriendMessagesClient.MessageReaction" -> enqueue(JSONObject().put("type", "reaction")
                .put("key", "p:${fields.number(1)}").put("timestamp", fields.number(2)).put("ordinal", fields.number(3))
                .put("reactor", fields.number(4).toString()).put("reactionType", fields.number(5))
                .put("name", fields.text(6).trim(':')).put("add", fields.number(7) != 0L))
            "ChatRoomClient.NotifyChatMessageModified" -> enqueue(JSONObject().put("type", "modified")
                .put("key", "g:${unsigned(fields.number(1))}:${unsigned(fields.number(2))}")
                .put("changes", JSONArray(fields.messages(3).map { JSONObject().put("timestamp", it.number(1))
                    .put("ordinal", it.number(2)).put("deleted", it.number(3) != 0L) })))
            "ChatRoomClient.NotifyMessageReaction" -> enqueue(JSONObject().put("type", "threadChanged")
                .put("key", "g:${unsigned(fields.number(1))}:${unsigned(fields.number(2))}"))
            "ChatRoomClient.NotifyIncomingChatMessage" -> {
                val event = fields.messages(8).firstOrNull()
                val text = fields.text(4).ifBlank { fields.text(9) }.ifBlank { event?.text(2).orEmpty() }
                enqueue(JSONObject().put("type", "message").put("message",
                    message("g:${unsigned(fields.number(1))}:${unsigned(fields.number(2))}", fields.number(3).toString(),
                        fields.number(5), fields.number(7), text).put("profile", profile(fields.number(3).toString()))
                        .put("system", (event?.number(1) ?: 0) != 0L).put("eventType", event?.number(1) ?: 0)))
            }
            "FriendMessagesClient.NotifyAckMessageEcho", "ChatRoomClient.NotifyAckChatMessageEcho" -> enqueue(JSONObject().put("type", "resync"))
            else -> if (method.startsWith("ChatRoomClient.")) enqueue(JSONObject().put("type", "resync"))
        }
    }

    override fun close() {
        closed = true
        nicknames.close()
        cm.close()
        synchronized(events) { events.clear() }
        profiles.clear()
    }

    internal data class Target(val peer: String?, val group: String?, val room: String?) {
        val key get() = peer?.let { "p:$it" } ?: "g:$group:$room"
        fun writer() = P.Writer().number(1, group!!.toULong().toLong()).number(2, room!!.toULong().toLong())
        companion object {
            fun parse(args: JSONObject): Target {
                val peer = args.optString("peer").takeIf { it.isNotBlank() }
                if (peer != null) { require(peer.matches(STEAM_ID)); return Target(peer, null, null) }
                val group = args.getString("group"); val room = args.getString("room")
                require(group.toULong() > 0u && room.toULong() > 0u)
                return Target(null, group, room)
            }
        }
    }

    companion object {
        internal fun avatar(bytes: ByteArray): String {
            val raw = bytes.toString(Charsets.UTF_8).trim()
            if (raw.startsWith("https://") && Regex("https://(?:[a-z0-9-]+\\.)*(?:steamstatic\\.com|steamusercontent\\.com|steamcommunity\\.com)/.*").matches(raw)) return raw
            val hash = if (raw.matches(Regex("[a-fA-F0-9]{40}"))) raw.lowercase()
            else if (bytes.size == 20) bytes.joinToString("") { "%02x".format(it.toInt() and 255) } else return ""
            return "https://community.akamai.steamstatic.com/images/chaticons/${hash.take(2)}/${hash.substring(2, 4)}/${hash.substring(4, 6)}/${hash}_256.jpg"
        }
        private val STEAM_ID = Regex("7656119\\d{10}")
        private fun steamId(id: Long) = if (id == 0L) "" else (76561197960265728L + id).toString()
        private fun unsigned(id: Long) = java.lang.Long.toUnsignedString(id)
        private fun message(key: String, sender: String, time: Long, ordinal: Long, text: String) = JSONObject()
            .put("key", key).put("id", "$time:$ordinal:$sender").put("sender", sender)
            .put("timestamp", time).put("ordinal", ordinal).put("text", text)
    }
}
