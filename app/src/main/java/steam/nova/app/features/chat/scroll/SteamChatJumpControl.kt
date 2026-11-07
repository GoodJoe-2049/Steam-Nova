package steam.nova.app.features.chat.scroll

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withSave
import steam.nova.app.R
import steam.nova.app.core.ModuleLocalization
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics

/** 56x64dp native page-down slot; animation values follow Nagram, colors follow Steam. */
internal class SteamChatJumpControl(context: Context) : View(context) {
    private val displayDensity = resources.displayMetrics.density
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (Build.VERSION.SDK_INT >= 31) 0xc02a3543.toInt() else 0xf02a3543.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x487a9bb5; style = Paint.Style.STROKE; strokeWidth = 2f / 3f
    }
    private val pressed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2666c0f4 }
    private val arrow = checkNotNull(ResourcesCompat.getDrawable(ModuleLocalization.resources(context), R.drawable.ic_chat_jump_down, null)).apply {
        colorFilter = PorterDuffColorFilter(0xffdcdedf.toInt(), PorterDuff.Mode.SRC_IN)
    }
    private val counter = SteamChatUnreadCounter(::invalidate)
    private var visibilityAnimator: ValueAnimator? = null
    private var pressAnimator: ValueAnimator? = null
    private var factor = 0f
    private var pressScale = 1f
    private var shown = false
    private var reduced = false
    private var unread = 0
    private var backdrop: SteamChatScrollBackdrop? = null
    private var backdropFailed = false
    private var loading = false
    private var loadingFactor = 0f
    private var loadingStart = 0L
    private var loadingAnimator: ValueAnimator? = null
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xffdcdedf.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 1.7f
    }
    private val segmentCurve = PathInterpolator(.4f, 0f, .2f, 1f)
    private val progressRect = RectF(18.575f, 26.575f, 37.425f, 45.425f)
    private val circle = RectF(6f, 14f, 50f, 58f)

    constructor(context: Context, source: View, click: () -> Unit) : this(context) {
        if (Build.VERSION.SDK_INT >= 31) backdrop = SteamChatScrollBackdrop(source, this)
        setOnClickListener { click() }
        isClickable = shown
    }

    init {
        isClickable = false
        isEnabled = false
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        alpha = 0f
        visibility = INVISIBLE
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null)
        body.setShadowLayer(1f, 0f, 1f / 3f, 0x55000000)
        updateDescription()
    }

    fun loading(value: Boolean) {
        if (loading == value) return
        loading = value; loadingAnimator?.cancel()
        if (value) loadingStart = SystemClock.elapsedRealtime()
        if (reduced) { loadingFactor = if (value) 1f else 0f; invalidate(); return }
        loadingAnimator = ValueAnimator.ofFloat(loadingFactor, if (value) 1f else 0f).apply {
            duration = 320
            interpolator = PathInterpolator(.23f, 1f, .32f, 1f)
            addUpdateListener { loadingFactor = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun update(count: Int, reduceMotion: Boolean, animated: Boolean) {
        val wasReduced = reduced
        reduced = reduceMotion || !ValueAnimator.areAnimatorsEnabled()
        if (reduced && !wasReduced) {
            visibilityAnimator?.cancel(); pressAnimator?.cancel(); loadingAnimator?.cancel(); counter.close()
            factor = if (shown) 1f else 0f; pressScale = 1f; loadingFactor = if (loading) 1f else 0f
            applyTransform()
        }
        if (unread != count) {
            unread = count.coerceAtLeast(0)
            counter.setCount(unread, animated && !reduced)
            updateDescription()
        }
    }

    private fun updateDescription() {
        contentDescription = if (unread > 0) ModuleLocalization.quantity(context, R.plurals.chat_jump_unread, unread, unread)
        else ModuleLocalization.text(context, R.string.chat_jump_to_latest)
    }

    fun show(value: Boolean, animated: Boolean = true) {
        if (shown == value) return
        shown = value
        visibilityAnimator?.cancel()
        isEnabled = value
        isClickable = value
        if (value) visibility = VISIBLE
        if (!animated || reduced) {
            factor = if (value) 1f else 0f
            applyTransform()
            return
        }
        visibilityAnimator = ValueAnimator.ofFloat(factor, if (value) 1f else 0f).apply {
            duration = 280
            interpolator = DecelerateInterpolator()
            addUpdateListener { factor = it.animatedValue as Float; applyTransform() }
            start()
        }
    }

    private fun applyTransform() {
        pivotX = 28f * displayDensity; pivotY = 36f * displayDensity
        alpha = factor
        scaleX = (.7f + .3f * factor) * pressScale
        scaleY = scaleX
        translationY = if (reduced) 0f else 80f * displayDensity * (1f - factor)
        if (factor == 0f && !shown) visibility = INVISIBLE
        invalidate()
    }

    override fun setPressed(pressed: Boolean) {
        if (pressed == isPressed) return
        super.setPressed(pressed)
        pressAnimator?.cancel()
        if (reduced) { pressScale = 1f; invalidate(); return }
        pressAnimator = ValueAnimator.ofFloat(pressScale, if (pressed) .87f else 1f).apply {
            duration = if (pressed) 80 else 350
            interpolator = if (pressed) AccelerateDecelerateInterpolator() else OvershootInterpolator(2f)
            addUpdateListener { pressScale = it.animatedValue as Float; applyTransform() }
            start()
        }
    }

    override fun performClick(): Boolean = shown && isEnabled && super.performClick()

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Button"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (Build.VERSION.SDK_INT >= 31 && !backdropFailed) runCatching { backdrop?.draw(canvas) }.onFailure {
            backdropFailed = true; body.color = 0xf02a3543.toInt(); SteamChatDiagnostics.failure("scroll.backdrop", it)
        }
        canvas.withSave {
            canvas.scale(displayDensity, displayDensity)
            canvas.drawRoundRect(circle, 22f, 22f, body)
            canvas.withSave {
                canvas.clipRect(0f, 0f, 56f, 36f); border.strokeWidth = 1f
                canvas.drawRoundRect(circle, 22f, 22f, border)
            }
            canvas.withSave {
                canvas.clipRect(0f, 36f, 56f, 64f); border.strokeWidth = 2f / 3f
                canvas.drawRoundRect(circle, 22f, 22f, border)
            }
            if (isPressed) canvas.drawRoundRect(circle, 22f, 22f, pressed)
            canvas.withSave {
                val iconScale = .4f + .6f * (1f - loadingFactor)
                canvas.scale(iconScale, iconScale, 28f, 37f)
                // Original xxhdpi resource is 51x31px, centered at its intrinsic 17x10.33dp size.
                canvas.translate(19.5f, 37f - 31f / 6f); canvas.scale(1f / 3f, 1f / 3f)
                arrow.alpha = ((1f - loadingFactor) * 255).toInt()
                arrow.setBounds(0, 0, 51, 31)
                arrow.draw(canvas)
            }
            if (loadingFactor > 0f) {
                canvas.withSave {
                    val scale = .4f + .6f * loadingFactor
                    canvas.scale(scale, scale, 28f, 36f)
                    val t = if (reduced) 667f else ((SystemClock.elapsedRealtime() - loadingStart) % 5400).toFloat()
                    var start = (1520f * t / 5400f - 20f).coerceAtLeast(0f)
                    var end = 1520f * t / 5400f
                    repeat(4) { i ->
                        end += segmentCurve.getInterpolation(((t - i * 1350) / 667f).coerceIn(0f, 1f)) * 250
                        start += segmentCurve.getInterpolation(((t - (667 + i * 1350)) / 667f).coerceIn(0f, 1f)) * 250
                    }
                    progressPaint.alpha = (loadingFactor * 255).toInt()
                    canvas.drawArc(progressRect, 90f + start, end - start, false, progressPaint)
                }
                if (!reduced) postInvalidateOnAnimation()
            }
            counter.draw(canvas)
        }
    }

    fun close() {
        visibilityAnimator?.cancel(); pressAnimator?.cancel(); loadingAnimator?.cancel(); counter.close()
        if (Build.VERSION.SDK_INT >= 31) backdrop?.close()
    }
}
