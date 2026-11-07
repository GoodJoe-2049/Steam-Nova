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
    // Capture the media child, excluding download/menu overlays from the opening image.
    private var thumbnail: Bitmap? = source.get()?.let(::captureMedia)
    private var closingFrame: Bitmap? = null
    private var animation: ValueAnimator? = null
    private var transitionView: ClippingImageView? = null
    private var contentView: View? = null
    var running = false
        private set

    fun run(
        root: FrameLayout,
        content: View,
        image: Bitmap?,
        opening: Boolean,
        ratio: Float,
        chrome: (Float) -> Unit,
        zoom: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        finish: () -> Unit,
    ): Boolean {
        val origin = source.get() ?: return false
        val originalAlpha = origin.alpha
        origin.alpha = 1f
        val visible = Rect()
        val visibleNow = origin.getGlobalVisibleRect(visible)
        origin.alpha = originalAlpha
        if (!visibleNow || origin.width <= 0 || origin.height <= 0) return false
        if (!opening) closingFrame = captureTexture(content)
        val bitmap = closingFrame ?: image ?: thumbnail ?: return false
        val point = IntArray(2)
        origin.getLocationOnScreen(point)
        val window = IntArray(2)
        root.getLocationOnScreen(window)
        val mediaRatio =
            ratio.takeIf { it.isFinite() && it > 0 } ?: (bitmap.width.toFloat() / bitmap.height)
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
        transitionView = clip
        contentView = content
        running = true
        content.alpha = 0f
        origin.alpha = 0f
        clip.animationProgress = 0f
        animation =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200
                addUpdateListener {
                    val p = it.animatedValue as Float
                    clip.animationProgress = p
                    chrome(if (opening) p else 1f - p)
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            root.removeView(clip)
                            clip.setImageBitmap(null)
                            transitionView = null
                            contentView = null
                            running = false
                            content.alpha = 1f
                            origin.alpha = 1f
                            closingFrame?.recycle()
                            closingFrame = null
                            finish()
                        }
                    }
                )
                start()
            }
        return true
    }

    private fun captureTexture(view: View): Bitmap? {
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
        if (view is ImageView && view.width > 0 && view.height > 0)
            return runCatching {
                    createBitmap(view.width, view.height).also { view.draw(Canvas(it)) }
                }
                .getOrNull()
        if (view is ViewGroup)
            for (i in 0 until view.childCount) captureMedia(view.getChildAt(i))?.let {
                return it
            }
        return null
    }

    fun restoreSource() {
        source.get()?.alpha = 1f
    }

    fun cancel() {
        animation?.removeAllListeners()
        animation?.cancel()
        animation = null
        transitionView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.setImageBitmap(null)
        }
        transitionView = null
        contentView?.alpha = 1f
        contentView = null
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
