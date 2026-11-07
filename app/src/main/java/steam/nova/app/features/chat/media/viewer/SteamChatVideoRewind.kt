package steam.nova.app.features.chat.media.viewer

/** Dispatches PhotoViewer's >180s and ordinary-video paths to the copied upstream rewinders. */
internal class SteamChatVideoRewind(private val host: Player) {
    interface Player {
        fun position(): Long

        fun duration(): Long

        fun playing(): Boolean

        fun seek(ms: Long)

        fun speed(value: Float)

        fun muted(value: Boolean)

        fun paused(value: Boolean)

        fun feedback(speed: Float, difference: Long, old: Boolean)

        fun ended()
    }

    private var muted = false
    private var old = false
    private var oldForward = false
    private var start = 0L
    val active
        get() = modern.rewinding || legacy.rewindCount > 0

    private val adapter =
        object : VideoPlayer {
            override val duration
                get() = host.duration()

            override val currentPosition
                get() = host.position()

            override val isPlaying
                get() = host.playing()

            override val isMuted
                get() = muted

            override fun setMute(muted: Boolean) {
                this@SteamChatVideoRewind.muted = muted
                host.muted(muted)
            }

            override fun setPlaybackSpeed(speed: Float) = host.speed(speed)

            override fun seekTo(position: Long) = host.seek(position)

            override fun play() = host.paused(false)

            override fun pause() = host.paused(true)
        }
    private val modern =
        object : VideoPlayerRewinder() {
            override fun onRewindCanceled() {
                host.ended()
            }
        }
    private val legacy =
        object : OldVideoPlayerRewinder() {
            override fun updateRewindProgressUi(
                timeDiff: Long,
                progress: Float,
                rewindByBackSeek: Boolean,
            ) {
                host.feedback(if (oldForward) 1f else -1f, timeDiff, true)
            }

            override fun onRewindCanceled() {
                host.ended()
            }
        }
    private val feedback =
        object : VideoPlayerRewinder.SpeedFeedback {
            override fun setSpeed(speed: Float, animated: Boolean) {
                host.feedback(speed, host.position() - start, false)
            }

            override fun setShown(shown: Boolean, animated: Boolean) {
                if (!shown) host.ended()
            }
        }

    fun start(x: Float, width: Int, longVideo: Boolean) {
        stop()
        old = longVideo
        start = host.position()
        if (old) {
            if (x in width / 3f..width / 3f * 2) return
            oldForward = x >= width / 3f * 2
            legacy.startRewind(adapter, oldForward, 1f)
        } else modern.startRewind(adapter, x > width / 3f, x, 1f, feedback)
    }

    fun move(x: Float) {
        if (!old && modern.rewinding) modern.setX(x)
    }

    fun stop() {
        if (modern.rewinding) modern.cancelRewind()
        if (legacy.rewindCount > 0) legacy.cancelRewind()
    }
}
