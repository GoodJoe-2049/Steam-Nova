package steam.nova.app.features.chat.scroll

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ReplacementSpan
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import androidx.core.graphics.withSave
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Adapted from Nagram/Telegram CounterView; attribution is in assets/licenses/nagram-chat-scroll.txt. */
internal class SteamChatUnreadCounter(private val invalidate: () -> Unit) {
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        color = 0xffdcdedf.toInt()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff1a9fff.toInt() }
    private val rect = RectF()
    private var animator: ValueAnimator? = null
    private var progress = 1f
    private var mode = 0
    private var textWidth = 12
    private var oldWidth = 12
    private var count = 0
    private var current: StaticLayout? = null
    private var old: StaticLayout? = null
    private var stable: StaticLayout? = null
    private var incoming: StaticLayout? = null
    private var increment = false

    private fun layout(value: CharSequence, size: Int) = StaticLayout.Builder.obtain(value, 0, value.length, text, size)
        .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()

    fun setCount(value: Int, animated: Boolean) {
        val next = value.coerceAtLeast(0)
        if (next == count) return
        animator?.cancel()
        animator = null
        val previous = count.toString()
        val number = next.toString()
        val shouldAnimate = animated && kotlin.math.abs(next.toLong() - count) <= 99
        mode = when { count == 0 -> 1; next == 0 -> 2; else -> 3 }
        oldWidth = textWidth
        old = current
        stable = null
        incoming = null
        if (next > 0) {
            textWidth = max(12, ceil(text.measureText(number)).toInt())
            current = layout(number, textWidth)
        }
        if (mode == 3 && previous.length == number.length) {
            val outgoingText = SpannableStringBuilder(previous)
            val incomingText = SpannableStringBuilder(number)
            val stableText = SpannableStringBuilder(number)
            for (index in number.indices) {
                if (previous[index] == number[index]) {
                    outgoingText.setSpan(EmptyDigit(), index, index + 1, 0)
                    incomingText.setSpan(EmptyDigit(), index, index + 1, 0)
                } else stableText.setSpan(EmptyDigit(), index, index + 1, 0)
            }
            old = layout(outgoingText, oldWidth)
            incoming = layout(incomingText, oldWidth)
            stable = layout(stableText, oldWidth)
        }
        increment = next > count
        count = next
        if (!shouldAnimate) {
            progress = 1f
            old = null; incoming = null; stable = null
            invalidate()
            return
        }
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when (mode) { 1 -> 220L; 2 -> 150L; else -> 430L }
            interpolator = if (mode == 1) OvershootInterpolator(2f) else DEFAULT
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    progress = 1f; old = null; incoming = null; stable = null; invalidate()
                }
            })
            start()
        }
    }

    fun draw(canvas: Canvas) {
        if (count == 0 && progress == 1f) return
        canvas.withSave {
            if (progress != 1f && mode != 3) {
                val scale = if (mode == 1) progress else 1f - progress
                canvas.scale(scale, scale, 28f, 14f)
            }
            val half = min(1f, progress * 2f)
            val w = if (progress != 1f && mode == 3) oldWidth + (textWidth - oldWidth) * half else textWidth.toFloat()
            rect.set(28f - (w + 11f) / 2f, 2.5f, 28f + (w + 11f) / 2f, 25.5f)
            if (progress != 1f && mode == 3 && increment) {
                val pulse = if (progress <= .5f) EASE_OUT.getInterpolation(progress * 2f)
                else EASE_IN.getInterpolation(2f - progress * 2f)
                canvas.scale(1f + .1f * pulse, 1f + .1f * pulse, 28f, 14f)
            }
            canvas.drawRoundRect(rect, 11.5f, 11.5f, fill)
            canvas.clipRect(rect)
            fun drawLayout(value: StaticLayout?, y: Float, alpha: Float) {
                if (value == null) return
                canvas.withSave {
                    text.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
                    canvas.translate(28f - w / 2f, 6.5f + y)
                    value.draw(canvas)
                }
            }
            if (progress != 1f && mode == 3) {
                // Page-down uses reverseCounter(): decreasing numbers enter from below.
                val direction = if (increment) -1f else 1f
                drawLayout(incoming ?: current, direction * 13f * (1f - half), half)
                drawLayout(old, -direction * 13f * half, 1f - half)
                drawLayout(stable, 0f, 1f)
            } else drawLayout(current, 0f, 1f)
            text.alpha = 255
        }
    }

    fun close() { animator?.cancel(); animator = null }

    private class EmptyDigit : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
            ceil(paint.measureText(text, start, end)).toInt()
        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float,
                          top: Int, y: Int, bottom: Int, paint: Paint) = Unit
    }

    companion object {
        private val DEFAULT = PathInterpolator(.25f, .1f, .25f, 1f)
        private val EASE_OUT = PathInterpolator(0f, 0f, .58f, 1f)
        private val EASE_IN = PathInterpolator(.42f, 0f, 1f, 1f)
    }
}
