package steam.nova.app.features.chat.voice

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

/** Native RN controls. The invisible media engine has no responsibility for presentation. */
internal object SteamChatVoiceScript {
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
            "mic_on" to R.drawable.ic_steam_chat_voice_mic_on,
            "mic_off" to R.drawable.ic_steam_chat_voice_mic_off,
            "audio_on" to R.drawable.ic_steam_chat_voice_audio_on,
            "audio_off" to R.drawable.ic_steam_chat_voice_audio_off,
            "leave" to R.drawable.ic_steam_chat_voice_leave,
            "settings" to R.drawable.ic_steam_chat_settings,
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
        val source = "var chatVoiceIcons = $icons;"
        cachedIcons = IconCache(Configuration(resources.configuration), size, source)
        return source
    }

    fun source(context: Context) = iconSource(context) + "\n" + """
    function chatVoiceUi(env){
        if(env.voiceUi)return env.voiceUi;
        var R=env.React,N=env.RN,h=R.createElement,S=env.require(1611),t=moduleText,requesting=false;
        var ease=N.Easing.bezier(0.25,0.1,0.25,1),out=N.Easing.bezier(0,0,0.58,1),inout=N.Easing.bezier(0.42,0,0.58,1);
        var absolute={position:'absolute',left:0,right:0,top:0,bottom:0};
        function text(value,style,props){return h(N.Text,Object.assign({allowFontScaling:false,style:Object.assign({color:'#969696',fontSize:12,lineHeight:16,
            fontFamily:(env.threadFonts||{}).regular||'sans-serif',includeFontPadding:false},style||{})},props||{}),value);}
        function active(v){return !!(v&&v.phase&&v.phase!=='idle'&&v.phase!=='ended'&&(!v.account||v.account===env.currentAccount()));}
        function inThread(v,target){return active(v)&&target&&v.key===target.key&&v.phase!=='incoming';}
        function status(v){return t('chat_voice_'+(v.phase||'idle'));}
        function failure(code){return t('chat_voice_error_'+(['permission','media','timeout','disconnected','declined','interrupted','background'].indexOf(code)>=0?code:'failed'));}
        function send(action,v,args){var account=env.currentAccount();if(v.account&&v.account!==account)return Promise.resolve();
            return env.request('voice',Object.assign({action:action,token:v.token},args||{})).then(function(){if(account===env.currentAccount())return env.request('voiceState');})
                .then(function(value){if(value&&account===env.currentAccount())env.voiceUpdate(value);})
                .catch(function(){if(account===env.currentAccount())N.Alert.alert(t('chat_voice_call'),failure('failed'));});}
        function permission(){return N.PermissionsAndroid.request('android.permission.RECORD_AUDIO',{
            title:t('chat_voice_permission_title'),message:t('chat_voice_permission_body'),buttonPositive:t('chat_voice_continue'),buttonNegative:t('chat_cancel')
        }).then(function(result){if(result==='granted')return true;
            N.Alert.alert(t('chat_voice_permission_title'),failure('permission'),[{text:t('chat_cancel'),style:'cancel'},
                {text:t('chat_voice_open_settings'),onPress:function(){N.Linking.openSettings();}}]);return false;});}
        function start(target){var v=env.voiceCurrent||{},account=env.currentAccount();
            if(active(v)||v.settling){if(active(v)&&v.key===target.key){if(v.phase==='incoming')accept(v);else send('leave',v);}
                else N.Alert.alert(t('chat_voice_call'),t('chat_voice_busy'));return;}
            if(requesting)return;requesting=true;N.Keyboard.dismiss();
            permission().then(function(granted){if(granted&&account===env.currentAccount()&&N.AppState.currentState==='active')return send('start',v,target);})
                .catch(function(){if(account===env.currentAccount())N.Alert.alert(t('chat_voice_call'),failure('permission'));}).then(function(){requesting=false;});}
        function accept(v){if(requesting)return;requesting=true;var account=env.currentAccount();
            permission().then(function(granted){var current=env.voiceCurrent||{};
                if(granted&&account===env.currentAccount()&&current.token===v.token&&current.phase==='incoming'&&N.AppState.currentState==='active')return send('accept',v);
            }).catch(function(){if(account===env.currentAccount())N.Alert.alert(t('chat_voice_call'),failure('permission'));}).then(function(){requesting=false;});}
        function toggleMic(v){send('mute',v,{mic:!v.mic,output:v.mic&&v.output?false:!!v.output});}
        function toggleOutput(v){send('mute',v,{mic:!!v.mic===!!v.output?!v.mic:!!v.mic,output:!v.output});}
        function Icon(props){var icon=chatVoiceIcons[props.name];return icon?h(N.Image,{source:{uri:icon},resizeMode:'contain',fadeDuration:0,accessible:false,
            style:{width:props.size||24,height:props.size||24,tintColor:props.color||'#c8f1c2'}}):null;}
        function useReduced(){var reduced=R.useState(false);R.useEffect(function(){var live=true;
            N.AccessibilityInfo.isReduceMotionEnabled().then(function(v){if(live)reduced[1](v);}).catch(function(){});
            var sub=N.AccessibilityInfo.addEventListener('reduceMotionChanged',reduced[1]);return function(){live=false;sub.remove();};
        },[]);return reduced[0];}
        function useTween(value,duration,curve,native,initial){var progress=R.useRef(new N.Animated.Value(initial===undefined?value:initial)).current,reduced=useReduced();
            R.useEffect(function(){var anim=N.Animated.timing(progress,{toValue:value,duration:reduced?90:duration,easing:curve,useNativeDriver:native,isInteraction:false});
                anim.start();return function(){anim.stop();};},[value,duration,reduced]);return progress;}
        // Keep the last children until exit finishes. Width/height deliberately follow the web's layout transitions;
        // opacity, gradient movement and glow run on the native animation driver.
        function Presence(props){var mounted=R.useState(props.show),size=R.useRef(new N.Animated.Value(0)).current,
            opacity=R.useRef(new N.Animated.Value(0)).current,last=R.useRef(props.children),reduced=useReduced();
            if(props.show)last.current=props.children;
            R.useEffect(function(){var live=true;if(props.show)mounted[1](true);
                var duration=reduced?90:(props.duration||300),fade=reduced?90:props.show?duration:(props.exitFade||duration);
                var animation=N.Animated.parallel([
                    N.Animated.timing(size,{toValue:props.show?1:0,duration:duration,easing:props.curve||out,useNativeDriver:false,isInteraction:false}),
                    N.Animated.timing(opacity,{toValue:props.show?1:0,duration:fade,easing:props.curve||out,useNativeDriver:true,isInteraction:false})]);
                animation.start(function(result){if(live&&result.finished&&!props.show)mounted[1](false);});
                return function(){live=false;animation.stop();};
            },[props.show,reduced]);
            if(!props.show&&!mounted[0])return null;
            var layout={};if(props.axis)layout[props.axis]=size.interpolate({inputRange:[0,1],outputRange:[0,props.extent]});
            return h(N.Animated.View,{pointerEvents:props.show?'auto':'none',accessibilityElementsHidden:!props.show,
                importantForAccessibility:props.show?'auto':'no-hide-descendants',style:[props.style,layout,props.axis?{overflow:props.overflow||'hidden'}:{}]},
                h(N.Animated.View,{style:[props.contentStyle,{opacity:props.fade===false?1:opacity}]},R.cloneElement(props.show?props.children:last.current,{exiting:!props.show})));}
        function Gradient(props){var id=props.id,radial=props.radial,width=props.width,height=props.height;
            return h(S.Svg,{width:width||'100%',height:height||'100%',viewBox:width&&height?'0 0 '+width+' '+height:undefined,
                style:absolute,accessible:false,pointerEvents:'none'},
                h(S.Defs,null,h(radial?S.RadialGradient:S.LinearGradient,Object.assign({id:id},radial||props.direction||{x1:0,y1:0,x2:1,y2:0}),
                    props.stops.map(function(stop,i){return h(S.Stop,{key:i,offset:stop[0],stopColor:stop[1],stopOpacity:stop.length>2?stop[2]:1});}))),
                h(S.Rect,{width:width||'100%',height:height||'100%',fill:'url(#'+id+')'}));}
        function Inset(props){if(Number(N.Platform.Version)>=29)return null;
            var stops=[[0,'#000000',props.row?0.25:0.65],[1,'#000000',0]];
            return h(N.View,{pointerEvents:'none',style:absolute},
                h(N.View,{style:{position:'absolute',left:0,right:0,top:0,height:4}},h(Gradient,{id:'voiceInsetTop',direction:{x1:0,y1:0,x2:0,y2:1},stops:stops})),
                !props.row?h(N.View,{style:{position:'absolute',left:0,top:0,bottom:0,width:4}},h(Gradient,{id:'voiceInsetLeft',stops:stops})):null,
                !props.row?h(N.View,{style:{position:'absolute',right:0,top:0,bottom:0,width:4}},h(Gradient,{id:'voiceInsetRight',direction:{x1:1,y1:0,x2:0,y2:0},stops:stops})):null,
                !props.row?h(N.View,{style:{position:'absolute',left:0,right:0,bottom:0,height:4}},h(Gradient,{id:'voiceInsetBottom',direction:{x1:0,y1:1,x2:0,y2:0},stops:stops})):null);}
        function insetStyle(row){return Number(N.Platform.Version)>=29?{boxShadow:[{inset:true,offsetX:0,offsetY:row?2:0,blurRadius:4,color:row?'rgba(0,0,0,0.25)':'#000000'}]}:{};}
        function GreenButton(props){var width=props.width||94.16;return h(N.Pressable,{onPress:props.press,accessibilityRole:'button',accessibilityLabel:props.title,hitSlop:{top:4,bottom:4},
            style:{height:32,width:width,marginTop:4,marginRight:8,borderRadius:3,justifyContent:'center',alignItems:'center',
                boxShadow:Number(N.Platform.Version)>=28?[{offsetX:1,offsetY:2,blurRadius:18,color:'rgba(0,0,0,0.3)'}]:undefined,elevation:Number(N.Platform.Version)<28?3:0}},
            function(s){var colors=s.pressed?['#0b6416','#0ead1e']:s.hovered?['#13ae26','#16f72d']:['#12a123','#10ce4f'];
                // Android resolves an SVG percentage against the padded content box. Keep the paint layer unpadded
                // and give it explicit dimensions; only the separate label wrapper owns the web's text padding.
                return h(N.View,{style:{width:width,height:32,borderRadius:3,overflow:'hidden'}},
                    h(Gradient,{id:'voiceGreenButton',width:width,height:32,direction:{gradientUnits:'userSpaceOnUse',x1:0,y1:0,x2:width,y2:0},stops:[[0,colors[0]],[1,colors[1]]]}),
                    h(N.View,{style:{width:width,height:32,justifyContent:'center',paddingHorizontal:16,paddingBottom:2}},
                        text(props.title,{fontSize:13.333,lineHeight:14,color:'#ffffff',textAlign:'center'},{numberOfLines:2})));});}
        function DockButton(props){var hover=R.useState(false),pressed=R.useState(false),leave=props.icon==='leave',lit=hover[0]||pressed[0];
            var glow=useTween(lit?1:0,300,ease,true);
            return h(N.Pressable,{onPress:props.press,onHoverIn:function(){hover[1](true);},onHoverOut:function(){hover[1](false);},
                onPressIn:function(){pressed[1](true);},onPressOut:function(){pressed[1](false);},accessibilityRole:'button',accessibilityLabel:props.title,
                accessibilityState:{selected:!!props.selected},hitSlop:{top:3,bottom:3},
                style:{width:leave?46:42,height:leave?46:42,marginLeft:leave?0:2,marginTop:leave?0:2,alignItems:'center',justifyContent:'center',
                    borderWidth:leave?1:0,borderTopColor:'#1b6329',borderLeftColor:'#1b6329',borderRightColor:'#1c852f',borderBottomColor:'#1c852f',
                    borderRightWidth:leave?0.5:0,borderBottomWidth:leave?0.5:0,backgroundColor:leave?'rgba(36,39,46,0.5)':pressed[0]?'rgba(97,253,151,0.5)':hover[0]?'rgba(97,253,151,0.3)':'transparent'}},
                leave?h(N.Animated.View,{pointerEvents:'none',style:[absolute,{backgroundColor:'rgba(97,253,151,0.3)',opacity:glow}]}):null,
                h(Icon,{name:props.icon,size:leave?26:24,color:lit?'#ffffff':'#c8f1c2'}));}
        function DockFace(props){var v=props.value,slide=useTween(props.exiting?0:1,300,ease,true,0);
            return h(N.View,{style:[{width:140,height:50,padding:2,backgroundColor:'#1b1c20',overflow:'hidden'},insetStyle(false)]},
                h(N.Animated.View,{pointerEvents:'none',style:{position:'absolute',left:0,top:0,width:256,height:128,
                    transform:[{translateX:slide.interpolate({inputRange:[0,1],outputRange:[-190,0]})}]}},
                    h(Gradient,{id:'voiceDockGreen',radial:{gradientUnits:'userSpaceOnUse',cx:51.2,cy:64,rx:128,ry:128},stops:[[0,'#1da834'],[1,'#1b1c20',0]]})),
                h(N.View,{style:{flexDirection:'row'}},
                    h(DockButton,{icon:'leave',title:t('chat_voice_leave'),press:function(){send('leave',v);}}),
                    h(DockButton,{icon:v.mic?'mic_off':'mic_on',selected:!v.mic,title:t(v.mic?'chat_voice_unmute':'chat_voice_mute'),press:function(){toggleMic(v);}}),
                    h(DockButton,{icon:v.output?'audio_off':'audio_on',selected:!v.output,title:t(v.output?'chat_voice_audio_enable':'chat_voice_audio_disable'),press:function(){toggleOutput(v);}})),h(Inset));}
        function Dock(props){var state=env.useStore(),v=state.voice||{},show=inThread(v,props.target);
            return h(Presence,{key:state.account+':'+props.target.key,show:show,axis:'width',extent:148,curve:ease,fade:false,
                style:{height:50,flexShrink:0},contentStyle:{width:140,height:50,marginLeft:8}},h(DockFace,{value:v}));}
        function HeaderGlow(props){var muted=!!props.value.mic,level=useTween(muted?0:1,200,inout,true);
            // Screen-blended #7af55b over the native header's #1c232c. This also works on older RN/Android renderers.
            // End the light inside the SVG, rather than cutting a still-visible gradient at the rectangular edge.
            // The 70% transparent stop gives a 12.6 x 15.4 dp footprint around the microphone.
            function layer(id,strong){var cx=strong?14:16;return h(Gradient,{id:id,width:44,height:47,
                radial:{gradientUnits:'userSpaceOnUse',cx:cx,cy:20,fx:cx,fy:20,rx:18,ry:22},
                stops:[[0,'#89f677',strong?1:0.2],[0.3,'#89f677',strong?0.3:0.1],[0.5,'#89f677',strong?0.1:0.05],[0.7,'#89f677',0],[1,'#89f677',0]]});}
            return h(N.View,{pointerEvents:'none',style:{width:26,height:42}},
                h(N.Animated.View,{style:{position:'absolute',left:-4,top:-1,width:44,height:47,opacity:level.interpolate({inputRange:[0,1],outputRange:[1,0]})}},layer('voiceHeaderMuted',false)),
                h(N.Animated.View,{style:{position:'absolute',left:-4,top:-1,width:44,height:47,opacity:level}},layer('voiceHeaderLive',true)),
                h(N.View,{style:{position:'absolute',top:13,left:2}},h(Icon,{name:muted?'mic_off':'mic_on',size:16,color:'#ffffff'})));}
        function HeaderIndicator(props){var state=env.useStore(),v=state.voice||{};
            return h(Presence,{key:state.account+':'+props.target.key,show:inThread(v,props.target),axis:'width',extent:26,duration:200,curve:inout,overflow:'visible',contentStyle:{width:26,height:42}},
                h(HeaderGlow,{value:v}));}
        function Participant(props){var connected=props.connected,dim=useTween(props.connecting?0.6:0,300,inout,true),visible=useTween(props.empty?0:1,320,inout,true);
            var avatar=props.avatar,p=props.profile||{};
            return h(N.View,{style:{width:36,height:36,marginTop:1,marginRight:8,borderRadius:2,backgroundColor:'rgba(0,0,0,0.1333)',
                borderBottomWidth:1,borderBottomColor:connected?'rgba(65,189,121,0.3)':'rgba(255,255,255,0.533)',
                boxShadow:Number(N.Platform.Version)>=29?[
                    {inset:true,offsetX:1,offsetY:1,blurRadius:4,color:'#000000'},{inset:true,offsetX:1,offsetY:1,blurRadius:4,color:'#000000'},
                    {offsetX:1,offsetY:0,blurRadius:1,color:'rgba(255,255,255,0.2)'},{offsetX:0,offsetY:1,blurRadius:0,color:'rgba(255,255,255,0.2)'}]:undefined}},
                h(N.Animated.View,{style:{width:36,height:36,opacity:visible}},
                    avatar?h(avatar,{profile:p,size:36}):p.avatar?h(N.Image,{source:{uri:p.avatar},fadeDuration:0,style:{width:36,height:36}}):null),
                h(N.Animated.View,{pointerEvents:'none',style:[absolute,{backgroundColor:'#000000',opacity:dim}]}),
                props.empty?h(Inset):null);}
        function name(v,state){if(v.peer)return env.profile(state.profiles[v.peer],v.peer).name;
            var room;state.groups.some(function(g){room=(g.rooms||[]).find(function(r){return r.key===v.key;});return !!room;});return room?room.title:t('chat_voice_call');}
        function Row(props){var v=props.value,state=props.state,connected=v.phase==='connected',width=R.useState(390),wash=useTween(connected?1:0,300,ease,true);
            var peerPresent=connected||(v.members||[]).some(function(m){return m.id===v.peer;}),waiting=!!v.peer&&!peerPresent;
            var ids=[state.account].concat(v.peer?[v.peer]:(v.members||[]).map(function(m){return m.id;}).filter(function(id){return id!==state.account;}));
            var label=waiting?t('chat_voice_waiting_friend',name(v,state)):connected?v.peer?t('chat_voice_active_friend',name(v,state)):status(v):status(v);
            return h(N.View,{onLayout:function(e){width[1](e.nativeEvent.layout.width);},style:[{height:40,flexDirection:'row',backgroundColor:'#393e45'},insetStyle(true)]},
                h(N.Animated.View,{pointerEvents:'none',style:[absolute,{opacity:wash}]},h(Gradient,{id:'voiceRowConnected',
                    radial:{gradientUnits:'userSpaceOnUse',cx:width[0]*0.1,cy:20,rx:width[0]*0.64,ry:124},stops:[[0,'#1c9331',0.5],[0.7,'#1c9331',0]]})),
                h(N.View,{accessible:true,accessibilityRole:'image',accessibilityLabel:t('chat_voice_settings'),style:{width:44,height:40,alignItems:'center',justifyContent:'center'}},h(Icon,{name:'settings',color:'#898989'})),
                h(GreenButton,{title:t(connected?'chat_voice_stop_talking':'chat_voice_end_chat'),press:function(){send('leave',v);}}),
                h(N.View,{style:{flexDirection:'row',maxWidth:v.peer?88:132,overflow:'hidden'}},ids.slice(0,v.peer?2:3).map(function(id){var p=env.profile(id===state.account?Object.assign({},state.self||{},state.profiles[id]||{}):state.profiles[id],id);
                    return h(Participant,{key:id,profile:p,avatar:props.avatar,empty:id===v.peer&&!peerPresent,connecting:v.phase==='connecting'||v.phase==='reconnecting',connected:connected});})),
                text(label,{flex:1,minWidth:0,marginLeft:8,marginRight:8,marginTop:10,color:connected?'#87dfa3':'#969696'},{numberOfLines:1,accessibilityLiveRegion:'polite'}),h(Inset,{row:true}));}
        function Bar(props){var state=env.useStore(),v=state.voice||{},focused=env.require(1366).useIsFocused();
            R.useEffect(function(){if(focused){env.voiceThread=props.target.key;env.emit();}
                return function(){if(env.voiceThread===props.target.key){env.voiceThread='';env.emit();}};},[focused,props.target.key,state.account]);
            return h(Presence,{key:state.account+':'+props.target.key,show:inThread(v,props.target),axis:'height',extent:40,exitFade:160,contentStyle:{height:40}},
                h(Row,{value:v,state:state,avatar:props.avatar}));}
        function Global(){var state=env.useStore(),v=state.voice||{},insets=env.require(1486).useSafeAreaInsets();env.voiceCurrent=v;
            var incoming=v.phase==='incoming',ended=v.phase==='ended',outside=active(v)&&env.voiceThread!==v.key,show=incoming||outside||(ended&&!!v.reason);
            R.useEffect(function(){if(ended&&!v.reason){var timer=setTimeout(function(){send('dismiss',v);},300);return function(){clearTimeout(timer);};}},[v.token,v.phase,v.reason]);
            R.useEffect(function(){if(incoming){N.Keyboard.dismiss();N.AccessibilityInfo.announceForAccessibility(t('chat_voice_incoming'));}},[incoming,v.token]);
            function open(){if(!v.key||!active(v))return;state.openTarget=env.targetFor(v.key);env.emit();}
            return h(Presence,{key:state.account,show:show,duration:200,exitFade:160,
                style:{position:'absolute',top:(insets.top||0)+56,left:8,right:8,zIndex:100,elevation:12}},
                h(N.View,{style:{backgroundColor:'#393e45',borderWidth:1,borderColor:'#4d555e',padding:10,
                    boxShadow:Number(N.Platform.Version)>=28?[{offsetX:0,offsetY:4,blurRadius:18,color:'rgba(0,0,0,0.4)'}]:undefined}},
                    h(N.Pressable,{onPress:open,disabled:ended,accessibilityRole:ended?undefined:'button',accessibilityLabel:t('chat_voice_expand'),style:{minHeight:40,justifyContent:'center'}},
                        text(name(v,state),{color:'#c8f1c2',fontSize:14},{numberOfLines:1}),text(ended&&v.reason?failure(v.reason):status(v),{marginTop:4},{accessibilityLiveRegion:'polite'})),
                    incoming?h(N.View,{style:{flexDirection:'row',justifyContent:'flex-end'}},
                        h(GreenButton,{title:t('chat_voice_decline'),press:function(){send('leave',v);}}),h(GreenButton,{title:t('chat_voice_accept'),press:function(){accept(v);}})):
                        ended?h(GreenButton,{title:t('chat_close'),press:function(){send('dismiss',v);}}):h(N.View,{style:{alignItems:'flex-end',marginTop:6}},h(DockFace,{value:v}))));
        }
        env.voiceUi={Global:Global,Bar:Bar,Dock:Dock,HeaderIndicator:HeaderIndicator,start:start,inThread:inThread};return env.voiceUi;
    }
    """.trimIndent()
}
