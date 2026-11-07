/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package steam.nova.app.features.chat.media.viewer

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import androidx.core.graphics.withTranslation

open class CombinedDrawable : Drawable, Drawable.Callback {

    var background: Drawable? = null
        private set

    var icon: Drawable? = null
        private set

    private var left: Int = 0
    private var top: Int = 0
    private var iconWidth: Int = 0
    private var iconHeight: Int = 0
    private var backWidth: Int = 0
    private var backHeight: Int = 0
    private var center: Boolean = false
    private var offsetX: Int = 0
    private var offsetY: Int = 0
    private var fullSize: Boolean = false
    private var both: Boolean = false

    var translateX: Float = 0f
    var translateY: Float = 0f

    constructor(backgroundDrawable: Drawable?, iconDrawable: Drawable?) {
        background = backgroundDrawable
        icon = iconDrawable
        if (iconDrawable != null) {
            iconDrawable.callback = this
        }
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        icon!!.colorFilter = colorFilter
        if (both) {
            background!!.colorFilter = colorFilter
        }
    }

    override fun isStateful(): Boolean {
        return icon!!.isStateful
    }

    override fun setState(stateSet: IntArray): Boolean {
        icon!!.state = stateSet
        return true
    }

    override fun getState(): IntArray {
        return icon!!.state
    }

    protected override fun onStateChange(state: IntArray): Boolean {
        return true
    }

    override fun jumpToCurrentState() {
        icon!!.jumpToCurrentState()
    }

    override fun getConstantState(): ConstantState? {
        return icon!!.constantState
    }

    override fun draw(canvas: Canvas) {
        canvas.withTranslation(translateX, translateY) {
            if (center) {
                val bounds = getBounds()
                setBounds(
                    bounds.centerX() - intrinsicWidth / 2,
                    bounds.centerY() - intrinsicHeight / 2,
                    bounds.centerX() + intrinsicWidth / 2,
                    bounds.centerY() + intrinsicHeight / 2,
                )
            }
            if (background != null) {
                background!!.bounds = bounds
                background!!.draw(canvas)
            }
            if (icon != null) {
                if (fullSize) {
                    val bounds = getBounds()
                    if (left != 0) {
                        icon!!.setBounds(
                            bounds.left + left,
                            bounds.top + top,
                            bounds.right - left,
                            bounds.bottom - top,
                        )
                    } else {
                        icon!!.bounds = bounds
                    }
                } else {
                    var x: Int
                    var y: Int
                    if (iconWidth != 0) {
                        x = bounds.centerX() - iconWidth / 2 + left + offsetX
                        y = bounds.centerY() - iconHeight / 2 + top + offsetY
                        icon!!.setBounds(x, y, x + iconWidth, y + iconHeight)
                    } else {
                        x = bounds.centerX() - icon!!.intrinsicWidth / 2 + left
                        y = bounds.centerY() - icon!!.intrinsicHeight / 2 + top
                        icon!!.setBounds(
                            x,
                            y,
                            x + icon!!.intrinsicWidth,
                            y + icon!!.intrinsicHeight,
                        )
                    }
                }
                icon!!.draw(canvas)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        icon!!.alpha = alpha
        background!!.alpha = alpha
    }

    override fun getIntrinsicWidth(): Int {
        return if (backWidth != 0) backWidth else background!!.intrinsicWidth
    }

    override fun getIntrinsicHeight(): Int {
        return if (backHeight != 0) backHeight else background!!.intrinsicHeight
    }

    override fun getMinimumWidth(): Int {
        return if (backWidth != 0) backWidth else background!!.minimumWidth
    }

    override fun getMinimumHeight(): Int {
        return if (backHeight != 0) backHeight else background!!.minimumHeight
    }

    @Suppress("DEPRECATION")
    @Deprecated("Required by Android Drawable")
    override fun getOpacity(): Int {
        return icon!!.opacity
    }

    override fun invalidateDrawable(who: Drawable) {
        invalidateSelf()
    }

    override fun scheduleDrawable(
        who: Drawable,
        what: Runnable,
        `when`: Long,
    ) {
        scheduleSelf(what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }
}
