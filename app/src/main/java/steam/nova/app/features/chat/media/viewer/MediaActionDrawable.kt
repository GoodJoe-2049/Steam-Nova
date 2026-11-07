package steam.nova.app.features.chat.media.viewer

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.withTranslation
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

open class MediaActionDrawable : Drawable() {

    private val textPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    var paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var paint2: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paint3: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect: RectF = RectF()
    private var colorFilter: ColorFilter? = null
    private var scale: Float = 1.0f
    private val interpolator: DecelerateInterpolator = DecelerateInterpolator()

    private var isMini: Boolean = false

    private var transitionAnimationTime: Float = 400.0f

    private var lastPercent: Int = -1
    private var percentString: String? = null
    private var percentStringWidth: Int = 0

    private var overrideAlpha: Float = 1.0f

    var previousIcon: Int = 0
        private set

    var currentIcon: Int = 0
        private set

    private var transitionProgress: Float = 1.0f
    private var savedTransitionProgress: Float = 0f
    private var lastAnimationTime: Long = 0
    private var animatingTransition: Boolean = false

    private var downloadRadOffset: Float = 0f
    var progress: Float = 0f
        private set

    private var animatedDownloadProgress: Float = 0f
    private var downloadProgressAnimationStart: Float = 0f
    private var downloadProgressTime: Float = 0f

    private var delegate: MediaActionDrawableDelegate? = null

    private var gradientDrawable: LinearGradient? = null
    private var gradientMatrix: Matrix? = null
    private var hasOverlayImage: Boolean = false

    open var color: Int
        get() {
            return paint.color
        }
        set(value) {
            paint.color = value or -0x1000000
            paint2.color = value or -0x1000000
            paint3.color = value or -0x1000000
            textPaint.color = value or -0x1000000
            colorFilter = PorterDuffColorFilter(value, PorterDuff.Mode.SRC_IN)
        }

    interface MediaActionDrawableDelegate {
        fun invalidate()
    }

    init {
        paint.color = -0x1
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = AndroidUtilities.dp(3f).toFloat()
        paint.style = Paint.Style.STROKE

        paint3.color = -0x1

        textPaint.typeface = AndroidUtilities.bold()
        textPaint.textSize = AndroidUtilities.dp(13f).toFloat()
        textPaint.color = -0x1

        paint2.color = -0x1
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        paint2.colorFilter = colorFilter
        paint3.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
    }

