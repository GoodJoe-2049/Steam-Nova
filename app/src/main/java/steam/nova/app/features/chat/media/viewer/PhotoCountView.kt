// Nagram/Telegram GPL-2.0-or-later; numeric text and ordinary-media branches.
package steam.nova.app.features.chat.media.viewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import java.util.Locale
import kotlin.math.max
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.dp
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.dpf2

open class PhotoCountView(context: Context) : View(context) {

    internal var backgroundPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal var left: AnimatedTextView.AnimatedTextDrawable? = null
    internal var paint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    internal var center: StaticLayout? = null
    internal var centerWidth: Float = 0f
    internal var centerTop: Float = 0f
    internal var right: AnimatedTextView.AnimatedTextDrawable? = null
    private val of: String
        get() {
            return " / "
        }

    private var shown: Boolean = false
    private val showT: AnimatedFloat =
        AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT)
    private var nextNotAnimate: Boolean = false

    private var marginTop: Int = 0

    init {

        backgroundPaint.color = Theme.ACTION_BAR_PHOTO_VIEWER_COLOR

        left =
            AnimatedTextView.AnimatedTextDrawable(
                splitByWords = false,
                preserveIndex = true,
                startFromEnd = true,
            )
        left!!.setAnimationProperties(.3f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT)
        left!!.textColor = -0x1
        left!!.textSize = dp(14f).toFloat()
        left!!.setTypeface(AndroidUtilities.bold())
        left!!.callback = this
        left!!.text = "0"
        left!!.setOverrideFullWidth(AndroidUtilities.displaySize.x)

        paint.color = -0x1
        paint.textSize = dp(14f).toFloat()
        paint.typeface = AndroidUtilities.bold()
        setCenterText()

        right =
            AnimatedTextView.AnimatedTextDrawable(
                splitByWords = false,
                preserveIndex = true,
                startFromEnd = true,
            )
        right!!.setAnimationProperties(.3f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT)
        right!!.textColor = -0x1
        right!!.textSize = dp(14f).toFloat()
        right!!.setTypeface(AndroidUtilities.bold())
        right!!.callback = this
        right!!.text = "0"
        right!!.setOverrideFullWidth(AndroidUtilities.displaySize.x)
    }

    private fun setCenterText() {
        center =
            StaticLayout.Builder.obtain(of, 0, of.length, paint, dp(200f))
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
        if (center!!.lineCount >= 1) {
            centerWidth = center!!.getLineWidth(0)
            centerTop = center!!.getLineDescent(0).toFloat()
        } else {
            centerWidth = 0f
            centerTop = 0f
        }
    }

    open fun set(left: Int, right: Int) {
        set(left, right, true)
    }

    open fun set(left: Int, right: Int, animated: Boolean) {
        var left = left
        var right = right
        left = max(0, left)
        right = max(left, right)
        this.left!!.setText(
            String.format(Locale.getDefault(), "%d", left),
            animated && !nextNotAnimate,
        )
        this.right!!.setText(
            String.format(Locale.getDefault(), "%d", right),
            animated && !nextNotAnimate,
        )
        nextNotAnimate = !animated
    }

    protected override fun verifyDrawable(who: Drawable): Boolean {
        return left === who || right === who || super.verifyDrawable(who)
    }

    open fun updateShow(show: Boolean, animated: Boolean) {
        if (shown != show) {
            shown = show
            if (!show) {
                nextNotAnimate = true
            }
            if (!animated) {
                showT.set((if (show) 1 else 0).toFloat(), true)
            }
            invalidate()
        }
    }

    override fun isShown(): Boolean {
        return shown
    }

    protected override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val show = this.showT.set((if (shown) 1 else 0).toFloat())

        if (show <= 0) {
            return
        }

        val width =
            left!!.currentWidth +
                centerWidth +
                right!!.currentWidth +
                dp((9 + 9).toFloat()).toFloat()
        val marginTop = this.marginTop + (1f - show) * -dp(8f)

        AndroidUtilities.rectTmp.set(
            (getWidth() - width) / 2f,
            marginTop + dpf2(10f),
            (getWidth() + width) / 2f,
            marginTop + dpf2((10 + 23).toFloat()),
        )
        val wasAlpha = backgroundPaint.alpha
        backgroundPaint.alpha = (wasAlpha * show).toInt()
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dpf2(12f), dpf2(12f), backgroundPaint)
        backgroundPaint.alpha = wasAlpha

        canvas.save()
        canvas.translate((getWidth() - width) / 2f + dp(9f), marginTop + dp(9.5f))
        left!!.setBounds(0, 0, left!!.currentWidth.toInt(), dp(23f))
        left!!.alpha = (0xFF * show).toInt()
        left!!.draw(canvas)

        canvas.translate(left!!.currentWidth, 0f)
        canvas.save()
        canvas.translate(
            -(center!!.width - centerWidth) / 2f,
            (dp(23f) - center!!.height + centerTop / 2f) / 2f,
        )
        paint.alpha = (0xFF * show).toInt()
        center!!.draw(canvas)
        canvas.restore()

        canvas.translate(centerWidth, 0f)
        right!!.setBounds(0, 0, right!!.currentWidth.toInt(), dp(23f))
        right!!.alpha = (0xFF * show).toInt()
        right!!.draw(canvas)
        canvas.restore()
    }

    protected override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        marginTop = dp(56f)
        left!!.setOverrideFullWidth(width)
        right!!.setOverrideFullWidth(width)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(
                marginTop + dp((10 + 23 + 10).toFloat()),
                MeasureSpec.EXACTLY,
            ),
        )
    }
}
