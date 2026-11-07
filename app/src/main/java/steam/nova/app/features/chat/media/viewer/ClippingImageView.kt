/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package steam.nova.app.features.chat.media.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import androidx.annotation.Keep
import androidx.core.graphics.withSave
import java.util.Arrays

open class ClippingImageView(context: Context) : View(context) {

    private var clipBottom: Int = 0
    private var clipLeft: Int = 0
    private var clipRight: Int = 0
    private var clipTop: Int = 0
    private var orientation: Int = 0
    private var invert: Int = 0
    private var imageY: Int = 0
    private var imageX: Int = 0
    private val drawRect: RectF
    private val paint: Paint = Paint(Paint.FILTER_BITMAP_FLAG)
    var bitmapHolder: Bitmap? = null
        private set

    private val imageMatrix: Matrix

    private var needRadius: Boolean = false
    val radius: IntArray = IntArray(4)

    fun setRadius(value: IntArray?) {
        if (value == null) {
            needRadius = false
            Arrays.fill(this@ClippingImageView.radius, 0)
            return
        }
        System.arraycopy(value, 0, this@ClippingImageView.radius, 0, value.size)
        needRadius = false
        for (a in value.indices) {
            if (value[a] != 0) {
                needRadius = true
                break
            }
        }
    }

    private var bitmapShader: BitmapShader? = null
    private val roundPaint: Paint
    private val roundRect: RectF
    private val bitmapRect: RectF
    private val shaderMatrix: Matrix
    private val roundPath: Path = Path()

    private var `in`: Boolean = false
    @get:Keep
    open var animationProgress: Float = 0f
        @Keep
        set(progress) {
            field = progress

            scaleX =
                animationValues!![0][0] +
                    (animationValues!![1][0] - animationValues!![0][0]) * this.animationProgress
            scaleY =
                animationValues!![0][1] +
                    (animationValues!![1][1] - animationValues!![0][1]) * this.animationProgress
            translationX =
                animationValues!![0][2] +
                    additionalTranslationX +
                    (animationValues!![1][2] + additionalTranslationX -
                        animationValues!![0][2] -
                        additionalTranslationX) * this.animationProgress
            translationY =
                animationValues!![0][3] +
                    (animationValues!![1][3] - animationValues!![0][3]) * this.animationProgress
            clipHorizontal =
                (animationValues!![0][4] +
                        (animationValues!![1][4] - animationValues!![0][4]) *
                            this.animationProgress)
                    .toInt()
            setClipTop(
                (animationValues!![0][5] +
                        (animationValues!![1][5] - animationValues!![0][5]) *
                            this.animationProgress)
                    .toInt()
            )
            setClipBottom(
                (animationValues!![0][6] +
                        (animationValues!![1][6] - animationValues!![0][6]) *
                            this.animationProgress)
                    .toInt()
            )
            for (a in this@ClippingImageView.radius.indices) {
                this@ClippingImageView.radius[a] =
                    (animationValues!![0][7 + a] +
                            (animationValues!![1][7 + a] - animationValues!![0][7 + a]) *
                                this.animationProgress)
                        .toInt()
                setRadius(this@ClippingImageView.radius)
            }
            if (animationValues!![0].size > 11) {
                setImageY(
                    (animationValues!![0][11] +
                            (animationValues!![1][11] - animationValues!![0][11]) *
                                this.animationProgress)
                        .toInt()
                )
                setImageX(
                    (animationValues!![0][12] +
                            (animationValues!![1][12] - animationValues!![0][12]) *
                                this.animationProgress)
                        .toInt()
                )
            }
            if (fade) {
                alpha = if (`in`) progress else 1 - progress
            }
            invalidate()
        }

    private var fade: Boolean = false
    private var animationValues: Array<FloatArray>? = null

    private var additionalTranslationY: Float = 0f
    private var additionalTranslationX: Float = 0f

    open var clipHorizontal: Int
        get() {
            return clipRight
        }
        set(value) {
            clipRight = value
            clipLeft = value
            invalidate()
        }

    open val bitmap: Bitmap?
        get() {
            return if (bitmapHolder != null) bitmapHolder else null
        }

    init {
        paint.isFilterBitmap = true
        imageMatrix = Matrix()
        drawRect = RectF()
        bitmapRect = RectF()
        roundPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        roundRect = RectF()
        shaderMatrix = Matrix()
    }

    open fun setAnimationValues(values: Array<FloatArray>?, `in`: Boolean, fade: Boolean) {
        animationValues = values
        this.`in` = `in`
        this.fade = fade
    }

    override fun setTranslationY(translationY: Float) {
        super.setTranslationY(translationY + additionalTranslationY)
    }

    override fun getTranslationY(): Float {
        return super.getTranslationY() - additionalTranslationY
    }

