package steam.nova.app.features.chat.media

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import java.lang.ref.WeakReference
import org.json.JSONObject
import steam.nova.app.features.chat.network.SteamChatAccount

internal object SteamChatMediaMenuBridge {
    private val main = Handler(Looper.getMainLooper())
    private data class Open(
        val owner: String,
        val account: String,
        val activity: WeakReference<Activity>,
        val menu: WeakReference<SteamChatMediaMenu>,
    )
    private var active: Open? = null

    fun handle(activity: Activity?, reactContext: Any, args: JSONObject, complete: (Result<JSONObject>) -> Unit) = main.post {
        complete(runCatching {
            val owner = args.getString("owner")
            require(owner.matches(Regex("media-menu-[A-Za-z0-9_-]+")))
            val account = args.getString("account")
            SteamChatAccount.current(account)
            if (args.optBoolean("detach")) {
                close(active?.takeIf { it.owner == owner && it.account == account })
            } else {
                val host = requireNotNull(activity)
                check(!host.isFinishing && !host.isDestroyed)
                val tag = args.getString("sourceTag")
                require(tag.matches(Regex("media-card-[A-Za-z0-9_-]+")))
                val source: View = requireNotNull(host.window.decorView.findViewWithTag(tag))
                val rows = args.getJSONArray("rows")
                val icons = mapOf("copy" to "copy", "gallery" to "save", "downloads" to "download",
                    "share" to "share", "forward" to "forward", "delete" to "delete")
                require(rows.length() in 1..6)
                val entries = (0 until rows.length()).map { index ->
                    val row = rows.getJSONObject(index)
                    val action = row.getString("key")
                    SteamChatMediaMenu.Entry(action, icons.getValue(action), row.getString("label").take(128))
                }
                close(active)
                val context = WeakReference(reactContext)
                val emitterType = Class.forName($$"com.facebook.react.modules.core.DeviceEventManagerModule$RCTDeviceEventEmitter", false, reactContext.javaClass.classLoader)
                val emit = emitterType.getMethod("emit", String::class.java, Any::class.java)
                val menu = SteamChatMediaMenu(host, entries, viewer = false) { action ->
                    if (active?.owner == owner) active = null
                    context.get()?.let { react ->
                        runCatching {
                            val emitter = react.javaClass.getMethod("getJSModule", Class::class.java).invoke(react, emitterType)
                            emit.invoke(emitter, "SteamNovaChatMediaMenu", JSONObject().put("owner", owner)
                                .put("action", action.orEmpty()).toString())
                        }
                    }
                }
                active = Open(owner, account, WeakReference(host), WeakReference(menu))
                val density = host.resources.displayMetrics.density
                try {
                    menu.show(source, (args.optDouble("x", 0.0) * density).toFloat(),
                        (args.optDouble("y", 8.1) * density).toFloat(), args.optBoolean("longPress"))
                } catch (error: Exception) {
                    menu.dismissImmediately()
                    active = null
                    throw error
                }
            }
            JSONObject()
        })
    }

    fun clear(accountToKeep: String? = null) = main.post {
        close(active?.takeIf { it.account != accountToKeep })
    }

    fun clearActivity(activity: Activity) = main.post {
        close(active?.takeIf { it.activity.get() === activity })
    }

    private fun close(open: Open?) {
        if (open == null) return
        if (active === open) active = null
        open.menu.get()?.dismissImmediately()
    }
}
