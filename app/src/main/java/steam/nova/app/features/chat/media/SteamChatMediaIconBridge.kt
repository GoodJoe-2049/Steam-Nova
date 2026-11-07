package steam.nova.app.features.chat.media

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import org.json.JSONObject
import steam.nova.app.features.chat.media.viewer.MediaActionDrawable
import steam.nova.app.features.chat.network.SteamChatAccount

/** Mounts the upstream animated Drawable inside a tagged RN slot, like the chat scroll control. */
internal object SteamChatMediaIconBridge {
    private val main = Handler(Looper.getMainLooper())
    private val layers = hashMapOf<String, Layer>()

    private class Layer(val slot: ViewGroup, val account: String) :
        View.OnAttachStateChangeListener {
        val drawable = MediaActionDrawable()
        val view =
            ImageView(slot.context).apply {
                setImageDrawable(drawable)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

        init {
            slot.addView(view, ViewGroup.LayoutParams(-1, -1))
            slot.addOnAttachStateChangeListener(this)
        }

        fun close() {
            slot.removeOnAttachStateChangeListener(this)
            slot.removeView(view)
            view.setImageDrawable(null)
        }

        override fun onViewAttachedToWindow(v: View) = Unit

        override fun onViewDetachedFromWindow(v: View) {
            close()
            layers.entries.removeAll { it.value === this }
        }
    }

    fun handle(activity: Activity?, args: JSONObject, complete: (Result<JSONObject>) -> Unit) =
        main.post {
            complete(
                runCatching {
                    val owner = args.getString("owner")
                    require(owner.matches(Regex("media-icon-[A-Za-z0-9_-]+")))
                    val account = args.getString("account")
                    SteamChatAccount.current(account)
                    if (args.optBoolean("detach"))
                        layers[owner]
                            ?.takeIf { it.account == account }
                            ?.let {
                                it.close()
                                layers.remove(owner)
                            }
                    else {
                        val host = requireNotNull(activity)
                        val slot =
                            requireNotNull(
                                host.window.decorView.findViewWithTag<View>(owner) as? ViewGroup
                            )
                        val layer =
                            layers[owner] ?: Layer(slot, account).also { layers[owner] = it }
                        require(layer.account == account && layer.slot === slot)
                        val icon =
                            when (args.optString("name")) {
                                "pause" -> MediaActionDrawable.ICON_PAUSE
                                "download" -> MediaActionDrawable.ICON_DOWNLOAD
                                "cancel" -> MediaActionDrawable.ICON_CANCEL
                                else -> MediaActionDrawable.ICON_PLAY
                            }
                        layer.drawable.setIcon(icon, args.optBoolean("animated", true))
                        layer.drawable.setProgress(
                            args.optDouble("progress").toFloat().let {
                                if (it.isFinite()) it.coerceIn(0f, 1f) else 0f
                            },
                            true,
                        )
                        layer.view.measure(
                            View.MeasureSpec.makeMeasureSpec(slot.width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(slot.height, View.MeasureSpec.EXACTLY),
                        )
                        layer.view.layout(0, 0, slot.width, slot.height)
                    }
                    JSONObject()
                }
            )
        }

    fun clear(accountToKeep: String? = null) =
        main.post {
            layers.entries
                .toList()
                .filter { it.value.account != accountToKeep }
                .forEach {
                    it.value.close()
                    layers.remove(it.key)
                }
        }
}
