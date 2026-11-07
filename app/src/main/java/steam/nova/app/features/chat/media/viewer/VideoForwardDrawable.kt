package steam.nova.app.features.chat.media.viewer

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.TextPaint
import androidx.core.graphics.withSave
import androidx.core.graphics.withScale
import kotlin.math.max
import kotlin.math.min

open class VideoForwardDrawable(private val isRound: Boolean) : Drawable() {

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val path1: Path = Path()
    private var leftSide: Boolean = false
    private var clippingPath: Path? = null
    private var lastClippingPath: Int = 0

    private var animationProgress: Float = 0f
    private var enterAnimationProgress: Float = 0f
    var isAnimating: Boolean = false
        private set

    private var isOneShootAnimation: Boolean = false
    private var showing: Boolean = false

    private var lastAnimationTime: Long = 0
    private var delegate: VideoForwardDrawableDelegate? = null

    private var time: Long = 0
    private var timeStr: String? = null
    private var playScaleFactor: Float = 1f

    open fun setTime(dt: Long) {
        time = dt
        timeStr =
            if (time >= 1000) {
                AndroidUtilities.seconds((time / 1000).toInt())
            } else {
                null
            }
    }

    interface VideoForwardDrawableDelegate {
        fun onAnimationEnd()

        fun invalidate()
    }

    init {
        paint.color = -0x1
        textPaint.color = -0x1
        textPaint.textSize = AndroidUtilities.dp(12f).toFloat()
        textPaint.textAlign = Paint.Align.CENTER

        path1.reset()
        for (a in 0 until playPath.size / 2) {
            if (a == 0) {
                path1.moveTo(
                    AndroidUtilities.dp(playPath[0].toFloat()).toFloat(),
                    AndroidUtilities.dp(playPath[1].toFloat()).toFloat(),
                )
            } else {
                path1.lineTo(
                    AndroidUtilities.dp(playPath[a * 2].toFloat()).toFloat(),
                    AndroidUtilities.dp(playPath[a * 2 + 1].toFloat()).toFloat(),
                )
            }
        }
        path1.close()
    }

    open fun startAnimation() {
        isAnimating = true
        animationProgress = 0.0f
        invalidateSelf()
    }

    open fun setOneShootAnimation(isOneShootAnimation: Boolean) {
        if (this.isOneShootAnimation != isOneShootAnimation) {
            this.isOneShootAnimation = isOneShootAnimation
            timeStr = null
            time = 0
            animationProgress = 0f
        }
    }

    open fun setLeftSide(value: Boolean) {
        if (leftSide == value && animationProgress >= 1.0f && isOneShootAnimation) {
            return
        }
        if (leftSide != value) {
            time = 0
            timeStr = null
        }
        leftSide = value
        startAnimation()
    }

    open fun setDelegate(videoForwardDrawableDelegate: VideoForwardDrawableDelegate?) {
        delegate = videoForwardDrawableDelegate
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Required by Android Drawable")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSPARENT
    }

