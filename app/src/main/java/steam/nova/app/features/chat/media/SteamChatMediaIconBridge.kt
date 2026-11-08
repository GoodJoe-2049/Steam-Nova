package steam.nova.app.features.chat.media

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.json.JSONObject
import steam.nova.app.features.chat.media.viewer.MediaActionDrawable
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.dp
import steam.nova.app.features.chat.network.SteamChatAccount

/** Mounts the upstream animated Drawable inside a tagged RN slot, like the chat scroll control. */
internal object SteamChatMediaIconBridge {
    private val main = Handler(Looper.getMainLooper())
    private val layers = hashMapOf<String, Layer>()

    private class IconView(context: Context) : View(context) {
        val drawable = MediaActionDrawable().apply { callback = this@IconView }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var circle = false
        var iconPressed = false

        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

        override fun onDraw(canvas: Canvas) {
            val icon = drawable.currentIcon
            val transition = drawable.iconTransitionProgress
            if (icon == MediaActionDrawable.ICON_NONE && transition >= 1f) return
            val cx = width / 2
            val cy = height / 2
            val radius = if (circle) dp(22f) else minOf(width, height) / 2
            if (circle) {
                val alpha = when (icon) {
                    MediaActionDrawable.ICON_PLAY -> if (drawable.previousIcon == MediaActionDrawable.ICON_NONE) transition else 1f
                    MediaActionDrawable.ICON_NONE -> 1f - transition
                    else -> 1f
                }
                paint.color = if (iconPressed) 0x7f000000 else 0x66000000
                paint.alpha = (paint.alpha * alpha).toInt()
                canvas.drawCircle(cx.toFloat(), cy.toFloat(), radius.toFloat(), paint)
            }
            drawable.color = if (iconPressed) 0xffd9d9d9.toInt() else -1
            drawable.setBounds(cx - radius, cy - radius, cx + radius, cy + radius)
            drawable.draw(canvas)
        }

        override fun verifyDrawable(who: Drawable) = who === drawable || super.verifyDrawable(who)

        override fun onDetachedFromWindow() {
            unscheduleDrawable(drawable)
            super.onDetachedFromWindow()
        }
    }

    private class Layer(val slot: ViewGroup, val account: String, badge: Boolean) :
        View.OnAttachStateChangeListener {
        var initialized = false
        val view: View = if (badge) SteamChatVideoBadge(slot.context) else
            IconView(slot.context)

        init {
            slot.addView(view, ViewGroup.LayoutParams(-1, -1))
            slot.addOnAttachStateChangeListener(this)
        }

        fun close() {
            slot.removeOnAttachStateChangeListener(this)
            slot.removeView(view)
            (view as? IconView)?.drawable?.callback = null
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
                        val layer = layers[owner] ?: Layer(slot, account, args.optString("name") == "videoBadge")
                            .also { layers[owner] = it }
                        require(layer.account == account && layer.slot === slot)
                        val badge = layer.view as? SteamChatVideoBadge
                        val hitWidth = badge?.update(args)
                        if (badge == null) {
                            val iconView = layer.view as IconView
                            iconView.circle = args.optBoolean("circle")
                            iconView.iconPressed = args.optBoolean("pressed")
                            val icon =
                                when (args.optString("name")) {
                                    "pause" -> MediaActionDrawable.ICON_PAUSE
                                    "download" -> MediaActionDrawable.ICON_DOWNLOAD
                                    "cancel" -> MediaActionDrawable.ICON_CANCEL
                                    "cancelFill" -> MediaActionDrawable.ICON_CANCEL_FILL
                                    "none" -> MediaActionDrawable.ICON_NONE
                                    else -> MediaActionDrawable.ICON_PLAY
                                }
                            iconView.drawable.setIcon(icon, layer.initialized && args.optBoolean("animated", true))
                            iconView.drawable.setProgress(
                                args.optDouble("progress").toFloat().let {
                                    if (it.isFinite()) it.coerceIn(0f, 1f) else 0f
                                },
                                true,
                            )
                            iconView.invalidate()
                            layer.initialized = true
                        }
                        layer.view.measure(
                            View.MeasureSpec.makeMeasureSpec(slot.width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(slot.height, View.MeasureSpec.EXACTLY),
                        )
                        layer.view.layout(0, 0, slot.width, slot.height)
                        return@runCatching JSONObject().apply { if (hitWidth != null) put("hitWidth", hitWidth) }
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
