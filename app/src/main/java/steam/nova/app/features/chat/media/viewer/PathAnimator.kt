/*
 * This is the source code of Telegram for Android v. 6.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package steam.nova.app.features.chat.media.viewer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import java.util.ArrayList

open class PathAnimator(
    private val scale: Float,
    private val tx: Float,
    private val ty: Float,
    open val durationScale: Float,
) {

    private val path: Path = Path()
    private var pathTime: Float = -1f
    private val keyFrames: ArrayList<KeyFrame?> = ArrayList<KeyFrame?>()

    private open class KeyFrame {
        var commands: ArrayList<Any?>? = ArrayList<Any?>()
        var time: Float = 0f
    }

    private open class MoveTo {
        var x: Float = 0f
        var y: Float = 0f
    }

    private open class LineTo {
        var x: Float = 0f
        var y: Float = 0f
    }

    private open class CurveTo {
        var x: Float = 0f
        var y: Float = 0f
        var x1: Float = 0f
        var y1: Float = 0f
        var x2: Float = 0f
        var y2: Float = 0f
    }

    open fun addSvgKeyFrame(svg: String?, ms: Float) {
        if (svg == null) {
            return
        }
        try {
            val keyFrame = KeyFrame()
            keyFrame.time = ms * durationScale
            val args = svg.split((" ").toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var a = 0
            while (a < args.size) {
                when (args[a][0]) {
                    'M' -> {
                        val moveTo = MoveTo()
                        moveTo.x = (java.lang.Float.parseFloat(args[a + 1]) + tx) * scale
                        moveTo.y = (java.lang.Float.parseFloat(args[a + 2]) + ty) * scale
                        keyFrame.commands!!.add(moveTo)
                        a += 2
                    }
                    'C' -> {
                        val curveTo = CurveTo()
                        curveTo.x1 = (java.lang.Float.parseFloat(args[a + 1]) + tx) * scale
                        curveTo.y1 = (java.lang.Float.parseFloat(args[a + 2]) + ty) * scale
                        curveTo.x2 = (java.lang.Float.parseFloat(args[a + 3]) + tx) * scale
                        curveTo.y2 = (java.lang.Float.parseFloat(args[a + 4]) + ty) * scale
                        curveTo.x = (java.lang.Float.parseFloat(args[a + 5]) + tx) * scale
                        curveTo.y = (java.lang.Float.parseFloat(args[a + 6]) + ty) * scale
                        keyFrame.commands!!.add(curveTo)
                        a += 6
                    }
                    'L' -> {
                        val lineTo = LineTo()
                        lineTo.x = (java.lang.Float.parseFloat(args[a + 1]) + tx) * scale
                        lineTo.y = (java.lang.Float.parseFloat(args[a + 2]) + ty) * scale
                        keyFrame.commands!!.add(lineTo)
                        a += 2
                    }
                }
                a++
            }
            keyFrames.add(keyFrame)
        } catch (e: Exception) {
            FileLog.e(e)
        }
    }

    open fun draw(canvas: Canvas, paint: Paint, time: Float) {
        if (pathTime != time) {
            pathTime = time
            var startKeyFrame: KeyFrame? = null
            var endKeyFrame: KeyFrame? = null
            run {
                var a = 0
                val count = keyFrames.size
                while (a < count) {
                    val keyFrame = keyFrames[a]
                    if (
                        (startKeyFrame == null || startKeyFrame.time < keyFrame!!.time) &&
                            keyFrame!!.time <= time
                    ) {
                        startKeyFrame = keyFrame
                    }
                    if (
                        (endKeyFrame == null || endKeyFrame.time > keyFrame.time) &&
                            keyFrame.time >= time
                    ) {
                        endKeyFrame = keyFrame
                    }
                    a++
                }
            }
            if (endKeyFrame === startKeyFrame) {
                startKeyFrame = null
            }
            if (startKeyFrame != null && endKeyFrame == null) {
                endKeyFrame = startKeyFrame
                startKeyFrame = null
            }
            if (
                endKeyFrame == null ||
                    startKeyFrame != null &&
                        startKeyFrame.commands!!.size != endKeyFrame.commands!!.size
            ) {
                return
            }
            path.reset()
            var a = 0
            val count = endKeyFrame.commands!!.size
            while (a < count) {
                val startCommand = if (startKeyFrame != null) startKeyFrame.commands!![a] else null
                val endCommand = endKeyFrame.commands!![a]
                if (startCommand != null && startCommand.javaClass != endCommand!!.javaClass) {
                    return
                }
                val progress =
                    if (startKeyFrame != null) {
                        (time - startKeyFrame.time) / (endKeyFrame.time - startKeyFrame.time)
                    } else {
                        1.0f
                    }
                if (endCommand is MoveTo) {
                    val end = endCommand as MoveTo?
                    val start = startCommand as MoveTo?
                    if (start != null) {
                        path.moveTo(
                            AndroidUtilities.dpf2(start.x + (end!!.x - start.x) * progress),
                            AndroidUtilities.dpf2(start.y + (end.y - start.y) * progress),
                        )
                    } else {
                        path.moveTo(AndroidUtilities.dpf2(end!!.x), AndroidUtilities.dpf2(end.y))
                    }
                } else if (endCommand is LineTo) {
                    val end = endCommand as LineTo?
                    val start = startCommand as LineTo?
                    if (start != null) {
                        path.lineTo(
                            AndroidUtilities.dpf2(start.x + (end!!.x - start.x) * progress),
                            AndroidUtilities.dpf2(start.y + (end.y - start.y) * progress),
                        )
                    } else {
                        path.lineTo(AndroidUtilities.dpf2(end!!.x), AndroidUtilities.dpf2(end.y))
                    }
                } else if (endCommand is CurveTo) {
                    val end = endCommand as CurveTo?
                    val start = startCommand as CurveTo?
                    if (start != null) {
                        path.cubicTo(
                            AndroidUtilities.dpf2(start.x1 + (end!!.x1 - start.x1) * progress),
                            AndroidUtilities.dpf2(start.y1 + (end.y1 - start.y1) * progress),
                            AndroidUtilities.dpf2(start.x2 + (end.x2 - start.x2) * progress),
                            AndroidUtilities.dpf2(start.y2 + (end.y2 - start.y2) * progress),
                            AndroidUtilities.dpf2(start.x + (end.x - start.x) * progress),
                            AndroidUtilities.dpf2(start.y + (end.y - start.y) * progress),
                        )
                    } else {
                        path.cubicTo(
                            AndroidUtilities.dpf2(end!!.x1),
                            AndroidUtilities.dpf2(end.y1),
                            AndroidUtilities.dpf2(end.x2),
                            AndroidUtilities.dpf2(end.y2),
                            AndroidUtilities.dpf2(end.x),
                            AndroidUtilities.dpf2(end.y),
                        )
                    }
                }
                a++
            }
            path.close()
        }
        canvas.drawPath(path, paint)
    }
}
