package steam.nova.app.features.chat.media.viewer

import androidx.core.graphics.withTranslation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import java.io.File

/** Decoder boundary replacing Telegram's ImageReceiver/FFmpeg native decoder.
 * The viewer's transforms and gestures operate on the decoded image, including animated images.
 */
internal object SteamChatImageDecoder {
    fun animation(file: File): Drawable? {
        if (Build.VERSION.SDK_INT >= 28) {
            val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val maximum = maxOf(info.size.width, info.size.height)
                if (maximum > 2048) decoder.setTargetSampleSize((maximum + 2047) / 2048)
            }
            return (drawable as? AnimatedImageDrawable)?.apply { repeatCount = AnimatedImageDrawable.REPEAT_INFINITE }
        }
        return legacyGif(file)
    }

    // ImageDecoder starts at API 28; Movie preserves animated GIFs on Android 8.
    @Suppress("DEPRECATION")
    private fun legacyGif(file: File): Drawable? {
        val movie = android.graphics.Movie.decodeFile(file.path) ?: return null
        return object : Drawable(), Animatable {
            private var running = false
            private var started = 0L
            override fun start() { if (!running) { running = true; started = SystemClock.uptimeMillis(); invalidateSelf() } }
            override fun stop() { running = false }
            override fun isRunning() = running
            override fun getIntrinsicWidth() = movie.width()
            override fun getIntrinsicHeight() = movie.height()
            override fun draw(canvas: Canvas) {
                if (running) movie.setTime(((SystemClock.uptimeMillis() - started) % movie.duration().coerceAtLeast(1)).toInt())
                canvas.withTranslation(bounds.left.toFloat(), bounds.top.toFloat()) {
                    canvas.scale(bounds.width().toFloat() / movie.width(), bounds.height().toFloat() / movie.height())
                    movie.draw(canvas, 0f, 0f)
                }
                if (running) invalidateSelf()
            }
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: ColorFilter?) = Unit
            @Deprecated("Required by Android Drawable")
            override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }

    fun bitmap(file: File): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val maximum = maxOf(info.size.width, info.size.height)
            if (maximum > 4096) decoder.setTargetSampleSize((maximum + 4095) / 4096)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
