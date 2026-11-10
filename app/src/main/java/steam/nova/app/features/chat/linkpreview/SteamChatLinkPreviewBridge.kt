package steam.nova.app.features.chat.linkpreview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import steam.nova.app.features.chat.network.SteamChatAccount
import java.lang.ref.WeakReference

internal object SteamChatLinkPreviewBridge {
    private val main = Handler(Looper.getMainLooper())
    private val layers = mutableMapOf<String, WeakReference<Layer>>()
    private val sessions = mutableListOf<WeakReference<Session>>()

    private fun session(host: Activity): Session {
        sessions.removeAll { it.get() == null }
        return sessions.firstNotNullOfOrNull { it.get()?.takeIf { value -> value.activity.get() === host } }
            ?: Session(host).also { sessions.add(WeakReference(it)) }
    }

    fun handle(activity: Activity?, reactContext: Any, args: JSONObject, current: () -> Boolean,
               complete: (Result<JSONObject>) -> Unit) {
        main.post {
            complete(runCatching {
                val owner = args.getString("owner")
                require(owner.matches(Regex("link-preview-[A-Za-z0-9_-]{1,100}")))
                val account = args.getString("account")
                SteamChatAccount.current(account)
                layers.entries.removeAll { it.value.get() == null }
                val previous = layers[owner]?.get()
                if (args.optBoolean("detach")) {
                    previous?.takeIf { it.account == account }?.unbind()
                    return@runCatching JSONObject().put("mounted", false)
                }
                check(current())
                if (args.optBoolean("update")) {
                    previous?.takeIf { it.account == account }?.update(args.optBoolean("active"))
                    return@runCatching JSONObject().put("mounted", previous?.account == account)
                }
                val host = requireNotNull(activity)
                check(!host.isFinishing && !host.isDestroyed)
                val slot = host.window.decorView.findViewWithTag<View>(owner) as? ViewGroup
                if (slot == null || !slot.isAttachedToWindow || slot.width == 0 || slot.height == 0)
                    return@runCatching JSONObject().put("mounted", false)
                val embed = requireNotNull(SteamChatLinkPreviewRegistry.resolve(host, args.getString("url")))
                val cacheKey = args.getString("cacheKey")
                require(cacheKey.length in 1..2048)
                val session = session(host)
                val key = "$account\n$cacheKey"
                if (previous !== session.entries[key]) previous?.unbind()
                val cached = session.entries[key]
                val layer = cached?.takeIf { it.embed == embed && !args.optBoolean("retry") } ?: run {
                    cached?.close()
                    Layer(session, key, account, host, embed, reactContext).also { session.entries[key] = it }
                }
                layer.bind(owner, slot, reactContext)
                layer.update(args.optBoolean("active"))
                JSONObject().put("mounted", true).put("state", layer.state)
            })
        }
    }

    fun clear(accountToKeep: String? = null) {
        main.post { sessions.mapNotNull { it.get() }.forEach { session ->
            session.entries.values.filter { it.account != accountToKeep }.forEach { it.close() }
        } }
    }

    fun clearActivity(activity: Activity) {
        main.post { sessions.mapNotNull { it.get() }.filter { it.activity.get() === activity }.forEach { it.close() } }
    }

    fun foreground(activity: Activity, enabled: Boolean) {
        main.post { sessions.mapNotNull { it.get() }.filter { it.activity.get() === activity }.forEach { session ->
            session.foreground = enabled
            session.entries.values.toList().forEach { it.inspect() }
        } }
    }

    private class Session(host: Activity) : View.OnAttachStateChangeListener {
        val activity = WeakReference(host)
        val entries = linkedMapOf<String, Layer>()
        var foreground = true
        private val root = WeakReference(host.window.decorView)

        init { host.window.decorView.addOnAttachStateChangeListener(this) }

        fun trim() {
            val idle = entries.values.filter { !it.running && it.hasWeb }.sortedBy { it.lastUsed }
            idle.take((idle.size - 8).coerceAtLeast(0)).forEach { layer ->
                if (layer.bound) layer.evict() else layer.close()
            }
            entries.values.filter { !it.bound && !it.hasWeb }.forEach { it.close() }
        }

