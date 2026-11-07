package steam.nova.app.features.chat.media.viewer

/** Does not log media URLs or message contents. */
internal object FileLog {
    fun e(error: Exception?) {
        android.util.Log.w("SteamNovaMedia", error!!.javaClass.simpleName)
    }
}
