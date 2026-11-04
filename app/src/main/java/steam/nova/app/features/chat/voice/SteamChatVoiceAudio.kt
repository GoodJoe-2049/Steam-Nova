package steam.nova.app.features.chat.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Owns only the audio focus and routing acquired for this call. All methods run on main. */
@Suppress("DEPRECATION")
internal class SteamChatVoiceAudio(context: Context, private val interrupted: () -> Unit) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var originalMode: Int? = null
    private var originalSpeaker = false
    private var originalDevice: AudioDeviceInfo? = null
    private var routeChanged = false
    private var focus: AudioFocusRequest? = null
    fun start() {
        originalMode = manager.mode
        originalSpeaker = manager.isSpeakerphoneOn
        if (Build.VERSION.SDK_INT >= 31) originalDevice = manager.communicationDevice
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({ value ->
                if (value == AudioManager.AUDIOFOCUS_LOSS || value == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) interrupted()
            }, Handler(Looper.getMainLooper())).build()
        focus = request
        check(manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
    }
    fun speaker(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 31) {
            if (enabled) {
                val device = manager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                check(device != null && manager.setCommunicationDevice(device))
            } else manager.clearCommunicationDevice()
        } else manager.isSpeakerphoneOn = enabled
        routeChanged = true
    }
    fun stop() {
        runCatching {
            if (routeChanged) {
                if (Build.VERSION.SDK_INT >= 31) {
                    val device = originalDevice
                    if (device != null && manager.availableCommunicationDevices.any { it.id == device.id }) manager.setCommunicationDevice(device)
                    else manager.clearCommunicationDevice()
                } else manager.isSpeakerphoneOn = originalSpeaker
            }
        }
        focus?.let { runCatching { manager.abandonAudioFocusRequest(it) } }; focus = null
        originalMode?.let { mode -> runCatching { manager.mode = mode } }; originalMode = null
    }
}
