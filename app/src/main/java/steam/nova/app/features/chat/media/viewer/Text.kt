// Nagram/Telegram GPL-2.0-or-later. Text.java's single-line, non-emoji drawing branch.
package steam.nova.app.features.chat.media.viewer

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max
import kotlin.math.min

internal class Text(text: CharSequence?, textSizeDp: Float) {
    private val paint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val layout: StaticLayout
    var currentWidth: Float = 0f
        private set

    private var left: Float = 0f

    init {
        paint.textSize = AndroidUtilities.dp(textSizeDp).toFloat()
        val singleLine = text!!.toString().replace('\n', ' ')
        layout =
            StaticLayout.Builder.obtain(singleLine, 0, singleLine.length, paint, 9999)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
        left = layout.width.toFloat()
        for (i in 0 until layout.lineCount) {
            currentWidth = max(currentWidth, layout.getLineWidth(i))
            left = min(left, layout.getLineLeft(i))
        }
    }

    fun draw(canvas: Canvas, x: Float, cy: Float, color: Int, alpha: Float) {
        paint.color = color
        paint.linkColor = color
        val wasAlpha = paint.alpha
        if (alpha != 1f) paint.alpha = (wasAlpha * alpha).toInt()
        canvas.save()
        canvas.translate(x, cy - layout.height / 2f)
        canvas.save()
        canvas.translate(-left, 0f)
        layout.draw(canvas)
        canvas.restore()
        canvas.restore()
        paint.alpha = wasAlpha
    }
}
