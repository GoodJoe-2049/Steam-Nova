package steam.nova.app.features.chat.media.viewer

import android.graphics.PointF
import android.view.animation.Interpolator
import kotlin.math.abs

open class CubicBezierInterpolator
@Throws(IllegalArgumentException::class)
constructor(protected var start: PointF?, protected var end: PointF?) : Interpolator {
    protected var a: PointF = PointF()
    protected var b: PointF = PointF()
    protected var c: PointF = PointF()

    init {
        if (start!!.x !in 0f..1f) {
            throw IllegalArgumentException("startX value must be in the range [0, 1]")
        }
        if (end!!.x !in 0f..1f) {
            throw IllegalArgumentException("endX value must be in the range [0, 1]")
        }
    }

    constructor(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ) : this(PointF(startX, startY), PointF(endX, endY))

    constructor(
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
    ) : this(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat())

    override fun getInterpolation(time: Float): Float {
        return getBezierCoordinateY(getXForTime(time))
    }

    protected open fun getBezierCoordinateY(time: Float): Float {
        c.y = 3 * start!!.y
        b.y = 3 * (end!!.y - start!!.y) - c.y
        a.y = 1f - c.y - b.y
        return time * (c.y + time * (b.y + time * a.y))
    }

    protected open fun getXForTime(time: Float): Float {
        var x = time
        var z: Float
        repeat(13) {
            z = getBezierCoordinateX(x) - time
            if (abs(z) < 1e-3) {
                return x
            }
            x -= z / getXDerivate(x)
        }
        return x
    }

    private fun getXDerivate(t: Float): Float {
        return c.x + t * (2 * b.x + 3f * a.x * t)
    }

    private fun getBezierCoordinateX(time: Float): Float {
        c.x = 3 * start!!.x
        b.x = 3 * (end!!.x - start!!.x) - c.x
        a.x = 1f - c.x - b.x
        return time * (c.x + time * (b.x + time * a.x))
    }

    companion object {

        val DEFAULT: CubicBezierInterpolator = CubicBezierInterpolator(0.25, 0.1, 0.25, 1.0)
        val EASE_OUT_QUINT: CubicBezierInterpolator = CubicBezierInterpolator(.23, 1.0, .32, 1.0)
        val EASE_BOTH: CubicBezierInterpolator = CubicBezierInterpolator(.42, 0.0, .58, 1.0)
    }
}
