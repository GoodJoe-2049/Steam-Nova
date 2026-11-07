package steam.nova.app.features.chat.media.viewer

import android.animation.ValueAnimator
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.Scroller
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.dp

/**
 * Normal-media branches extracted from Nagram PhotoViewer.onTouchEvent/onDoubleTap/onFling.
 * Telegram editor, sticker, sponsored-message and PiP branches have no Steam counterparts.
 * Coordinates, thresholds, bounds resistance and animation equations retain the source behavior.
 */
internal class SteamChatMediaGestures(context: Context, private val host: Host) {
    interface Host {
        val width: Int
        val height: Int
        val mediaWidth: Float
        val mediaHeight: Float

        fun transform(scale: Float, x: Float, y: Float)

        fun tap(x: Float, y: Float): Boolean

        fun centralTap(x: Float, y: Float): Boolean

        fun doubleTapVideo(x: Float): Boolean

        fun hideBars()

        fun close()

        fun hasPage(direction: Int): Boolean

        fun page(direction: Int)

        fun longPress(x: Float)

        fun rewindMove(x: Float): Boolean

        fun rewindEnd()

        fun schedule(action: Runnable, delay: Long)

        fun cancel(action: Runnable)
    }

    var scale = 1f
        private set

    private var translationX = 0f
    private var translationY = 0f
    private var minX = 0f
    private var maxX = 0f
    private var minY = 0f
    private var maxY = 0f
    private var pinchStartDistance = 0f
    private var pinchStartScale = 1f
    private var pinchCenterX = 0f
    private var pinchCenterY = 0f
    private var pinchStartX = 0f
    private var pinchStartY = 0f
    private var moveStartX = 0f
    private var moveStartY = 0f
    private var dragY = 0f
    private var zooming = false
    private var moving = false
    private var draggingDown = false
    private var canDragDown = true
    private var invalidCoords = false
    private var discardTap = false
    private var longPressX = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var velocity: VelocityTracker? = null
    private var animation: ValueAnimator? = null
    private val scroller = Scroller(context)
    private val longPress = Runnable {
        if (scale <= 1.35f) {
            discardTap = true
            host.longPress(longPressX)
        }
    }
    private val flingFrame =
        object : Runnable {
            override fun run() {
                if (scroller.computeScrollOffset()) {
                    translationX = scroller.currX.toFloat()
                    translationY = scroller.currY.toFloat()
                    apply()
                    host.schedule(this, 16)
                }
            }
        }
    private val detector =
        GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent) = true

                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (!discardTap) return host.tap(e.x, e.y)
                        return false
                    }

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        if (!discardTap && host.centralTap(e.x, e.y)) {
                            discardTap = true
                            return true
                        }
                        return false
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (host.doubleTapVideo(e.x)) return true
                        if (
                            animation != null ||
                                scale == 1f && (translationX != 0f || translationY != 0f)
                        )
                            return false
                        if (scale == 1f) {
                            val x =
                                (e.x - host.width / 2) -
                                    ((e.x - host.width / 2) - translationX) * (3f / scale)
                            val y =
                                (e.y - host.height / 2) -
                                    ((e.y - host.height / 2) - translationY) * (3f / scale)
                            bounds(3f)
                            animateTo(3f, x.coerceIn(minX, maxX), y.coerceIn(minY, maxY))
                        } else animateTo(1f, 0f, 0f)
                        return true
                    }

                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float,
                    ): Boolean {
                        if (scale != 1f) {
                            bounds(scale)
                            scroller.abortAnimation()
                            scroller.fling(
                                translationX.roundToInt(),
                                translationY.roundToInt(),
                                velocityX.roundToInt(),
                                velocityY.roundToInt(),
                                minX.toInt(),
                                maxX.toInt(),
                                minY.toInt(),
                                maxY.toInt(),
                            )
                            host.schedule(flingFrame, 0)
                        }
                        return false
                    }
                },
            )
            .apply {
                // Android's setter is setIsLongpressEnabled, but the property has no Kotlin setter.
                @Suppress("UsePropertyAccessSyntax") setIsLongpressEnabled(false)
            }

    fun touch(ev: MotionEvent) {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            animation?.cancel()
            animation = null
            host.cancel(flingFrame)
            scroller.abortAnimation()
            velocity?.recycle()
            velocity = VelocityTracker.obtain()
            discardTap = false
        }
        velocity?.addMovement(ev)
        detector.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_MOVE && host.rewindMove(ev.x)) {
            discardTap = true
            return
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!draggingDown) {
                    if (ev.pointerCount == 2) {
                        pinchStartDistance = hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0))
                        pinchStartScale = scale
                        pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2f
                        pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2f
                        pinchStartX = translationX
                        pinchStartY = translationY
                        zooming = true
                        moving = false
                        velocity?.clear()
                    } else if (ev.pointerCount == 1) {
                        moveStartX = ev.x
                        moveStartY = ev.y
                        dragY = ev.y
                        draggingDown = false
                        canDragDown = true
                    }
                }
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    longPressX = ev.x
                    host.schedule(longPress, 300)
                } else host.cancel(longPress)
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(longPressX - ev.x) > touchSlop) host.cancel(longPress)
                if (ev.pointerCount == 2 && !draggingDown && zooming) {
                    discardTap = true
                    scale =
                        hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0)) /
                            pinchStartDistance.coerceAtLeast(1f) * pinchStartScale
                    translationX =
                        (pinchCenterX - host.width / 2) -
                            ((pinchCenterX - host.width / 2) - pinchStartX) *
                                (scale / pinchStartScale)
                    translationY =
                        (pinchCenterY - host.height / 2) -
                            ((pinchCenterY - host.height / 2) - pinchStartY) *
                                (scale / pinchStartScale)
                    bounds(scale)
                    apply()
                } else if (ev.pointerCount == 1) {
                    val dx = abs(ev.x - moveStartX)
                    val dy = abs(ev.y - dragY)
                    if (dx > touchSlop || dy > touchSlop) {
                        discardTap = true
                        host.cancel(longPress)
                    }
                    if (
                        canDragDown && !draggingDown && scale == 1f && dy >= dp(30f) && dy / 2 > dx
                    ) {
                        draggingDown = true
                        moving = false
                        dragY = ev.y
                        host.hideBars()
                        return
                    } else if (draggingDown) {
                        translationY = ev.y - dragY
                        apply()
                    } else if (!invalidCoords && animation == null) {
                        var moveDx = moveStartX - ev.x
                        var moveDy = moveStartY - ev.y
                        if (
                            moving ||
                                scale == 1f && abs(moveDy) + dp(12f) < abs(moveDx) ||
                                scale != 1f
                        ) {
                            if (!moving) {
                                moveDx = 0f
                                moveDy = 0f
                                moving = true
                                canDragDown = false
                            }
                            moveStartX = ev.x
                            moveStartY = ev.y
                            bounds(scale)
                            if (
                                translationX < minX && !host.hasPage(1) ||
                                    translationX > maxX && !host.hasPage(-1)
                            )
                                moveDx /= 3f
                            if (maxY == 0f && minY == 0f) {
                                if (translationY - moveDy < minY) {
                                    translationY = minY
                                    moveDy = 0f
                                } else if (translationY - moveDy > maxY) {
                                    translationY = maxY
                                    moveDy = 0f
                                }
                            } else if (translationY !in minY..maxY) moveDy /= 3f
                            translationX -= moveDx
                            if (scale != 1f) translationY -= moveDy
                            apply()
                        }
                    } else {
                        invalidCoords = false
                        moveStartX = ev.x
                        moveStartY = ev.y
                    }
                }
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                host.cancel(longPress)
                host.rewindEnd()
                if (zooming) {
                    invalidCoords = true
                    if (scale < 1f) {
                        bounds(1f)
                        animateTo(1f, 0f, 0f)
                    } else if (scale > 3f) {
                        val x =
                            (pinchCenterX - host.width / 2) -
                                ((pinchCenterX - host.width / 2) - pinchStartX) *
                                    (3f / pinchStartScale)
                        val y =
                            (pinchCenterY - host.height / 2) -
                                ((pinchCenterY - host.height / 2) - pinchStartY) *
                                    (3f / pinchStartScale)
                        bounds(3f)
                        animateTo(3f, x.coerceIn(minX, maxX), y.coerceIn(minY, maxY))
                    } else settle()
                    zooming = false
                    moving = false
                } else if (draggingDown) {
                    if (
                        ev.actionMasked != MotionEvent.ACTION_CANCEL &&
                            abs(dragY - ev.y) > host.height / 6f
                    )
                        host.close()
                    else animateTo(1f, 0f, 0f)
                    draggingDown = false
                } else if (moving) {
                    bounds(scale)
                    moving = false
                    canDragDown = true
                    velocity?.computeCurrentVelocity(1000)
                    val vx = if (scale == 1f) velocity?.xVelocity ?: 0f else 0f
                    val direction =
                        if (
                            (translationX < minX - host.width / 3 || vx < -dp(650f)) &&
                                host.hasPage(1)
                        )
                            1
                        else if (
                            (translationX > maxX + host.width / 3 || vx > dp(650f)) &&
                                host.hasPage(-1)
                        )
                            -1
                        else 0
                    if (direction != 0 && ev.actionMasked != MotionEvent.ACTION_CANCEL) {
                        val extra =
                            if (scale != 1f) (host.width - host.mediaWidth) / 2 * scale else 0f
                        animateTo(
                            scale,
                            if (direction == 1) minX - host.width - extra - dp(15f)
                            else maxX + host.width + extra + dp(15f),
                            translationY,
                        ) {
                            host.page(direction)
                            reset()
                        }
                    } else if (scroller.isFinished) settle()
                }
                if (ev.actionMasked != MotionEvent.ACTION_POINTER_UP) {
                    velocity?.recycle()
                    velocity = null
                }
            }
        }
        return
    }

    private fun bounds(value: Float) {
        maxX = max(0f, (host.mediaWidth * value - host.width) / 2)
        minX = -maxX
        maxY = max(0f, (host.mediaHeight * value - host.height) / 2)
        minY = -maxY
    }

    private fun apply() = host.transform(scale, translationX, translationY)

    private fun settle() {
        bounds(scale)
        animateTo(scale, translationX.coerceIn(minX, maxX), translationY.coerceIn(minY, maxY))
    }

    private fun animateTo(s: Float, x: Float, y: Float, complete: (() -> Unit)? = null) {
        animation?.cancel()
        val fromScale = scale
        val fromX = translationX
        val fromY = translationY
        animation =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 250
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener {
                    val v = it.animatedValue as Float
                    scale = fromScale + (s - fromScale) * v
                    translationX = fromX + (x - fromX) * v
                    translationY = fromY + (y - fromY) * v
                    apply()
                }
                addListener(
                    object : android.animation.AnimatorListenerAdapter() {
                        private var cancelled = false

                        override fun onAnimationCancel(a: android.animation.Animator) {
                            cancelled = true
                        }

                        override fun onAnimationEnd(a: android.animation.Animator) {
                            if (!cancelled) {
                                animation = null
                                complete?.invoke()
                            }
                        }
                    }
                )
                start()
            }
    }

    fun reset() {
        animation?.cancel()
        animation = null
        host.cancel(longPress)
        host.cancel(flingFrame)
        scroller.abortAnimation()
        zooming = false
        moving = false
        draggingDown = false
        invalidCoords = false
        scale = 1f
        translationX = 0f
        translationY = 0f
        apply()
    }

    fun close() {
        animation?.cancel()
        host.cancel(longPress)
        host.cancel(flingFrame)
        scroller.abortAnimation()
        velocity?.recycle()
        velocity = null
    }
}
