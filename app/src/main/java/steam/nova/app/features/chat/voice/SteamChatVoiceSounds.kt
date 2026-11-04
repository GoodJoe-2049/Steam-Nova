package steam.nova.app.features.chat.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.annotation.RawRes
import steam.nova.app.R
import steam.nova.app.core.ModuleLocalization
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics

/** Steam web's one-shot sounds and finite incoming-call reminder, owned by the account. Runs on main. */
internal class SteamChatVoiceSounds(
    private val context: Context,
    private val communicationActive: () -> Boolean,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val players = mutableSetOf<Playback>()
    private var incoming: Playback? = null
    private var closed = false
    private val reminder = Runnable { incoming = play(R.raw.steam_chat_voice_call, repeats = 1) }

    fun outgoing() { play(R.raw.steam_chat_voice_call) }
    fun entered() { play(R.raw.steam_chat_voice_enter) }
    fun exited() { play(R.raw.steam_chat_voice_exit) }

    fun startIncoming() {
        stopIncoming()
        if (closed) return
        // Web schedules one reminder from invitation receipt, not from playback completion.
        handler.postDelayed(reminder, 20_000L)
        incoming = play(R.raw.steam_chat_voice_call, repeats = 1)
    }

    fun stopIncoming() {
        handler.removeCallbacks(reminder)
        incoming?.stop()
        incoming = null
    }

    private fun play(@RawRes resource: Int, repeats: Int = 0): Playback? {
        if (closed) return null
        // An unavailable sound must not fail the call or alter signaling.
        return runCatching {
            Playback(resource, repeats).also { players.add(it); it.prepare() }
        }.onFailure { SteamChatDiagnostics.failure("voice.sound.create", it) }.getOrNull()
    }

    private inner class Playback(@param:RawRes private val resource: Int, private var repeats: Int) {
        private val player = MediaPlayer()
        private var stopped = false

        fun prepare() {
            runCatching {
                player.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(if (communicationActive()) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                // These IDs belong to the module APK, not the Steam host's resources.
                checkNotNull(ModuleLocalization.resources(context).openRawResourceFd(resource)).use { asset ->
                    player.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                }
                player.setOnPreparedListener { start() }
                player.setOnCompletionListener {
                    if (!stopped && repeats > 0) {
                        repeats--
                        // Starting a completed MediaPlayer replays it from the beginning.
                        start()
                    } else stop()
                }
                player.setOnErrorListener { _, what, extra ->
                    failed(IllegalStateException("MediaPlayer error $what/$extra"))
                    true
                }
                player.prepareAsync()
            }.onFailure(::failed)
        }

        private fun start() {
            if (!stopped) runCatching { player.start() }.onFailure(::failed)
        }

        private fun failed(error: Throwable) {
            SteamChatDiagnostics.failure("voice.sound.play", error)
            stop()
        }

        fun stop() {
            if (stopped) return
            stopped = true
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            runCatching { player.release() }
            players.remove(this)
        }
    }

    fun close() {
        closed = true
        stopIncoming()
        players.toList().forEach { it.stop() }
    }
}
