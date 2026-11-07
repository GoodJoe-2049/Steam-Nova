package steam.nova.app.features.chat.media.viewer
import androidx.core.graphics.scale

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.view.View
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import steam.nova.app.features.chat.media.SteamChatMediaResources
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.dp

/**
 * VideoSeekPreviewImage's local-file path, with Android's decoder replacing AnimatedFileDrawable.
 */
internal class SteamChatVideoPreview(context: Context) : View(context) {
    private val frame = SteamChatMediaResources.drawable(context, "preview", false)
    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dp(13f).toFloat()
            color = Color.WHITE
        }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val matrix = Matrix()
    private var bitmap: Bitmap? = null
    private var bitmapShader: BitmapShader? = null
    private var pixel = -1
    private var frameTime = ""
    private var generation = 0
    private var pending: Pair<File, Long>? = null
    private var running = false
    private val main = Handler(Looper.getMainLooper())

    fun progress(file: File, duration: Long, progress: Float, width: Int) {
        val next = (width * progress).toInt() / 5
        if (pixel == next) return
        pixel = next
        val time = (duration * progress).toLong()
        val seconds = time / 1000
        frameTime = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
        invalidate()
        pending = file to time
        decode()
    }

    private fun decode() {
        if (running) return
        val request = pending ?: return
        pending = null
        running = true
        val ticket = generation
        worker.execute {
            val decoded =
                runCatching {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(request.first.path)
                            retriever
                                .getFrameAtTime(
                                    request.second * 1000,
                                    MediaMetadataRetriever.OPTION_CLOSEST,
                                )
                                ?.let { source ->
                                    val max = maxOf(200, dp(100f))
                                    val factor = max.toFloat() / maxOf(source.width, source.height)
                                    val result =
                                        source.scale(
                                            (source.width * factor).toInt().coerceAtLeast(1),
                                            (source.height * factor).toInt().coerceAtLeast(1),
                                            true,
                                        )
                                    if (source !== result) source.recycle()
                                    result
                                }
                        } finally {
                            retriever.release()
                        }
                    }
                    .getOrNull()
            main.post {
                running = false
                if (generation != ticket) {
                    decoded?.recycle()
                    decode()
                    return@post
                }
                if (decoded != null) {
                    bitmap?.recycle()
                    bitmap = decoded
                    bitmapShader =
                        BitmapShader(decoded, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    bitmapPaint.shader = bitmapShader
                    val aspect = decoded.width.toFloat() / decoded.height
                    layoutParams =
                        layoutParams.apply {
                            width = if (aspect > 1f) dp(150f) else (dp(150f) * aspect).toInt()
                            height = if (aspect > 1f) (dp(150f) / aspect).toInt() else dp(150f)
                        }
                    invalidate()
                }
                decode()
            }
        }
    }

    // Nagram VideoSeekPreviewImage.onDraw local bitmap branch.
    override fun onDraw(canvas: Canvas) {
        val image = bitmap ?: return
        matrix.reset()
        val scale = measuredWidth.toFloat() / image.width
        matrix.preScale(scale, scale)
        bitmapShader?.setLocalMatrix(matrix)
        rect.set(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
        canvas.drawRoundRect(rect, dp(6f).toFloat(), dp(6f).toFloat(), bitmapPaint)
        frame.setBounds(0, 0, measuredWidth, measuredHeight)
        frame.draw(canvas)
        canvas.drawText(
            frameTime,
            (measuredWidth - textPaint.measureText(frameTime)) / 2f,
            (measuredHeight - dp(9f)).toFloat(),
            textPaint,
        )
    }

    fun clear() {
        generation++
        pending = null
        pixel = -1
        bitmap?.recycle()
        bitmap = null
        bitmapShader = null
        bitmapPaint.shader = null
    }

    fun close() = clear()

    companion object {
        private val worker =
            Executors.newSingleThreadExecutor {
                Thread(it, "SteamNovaVideoFrames").apply { isDaemon = true }
            }
    }
}
