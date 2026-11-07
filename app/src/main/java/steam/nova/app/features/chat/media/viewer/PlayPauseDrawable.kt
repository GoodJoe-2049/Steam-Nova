package steam.nova.app.features.chat.media.viewer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.AnimationUtils

open class PlayPauseDrawable(size: Int) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val size: Int = AndroidUtilities.dp(size.toFloat())

    private var pause: Boolean = false
    private var progress: Float = 0f
    private var lastUpdateTime: Long = 0

    private var parent: View? = null

    private var alpha: Int = 255

    internal var duration: Float = 300f

    init {
        paint.color = Color.WHITE
    }

    override fun draw(canvas: Canvas) {
        val newUpdateTime = AnimationUtils.currentAnimationTimeMillis()
        var dt = newUpdateTime - lastUpdateTime
        lastUpdateTime = newUpdateTime
        if (dt > 18) {
            dt = 16
        }
        if (pause && progress < 1f) {
            progress += dt / duration
            if (progress >= 1f) {
                progress = 1f
            } else {
                if (parent != null) {
                    parent!!.invalidate()
                }
                invalidateSelf()
            }
        } else if (!pause && progress > 0f) {
            progress -= dt / duration
            if (progress <= 0f) {
                progress = 0f
            } else {
                if (parent != null) {
                    parent!!.invalidate()
                }
                invalidateSelf()
            }
        }
        val bounds = getBounds()
        if (alpha == 255) {
            canvas.save()
        } else {
            canvas.saveLayerAlpha(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                alpha,
            )
        }
        canvas.translate(
            bounds.centerX() + AndroidUtilities.dp(1f) * (1.0f - progress),
            bounds.centerY().toFloat(),
        )
        val ms = 500.0f * progress
        val rotation =
            if (ms < 100) {
                -5 * CubicBezierInterpolator.EASE_BOTH.getInterpolation(ms / 100.0f)
            } else if (ms < 484) {
                -5 + 95 * CubicBezierInterpolator.EASE_BOTH.getInterpolation((ms - 100) / 384)
            } else {
                90f
            }
        canvas.scale(
            1.45f * size / AndroidUtilities.dp(28f),
            1.5f * size / AndroidUtilities.dp(28f),
        )
        canvas.rotate(rotation)
        Theme.playPauseAnimator.draw(canvas, paint, ms)
        canvas.scale(1.0f, -1.0f)
        Theme.playPauseAnimator.draw(canvas, paint, ms)
        canvas.restore()
    }

    open fun setPause(pause: Boolean, animated: Boolean) {
        if (this.pause != pause) {
            this.pause = pause
            if (!animated) {
                progress = if (pause) 1f else 0f
            }
            this.lastUpdateTime = AnimationUtils.currentAnimationTimeMillis()
            invalidateSelf()
        }
    }

    override fun setAlpha(i: Int) {
        alpha = i
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Required by Android Drawable")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSPARENT
    }

    override fun getIntrinsicWidth(): Int {
        return size
    }

    override fun getIntrinsicHeight(): Int {
        return size
    }

    open fun setParent(parent: View?) {
        this.parent = parent
    }

    open fun setDuration(duration: Int) {
        this.duration = duration.toFloat()
    }
}