    @Deprecated("Required by Android Drawable")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSPARENT
    }

    open fun setIcon(icon: Int, animated: Boolean): Boolean {
        if (previousIcon == icon && this@MediaActionDrawable.currentIcon != icon) {
            previousIcon = this@MediaActionDrawable.currentIcon
            transitionProgress = 1.0f
        }
        if (animated) {
            if (previousIcon == icon || this@MediaActionDrawable.currentIcon == icon) {
                return false
            }
            if (
                previousIcon == ICON_PLAY && icon == ICON_PAUSE ||
                    previousIcon == ICON_PAUSE && icon == ICON_PLAY
            ) {
                transitionAnimationTime = 300.0f
            } else if (
                previousIcon == ICON_DOWNLOAD && (icon == ICON_CANCEL || icon == ICON_CANCEL_FILL)
            ) {
                transitionAnimationTime = 400.0f
            } else if (previousIcon != ICON_NONE && icon == ICON_CHECK) {
                transitionAnimationTime = 360.0f
            } else if (
                previousIcon == ICON_NONE && icon == ICON_CANCEL_FILL ||
                    previousIcon == ICON_CANCEL_FILL && icon == ICON_NONE
            ) {
                transitionAnimationTime = 160.0f
            } else {
                transitionAnimationTime = 220.0f
            }
            if (animatingTransition) {
                previousIcon = this@MediaActionDrawable.currentIcon
            }
            animatingTransition = true
            this@MediaActionDrawable.currentIcon = icon
            savedTransitionProgress = transitionProgress
            transitionProgress = 0.0f
        } else {
            if (previousIcon == icon) {
                return false
            }
            animatingTransition = false
            this@MediaActionDrawable.currentIcon = icon
            previousIcon = this@MediaActionDrawable.currentIcon
            savedTransitionProgress = transitionProgress
            transitionProgress = 1.0f
        }
        if (icon == ICON_CANCEL || icon == ICON_CANCEL_FILL) {
            downloadRadOffset = 112f
            animatedDownloadProgress = 0.0f
            downloadProgressAnimationStart = 0.0f
            downloadProgressTime = 0.0f
        }
        invalidateSelf()
        return true
    }

    open fun setProgress(value: Float, animated: Boolean) {
        if (progress == value) {
            return
        }
        if (!animated) {
            animatedDownloadProgress = value
            downloadProgressAnimationStart = value
        } else {
            if (animatedDownloadProgress > value) {
                animatedDownloadProgress = value
            }
            downloadProgressAnimationStart = animatedDownloadProgress
        }
        progress = value
        downloadProgressTime = 0f
        invalidateSelf()
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        scale = (right - left) / intrinsicWidth.toFloat()
        if (scale < 0.7f) {
            paint.strokeWidth = AndroidUtilities.dp(2f).toFloat()
        }
    }

    override fun invalidateSelf() {
        super.invalidateSelf()
        if (delegate != null) {
            delegate!!.invalidate()
        }
    }

    // Steam controls do not use a Telegram message-bubble gradient.
    open fun applyShaderMatrix(path: Boolean) {}

    override fun draw(canvas: Canvas) {
        val bounds = getBounds()

        if (gradientDrawable != null && !hasOverlayImage) {
            gradientMatrix!!.reset()
            gradientMatrix!!.setTranslate(0f, bounds.top.toFloat())
            gradientDrawable!!.setLocalMatrix(gradientMatrix)
            paint.shader = gradientDrawable
            paint2.shader = gradientDrawable
            paint3.shader = gradientDrawable
        } else {
            paint.shader = null
            paint2.shader = null
            paint3.shader = null
        }

        val cx = bounds.centerX()
        val cy = bounds.centerY()

        var saveCount = 0

        if (this@MediaActionDrawable.currentIcon == ICON_NONE) {
            if (previousIcon != ICON_CANCEL && previousIcon != ICON_CANCEL_FILL) {
                saveCount = canvas.save()
                val progress = 1.0f - transitionProgress
                canvas.scale(progress, progress, cx.toFloat(), cy.toFloat())
            }
        } else if (
            (this@MediaActionDrawable.currentIcon == ICON_CHECK ||
                this@MediaActionDrawable.currentIcon == ICON_EMPTY) && previousIcon == ICON_NONE
        ) {
            saveCount = canvas.save()
            canvas.scale(transitionProgress, transitionProgress, cx.toFloat(), cy.toFloat())
        }

        if (
            previousIcon == ICON_DOWNLOAD || this@MediaActionDrawable.currentIcon == ICON_DOWNLOAD
        ) {
            applyShaderMatrix(false)
            val yStart = cy - AndroidUtilities.dp(9f) * scale
            val yEnd = cy + AndroidUtilities.dp(9f) * scale
            var yStart2: Float
            val yEnd2 = cy + AndroidUtilities.dp(12f) * scale

            var transition: Float
            if (
                (previousIcon == ICON_CANCEL || previousIcon == ICON_CANCEL_FILL) &&
                    this@MediaActionDrawable.currentIcon == ICON_DOWNLOAD
            ) {
                paint.alpha = (255 * min(1.0f, transitionProgress / 0.5f)).toInt()
                transition = transitionProgress
                yStart2 = cy + AndroidUtilities.dp(12f) * scale
            } else {
                if (
                    this@MediaActionDrawable.currentIcon != ICON_CANCEL &&
                        this@MediaActionDrawable.currentIcon != ICON_CANCEL_FILL &&
                        this@MediaActionDrawable.currentIcon != ICON_DOWNLOAD
                ) {
                    paint.alpha =
                        (255f *
                                min(1.0f, savedTransitionProgress / 0.5f) *
                                (1.0f - transitionProgress))
                            .toInt()
                    transition = savedTransitionProgress
                } else {
                    paint.alpha = 255
                    transition = transitionProgress
                }
                yStart2 = cy + AndroidUtilities.dp(1f) * scale
            }

            var y1: Float
            var y2: Float
            var x1: Float
            var x2: Float
            var y3: Float
            if (animatingTransition) {
                var progress = transition
                if (
                    this@MediaActionDrawable.currentIcon == ICON_DOWNLOAD ||
                        progress <= DOWNLOAD_TO_CANCEL_STAGE1
                ) {
                    var currentProgress: Float
                    var currentBackProgress: Float
                    if (this@MediaActionDrawable.currentIcon == ICON_DOWNLOAD) {
                        currentBackProgress = transition
                        currentProgress = 1.0f - currentBackProgress
                    } else {
                        currentProgress = transition / DOWNLOAD_TO_CANCEL_STAGE1
                        currentBackProgress = 1.0f - currentProgress
                    }
                    y1 = yStart + (yStart2 - yStart) * currentProgress
                    y2 = yEnd + (yEnd2 - yEnd) * currentProgress
                    x1 = cx - AndroidUtilities.dp(8f).toFloat() * currentBackProgress * scale
                    x2 = cx + AndroidUtilities.dp(8f).toFloat() * currentBackProgress * scale
                    y3 = y2 - AndroidUtilities.dp(8f).toFloat() * currentBackProgress * scale
                } else {
                    var currentProgress: Float
                    var currentProgress2: Float
                    var d =
                        AndroidUtilities.dp(13f).toFloat() * scale * scale +
                            (if (isMini) AndroidUtilities.dp(2f) else 0)

                    progress -= DOWNLOAD_TO_CANCEL_STAGE1
                    val currentProgress3 =
                        progress / (DOWNLOAD_TO_CANCEL_STAGE2 + DOWNLOAD_TO_CANCEL_STAGE3)
                    if (progress > DOWNLOAD_TO_CANCEL_STAGE2) {
                        progress -= DOWNLOAD_TO_CANCEL_STAGE2
                        currentProgress = 1.0f
                        currentProgress2 = progress / DOWNLOAD_TO_CANCEL_STAGE3
                    } else {
                        currentProgress = progress / DOWNLOAD_TO_CANCEL_STAGE2
                        currentProgress2 = 0.0f
                    }
                    rect.set(cx - d, yEnd2 - d / 2, cx.toFloat(), yEnd2 + d / 2)
                    val start = 100 * currentProgress2
                    canvas.drawArc(rect, start, 104 * currentProgress3 - start, false, paint)

                    y1 = yStart2 + (yEnd2 - yStart2) * currentProgress
                    y3 = yEnd2
                    y2 = y3
                    x2 = cx.toFloat()
                    x1 = x2

                    if (currentProgress2 > 0) {
                        val rotation =
                            if (this@MediaActionDrawable.currentIcon == ICON_CANCEL_FILL) {
                                0f
                            } else {
                                -45 * (1.0f - currentProgress2)
                            }
                        d = AndroidUtilities.dp(7f).toFloat() * currentProgress2 * scale
                        var alpha = (255 * currentProgress2).toInt()
                        if (
                            this@MediaActionDrawable.currentIcon != ICON_CANCEL &&
                                this@MediaActionDrawable.currentIcon != ICON_CANCEL_FILL &&
                                this@MediaActionDrawable.currentIcon != ICON_DOWNLOAD
                        ) {
                            val backProgress = (1.0f - min(1.0f, transitionProgress / 0.5f))
                            // d *= backProgress;
                            alpha = (alpha * backProgress).toInt()
                        }

                        if (rotation != 0f) {
                            canvas.save()
                            canvas.rotate(rotation, cx.toFloat(), cy.toFloat())
                        }
                        if (alpha != 0) {
                            paint.alpha = alpha
                            if (this@MediaActionDrawable.currentIcon == ICON_CANCEL_FILL) {
                                paint3.alpha = alpha
                                rect.set(
                                    (cx - AndroidUtilities.dp(3.5f)).toFloat(),
                                    (cy - AndroidUtilities.dp(3.5f)).toFloat(),
                                    (cx + AndroidUtilities.dp(3.5f)).toFloat(),
                                    (cy + AndroidUtilities.dp(3.5f)).toFloat(),
                                )
                                canvas.drawRoundRect(
                                    rect,
                                    AndroidUtilities.dp(2f).toFloat(),
                                    AndroidUtilities.dp(2f).toFloat(),
                                    paint3,
                                )

                                paint.alpha = (alpha * 0.15f).toInt()
                                val diff = AndroidUtilities.dp((if (isMini) 2 else 4).toFloat())
                                rect.set(
                                    (bounds.left + diff).toFloat(),
                                    (bounds.top + diff).toFloat(),
                                    (bounds.right - diff).toFloat(),
                                    (bounds.bottom - diff).toFloat(),
                                )
                                canvas.drawArc(rect, 0f, 360f, false, paint)
                                paint.alpha = alpha
                            } else {
                                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint)
                                canvas.drawLine(cx + d, cy - d, cx - d, cy + d, paint)
                            }
                        }
                        if (rotation != 0f) {
                            canvas.restore()
                        }
                    }
                }
            } else {
                y1 = yStart
                y2 = yEnd
                x1 = cx - AndroidUtilities.dp(8f) * scale
                x2 = cx + AndroidUtilities.dp(8f) * scale
                y3 = y2 - AndroidUtilities.dp(8f) * scale
            }
            if (y1 != y2) {
                canvas.drawLine(cx.toFloat(), y1, cx.toFloat(), y2, paint)
            }
            if (x1 != cx.toFloat()) {
                canvas.drawLine(x1, y3, cx.toFloat(), y2, paint)
                canvas.drawLine(x2, y3, cx.toFloat(), y2, paint)
            }
        }

        if (
            previousIcon == ICON_CANCEL ||
                previousIcon == ICON_CANCEL_FILL ||
                previousIcon == ICON_NONE &&
                    (this@MediaActionDrawable.currentIcon == ICON_CANCEL_FILL ||
                        this@MediaActionDrawable.currentIcon == ICON_CANCEL)
        ) {
            applyShaderMatrix(false)
            var d: Float
            var rotation: Float
            var iconScale = 1.0f

            var iconScaleX = 0f

            var iconScaleY = 0f

            var alpha: Int
            if (this@MediaActionDrawable.currentIcon == ICON_DOWNLOAD) {
                if (transitionProgress <= DOWNLOAD_TO_CANCEL_STAGE3 + DOWNLOAD_TO_CANCEL_STAGE2) {
                    val progress =
                        transitionProgress / (DOWNLOAD_TO_CANCEL_STAGE3 + DOWNLOAD_TO_CANCEL_STAGE2)
                    val backProgress = 1.0f - progress
                    d = AndroidUtilities.dp(7f).toFloat() * backProgress * scale
                    alpha = (255 * backProgress).toInt()
                } else {
                    d = 0f
                    alpha = 0
                }
                rotation = 0f
            } else if (
                this@MediaActionDrawable.currentIcon == ICON_UPDATE ||
                    this@MediaActionDrawable.currentIcon == ICON_PLAY ||
                    this@MediaActionDrawable.currentIcon == ICON_PAUSE ||
                    this@MediaActionDrawable.currentIcon == ICON_FILE ||
                    this@MediaActionDrawable.currentIcon == ICON_GIF ||
                    this@MediaActionDrawable.currentIcon == ICON_SECRETCHECK ||
                    this@MediaActionDrawable.currentIcon == ICON_FIRE ||
                    this@MediaActionDrawable.currentIcon == ICON_CHECK
            ) {
                val progress =
                    if (this@MediaActionDrawable.currentIcon == ICON_CHECK) {
                        min(1.0f, transitionProgress / CANCEL_TO_CHECK_STAGE1)
                    } else {
                        transitionProgress
                    }
                val backProgress = 1.0f - progress
                rotation = 0f

                iconScale = 1.0f - progress
                iconScaleX = bounds.centerX().toFloat()
                iconScaleY = bounds.centerY().toFloat()
                d = AndroidUtilities.dp(7f).toFloat() * backProgress * scale
                alpha = (255 * min(1.0f, backProgress * 2.0f)).toInt()
            } else if (this@MediaActionDrawable.currentIcon == ICON_NONE) {
                val progress = transitionProgress
                val backProgress = 1.0f - progress
                d = AndroidUtilities.dp(7f) * scale
                alpha = (255 * backProgress).toInt()
                rotation = 0f
                iconScale = backProgress

                if (previousIcon == ICON_CANCEL_FILL) {
                    iconScaleX = bounds.left.toFloat()
                    iconScaleY = bounds.top.toFloat()
                } else {
                    iconScaleX = bounds.centerX().toFloat()
                    iconScaleY = bounds.centerY().toFloat()
                }
            } else if (
                this@MediaActionDrawable.currentIcon == ICON_CANCEL_FILL ||
                    this@MediaActionDrawable.currentIcon == ICON_CANCEL
            ) {
                val progress = transitionProgress
                val backProgress = 1.0f - progress
                if (previousIcon == ICON_NONE) {
                    rotation = 0f

                    iconScale = progress
                } else {
                    rotation = 45 * backProgress

                    iconScale = 1.0f
                }
                d = AndroidUtilities.dp(7f) * scale
                alpha = (255 * progress).toInt()
                if (this@MediaActionDrawable.currentIcon == ICON_CANCEL_FILL) {
                    iconScaleX = bounds.left.toFloat()
                    iconScaleY = bounds.top.toFloat()
                } else {
                    iconScaleX = bounds.centerX().toFloat()
                    iconScaleY = bounds.centerY().toFloat()
                }
            } else {
                rotation = 0f
                d = AndroidUtilities.dp(7f) * scale
                alpha = 255
            }
            if (iconScale != 1.0f) {
                canvas.save()
                canvas.scale(iconScale, iconScale, iconScaleX, iconScaleY)
            }
            if (rotation != 0f) {
                canvas.save()
                canvas.rotate(rotation, cx.toFloat(), cy.toFloat())
            }
            if (alpha != 0) {
                paint.alpha = (alpha * overrideAlpha).toInt()
                if (
                    previousIcon == ICON_CANCEL_FILL ||
                        this@MediaActionDrawable.currentIcon == ICON_CANCEL_FILL
                ) {
                    paint3.alpha = (alpha * overrideAlpha).toInt()
                    rect.set(
                        (cx - AndroidUtilities.dp(3.5f)).toFloat(),
                        (cy - AndroidUtilities.dp(3.5f)).toFloat(),
                        (cx + AndroidUtilities.dp(3.5f)).toFloat(),
                        (cy + AndroidUtilities.dp(3.5f)).toFloat(),
                    )
                    canvas.drawRoundRect(
                        rect,
                        AndroidUtilities.dp(2f).toFloat(),
                        AndroidUtilities.dp(2f).toFloat(),
                        paint3,
                    )
                } else {
                    canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint)
                    canvas.drawLine(cx + d, cy - d, cx - d, cy + d, paint)
                }
            }
            if (rotation != 0f) {
                canvas.restore()
            }
            if (iconScale != 1.0f) {
                canvas.restore()
            }

            if (
                (previousIcon == ICON_CANCEL ||
                    previousIcon == ICON_CANCEL_FILL ||
                    previousIcon == ICON_NONE &&
                        (this@MediaActionDrawable.currentIcon == ICON_CANCEL_FILL ||
                            this@MediaActionDrawable.currentIcon == ICON_CANCEL)) && alpha != 0
            ) {
                val rad = max(4f, 360 * animatedDownloadProgress)
                val diff = AndroidUtilities.dp((if (isMini) 2 else 4).toFloat())
                rect.set(
                    (bounds.left + diff).toFloat(),
                    (bounds.top + diff).toFloat(),
                    (bounds.right - diff).toFloat(),
                    (bounds.bottom - diff).toFloat(),
                )

                if (
                    previousIcon == ICON_CANCEL_FILL ||
                        previousIcon == ICON_NONE &&
                            this@MediaActionDrawable.currentIcon == ICON_CANCEL_FILL
                ) {
                    paint.alpha = (alpha.toFloat() * 0.15f * overrideAlpha).toInt()
                    canvas.drawArc(rect, 0f, 360f, false, paint)
                    paint.alpha = alpha
                }
                canvas.drawArc(rect, downloadRadOffset, rad, false, paint)
            }
        } else if (
            previousIcon == ICON_EMPTY ||
                this@MediaActionDrawable.currentIcon == ICON_EMPTY ||
                previousIcon == ICON_CANCEL_PERCENT
        ) {
            var alpha: Int
            if (
                this@MediaActionDrawable.currentIcon == ICON_NONE ||
                    this@MediaActionDrawable.currentIcon == ICON_CHECK
            ) {
                val progress = transitionProgress
                val backProgress = 1.0f - progress
                alpha = (255 * backProgress).toInt()
            } else {
                alpha = 255
            }

            if (alpha != 0) {
                applyShaderMatrix(false)
                paint.alpha = (alpha * overrideAlpha).toInt()
                val rad = max(4f, 360 * animatedDownloadProgress)
                val diff = AndroidUtilities.dp((if (isMini) 2 else 4).toFloat())
                rect.set(
                    (bounds.left + diff).toFloat(),
                    (bounds.top + diff).toFloat(),
                    (bounds.right - diff).toFloat(),
                    (bounds.bottom - diff).toFloat(),
                )
                canvas.drawArc(rect, downloadRadOffset, rad, false, paint)
            }
        }

        var nextDrawable: Drawable? = null
        var previousDrawable: Drawable? = null
        var nextPath: Array<Path>? = null
        var previousPath: Array<Path>? = null

        var drawableScale: Float
        var previowsDrawableScale: Float
        when (previousIcon) {
            this@MediaActionDrawable.currentIcon -> {
                previowsDrawableScale = 1.0f
                drawableScale = previowsDrawableScale
            }
            ICON_NONE,
            ICON_CANCEL,
            ICON_CANCEL_FILL -> {
                drawableScale = transitionProgress
                previowsDrawableScale = 1.0f - transitionProgress
            }
            else -> {
                drawableScale = min(1.0f, transitionProgress / 0.5f)
                previowsDrawableScale = max(0.0f, 1.0f - transitionProgress / 0.5f)
            }
        }

        if (this@MediaActionDrawable.currentIcon == ICON_UPDATE) {
            nextPath = Theme.chat_updatePath
        } else if (previousIcon == ICON_UPDATE) {
            previousPath = Theme.chat_updatePath
        }
        if (this@MediaActionDrawable.currentIcon == ICON_FILE) {
            nextPath = Theme.chat_filePath
        } else if (previousIcon == ICON_FILE) {
            previousPath = Theme.chat_filePath
        }
        if (this@MediaActionDrawable.currentIcon == ICON_FIRE) {
            nextDrawable = Theme.chat_flameIcon
        } else if (previousIcon == ICON_FIRE) {
            previousDrawable = Theme.chat_flameIcon
        }
        if (this@MediaActionDrawable.currentIcon == ICON_GIF) {
            nextDrawable = Theme.chat_gifIcon
        } else if (previousIcon == ICON_GIF) {
            previousDrawable = Theme.chat_gifIcon
        }

        if (
            previousIcon == ICON_SECRETCHECK ||
                this@MediaActionDrawable.currentIcon == ICON_SECRETCHECK
        ) {
            applyShaderMatrix(false)
            paint.alpha =
                if (previousIcon == this@MediaActionDrawable.currentIcon) 255
                else (transitionProgress * 255).toInt()
            val y = cy + AndroidUtilities.dp(7f)
            val x = cx - AndroidUtilities.dp(3f)
            if (previousIcon != this@MediaActionDrawable.currentIcon) {
                canvas.save()
                canvas.scale(transitionProgress, transitionProgress, cx.toFloat(), cy.toFloat())
            }
            canvas.drawLine(
                (x - AndroidUtilities.dp(6f)).toFloat(),
                (y - AndroidUtilities.dp(6f)).toFloat(),
                x.toFloat(),
                y.toFloat(),
                paint,
            )
            canvas.drawLine(
                x.toFloat(),
                y.toFloat(),
                (x + AndroidUtilities.dp(12f)).toFloat(),
                (y - AndroidUtilities.dp(12f)).toFloat(),
                paint,
            )
            if (previousIcon != this@MediaActionDrawable.currentIcon) {
                canvas.restore()
            }
        }
        if (
            previousIcon == ICON_CANCEL_NOPROFRESS ||
                this@MediaActionDrawable.currentIcon == ICON_CANCEL_NOPROFRESS
        ) {
            applyShaderMatrix(false)
            val transition =
                if (previousIcon == this@MediaActionDrawable.currentIcon) {
                    1.0f
                } else if (this@MediaActionDrawable.currentIcon == ICON_CANCEL_PERCENT) {
                    transitionProgress
                } else {
                    1.0f - transitionProgress
                }

            paint.alpha =
                if (previousIcon == this@MediaActionDrawable.currentIcon) 255
                else (transition * 255).toInt()
            if (previousIcon != this@MediaActionDrawable.currentIcon) {
                canvas.save()
                canvas.scale(transition, transition, cx.toFloat(), cy.toFloat())
            }

            val d = AndroidUtilities.dp(7f) * scale
            canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint)
            canvas.drawLine(cx + d, cy - d, cx - d, cy + d, paint)

            if (previousIcon != this@MediaActionDrawable.currentIcon) {
                canvas.restore()
            }
        }
        if (
            previousIcon == ICON_CANCEL_PERCENT ||
                this@MediaActionDrawable.currentIcon == ICON_CANCEL_PERCENT
        ) {
            applyShaderMatrix(false)
            val transition =
                if (previousIcon == this@MediaActionDrawable.currentIcon) {
                    1.0f
                } else if (this@MediaActionDrawable.currentIcon == ICON_CANCEL_PERCENT) {
                    transitionProgress
                } else {
                    1.0f - transitionProgress
                }

            textPaint.alpha = (transition * 255).toInt()
            val y = cy + AndroidUtilities.dp(5f)
            val x = cx - percentStringWidth / 2
            if (previousIcon != this@MediaActionDrawable.currentIcon) {
                canvas.save()
                canvas.scale(transition, transition, cx.toFloat(), cy.toFloat())
            }
            val newPercent = (animatedDownloadProgress * 100).toInt()
            if (percentString == null || newPercent != lastPercent) {
                lastPercent = newPercent
                percentString = String.format(Locale.getDefault(), "%d%%", lastPercent)
                percentStringWidth = ceil(textPaint.measureText(percentString).toDouble()).toInt()
            }
            canvas.drawText(percentString!!, x.toFloat(), y.toFloat(), textPaint)
            if (previousIcon != this@MediaActionDrawable.currentIcon) {
                canvas.restore()
            }
        }
        if (
            previousIcon == ICON_PLAY ||
                previousIcon == ICON_PAUSE ||
                this@MediaActionDrawable.currentIcon == ICON_PLAY ||
                this@MediaActionDrawable.currentIcon == ICON_PAUSE
        ) {
            val p =
                if (
                    previousIcon == ICON_PLAY && currentIcon == ICON_PAUSE ||
                        previousIcon == ICON_PAUSE && currentIcon == ICON_PLAY
                ) {
                    if (animatingTransition) {
                        if (currentIcon == ICON_PLAY) 1.0f - transitionProgress
                        else transitionProgress
                    } else if (currentIcon == ICON_PAUSE) 1.0f else 0.0f
                } else if (previousIcon == ICON_PAUSE) 1.0f else 0.0f

            if (
                this@MediaActionDrawable.currentIcon != ICON_PLAY &&
                    this@MediaActionDrawable.currentIcon != ICON_PAUSE ||
                    previousIcon != ICON_PLAY && previousIcon != ICON_PAUSE
            ) {
                if (this@MediaActionDrawable.currentIcon == ICON_NONE) {
                    paint2.alpha = (255 * (1.0f - transitionProgress)).toInt()
                } else {
                    paint2.alpha =
                        if (previousIcon == this@MediaActionDrawable.currentIcon) 255
                        else (transitionProgress * 255).toInt()
                }
            } else {
                paint2.alpha = 255
            }

            applyShaderMatrix(true)

            canvas.withTranslation(
                bounds.centerX() + AndroidUtilities.dp(1f) * (1.0f - p),
                bounds.centerY().toFloat(),
            ) {
                var ms = 500.0f * p
                var rotation = (if (previousIcon == ICON_PAUSE) 90 else 0).toFloat()
                if (
                    previousIcon == ICON_PLAY && this@MediaActionDrawable.currentIcon == ICON_PAUSE
                ) {
                    rotation =
                        if (ms < 384) {
                            95 * CubicBezierInterpolator.EASE_BOTH.getInterpolation(ms / 384)
                        } else if (ms < 484) {
                            95 -
                                5 *
                                    CubicBezierInterpolator.EASE_BOTH.getInterpolation(
                                        (ms - 384) / 100.0f
                                    )
                        } else {
                            90f
                        }
                    ms += 100f
                } else if (
                    previousIcon == ICON_PAUSE && this@MediaActionDrawable.currentIcon == ICON_PLAY
                ) {
                    rotation =
                        if (ms < 100) {
                            -5 * CubicBezierInterpolator.EASE_BOTH.getInterpolation(ms / 100.0f)
                        } else if (ms < 484) {
                            -5 +
                                95 *
                                    CubicBezierInterpolator.EASE_BOTH.getInterpolation(
                                        (ms - 100) / 384
                                    )
                        } else {
                            90f
                        }
                }
                canvas.rotate(rotation)
                if (previousIcon != ICON_PLAY && previousIcon != ICON_PAUSE) {
                    canvas.scale(drawableScale, drawableScale)
                }
                Theme.playPauseAnimator.draw(canvas, paint2, ms)
                canvas.scale(1.0f, -1.0f)
                Theme.playPauseAnimator.draw(canvas, paint2, ms)
            }
        }
        if (previousIcon == ICON_CHECK || this@MediaActionDrawable.currentIcon == ICON_CHECK) {
            applyShaderMatrix(false)
            var progress1: Float
            var progress2: Float
            if (previousIcon != ICON_CHECK) {
                if (transitionProgress > CANCEL_TO_CHECK_STAGE1) {
                    val progress =
                        (transitionProgress - CANCEL_TO_CHECK_STAGE1) / CANCEL_TO_CHECK_STAGE2
                    progress1 = 1.0f - min(1.0f, progress / 0.5f)
                    progress2 = if (progress > 0.5f) ((progress - 0.5f) / 0.5f) else 0.0f
                } else {
                    progress1 = 1.0f
                    progress2 = 0.0f
                }
                paint.alpha = 255
            } else {
                progress1 = 0.0f
                progress2 = 1.0f
                if (this@MediaActionDrawable.currentIcon != ICON_CHECK) {
                    paint.alpha = (255 * (1.0f - transitionProgress)).toInt()
                } else {
                    paint.alpha = 255
                }
            }
            val y = cy + AndroidUtilities.dp(7f)
            val x = cx - AndroidUtilities.dp(3f)
            if (progress1 < 1) {
                canvas.drawLine(
                    (x - AndroidUtilities.dp(6f)).toFloat(),
                    (y - AndroidUtilities.dp(6f)).toFloat(),
                    x - AndroidUtilities.dp(6f) * progress1,
                    y - AndroidUtilities.dp(6f) * progress1,
                    paint,
                )
            }
            if (progress2 > 0) {
                canvas.drawLine(
                    x.toFloat(),
                    y.toFloat(),
                    x + AndroidUtilities.dp(12f) * progress2,
                    y - AndroidUtilities.dp(12f) * progress2,
                    paint,
                )
            }
        }

        if (previousDrawable != null && previousDrawable !== nextDrawable) {
            val w = (previousDrawable.intrinsicWidth * previowsDrawableScale).toInt()
            val h = (previousDrawable.intrinsicHeight * previowsDrawableScale).toInt()
            previousDrawable.colorFilter = colorFilter
            previousDrawable.alpha =
                if (previousIcon == this@MediaActionDrawable.currentIcon) 255
                else ((1.0f - transitionProgress) * 255).toInt()
            previousDrawable.setBounds(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            previousDrawable.draw(canvas)
        }
        if (nextDrawable != null) {
            val w = (nextDrawable.intrinsicWidth * drawableScale).toInt()
            val h = (nextDrawable.intrinsicHeight * drawableScale).toInt()
            nextDrawable.colorFilter = colorFilter
            nextDrawable.alpha =
                if (previousIcon == this@MediaActionDrawable.currentIcon) 255
                else (transitionProgress * 255).toInt()
            nextDrawable.setBounds(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            nextDrawable.draw(canvas)
        }

        if (previousPath != null && previousPath !== nextPath) {
            val size = AndroidUtilities.dp(24f)
            paint2.style = Paint.Style.FILL_AND_STROKE
            paint2.alpha =
                if (previousIcon == this@MediaActionDrawable.currentIcon) 255
                else ((1.0f - transitionProgress) * 255).toInt()
            applyShaderMatrix(true)
            canvas.withTranslation(cx.toFloat(), cy.toFloat()) {
                canvas.scale(previowsDrawableScale, previowsDrawableScale)
                canvas.translate((-size / 2).toFloat(), (-size / 2).toFloat())
                canvas.drawPath(previousPath[0], paint2)
                canvas.drawPath(previousPath[1], backPaint)
            }
        }
        if (nextPath != null) {
            val size = AndroidUtilities.dp(24f)
            val alpha =
                if (previousIcon == this@MediaActionDrawable.currentIcon) 255
                else (transitionProgress * 255).toInt()
            paint2.style = Paint.Style.FILL_AND_STROKE
            paint2.alpha = alpha
            applyShaderMatrix(true)
            canvas.withTranslation(cx.toFloat(), cy.toFloat()) {
                canvas.scale(drawableScale, drawableScale)
                canvas.translate((-size / 2).toFloat(), (-size / 2).toFloat())
                canvas.drawPath(nextPath[0], paint2)
                if (nextPath.size >= 3) {
                    canvas.drawPath(nextPath[2], paint)
                }
                if (alpha != 255) {
                    val backgroundAlpha = backPaint.alpha
                    backPaint.alpha = (backgroundAlpha * (alpha / 255f)).toInt()
                    canvas.drawPath(nextPath[1], backPaint)
                    backPaint.alpha = backgroundAlpha
                } else {
                    canvas.drawPath(nextPath[1], backPaint)
                }
            }
        }

        val newTime = System.currentTimeMillis()
        var dt = newTime - lastAnimationTime
        if (dt > 17) {
            dt = 17
        }
        lastAnimationTime = newTime

        if (
            previousIcon == ICON_CANCEL ||
                previousIcon == ICON_CANCEL_FILL ||
                previousIcon == ICON_NONE &&
                    this@MediaActionDrawable.currentIcon == ICON_CANCEL_FILL ||
                previousIcon == ICON_EMPTY ||
                previousIcon == ICON_CANCEL_PERCENT
        ) {
            downloadRadOffset += 360 * dt / 2500.0f
            downloadRadOffset = getCircleValue(downloadRadOffset)

            if (this@MediaActionDrawable.currentIcon != ICON_DOWNLOAD) {
                val progressDiff = progress - downloadProgressAnimationStart
                if (progressDiff > 0) {
                    downloadProgressTime += dt.toFloat()
                    if (downloadProgressTime >= 200.0f) {
                        animatedDownloadProgress = progress
                        downloadProgressAnimationStart = progress
                        downloadProgressTime = 0f
                    } else {
                        animatedDownloadProgress =
                            downloadProgressAnimationStart +
                                progressDiff *
                                    interpolator.getInterpolation(downloadProgressTime / 200.0f)
                    }
                }
            }
            invalidateSelf()
        }

        if (animatingTransition) {
            if (transitionProgress < 1.0f) {
                transitionProgress += dt / transitionAnimationTime
                if (transitionProgress >= 1.0f) {
                    previousIcon = this@MediaActionDrawable.currentIcon
                    transitionProgress = 1.0f
                    animatingTransition = false
                }
                invalidateSelf()
            }
        }
        if (saveCount >= 1) {
            canvas.restoreToCount(saveCount)
        }
    }

    override fun getIntrinsicWidth(): Int {
        return AndroidUtilities.dp(48f)
    }

    override fun getIntrinsicHeight(): Int {
        return AndroidUtilities.dp(48f)
    }

    override fun getMinimumWidth(): Int {
        return AndroidUtilities.dp(48f)
    }

    override fun getMinimumHeight(): Int {
        return AndroidUtilities.dp(48f)
    }

    companion object {

        const val ICON_PLAY: Int = 0
        const val ICON_PAUSE: Int = 1
        const val ICON_DOWNLOAD: Int = 2
        const val ICON_CANCEL: Int = 3
        const val ICON_NONE: Int = 4
        const val ICON_FILE: Int = 5
        const val ICON_CHECK: Int = 6
        const val ICON_FIRE: Int = 7
        const val ICON_GIF: Int = 8
        const val ICON_SECRETCHECK: Int = 9
        const val ICON_EMPTY: Int = 10
        const val ICON_CANCEL_NOPROFRESS: Int = 12
        const val ICON_CANCEL_PERCENT: Int = 13
        const val ICON_CANCEL_FILL: Int = 14
        const val ICON_UPDATE: Int = 15

        private const val DOWNLOAD_TO_CANCEL_STAGE1: Float = 0.5f
        private const val DOWNLOAD_TO_CANCEL_STAGE2: Float = 0.2f
        private const val DOWNLOAD_TO_CANCEL_STAGE3: Float = 0.3f

        private const val CANCEL_TO_CHECK_STAGE1: Float = 0.5f
        private const val CANCEL_TO_CHECK_STAGE2: Float = 0.5f

        fun getCircleValue(value: Float): Float {
            var value = value
            while (value > 360) {
                value -= 360f
            }
            return value
        }
    }
}
