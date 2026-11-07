// Nagram/Telegram GPL-2.0-or-later; numeric text and ordinary-media branches.
package steam.nova.app.features.chat.media.viewer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import androidx.core.graphics.withSave
import java.util.ArrayList
import java.util.stream.IntStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.lerp

class AnimatedTextView {

    open class AnimatedTextDrawable
    @JvmOverloads
    constructor(
        private var splitByWords: Boolean = false,
        private var preserveIndex: Boolean = false,
        private var startFromEnd: Boolean = false,
        private var enforceByLetter: Boolean = false,
    ) : Drawable() {

        open val paint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        open var gravity: Int = 0

        private var isRTL: Boolean = false

        var animateToWidth: Float = 0f
            private set

        var height: Float = 0f
            private set

        private var currentParts: Array<Part>? = null
        private var currentText: CharSequence? = null

        private var oldWidth: Float = 0f
        private var oldHeight: Float = 0f
        private var oldParts: Array<Part>? = null
        private var oldText: CharSequence? = null

        private var t: Float = 0f
        private var moveDown: Boolean = true
        private var animator: ValueAnimator? = null
        private var toSetText: CharSequence? = null
        private var toSetTextMoveDown: Boolean = false

        private var animateDelay: Long = 0
        private var animateDuration: Long = 320
        private var animateInterpolator: TimeInterpolator? = CubicBezierInterpolator.EASE_OUT_QUINT
        private var animateWave: Float = -1f
        private var moveAmplitude: Float = .3f

        private var scaleAmplitude: Float = 0f

        private var alpha: Int = 255
        private val bounds: Rect = Rect()

        private var onAnimationFinishListener: Runnable? = null
        private var allowCancel: Boolean = false
        var ignoreRTL: Boolean = false
        var updateAll: Boolean = false

        private var overrideFullWidth: Int = 0

        open var rightPadding: Float = 0f
            set(rightPadding) {
                field = rightPadding
                invalidateSelf()
            }

        private var ellipsizeByGradient: Boolean = false
        private var ellipsizeGradient: LinearGradient? = null
        private var ellipsizeGradientMatrix: Matrix? = null
        private var ellipsizePaint: Paint? = null

        private var includeFontPadding: Boolean = true

        var centerY: Boolean = true

        open val isAnimating: Boolean
            get() {
                return animator != null && animator!!.isRunning
            }

        open var text: CharSequence?
            get() {
                return currentText
            }
            set(text) {
                setText(text, true)
            }

        open val width: Float
            get() {
                return max(animateToWidth, oldWidth)
            }

        open val currentWidth: Float
            get() {
                if (currentParts != null && oldParts != null) {
                    return lerp(oldWidth, animateToWidth, t)
                }
                return animateToWidth
            }

        open // relayout parts:
        var textSize: Float
            get() {
                return paint.textSize
            }
            set(textSizePx) {
                val lastTextPaint = paint.textSize
                paint.textSize = textSizePx
                if (abs(lastTextPaint - textSizePx) > 0.5f) {
                    val width = if (overrideFullWidth > 0) overrideFullWidth else bounds.width()
                    if (currentParts != null) {
                        animateToWidth = 0f
                        height = 0f
                        for (i in currentParts!!.indices) {
                            val layout =
                                makeLayout(
                                    currentParts!![i].layout!!.text,
                                    width - ceil(min(animateToWidth, oldWidth).toDouble()).toInt(),
                                )
                            currentParts!![i] =
                                Part(
                                    layout,
                                    currentParts!![i].offset,
                                    currentParts!![i].toOppositeIndex,
                                )
                            animateToWidth += currentParts!![i].width
                            height = max(height, currentParts!![i].layout!!.height.toFloat())
                        }
                    }
                    if (oldParts != null) {
                        oldWidth = 0f
                        oldHeight = 0f
                        for (i in oldParts!!.indices) {
                            val layout =
                                makeLayout(
                                    oldParts!![i].layout!!.text,
                                    width - ceil(min(animateToWidth, oldWidth).toDouble()).toInt(),
                                )
                            oldParts!![i] =
                                Part(layout, oldParts!![i].offset, oldParts!![i].toOppositeIndex)
                            oldWidth += oldParts!![i].width
                            oldHeight = max(oldHeight, oldParts!![i].layout!!.height.toFloat())
                        }
                    }
                    invalidateSelf()
                }
            }

        private var shadowed: Boolean = false
        private var shadowRadius: Float = 0f
        private var shadowDx: Float = 0f
        private var shadowDy: Float = 0f
        private var shadowColor: Int = 0

        open var textColor: Int
            get() {
                return paint.color
            }
            set(color) {
                paint.color = color
                alpha = Color.alpha(color)
            }

        open val isNotEmpty: Float
            get() {
                return lerp(
                    if (oldText == null || oldText!!.isEmpty()) 0f else 1f,
                    if (currentText == null || currentText!!.isEmpty()) 0f else 1f,
                    if (oldText == null) 1f else t,
                )
            }

        private var widthUpdatedListener: Runnable? = null

        private open inner class Part(
            var layout: StaticLayout?,
            offset: Float,
            var toOppositeIndex: Int,
        ) {
            var offset: Float = 0f
            var left: Float = 0f
            var width: Float = 0f

            init {
                layout(offset)
            }

            open fun detach() {}

            open fun layout(offset: Float) {
                this.offset = offset
                this.left =
                    if (layout == null || layout!!.lineCount <= 0) 0f else layout!!.getLineLeft(0)
                this.width =
                    if (layout == null || layout!!.lineCount <= 0) 0f else layout!!.getLineWidth(0)
            }

            open fun draw(canvas: Canvas, alpha: Float) {
                layout!!.draw(canvas)
            }
        }

        open fun setOverrideFullWidth(value: Int) {
            overrideFullWidth = value
        }

        private fun applyAlphaInternal(t: Float) {
            paint.alpha = (alpha * t).toInt()
            if (shadowed) {
                paint.setShadowLayer(
                    shadowRadius,
                    shadowDx,
                    shadowDy,
                    Theme.multAlpha(shadowColor, t),
                )
            }
        }

        // Glyph offsets handle RTL explicitly; these flags describe physical alignment.
        @android.annotation.SuppressLint("RtlHardcoded")
        override fun draw(canvas: Canvas) {
            if (ellipsizeByGradient) {
                AndroidUtilities.rectTmp.set(bounds)
                AndroidUtilities.rectTmp.right -= this.rightPadding
                canvas.saveLayerAlpha(AndroidUtilities.rectTmp, 255)
            }
            canvas.save()
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            val fullWidth = bounds.width()
            val fullHeight = bounds.height()
            if (currentParts != null && oldParts != null && t != 1f) {
                val width = lerp(oldWidth, animateToWidth, t)
                val height = lerp(oldHeight, this.height, t)
                if (centerY) canvas.translate(0f, (fullHeight - height) / 2f)
                for (i in currentParts!!.indices) {
                    val current = currentParts!![i]
                    val j = current.toOppositeIndex
                    var x = current.offset
                    var y = 0f
                    if (isRTL && !ignoreRTL) {
                        x = animateToWidth - (x + current.width)
                    }
                    var localT = t
                    if (animateWave > 0) {
                        localT =
                            AndroidUtilities.cascade(
                                t,
                                i.toFloat(),
                                currentParts!!.size.toFloat(),
                                animateWave,
                            )
                    }
                    if (j >= 0) {
                        val old = oldParts!![j]
                        var oldX = old.offset
                        if (isRTL && !ignoreRTL) {
                            oldX = oldWidth - (oldX + old.width)
                        }
                        x = lerp(oldX - old.left, x - current.left, t)
                        applyAlphaInternal(1f)
                    } else {
                        x -= current.left
                        y =
                            -paint.textSize *
                                moveAmplitude *
                                (1f - localT) *
                                (if (moveDown) 1f else -1f)
                        applyAlphaInternal(localT)
                    }
                    canvas.withSave {
                        val lwidth = if (j >= 0) width else animateToWidth
                        if ((gravity or Gravity.LEFT.inv()) != 0.inv()) {
                            if ((gravity or Gravity.RIGHT.inv()) == 0.inv()) {
                                x += fullWidth - lwidth
                            } else if ((gravity or Gravity.CENTER_HORIZONTAL.inv()) == 0.inv()) {
                                x += (fullWidth - lwidth) / 2f
                            } else if (isRTL && !ignoreRTL) {
                                x += fullWidth - lwidth
                            }
                        }
                        canvas.translate(x, y)
                        if (j < 0 && scaleAmplitude > 0) {
                            val s = lerp(1f - scaleAmplitude, 1f, t)
                            canvas.scale(s, s, current.width / 2f, current.layout!!.height / 2f)
                        }
                        current.draw(canvas, if (j >= 0) 1f else t)
                    }
                }
                for (i in oldParts!!.indices) {
                    val old = oldParts!![i]
                    val j = old.toOppositeIndex
                    if (j >= 0) {
                        continue
                    }
                    var localT = t
                    if (animateWave > 0) {
                        localT =
                            AndroidUtilities.cascade(
                                t,
                                i.toFloat(),
                                oldParts!!.size.toFloat(),
                                animateWave,
                            )
                    }
                    var x = old.offset
                    val y = paint.textSize * moveAmplitude * localT * (if (moveDown) 1f else -1f)
                    applyAlphaInternal(1f - localT)
                    canvas.withSave {
                        if (isRTL && !ignoreRTL) {
                            x = oldWidth - (x + old.width)
                        }
                        x -= old.left
                        if ((gravity or Gravity.LEFT.inv()) != 0.inv()) {
                            if ((gravity or Gravity.RIGHT.inv()) == 0.inv()) {
                                x += fullWidth - oldWidth
                            } else if ((gravity or Gravity.CENTER_HORIZONTAL.inv()) == 0.inv()) {
                                x += (fullWidth - oldWidth) / 2f
                            } else if (isRTL && !ignoreRTL) {
                                x += fullWidth - oldWidth
                            }
                        }
                        canvas.translate(x, y)
                        if (scaleAmplitude > 0) {
                            val s = lerp(1f, 1f - scaleAmplitude, t)
                            canvas.scale(s, s, old.width / 2f, old.layout!!.height / 2f)
                        }
                        old.draw(canvas, 1f - localT)
                    }
                }
            } else {
                if (centerY) canvas.translate(0f, (fullHeight - height) / 2f)
                if (currentParts != null) {
                    applyAlphaInternal(1f)
                    for (i in currentParts!!.indices) {
                        canvas.withSave {
                            val current = currentParts!![i]
                            var x = current.offset
                            if (isRTL && !ignoreRTL) {
                                x = animateToWidth - (x + current.width)
                            }
                            x -= current.left
                            if ((gravity or Gravity.LEFT.inv()) != 0.inv()) {
                                if ((gravity or Gravity.RIGHT.inv()) == 0.inv()) {
                                    x += fullWidth - animateToWidth
                                } else if (
                                    (gravity or Gravity.CENTER_HORIZONTAL.inv()) == 0.inv()
                                ) {
                                    x += (fullWidth - animateToWidth) / 2f
                                } else if (isRTL && !ignoreRTL) {
                                    x += fullWidth - animateToWidth
                                }
                            }
                            canvas.translate(x, 0f)
                            current.draw(canvas, 1f)
                        }
                    }
                }
            }
            canvas.restore()
            if (ellipsizeByGradient) {
                val w = AndroidUtilities.dp(16f).toFloat()
                if (ellipsizeGradient == null) {
                    ellipsizeGradient =
                        LinearGradient(
                            0f,
                            0f,
                            w,
                            0f,
                            intArrayOf(0x00ff0000, -0x10000),
                            floatArrayOf(0f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                    ellipsizeGradientMatrix = Matrix()
                    ellipsizePaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    ellipsizePaint!!.shader = ellipsizeGradient
                    ellipsizePaint!!.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                }
                ellipsizeGradientMatrix!!.reset()
                ellipsizeGradientMatrix!!.postTranslate(
                    bounds.right.toFloat() - this.rightPadding - w,
                    0f,
                )
                ellipsizeGradient!!.setLocalMatrix(ellipsizeGradientMatrix)
                canvas.drawRect(
                    bounds.right.toFloat() - this.rightPadding - w,
                    bounds.top.toFloat(),
                    bounds.right - this.rightPadding + AndroidUtilities.dp(1f),
                    bounds.bottom.toFloat(),
                    ellipsizePaint!!,
                )
                canvas.restore()
            }
        }

        open fun cancelAnimation() {
            if (animator != null) {
                animator!!.cancel()
            }
        }

        open fun setText(text: CharSequence?, animated: Boolean) {
            setText(text, animated, true)
        }

        open fun setText(text: CharSequence?, animated: Boolean, moveDown: Boolean) {
            var text = text
            var animated = animated
            if (this.currentText == null || text == null) {
                animated = false
            }
            if (text == null) {
                text = ""
            }
            val width = if (overrideFullWidth > 0) overrideFullWidth else bounds.width()
            if (animated) {
                if (TextUtils.equals(text, currentText)) {
                    return
                }

                if (allowCancel) {
                    if (animator != null) {
                        animator!!.cancel()
                        animator = null
                    }
                } else if (isAnimating) {
                    toSetText = text
                    toSetTextMoveDown = moveDown
                    return
                }

                oldText = currentText
                currentText = text

                val currentParts = ArrayList<Part>()
                val oldParts = ArrayList<Part>()

                height = 0f
                animateToWidth = height
                oldHeight = 0f
                oldWidth = oldHeight
                isRTL = AndroidUtilities.isRTL(currentText)

                // order execution matters
                val onEqualRegion = RegionCallback { part, _, _ ->
                    val layout =
                        makeLayout(
                            part,
                            width - ceil(min(animateToWidth, oldWidth).toDouble()).toInt(),
                        )
                    val currentPart = Part(layout, animateToWidth, oldParts.size)
                    val oldPart = Part(layout, oldWidth, oldParts.size)
                    currentParts.add(currentPart)
                    oldParts.add(oldPart)
                    val partWidth = currentPart.width
                    animateToWidth += partWidth
                    oldWidth += partWidth
                    height = max(height, layout.height.toFloat())
                    oldHeight = max(oldHeight, layout.height.toFloat())
                }
                val onNewPart = RegionCallback { part, _, _ ->
                    val layout = makeLayout(part, width - ceil(animateToWidth.toDouble()).toInt())
                    val currentPart = Part(layout, animateToWidth, -1)
                    currentParts.add(currentPart)
                    animateToWidth += currentPart.width
                    height = max(height, layout.height.toFloat())
                }
                val onOldPart = RegionCallback { part, _, _ ->
                    val layout = makeLayout(part, width - ceil(oldWidth.toDouble()).toInt())
                    val oldPart = Part(layout, oldWidth, -1)
                    oldParts.add(oldPart)
                    oldWidth += oldPart.width
                    oldHeight = max(oldHeight, layout.height.toFloat())
                }

                val from = if (splitByWords) WordSequence(oldText) else oldText
                val to = if (splitByWords) WordSequence(currentText) else currentText

                diff(from, to, onEqualRegion, onNewPart, onOldPart)
                //                betterDiff(from, to, onEqualRegion, onNewPart, onOldPart);

                clearCurrentParts()
                this.currentParts = currentParts.toTypedArray()
                clearOldParts()
                this.oldParts = oldParts.toTypedArray()
                if (animator != null) {
                    animator!!.cancel()
                }

                this.moveDown = moveDown
                t = 0f
                animator = ValueAnimator.ofFloat(0f, 1f)
                if (widthUpdatedListener != null) {
                    widthUpdatedListener!!.run()
                }
                animator!!.addUpdateListener { anm ->
                    t = anm.animatedValue as Float
                    invalidateSelf()
                    if (widthUpdatedListener != null) {
                        widthUpdatedListener!!.run()
                    }
                }
                animator!!.addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            super.onAnimationEnd(animation)
                            clearOldParts()
                            oldText = null
                            oldWidth = 0f
                            t = 0f
                            invalidateSelf()
                            if (widthUpdatedListener != null) {
                                widthUpdatedListener!!.run()
                            }
                            animator = null

                            if (toSetText != null) {
                                setText(toSetText, true, toSetTextMoveDown)
                                toSetText = null
                                toSetTextMoveDown = false
                            } else if (onAnimationFinishListener != null) {
                                onAnimationFinishListener!!.run()
                            }
                        }
                    }
                )
                animator!!.startDelay = animateDelay
                animator!!.duration = animateDuration
                animator!!.interpolator = animateInterpolator
                animator!!.start()
            } else {
                if (animator != null) {
                    animator!!.cancel()
                }
                animator = null
                toSetText = null
                toSetTextMoveDown = false
                t = 0f

                if (text != currentText) {
                    clearCurrentParts()
                    currentText = text
                    currentParts = arrayOf(Part(makeLayout(text, width), 0f, -1))
                    animateToWidth = currentParts!![0].width
                    height = currentParts!![0].layout!!.height.toFloat()
                    isRTL = AndroidUtilities.isRTL(currentText)
                }

                clearOldParts()
                oldText = null
                oldWidth = 0f
                oldHeight = 0f

                invalidateSelf()
                if (widthUpdatedListener != null) {
                    widthUpdatedListener!!.run()
                }
            }
        }

