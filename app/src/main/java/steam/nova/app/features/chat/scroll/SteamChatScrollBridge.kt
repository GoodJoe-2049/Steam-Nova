package steam.nova.app.features.chat.scroll

import android.app.Activity
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ScrollView
import androidx.core.view.isVisible
import org.json.JSONArray
import org.json.JSONObject
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import steam.nova.app.features.chat.network.SteamChatAccount
import java.lang.ref.WeakReference

/** Mounts inside a dedicated empty RN slot; never adds a window overlay or root provider. */
internal object SteamChatScrollBridge {
    private val main = Handler(Looper.getMainLooper())
    private val owners = hashMapOf<String, Layer>()
    private const val EVENT = "SteamNovaChatScroll"
    private const val PREFIX = "millennium.chat.scroll."

    fun handle(activity: Activity?, reactContext: Any, args: JSONObject, current: () -> Boolean,
               complete: (Result<JSONObject>) -> Unit) {
        main.post {
            complete(runCatching {
                val account = args.getString("account")
                SteamChatAccount.current(account)
                check(current())
                val owner = args.getString("owner")
                require(owner.matches(Regex("[A-Za-z0-9_-]{1,100}")))
                val action = args.getString("action")
                if (action == "detach") {
                    owners[owner]?.takeIf { it.account == account }?.close()
                } else {
                    val host = requireNotNull(activity)
                    check(!host.isFinishing && !host.isDestroyed)
                    var layer = owners[owner]
                    if (layer == null) {
                        require(action == "update")
                        val slot = requireNotNull(host.window.decorView.findViewWithTag<View>("$PREFIX$owner.slot") as? ViewGroup)
                        val viewport = requireNotNull(host.window.decorView.findViewWithTag<View>("$PREFIX$owner.viewport") as? ViewGroup)
                        val tagged: View = requireNotNull(viewport.findViewWithTag("$PREFIX$owner.list"))
                        val scroll = requireNotNull(findScroll(tagged))
                        layer = Layer(owner, account, slot, viewport, scroll, reactContext, current)
                        owners[owner] = layer
                    }
                    require(layer.account == account)
                    layer.current = current
                    when (action) {
                        "update" -> layer.update(args)
                        "latest" -> layer.latest(args.optString("latest"))
                        else -> error("Unknown scroll action")
                    }
                }
                JSONObject()
            })
        }
    }

