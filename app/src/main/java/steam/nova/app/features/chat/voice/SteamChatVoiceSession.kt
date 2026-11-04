package steam.nova.app.features.chat.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import steam.nova.app.features.chat.data.SteamChatRepository
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import kotlin.time.Duration.Companion.seconds
import steam.nova.app.features.chat.protocol.SteamChatProtocol as P

/** One main-thread state machine per account, sharing chat's CM socket. No Android service. */
internal class SteamChatVoiceSession(private val app: Context, private val repo: SteamChatRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var generation = 0L
    private var revision = 0L
    private var target: SteamChatRepository.Target? = null
    private var voiceId = ""
    private var phase = "idle"
    private var reason = ""
    private var relay: SteamChatVoiceProtocol.Relay? = null
    private var accepted = false
    private var ice = false
    private var joining = false
    private var binding = false
    private var bound = false
    private var microphone = false
    private var output = false
    private var speaker = false
    private var startedAt = 0L
    private var media: SteamChatVoiceMedia? = null
    private var audio: SteamChatVoiceAudio? = null
    private val sounds = SteamChatVoiceSounds(app) { audio != null }
    private var deadline: Job? = null
    private var disconnect: Job? = null
    private var background: Job? = null
    private var foreground = true
    private var pendingSetup = 0
    private var incomingInvitation = false
    private val roomVoiceMembers = mutableMapOf<String, Set<Long>>()
    private val pendingAcks = mutableListOf<String>()
    @Volatile private var closed = false
    @Volatile private var published = "{}"
    private val members = linkedMapOf<String, JSONObject>()
    private val profiles = linkedMapOf<String, JSONObject>()
    private val loadingProfiles = hashSetOf<String>()
    private val active get() = phase !in setOf("idle", "ended")

    fun snapshot() = JSONObject(published)
    private fun publish() {
        published = JSONObject().put("account", repo.account.id).put("token", generation.toString()).put("revision", ++revision).put("phase", phase).put("reason", reason)
            .put("key", target?.key.orEmpty()).put("peer", target?.peer.orEmpty()).put("mic", microphone)
            .put("output", output).put("speaker", speaker).put("startedAt", startedAt)
            .put("members", JSONArray(members.values.toList())).put("profiles", JSONArray(profiles.values.toList()))
            .put("settling", pendingSetup > 0).toString()
    }
    private fun state(value: String) {
        if (value != "incoming") sounds.stopIncoming()
        if (phase != value) SteamChatDiagnostics.info("voice.state", "phase=$value")
        phase = value; publish()
    }
    private fun live(token: Long) = !closed && active && generation == token
    private fun rpc(method: String, body: P.Writer, token: Long = generation, done: (P.Fields) -> Unit = {}) {
        val createsSession = method in setOf("WebRTCClient.InitiateWebRTCConnection", "VoiceChat.RequestOneOnOneChat", "ChatRoom.JoinVoiceChat")
        if (createsSession) { pendingSetup++; publish() }
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { repo.call(method, body) } }
            if (!live(token)) {
                // Cancellation can race a successful request: leave the newly-created server room too.
                result.getOrNull()?.let { fields ->
                    if (method == "VoiceChat.RequestOneOnOneChat") {
                        val id = SteamChatVoiceProtocol.id(fields)
                        if (id != "0") send("VoiceChat.LeaveOneOnOneChat", SteamChatVoiceProtocol.leave(SteamChatVoiceProtocol.id(P.read(body.build())), id))
                    } else if (method == "ChatRoom.JoinVoiceChat") send("ChatRoom.LeaveVoiceChat", body)
                }
                if (createsSession) { pendingSetup--; publish() }
                return@launch
            }
            if (createsSession) { pendingSetup--; publish() }
            result.fold({ fields -> runCatching { done(fields) }.onFailure { fail("failed", it) } }, { fail("failed", it) })
        }
    }
    private fun send(method: String, body: P.Writer) {
        val leaving = method == "VoiceChat.LeaveOneOnOneChat" || method == "ChatRoom.LeaveVoiceChat"
        if (leaving) { pendingSetup++; publish() }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { repo.voiceNotify(method, body) } }
                .onFailure { SteamChatDiagnostics.failure("voice.leaveOrStatus", it) }
            if (leaving) { pendingSetup--; publish() }
        }
    }
    fun command(args: JSONObject): JSONObject {
        val copy = JSONObject(args.toString())
        scope.launch {
            if (closed) return@launch
            runCatching {
                repo.checkAccount()
                val action = copy.optString("action")
                if (action != "start" && copy.optString("token") != generation.toString()) return@launch
                when (action) {
                    "start" -> {
                        if (!foreground || active || pendingSetup > 0) return@launch
                        val parsed = SteamChatRepository.Target.parse(copy)
                        require(parsed.peer != repo.account.id)
                        begin(parsed)
                    }
                    "accept" -> if (phase == "incoming" && foreground) {
                        check(app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                        accepted = true; state("connecting"); armDeadline()
                        rpc("VoiceChat.AnswerOneOnOneChat", SteamChatVoiceProtocol.answer(requireNotNull(target?.peer), voiceId, true)) { startMedia() }
                    }
                    "leave" -> finish("", true)
                    "dismiss" -> if (!active) { target = null; reason = ""; state("idle") }
                    "mute" -> if (media != null) {
                        microphone = copy.optBoolean("mic"); output = copy.optBoolean("output")
                        media?.mute(microphone, output); status(); publish()
                    }
                    "speaker" -> if (audio != null) {
                        val enabled = copy.optBoolean("enabled")
                        audio?.speaker(enabled); speaker = enabled; publish()
                    }
                }
            }.onFailure { fail(if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) "permission" else "failed", it) }
        }
        return snapshot()
    }
    private fun begin(value: SteamChatRepository.Target) {
        generation++; target = value; voiceId = ""; reason = ""; accepted = value.peer == null
        incomingInvitation = false
        microphone = false; output = false; speaker = false; startedAt = 0; members.clear()
        state("connecting"); armDeadline(); startMedia()
        // InitiateFriendChat plays once; room entry, ringing updates and reconnects do not.
        if (value.peer != null) sounds.outgoing()
        loadProfiles(listOfNotNull(value.peer, repo.account.id))
    }
    private fun armDeadline() {
        deadline?.cancel(); val token = generation
        deadline = scope.launch { delay(60.seconds); if (live(token) && phase != "connected") finish("timeout", true) }
    }
    private fun startMedia() {
        val token = generation
        try {
            val route = SteamChatVoiceAudio(app) { if (live(token)) finish("interrupted", true) }
            audio = route; route.start()
            val engine = SteamChatVoiceMedia(app) { kind, value ->
                if (live(token)) runCatching { mediaEvent(kind, value) }.onFailure { fail("media", it) }
            }
            media = engine; engine.start()
        } catch (error: Exception) { fail(if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) "permission" else "media", error) }
    }
    private fun mediaEvent(kind: String, value: String) {
        when (kind) {
            "offer" -> {
                // A mute pressed while the local page was loading must apply before any relay is joined.
                media?.mute(microphone, output)
                val version = Regex("Chrome/([0-9.]+)").find(media?.userAgent.orEmpty())?.groupValues?.get(1) ?: "126"
                rpc("WebRTCClient.InitiateWebRTCConnection", P.Writer().text(1, value).text(2, "Chrome").text(3, version)) {
                    val remote = it.text(1); require(remote.isNotBlank()); media?.remote(remote)
                }
            }
            "ack" -> { pendingAcks.add(value); acknowledge() }
            "ice" -> when (value) {
                "connected", "completed" -> { ice = true; disconnect?.cancel(); join(); bind() }
                "disconnected" -> {
                    ice = false; state("reconnecting"); disconnect?.cancel(); val token = generation
                    disconnect = scope.launch { delay(10.seconds); if (live(token) && !ice) finish("disconnected", true) }
                }
                "failed", "closed" -> finish("disconnected", true)
            }
            "error" -> finish(if (value == "permission") "permission" else "media", true)
            "stats" -> runCatching {
                val stats = JSONObject(value)
                SteamChatDiagnostics.info("voice.media", "sent=${stats.optLong("sent")} received=${stats.optLong("received")}", true)
            }
        }
    }
    private fun acknowledge() {
        val coordinates = relay ?: return
        val versions = pendingAcks.toList(); pendingAcks.clear()
        versions.forEach { version -> rpc("WebRTCClient.AcknowledgeUpdatedRemoteDescription",
            coordinates.coordinates().number(5, java.lang.Long.parseUnsignedLong(version))) }
    }
    private fun join() {
        if (!ice || joining || voiceId.isNotEmpty()) return
        val selected = target ?: return
        joining = true
        rpc(if (selected.peer != null) "VoiceChat.RequestOneOnOneChat" else "ChatRoom.JoinVoiceChat",
            if (selected.peer != null) P.Writer().fixed64(1, SteamChatVoiceProtocol.bits(selected.peer)) else selected.writer()) { fields ->
            voiceId = SteamChatVoiceProtocol.id(fields); require(voiceId != "0")
            joining = false
            state(if (selected.peer != null && !accepted) "ringing" else "connecting"); bind()
        }
    }
    private fun bind() {
        if (!ice || !accepted || voiceId.isBlank()) return
        if (bound) { state("connected"); return }
        if (binding) return
        val coordinates = relay ?: return
        binding = true
        rpc("VoiceChat.UpdateVoiceChatWebRTCData", SteamChatVoiceProtocol.bind(voiceId, coordinates, media?.userAgent.orEmpty())) {
            binding = false; bound = true; startedAt = System.currentTimeMillis(); deadline?.cancel()
            state(if (ice) "connected" else "reconnecting"); status()
        }
    }
    private fun status() {
        if (bound && voiceId.isNotEmpty()) send("VoiceChat.NotifyUserVoiceStatus",
            SteamChatVoiceProtocol.status(voiceId, repo.account.id, microphone, output))
    }
    fun event(method: String, fields: P.Fields) {
        scope.launch {
            if (closed) return@launch
            runCatching { receive(method, fields) }.onFailure { fail("failed", it) }
        }
    }
    private fun receive(method: String, fields: P.Fields) {
        val name = method.substringAfterLast('.')
        val id = SteamChatVoiceProtocol.id(fields)
        if (method == "ChatRoom.GetMyChatRoomGroups" || name == "NotifyChatRoomGroupRoomsChange") {
            updateRoomVoiceMembers(id, fields.messages(if (name == "GetMyChatRoomGroups") 6 else 3))
            return
        }
        if (name == "NotifyOneOnOneChatRequested") {
            val peer = SteamChatVoiceProtocol.id(fields, 2)
            if (!peer.matches(Regex("7656119\\d{10}")) || id == "0") return
            if (active || !foreground || pendingSetup > 0) {
                if (voiceId != id) send("VoiceChat.AnswerOneOnOneChat", SteamChatVoiceProtocol.answer(peer, id, false))
                return
            }
            generation++; target = SteamChatRepository.Target(peer, null, null); voiceId = id
            incomingInvitation = true
            reason = ""; members.clear(); microphone = false; output = false; speaker = false; startedAt = 0
            state("incoming"); armDeadline()
            sounds.startIncoming()
            loadProfiles(listOf(peer, repo.account.id))
            return
        }
        if (!active) return
        when (name) {
            "NotifyWebRTCSessionConnected" -> if (media != null) { relay = SteamChatVoiceProtocol.relay(fields); acknowledge(); bind() }
            "NotifyWebRTCUpdateRemoteDescription" -> if (media != null) media?.remote(fields.text(1), SteamChatVoiceProtocol.id(fields, 2))
            "NotifyOneOnOneChatResponse" -> if (SteamChatVoiceProtocol.id(fields, 2) == target?.peer && (id == voiceId || joining)) {
                // Response and request reply may cross on the socket.
                if (voiceId.isEmpty()) voiceId = id
                accepted = fields.number(3) == 1L
                if (accepted) { sounds.entered(); state("connecting"); bind() }
                else { sounds.exited(); finish("declined", false) }
            }
            "NotifyVoiceChatEnded" -> if (id == voiceId) {
                // Web has separate pending-invitation and active private-call branches.
                // Accepting an invitation stops its ring but keeps the invitation until it ends.
                if (incomingInvitation) sounds.exited()
                if (target?.peer != null && (binding || bound)) sounds.exited()
                finish("", false)
            }
            "NotifyShouldRejoinChatRoomVoiceChat" -> if (id == target?.room && SteamChatVoiceProtocol.id(fields, 2) == target?.group) {
                voiceId = ""; bound = false; binding = false; joining = false; state("reconnecting"); armDeadline(); join()
            }
            "NotifyUserJoinedVoiceChat" -> if (id == voiceId) { member(fields, false); publish() }
            "NotifyUserVoiceStatus" -> if (id == voiceId) { member(fields); publish() }
            "NotifyUserLeftVoiceChat" -> if (id == voiceId) { members.remove(SteamChatVoiceProtocol.id(fields, 2)); publish() }
            "NotifyAllUsersVoiceStatus" -> if (id == voiceId) { members.clear(); fields.messages(2).forEach { member(it) }; publish() }
        }
    }
    private fun updateRoomVoiceMembers(group: String, rooms: List<P.Fields>) {
        val keys = hashSetOf<String>()
        val self = repo.account.id.toLong() - 76561197960265728L
        rooms.forEach { room ->
            val key = "g:$group:${SteamChatVoiceProtocol.id(room)}"
            keys.add(key)
            val next = room.numbers(4).toSet()
            val previous = roomVoiceMembers.put(key, next).orEmpty()
            if (active && target?.key == key) {
                // UpdateChatState gives arrivals priority if the same update also contains departures.
                if (next.any { it != self && it !in previous }) sounds.entered()
                else if (previous.any { it != self && it !in next }) sounds.exited()
            }
        }
        roomVoiceMembers.keys.removeAll { it.startsWith("g:$group:") && it !in keys }
    }
    private fun member(fields: P.Fields, hasStatus: Boolean = true) {
        val id = SteamChatVoiceProtocol.id(fields, 2)
        if (id == "0") return
        val row = members[id] ?: JSONObject().put("id", id)
        if (hasStatus) row.put("mic", fields.number(3) != 0L).put("output", fields.number(4) != 0L)
        members[id] = row
        loadProfiles(listOf(id))
    }
    private fun loadProfiles(ids: List<String>) {
        val missing = ids.filter { it !in profiles && it !in loadingProfiles }.take(100)
        if (missing.isEmpty()) return
        loadingProfiles.addAll(missing)
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { repo.voiceProfiles(missing) } }
            loadingProfiles.removeAll(missing.toSet())
            if (!closed) result.getOrNull()?.let { rows ->
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index); profiles[row.getString("id")] = row
                }
                while (profiles.size > 200) profiles.remove(profiles.keys.first())
                publish()
            }
        }
    }
    fun connectionLost() { scope.launch { if (active) finish("disconnected", false) } }
    fun foreground(value: Boolean) {
        scope.launch {
            foreground = value; background?.cancel()
            // No foreground service: explicitly end on leaving Steam instead of silently losing microphone capture.
            if (!value) background = scope.launch { delay(3.seconds); if (!foreground && active) finish("background", true) }
        }
    }
    private fun fail(code: String, error: Throwable) {
        SteamChatDiagnostics.failure("voice.failed", error); finish(code, true)
    }
    private fun finish(code: String, notify: Boolean) {
        if (notify && voiceId.isNotEmpty()) target?.let { selected ->
            if (selected.peer == null) send("ChatRoom.LeaveVoiceChat", selected.writer())
            else if (phase == "incoming") send("VoiceChat.AnswerOneOnOneChat", SteamChatVoiceProtocol.answer(selected.peer, voiceId, false))
            else send("VoiceChat.LeaveOneOnOneChat", SteamChatVoiceProtocol.leave(selected.peer, voiceId))
        }
        generation++; deadline?.cancel(); disconnect?.cancel()
        media?.stop(); media = null; audio?.stop(); audio = null
        relay = null; voiceId = ""; accepted = false; ice = false; joining = false; binding = false; bound = false
        pendingAcks.clear()
        incomingInvitation = false
        reason = code; state("ended")
    }
    fun close() {
        closed = true
        scope.launch { finish("", false); sounds.close(); scope.cancel() }
    }
}
