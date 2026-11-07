// Nagram/Telegram GPL-2.0-or-later; original rewind algorithms, Steam player interface adapter.
package steam.nova.app.features.chat.media.viewer

open class OldVideoPlayerRewinder {

    var rewindCount: Int = 0
    private var rewindForward: Boolean = false
    var rewindByBackSeek: Boolean = false
    private var startRewindFrom: Long = 0
    private var updateRewindRunnable: Runnable? = null
    private var rewindLastTime: Long = 0
    private var rewindLastUpdatePlayerTime: Long = 0
    private var rewindBackSeekPlayerPosition: Long = 0
    private var playSpeed: Float = 1f

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

                val t = System.currentTimeMillis()
                var dt = t - rewindLastTime
                rewindLastTime = t
                dt *=
                    when (rewindCount) {
                        1 -> 3
                        2 -> 6
                        else -> 12
                    }
                if (rewindForward) {
                    rewindBackSeekPlayerPosition += dt
                } else {
                    rewindBackSeekPlayerPosition -= dt
                }
                if (rewindBackSeekPlayerPosition < 0) {
                    rewindBackSeekPlayerPosition = 0
                } else if (rewindBackSeekPlayerPosition > duration) {
                    rewindBackSeekPlayerPosition = duration
                }
                if (rewindByBackSeek && rewindLastTime - rewindLastUpdatePlayerTime > 350) {
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
                if (rewindCount > 0) {
                    AndroidUtilities.runOnUIThread(this, 16)
                }
            }
        }

    private val currentPosition: Long
        get() {
            return if (videoPlayer == null) 0 else videoPlayer!!.currentPosition
        }

    private val duration: Long
        get() {
            return if (videoPlayer == null) 0 else videoPlayer!!.duration
        }

    private val isPlaying: Boolean
        get() {
            return videoPlayer != null && videoPlayer!!.isPlaying
        }

    open fun startRewind(videoPlayer: VideoPlayer?, forward: Boolean, playbackSpeed: Float) {
        this.videoPlayer = videoPlayer
        this.playSpeed = playbackSpeed
        rewindForward = forward
        cancelRewind()
        incrementRewindCount()
    }

    open fun cancelRewind() {
        if (rewindCount != 0) {
            rewindCount = 0

            if (videoPlayer != null) {
                if (rewindByBackSeek) {
                    seekTo(rewindBackSeekPlayerPosition)
                } else {
                    val current = currentPosition
                    seekTo(current)
                }
                setPlaybackSpeed(playSpeed)
            }
        }
        AndroidUtilities.cancelRunOnUIThread(backSeek)

        if (updateRewindRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(updateRewindRunnable!!)
            updateRewindRunnable = null
        }

        onRewindCanceled()
    }

    private fun incrementRewindCount() {
        if (videoPlayer == null) {
            return
        }
        rewindCount++
        var needUpdate = false
        if (rewindCount == 1) {
            rewindByBackSeek = !(rewindForward && isPlaying)
        }
        if (rewindForward && !rewindByBackSeek) {
            when (rewindCount) {
                1 -> {
                    setPlaybackSpeed(4f)
                    needUpdate = true
                }
                2 -> {
                    setPlaybackSpeed(7f)
                    needUpdate = true
                }
                else -> {
                    setPlaybackSpeed(13f)
                }
            }
        } else {
            if (rewindCount == 1 || rewindCount == 2) {
                needUpdate = true
            }
        }

        if (rewindCount == 1) {
            rewindBackSeekPlayerPosition = currentPosition
            rewindLastTime = System.currentTimeMillis()
            rewindLastUpdatePlayerTime = rewindLastTime
            startRewindFrom = currentPosition
            onRewindStart(rewindForward)
        }

        AndroidUtilities.cancelRunOnUIThread(backSeek)
        AndroidUtilities.runOnUIThread(backSeek)

        if (needUpdate) {
            if (updateRewindRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(updateRewindRunnable!!)
            }
            updateRewindRunnable = Runnable {
                updateRewindRunnable = null
                incrementRewindCount()
            }
            AndroidUtilities.runOnUIThread(updateRewindRunnable!!, 2000)
        }
    }

    protected open fun updateRewindProgressUi(
        timeDiff: Long,
        progress: Float,
        rewindByBackSeek: Boolean,
    ) {}

    protected open fun onRewindStart(rewindForward: Boolean) {}

    protected open fun onRewindCanceled() {}

    private fun seekTo(position: Long) {
        if (videoPlayer != null) videoPlayer!!.seekTo(position)
    }

    private fun setPlaybackSpeed(speed: Float) {
        if (videoPlayer != null) videoPlayer!!.setPlaybackSpeed(speed)
    }
}
