// Nagram/Telegram GPL-2.0-or-later. PhotoViewer.PhotoProgressView normal-media branch.
package steam.nova.app.features.chat.media.viewer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.dp

open class PhotoProgressView(
    private val parent: View?,
    circle: Drawable?,
    cancel: Drawable?,
    load: Drawable?,
) {

    private var lastUpdateTime: Long = 0
    private var radOffset: Float = 0f
    private var currentProgress: Float = 0f
    private var animationProgressStart: Float = 0f
    private var currentProgressTime: Long = 0
    private var animatedProgressValue: Float = 0f
    private val progressRect: RectF = RectF()
    private var backgroundState: Int = -1
    private val size: Int = dp(64f)
    private var previousBackgroundState: Int = -2
    private var animatedAlphaValue: Float = 1.0f
    private val animAlphas: FloatArray = FloatArray(3)
    private val alphas: FloatArray = FloatArray(3)
    private var scale: Float = 1.0f
    var isVisible: Boolean = false
        private set

    private val playDrawable: CombinedDrawable
    private val playPauseDrawable: PlayPauseDrawable

    private val progressDrawables: Array<Drawable?> = arrayOf(circle, cancel, load)
    private val decelerateInterpolator: android.view.animation.DecelerateInterpolator =
        android.view.animation.DecelerateInterpolator(1.5f)
    private val progressPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    open val x: Int
        get() {
            return (parent!!.width - (size * scale).toInt()) / 2
        }

    open val y: Int
        get() {
            return (parent!!.height - (size * scale).toInt()) / 2
        }

    init {
        progressPaint.style = Paint.Style.STROKE
        progressPaint.strokeCap = Paint.Cap.ROUND
        progressPaint.strokeWidth = dp(3f).toFloat()
        progressPaint.color = -0x1
        resetAlphas()

        playPauseDrawable = PlayPauseDrawable(28)
        playPauseDrawable.setDuration(200)

        val circleDrawable = circle!!.constantState!!.newDrawable().mutate()
        playDrawable = CombinedDrawable(circleDrawable.mutate(), playPauseDrawable)
    }

    private fun updateAnimation(withProgressAnimation: Boolean) {
        val newTime = System.currentTimeMillis()
        var dt = newTime - lastUpdateTime
        if (dt > 18) {
            dt = 18
        }
        lastUpdateTime = newTime

        var postInvalidate = false

        if (withProgressAnimation) {
            if (animatedProgressValue != 1f || currentProgress != 1f) {
                radOffset += 360 * dt / 3000.0f
                val progressDiff = currentProgress - animationProgressStart
                if (abs(progressDiff) > 0) {
                    currentProgressTime += dt
                    if (currentProgressTime >= 300) {
                        animatedProgressValue = currentProgress
                        animationProgressStart = currentProgress
                        currentProgressTime = 0
                    } else {
                        animatedProgressValue =
                            animationProgressStart +
                                progressDiff *
                                    decelerateInterpolator.getInterpolation(
                                        currentProgressTime / 300.0f
                                    )
                    }
                }
                postInvalidate = true
            }

            if (animatedAlphaValue > 0 && previousBackgroundState != -2) {
                animatedAlphaValue -= dt / 200.0f
                if (animatedAlphaValue <= 0) {
                    animatedAlphaValue = 0.0f
                    previousBackgroundState = -2
                }
                postInvalidate = true
            }
        }

        for (i in alphas.indices) {
            if (alphas[i] > animAlphas[i]) {
                animAlphas[i] = min(1f, animAlphas[i] + dt / 200f)
                postInvalidate = true
            } else if (alphas[i] < animAlphas[i]) {
                animAlphas[i] = max(0f, animAlphas[i] - dt / 200f)
                postInvalidate = true
            }
        }

        if (postInvalidate) {
            parent!!.postInvalidateOnAnimation()
        }
    }

    open fun setProgress(value: Float, animated: Boolean) {
        if (!animated) {
            animatedProgressValue = value
            animationProgressStart = value
        } else {
            animationProgressStart = animatedProgressValue
        }
        currentProgress = value
        currentProgressTime = 0
        parent!!.invalidate()
    }

    open fun setBackgroundState(state: Int, animated: Boolean, animateIcon: Boolean) {
        if (backgroundState == state) {
            return
        }
        val animatePlayPause =
            animateIcon && (backgroundState == PROGRESS_PLAY || backgroundState == PROGRESS_PAUSE)
        if (state == PROGRESS_PLAY) {
            playPauseDrawable.setPause(false, animatePlayPause)
        } else if (state == PROGRESS_PAUSE) {
            playPauseDrawable.setPause(true, animatePlayPause)
        }
        playPauseDrawable.setParent(parent)
        playPauseDrawable.invalidateSelf()
        lastUpdateTime = System.currentTimeMillis()
        if (animated && backgroundState != state) {
            previousBackgroundState = backgroundState
            animatedAlphaValue = 1.0f
        } else {
            previousBackgroundState = -2
        }
        backgroundState = state
        onBackgroundStateUpdated(state)
        parent!!.invalidate()
    }

    protected open fun onBackgroundStateUpdated(state: Int) {}

    open fun setAlpha(value: Float) {
        setIndexedAlpha(0, value, false)
    }

    open fun setIndexedAlpha(index: Int, alpha: Float, animated: Boolean) {
        if (alphas[index] != alpha) {
            alphas[index] = alpha
            if (!animated) {
                animAlphas[index] = alpha
            }
            checkVisibility()
            parent!!.invalidate()
        }
    }

    open fun resetAlphas() {
        for (i in alphas.indices) {
            animAlphas[i] = 1.0f
            alphas[i] = animAlphas[i]
        }
        checkVisibility()
    }

    private fun calculateAlpha(): Float {
        var alpha = 1.0f
        for (i in animAlphas.indices) {
            alpha =
                if (i == 2) {
                    alpha *
                        android.view.animation
                            .AccelerateInterpolator()
                            .getInterpolation(animAlphas[i])
                } else {
                    alpha * animAlphas[i]
                }
        }
        return alpha
    }

    private fun checkVisibility() {
        var newVisible = true
        for (i in alphas.indices) {
            if (alphas[i] != 1.0f) {
                newVisible = false
                break
            }
        }
        if (newVisible != isVisible) {
            isVisible = newVisible
            onVisibilityChanged(isVisible)
        }
    }

    protected open fun onVisibilityChanged(visible: Boolean) {}

    open fun onDraw(canvas: Canvas) {
        val sizeScaled = (size * scale).toInt()
        val x = x
        val y = y

        val alpha = calculateAlpha()

        if (previousBackgroundState >= 0 && previousBackgroundState < progressDrawables.size + 2) {
            val drawable =
                if (previousBackgroundState < progressDrawables.size) {
                    progressDrawables[previousBackgroundState]
                } else {
                    playDrawable
                }
            if (drawable != null) {
                drawable.alpha = (255f * animatedAlphaValue * alpha).toInt()
                drawable.setBounds(x, y, x + sizeScaled, y + sizeScaled)
                drawable.draw(canvas)
            }
        }

        if (backgroundState >= 0 && backgroundState < progressDrawables.size + 2) {
            val drawable =
                if (backgroundState < progressDrawables.size) {
                    progressDrawables[backgroundState]
                } else {
                    playDrawable
                }
            if (drawable != null) {
                if (previousBackgroundState != -2) {
                    drawable.alpha = (255f * (1.0f - animatedAlphaValue) * alpha).toInt()
                } else {
                    drawable.alpha = (255 * alpha).toInt()
                }
                drawable.setBounds(x, y, x + sizeScaled, y + sizeScaled)
                drawable.draw(canvas)
            }
        }

        if (
            backgroundState == PROGRESS_EMPTY ||
                backgroundState == PROGRESS_CANCEL ||
                previousBackgroundState == PROGRESS_EMPTY ||
                previousBackgroundState == PROGRESS_CANCEL
        ) {
            val diff = dp(4f)
            if (previousBackgroundState != -2) {
                progressPaint.alpha = (255f * animatedAlphaValue * alpha).toInt()
            } else {
                progressPaint.alpha = (255 * alpha).toInt()
            }
            progressRect.set(
                (x + diff).toFloat(),
                (y + diff).toFloat(),
                (x + sizeScaled - diff).toFloat(),
                (y + sizeScaled - diff).toFloat(),
            )
            canvas.drawArc(
                progressRect,
                -90 + radOffset,
                max(4f, 360 * animatedProgressValue),
                false,
                progressPaint,
            )
            updateAnimation(true)
        } else {
            updateAnimation(false)
        }
    }

    companion object {
        const val PROGRESS_EMPTY: Int = 0
        const val PROGRESS_CANCEL: Int = 1
        const val PROGRESS_LOAD: Int = 2
        const val PROGRESS_PLAY: Int = 3
        const val PROGRESS_PAUSE: Int = 4
    }
}
