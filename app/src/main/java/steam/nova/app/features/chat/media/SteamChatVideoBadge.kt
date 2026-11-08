package steam.nova.app.features.chat.media

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import androidx.core.graphics.withTranslation
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import org.json.JSONObject
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.dp
import steam.nova.app.features.chat.media.viewer.MediaActionDrawable

internal class SteamChatVideoBadge(context: Context) : View(context) {
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(12f).toFloat()
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val progress = MediaActionDrawable().apply { callback = this@SteamChatVideoBadge }
    private val mute = SteamChatMediaResources.drawable(context, "mute")
    private var info = layout("0:00")
    private var fileSize = layout("")
    private var expanded = 1f
    private var expandedTarget = 1f
    private var muteProgress = 0f
    private var muteTarget = 0f
    private var initialized = false
    private var lastFrame = 0L
    private var total = 0L
    private var downloading = false

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun update(args: JSONObject): Float {
        val ready = args.optBoolean("ready")
        val wasDownloading = downloading
        downloading = args.optBoolean("downloading")
        val reportedTotal = args.optLong("size").coerceAtLeast(0)
        if (!wasDownloading || !downloading || total == 0L) total = reportedTotal
        val received = args.optLong("received").coerceAtLeast(0).let {
            if (total > 0) it.coerceAtMost(total) else it
        }
        expandedTarget = if (ready) 0f else 1f
        muteTarget = if (ready && args.optBoolean("muted")) 1f else 0f
        info = layout(args.optString("time", "0:00"))
        var details = if (downloading && total > 0) "${size(received)} / ${size(total)}"
        else if (total > 0) size(total) else args.optString("unknownSize")
        val available = dp(args.optDouble("width", 200.0).toFloat()) - dp(48f)
        if (downloading && total > 0 && textPaint.measureText(details) > available) {
            details = String.format(Locale.US, "%2d%%", (received.toDouble() / total * 100).toInt())
        }
        fileSize = layout(details)
        if (!initialized) {
            expanded = expandedTarget
            muteProgress = muteTarget
        }
        progress.setIcon(when {
            ready -> MediaActionDrawable.ICON_NONE
            downloading -> MediaActionDrawable.ICON_CANCEL_FILL
            else -> MediaActionDrawable.ICON_DOWNLOAD
        }, initialized)
        progress.setProgress(if (total > 0) received.toFloat() / total else 0f, initialized)
        initialized = true
        invalidate()
        val hitWidth = if (ready) info.width + dp(12f) + mute.intrinsicWidth + dp(4f)
        else dp(34f) + max(info.width, fileSize.width)
        return hitWidth / resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else (now - lastFrame).coerceAtMost(17).toFloat()
        lastFrame = now
        expanded = approach(expanded, expandedTarget, dt / 160f)
        muteProgress = approach(muteProgress, muteTarget, dt / 180f)
        val imageWidth = if (downloading) 0f else (mute.intrinsicWidth + dp(4f)) * muteProgress
        val infoWidth = if (downloading) fileSize.width else info.width
        val fullWidth = max(infoWidth + if (infoWidth == info.width) imageWidth else 0f, fileSize.width.toFloat()) + dp(32f)
        val badgeWidth = ceil(infoWidth + dp(12f) + imageWidth + (fullWidth - infoWidth - imageWidth) * expanded)
        canvas.drawRoundRect(dp(4f).toFloat(), dp(4f).toFloat(), dp(4f) + badgeWidth,
            dp(4f) + dp(16.5f + 15.5f * expanded).toFloat(), dp(8f).toFloat(), dp(8f).toFloat(), backgroundPaint)
        canvas.withTranslation(dp(10f + 30f * expanded).toFloat(), dp(5.5f + .2f * expanded).toFloat()) {
            textPaint.alpha = 255
            info.draw(this)
            if (!downloading && muteProgress > 0f) {
                val side = dp(14f * muteProgress)
                val top = (dp(14f) - side) / 2
                val left = info.width + dp(4f)
                mute.alpha = (255 * muteProgress * muteProgress).toInt()
                mute.setBounds(left, top, left + side, top + side)
                mute.draw(this)
            }
            if (expanded > 0f) withTranslation(0f, dp(14.3f * expanded).toFloat()) {
                textPaint.alpha = (255 * expanded).toInt()
                fileSize.draw(this)
            }
            textPaint.alpha = 255
        }
        progress.setBounds(dp(5f), dp(5f), dp(35f), dp(35f))
        progress.draw(canvas)
        if (expanded != expandedTarget || muteProgress != muteTarget) postInvalidateOnAnimation()
    }

    override fun verifyDrawable(who: Drawable): Boolean = who === progress || super.verifyDrawable(who)

    override fun onDetachedFromWindow() {
        unscheduleDrawable(progress)
        lastFrame = 0
        super.onDetachedFromWindow()
    }

    private fun layout(text: String): StaticLayout = StaticLayout.Builder.obtain(
        text, 0, text.length, textPaint, ceil(textPaint.measureText(text)).toInt().coerceAtLeast(1)
    ).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()

    private fun approach(value: Float, target: Float, delta: Float): Float =
        if (target > value) (value + delta).coerceAtMost(target) else (value - delta).coerceAtLeast(target)

    private fun size(bytes: Long): String = when {
        bytes == 0L -> "0 KB"
        bytes < 1024 -> String.format(Locale.US, "%d B", bytes)
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
        bytes < 1000L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024))
        else -> String.format(Locale.US, "%.2f GB", (bytes / 1024 / 1024) / 1000f)
    }
}
