package steam.nova.app.features.chat.ui

import android.content.Context
import org.json.JSONObject
import steam.nova.app.features.chat.voice.SteamChatVoiceScript

internal object SteamChatScript {
    fun source(context: Context, prefix: String): String = "var chatBridgePrefix = ${JSONObject.quote(prefix)};\n" +
            SteamChatStoreScript.source() + "\n" + SteamChatRoomEffectsScript.source() + "\n" + SteamChatMediaScript.source() + "\n" +
            SteamChatActionsScript.source() + "\n" + SteamChatGroupScript.source() + "\n" +
            SteamChatThreadAppearanceScript.source() + "\n" + SteamChatVoiceScript.source(context) + "\n" + SteamChatThreadLayoutScript.source() + "\n" + SteamChatUiScript.source() + "\n" + SteamChatNavigationScript.source()
}