    override fun draw(canvas: Canvas) {
        val rect = bounds

        var x = rect.left + (rect.width() - intrinsicWidth) / 2
        val y = rect.top + (rect.height() - intrinsicHeight) / 2
        if (leftSide) {
            x -= rect.width() / 4 - AndroidUtilities.dp(16f)
        } else {
            x += rect.width() / 4 + AndroidUtilities.dp(16f)
        }

        canvas.withSave {
            if (isRound) {
                if (clippingPath == null) {
                    clippingPath = Path()
                }
                val clippingPathHash =
                    rect.left + (rect.top shl 8) + (rect.bottom shl 16) + (rect.right shl 24)
                if (lastClippingPath != clippingPathHash) {
                    clippingPath!!.reset()
                    AndroidUtilities.rectTmp.set(rect)
                    clippingPath!!.addOval(AndroidUtilities.rectTmp, Path.Direction.CCW)
                    lastClippingPath = clippingPathHash
                }
                canvas.clipPath(clippingPath!!)
            } else {
                canvas.clipRect(rect.left, rect.top, rect.right, rect.bottom)
            }
            if (!isOneShootAnimation) {
                paint.alpha = (80 * enterAnimationProgress).toInt()
                textPaint.alpha = (255 * enterAnimationProgress).toInt()
            } else {
                if (animationProgress <= 0.7f) {
                    paint.alpha = (80 * min(1.0f, animationProgress / 0.3f)).toInt()
                    textPaint.alpha = (255 * min(1.0f, animationProgress / 0.3f)).toInt()
                } else {
                    paint.alpha = (80 * (1.0f - (animationProgress - 0.7f) / 0.3f)).toInt()
                    textPaint.alpha = (255 * (1.0f - (animationProgress - 0.7f) / 0.3f)).toInt()
                }
            }
            canvas.drawCircle(
                (x + max(rect.width(), rect.height()) / 4 * (if (leftSide) -1 else 1)).toFloat(),
                (y + AndroidUtilities.dp(16f)).toFloat(),
                (max(rect.width(), rect.height()) / 2).toFloat(),
                paint,
            )
        }

        if (timeStr != null) {
            canvas.drawText(
                timeStr!!,
                (x + intrinsicWidth * (if (leftSide) -1 else 1)).toFloat(),
                (y + intrinsicHeight + AndroidUtilities.dp(15f)).toFloat(),
                textPaint,
            )
        }

        canvas.withScale(playScaleFactor, playScaleFactor, x.toFloat(), y + intrinsicHeight / 2f) {
            if (leftSide) {
                canvas.rotate(180f, x.toFloat(), (y + intrinsicHeight / 2).toFloat())
            }
            canvas.translate(x.toFloat(), y.toFloat())
            if (animationProgress <= 0.6f) {
                var a =
                    if (animationProgress < 0.4f) {
                        min(255, (255 * animationProgress / 0.2f).toInt())
                    } else {
                        (255 * (1.0f - (animationProgress - 0.4f) / 0.2f)).toInt()
                    }
                if (!isOneShootAnimation) {
                    a = (a * enterAnimationProgress).toInt()
                }
                paint.alpha = a
                canvas.drawPath(path1, paint)
            }
            canvas.translate(AndroidUtilities.dp(18f).toFloat(), 0f)
            if (animationProgress in 0.2f..0.8f) {
                val progress = animationProgress - 0.2f
                var a =
                    if (progress < 0.4f) {
                        min(255, (255 * progress / 0.2f).toInt())
                    } else {
                        (255 * (1.0f - (progress - 0.4f) / 0.2f)).toInt()
                    }
                if (!isOneShootAnimation) {
                    a = (a * enterAnimationProgress).toInt()
                }
                paint.alpha = a
                canvas.drawPath(path1, paint)
            }
            canvas.translate(AndroidUtilities.dp(18f).toFloat(), 0f)
            if (animationProgress in 0.4f..1.0f) {
                val progress = animationProgress - 0.4f
                var a =
                    if (progress < 0.4f) {
                        min(255, (255 * progress / 0.2f).toInt())
                    } else {
                        (255 * (1.0f - (progress - 0.4f) / 0.2f)).toInt()
                    }
                if (!isOneShootAnimation) {
                    a = (a * enterAnimationProgress).toInt()
                }
                paint.alpha = a
                canvas.drawPath(path1, paint)
            }
        }

        if (isAnimating) {
            val newTime = System.currentTimeMillis()
            var dt = newTime - lastAnimationTime
            if (dt > 17) {
                dt = 17
            }
            lastAnimationTime = newTime
            if (animationProgress < 1.0f) {
                animationProgress += dt / 800.0f
                if (!isOneShootAnimation) {
                    if (animationProgress >= 1.0f) {
                        animationProgress =
                            if (showing) {
                                0f
                            } else {
                                1f
                            }
                    }
                } else {
                    if (animationProgress >= 1.0f) {
                        animationProgress = 0.0f
                        isAnimating = false
                        time = 0
                        timeStr = null
                        if (delegate != null) {
                            delegate!!.onAnimationEnd()
                        }
                    }
                }
                invalidate()
            }
            if (!isOneShootAnimation) {
                if (showing && enterAnimationProgress != 1f) {
                    enterAnimationProgress += 16 / 150f
                    invalidate()
                } else if (!showing && enterAnimationProgress != 0f) {
                    enterAnimationProgress -= 16 / 150f
                    invalidate()
                }
                if (enterAnimationProgress < 0) {
                    enterAnimationProgress = 0f
                } else if (enterAnimationProgress > 1f) {
                    enterAnimationProgress = 1f
                }
            }
        }
    }

    private fun invalidate() {
        if (delegate != null) {
            delegate!!.invalidate()
        } else {
            invalidateSelf()
        }
    }

    override fun getIntrinsicWidth(): Int {
        return AndroidUtilities.dp(32f)
    }

    override fun getIntrinsicHeight(): Int {
        return AndroidUtilities.dp(32f)
    }

    override fun getMinimumWidth(): Int {
        return AndroidUtilities.dp(32f)
    }

    override fun getMinimumHeight(): Int {
        return AndroidUtilities.dp(32f)
    }

    open fun addTime(time: Long) {
        this.time += time
        timeStr = AndroidUtilities.seconds((this.time / 1000).toInt())
    }

    companion object {

        private val playPath: IntArray = intArrayOf(10, 7, 26, 16, 10, 25)
    }
}
