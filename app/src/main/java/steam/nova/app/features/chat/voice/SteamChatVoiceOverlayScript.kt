package steam.nova.app.features.chat.voice

/** Mount voice overlays inside Steam's screen providers, never beside its root navigator. */
internal object SteamChatVoiceOverlayScript {
    fun source() = """
    function chatVoiceOverlay(env){
        if(env.voiceOverlay)return env.voiceOverlay;
        var R=env.React;
        function Host(props){
            var focused=env.require(1366).useIsFocused();
            // Retained background screens must not duplicate banners or dismiss timers.
            // MainTabs and the thread route both inherit the root stack's safe-area provider.
            return focused?R.createElement(chatVoiceUi(env).Global,{
                threadKey:props.threadKey||'',account:props.account||''
            }):null;
        }
        env.voiceOverlay={Host:Host};return env.voiceOverlay;
    }
    """.trimIndent()
}
