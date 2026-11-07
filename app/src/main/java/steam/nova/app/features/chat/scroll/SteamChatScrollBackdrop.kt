package steam.nova.app.features.chat.scroll

import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.graphics.withSave

/** Frosted branch of Nagram's backdrop. Records only the list, never the control itself. */
@RequiresApi(31)
internal class SteamChatScrollBackdrop(private val source: View, private val target: View) {
    private val node = RenderNode("SteamNova chat page down")
    private val sourceMatrix = Matrix()
    private val targetMatrix = Matrix()
    private val inverse = Matrix()
    private val clip = Path()
    private val displayDensity = target.resources.displayMetrics.density
    private val padding = 40f * displayDensity

    init {
        node.setRenderEffect(RenderEffect.createChainEffect(
            RenderEffect.createBlurEffect(padding, padding, Shader.TileMode.CLAMP),
            RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(3f) }))))
    }

    fun draw(canvas: Canvas) {
        if (!canvas.isHardwareAccelerated || source.width == 0) return
        sourceMatrix.reset(); targetMatrix.reset()
        source.transformMatrixToGlobal(sourceMatrix)
        target.transformMatrixToGlobal(targetMatrix)
        if (!targetMatrix.invert(inverse)) return
        sourceMatrix.postConcat(inverse)
        val width = (target.width + 2 * padding).toInt()
        val height = (target.height + 2 * padding).toInt()
        node.setPosition(0, 0, width, height)
        val recording = node.beginRecording()
        try {
            recording.drawColor(0xff22252b.toInt())
            recording.translate(padding, padding)
            recording.concat(sourceMatrix)
            recording.translate(-source.scrollX.toFloat(), -source.scrollY.toFloat())
            source.draw(recording)
        } finally { node.endRecording() }
        canvas.withSave {
            clip.reset(); clip.addCircle(28f * displayDensity, 36f * displayDensity, 22f * displayDensity, Path.Direction.CW)
            canvas.clipPath(clip)
            canvas.translate(-padding, -padding)
            canvas.drawRenderNode(node)
        }
    }

    fun close() = node.discardDisplayList()
}