        fun close() {
            entries.values.toList().forEach { it.close() }
            root.get()?.removeOnAttachStateChangeListener(this)
            sessions.removeAll { it.get() == null || it.get() === this }
        }

        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) { close() }
    }

    private class Layer(
        private val session: Session,
        private val cacheKey: String,
        val account: String,
        host: Activity,
        val embed: SteamChatLinkPreviewRegistry.Embed,
        reactContext: Any,
    ) : ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        val activity = WeakReference(host)
        private var react = WeakReference(reactContext)
        private var slot = WeakReference<ViewGroup>(null)
        private var owner = ""
        private val gestureMethods = runCatching {
            val type = Class.forName("com.facebook.react.uimanager.events.NativeGestureUtil", false, reactContext.javaClass.classLoader)
            type.getMethod("notifyNativeGestureStarted", View::class.java, MotionEvent::class.java) to
                    type.getMethod("notifyNativeGestureEnded", View::class.java, MotionEvent::class.java)
        }.onFailure { SteamChatDiagnostics.failure("linkPreview.gestures", it) }.getOrNull()
        private var web: WebView? = null
        private var observer: ViewTreeObserver? = null
        private var active = false
        private var closed = false
        private var failed = false
        var state = "idle"
            private set
        var running = false
            private set
        var lastUsed = SystemClock.uptimeMillis()
            private set
        val hasWeb get() = web != null
        val bound get() = slot.get() != null
        private val timeout = Runnable { fail("timeout") }

        fun bind(nextOwner: String, nextSlot: ViewGroup, reactContext: Any) {
            if (owner != nextOwner || slot.get() !== nextSlot) {
                detachSlot()
                owner = nextOwner
                slot = WeakReference(nextSlot)
                nextSlot.addOnAttachStateChangeListener(this)
                web?.let { nextSlot.addView(it, ViewGroup.LayoutParams(-1, -1)) }
            }
            react = WeakReference(reactContext)
            layers[owner] = WeakReference(this)
            observe()
        }

        fun unbind() {
            if (closed) return
            active = false
            setRunning(false)
            detachSlot()
            session.trim()
        }

        private fun detachSlot() {
            unobserve()
            slot.get()?.let { previous ->
                previous.removeOnAttachStateChangeListener(this)
                web?.let { previous.removeView(it) }
            }
            slot.clear()
            if (layers[owner]?.get() === this) layers.remove(owner)
        }

        fun update(enabled: Boolean) {
            active = enabled
            inspect()
        }

        private fun observe() {
            if (observer?.isAlive == true) return
            observer = slot.get()?.viewTreeObserver?.also { it.addOnPreDrawListener(this) }
        }

        fun inspect() {
            if (closed) return
            val bounds = Rect()
            val container = slot.get()
            val visible = active && session.foreground && container != null && container.isAttachedToWindow &&
                    container.isShown && container.hasWindowFocus() && container.getGlobalVisibleRect(bounds) && !bounds.isEmpty
            if (visible && !failed && web == null) {
                runCatching { create(container) }.onFailure {
                    SteamChatDiagnostics.failure("linkPreview.create", it)
                    fail("create")
                }
            }
            setRunning(visible && web != null && !failed)
            if (container == null) return
            web?.let { view ->
                if (view.width != container.width || view.height != container.height || view.isLayoutRequested) {
                    view.measure(View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(container.height, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, container.width, container.height)
                }
            }
        }

        private fun setRunning(enabled: Boolean) {
            if (running == enabled) return
            running = enabled
            main.removeCallbacks(timeout)
            web?.let { view ->
                if (enabled) {
                    lastUsed = SystemClock.uptimeMillis()
                    view.onResume()
                    if (state == "loading") main.postDelayed(timeout, 30_000)
                } else {
                    SteamChatLinkPreviewPage.pause(embed)?.let { view.evaluateJavascript(it, null) }
                    view.onPause()
                }
            }
            if (!enabled) session.trim()
        }

        fun evict() {
            release()
            if (!failed) emit("idle")
        }

        @SuppressLint("SetJavaScriptEnabled")
        private fun create(container: ViewGroup) {
            val view = SteamChatLinkPreviewTouch(container.context).apply {
                nativeStarted = { target, event -> runCatching { gestureMethods?.first?.invoke(null, target, event) } }
                nativeEnded = { target, event -> runCatching { gestureMethods?.second?.invoke(null, target, event) } }
            }
            web = view
            view.setBackgroundColor(Color.TRANSPARENT)
            view.isVerticalScrollBarEnabled = false
            view.isHorizontalScrollBarEnabled = false
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                mediaPlaybackRequiresUserGesture = true
                textZoom = 100
                setSupportMultipleWindows(false)
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
            view.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    val resources = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
                    if (!closed && !failed && request.origin.toString().trimEnd('/') == embed.origin && resources.isNotEmpty())
                        request.grant(resources.toTypedArray()) else request.deny()
                }
            }
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme.orEmpty()
                    if (!request.isForMainFrame && scheme in setOf("https", "http")) return false
                    if (request.url.toString() == embed.src) return false
                    if (request.hasGesture() && (scheme in setOf("https", "http") || scheme in embed.externalSchemes)) {
                        activity.get()?.let { host ->
                            runCatching { host.startActivity(Intent(Intent.ACTION_VIEW, request.url).addCategory(Intent.CATEGORY_BROWSABLE)) }
                        }
                    }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (web === view && !closed && !failed && url == embed.src) {
                        main.removeCallbacks(timeout)
                        emit("loaded")
                    }
                }

                private fun isDocument(request: WebResourceRequest) = request.isForMainFrame ||
                        request.url.toString().substringBefore('?') == embed.src.substringBefore('?')

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (web === view && isDocument(request)) fail("network")
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (web === view && isDocument(request) && response.statusCode >= 400) fail("http")
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    if (web === view) fail("renderer")
                    return true
                }
            }
            container.addView(view, ViewGroup.LayoutParams(-1, -1))
            emit("loading")
            view.loadUrl(embed.src)
        }

        private fun fail(reason: String) {
            if (closed || failed) return
            failed = true
            SteamChatDiagnostics.info("linkPreview.failed", "provider=${embed.provider} kind=${embed.kind} reason=$reason")
            emit("failed")
            // A WebView callback must return before that WebView is destroyed.
            main.post { if (failed) release() }
        }

        private fun release() {
            main.removeCallbacks(timeout)
            val view = web ?: return
            web = null
            running = false
            runCatching { view.stopLoading(); view.onPause() }
                .onFailure { SteamChatDiagnostics.failure("linkPreview.pause", it) }
            (view.parent as? ViewGroup)?.removeView(view)
            runCatching { view.removeAllViews(); view.destroy() }
                .onFailure { SteamChatDiagnostics.failure("linkPreview.destroy", it) }
        }

        private fun emit(state: String) {
            this.state = state
            if (closed) return
            if (!bound) return
            val context = react.get() ?: return
            runCatching {
                val type = Class.forName($$"com.facebook.react.modules.core.DeviceEventManagerModule$RCTDeviceEventEmitter", false, context.javaClass.classLoader)
                val emitter = context.javaClass.getMethod("getJSModule", Class::class.java).invoke(context, type)
                type.getMethod("emit", String::class.java, Any::class.java).invoke(emitter, "SteamNovaChatLinkPreview",
                    JSONObject().put("owner", owner).put("state", state).toString())
            }.onFailure { SteamChatDiagnostics.failure("linkPreview.event", it) }
        }

        override fun onPreDraw(): Boolean {
            runCatching { inspect() }.onFailure {
                SteamChatDiagnostics.failure("linkPreview.layout", it)
                fail("layout")
            }
            return true
        }
        override fun onViewAttachedToWindow(view: View) { observe(); inspect() }
        override fun onViewDetachedFromWindow(view: View) { unobserve(); setRunning(false) }

        private fun unobserve() {
            observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
            observer = null
        }

        fun close() {
            if (closed) return
            closed = true
            detachSlot()
            release()
            if (session.entries[cacheKey] === this) session.entries.remove(cacheKey)
        }
    }
}
