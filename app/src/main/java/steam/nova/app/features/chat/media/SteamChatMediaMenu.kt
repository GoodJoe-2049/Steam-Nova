package steam.nova.app.features.chat.media

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.dp

internal class SteamChatMediaMenu(
    context: Context,
    entries: List<Entry>,
    private val viewer: Boolean,
    private val result: (String?) -> Unit,
) {
    data class Entry(val action: String, val icon: String, val label: String)

    private val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val content = Content(context)
    private var animator: ValueAnimator? = null
    private var closing = false
    private var selection: String? = null
    private var anchor: View? = null
    private var localX = 0f
    private var localY = 0f
    private var longPress = false
    private var screenCoordinates = false
    private val reposition = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> reposition() }
    private val attach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = dismissImmediately()
    }
    private val popup = object : PopupWindow(content, -2, -2, true) {
        override fun dismiss() = this@SteamChatMediaMenu.dismiss()
        fun finish() = super.dismiss()
    }.apply {
        setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        isOutsideTouchable = true
        isClippingEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("UsePropertyAccessSyntax")
            setIsLaidOutInScreen(true)
            screenCoordinates = true
        } else {
            screenCoordinates = runCatching {
                PopupWindow::class.java.getMethod("setLayoutInScreenEnabled", Boolean::class.javaPrimitiveType)
                    .invoke(this, true)
            }.isSuccess
        }
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        animationStyle = 0
        setOnDismissListener {
            animator?.cancel()
            rows.children().forEach { it.animate().cancel() }
            anchor?.removeOnAttachStateChangeListener(attach)
            anchor?.rootView?.removeOnLayoutChangeListener(reposition)
            anchor = null
            result(selection)
        }
    }

    init {
        require(entries.isNotEmpty())
        for ((index, entry) in entries.withIndex()) {
            val row = FrameLayout(context).apply {
                minimumWidth = dp(196f)
                setPadding(dp(18f), 0, dp(18f), 0)
                val top = if (index == 0) dp(12f).toFloat() else 0f
                val bottom = if (index == entries.lastIndex) dp(12f).toFloat() else 0f
                val mask = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
                }
                background = RippleDrawable(ColorStateList.valueOf(0x0fffffff), null, mask)
                contentDescription = entry.label
            }
            row.addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER
                setImageDrawable(SteamChatMediaResources.drawable(context, entry.icon))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(-2, dp(40f), Gravity.START or Gravity.CENTER_VERTICAL))
            row.addView(TextView(context).apply {
                text = entry.label
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
                setTextColor(if (entry.action == "delete" && !viewer) 0xfff1a7a0.toInt() else Color.WHITE)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                setPaddingRelative(dp(43f), 0, 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
            row.setOnClickListener { if (!closing) { selection = entry.action; dismiss() } }
            rows.addView(row, LinearLayout.LayoutParams(-1, dp(48f)))
        }
        content.addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(rows, ViewGroup.LayoutParams(-1, -2))
        }, FrameLayout.LayoutParams(-1, -2))
    }

    fun show(source: View, x: Float = 0f, y: Float = 0f, contextMenu: Boolean = false) {
        anchor = source
        localX = x
        localY = y
        longPress = contextMenu
        source.addOnAttachStateChangeListener(attach)
        source.rootView.addOnLayoutChangeListener(reposition)
        rows.children().forEach { it.alpha = 0f }
        content.reveal = 0f
        position(show = true)
        var started = 0
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 150L + 16L * rows.childCount
            addUpdateListener { animation ->
                content.reveal = animation.animatedValue as Float
                val available = (content.measuredHeight - dp(16f)).coerceAtLeast(1)
                while (started < rows.childCount && (started + 1) * dp(48f) - dp(24f) <= content.reveal * available) {
                    val row = rows.getChildAt(started++)
                    row.translationY = -dp(6f).toFloat()
                    row.animate().alpha(1f).translationY(0f).setDuration(180)
                        .setInterpolator(DecelerateInterpolator()).start()
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!closing) rows.children().forEach { it.alpha = 1f }
                }
            })
            start()
        }
    }

    private fun position(show: Boolean) {
        val source = anchor ?: return
        val frame = Rect()
        source.getWindowVisibleDisplayFrame(frame)
        val root = source.rootView
        val rootPosition = IntArray(2).also(root::getLocationOnScreen)
        val insets = ViewCompat.getRootWindowInsets(root)?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
        frame.left = maxOf(frame.left, rootPosition[0] + (insets?.left ?: 0))
        frame.right = minOf(frame.right, rootPosition[0] + root.width - (insets?.right ?: 0))
        frame.top = maxOf(frame.top, rootPosition[1] + (insets?.top ?: 0))
        frame.bottom = minOf(frame.bottom, rootPosition[1] + root.height - (insets?.bottom ?: 0))
        val origin = IntArray(2).also(source::getLocationOnScreen)
        var listTop = frame.top
        if (!viewer) {
            var parent = source.parent
            while (parent is View) {
                if (parent.javaClass.simpleName.contains("Scroll") || parent.javaClass.simpleName.contains("Recycler")) {
                    val visible = Rect()
                    if (parent.getGlobalVisibleRect(visible)) {
                        listTop = maxOf(listTop, visible.top)
                        frame.bottom = minOf(frame.bottom, visible.bottom)
                    }
                    break
                }
                parent = parent.parent
            }
        }
        val minY = if (viewer) frame.top else listTop + dp(24f)
        val maxHeight = (frame.bottom - minY - dp(8f)).coerceAtLeast(1)
        val maxWidth = (frame.width() - dp(40f)).coerceAtLeast(1)
        rows.measure(View.MeasureSpec.makeMeasureSpec((maxWidth - dp(16f)).coerceAtLeast(1), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        content.measure(View.MeasureSpec.makeMeasureSpec((rows.measuredWidth + dp(16f)).coerceAtMost(maxWidth), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
        val width = content.measuredWidth
        val height = content.measuredHeight
        val x = if (viewer) origin[0] + source.width - width
        else if (longPress) origin[0] + localX.toInt() - width + dp(8f) - dp(28f)
        else origin[0] + source.width - width
        var y = if (viewer) origin[1] else origin[1] + localY.toInt()
        if (!viewer && longPress && height - dp(16f) > dp(240f)) y += dp(240f) - height
        val left = x.coerceIn(frame.left + dp(6f), maxOf(frame.left + dp(6f), frame.right - dp(6f) - width))
        val top = y.coerceIn(minY, maxOf(minY, frame.bottom - height - dp(8f)))
        val popupX = left - if (screenCoordinates) 0 else rootPosition[0]
        val popupY = top - if (screenCoordinates) 0 else rootPosition[1]
        if (show) {
            popup.width = width
            popup.height = height
            // The measured offsets already use the physical left edge, including in RTL layouts.
            val horizontalGravity = if (root.layoutDirection == View.LAYOUT_DIRECTION_RTL) Gravity.END else Gravity.START
            val gravity = Gravity.getAbsoluteGravity(Gravity.TOP or horizontalGravity, root.layoutDirection)
            popup.showAtLocation(root, gravity, popupX, popupY)
        } else popup.update(popupX, popupY, width, height)
    }

    private fun reposition() {
        if (popup.isShowing && !closing) position(show = false)
    }

    fun dismiss() {
        if (closing || !popup.isShowing) return
        closing = true
        popup.isFocusable = false
        animator?.cancel()
        rows.children().forEach { it.animate().cancel() }
        content.animate().translationY(-dp(5f).toFloat()).alpha(0f).setDuration(150)
            .withEndAction { popup.finish() }.start()
    }

    fun dismissImmediately() {
        closing = true
        selection = null
        animator?.cancel()
        content.animate().cancel()
        popup.finish()
    }

    private inner class Content(context: Context) : FrameLayout(context) {
        private val backdrop = SteamChatMediaPopupResources.drawable(context, popup = true).apply {
            colorFilter = PorterDuffColorFilter(if (viewer) 0xf9222222.toInt() else 0xff232932.toInt(), PorterDuff.Mode.MULTIPLY)
        }
        var reveal = 1f
            set(value) { field = value; invalidate() }

        init {
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            setWillNotDraw(false)
        }

        override fun onDraw(canvas: Canvas) {
            backdrop.setBounds(0, 0, width, (height * reveal).toInt())
            backdrop.alpha = (255 * reveal).toInt()
            backdrop.draw(canvas)
        }
    }

    private fun ViewGroup.children() = (0 until childCount).map { getChildAt(it) }
}
