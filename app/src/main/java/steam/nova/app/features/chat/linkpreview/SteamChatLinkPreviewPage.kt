package steam.nova.app.features.chat.linkpreview

import org.json.JSONObject

internal object SteamChatLinkPreviewPage {
    fun hitTest(x: Float, y: Float, width: Int, height: Int): String = """
        (function(){
            var x=$x*innerWidth/${width.coerceAtLeast(1)},y=$y*innerHeight/${height.coerceAtLeast(1)};
            var element=document.elementFromPoint(x,y),axes=0;
            for(var node=element;node&&node!==document.documentElement&&node!==document.body;node=node.parentElement){
                var style=getComputedStyle(node),role=node.getAttribute('role');
                if(role==='slider'||(node.tagName==='INPUT'&&node.type==='range'))
                    axes|=node.getAttribute('aria-orientation')==='vertical'?2:1;
                if(/^(auto|scroll)$/.test(style.overflowX)&&node.scrollWidth>node.clientWidth+1)axes|=1;
                if(/^(auto|scroll)$/.test(style.overflowY)&&node.scrollHeight>node.clientHeight+1)axes|=2;
            }
            return axes;
        })();
    """.trimIndent()

    fun pause(embed: SteamChatLinkPreviewRegistry.Embed): String? = embed.pauseMessage?.let { message ->
        "window.postMessage($message,${JSONObject.quote(embed.origin)});"
    }
}
