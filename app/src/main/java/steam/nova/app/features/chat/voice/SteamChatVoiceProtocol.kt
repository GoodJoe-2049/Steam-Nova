package steam.nova.app.features.chat.voice

import steam.nova.app.features.chat.protocol.SteamChatProtocol as P

/** Steam CM voice protocol. IDs never pass through a JavaScript Number. */
internal object SteamChatVoiceProtocol {
    data class Relay(val ssrc: Long, val clientIp: Long, val clientPort: Long, val serverIp: Long, val serverPort: Long) {
        fun coordinates() = P.Writer().number(1, serverIp).number(2, serverPort).number(3, clientIp).number(4, clientPort)
    }
    fun owns(method: String) = method.startsWith("VoiceChatClient.") ||
            method.startsWith("WebRTCClientNotifications.") || method == "ChatRoomClient.NotifyShouldRejoinChatRoomVoiceChat"
    fun id(fields: P.Fields, field: Int = 1): String = java.lang.Long.toUnsignedString(fields.number(field))
    fun bits(value: String) = java.lang.Long.parseUnsignedLong(value).also { require(it != 0L) }
    fun answer(peer: String, voice: String, accepted: Boolean) =
        P.Writer().fixed64(1, bits(voice)).fixed64(2, bits(peer)).number(3, if (accepted) 1 else 0)
    fun leave(peer: String, voice: String) = P.Writer().fixed64(1, bits(peer)).fixed64(2, bits(voice))
    fun relay(fields: P.Fields) = Relay(fields.number(1), fields.number(2), fields.number(3), fields.number(4), fields.number(5))
    fun bind(voice: String, relay: Relay, userAgent: String) = P.Writer().fixed64(1, bits(voice))
        .number(2, relay.serverIp).number(3, relay.serverPort).number(4, relay.clientIp).number(5, relay.clientPort)
        .number(6, relay.ssrc).text(7, userAgent).number(8, 0).number(9, 0).number(10, 0).number(11, 0)
    fun status(voice: String, self: String, mic: Boolean, output: Boolean) = P.Writer()
        .fixed64(1, bits(voice)).fixed64(2, bits(self)).number(3, if (mic) 1 else 0)
        .number(4, if (output) 1 else 0).number(5, 0).number(6, 48000).number(7, 0)
}
