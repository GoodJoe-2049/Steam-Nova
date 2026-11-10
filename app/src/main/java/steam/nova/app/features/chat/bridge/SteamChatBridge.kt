package steam.nova.app.features.chat.bridge

import steam.nova.app.features.chat.linkpreview.SteamChatLinkPreviewBridge
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import steam.nova.app.features.chat.ui.SteamChatTradeScript
import steam.nova.app.features.chat.ui.SteamChatThreadFonts
import steam.nova.app.features.chat.ui.SteamChatComposer
import steam.nova.app.features.chat.runtime.SteamChatRuntime
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import steam.nova.app.features.chat.media.SteamChatMediaIconBridge
import steam.nova.app.features.chat.media.SteamChatMediaMenuBridge
import steam.nova.app.features.chat.media.viewer.SteamChatMediaViewerBridge
import steam.nova.app.features.chat.effects.SteamChatRoomEffects
import steam.nova.app.features.chat.scroll.SteamChatScrollBridge
import android.view.WindowManager
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import steam.nova.app.features.chat.media.SteamChatAttachments
import steam.nova.app.features.chat.media.SteamChatMediaTransfer
import steam.nova.app.features.chat.network.SteamChatAccount
import steam.nova.app.features.chat.network.SteamChatFailure
import steam.nova.app.features.steamguard.data.SteamSessionData
import steam.nova.app.features.ui.settings.SteamModuleSettings
import io.github.libxposed.api.XposedModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.reflect.Method
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Private in-process bridge. Commands never become Android intents or public deep links. */
internal object SteamChatBridge {
    val prefix = "millennium-chat-${UUID.randomUUID().toString().replace("-", "")}://request?payload="
    @Volatile var available = false
        private set
    private val lock = Any()
    private val imeWindowLock = Any()
    private data class ChatImeWindow(val originalMode: Int, val owners: MutableSet<String>)
    private val chatImeWindows = WeakHashMap<Activity, ChatImeWindow>()
    private var activeAccount: SteamChatAccount? = null
    private var lifecycleInstalled = false
    private data class NotificationTarget(val account: String, val key: String)
    @Volatile private var notificationTarget: NotificationTarget? = null
    // current() constructs this only with applicationContext; no Activity or ReactContext is retained.
    @SuppressLint("StaticFieldLeak")
    private var attachments: SteamChatAttachments? = null
    @SuppressLint("StaticFieldLeak") // Application context only.
    private var mediaTransfer: SteamChatMediaTransfer? = null
    // Constructed exclusively with Steam's applicationContext.
    @SuppressLint("StaticFieldLeak")
    private var runtime: SteamChatRuntime? = null
    private var picker: Picker? = null
    private var epochAccount = ""
    private var latestEpoch = -1L
    private val hooked = hashSetOf<Method>()
    private val main = Handler(Looper.getMainLooper())
    private val io = ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS, ArrayBlockingQueue(48)) { task ->
        Thread(task, "MillenniumChatIO").apply { isDaemon = true }
    }
    private val local = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(48)) { task ->
        Thread(task, "MillenniumChatState").apply { isDaemon = true }
    }
    private val gameInfo = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(32)) { task ->
        Thread(task, "MillenniumChatGames").apply { isDaemon = true }
    }
    // Font/CDN and avatar-decoration requests must not occupy message/history workers.
    private val appearance = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(64)) { task ->
        Thread(task, "MillenniumChatAppearance").apply { isDaemon = true }
    }
    private val mediaWorkers = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue(48)) { task ->
        Thread(task, "SteamNovaMedia").apply { isDaemon = true }
    }
    // Slow HTTP/CM requests must not queue ahead of visibility changes or incoming events.
    private val localOperations = setOf("cache", "threadCache", "threadState", "preferences", "visibility", "events",
        "backgroundStatus", "backgroundEnable", "backgroundDisable", "catalogCache", "discardPending", "uploadProgress", "pause", "voice", "voiceState", "ack",
        "mediaStatus", "mediaCancel", "deleteLocalMedia")
    private data class Picker(val account: String, val component: String, val complete: (JSONObject) -> Unit)
    private const val REQUEST = 0x6d43

    fun install(module: XposedModule, loader: ClassLoader, context: () -> Context?) {
        if (available) return
        SteamChatDiagnostics.install { priority, message -> module.log(priority, "MillenniumChat", message) }
        SteamChatDiagnostics.info("bridge.install.begin")
        val intentClass = Class.forName("com.facebook.react.modules.intent.IntentModule", false, loader)
        val promiseClass = Class.forName("com.facebook.react.bridge.Promise", false, loader)
        val resolve = promiseClass.getMethod("resolve", Any::class.java)
        val open = intentClass.getDeclaredMethod("openURL", String::class.java, promiseClass)
        val getReactContext = findMethod(intentClass, "getReactApplicationContext")
        module.hook(open).setId("millennium.chat.bridge").intercept { chain ->
            val url = chain.args[0] as? String
            if (url == null || !url.startsWith(prefix)) return@intercept chain.proceed()
            val promise = chain.args[1]
            var diagnosticOperation = "unparsed"
            val complete: (JSONObject) -> Unit = { response ->
                SteamChatDiagnostics.info("bridge.response.$diagnosticOperation",
                    "ok=${response.optBoolean("ok")} code=${SteamChatDiagnostics.operation(response.optString("error", "NONE"))}",
                    SteamChatDiagnostics.quiet(diagnosticOperation))
                runCatching { resolve.invoke(promise, response.toString()) }
                    .onFailure { SteamChatDiagnostics.failure("bridge.resolve", it) }
            }
            try {
                require(url.length <= 128 * 1024)
                val args = JSONObject(Uri.decode(url.substring(prefix.length)))
                val app = context() ?: throw SteamChatFailure("NOT_READY")
                runCatching { SteamChatRoomEffects.install(module, loader, app) }
                    .onFailure {
                        if (args.optString("operation").startsWith("roomEffects"))
                            SteamChatDiagnostics.effectFailure("install", it, publicResource = true)
                    }
                if (!SteamModuleSettings.isBuiltInChatEnabled(app)) {
                    synchronized(lock) {
                        attachments?.clear()
                        mediaTransfer?.clear(); SteamChatMediaViewerBridge.clear(); SteamChatMediaIconBridge.clear(); SteamChatMediaMenuBridge.clear(); SteamChatLinkPreviewBridge.clear(); SteamChatComposer.clear()
                        runtime?.stop(); activeAccount = null; notificationTarget = null; SteamChatRoomEffects.clear(); SteamChatScrollBridge.clear()
                    }
                    throw SteamChatFailure("DISABLED")
                }
                val account = args.getString("account")
                val operation = args.getString("operation")
                diagnosticOperation = SteamChatDiagnostics.operation(operation)
                val epoch = args.getLong("epoch")
                SteamChatDiagnostics.info("bridge.request.$diagnosticOperation", "epoch=$epoch", SteamChatDiagnostics.quiet(operation))
                require(epoch >= 0)
                SteamChatAccount.current(account)
                synchronized(lock) {
                    if (epochAccount != account) { epochAccount = account; latestEpoch = -1 }
                    if (epoch < latestEpoch) throw SteamChatFailure("STALE_REQUEST")
                    latestEpoch = epoch
                }
                installLifecycle(module, app)
                val currentActivity = runCatching {
                    val reactContext = getReactContext.invoke(chain.thisObject)
                    reactContext.javaClass.getMethod("getCurrentActivity").invoke(reactContext) as? Activity
                }.getOrNull()
                if (currentActivity != null) main.post { watchNotificationIntent(module, currentActivity) }
                when (operation) {
                    "chatComposer" -> {
                        val reactContext = requireNotNull(getReactContext.invoke(chain.thisObject))
                        SteamChatComposer.handle(currentActivity, reactContext, args,
                            { synchronized(lock) { epochAccount == account && latestEpoch == epoch } }) { result ->
                            result.fold({ complete(success(it)) }, { complete(failure(it)) })
                        }
                    }
                    "linkPreview" -> {
                        val reactContext = requireNotNull(getReactContext.invoke(chain.thisObject))
                        SteamChatLinkPreviewBridge.handle(currentActivity, reactContext, args,
                            { synchronized(lock) { epochAccount == account && latestEpoch == epoch } }) { result ->
                            result.fold({ complete(success(it)) }, { complete(failure(it)) })
                        }
                    }
                    "mediaMenu" -> {
                        val reactContext = requireNotNull(getReactContext.invoke(chain.thisObject))
                        SteamChatMediaMenuBridge.handle(currentActivity, reactContext, args) { result ->
                            result.fold({ complete(success(it)) }, { complete(failure(it)) })
                        }
                    }
                    "mediaIcon" -> SteamChatMediaIconBridge.handle(currentActivity, args) { result ->
                        result.fold({ complete(success(it)) }, { complete(failure(it)) })
                    }
                    "mediaViewer" -> {
                        val reactContext = requireNotNull(getReactContext.invoke(chain.thisObject))
                        current(app, account)
                        SteamChatMediaViewerBridge.handle(currentActivity, reactContext, requireNotNull(mediaTransfer), args) { result ->
                            result.fold({ complete(success(it)) }, { complete(failure(it)) })
                        }
                    }
                    "chatScroll" -> {
                        val reactContext = getReactContext.invoke(chain.thisObject)
                            ?: throw SteamChatFailure("NOT_READY")
                        SteamChatScrollBridge.handle(currentActivity, reactContext, args,
                            { synchronized(lock) { epochAccount == account && latestEpoch == epoch } }) { result ->
                            result.fold({ complete(success(it)) }, { complete(failure(it)) })
                        }
                    }
                    "roomEffectsCatalog", "roomEffectsAttach", "roomEffectsPlay", "roomEffectsDetach" ->
                        SteamChatRoomEffects.handle(app, currentActivity, args, { synchronized(lock) { epochAccount == account && latestEpoch == epoch } }) { result ->
                            result.fold({ complete(success(it)) }, { complete(failure(it)) })
                        }
                    "chatIme" -> {
                        val activity = currentActivity ?: throw SteamChatFailure("NOT_READY")
                        val enabled = args.optBoolean("enabled")
                        val owner = args.optString("owner")
                        require(owner.matches(Regex("chat-ime-[A-Za-z0-9]+")))
                        main.post {
                            runCatching { setChatImeMode(activity, owner, enabled) }
                                .fold({ complete(success(JSONObject())) }, { complete(failure(it)) })
                        }
                    }
                    "chatFonts" -> appearance.execute {
                        runCatching { SteamChatThreadFonts.load(app, loader) }
                            .fold({ complete(success(it)) }, { complete(failure(it)) })
                    }
                    "tradeUrlField" -> main.post {
                        runCatching { SteamChatTradeScript.configureUrlField(currentActivity, args) }
                            .fold({ complete(success(it)) }, { complete(failure(it)) })
                    }
                    "copy" -> main.post {
                        runCatching { app.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("Steam", args.getString("text").take(16000))) }
                            .fold({ complete(success(JSONObject())) }, { complete(failure(it)) })
                    }
                    "pickImage", "pickFile" -> {
                        val reactContext = getReactContext.invoke(chain.thisObject)
                        val activity = reactContext.javaClass.getMethod("getCurrentActivity").invoke(reactContext) as? Activity
                            ?: throw SteamChatFailure("NOT_READY")
                        main.post { pick(module, activity, account, operation == "pickImage", complete) }
                    }
                    else -> (when (operation) {
                        in localOperations -> local
                        "gameInfo" -> gameInfo
                        "chatProfileAppearance" -> appearance
                        "mediaInfo", "mediaDownload", "mediaExport", "mediaShare" -> mediaWorkers
                        else -> io
                    }).execute {
                        runCatching {
                            val active = synchronized(lock) {
                                if (epochAccount != account || latestEpoch != epoch) throw SteamChatFailure("STALE_REQUEST")
                                current(app, account)
                            }
                            val host = requireNotNull(runtime)
                            val media = requireNotNull(attachments)
                            val result = SteamChatDiagnostics.trace("runtime.execute.$diagnosticOperation", SteamChatDiagnostics.quiet(operation)) {
                                when (operation) {
                                    "groupAvatar" -> host.withRepository(active, args) { media.uploadAvatar(it, args) }
                                    "upload" -> host.withRepository(active, args) { media.upload(it, args) }
                                    "uploadProgress" -> media.progress(args.getString("selection"))
                                    "saveMedia" -> media.save(args)
                                    "mediaInfo", "mediaStatus", "mediaDownload", "mediaCancel", "mediaExport", "mediaShare" ->
                                        requireNotNull(mediaTransfer).execute(account, args)
                                    else -> host.execute(active, args)
                                }
                            }
                            if (operation == "events" && result is org.json.JSONArray) {
                                synchronized(lock) {
                                    notificationTarget?.let { target ->
                                        notificationTarget = null
                                        if (target.account == account) result.put(JSONObject().put("type", "openThread").put("key", target.key))
                                    }
                                }
                            }
                            if (operation != "pause" && SteamChatAccount.current(account) != active) throw SteamChatFailure("ACCOUNT_CHANGED")
                            result
                        }.fold({ complete(success(it)) }, { complete(failure(it)) })
                    }
                }
            } catch (error: Exception) { complete(failure(error)) }
            null
        }
        available = true
        SteamChatDiagnostics.info("bridge.install.end")
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            SteamSessionData.json.collect {
                synchronized(lock) {
                    val active = activeAccount
                    if (active != null && runCatching { SteamChatAccount.current(active.id) == active }.getOrDefault(false).not()) {
                        activeAccount = null; attachments?.clear(); notificationTarget = null
                        mediaTransfer?.clear(); SteamChatMediaViewerBridge.clear(); SteamChatMediaIconBridge.clear(); SteamChatMediaMenuBridge.clear(); SteamChatLinkPreviewBridge.clear(); SteamChatComposer.clear()
                        runtime?.stop(); SteamChatRoomEffects.clear(); SteamChatScrollBridge.clear()
                    }
                    picker?.let { pending ->
                        if (runCatching { SteamChatAccount.current(pending.account) }.isFailure) {
                            picker = null
                            pending.complete(failure(SteamChatFailure("ACCOUNT_CHANGED")))
                        }
                    }
                }
            }
        }
    }

    private fun current(context: Context, account: String): SteamChatAccount = synchronized(lock) {
        val active = SteamChatAccount.current(account)
        if (activeAccount != active) { attachments?.clear(); mediaTransfer?.clear(active.id); SteamChatMediaViewerBridge.clear(active.id); SteamChatMediaIconBridge.clear(active.id); SteamChatMediaMenuBridge.clear(active.id); SteamChatLinkPreviewBridge.clear(active.id); SteamChatComposer.clear(active.id); runtime?.stop(); SteamChatRoomEffects.clearOtherAccounts(active.id); SteamChatScrollBridge.clear(active.id); activeAccount = active }
        if (attachments == null) attachments = SteamChatAttachments(context.applicationContext)
        if (mediaTransfer == null) mediaTransfer = SteamChatMediaTransfer(context.applicationContext)
        if (runtime == null) runtime = SteamChatRuntime(context.applicationContext)
        active
    }

    @Suppress("DEPRECATION") // Retain adjustResize for the host Activity's pre-edge-to-edge window behavior.
    private fun setChatImeMode(activity: Activity, owner: String, enabled: Boolean) = synchronized(imeWindowLock) {
        val window = activity.window
        if (enabled) {
            val state = chatImeWindows.getOrPut(activity) {
                ChatImeWindow(window.attributes.softInputMode, mutableSetOf())
            }
            state.owners += owner
            val current = window.attributes.softInputMode
            val resized = (current and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (current != resized) window.setSoftInputMode(resized)
        } else {
            val state = chatImeWindows[activity] ?: return@synchronized
            state.owners -= owner
            if (state.owners.isEmpty()) {
                chatImeWindows.remove(activity)
                window.setSoftInputMode(state.originalMode)
            }
        }
    }

    private fun installLifecycle(module: XposedModule, context: Context) = synchronized(lock) {
        if (lifecycleInstalled) return@synchronized
        val application = context.applicationContext as? Application ?: return@synchronized
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private val started = java.util.Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
            override fun onActivityResumed(activity: Activity) {
                SteamChatMediaViewerBridge.foreground(activity, true)
                SteamChatLinkPreviewBridge.foreground(activity, true)
                started.add(activity)
                runtime?.voiceForeground(true)
                watchNotificationIntent(module, activity)
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) {
                started.add(activity)
                runtime?.voiceForeground(true)
            }
            override fun onActivityPaused(activity: Activity) { SteamChatMediaViewerBridge.foreground(activity, false); SteamChatLinkPreviewBridge.foreground(activity, false); SteamChatMediaMenuBridge.clearActivity(activity) }
            override fun onActivityStopped(activity: Activity) {
                started.remove(activity)
                if (started.isEmpty()) runtime?.voiceForeground(false)
            }
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) { SteamChatComposer.clearActivity(activity); SteamChatLinkPreviewBridge.clearActivity(activity); SteamChatScrollBridge.clearActivity(activity); SteamChatMediaViewerBridge.clearActivity(activity); SteamChatMediaMenuBridge.clearActivity(activity) }
        })
        lifecycleInstalled = true
    }

    private fun notificationIntent(intent: Intent?) {
        val key = intent?.getStringExtra("millennium_chat_target") ?: return
        val expected = intent.getStringExtra("millennium_chat_account")
        intent.removeExtra("millennium_chat_target"); intent.removeExtra("millennium_chat_account")
        if (expected == null || !key.matches(Regex("p:7656119\\d{10}|g:[0-9]+:[0-9]+"))) return
        synchronized(lock) {
            if (runCatching { SteamChatAccount.current(expected) }.isSuccess) notificationTarget = NotificationTarget(expected, key)
        }
    }

    private fun watchNotificationIntent(module: XposedModule, activity: Activity) {
        notificationIntent(activity.intent)
        runCatching {
            val method = findMethod(activity.javaClass, "onNewIntent", Intent::class.java)
            if (hooked.add(method)) module.hook(method).setId("millennium.chat.notification.${method.declaringClass.name}").intercept { chain ->
                notificationIntent(chain.args[0] as? Intent)
                chain.proceed()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun pick(module: XposedModule, activity: Activity, account: String, imagesOnly: Boolean, complete: (JSONObject) -> Unit) {
        runCatching {
            SteamChatAccount.current(account)
            current(activity, account)
            val method = findMethod(activity.javaClass, "onActivityResult", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Intent::class.java)
            if (hooked.add(method)) module.hook(method).setId("millennium.chat.picker.${method.declaringClass.name}").intercept { chain ->
                if (chain.args[0] != REQUEST) return@intercept chain.proceed()
                val pending = synchronized(lock) {
                    picker?.takeIf { it.component == (chain.thisObject as Activity).componentName.flattenToString() }
                        ?.also { picker = null }
                } ?: return@intercept chain.proceed()
                val result = chain.args[1] as Int
                val uri = (chain.args[2] as? Intent)?.data
                io.execute {
                    runCatching {
                        SteamChatAccount.current(pending.account)
                        if (result == Activity.RESULT_OK && uri != null) attachments!!.select(pending.account, uri)
                        else JSONObject().put("cancelled", true)
                    }.fold({ pending.complete(success(it)) }, { pending.complete(failure(it)) })
                }
                null
            }
            synchronized(lock) {
                if (picker != null) throw SteamChatFailure("BUSY")
                picker = Picker(account, activity.componentName.flattenToString(), complete)
            }
            activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = if (imagesOnly) "image/*" else "*/*"
                if (!imagesOnly) putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/mp4", "video/webm", "video/mpeg", "video/ogg", "application/zip", "application/x-zip-compressed"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, REQUEST)
        }.onFailure { synchronized(lock) { if (picker?.complete === complete) picker = null }; complete(failure(it)) }
    }

    private fun findMethod(type: Class<*>, name: String, vararg args: Class<*>): Method {
        var current: Class<*>? = type
        while (current != null) {
            val method = runCatching { current.getDeclaredMethod(name, *args) }.getOrNull()
            if (method != null) return method.apply { isAccessible = true }
            current = current.superclass
        }
        error("Missing host method")
    }
    private fun success(value: Any) = JSONObject().put("ok", true).put("value", value)
    private fun failure(error: Throwable): JSONObject {
        SteamChatDiagnostics.failure("bridge.request.failed", error)
        return JSONObject().put("ok", false).put("error", SteamChatDiagnostics.errorCode(error))
    }
}