    private fun findScroll(view: View): ScrollView? {
        if (view is ScrollView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findScroll(view.getChildAt(index))?.let { return it }
        return null
    }

    fun clear(accountToKeep: String? = null) { main.post { owners.values.toList().filter { it.account != accountToKeep }.forEach { it.close() } } }
    fun clearActivity(activity: Activity) { main.post { owners.values.toList().filter { it.root === activity.window.decorView.rootView }.forEach { it.close() } } }

    private class Layer(
        val owner: String, val account: String, private val slot: ViewGroup,
        private val viewport: ViewGroup, private val list: ScrollView, reactContext: Any,
        var current: () -> Boolean,
    ) : ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        val root: View get() = slot.rootView
        private val context = WeakReference(reactContext)
        private val emitterType = Class.forName($$"com.facebook.react.modules.core.DeviceEventManagerModule$RCTDeviceEventEmitter", false, reactContext.javaClass.classLoader)
        private val emitMethod = emitterType.getMethod("emit", String::class.java, Any::class.java)
        private val control = SteamChatJumpControl(slot.context, list) { emit("jump") }
        private val transition = SteamChatScrollTransition(viewport, list, ::inspect) {
            emit("settled"); signature = ""; inspect()
        }
        private val born = SystemClock.uptimeMillis()
        private var active = false
        private var blocked = false
        private var ready = false
        private var reduced = false
        private var closed = false
        private var count = 0
        private var latestId = ""
        private var canShow = false
        private var shownByScroll = false
        private var totalDy = 0f
        private var lastY = list.scrollY
        private var signature = ""
        private var snapshot = emptyMap<String, Rect>()

        init {
            slot.clipChildren = false
            slot.clipToPadding = false
            slot.addView(control, ViewGroup.LayoutParams(-1, -1))
            slot.addOnAttachStateChangeListener(this)
            slot.viewTreeObserver.addOnPreDrawListener(this)
        }

        fun update(args: JSONObject) {
            val wasActive = active && !blocked && ready
            active = args.optBoolean("active")
            blocked = args.optBoolean("blocked")
            ready = args.optBoolean("ready")
            reduced = args.optBoolean("reduced")
            latestId = args.optString("latest")
            val next = args.optInt("count").coerceAtLeast(0)
            val atBottom = list.scrollY <= 5 * list.resources.displayMetrics.density
            if (!transition.running && next > count && !atBottom) { canShow = true; shownByScroll = false }
            if (args.optBoolean("restoreAway") && !ready) { canShow = true; shownByScroll = false }
            count = next
            control.update(count, reduced, SystemClock.uptimeMillis() - born >= 150)
            control.loading(args.optBoolean("loading"))
            if (!wasActive && active && ready && !atBottom && (count > 0 || args.optBoolean("restoreAway"))) {
                canShow = true; shownByScroll = false
            }
            if (!active || blocked) transition.cancel()
            if (wasActive != (active && !blocked && ready)) signature = ""
            inspect()
        }

        override fun onPreDraw(): Boolean {
            runCatching { inspect() }.onFailure { SteamChatDiagnostics.failure("scroll.inspect", it); close() }
            return true
        }

        private fun rows(): Map<String, Rect> {
            val result = linkedMapOf<String, Rect>()
            val clip = Rect()
            if (!viewport.getGlobalVisibleRect(clip)) return result
            val prefix = "$PREFIX$owner.row."
            fun visit(view: View) {
                val tag = view.tag as? String
                if (tag != null && tag.startsWith(prefix)) {
                    val visible = Rect()
                    if (view.isShown && view.getGlobalVisibleRect(visible) && visible.intersect(clip) && visible.width() > 0 && visible.height() > 0) {
                        val point = IntArray(2)
                        view.getLocationOnScreen(point)
                        result[tag.substring(prefix.length)] = Rect(point[0], point[1], point[0] + view.width, point[1] + view.height)
                    }
                    return
                }
                if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
            visit(list)
            return result
        }

        private fun inspect() {
            if (closed) return
            if (!current()) { close(); return }
            if (control.width != slot.width || control.height != slot.height || control.isLayoutRequested) {
                control.measure(View.MeasureSpec.makeMeasureSpec(slot.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(slot.height, View.MeasureSpec.EXACTLY))
                control.layout(0, 0, slot.width, slot.height)
            }
            if (!active || !slot.isShown || !slot.hasWindowFocus()) {
                transition.cancel(); control.show(value = false, animated = false); signature = ""; return
            }
            val dy = (lastY - list.scrollY) / list.resources.displayMetrics.density
            lastY = list.scrollY
            if (list.alpha == 0f && transition.running) return
            snapshot = rows()
            val latestVisible = latestId.isNotEmpty() && snapshot.containsKey(latestId)
            val atBottom = list.scrollY <= 5 * list.resources.displayMetrics.density
            if (!transition.running) {
                if (latestVisible && dy >= 0) canShow = false
                else if (dy > 0 && !canShow) {
                    totalDy += dy
                    if (totalDy > 100) { totalDy = 0f; canShow = true; shownByScroll = true }
                } else if (dy < 0 && canShow && shownByScroll) {
                    totalDy += dy
                    if (totalDy < -100) { totalDy = 0f; canShow = false; shownByScroll = false }
                }
            }
            control.show(canShow && !blocked && !transition.running, SystemClock.uptimeMillis() - born >= 150)
            if (dy != 0f && control.isVisible) control.invalidate()
            if (!ready || blocked || transition.running) { signature = ""; return }
            val next = "${snapshot.keys.joinToString("|")}:$atBottom"
            if (next != signature) {
                signature = next
                emit("visible", JSONObject().put("ids", JSONArray(snapshot.keys.toList())).put("atBottom", atBottom))
            }
        }

        fun latest(destination: String) {
            if (closed || !active || blocked || !ready || transition.running) { emit("settled"); return }
            latestId = destination
            canShow = false; shownByScroll = false; totalDy = 0f
            control.show(value = false)
            try { transition.start(reduced, rows(), ::rows) { latestId } }
            catch (error: Exception) { transition.cancel(); throw error }
        }

        private fun emit(type: String, fields: JSONObject = JSONObject()) {
            if (closed || !current()) return
            val react = context.get() ?: return
            runCatching {
                val emitter = react.javaClass.getMethod("getJSModule", Class::class.java).invoke(react, emitterType)
                emitMethod.invoke(emitter, EVENT, fields.put("type", type).put("owner", owner).toString())
            }.onFailure { SteamChatDiagnostics.failure("scroll.event", it) }
        }

        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) { close() }

        fun close() {
            if (closed) return
            closed = true
            transition.cancel()
            control.close()
            if (slot.viewTreeObserver.isAlive) slot.viewTreeObserver.removeOnPreDrawListener(this)
            slot.removeOnAttachStateChangeListener(this)
            slot.removeView(control)
            if (owners[owner] === this) owners.remove(owner)
        }
    }
}
