/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package steam.nova.app.features.chat.media.viewer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Pair
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withSave
import java.util.ArrayList
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

open class VideoPlayerSeekBar(private val parentView: View?) {

    private var progress: Float = 0f
    private var thumbX: Int = 0
    private var animatedThumbX: Float = 0f
    private var draggingThumbX: Int = 0
    private var thumbDX: Int = 0
    var isDragging: Boolean = false
        private set

    private var pressedDelayed: Boolean = false
    private var width: Int = 0
    private var height: Int = 0
    private var delegate: SeekBarDelegate? = null
    private var backgroundColor: Int = 0
    private var cacheColor: Int = 0
    private var circleColor: Int = 0
    private var progressColor: Int = 0
    private var backgroundSelectedColor: Int = 0
    private val rect: RectF = RectF()
    private var selected: Boolean = false
    private var animateFromBufferedProgress: Float = 0f
    private var animateResetBuffering: Boolean = false
    private var bufferedAnimationValue: Float = 1f
    private var bufferedProgress: Float = 0f
    private var currentRadius: Float = 0f
    private var lastUpdateTime: Long = 0

    private val lineHeight: Int = AndroidUtilities.dp(4f)
    private val smallLineHeight: Int = AndroidUtilities.dp(2f)

    open var transitionProgress: Float = 0f
        set(transitionProgress) {
            if (this@VideoPlayerSeekBar.transitionProgress != transitionProgress) {
                field = transitionProgress
                parentView!!.invalidate()
            }
        }

    open var horizontalPadding: Int = 0
    private var smallLineColor: Int = 0

    private var fromThumbX: Int = 0
    private var animateThumbProgress: Float = 1f
    private val animateThumbLoopBackProgress: AnimatedFloat
    private var loopBackWasThumbX: Float = 0f

    private var timestamps: ArrayList<Pair<Float, CharSequence>>? = null

    private var timestampsAppearing: Float = 0f
    private var lastTimestampsAppearingUpdate: Long = 0
    private val timestampGap: Float = 1f

    private var currentTimestamp: Int = -1
    private var lastTimestamp: Int = -1
    private var timestampLabel: Array<StaticLayout?>? = null
    private var timestampLabelPaint: TextPaint? = null
    private var timestampChangeT: Float = 1f
    private var timestampChangeDirection: Int = 0
    private var lastTimestampUpdate: Long = 0
    private var lastWidth: Float = -1f

    interface SeekBarDelegate {
        fun onSeekBarDrag(progress: Float)

        fun onSeekBarContinuousDrag(progress: Float) {}
    }

    init {

        thumbWidth = AndroidUtilities.dp(24f)
        currentRadius = AndroidUtilities.dp(6f).toFloat()
        animateThumbLoopBackProgress =
            AnimatedFloat(0f, parentView, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT)
    }

    open fun setDelegate(seekBarDelegate: SeekBarDelegate?) {
        delegate = seekBarDelegate
    }

