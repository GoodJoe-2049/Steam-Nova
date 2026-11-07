package steam.nova.app.features.chat.scroll

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.ScrollView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import kotlin.math.abs
import kotlin.math.max

/** RN retains its cells. A short-lived viewport snapshot replaces Telegram's retained outgoing views. */
internal class SteamChatScrollTransition(
    private val viewport: ViewGroup,
    private val list: ScrollView,
    private val changed: () -> Unit,
    private val finished: () -> Unit,
) {
    var running = false
        private set
    private var animator: ValueAnimator? = null
    private var outgoing: ImageView? = null
    private var blocker: View? = null
    private var token = 0
    private var baseTranslation = 0f
    private var baseAlpha = 1f
    private var baseScrollbar = true
    private val sharedRows = arrayListOf<Rect>()

    fun start(reduced: Boolean, oldRows: Map<String, Rect>, newRows: () -> Map<String, Rect>, latest: () -> String) {
        if (running) return
        if (list.width <= 0 || list.height <= 0 || viewport.width <= 0 || viewport.height <= 0) { finished(); return }
        // RN has a ValueAnimator and an OverScroller; fling(0) starts another fling at the old offset.
        (list.javaClass.getMethod("getFlingAnimator").invoke(list) as? ValueAnimator)?.cancel()
        list.javaClass.getMethod("abortAnimation").invoke(list)
        if (list.scrollY == 0 || reduced || !ValueAnimator.areAnimatorsEnabled()) {
            list.scrollTo(0, 0); changed(); finished(); return
        }
        val width = viewport.width
        val height = viewport.height
        val startedAt = SystemClock.uptimeMillis()
        SteamChatDiagnostics.info("scroll.transition.prepare",
            "scrollY=${list.scrollY} rotation=${list.rotation} scaleX=${list.scaleX} scaleY=${list.scaleY}")
        val sourceTransform = Matrix()
        var source: View = list
        // Compose local matrices explicitly; View.transformMatrixToLocal/Global require API 29.
        while (source !== viewport) {
            val parent = source.parent as? View ?: error("Chat list is outside its viewport")
            sourceTransform.postConcat(source.matrix)
            sourceTransform.postTranslate((source.left - parent.scrollX).toFloat(), (source.top - parent.scrollY).toFloat())
            source = parent
        }
        fun record(canvas: Canvas) {
            // RN decomposes scale(-1) into rotation(180) and positive scales. Preserve the full
            // list-to-viewport matrix, including pivots and ancestors, instead of copying scales.
            canvas.concat(sourceTransform)
            canvas.translate(-list.scrollX.toFloat(), -list.scrollY.toFloat())
            list.draw(canvas)
        }
        val captured = if (Build.VERSION.SDK_INT >= 28) {
            val picture = Picture()
            try { record(picture.beginRecording(width, height)) } finally { picture.endRecording() }
            Bitmap.createBitmap(picture)
        } else {
            val pixels = createBitmap(width, height)
            try { record(Canvas(pixels)); pixels } catch (error: Exception) { pixels.recycle(); throw error }
        }
        baseTranslation = list.translationY
        baseAlpha = list.alpha
        baseScrollbar = list.isVerticalScrollBarEnabled
        running = true
        list.isVerticalScrollBarEnabled = false
        val origin = IntArray(2).also(viewport::getLocationOnScreen)
        outgoing = ImageView(list.context).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            setImageDrawable(object : BitmapDrawable(list.resources, captured) {
                override fun draw(canvas: Canvas) {
                    canvas.withSave {
                        sharedRows.forEach { canvas.clipOutRect(it) }
                        super.draw(canvas)
                    }
                }
            }.apply {
                // Keep the drawable in viewport pixels so the shared-row mask is not density-scaled.
                setTargetDensity(captured.density)
            })
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        outgoing?.let { viewport.addView(it, ViewGroup.LayoutParams(width, height)); it.layout(0, 0, width, height) }
        blocker = View(list.context).apply {
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        blocker?.let { viewport.addView(it, ViewGroup.LayoutParams(width, height)); it.layout(0, 0, width, height) }
        val destination = latest()
        val current = ++token
        list.alpha = 0f
        list.scrollTo(0, 0)
        // Let VirtualizedList mount the destination before revealing it. No empty-list jump is shown.
        fun awaitDestination(attempt: Int) {
            if (!running || current != token) return
            try {
                if (!viewport.isAttachedToWindow || !viewport.hasWindowFocus()) { cancel(); return }
                // A pending RN layout can restore its previous anchor. Keep the requested end
                // while the outgoing snapshot covers the list and new cells are being mounted.
                if (list.scrollY != 0) list.scrollTo(0, 0)
                val rows = newRows()
                if ((rows.isEmpty() || destination.isNotEmpty() && !rows.containsKey(destination)) && attempt < 120) {
                    viewport.postOnAnimation { awaitDestination(attempt + 1) }
                    return
                }
                if (rows.isEmpty() || destination.isNotEmpty() && !rows.containsKey(destination)) {
                    SteamChatDiagnostics.info("scroll.transition.timeout",
                        "elapsedMs=${SystemClock.uptimeMillis() - startedAt} scrollY=${list.scrollY} rows=${rows.size} latestVisible=${rows.containsKey(destination)}")
                    cancel(); return
                }
                val common = oldRows.keys.firstOrNull { rows.containsKey(it) }
                // As in RecyclerAnimationScrollHelper, shared cells belong only to the incoming list.
                oldRows.filterKeys(rows::containsKey).values.forEach { rect ->
                    sharedRows.add(Rect(rect).apply { offset(-origin[0], -origin[1]) })
                }
                val top = IntArray(2).also(viewport::getLocationOnScreen)[1]
                val distance = if (common != null) abs(rows.getValue(common).top - oldRows.getValue(common).top).toFloat()
                else ((oldRows.values.maxOfOrNull { it.bottom } ?: (top + height)) - top -
                        ((rows.values.minOfOrNull { it.top } ?: top) - top)).toFloat()
                val travel = max(1f, distance)
                val duration = if (common != null) 600L else ((travel / height + 1f) * 200).toLong().coerceIn(300, 1300)
                SteamChatDiagnostics.info("scroll.transition.animate",
                    "prepareMs=${SystemClock.uptimeMillis() - startedAt} durationMs=$duration travelPx=$travel rows=${rows.size}")
                list.translationY = baseTranslation + travel
                list.alpha = baseAlpha
                animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    this.duration = duration
                    interpolator = PathInterpolator(.23f, 1f, .32f, 1f)
                    addUpdateListener {
                        val value = it.animatedValue as Float
                        outgoing?.translationY = -travel * value
                        list.translationY = baseTranslation + travel * (1f - value)
                        changed()
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (current == token) {
                                SteamChatDiagnostics.info("scroll.transition.end", "elapsedMs=${SystemClock.uptimeMillis() - startedAt}")
                                cleanup()
                            }
                        }
                    })
                    start()
                }
            } catch (error: Exception) { SteamChatDiagnostics.failure("scroll.transition", error); cancel() }
        }
        viewport.postOnAnimation { viewport.postOnAnimation { awaitDestination(0) } }
    }

    fun cancel() {
        if (!running) return
        token++
        animator?.removeAllListeners()
        animator?.cancel()
        cleanup()
    }

    private fun cleanup() {
        if (!running) return
        running = false
        animator = null
        list.translationY = baseTranslation
        list.alpha = baseAlpha
        list.isVerticalScrollBarEnabled = baseScrollbar
        // Arrivals can change content offsets during the animation. Finish at the actual latest end.
        list.scrollTo(0, 0)
        outgoing?.let { it.setImageDrawable(null); viewport.removeView(it) }
        blocker?.let(viewport::removeView)
        outgoing = null; blocker = null
        // Removed ImageViews release their bitmap after RenderThread has consumed its last frame.
        sharedRows.clear()
        finished()
    }
}
