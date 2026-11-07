// Nagram/Telegram GPL-2.0-or-later; original rewind algorithms, Steam player interface adapter.
package steam.nova.app.features.chat.media.viewer

import kotlin.math.max
import kotlin.math.min
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.dp

open class VideoPlayerRewinder {

    private var fastSeeking: Boolean = false
    var rewindByBackSeek: Boolean = false
    private val startRewindFrom: Long = 0
    private var updateRewindRunnable: Runnable? = null
    private var rewindLastTime: Long = 0
    private var rewindLastUpdatePlayerTime: Long = 0
    private var rewindBackSeekLastPlayerPosition: Long = 0
    private var rewindBackSeekPlayerPosition: Long = -1
    private var playSpeed: Float = 1f
    private var wasMuted: Boolean = false
    private var wasPaused: Boolean = false

    private var value: Float = 0f

    private var videoPlayer: VideoPlayer? = null

    private val backSeek: Runnable =
        object : Runnable {
            override fun run() {
                if (videoPlayer == null) {
                    return
                }
                val duration = duration
                if (duration == 0L || duration == -9223372036854775807L) {
                    rewindLastTime = System.currentTimeMillis()
                    return
                }

                val now = System.currentTimeMillis()
                var dt = now - rewindLastTime
                rewindLastTime = now
                val speed = max(0f, -rewindSpeed * playSpeed)
                dt = (dt * speed).toLong()
                rewindBackSeekPlayerPosition -= dt
                rewindBackSeekPlayerPosition = max(0, min(duration, rewindBackSeekPlayerPosition))
                if (
                    rewindByBackSeek &&
                        currentPosition > rewindBackSeekPlayerPosition &&
                        rewindLastTime - rewindLastUpdatePlayerTime > 10
                ) {
                    rewindLastUpdatePlayerTime = rewindLastTime

                    seekTo(rewindBackSeekPlayerPosition)
                }

                val timeDiff = rewindBackSeekPlayerPosition - startRewindFrom
                val progress = rewindBackSeekPlayerPosition / duration.toFloat()
                updateRewindProgressUi(timeDiff, progress, rewindByBackSeek)

                if (
                    rewindBackSeekPlayerPosition == 0L || rewindBackSeekPlayerPosition >= duration
                ) {
                    if (rewindByBackSeek) {
                        rewindLastUpdatePlayerTime = rewindLastTime
                        seekTo(rewindBackSeekPlayerPosition)
                    }
                    cancelRewind()
                }
                if (rewinding && rewindSpeed < 0) {
                    AndroidUtilities.runOnUIThread(this, 16)
                }
            }
        }

    var rewinding: Boolean = false
    private var x: Float = 0f
    private var seekSpeedDrawable: SpeedFeedback? = null

    open //        v /= 2.0f;
    //        v = v * v * v;
    val rewindSpeed: Float
        get() {
            var v = value
            v = if (v < 0.4f) v - 1.9f else v
            return max(-6.0f, min(10.0f, v))
        }

    private val currentPosition: Long
        get() {
            return if (videoPlayer == null) 0 else videoPlayer!!.currentPosition
        }

    private val duration: Long
        get() {
            return if (videoPlayer == null) 0 else videoPlayer!!.duration
        }

    interface SpeedFeedback {
        fun setSpeed(speed: Float, animated: Boolean)

        fun setShown(shown: Boolean, animated: Boolean)
    }

    open fun startRewind(
        videoPlayer: VideoPlayer?,
        forward: Boolean,
        initialX: Float,
        playbackSpeed: Float,
        seekSpeedDrawable: SpeedFeedback?,
    ) {
        cancelRewind()
        this.videoPlayer = null

        rewindByBackSeek = forward
        rewinding = true
        rewindBackSeekPlayerPosition = -1
        this.videoPlayer = videoPlayer
        this.seekSpeedDrawable = seekSpeedDrawable
        this.playSpeed = playbackSpeed
        this.wasMuted = videoPlayer != null && videoPlayer.isMuted
        this.wasPaused = videoPlayer != null && !videoPlayer.isPlaying
        fastSeeking = false
        rewindLastUpdatePlayerTime = 0
        x = initialX
        value = if (forward) getValueBySpeed(2.0f) else getValueBySpeed(-2.0f)
        rewindBackSeekLastPlayerPosition = -100
        if (seekSpeedDrawable != null) {
            seekSpeedDrawable.setSpeed(rewindSpeed, false)
            seekSpeedDrawable.setShown(shown = true, animated = true)
        }
        updateRewindSpeed()
    }

    open fun getValueBySpeed(speed: Float): Float {
        var value = speed
        //        value = (float) Math.cbrt(value);
        //        value *= 2.0f;
        if (value < -1.5f) {
            value += 1.9f
        }
        return value
    }

    open fun updateRewindSpeed() {
        val rewindSpeed = rewindSpeed
        if (rewindSpeed < 0) {
            if (!rewindByBackSeek) {
                rewindByBackSeek = true
                rewindBackSeekPlayerPosition = currentPosition
                rewindLastTime = System.currentTimeMillis()
                AndroidUtilities.runOnUIThread(backSeek)
                setMuted(true)
                setPaused(true)
                setPlaybackSpeed(playSpeed)
            }
        } else {
            if (rewindByBackSeek) {
                rewindByBackSeek = false
                AndroidUtilities.cancelRunOnUIThread(backSeek)
                setMuted(wasMuted || wasPaused)
                setPaused(false)
            }
            setPlaybackSpeed(playSpeed * rewindSpeed)
        }
    }

    open fun setX(x: Float) {
        val diff = this.x - x
        value -= diff / dp(40f)
        this.x = x

        if (seekSpeedDrawable != null) {
            seekSpeedDrawable!!.setSpeed(rewindSpeed, true)
        }

        updateRewindSpeed()
    }

    open fun cancelRewind() {
        if (!rewinding) return

        rewinding = false
        fastSeeking = false
        if (videoPlayer != null) {
            if (rewindByBackSeek) {

                seekTo(rewindBackSeekPlayerPosition)
            } else {
                seekTo(currentPosition)
            }
            setPlaybackSpeed(playSpeed)
        }
        setMuted(wasMuted)
        setPaused(wasPaused)
        AndroidUtilities.cancelRunOnUIThread(backSeek)

        if (updateRewindRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(updateRewindRunnable!!)
            updateRewindRunnable = null
        }

        onRewindCanceled()

        if (seekSpeedDrawable != null) {
            seekSpeedDrawable!!.setShown(shown = false, animated = true)
        }
    }

    protected open fun updateRewindProgressUi(
        timeDiff: Long,
        progress: Float,
        rewindByBackSeek: Boolean,
    ) {}

    protected open fun onRewindCanceled() {}

    private fun seekTo(position: Long) {
        if (videoPlayer != null) videoPlayer!!.seekTo(position)
        rewindBackSeekLastPlayerPosition = position
    }

    private fun setMuted(muted: Boolean) {
        if (videoPlayer != null) {
            videoPlayer!!.setMute(muted)
        }
    }

    private fun setPaused(paused: Boolean) {
        if (videoPlayer != null) {
            if (paused) videoPlayer!!.pause() else videoPlayer!!.play()
        }
    }

    private fun setPlaybackSpeed(speed: Float) {
        if (videoPlayer != null) videoPlayer!!.setPlaybackSpeed(speed)
    }
}
