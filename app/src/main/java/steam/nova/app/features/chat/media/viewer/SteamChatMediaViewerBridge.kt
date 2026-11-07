package steam.nova.app.features.chat.media.viewer

import android.app.Activity
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import steam.nova.app.features.chat.media.SteamChatMediaTransfer
import steam.nova.app.features.chat.network.SteamChatAccount
import java.lang.ref.WeakReference

internal object SteamChatMediaViewerBridge {
    private val main = Handler(Looper.getMainLooper())
    private data class Open(val owner: String, val viewer: SteamChatMediaViewer)
    private var active: Open? = null

    fun handle(activity: Activity?, reactContext: Any, transfer: SteamChatMediaTransfer, args: JSONObject,
        complete: (Result<JSONObject>) -> Unit) = main.post {
        complete(runCatching {
            val account = args.getString("account")
            SteamChatAccount.current(account)
            val owner = args.getString("owner")
            require(owner.matches(Regex("media-viewer-[A-Za-z0-9_-]+")))
            when (args.optString("action")) {
                "close" -> active?.takeIf { it.owner == owner && it.viewer.account == account }?.viewer?.closeImmediately()
                "resume" -> active?.takeIf { it.owner == owner && it.viewer.account == account }?.viewer?.actionFinished()
                "open" -> {
                    val host = requireNotNull(activity)
                    check(!host.isFinishing && !host.isDestroyed)
                    active?.viewer?.closeImmediately()
                    val context = WeakReference(reactContext)
                    val emitterType = Class.forName($$"com.facebook.react.modules.core.DeviceEventManagerModule$RCTDeviceEventEmitter", false, reactContext.javaClass.classLoader)
                    val emit = emitterType.getMethod("emit", String::class.java, Any::class.java)
                    val viewer = SteamChatMediaViewer(host, account, transfer, args) { result ->
                        if (result.optString("type") == "closed" && active?.owner == owner) active = null
                        context.get()?.let { react ->
                            runCatching {
                                val emitter = react.javaClass.getMethod("getJSModule", Class::class.java).invoke(react, emitterType)
                                emit.invoke(emitter, "SteamNovaChatMediaViewer", result.put("owner", owner).toString())
                            }
                        }
                    }
                    active = Open(owner, viewer)
                    try { viewer.show() } catch (error: Exception) { active = null; viewer.closeImmediately(); throw error }
                }
                else -> error("Unknown viewer action")
            }
            JSONObject()
        })
    }
    fun clear(accountToKeep: String? = null) = main.post { active?.takeIf { it.viewer.account != accountToKeep }?.viewer?.closeImmediately() }
    fun foreground(activity: Activity, foreground: Boolean) = main.post { active?.viewer?.takeIf { it.belongsTo(activity) }?.foreground(foreground) }
    fun clearActivity(activity: Activity) = main.post { active?.viewer?.takeIf { it.belongsTo(activity) }?.closeImmediately() }
}
