package steam.nova.app.features.chat.linkpreview

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import kotlin.math.abs

internal class SteamChatLinkPreviewTouch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : WebView(context, attrs) {
    var nativeStarted: ((View, MotionEvent) -> Unit)? = null
    var nativeEnded: ((View, MotionEvent) -> Unit)? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var sequence = 0
    private var pressed = false
    private var startX = 0f
    private var startY = 0f
    private var moveX = 0f
    private var moveY = 0f
    private var axes: Int? = null
    private var outer = false
    private var dragging = false
    private var cancelled = false
    private var notified = false
    private val hitTimeout = Runnable {
        if (pressed && axes == null) {
            axes = 0
            chooseOwner()
        }
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        super.requestDisallowInterceptTouchEvent(pressed && !outer && (axes == null || axes != 0 || dragging))
    }

    private fun chooseOwner() {
        val allowed = axes ?: return
        if (outer || dragging) return
        if (abs(moveX) <= slop && abs(moveY) <= slop) {
            parent?.requestDisallowInterceptTouchEvent(allowed != 0)
            return
        }
        dragging = true
        val direction = if (abs(moveY) >= abs(moveX)) 2 else 1
        outer = allowed and direction == 0
        parent?.requestDisallowInterceptTouchEvent(!outer)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val finished = event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            sequence++
            pressed = true
            startX = event.x
            startY = event.y
            moveX = 0f
            moveY = 0f
            axes = null
            outer = false
            dragging = false
            cancelled = false
            notified = true
            parent?.requestDisallowInterceptTouchEvent(true)
            nativeStarted?.invoke(this, event)
            val token = sequence
            removeCallbacks(hitTimeout)
            postDelayed(hitTimeout, 150)
            evaluateJavascript(SteamChatLinkPreviewPage.hitTest(event.x, event.y, width, height)) { result ->
                if (pressed && sequence == token && axes == null) {
                    removeCallbacks(hitTimeout)
                    axes = result?.toIntOrNull()?.and(3) ?: 0
                    chooseOwner()
                }
            }
        } else if (pressed) {
            moveX = event.x - startX
            moveY = event.y - startY
            chooseOwner()
        }
        return try {
            if (outer || cancelled || (finished && axes == null && (abs(moveX) > slop || abs(moveY) > slop))) {
                if (!cancelled) {
                    val cancel = MotionEvent.obtain(event)
                    try {
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancel)
                    } finally { cancel.recycle() }
                    cancelled = true
                }
                true
            } else super.dispatchTouchEvent(event)
        } finally {
            if (finished) {
                finishGesture(event)
            } else {
                val retain = !outer && (axes == null || axes != 0 || dragging)
                parent?.requestDisallowInterceptTouchEvent(retain)
            }
        }
    }

    private fun finishGesture(event: MotionEvent) {
        pressed = false
        sequence++
        removeCallbacks(hitTimeout)
        parent?.requestDisallowInterceptTouchEvent(false)
        if (notified) nativeEnded?.invoke(this, event)
        notified = false
    }

    override fun onDetachedFromWindow() {
        if (pressed) {
            val now = SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            try {
                if (!cancelled) super.dispatchTouchEvent(cancel)
                finishGesture(cancel)
            } finally { cancel.recycle() }
        }
        super.onDetachedFromWindow()
    }
}
