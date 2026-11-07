// Nagram/Telegram GPL-2.0-or-later; original paths and animation equations.
package steam.nova.app.features.chat.media.viewer

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.Gravity
import androidx.core.content.edit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.dp

open class SeekSpeedDrawable(
    private val context: android.content.Context,
    private val invalidate: Runnable?,
    private val isPiP: Boolean,
    isRound: Boolean,
) : Drawable() {
    private val backgroundPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var hintDrawable: com.airbnb.lottie.LottieDrawable? = null
    private val preferences =
        context.getSharedPreferences("steamnova-media", android.content.Context.MODE_PRIVATE)
    private val hintArrow: Path = Path()
    private val hintText: Text =
        Text(
            steam.nova.app.core.ModuleLocalization.text(
                context,
                steam.nova.app.R.string.chat_media_seek_hint,
            ),
            14f,
        )

    private val leftArrow: Path = Path()
    private val rightArrow: Path = Path()

    private var shown: Boolean = false
    private var direction: Int = +1
    private val animatedShown =
        AnimatedFloat(invalidate, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT)
    private val animatedDirection =
        AnimatedFloat(invalidate, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT)
    private val animatedSpeed =
        AnimatedFloat(invalidate, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT)
    private val animatedHintShown =
        AnimatedFloat(invalidate, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT)

    private val speedText: AnimatedTextView.AnimatedTextDrawable?

    private var showHint: Boolean =
        !isPiP && !isRound && !preferences.getBoolean("seekSpeedHintShowed", false)

    open val isShown: Boolean
        get() {
            return shown || animatedShown.currentValue > 0
        }

    private val speedRect: RectF = RectF()
    private val hintRect: RectF = RectF()

    private var t: Float = 0f
    private var lastFrameTime: Long = 0

    private var lastSpeed: Float = 0f

    private var hideHintScheduled: Boolean = false
    private val hideHintRunnable: Runnable = Runnable {
        showHint = false
        this.invalidate!!.run()
    }

    init {

        animatedShown.set(mustBe = false, force = true)
        animatedHintShown.set(mustBe = false, force = true)

        speedText =
            object : AnimatedTextView.AnimatedTextDrawable(false, true, true, true) {
                override fun invalidateSelf() {
                    invalidate!!.run()
                }
            }
        speedText.setScaleProperty(.3f)
        speedText.setAnimationProperties(.4f, 0, 650, 1.6f, CubicBezierInterpolator.EASE_OUT_QUINT)
        speedText.setTypeface(
            steam.nova.app.features.chat.media.SteamChatMediaResources.numberTypeface(context)
        )
        speedText.textSize = dp(16f).toFloat()
        setSpeed(2.0f, false)
        speedText.textColor = -0x1
        speedText.gravity = Gravity.CENTER

        arrowPaint.pathEffect = CornerPathEffect(dp(1.66f).toFloat())

        leftArrow.moveTo(dp(8.66f).toFloat(), (-dp(12.66f / 2.0f)).toFloat())
        leftArrow.lineTo(0f, 0f)
        leftArrow.lineTo(dp(8.66f).toFloat(), dp(12.66f / 2.0f).toFloat())
        leftArrow.close()

        rightArrow.moveTo(0f, (-dp(12.66f / 2.0f)).toFloat())
        rightArrow.lineTo(dp(8.66f).toFloat(), 0f)
        rightArrow.lineTo(0f, dp(12.66f / 2.0f).toFloat())
        rightArrow.close()

        hintArrow.moveTo((-dp(6.5f)).toFloat(), 0f)
        hintArrow.lineTo(0f, (-dp(6.33f)).toFloat())
        hintArrow.lineTo(dp(6.5f).toFloat(), 0f)
        hintArrow.close()
    }

    override fun draw(canvas: Canvas) {
        val bounds = getBounds()
        val speedRectWidth = speedText!!.currentWidth + dp((9 + 8 + 20 + 9).toFloat())

        val shown = animatedShown.set(this.shown)
        val direction = animatedDirection.set(this.direction.toFloat())
        if (shown <= 0.0f) return
        val speed = animatedSpeed.set(abs(this.lastSpeed))

        val now = System.currentTimeMillis()
        val deltaTime = min(.016f, (now - lastFrameTime) / 1000.0f)
        lastFrameTime = now
        t += deltaTime * (1.5f * min(speed, 4.0f))
        invalidate!!.run()

        speedRect.set(
            bounds.centerX() - speedRectWidth / 2f,
            (bounds.top + dp(9f)).toFloat(),
            bounds.centerX() + speedRectWidth / 2f,
            (bounds.top + dp((9 + 28).toFloat())).toFloat(),
        )
        canvas.save()
        var scale = .6f + .4f * shown
        if (bounds.width() < AndroidUtilities.displaySize.x * .7f) {
            scale *= .75f
            if (isPiP) {
                canvas.translate((-dp(45f)).toFloat(), 0f)
            }
        }
        canvas.scale(scale, scale, speedRect.centerX(), speedRect.top)
        canvas.translate(0f, -dp(15f) * (1.0f - shown))
        canvas.clipRect(speedRect)

        backgroundPaint.color = Theme.multAlpha(-0x1000000, 0.4f * shown)
        canvas.drawRoundRect(
            speedRect,
            speedRect.height() / 2f,
            speedRect.height() / 2f,
            backgroundPaint,
        )
        speedText.setBounds(speedRect)

        canvas.save()
        canvas.translate(
            speedRect.centerX() - speedRectWidth / 2.0f + dp(9f) -
                dp(30f) * (1.0f - max(0f, -direction)),
            speedRect.centerY(),
        )
        var p = (sin((t) * PI).toFloat() / 2.0f + 1.0f)
        arrowPaint.color = Theme.multAlpha(-0x1, max(0f, -direction) * shown * (.2f + .75f * p))
        canvas.drawPath(leftArrow, arrowPaint)
        canvas.translate(dp(10.66f).toFloat(), 0f)
        p = (sin((t + .17f) * PI).toFloat() / 2.0f + 1.0f)
        arrowPaint.color = Theme.multAlpha(-0x1, max(0f, -direction) * shown * (.2f + .75f * p))
        canvas.drawPath(leftArrow, arrowPaint)
        canvas.restore()

        canvas.save()
        canvas.translate(-dp((20 + 8).toFloat()) / 2.0f * direction, 0.0f)
        speedText.alpha = (0xFF * shown).toInt()
        speedText.draw(canvas)
        canvas.restore()

        canvas.save()
        canvas.translate(
            speedRect.centerX() + speedRectWidth / 2.0f - dp(30f) +
                dp(30f) * (1.0f - max(0f, direction)),
            speedRect.centerY(),
        )
        p = (sin((t) * PI).toFloat() / 2.0f + 1.0f)
        arrowPaint.color = Theme.multAlpha(-0x1, max(0f, direction) * shown * (.2f + .75f * p))
        canvas.drawPath(rightArrow, arrowPaint)
        canvas.translate(dp(10.66f).toFloat(), 0f)
        p = (sin((t - .17f) * PI).toFloat() / 2.0f + 1.0f)
        arrowPaint.color = Theme.multAlpha(-0x1, max(0f, direction) * shown * (.2f + .75f * p))
        canvas.drawPath(rightArrow, arrowPaint)
        canvas.restore()

        canvas.restore()

        val hintShown = animatedHintShown.set(this.showHint && this.shown)
        if (hintShown > 0) {
            if (hintDrawable == null) {
                hintDrawable = com.airbnb.lottie.LottieDrawable()
                try {
                    steam.nova.app.core.ModuleLocalization.resources(
                            context,
                            context.resources!!.configuration,
                            null,
                        )
                        .openRawResource(steam.nova.app.R.raw.chat_media_seek_speed_hint)
                        .use { input ->
                            val composition =
                                com.airbnb.lottie.LottieCompositionFactory.fromJsonInputStreamSync(
                                        input,
                                        "steamnova-seek-speed-hint",
                                    )
                                    .getValue()
                            if (composition != null) hintDrawable!!.setComposition(composition)
                        }
                } catch (error: java.io.IOException) {
                    FileLog.e(error)
                }

                hintDrawable!!.callback =
                    object : Callback {
                        override fun invalidateDrawable(who: Drawable) {
                            invalidate.run()
                        }

                        override fun scheduleDrawable(
                            who: Drawable,
                            what: Runnable,
                            `when`: Long,
                        ) {}

                        override fun unscheduleDrawable(
                            who: Drawable,
                            what: Runnable,
                        ) {}
                    }
                hintDrawable!!.repeatCount = com.airbnb.lottie.LottieDrawable.INFINITE
                hintDrawable!!.start()
            }
            val hintW = hintText.currentWidth + dp((22 + 24 + 8).toFloat())
            val hintH = dp(32f).toFloat()
            hintRect.set(
                bounds.centerX() - hintW / 2.0f,
                speedRect.top + speedRect.height() * shown + dp(11f).toFloat(),
                bounds.centerX() + hintW / 2.0f,
                speedRect.top + speedRect.height() * shown + dp(11f).toFloat() + hintH,
            )

            canvas.save()
            val hintScale = .75f + .25f * hintShown
            canvas.scale(hintScale, hintScale, hintRect.centerX(), hintRect.top)
            backgroundPaint.color = Theme.multAlpha(-0x1000000, 0.4f * hintShown)

            canvas.save()
            canvas.translate(hintRect.centerX(), hintRect.top)
            canvas.drawPath(hintArrow, backgroundPaint)
            canvas.restore()
            canvas.drawRoundRect(hintRect, dp(8f).toFloat(), dp(8f).toFloat(), backgroundPaint)

            hintDrawable!!.setBounds(
                hintRect.left.toInt() + dp(11f),
                hintRect.centerY().toInt() - dp(24f) / 2,
                hintRect.left.toInt() + dp((11 + 24).toFloat()),
                hintRect.centerY().toInt() + dp(24f) / 2,
            )
            hintDrawable!!.setAlpha((0xFF * hintShown).toInt())
            if (!hintDrawable!!.isRunning) {
                hintDrawable!!.playAnimation()
            }
            hintDrawable!!.draw(canvas)

            hintText.draw(
                canvas,
                hintRect.left + dp((11 + 24 + 4).toFloat()),
                hintRect.centerY(),
                -0x1,
                hintShown,
            )

            canvas.restore()
        }
    }

    open fun setShown(shown: Boolean, animated: Boolean) {
        this.shown = shown
        if (!animated) {
            animatedShown.set(shown, true)
        }
        invalidate!!.run()

        if (hintDrawable != null && showHint) {
            if (shown) {
                hintDrawable!!.playAnimation()
            } else {
                hintDrawable!!.stop()
            }
        }
    }

    open fun setSpeed(speed: Float, animated: Boolean) {
        if (floor((lastSpeed * 10).toDouble()) != floor((speed * 10).toDouble())) {
            speedText!!.cancelAnimation()
            speedText.setText(String.format(Locale.US, "%.1fx", abs(speed)), animated)
            lastSpeed = speed
        }
        direction = if (speed > 0) +1 else -1
        if (!animated) {
            animatedDirection.set(direction.toFloat(), true)
        }
        invalidate!!.run()

        if (showHint && abs(speed) > 3.0f && !hideHintScheduled) {
            hideHintScheduled = true
            AndroidUtilities.runOnUIThread(hideHintRunnable, 2500)
            preferences.edit { putBoolean("seekSpeedHintShowed", true) }
        }
    }

    open fun close() {
        AndroidUtilities.cancelRunOnUIThread(hideHintRunnable)
        if (hintDrawable != null) {
            hintDrawable!!.cancelAnimation()
            hintDrawable!!.callback = null
        }
        speedText!!.cancelAnimation()
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Required by Android Drawable")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSPARENT
    }
}