    override fun onDraw(canvas: Canvas) {
        if (visibility != VISIBLE) {
            return
        }
        if (bitmapHolder != null && !bitmapHolder!!.isRecycled) {
            val scaleY = getScaleY()
            canvas.withSave {
                if (needRadius) {
                    shaderMatrix.reset()
                    roundRect.set(
                        imageX / scaleY,
                        imageY / scaleY,
                        this@ClippingImageView.width - imageX / scaleY,
                        this@ClippingImageView.height - imageY / scaleY,
                    )
                    bitmapRect.set(
                        0f,
                        0f,
                        bitmapHolder!!.width.toFloat(),
                        bitmapHolder!!.height.toFloat(),
                    )
                    AndroidUtilities.setRectToRect(
                        shaderMatrix,
                        bitmapRect,
                        roundRect,
                        orientation,
                        invert,
                        false,
                    )
                    bitmapShader!!.setLocalMatrix(shaderMatrix)
                    canvas.clipRect(
                        clipLeft / scaleY,
                        clipTop / scaleY,
                        this@ClippingImageView.width - clipRight / scaleY,
                        this@ClippingImageView.height - clipBottom / scaleY,
                    )

                    for (a in this@ClippingImageView.radius.indices) {
                        radii[a * 2] = this@ClippingImageView.radius[a].toFloat()
                        radii[a * 2 + 1] = this@ClippingImageView.radius[a].toFloat()
                    }
                    roundPath.reset()
                    roundPath.addRoundRect(roundRect, radii, Path.Direction.CW)
                    roundPath.close()
                    canvas.drawPath(roundPath, roundPaint)
                } else {
                    when (orientation) {
                        90,
                        270 -> {
                            drawRect.set(
                                (-this@ClippingImageView.height / 2).toFloat(),
                                (-this@ClippingImageView.width / 2).toFloat(),
                                (this@ClippingImageView.height / 2).toFloat(),
                                (this@ClippingImageView.width / 2).toFloat(),
                            )
                            imageMatrix.setRectToRect(bitmapRect, drawRect, Matrix.ScaleToFit.FILL)
                            if (invert == 1) {
                                imageMatrix.postScale(-1f, 1f)
                            } else if (invert == 2) {
                                imageMatrix.postScale(1f, -1f)
                            }
                            imageMatrix.postRotate(orientation.toFloat(), 0f, 0f)
                            imageMatrix.postTranslate(
                                (this@ClippingImageView.width / 2).toFloat(),
                                (this@ClippingImageView.height / 2).toFloat(),
                            )
                        }
                        180 -> {
                            drawRect.set(
                                (-this@ClippingImageView.width / 2).toFloat(),
                                (-this@ClippingImageView.height / 2).toFloat(),
                                (this@ClippingImageView.width / 2).toFloat(),
                                (this@ClippingImageView.height / 2).toFloat(),
                            )
                            imageMatrix.setRectToRect(bitmapRect, drawRect, Matrix.ScaleToFit.FILL)
                            if (invert == 1) {
                                imageMatrix.postScale(-1f, 1f)
                            } else if (invert == 2) {
                                imageMatrix.postScale(1f, -1f)
                            }
                            imageMatrix.postRotate(orientation.toFloat(), 0f, 0f)
                            imageMatrix.postTranslate(
                                (this@ClippingImageView.width / 2).toFloat(),
                                (this@ClippingImageView.height / 2).toFloat(),
                            )
                        }
                        else -> {
                            drawRect.set(
                                0f,
                                0f,
                                this@ClippingImageView.width.toFloat(),
                                this@ClippingImageView.height.toFloat(),
                            )
                            if (invert == 1) {
                                imageMatrix.postScale(
                                    -1f,
                                    1f,
                                    (this@ClippingImageView.width / 2).toFloat(),
                                    (this@ClippingImageView.height / 2).toFloat(),
                                )
                            } else if (invert == 2) {
                                imageMatrix.postScale(
                                    1f,
                                    -1f,
                                    (this@ClippingImageView.width / 2).toFloat(),
                                    (this@ClippingImageView.height / 2).toFloat(),
                                )
                            }
                            imageMatrix.setRectToRect(bitmapRect, drawRect, Matrix.ScaleToFit.FILL)
                        }
                    }

                    canvas.clipRect(
                        clipLeft / scaleY,
                        clipTop / scaleY,
                        this@ClippingImageView.width - clipRight / scaleY,
                        this@ClippingImageView.height - clipBottom / scaleY,
                    )
                    try {
                        canvas.drawBitmap(bitmapHolder!!, imageMatrix, paint)
                    } catch (e: Exception) {
                        FileLog.e(e)
                    }
                }
            }
        }
    }

    open fun setClipBottom(value: Int) {
        clipBottom = value
        invalidate()
    }

    open fun setClipTop(value: Int) {
        clipTop = value
        invalidate()
    }

    open fun setImageY(value: Int) {
        imageY = value
    }

    open fun setImageX(value: Int) {
        imageX = value
    }

    open fun setImageBitmap(bitmap: Bitmap?) {
        var bitmap = bitmap
        if (bitmapHolder != null) {
            // Bitmap lifetime belongs to the Steam viewer adapter.
            bitmapShader = null
        }
        if (bitmap != null && bitmap.isRecycled) {
            bitmap = null
        }
        bitmapHolder = bitmap
        if (bitmap != null) {
            bitmapRect.set(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            roundPaint.shader = bitmapShader
        }
        invalidate()
    }

    companion object {
        private val radii: FloatArray = FloatArray(8)
    }
}
