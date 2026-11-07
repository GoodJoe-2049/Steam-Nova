package steam.nova.app.features.chat.media.viewer

import android.content.res.Resources
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Android services adapter for the copied Nagram drawing classes. */
object AndroidUtilities {
    fun setRectToRect(
        matrix: android.graphics.Matrix,
        src: RectF,
        dst: RectF,
        rotation: Int,
        invert: Int,
        translate: Boolean,
    ) {
        var tx: Float
        var sx: Float
        var ty: Float
        var sy: Float
        var xLarger = false
        if (rotation == 90 || rotation == 270) {
            sx = dst.height() / src.width()
            sy = dst.width() / src.height()
        } else {
            sx = dst.width() / src.width()
            sy = dst.height() / src.height()
        }
        if (sx < sy) {
            sx = sy
            xLarger = true
        } else {
            sy = sx
        }
        if (translate) {
            matrix.setTranslate(dst.left, dst.top)
        }
        when (rotation) {
            90 -> {
                matrix.preRotate(90f)
                if (invert == 1) {
                    matrix.preScale(-1f, 1f)
                } else if (invert == 2) {
                    matrix.preScale(1f, -1f)
                }
                matrix.preTranslate(0f, -dst.width())
            }
            180 -> {
                matrix.preRotate(180f)
                if (invert == 1) {
                    matrix.preScale(-1f, 1f)
                } else if (invert == 2) {
                    matrix.preScale(1f, -1f)
                }
                matrix.preTranslate(-dst.width(), -dst.height())
            }
            270 -> {
                matrix.preRotate(270f)
                if (invert == 1) {
                    matrix.preScale(-1f, 1f)
                } else if (invert == 2) {
                    matrix.preScale(1f, -1f)
                }
                matrix.preTranslate(-dst.height(), 0f)
            }
        }

        if (translate) {
            tx = -src.left * sx
            ty = -src.top * sy
        } else {
            tx = dst.left - src.left * sx
            ty = dst.top - src.top * sy
        }

        var diff =
            if (xLarger) {
                dst.width() - src.width() * sy
            } else {
                dst.height() - src.height() * sy
            }
        diff /= 2.0f
        if (xLarger) {
            tx += diff
        } else {
            ty += diff
        }

        matrix.preScale(sx, sy)
        if (translate) {
            matrix.preTranslate(tx, ty)
        }
    }

    private val main: Handler = Handler(Looper.getMainLooper())
    val rectTmp: RectF = RectF()
    val displaySize: android.graphics.Point =
        android.graphics.Point(
            Resources.getSystem()!!.displayMetrics!!.widthPixels,
            Resources.getSystem()!!.displayMetrics!!.heightPixels,
        )

    fun cascade(fullAnimationT: Float, position: Float, count: Float, waveLength: Float): Float {
        if (count <= 0) return fullAnimationT
        val waveDuration = 1f / count * min(waveLength, count)
        val waveOffset = position / count * (1f - waveDuration)
        return max(0f, min(1f, (fullAnimationT - waveOffset) / waveDuration))
    }

    fun isRTL(text: CharSequence?): Boolean {
        if (text != null)
            for (c in text) {
                if (c.code in 0x590..0x6ff) return true
            }
        return false
    }

    fun dp(value: Float): Int {
        return ceil(dpf2(value).toDouble()).toInt()
    }

    fun dpf2(value: Float): Float {
        return value * Resources.getSystem()!!.displayMetrics!!.density
    }

    fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }

    fun bold(): Typeface {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun runOnUIThread(action: Runnable, delay: Long) {
        main.postDelayed(action, delay)
    }

    fun runOnUIThread(action: Runnable) {
        main.post(action)
    }

    fun cancelRunOnUIThread(action: Runnable) {
        main.removeCallbacks(action)
    }

    fun vibrateCursor(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun seconds(value: Int): String {
        return if (Locale.getDefault().language == "zh") "$value 秒"
        else value.toString() + (if (value == 1) " second" else " seconds")
    }
}
