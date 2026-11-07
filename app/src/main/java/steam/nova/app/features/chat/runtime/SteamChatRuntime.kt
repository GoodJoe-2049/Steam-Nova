package steam.nova.app.features.chat.runtime

import android.content.Context
import steam.nova.app.features.chat.background.SteamChatNotifications
import steam.nova.app.features.chat.data.SteamChatCache
import steam.nova.app.features.chat.data.SteamChatGameInfo
import steam.nova.app.features.chat.data.SteamChatGroupAdmin
import steam.nova.app.features.chat.data.SteamChatMessageActions
import steam.nova.app.features.chat.data.SteamChatRepository
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import steam.nova.app.features.chat.network.SteamChatAccount
import steam.nova.app.features.chat.network.SteamChatFailure
import steam.nova.app.features.chat.read.SteamChatReadState
import steam.nova.app.features.chat.read.SteamChatReadSync
import steam.nova.app.features.chat.voice.SteamChatVoiceSession
import steam.nova.app.features.ui.settings.SteamModuleSettings
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One chat session inside Steam. No Binder, module Activity or foreground service is needed. */
internal class SteamChatRuntime(context: Context) {
    private val app = context.applicationContext
    private val lock = Any()
    private val notifications = SteamChatNotifications(app)
    private val games = SteamChatGameInfo(app)
    private val events = Executors.newSingleThreadExecutor { task -> Thread(task, "MillenniumChatEvents").apply { isDaemon = true } }
    private val clock = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "MillenniumChatRefresh").apply { isDaemon = true } }
    @Volatile private var session: Session? = null
    private var revoked: SteamChatAccount? = null
    private var revokedEpoch = -1L

    private inner class Session(val account: SteamChatAccount, var epoch: Long) {
        val disk = SteamChatCache(app, account.id)
        val reads = SteamChatReadState(disk.readReceipts(), disk::saveReadReceipts)
        var groups: JSONArray = disk.summary().optJSONArray("groups") ?: JSONArray()
        @Volatile var foreground = true
        @Volatile var visibleThread = ""
        var visibilityEpoch = -1L
        var visibilityVersion = -1L
        val repo: SteamChatRepository = SteamChatRepository(account, { checkSession(this) }, { event ->
            events.execute {
                runCatching { checkSession(this); handleEvent(this, event) }
                    .onFailure { if (session === this) SteamChatDiagnostics.failure("runtime.event", it) }
            }
        }, { method, fields -> voice.event(method, fields) })
        val voice = SteamChatVoiceSession(app, repo)
        val readSync = SteamChatReadSync(reads, { checkSession(this) }, { key, timestamp ->
            val parts = key.split(':')
            val args = JSONObject().put("timestamp", timestamp)
            if (parts[0] == "p") args.put("peer", parts[1]) else args.put("group", parts[1]).put("room", parts[2])
            repo.acknowledge(args)
        }, repo::readPositions)
    }

    init {
        SteamChatDiagnostics.info("runtime.create", "process=steam")
        clock.scheduleWithFixedDelay({
            val lease = session
            if (lease != null) runCatching {
                // Even when background notifications are off, revoke a signed-out or disabled session.
                checkSession(lease)
                if (!lease.foreground && notifications.enabled()) {
                    val groups = lease.repo.groups()
                    val snapshot = lease.repo.snapshot()
                    synchronized(lock) {
                        checkSession(lease); lease.groups = groups
                        saveSummary(lease, snapshot.put("groups", groups))
                    }
                }
            }.onFailure { error ->
                synchronized(lock) {
                    if (session === lease && !valid(lease.account)) clearSession()
                }
                SteamChatDiagnostics.failure("runtime.background", error)
            }
        }, 30, 30, TimeUnit.SECONDS)
    }

    private fun valid(account: SteamChatAccount): Boolean = SteamModuleSettings.isBuiltInChatEnabled(app) &&
            runCatching { SteamChatAccount.current(account.id) == account }.getOrDefault(false)

    private fun checkSession(lease: Session) {
        if (session !== lease || !valid(lease.account)) throw SteamChatFailure("ACCOUNT_CHANGED")
    }

    private fun acquire(account: SteamChatAccount, args: JSONObject): Session = synchronized(lock) {
        if (!valid(account)) throw SteamChatFailure("ACCOUNT_CHANGED")
        val epoch = args.getLong("epoch")
        require(epoch >= 0)
        if (account == revoked && epoch <= revokedEpoch) throw SteamChatFailure("STALE_REQUEST")
        val existing = session
        if (existing?.account == account) {
            if (epoch < existing.epoch) throw SteamChatFailure("STALE_REQUEST")
            existing.epoch = epoch
            return@synchronized existing
        }
        clearSession()
        Session(account, epoch).also { session = it; it.readSync.wake(); SteamChatDiagnostics.info("runtime.session.ready") }
    }

    fun <T> withRepository(account: SteamChatAccount, args: JSONObject, block: (SteamChatRepository) -> T): T {
        val lease = acquire(account, args)
        return block(lease.repo).also { checkSession(lease) }
    }

    fun execute(account: SteamChatAccount, args: JSONObject): Any {
        val operation = args.getString("operation")
        val requestEpoch = args.getLong("epoch")
        if (operation == "pause") return synchronized(lock) {
            val active = session
            if (active?.account == account && requestEpoch >= active.epoch) {
                revoked = account; revokedEpoch = requestEpoch; clearSession()
            }
            JSONObject()
        }
        val lease = acquire(account, args)
        val repo = lease.repo
        val disk = lease.disk
        val result: Any = when (operation) {
            "voice" -> lease.voice.command(args)
            "voiceState" -> lease.voice.snapshot()
            "gameInfo" -> games.resolve(args.getString("appId"), args.optString("language"))
            "cache" -> lease.reads.summary(disk.summary()).put("background", notifications.status())
            "threadCache" -> disk.recover(SteamChatRepository.Target.parse(args).key)
            "threadState" -> disk.state(SteamChatRepository.Target.parse(args).key, args)
            "preferences" -> disk.preference(SteamChatRepository.Target.parse(args).key, args)
            "visibility" -> {
                synchronized(lock) {
                    checkSession(lease)
                    val version = args.optLong("version")
                    if (requestEpoch > lease.visibilityEpoch || (requestEpoch == lease.visibilityEpoch && version >= lease.visibilityVersion)) {
                        lease.visibilityEpoch = requestEpoch; lease.visibilityVersion = version
                        lease.foreground = args.optBoolean("foreground", true); lease.visibleThread = args.optString("key")
                        lease.voice.foreground(lease.foreground)
                        lease.repo.foreground(lease.foreground)
                        if (lease.foreground && lease.visibleThread.isNotBlank()) notifications.cancel(account.id, lease.visibleThread)
                    }
                }
                JSONObject()
            }
            "backgroundStatus" -> notifications.status()
            "backgroundEnable" -> synchronized(lock) { checkSession(lease); notifications.enable() }
            "backgroundDisable" -> synchronized(lock) { checkSession(lease); notifications.disable() }
            "snapshot" -> repo.snapshot().also { value ->
                synchronized(lock) { checkSession(lease); saveSummary(lease, value.put("groups", lease.groups)) }
            }
            "groups" -> repo.groups().also { value ->
                synchronized(lock) {
                    checkSession(lease); lease.groups = value
                    saveSummary(lease, disk.summary().put("groups", value))
                }
            }
            "events" -> repo.drain().also { values ->
                val updates = lease.reads.drain()
                for (index in 0 until updates.length()) values.put(updates.getJSONObject(index))
            }.put(JSONObject().put("type", "voice").put("value", lease.voice.snapshot()))
            "friends" -> repo.friends()
            "history" -> repo.history(args).also {
                synchronized(lock) { checkSession(lease); disk.merge(SteamChatRepository.Target.parse(args).key, it.getJSONArray("messages"), it.getBoolean("more")) }
            }
            "send" -> {
                val key = SteamChatRepository.Target.parse(args).key
                val local = args.getString("localId").also { require(it.startsWith("local:") && it.length < 160) }
                val pending = JSONObject().put("id", local).put("key", key).put("sender", account.id)
                    .put("text", args.getString("text")).put("timestamp", args.optLong("localTime", System.currentTimeMillis() / 1000))
                    .put("ordinal", Int.MAX_VALUE).put("pending", true)
                disk.merge(key, JSONArray().put(pending))
                disk.state(key, JSONObject().put("draft", ""))
                try { repo.send(args).also { disk.merge(key, JSONArray().put(it), remove = local) } }
                catch (error: Exception) {
                    pending.put("pending", false).put("failed", true).put("error", "DELIVERY_UNKNOWN")
                    disk.merge(key, JSONArray().put(pending)); throw error
                }
            }
            "discardPending" -> disk.merge(SteamChatRepository.Target.parse(args).key, JSONArray(), remove = args.getString("localId"))
            "deleteLocalMedia" -> disk.deleteLocal(SteamChatRepository.Target.parse(args).key, args.getString("id"))
            "ack" -> synchronized(lock) {
                checkSession(lease)
                val key = SteamChatRepository.Target.parse(args).key
                val timestamp = args.getLong("timestamp")
                // Persist first; network work runs on the read worker and may outlive this page.
                lease.reads.mark(key, timestamp, seenTimestamp = args.optLong("seenTimestamp", timestamp),
                    seenOrdinal = args.optLong("seenOrdinal", 0xffffffffL)).also {
                    disk.saveSummary(lease.reads.summary(disk.summary()))
                    notifications.cancelThrough(account.id, key, timestamp)
                    lease.readSync.wake()
                    SteamChatDiagnostics.info("read.local.saved")
                }
            }
            "catalog" -> repo.catalog().also { synchronized(lock) { checkSession(lease); disk.saveCatalog(it) } }
            "catalogCache" -> disk.catalog()
            "chatTradeUrl" -> repo.tradeUrl()
            "chatProfileAppearance" -> repo.profileAppearance(args.getString("id"))
            "reaction", "report", "deleteMessage", "typing" -> SteamChatMessageActions(repo).execute(operation, args)
            "groupInfo" -> SteamChatGroupAdmin(repo).snapshot(args)
            "groupAction" -> SteamChatGroupAdmin(repo).execute(args)
            else -> throw SteamChatFailure("INVALID_OPERATION")
        }
        repo.checkAccount()
        return result
    }


    private fun saveSummary(lease: Session, value: JSONObject) {
        value.optJSONArray("sessions")?.let(lease.reads::observe)
        value.optJSONArray("groups")?.let { groups ->
            for (index in 0 until groups.length()) groups.getJSONObject(index).optJSONArray("rooms")?.let(lease.reads::observe)
        }
        lease.disk.saveSummary(lease.reads.summary(value))
    }

    private fun handleEvent(lease: Session, event: JSONObject): Unit = synchronized(lock) {
        checkSession(lease)
        val repo = lease.repo
        val disk = lease.disk
        val type = event.optString("type")
        if (type == "disconnected") lease.voice.connectionLost()
        if (type == "connected") lease.readSync.wake()
        if (type == "read") {
            val key = event.getString("key")
            val timestamp = event.getLong("timestamp")
            lease.reads.mark(key, timestamp, confirmed = true)
            disk.saveSummary(lease.reads.summary(disk.summary()))
            notifications.cancelThrough(repo.account.id, key, timestamp)
            SteamChatDiagnostics.info("read.echo.applied")
            return
        }
        if (type == "reaction" || type == "modified") {
            val key = event.getString("key"); val messages = disk.thread(key).getJSONArray("messages")
            for (index in 0 until messages.length()) {
                val row = messages.getJSONObject(index)
                if (type == "modified") {
                    val changes = event.getJSONArray("changes")
                    for (changeIndex in 0 until changes.length()) {
                        val change = changes.getJSONObject(changeIndex)
                        if (row.optLong("timestamp") == change.optLong("timestamp") && row.optLong("ordinal") == change.optLong("ordinal")) row.put("deleted", change.optBoolean("deleted"))
                    }
                } else if (row.optLong("timestamp") == event.optLong("timestamp") && row.optLong("ordinal") == event.optLong("ordinal")) {
                    val reactions = row.optJSONArray("reactions") ?: JSONArray()
                    var reaction: JSONObject? = null
                    for (reactionIndex in 0 until reactions.length()) {
                        val value = reactions.getJSONObject(reactionIndex)
                        if (value.optLong("type") == event.optLong("reactionType") && value.optString("name") == event.optString("name")) reaction = value
                    }
                    val value = reaction ?: JSONObject().put("type", event.optLong("reactionType")).put("name", event.optString("name")).also { reactions.put(it) }
                    val reactors = value.optJSONArray("reactors") ?: JSONArray()
                    val people = (0 until reactors.length()).map { reactors.getString(it) }.filter { it != event.optString("reactor") }.toMutableList()
                    if (event.optBoolean("add")) people.add(event.getString("reactor"))
                    value.put("reactors", JSONArray(people)).put("count", people.size).put("selected", repo.account.id in people)
                    row.put("reactions", reactions)
                }
            }
            disk.merge(key, messages); return
        }
        if (type != "message") return
        val message = event.getJSONObject("message"); val key = message.getString("key")
        val prior = disk.thread(key).getJSONArray("messages")
        val known = (0 until prior.length()).any { prior.getJSONObject(it).optString("id") == message.getString("id") }
        disk.merge(key, JSONArray().put(message))
        if (known || message.optLong("timestamp") <= lease.reads.readThrough(key) ||
            message.optString("sender") == repo.account.id || (lease.foreground && lease.visibleThread == key) ||
            disk.preferences().optJSONObject(key)?.optBoolean("muted") == true) return
        notifications.post(repo.account.id, key, message)
    }

    fun stop() = synchronized(lock) {
        session?.let { revoked = it.account; revokedEpoch = it.epoch }
        clearSession()
    }

    fun voiceForeground(value: Boolean) { session?.voice?.foreground(value) }

    private fun clearSession() {
        val old = session
        session = null
        old?.readSync?.close()
        old?.voice?.close()
        old?.repo?.close()
        notifications.clear()
        if (old != null) SteamChatDiagnostics.info("runtime.session.closed")
    }
}
