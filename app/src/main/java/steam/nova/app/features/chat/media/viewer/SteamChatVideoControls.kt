package steam.nova.app.features.chat.media.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.util.Locale
import steam.nova.app.features.chat.media.SteamChatMediaResources
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.dp

/**
 * PhotoViewer.createVideoControlsInterface / VideoPlayerControlFrameLayout, Steam player adapter.
 */
// The controller requires live player callbacks and is created programmatically.
@SuppressLint("ViewConstructor")
internal class SteamChatVideoControls(
    context: Context,
    private val seek: (Float) -> Unit,
    private val preview: (Float?, Int) -> Unit,
    rotate: () -> Unit,
) : FrameLayout(context) {
    private val time =
        TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.END or Gravity.TOP
            includeFontPadding = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    private val orientation =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER
            setImageDrawable(SteamChatMediaResources.drawable(context, "landscape"))
            setOnClickListener { rotate() }
            background =
                android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33ffffff),
                    null,
                    null,
                )
        }
    private val seekView: View =
        object : View(context) {
            override fun onDraw(canvas: Canvas) {
                seekBar.draw(canvas, this)
            }
        }
    val seekBar = VideoPlayerSeekBar(seekView)
    private var lastTimeWidth = 0
    private val timeValue = FloatValueHolder(0f)
    // PhotoViewer.VideoPlayerControlFrameLayout's original spring and width calculation.
    private val timeSpring =
        SpringAnimation(timeValue)
            .setSpring(
                SpringForce(0f)
                    .setStiffness(750f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
            )
            .addUpdateListener { _, value, _ ->
                seekBar.setSize(
                    (measuredWidth - dp(16f) - value - dp(48f)).toInt().coerceAtLeast(dp(25f)),
                    measuredHeight,
                )
                seekView.invalidate()
            }
    var total = 0L
        private set

    var position = 0L
        private set

    var looping = false
        set(value) {
            field = value
            controlsProgress = controlsProgress
        }

    var controlsProgress = 1f
        set(value) {
            field = value
            time.alpha = value
            orientation.alpha = value
            if (looping) {
                translationY = 0f
                seekView.alpha = 1f
                time.pivotX = time.width.toFloat()
                time.pivotY = time.height.toFloat()
                time.scaleX = 1f - .1f * (1f - value)
                time.scaleY = time.scaleX
                seekBar.transitionProgress = 1f - value
            } else {
                time.scaleX = 1f
                time.scaleY = 1f
                seekBar.transitionProgress = 0f
                translationY = dp(24f) * (1f - value)
                seekView.alpha = value
            }
        }

    init {
        setWillNotDraw(false)
        addView(seekView, LayoutParams(-1, -1))
        addView(
            time,
            LayoutParams(-2, -2, Gravity.END or Gravity.TOP).apply {
                rightMargin = dp(47f)
                topMargin = dp(15f)
            },
        )
        addView(orientation, LayoutParams(dp(48f), dp(48f), Gravity.END or Gravity.TOP))
        seekBar.horizontalPadding = dp(2f)
        seekBar.setColors(0x33ffffff, 0x33ffffff, Color.WHITE, Color.WHITE, Color.WHITE, 0x59ffffff)
        seekBar.setDelegate(
            object : VideoPlayerSeekBar.SeekBarDelegate {
                override fun onSeekBarDrag(progress: Float) {
                    seek(progress)
                    preview(null, seekBar.getWidth())
                }

                override fun onSeekBarContinuousDrag(progress: Float) {
                    preview(progress, seekBar.getWidth())
                }
            }
        )
    }

    fun update(positionMs: Long, totalMs: Long) {
        position = positionMs
        val changed = total != totalMs
        total = totalMs
        time.text = String.format(Locale.getDefault(), "%s / %s", time(positionMs), time(totalMs))
        if (!seekBar.isDragging)
            seekBar.setProgress(if (totalMs > 0) positionMs.toFloat() / totalMs else 0f)
        seekBar.setBufferedProgress(1f)
        if (changed) requestLayout()
        seekView.invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val totalText = time(total)
        val textWidth = kotlin.math.ceil(time.paint.measureText("$totalText / $totalText")).toInt()
        timeSpring.cancel()
        if (lastTimeWidth != 0 && timeValue.value != textWidth.toFloat()) {
            timeSpring.spring.finalPosition = textWidth.toFloat()
            timeSpring.start()
        } else {
            seekBar.setSize(
                (measuredWidth - dp(16f) - textWidth - dp(48f)).coerceAtLeast(dp(25f)),
                measuredHeight,
            )
            timeValue.value = textWidth.toFloat()
        }
        lastTimeWidth = textWidth
        orientation.setImageDrawable(
            SteamChatMediaResources.drawable(
                context,
                if (resources.configuration.orientation == 2) "portrait" else "landscape",
            )
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (controlsProgress < 1f) return false
        if (seekBar.onTouch(event.action, event.x - dp(2f), event.y)) {
            parent.requestDisallowInterceptTouchEvent(true)
            seekView.invalidate()
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) preview(null, seekBar.getWidth())
            if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        timeSpring.cancel()
        timeValue.value = 0f
        lastTimeWidth = 0
        super.onDetachedFromWindow()
    }

    companion object {
        fun time(ms: Long): String {
            val s = ms.coerceAtLeast(0) / 1000
            return if (s / 60 > 60)
                String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
            else String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60)
        }
    }
}
