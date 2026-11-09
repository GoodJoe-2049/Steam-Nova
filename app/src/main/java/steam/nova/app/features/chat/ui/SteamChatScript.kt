package steam.nova.app.features.chat.ui

import android.content.Context
import org.json.JSONObject
import steam.nova.app.features.chat.read.SteamChatReadScript
import steam.nova.app.features.chat.presence.SteamChatPresenceScript
import steam.nova.app.features.chat.scroll.SteamChatScrollScript
import steam.nova.app.features.chat.voice.SteamChatVoiceOverlayScript
import steam.nova.app.features.chat.voice.SteamChatVoiceScript
import steam.nova.app.features.chat.media.SteamChatMediaPresentationScript
import steam.nova.app.features.chat.media.SteamChatMediaResources

internal object SteamChatScript {
    fun source(context: Context, prefix: String): String = "var chatBridgePrefix = ${JSONObject.quote(prefix)};\n" +
            SteamChatMediaResources.source(context) + SteamChatTradeScript.source(context) + "\n" +
            SteamChatPresenceScript.source(context) + "\n" + SteamChatReadScript.source() + "\n" + SteamChatStoreScript.source() + "\n" + SteamChatRoomEffectsScript.source() + "\n" + SteamChatMediaPresentationScript.source() + "\n" + SteamChatMediaScript.source() + "\n" +
            SteamChatActionsScript.source() + "\n" + SteamChatGroupScript.source() + "\n" +
            SteamChatThreadAppearanceScript.source() + "\n" + SteamChatVoiceScript.source(context) + "\n" +
            SteamChatVoiceOverlayScript.source() + "\n" + SteamChatThreadLayoutScript.source() + "\n" + SteamChatScrollScript.source() + "\n" + SteamChatUiScript.source() + "\n" + SteamChatNavigationScript.source()
}
