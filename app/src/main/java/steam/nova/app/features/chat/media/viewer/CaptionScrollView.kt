// Nagram/Telegram GPL-2.0-or-later. PhotoViewer.CaptionScrollView, source lines 3850-4170.
// Steam has one caption TextView instead of Telegram's translated-text switcher.
package steam.nova.app.features.chat.media.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.OverScroller
import android.widget.TextView
import androidx.core.graphics.withClip
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.dp

// Created with the viewer's caption views, never inflated from XML.
@SuppressLint("ViewConstructor")
open class CaptionScrollView(
    context: Context,
    private val captionTextViewSwitcher: TextView?,
    private val captionContainer: FrameLayout?,
) : NestedScrollView(context) {

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val springAnimation: SpringAnimation

    private var nestedScrollStarted: Boolean = false
    private var overScrollY: Float = 0f
    private var velocitySign: Float = 0f
    private var velocityY: Float = 0f

    private var abortAnimatedScrollMethod: Method? = null
    private var scroller: OverScroller? = null

    private var isLandscape: Boolean = false
    private var textHash: Int = 0
    private var prevHeight: Int = 0

    var backgroundAlpha: Float = 1f
    var dontChangeTopMargin: Boolean = false
    private var pendingTopMargin: Int = -1

    override fun getTopFadingEdgeStrength(): Float = 1f

    override fun getBottomFadingEdgeStrength(): Float = 1f

    init {

        clipChildren = false
        overScrollMode = OVER_SCROLL_NEVER

        paint.color = Color.BLACK
        setFadingEdgeLength(dp(12f))
        isVerticalFadingEdgeEnabled = true
        setWillNotDraw(false)

        springAnimation =
            SpringAnimation(captionTextViewSwitcher, DynamicAnimation.TRANSLATION_Y, 0f)
        springAnimation.spring.setStiffness(100f)
        springAnimation.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS)
        springAnimation.addUpdateListener { _, value, velocity ->
            overScrollY = value
            velocityY = velocity
            onScrollUpdate()
        }
        springAnimation.addEndListener { _, _, _, _ -> onScrollEnd() }
        springAnimation.spring.setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)

        try {
            abortAnimatedScrollMethod =
                NestedScrollView::class.java.getDeclaredMethod("abortAnimatedScroll")
            abortAnimatedScrollMethod!!.isAccessible = true
        } catch (e: Exception) {
            abortAnimatedScrollMethod = null
            FileLog.e(e)
        }

        try {
            val scrollerField = NestedScrollView::class.java.getDeclaredField("mScroller")
            scrollerField.isAccessible = true
            scroller = scrollerField.get(this) as OverScroller?
        } catch (e: Exception) {
            scroller = null
            FileLog.e(e)
        }
    }

    // NestedScrollView handles the touch sequence; this only rejects the empty caption margin.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (
            ev.action == MotionEvent.ACTION_DOWN &&
                ev.y < captionContainer!!.top - scrollY + captionTextViewSwitcher!!.translationY
        ) {
            return false
        }
        return super.onTouchEvent(ev)
    }

    protected override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        updateTopMargin(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec),
        )
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    open fun updateTopMargin() {
        updateTopMargin(width, height)
    }

    private fun updateTopMargin(width: Int, height: Int) {
        val marginTop = calculateNewContainerMarginTop(width, height)
        if (marginTop >= 0) {
            if (dontChangeTopMargin) {
                pendingTopMargin = marginTop
            } else {
                (captionContainer!!.layoutParams as MarginLayoutParams).topMargin = marginTop
                pendingTopMargin = -1
            }
        }
    }

    open fun calculateNewContainerMarginTop(width: Int, height: Int): Int {
        if (width == 0 || height == 0) {
            return -1
        }

        val textView = captionTextViewSwitcher
        val text = textView!!.text

        val textHash = text!!.hashCode()
        val isLandscape =
            resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE

        if (
            this.textHash == textHash &&
                this.isLandscape == isLandscape &&
                this.prevHeight == height
        ) {
            return -1
        }

        this.textHash = textHash
        this.isLandscape = isLandscape
        this.prevHeight = height

        textView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST),
        )

        val layout = textView.layout
        val lineCount = layout!!.lineCount

        if (isLandscape && lineCount <= 2 || !isLandscape && lineCount <= 5) {
            return height - textView.measuredHeight - captionTextViewSwitcher.paddingBottom
        }

        var i = min(if (isLandscape) 2 else 5, lineCount)

        cycle@ while (i > 1) {
            for (j in layout.getLineStart(i - 1) until layout.getLineEnd(i - 1)) {
                if (!Character.isWhitespace(text[j])) {
                    break@cycle
                }
            }
            i--
        }

        val lineHeight = textView.paint!!.getFontMetricsInt(null)
        return height - lineHeight * i - dp(8f)
    }

    open fun reset() {
        scrollTo(0, 0)
    }

    open fun stopScrolling() {
        if (abortAnimatedScrollMethod != null) {
            try {
                abortAnimatedScrollMethod!!.invoke(this)
            } catch (e: Exception) {
                FileLog.e(e)
            }
        }
    }

    override fun fling(velocityY: Int) {
        super.fling(velocityY)
        this.velocitySign = sign(velocityY.toFloat())
        this.velocityY = 0f
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean {
        val consumedBuffer = consumed ?: IntArray(2)
        consumedBuffer[1] = 0

        if (nestedScrollStarted && (overScrollY > 0 && dy > 0 || overScrollY < 0 && dy < 0)) {
            val delta = overScrollY - dy

            if (overScrollY > 0) {
                if (delta < 0) {
                    overScrollY = 0f
                    consumedBuffer[1] += (dy + delta).toInt()
                } else {
                    overScrollY = delta
                    consumedBuffer[1] += dy
                }
            } else {
                if (delta > 0) {
                    overScrollY = 0f
                    consumedBuffer[1] += (dy + delta).toInt()
                } else {
                    overScrollY = delta
                    consumedBuffer[1] += dy
                }
            }
            onScrollUpdate()

            captionTextViewSwitcher!!.translationY = overScrollY
            return true
        }

        return false
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray,
    ) {
        if (dyUnconsumed != 0) {
            val topMargin =
                dp(56f) +
                    (ViewCompat.getRootWindowInsets(this)
                        ?.getInsets(WindowInsetsCompat.Type.systemBars())
                        ?.top ?: 0)
            val dy =
                (dyUnconsumed *
                        (1f - abs((-overScrollY / max(1, captionContainer!!.top - topMargin)))))
                    .roundToInt()

            if (dy != 0) {
                if (!nestedScrollStarted) {
                    if (!springAnimation.isRunning) {
                        var consumedY: Int
                        var velocity =
                            if (scroller != null) scroller!!.currVelocity else java.lang.Float.NaN
                        if (!java.lang.Float.isNaN(velocity) && velocity > 0) {
                            val clampedVelocity =
                                min(
                                    (if (
                                            resources.configuration.orientation ==
                                                android.content.res.Configuration
                                                    .ORIENTATION_LANDSCAPE
                                        )
                                            3000
                                        else 5000)
                                        .toFloat(),
                                    velocity,
                                )
                            consumedY = (dy * clampedVelocity / velocity).toInt()
                            velocity = clampedVelocity * -velocitySign
                        } else {
                            consumedY = dy
                            velocity = 0f
                        }
                        if (consumedY != 0) {
                            overScrollY -= consumedY.toFloat()
                            captionTextViewSwitcher!!.translationY = overScrollY
                        }
                        startSpringAnimationIfNotRunning(velocity)
                    }
                } else {
                    overScrollY -= dy.toFloat()
                    captionTextViewSwitcher!!.translationY = overScrollY
                }
            }

            onScrollUpdate()
        }
    }

    private fun startSpringAnimationIfNotRunning(velocityY: Float) {
        if (!springAnimation.isRunning) {
            springAnimation.setStartVelocity(velocityY)
            springAnimation.start()
        }
    }

    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        if (type == ViewCompat.TYPE_TOUCH) {
            springAnimation.cancel()
            nestedScrollStarted = true
            overScrollY = captionTextViewSwitcher!!.translationY
            onScrollStart()
        }
        return true
    }

    override fun computeScroll() {
        super.computeScroll()
        if (
            !nestedScrollStarted && overScrollY != 0f && scroller != null && scroller!!.isFinished
        ) {
            startSpringAnimationIfNotRunning(0f)
        }
        onScrollUpdate()
    }

    override fun stopNestedScroll(type: Int) {
        if (nestedScrollStarted && type == ViewCompat.TYPE_TOUCH) {
            nestedScrollStarted = false
            if (overScrollY != 0f && scroller != null && scroller!!.isFinished) {
                startSpringAnimationIfNotRunning(velocityY)
            }
            onScrollEnd()
        }
    }

    protected open fun onScrollStart() {}

    protected open fun onScrollUpdate() {}

    protected open fun onScrollEnd() {}

    override fun draw(canvas: Canvas) {
        val width = getWidth()
        val height = getHeight()
        val scrollY = getScrollY()

        canvas.withClip(0, scrollY, width, height + scrollY) {
            paint.alpha = (backgroundAlpha * 127).toInt()
            canvas.drawRect(
                0f,
                captionContainer!!.top + captionTextViewSwitcher!!.translationY,
                width.toFloat(),
                (height + scrollY).toFloat(),
                paint,
            )

            super.draw(canvas)
        }
    }

    protected override fun onDetachedFromWindow() {
        springAnimation.cancel()
        stopScrolling()
        super.onDetachedFromWindow()
    }
}
