package steam.nova.app.features.chat.presence

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import org.json.JSONObject
import steam.nova.app.R
import steam.nova.app.core.ModuleLocalization
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/** Export the imported module vectors for the host RN renderer. */
internal object SteamChatPresenceScript {
    private data class IconCache(val configuration: Configuration, val size: Int, val source: String)

    private var cachedIcons: IconCache? = null

    /** Exports module drawables for RN Image; Steam's resource IDs belong to a different APK. */
    @Synchronized
    private fun iconSource(context: Context): String {
        val resources = ModuleLocalization.resources(context)
        val size = ceil(24 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        cachedIcons?.takeIf { it.size == size && it.configuration == resources.configuration }
            ?.let { return it.source }

        val icons = JSONObject()
        for ((name, id) in mapOf(
            "mobile" to R.drawable.ic_chat_platform_mobile,
            "big_picture" to R.drawable.ic_chat_platform_big_picture,
            "vr" to R.drawable.ic_chat_platform_vr,
            "handheld" to R.drawable.ic_chat_platform_handheld,
            "web" to R.drawable.ic_chat_platform_web,
            "desktop" to R.drawable.ic_chat_platform_desktop,
        )) {
            val drawable = checkNotNull(ResourcesCompat.getDrawable(resources, id, null))
            val bitmap = createBitmap(size, size)
            try {
                drawable.setBounds(0, 0, size, size)
                drawable.draw(Canvas(bitmap))
                val uri = ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                }
                icons.put(name, uri)
            } finally {
                bitmap.recycle()
            }
        }
        val source = "var chatPresenceIcons = $icons;"
        cachedIcons = IconCache(Configuration(resources.configuration), size, source)
        return source
    }

    fun source(context: Context) = iconSource(context) + "\n" + """
    function chatPresenceState(){
        var epoch=0,values={};
        function reset(next){if(next>epoch){epoch=next;values={};}}
        function accept(packet){
            if(!packet||!packet.epoch||packet.epoch<epoch)return;
            reset(packet.epoch);
            (packet.values||[]).forEach(function(p){
                if(!p.id||p.epoch!==epoch)return;
                var old=values[p.id];if(!old||p.revision>old.revision)values[p.id]=p;
            });
        }
        function remember(profile){if(profile&&profile.presence)accept({epoch:profile.presence.epoch,values:[profile.presence]});}
        function apply(profile){
            var p=values[profile.id];
            return Object.assign({},profile,{presence:p||null,presenceKnown:!!(p&&p.known),
                personaState:p?p.personaState:0,online:!!(p&&p.known&&p.online),
                playing:!!(p&&p.online&&(p.playing||p.game)),game:p&&p.online?p.game:'',
                richPresence:p&&p.online&&(p.playing||p.game)?p.richPresence||'':'',
                gameId:p&&p.online?p.gameId:'0',appId:p&&p.online?p.appId:0});
        }
        return {accept:accept,remember:remember,apply:apply,reset:reset};
    }
    function chatPresencePlatforms(p){
        if(!p||!p.known||!p.online)return [];
        var flags=p.flags,device=p.device,icons=[],handheld=device===544||device===545||p.deck===true;
        if(flags!==null&&flags!==undefined){
            if(flags&512)icons.push('mobile');
            if(flags&256)icons.push('web');
            if(flags&2048)icons.push('vr');
            if((flags&1024)&&!(flags&2048)&&!handheld)icons.push('big_picture');
        }
        if(handheld)icons.push('handheld');
        // Product rule: confirmed online without a special platform is displayed as desktop.
        // LaunchTypeGamepad (4096) / LaunchTypeCompatTool (8192) are not platform bits.
        if(!icons.length&&flags!==null&&flags!==undefined)icons.push('desktop');
        return icons;
    }
    function chatPresenceUi(env){
        if(env.presenceUi)return env.presenceUi;
        var R=env.React,N=env.RN,h=R.createElement,t=moduleText;
        function Status(props){
            var p=props.profile||{},playing=!!(p.playing||p.game),icons=props.typing||playing?[]:chatPresencePlatforms(p.presence);
            var title=props.typing||(playing?(p.game||t('chat_in_game')):(p.presenceKnown?t(p.online?'chat_online':'chat_offline'):'—'));
            var color=props.color||(playing?'#90ba3c':p.online?'#6dcff6':'#898989'),size=props.fontSize||13;
            var descriptions=icons.map(function(key){return t('chat_platform_'+key);});descriptions.push(title);
            return h(N.View,{accessible:true,accessibilityLabel:descriptions.join(', '),pointerEvents:'none',
                style:[{flexDirection:'row',alignItems:'center',minWidth:0},props.style]},
                icons.map(function(key){return h(N.Image,{key:key,accessible:false,source:{uri:chatPresenceIcons[key]},
                    fadeDuration:0,resizeMode:'contain',style:{width:size+3,height:size+3,marginRight:4,tintColor:color}});}),
                h(N.Text,{numberOfLines:1,ellipsizeMode:'tail',style:{fontSize:size,lineHeight:props.lineHeight||size+4,includeFontPadding:false,
                    fontFamily:(env.threadFonts||{}).regular||'sans-serif',color:color,flexShrink:1}},title));
        }
        function GameDetails(props){
            var p=props.profile||{},text=p.online&&(p.playing||p.game)?p.richPresence||'':'';
            if(!text.trim())return null;
            return h(N.Text,{numberOfLines:1,ellipsizeMode:'tail',accessibilityLabel:text,
                style:{fontSize:11,lineHeight:13,includeFontPadding:false,color:'#62813b',
                    fontFamily:(env.threadFonts||{}).regular||'sans-serif'}},text);
        }
        env.presenceUi={Status:Status,GameDetails:GameDetails};return env.presenceUi;
    }
""".trimIndent()
}
