package steam.nova.app.features.chat.media.viewer

/** Only the player boundary is replaced; rewind scheduling remains Nagram's implementation. */
interface VideoPlayer {
    val duration: Long
    val currentPosition: Long
    val isPlaying: Boolean
    val isMuted: Boolean

    fun setMute(muted: Boolean)

    fun setPlaybackSpeed(speed: Float)

    fun seekTo(position: Long)

    fun play()

    fun pause()
}
