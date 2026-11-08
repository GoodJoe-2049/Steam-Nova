package steam.nova.app.features.chat.presence

import steam.nova.app.features.chat.protocol.SteamChatProtocol as P

/** Used under the connection lock. Automatic activity never claims a user-selected status. */
internal class SteamChatLocalPresence {
    private var foreground = true
    private var state = 1
    private var userSelected = false

    fun foreground(value: Boolean): Boolean {
        if (foreground == value) return false
        foreground = value
        return true
    }

    fun receive(account: String, fields: P.Fields) {
        if (fields.number(1) and 1L == 0L) return
        val self = fields.messages(2).firstOrNull { it.number(1).toString() == account } ?: return
        if (!self.has(2) || self.number(2) !in 0L..7L) return
        state = self.number(2).toInt()
        if (self.has(10)) userSelected = self.number(10) != 0L
    }

    fun packet(): ByteArray {
        // Preserve explicit away/busy and all offline/invisible states across activity/reconnects.
        val automatic = state == 1 || (!userSelected && (state == 3 || state == 4))
        val effective = if (automatic) { if (foreground) 1 else 3 } else state
        return P.Writer().number(1, effective.toLong()).number(5, 0).number(7, 1)
            .number(8, if (foreground) 0 else 1).build()
    }
}
