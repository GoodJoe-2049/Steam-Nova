package steam.nova.app.features.chat.media.viewer

import androidx.core.view.isVisible
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.dp

/** PhotoViewer PlaceProvider/animationValues adapter for a tagged RN media card. */
internal class SteamChatMediaTransition(activity: Activity, tag: String) {
    private val source = WeakReference(activity.window.decorView.findViewWithTag<View>(tag))
    private val sourceAlpha = source.get()?.alpha ?: 1f
    // Capture the media child, excluding download/menu overlays from the opening image.
    private var thumbnail: Bitmap? = source.get()?.let(::captureMedia)
    private var closingFrame: Bitmap? = null
    private var animation: ValueAnimator? = null
    private var transitionView: ClippingImageView? = null
    private var contentView: View? = null
    private var openingAnimation = false
    private var returning = false
    private var finished: (() -> Unit)? = null
    var running = false
        private set

    fun copyThumbnail(): Bitmap? = thumbnail?.copy(Bitmap.Config.ARGB_8888, false)

    fun reverseOpening(finish: () -> Unit): Boolean {
        if (!running || !openingAnimation || returning) return false
        returning = true
        finished = finish
        animation?.reverse()
        return true
    }

    fun run(
        root: FrameLayout,
        content: View,
        image: Bitmap?,
        opening: Boolean,
        chrome: (Float) -> Unit,
        zoom: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        finish: () -> Unit,
    ): Boolean {
        closingFrame?.recycle()
        closingFrame = if (opening) null else image
        val origin = source.get() ?: return false
        val originalAlpha = origin.alpha
        origin.alpha = 1f
        val visible = Rect()
        val visibleNow = origin.getGlobalVisibleRect(visible)
        origin.alpha = originalAlpha
        if (!visibleNow || origin.width <= 0 || origin.height <= 0 || root.width <= 0 || root.height <= 0)
            return false
        val bitmap = (if (opening) thumbnail else closingFrame ?: image ?: thumbnail)
            ?.takeUnless { it.isRecycled } ?: return false
        val point = IntArray(2)
        origin.getLocationOnScreen(point)
        val window = IntArray(2)
        root.getLocationOnScreen(window)
        val mediaRatio = bitmap.width.toFloat() / bitmap.height
        val coveredWidth = max(origin.width.toFloat(), origin.height * mediaRatio)
        val coveredHeight = coveredWidth / mediaRatio
        val drawLeft = point[0] - window[0] - (coveredWidth - origin.width) / 2f
        val drawTop = point[1] - window[1] - (coveredHeight - origin.height) / 2f
        val scale = min(root.width / coveredWidth, root.height / coveredHeight)
        val sourceValues =
            floatArrayOf(
                1f,
                1f,
                drawLeft,
                drawTop,
                (coveredWidth - origin.width) / 2f,
                max(
                    (coveredHeight - origin.height) / 2f,
                    visible.top - point[1] + (coveredHeight - origin.height) / 2f,
                ),
                max(
                    (coveredHeight - origin.height) / 2f,
                    point[1] + origin.height - visible.bottom + (coveredHeight - origin.height) / 2f,
                ),
                dp(16f).toFloat(),
                dp(16f).toFloat(),
                dp(16f).toFloat(),
                dp(16f).toFloat(),
                0f,
                0f,
            )
        val fullValues =
            floatArrayOf(
                scale * zoom,
                scale * zoom,
                (root.width - coveredWidth * scale * zoom) / 2 + offsetX,
                (root.height - coveredHeight * scale * zoom) / 2 + offsetY,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
            )
        val clip =
            ClippingImageView(root.context).apply {
                pivotX = 0f
                pivotY = 0f
                setImageBitmap(bitmap)
                setAnimationValues(
                    if (opening) arrayOf(sourceValues, fullValues)
                    else arrayOf(fullValues, sourceValues),
                    opening,
                    false,
                )
            }
        root.addView(clip, FrameLayout.LayoutParams(coveredWidth.toInt(), coveredHeight.toInt()))
        clip.measure(
            View.MeasureSpec.makeMeasureSpec(coveredWidth.toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(coveredHeight.toInt(), View.MeasureSpec.EXACTLY),
        )
        clip.layout(0, 0, clip.measuredWidth, clip.measuredHeight)
        transitionView = clip
        contentView = content
        running = true
        openingAnimation = opening
        returning = !opening
        finished = finish
        content.alpha = 0f
        origin.alpha = 0f
        clip.animationProgress = 0f
        chrome(if (opening) 0f else 1f)
        animation =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200
                addUpdateListener {
                    val p = it.animatedValue as Float
                    clip.animationProgress = p
                    chrome(if (opening) p else 1f - p)
                    root.invalidate()
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            root.removeView(clip)
                            clip.setImageBitmap(null)
                            transitionView = null
                            contentView = null
                            running = false
                            if (!returning) content.alpha = 1f else restoreSource()
                            this@SteamChatMediaTransition.animation = null
                            val complete = finished
                            finished = null
                            complete?.invoke()
                        }
                    }
                )
                start()
            }
        return true
    }

    private fun captureTexture(view: View): Bitmap? {
        if (!view.isVisible || view.alpha <= 0f) return null
        if (view is TextureView && view.isAvailable && view.isVisible)
            return runCatching { view.bitmap }.getOrNull()
        if (view is ViewGroup)
            for (i in 0 until view.childCount) captureTexture(view.getChildAt(i))?.let {
                return it
            }
        return null
    }

    private fun captureMedia(view: View): Bitmap? {
        captureTexture(view)?.let {
            return it
        }
        return captureImage(view, view.width.toLong() * view.height / 2)
    }

    private fun captureImage(view: View, minimumArea: Long): Bitmap? {
        if (!view.isVisible || view.alpha <= 0f) return null
        if (view is ImageView && view.width > 0 && view.height > 0 &&
            view.width.toLong() * view.height >= minimumArea)
            return runCatching {
                createBitmap(view.width, view.height).also { view.draw(Canvas(it)) }
            }
                .getOrNull()
        if (view is ViewGroup)
            for (i in 0 until view.childCount) captureImage(view.getChildAt(i), minimumArea)?.let {
                return it
            }
        return null
    }

    fun restoreSource() {
        source.get()?.alpha = sourceAlpha
    }

    fun hideSource() {
        source.get()?.alpha = 0f
    }

    fun cancel() {
        animation?.removeAllListeners()
        animation?.removeAllUpdateListeners()
        animation?.cancel()
        animation = null
        transitionView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.setImageBitmap(null)
        }
        transitionView = null
        contentView?.alpha = 1f
        contentView = null
        finished = null
        restoreSource()
        running = false
        closingFrame?.recycle()
        closingFrame = null
    }

    fun close() {
        cancel()
        thumbnail?.recycle()
        thumbnail = null
    }
}
