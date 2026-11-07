package steam.nova.app.features.chat.media.viewer

import android.animation.TimeInterpolator
import android.os.SystemClock
import android.view.View
import androidx.core.math.MathUtils
import kotlin.math.abs

open class AnimatedFloat {

    private var parent: View? = null
    private var invalidate: Runnable? = null
    private var value: Float = 0f
    var targetValue: Float = 0f
        private set

    private var firstSet: Boolean = false

    private var transitionDelay: Long = 0
    open var duration: Long = 200
    private var transitionInterpolator: TimeInterpolator? = CubicBezierInterpolator.DEFAULT
    var isInProgress: Boolean = false
        private set

    private var transitionStart: Long = 0
    private var startValue: Float = 0f

    constructor(
        parentToInvalidate: View?,
        transitionDelay: Long,
        transitionDuration: Long,
        transitionInterpolator: TimeInterpolator?,
    ) {
        this.parent = parentToInvalidate
        this.transitionDelay = transitionDelay
        this.duration = transitionDuration
        this.transitionInterpolator = transitionInterpolator
        this.firstSet = true
    }

    constructor(
        invalidate: Runnable?,
        transitionDelay: Long,
        transitionDuration: Long,
        transitionInterpolator: TimeInterpolator?,
    ) {
        this.invalidate = invalidate
        this.transitionDelay = transitionDelay
        this.duration = transitionDuration
        this.transitionInterpolator = transitionInterpolator
        this.firstSet = true
    }

    constructor(
        initialValue: Float,
        parentToInvalidate: View?,
        transitionDelay: Long,
        transitionDuration: Long,
        transitionInterpolator: TimeInterpolator?,
    ) {
        this.parent = parentToInvalidate
        targetValue = initialValue
        this.value = targetValue
        this.transitionDelay = transitionDelay
        this.duration = transitionDuration
        this.transitionInterpolator = transitionInterpolator
        this.firstSet = false
    }

    // Read the last rendered value without advancing the animation clock.
    val currentValue: Float
        get() = value

    // set() must be called inside onDraw/dispatchDraw
    // the main purpose of AnimatedFloat is to interpolate between abrupt changing states

    open fun set(mustBe: Float): Float {
        return this.set(mustBe, false)
    }

    open fun set(mustBe: Boolean): Float {
        return this.set((if (mustBe) 1 else 0).toFloat(), false)
    }

    // do set(value, true) when it's needed to skip animation

    open fun set(mustBe: Boolean, force: Boolean): Float {
        return this.set((if (mustBe) 1 else 0).toFloat(), force)
    }

    open fun set(mustBe: Float, force: Boolean): Float {
        if (force || duration <= 0 || firstSet) {
            targetValue = mustBe
            value = targetValue
            isInProgress = false
            firstSet = false
        } else if (abs(targetValue - mustBe) > 0.0001f) {
            isInProgress = true
            targetValue = mustBe
            startValue = value
            transitionStart = SystemClock.elapsedRealtime()
        }

        return getValue()
    }

    open fun getValue(): Float {
        if (isInProgress) {
            val now = SystemClock.elapsedRealtime()
            val t =
                MathUtils.clamp(
                    (now - transitionStart - transitionDelay) / duration.toFloat(),
                    0f,
                    1f,
                )
            if (now - transitionStart >= transitionDelay) {
                value =
                    if (transitionInterpolator == null) {
                        AndroidUtilities.lerp(startValue, targetValue, t)
                    } else {
                        AndroidUtilities.lerp(
                            startValue,
                            targetValue,
                            transitionInterpolator!!.getInterpolation(t),
                        )
                    }
            }
            if (t >= 1f) {
                isInProgress = false
            } else {
                if (parent != null) {
                    parent!!.invalidate()
                }
                if (invalidate != null) {
                    invalidate!!.run()
                }
            }
        }
        return value
    }
}