        private fun clearOldParts() {
            if (oldParts != null) {
                for (i in oldParts!!.indices) {
                    oldParts!![i].detach()
                }
            }
            oldParts = null
        }

        private fun clearCurrentParts() {
            if (oldParts != null) {
                for (i in oldParts!!.indices) {
                    oldParts!![i].detach()
                }
            }
            oldParts = null
        }

        private fun makeLayout(textPart: CharSequence?, width: Int): StaticLayout {
            var width = width
            if (width <= 0) {
                width = min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)
            }
            return StaticLayout.Builder.obtain(
                    requireNotNull(textPart),
                    0,
                    textPart.length,
                    paint,
                    width,
                )
                .setMaxLines(1)
                .setLineSpacing(0f, 1f)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setEllipsizedWidth(width)
                .setIncludePad(includeFontPadding)
                .build()
        }

        private open class WordSequence : CharSequence {

            private val words: Array<CharSequence>
            private var totalLength: Int

            constructor(text: CharSequence?) {
                if (text == null) {
                    words = emptyArray()
                    totalLength = 0
                    return
                }
                totalLength = text.length
                var spacesCount = 0
                for (i in 0 until totalLength) {
                    if (text[i] == SPACE) {
                        spacesCount++
                    }
                }
                var j = 0
                words = Array(spacesCount + 1) { "" }
                var start = 0
                for (i in 0..totalLength) {
                    if (i == totalLength || text[i] == SPACE) {
                        words[j++] = text.subSequence(start, i + (if (i < totalLength) 1 else 0))
                        start = i + 1
                    }
                }
            }

            open fun wordAt(i: Int): CharSequence? {
                if (i < 0 || i >= words.size) {
                    return null
                }
                return words[i]
            }

            override val length: Int
                get() {
                    return words.size
                }

            override operator fun get(index: Int): Char {
                var i = index
                for (j in words.indices) {
                    if (i < words[j].length) return words[j][i]
                    i -= words[j].length
                }
                return 0.toChar()
            }

            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
                return TextUtils.concat(*words.copyOfRange(startIndex, endIndex))
            }

            override fun toString(): String {
                val sb = StringBuilder()
                for (i in words.indices) {
                    sb.append(words[i])
                }
                return sb.toString()
            }

            open fun toCharSequence(): CharSequence {
                return TextUtils.concat(*words)
            }

            override fun chars(): IntStream = toCharSequence().chars()

            override fun codePoints(): IntStream = toCharSequence().codePoints()

            companion object {
                private const val SPACE: Char = ' '
            }
        }

        private fun part(onPart: RegionCallback?, text: CharSequence?, start: Int, end: Int) {
            if (enforceByLetter && text!!.length > 1) {
                for (i in text.indices) {
                    onPart!!.run(text.subSequence(i, i + 1), start + i, start + i + 1)
                }
                return
            }
            onPart!!.run(text, start, end)
        }

        private fun diff(
            oldText: CharSequence?,
            newText: CharSequence?,
            onEqualPart: RegionCallback?,
            onNewPart: RegionCallback?,
            onOldPart: RegionCallback?,
        ) {
            if (updateAll) {
                part(onOldPart, oldText, 0, oldText!!.length)
                part(onNewPart, newText, 0, newText!!.length)
                return
            }
            if (preserveIndex) {
                var equal = true
                var start = 0
                val minLength = min(newText!!.length, oldText!!.length)
                if (startFromEnd) {
                    val indexes = ArrayList<Int?>()
                    var eq = true
                    for (i in 0..minLength) {
                        val a = newText.length - i - 1
                        val b = oldText.length - i - 1
                        val thisEqual = a >= 0 && b >= 0 && partEquals(newText, oldText, a, b)
                        if (equal != thisEqual || i == minLength) {
                            if (i - start > 0) {
                                if (indexes.isEmpty()) {
                                    eq = equal
                                }
                                indexes.add(i - start)
                            }
                            equal = thisEqual
                            start = i
                        }
                    }
                    var a = newText.length - minLength
                    var b = oldText.length - minLength
                    if (a > 0) {
                        part(onNewPart, newText.subSequence(0, a), 0, a)
                    }
                    if (b > 0) {
                        part(onOldPart, oldText.subSequence(0, b), 0, b)
                    }
                    for (i in indexes.indices.reversed()) {
                        val count = indexes[i]!!
                        if ((i % 2 == 0) == eq) {
                            if (newText.length > oldText.length) {
                                onEqualPart!!.run(newText.subSequence(a, a + count), a, a + count)
                            } else {
                                onEqualPart!!.run(oldText.subSequence(b, b + count), b, b + count)
                            }
                        } else {
                            part(onNewPart, newText.subSequence(a, a + count), a, a + count)
                            part(onOldPart, oldText.subSequence(b, b + count), b, b + count)
                        }
                        a += count
                        b += count
                    }
                } else {
                    for (i in 0..minLength) {
                        val thisEqual = i < minLength && partEquals(newText, oldText, i, i)
                        if (equal != thisEqual || i == minLength) {
                            if (i - start > 0) {
                                if (equal) {
                                    part(onEqualPart, newText.subSequence(start, i), start, i)
                                } else {
                                    part(onNewPart, newText.subSequence(start, i), start, i)
                                    part(onOldPart, oldText.subSequence(start, i), start, i)
                                }
                            }
                            equal = thisEqual
                            start = i
                        }
                    }
                    if (newText.length - minLength > 0) {
                        part(
                            onNewPart,
                            newText.subSequence(minLength, newText.length),
                            minLength,
                            newText.length,
                        )
                    }
                    if (oldText.length - minLength > 0) {
                        part(
                            onOldPart,
                            oldText.subSequence(minLength, oldText.length),
                            minLength,
                            oldText.length,
                        )
                    }
                }
            } else {
                var astart = 0
                var bstart = 0
                var equal = true
                var a = 0
                var b = 0
                val minLength = min(newText!!.length, oldText!!.length)
                while (a <= minLength) {
                    val thisEqual = a < minLength && partEquals(newText, oldText, a, b)
                    if (equal != thisEqual || a == minLength) {
                        if (a == minLength) {
                            a = newText.length
                            b = oldText.length
                        }
                        val alen = a - astart
                        val blen = b - bstart
                        if (alen > 0 || blen > 0) {
                            if (alen == blen && equal) {
                                // equal part on [astart, a)
                                onEqualPart!!.run(newText.subSequence(astart, a), astart, a)
                            } else {
                                if (alen > 0) {
                                    // new part on [astart, a)
                                    part(onNewPart, newText.subSequence(astart, a), astart, a)
                                }
                                if (blen > 0) {
                                    // old part on [bstart, b)
                                    part(onOldPart, oldText.subSequence(bstart, b), bstart, b)
                                }
                            }
                        }
                        equal = thisEqual
                        astart = a
                        bstart = b
                    }
                    if (thisEqual) {
                        b++
                    }
                    ++a
                }
            }
        }

        open fun setTypeface(typeface: Typeface?) {
            paint.typeface = typeface
        }

        open fun setAnimationProperties(
            moveAmplitude: Float,
            startDelay: Long,
            duration: Long,
            interpolator: TimeInterpolator?,
        ) {
            setAnimationProperties(moveAmplitude, startDelay, duration, 1.0f, interpolator)
        }

        open fun setAnimationProperties(
            moveAmplitude: Float,
            startDelay: Long,
            duration: Long,
            wave: Float,
            interpolator: TimeInterpolator?,
        ) {
            this.moveAmplitude = moveAmplitude
            animateDelay = startDelay
            animateDuration = duration
            animateWave = wave
            animateInterpolator = interpolator
        }

        open fun setScaleProperty(scale: Float) {
            this.scaleAmplitude = scale
        }

        private fun interface RegionCallback {
            fun run(part: CharSequence?, start: Int, end: Int)
        }

        override fun setAlpha(alpha: Int) {
            this.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("")
        override fun getOpacity(): Int {
            return PixelFormat.TRANSPARENT
        }

        override fun setBounds(bounds: Rect) {
            super.setBounds(bounds)
            this.bounds.set(bounds)
        }

        open fun setBounds(bounds: RectF?) {
            setBounds(
                bounds!!.left.toInt(),
                bounds.top.toInt(),
                bounds.right.toInt(),
                bounds.bottom.toInt(),
            )
        }

        override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
            super.setBounds(left, top, right, bottom)
            this.bounds.set(left, top, right, bottom)
        }

        open fun setBounds(left: Float, top: Float, right: Float, bottom: Float) {
            super.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
            this.bounds.set(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
        }

        override fun getDirtyBounds(): Rect {
            return this.bounds
        }

        companion object {

            fun partEquals(a: CharSequence?, b: CharSequence?, aIndex: Int, bIndex: Int): Boolean {
                if (a is WordSequence && b is WordSequence) {
                    val wordA = a.wordAt(aIndex)
                    val wordB = b.wordAt(bIndex)
                    return wordA == null && wordB == null || wordA != null && wordA == wordB
                }
                return (a == null && b == null || a != null && b != null && a[aIndex] == b[bIndex])
            }
        }
    }
}