    open fun onTouch(action: Int, x: Float, y: Float): Boolean {
        if (action == MotionEvent.ACTION_DOWN) {
            if (this@VideoPlayerSeekBar.transitionProgress > 0f) {
                return false
            }
            val additionWidth = (height - thumbWidth) / 2
            if (x >= -additionWidth && x <= width + additionWidth && y >= 0 && y <= height) {
                if (!(thumbX - additionWidth <= x && x <= thumbX + thumbWidth + additionWidth)) {
                    thumbX = x.toInt() - thumbWidth / 2
                    if (thumbX < 0) {
                        thumbX = 0
                    } else if (thumbX > width - thumbWidth) {
                        thumbX = thumbWidth - width
                    }
                    animatedThumbX = thumbX.toFloat()
                }
                pressedDelayed = true
                isDragging = true
                draggingThumbX = thumbX
                thumbDX = (x - thumbX).toInt()
                return true
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (isDragging) {
                thumbX = draggingThumbX
                animatedThumbX = thumbX.toFloat()
                if (action == MotionEvent.ACTION_UP && delegate != null) {
                    delegate!!.onSeekBarDrag(thumbX.toFloat() / (width - thumbWidth).toFloat())
                }
                isDragging = false
                AndroidUtilities.runOnUIThread({ pressedDelayed = false }, 50)
                return true
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (isDragging) {
                draggingThumbX = (x - thumbDX).toInt()
                if (draggingThumbX < 0) {
                    draggingThumbX = 0
                } else if (draggingThumbX > width - thumbWidth) {
                    draggingThumbX = width - thumbWidth
                }
                if (delegate != null) {
                    delegate!!.onSeekBarContinuousDrag(
                        draggingThumbX.toFloat() / (width - thumbWidth).toFloat()
                    )
                }
                return true
            }
        }
        return false
    }

    open fun setColors(
        background: Int,
        cache: Int,
        progress: Int,
        circle: Int,
        selected: Int,
        smallLineColor: Int,
    ) {
        backgroundColor = background
        cacheColor = cache
        circleColor = circle
        progressColor = progress
        backgroundSelectedColor = selected
        this.smallLineColor = smallLineColor
    }

    open fun setProgress(progress: Float, animated: Boolean) {
        if (abs(this.progress - 1f) < 0.04f && abs(progress) < 0.04f) {
            animateThumbLoopBackProgress.set(1f, true)
            loopBackWasThumbX = thumbX.toFloat()
        }
        this.progress = progress
        val newThumb = ceil(((width - thumbWidth) * progress).toDouble()).toInt()

        if (animated) {
            if (abs(newThumb - thumbX) > AndroidUtilities.dp(10f)) {
                val progressInterpolated =
                    CubicBezierInterpolator.DEFAULT.getInterpolation(animateThumbProgress)
                fromThumbX =
                    (thumbX * progressInterpolated + fromThumbX * (1f - progressInterpolated))
                        .toInt()
                animateThumbProgress = 0f
            } else if (animateThumbProgress == 1f) {
                animateThumbProgress = 0f
                fromThumbX = thumbX
            }
        }
        thumbX = newThumb

        if (thumbX < 0) {
            thumbX = 0
        } else if (thumbX > width - thumbWidth) {
            thumbX = width - thumbWidth
        }
        if (abs(animatedThumbX - thumbX) > AndroidUtilities.dp(8f)) {
            animatedThumbX = thumbX.toFloat()
        }
    }

    open fun setProgress(progress: Float) {
        setProgress(progress, false)
    }

    open fun setBufferedProgress(value: Float) {
        if (value != bufferedProgress) {
            animateFromBufferedProgress = bufferedProgress
            animateResetBuffering = value < bufferedProgress
            bufferedProgress = value
            bufferedAnimationValue = 0f
        }
    }

    open fun getProgress(): Float {
        return thumbX.toFloat() / (width - thumbWidth).toFloat()
    }

    open fun getThumbX(): Int {
        return (if (isDragging) draggingThumbX else thumbX) + thumbWidth / 2
    }

    open fun setSize(w: Int, h: Int) {
        width = w
        height = h

        parentView?.invalidate()
    }

    open fun getWidth(): Int {
        return width - thumbWidth
    }

    /** Steam supplies caption timestamp positions; rendering is unchanged upstream code. */
    open fun draw(canvas: Canvas, view: View?) {
        rect.left =
            horizontalPadding +
                AndroidUtilities.lerp(
                    thumbWidth / 2f,
                    0f,
                    this@VideoPlayerSeekBar.transitionProgress,
                )
        rect.top =
            AndroidUtilities.lerp(
                (height - lineHeight) / 2f,
                (height - AndroidUtilities.dp(3f) - smallLineHeight).toFloat(),
                this@VideoPlayerSeekBar.transitionProgress,
            )
        rect.bottom =
            AndroidUtilities.lerp(
                (height + lineHeight) / 2f,
                (height - AndroidUtilities.dp(3f)).toFloat(),
                this@VideoPlayerSeekBar.transitionProgress,
            )

        var thumbX = this.thumbX.toFloat()
        animatedThumbX = min(animatedThumbX, thumbX)
        animatedThumbX = AndroidUtilities.lerp(animatedThumbX, thumbX, .5f)
        if (abs(thumbX - animatedThumbX) > 0.005f) {
            parentView!!.invalidate()
        }
        thumbX = animatedThumbX

        var currentThumbX = thumbX
        if (animateThumbProgress != 1f) {
            animateThumbProgress += 16 / 220f
            if (animateThumbProgress >= 1f) {
                animateThumbProgress = 1f
            } else {
                view!!.invalidate()
                val progressInterpolated =
                    CubicBezierInterpolator.DEFAULT.getInterpolation(animateThumbProgress)
                currentThumbX =
                    fromThumbX * (1f - progressInterpolated) + thumbX * progressInterpolated
            }
        }

        var loopBack = animateThumbLoopBackProgress.set(0f)
        if (isDragging) {
            loopBack = 0f
        }

        // background
        rect.right =
            horizontalPadding +
                AndroidUtilities.lerp(
                    width - thumbWidth / 2f,
                    parentView!!.width - horizontalPadding * 2f,
                    this@VideoPlayerSeekBar.transitionProgress,
                )
        setPaintColor(
            if (selected) backgroundSelectedColor else backgroundColor,
            1f - this@VideoPlayerSeekBar.transitionProgress,
        )
        drawProgressBar(canvas, rect, paint)

        if (bufferedAnimationValue != 1f) {
            bufferedAnimationValue += 16 / 100f
            if (bufferedAnimationValue > 1) {
                bufferedAnimationValue = 1f
            } else {
                parentView.invalidate()
            }
        }

        // buffered
        if (animateResetBuffering) {
            if (animateFromBufferedProgress > 0) {
                rect.right =
                    horizontalPadding +
                        AndroidUtilities.lerp(
                            thumbWidth / 2f + animateFromBufferedProgress * (width - thumbWidth),
                            parentView.width - horizontalPadding * 2f,
                            this@VideoPlayerSeekBar.transitionProgress,
                        )
                setPaintColor(
                    if (selected) backgroundSelectedColor else cacheColor,
                    (1f - this@VideoPlayerSeekBar.transitionProgress) *
                        (1f - bufferedAnimationValue),
                )
                drawProgressBar(canvas, rect, paint)
            }
            if (bufferedProgress > 0) {
                rect.right =
                    horizontalPadding +
                        AndroidUtilities.lerp(
                            thumbWidth / 2f + bufferedProgress * (width - thumbWidth),
                            parentView.width - horizontalPadding * 2f,
                            this@VideoPlayerSeekBar.transitionProgress,
                        )
                setPaintColor(
                    if (selected) backgroundSelectedColor else cacheColor,
                    1f - this@VideoPlayerSeekBar.transitionProgress,
                )
                drawProgressBar(canvas, rect, paint)
            }
        } else {
            val currentBufferedProgress =
                animateFromBufferedProgress * (1f - bufferedAnimationValue) +
                    bufferedProgress * bufferedAnimationValue
            if (currentBufferedProgress > 0) {
                rect.right =
                    horizontalPadding +
                        AndroidUtilities.lerp(
                            thumbWidth / 2f + currentBufferedProgress * (width - thumbWidth),
                            parentView.width - horizontalPadding * 2f,
                            this@VideoPlayerSeekBar.transitionProgress,
                        )
                setPaintColor(
                    if (selected) backgroundSelectedColor else cacheColor,
                    1f - this@VideoPlayerSeekBar.transitionProgress,
                )
                drawProgressBar(canvas, rect, paint)
            }
        }

        val newRad = AndroidUtilities.dp((if (isDragging) 8 else 6).toFloat())
        if (currentRadius != newRad.toFloat()) {
            val newUpdateTime = SystemClock.elapsedRealtime()
            var dt = newUpdateTime - lastUpdateTime
            lastUpdateTime = newUpdateTime
            if (dt > 18) {
                dt = 16
            }
            if (currentRadius < newRad) {
                currentRadius += AndroidUtilities.dp(1f) * (dt / 60.0f)
                if (currentRadius > newRad) {
                    currentRadius = newRad.toFloat()
                }
            } else {
                currentRadius -= AndroidUtilities.dp(1f) * (dt / 60.0f)
                if (currentRadius < newRad) {
                    currentRadius = newRad.toFloat()
                }
            }
            parentView.invalidate()
        }
        val circleRadius =
            AndroidUtilities.lerp(currentRadius, 0f, this@VideoPlayerSeekBar.transitionProgress)

        if (loopBack > 0) {
            val wasLeft = rect.left
            rect.right =
                horizontalPadding +
                    AndroidUtilities.lerp(
                        thumbWidth / 2f + (width - thumbWidth),
                        parentView.width - horizontalPadding * 2f,
                        this@VideoPlayerSeekBar.transitionProgress,
                    )
            rect.left = AndroidUtilities.lerp(wasLeft, rect.right, 1f - loopBack)
            if (this@VideoPlayerSeekBar.transitionProgress > 0f && rect.width() > 0) {
                // progress stroke
                strokePaint.alpha =
                    (this@VideoPlayerSeekBar.transitionProgress * 255f * 0.2f).toInt()
                drawProgressBar(canvas, rect, strokePaint)
            }
            setPaintColor(
                ColorUtils.blendARGB(
                    progressColor,
                    smallLineColor,
                    this@VideoPlayerSeekBar.transitionProgress,
                ),
                1f,
            )
            drawProgressBar(canvas, rect, paint)

            rect.left = wasLeft

            setPaintColor(
                ColorUtils.blendARGB(
                    circleColor,
                    if (getProgress() == 0f) Color.TRANSPARENT else smallLineColor,
                    this@VideoPlayerSeekBar.transitionProgress,
                ),
                1f - this@VideoPlayerSeekBar.transitionProgress,
            )
            val wasRight =
                horizontalPadding +
                    AndroidUtilities.lerp(
                        thumbWidth / 2f + loopBackWasThumbX,
                        (parentView.width - horizontalPadding * 2f) *
                            (loopBackWasThumbX / (width - thumbWidth).toFloat()),
                        this@VideoPlayerSeekBar.transitionProgress,
                    )
            canvas.drawCircle(wasRight, rect.centerY(), circleRadius * loopBack, paint)
        }

        // progress
        rect.right =
            horizontalPadding +
                AndroidUtilities.lerp(
                    thumbWidth / 2f + (if (isDragging) draggingThumbX.toFloat() else currentThumbX),
                    (parentView.width - horizontalPadding * 2f) * getProgress(),
                    this@VideoPlayerSeekBar.transitionProgress,
                )
        if (this@VideoPlayerSeekBar.transitionProgress > 0f && rect.width() > 0) {
            // progress stroke
            strokePaint.alpha = (this@VideoPlayerSeekBar.transitionProgress * 255f * 0.2f).toInt()
            drawProgressBar(canvas, rect, strokePaint)
        }
        setPaintColor(
            ColorUtils.blendARGB(
                progressColor,
                smallLineColor,
                this@VideoPlayerSeekBar.transitionProgress,
            ),
            1f,
        )
        drawProgressBar(canvas, rect, paint)

        // circle
        setPaintColor(
            ColorUtils.blendARGB(
                circleColor,
                if (getProgress() == 0f) Color.TRANSPARENT else smallLineColor,
                this@VideoPlayerSeekBar.transitionProgress,
            ),
            1f - this@VideoPlayerSeekBar.transitionProgress,
        )
        canvas.drawCircle(rect.right, rect.centerY(), circleRadius * (1f - loopBack), paint)

        drawTimestampLabel(canvas)
    }

    private fun drawProgressBar(canvas: Canvas, rect: RectF, paint: Paint) {
        val radius =
            AndroidUtilities.dp(
                    AndroidUtilities.lerp(2f, 1f, this@VideoPlayerSeekBar.transitionProgress)
                )
                .toFloat()
        if (timestamps == null || timestamps!!.isEmpty()) {
            canvas.drawRoundRect(rect, radius, radius, paint)
        } else {
            val left =
                horizontalPadding +
                    AndroidUtilities.lerp(
                        thumbWidth / 2f,
                        0f,
                        this@VideoPlayerSeekBar.transitionProgress,
                    )
            val right =
                horizontalPadding +
                    AndroidUtilities.lerp(
                        width - thumbWidth / 2f,
                        parentView!!.width - horizontalPadding * 2f,
                        this@VideoPlayerSeekBar.transitionProgress,
                    )
            AndroidUtilities.rectTmp.set(rect)
            val halfGap = AndroidUtilities.dp(timestampGap * timestampsAppearing) / 2f

            tmpPath.reset()
            val minDur = AndroidUtilities.dp(4f) / (right - left)
            var start = -1
            var end = -1
            for (i in timestamps!!.indices) {
                if (timestamps!![i].first >= minDur) {
                    start = i
                    break
                }
            }
            if (start < 0) {
                start = 0
            }
            for (i in timestamps!!.indices.reversed()) {
                if (1f - timestamps!![i].first!! >= minDur) {
                    end = i + 1
                    break
                }
            }
            if (end < 0) {
                end = timestamps!!.size
            }
            var i = start
            while (i <= end) {
                val from = if (i == start) 0f else timestamps!![i - 1].first
                var to = if (i == end) 1f else timestamps!![i].first
                while (
                    i != end &&
                        i != 0 &&
                        i < timestamps!!.size - 1 &&
                        timestamps!![i].first!! - from <= minDur
                ) {
                    i++
                    to = timestamps!![i].first!!
                }

                AndroidUtilities.rectTmp.left =
                    AndroidUtilities.lerp(left, right, from) + (if (i > 0) halfGap else 0f)
                AndroidUtilities.rectTmp.right =
                    AndroidUtilities.lerp(left, right, to) - (if (i < end) halfGap else 0f)

                val last = AndroidUtilities.rectTmp.right > rect.right
                if (last) {
                    AndroidUtilities.rectTmp.right = rect.right
                }
                if (AndroidUtilities.rectTmp.right < rect.left) {
                    ++i
                    continue
                }
                if (AndroidUtilities.rectTmp.left < rect.left) {
                    AndroidUtilities.rectTmp.left = rect.left
                }

                if (i == start || last && AndroidUtilities.rectTmp.left >= rect.left) {
                    tmpRadii[7] = radius
                    tmpRadii[6] = tmpRadii[7]
                    tmpRadii[1] = tmpRadii[6]
                    tmpRadii[0] = tmpRadii[1]
                    tmpRadii[5] = radius * 0.7f * timestampsAppearing
                    tmpRadii[4] = tmpRadii[5]
                    tmpRadii[3] = tmpRadii[4]
                    tmpRadii[2] = tmpRadii[3]
                } else if (i >= end) {
                    tmpRadii[7] = radius * 0.7f * timestampsAppearing
                    tmpRadii[6] = tmpRadii[7]
                    tmpRadii[1] = tmpRadii[6]
                    tmpRadii[0] = tmpRadii[1]
                    tmpRadii[5] = radius
                    tmpRadii[4] = tmpRadii[5]
                    tmpRadii[3] = tmpRadii[4]
                    tmpRadii[2] = tmpRadii[3]
                } else {
                    tmpRadii[5] = radius * 0.7f * timestampsAppearing
                    tmpRadii[4] = tmpRadii[5]
                    tmpRadii[3] = tmpRadii[4]
                    tmpRadii[2] = tmpRadii[3]
                    tmpRadii[7] = tmpRadii[2]
                    tmpRadii[6] = tmpRadii[7]
                    tmpRadii[1] = tmpRadii[6]
                    tmpRadii[0] = tmpRadii[1]
                }
                tmpPath.addRoundRect(AndroidUtilities.rectTmp, tmpRadii, Path.Direction.CW)

                if (last) {
                    break
                }
                ++i
            }
            canvas.drawPath(tmpPath, paint)
        }
    }

    private fun drawTimestampLabel(canvas: Canvas) {
        if (timestamps == null || timestamps!!.isEmpty()) {
            return
        }

        val progress =
            if (isDragging || pressedDelayed) (draggingThumbX / (width - thumbWidth).toFloat())
            else (animatedThumbX / (width - thumbWidth).toFloat())

        var timestampIndex = -1
        for (i in timestamps!!.indices.reversed()) {
            if (timestamps!![i].first!! - 0.001f <= progress) {
                timestampIndex = i
                break
            }
        }

        if (timestampLabel == null) {
            timestampLabel = arrayOfNulls<StaticLayout?>(2)
        }

        val left =
            horizontalPadding +
                AndroidUtilities.lerp(
                    thumbWidth / 2f,
                    0f,
                    this@VideoPlayerSeekBar.transitionProgress,
                )
        val right =
            horizontalPadding +
                AndroidUtilities.lerp(
                    width - thumbWidth / 2f,
                    parentView!!.width - horizontalPadding * 2f,
                    this@VideoPlayerSeekBar.transitionProgress,
                )
        val rightPadded = horizontalPadding + (width - thumbWidth / 2f)
        val width = abs(left - rightPadded) - AndroidUtilities.dp(16f)

        if (lastWidth > 0 && abs(lastWidth - width) > 0.01f) {
            if (timestampLabel!![0] != null) {
                timestampLabel!![0] = makeStaticLayout(timestampLabel!![0]!!.text, width.toInt())
            }
            if (timestampLabel!![1] != null) {
                timestampLabel!![1] = makeStaticLayout(timestampLabel!![1]!!.text, width.toInt())
            }
        }
        lastWidth = width

        if (timestampIndex != currentTimestamp) {
            timestampLabel!![1] = timestampLabel!![0]
            if (isDragging) {
                AndroidUtilities.vibrateCursor(parentView)
            }
            if (timestampIndex >= 0 && timestampIndex < timestamps!!.size) {
                val label = timestamps!![timestampIndex].second
                if (label == null) {
                    timestampLabel!![0] = null
                } else {
                    timestampLabel!![0] = makeStaticLayout(label, width.toInt())
                }
            } else {
                timestampLabel!![0] = null
            }
            timestampChangeT = 0f
            if (timestampIndex == -1) {
                timestampChangeDirection = -1
            } else if (currentTimestamp == -1) {
                timestampChangeDirection = 1
            } else if (timestampIndex < currentTimestamp) {
                timestampChangeDirection = -1
            } else if (timestampIndex > currentTimestamp) {
                timestampChangeDirection = 1
            }
            lastTimestamp = currentTimestamp
            currentTimestamp = timestampIndex
        }
        if (timestampChangeT < 1f) {
            val tx = min(17, abs(SystemClock.elapsedRealtime() - lastTimestampUpdate))
            val duration = if (timestamps!!.size > 8) 160f else 220f
            timestampChangeT = min(timestampChangeT + tx / duration, 1f)
            parentView.invalidate()
            lastTimestampUpdate = SystemClock.elapsedRealtime()
        }
        if (timestampsAppearing < 1f) {
            val tx = min(17, abs(SystemClock.elapsedRealtime() - lastTimestampUpdate))
            timestampsAppearing = min(timestampsAppearing + tx / 200f, 1f)
            parentView.invalidate()
            lastTimestampsAppearingUpdate = SystemClock.elapsedRealtime()
        }
        val changeT = CubicBezierInterpolator.DEFAULT.getInterpolation(timestampChangeT)

        canvas.withSave {
            val bottom =
                AndroidUtilities.lerp(
                    (this@VideoPlayerSeekBar.height + lineHeight) / 2f,
                    (this@VideoPlayerSeekBar.height - AndroidUtilities.dp(3f)).toFloat(),
                    this@VideoPlayerSeekBar.transitionProgress,
                )
            canvas.translate(
                left + (right - rightPadded) * (this@VideoPlayerSeekBar.transitionProgress),
                bottom + AndroidUtilities.dp(12f),
            )
            if (timestampLabel!![1] != null) {
                canvas.withSave {
                    if (timestampChangeDirection != 0) {
                        canvas.translate(
                            AndroidUtilities.dp(8f) +
                                AndroidUtilities.dp(16f).toFloat() *
                                    (-timestampChangeDirection).toFloat() *
                                    changeT,
                            0f,
                        )
                    }
                    canvas.translate(0f, -timestampLabel!![1]!!.height / 2f)
                    timestampLabelPaint!!.alpha =
                        (255f *
                                (1f - this@VideoPlayerSeekBar.transitionProgress) *
                                (1f - changeT) *
                                timestampsAppearing)
                            .toInt()
                    timestampLabel!![1]!!.draw(canvas)
                }
            }
            if (timestampLabel!![0] != null) {
                canvas.withSave {
                    if (timestampChangeDirection != 0) {
                        canvas.translate(
                            AndroidUtilities.dp(8f) +
                                AndroidUtilities.dp(16f).toFloat() *
                                    timestampChangeDirection.toFloat() *
                                    (1f - changeT),
                            0f,
                        )
                    }
                    canvas.translate(0f, -timestampLabel!![0]!!.height / 2f)
                    timestampLabelPaint!!.alpha =
                        (255f *
                                (1f - this@VideoPlayerSeekBar.transitionProgress) *
                                changeT *
                                timestampsAppearing)
                            .toInt()
                    timestampLabel!![0]!!.draw(canvas)
                }
            }
        }
    }

    private fun makeStaticLayout(text: CharSequence?, width: Int): StaticLayout {
        var text = text
        if (timestampLabelPaint == null) {
            timestampLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            timestampLabelPaint!!.textSize = AndroidUtilities.dp(12f).toFloat()
            timestampLabelPaint!!.color = -0x1
        }
        if (text == null) {
            text = ""
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, timestampLabelPaint!!, width)
            .setMaxLines(1)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setEllipsizedWidth(min(AndroidUtilities.dp(400f), width))
            .build()
    }

    private fun setPaintColor(color: Int, alpha: Float) {
        var color = color
        if (alpha < 1f) {
            color = ColorUtils.setAlphaComponent(color, (Color.alpha(color) * alpha).toInt())
        }
        paint.color = color
    }

    companion object {
        private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint: Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.BLACK
                strokeWidth = 1f
            }
        private var thumbWidth: Int = 0
        private val tmpRadii: FloatArray = FloatArray(8)
        private val tmpPath: Path = Path()
    }
}
