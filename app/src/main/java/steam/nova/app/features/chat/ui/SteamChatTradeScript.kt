package steam.nova.app.features.chat.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.content.res.XmlResourceParser
import android.text.method.ScrollingMovementMethod
import android.widget.EditText
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import steam.nova.app.R
import steam.nova.app.core.ModuleLocalization
import java.util.Locale

internal object SteamChatTradeScript {
    private const val ANDROID = "http://schemas.android.com/apk/res/android"
    @Volatile private var cachedIcons: String? = null

    fun configureUrlField(activity: Activity?, args: JSONObject): JSONObject {
        val owner = args.getString("owner")
        require(owner.matches(Regex("trade-url-[A-Za-z0-9_-]+")))
        val field = activity?.window?.decorView?.findViewWithTag<EditText>(owner)
            ?: return JSONObject().put("attached", false)
        field.keyListener = null
        field.setTextIsSelectable(false)
        field.setSelectAllOnFocus(false)
        field.setHorizontallyScrolling(true)
        field.movementMethod = ScrollingMovementMethod.getInstance()
        field.showSoftInputOnFocus = false
        field.isCursorVisible = false
        field.isLongClickable = false
        field.isFocusableInTouchMode = false
        field.isFocusable = false
        field.clearFocus()
        field.setSelection(0)
        field.scrollTo(0, 0)
        return JSONObject().put("attached", true)
    }

    private fun color(parser: XmlResourceParser, name: String): String {
        val color = parser.getAttributeIntValue(ANDROID, name, Color.TRANSPARENT)
        return if (Color.alpha(color) == 0) "none"
        else String.format(Locale.ROOT, "#%06X", color and 0xffffff)
    }

    private fun icons(context: Context): String {
        cachedIcons?.let { return it }
        val resources = ModuleLocalization.resources(context)
        val icons = JSONObject()
        for ((name, id) in mapOf(
            "trade" to R.drawable.ic_steam_chat_trade_offer,
            "copy" to R.drawable.ic_steam_chat_copy_to_clipboard,
        )) {
            val icon = JSONObject()
            val paths = JSONArray()
            resources.getXml(id).use { parser ->
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    when (parser.name) {
                        "vector" -> {
                            val width = parser.getAttributeValue(ANDROID, "viewportWidth")
                            val height = parser.getAttributeValue(ANDROID, "viewportHeight")
                            icon.put("viewBox", "0 0 $width $height")
                        }
                        "path" -> paths.put(JSONObject()
                            .put("d", parser.getAttributeValue(ANDROID, "pathData"))
                            .put("fill", color(parser, "fillColor"))
                            .put("stroke", color(parser, "strokeColor"))
                            .put("strokeWidth", parser.getAttributeFloatValue(ANDROID, "strokeWidth", 0f))
                            .put("strokeLinecap", when (parser.getAttributeIntValue(ANDROID, "strokeLineCap", 0)) {
                                1 -> "round"; 2 -> "square"; else -> "butt"
                            })
                            .put("strokeLinejoin", when (parser.getAttributeIntValue(ANDROID, "strokeLineJoin", 0)) {
                                1 -> "round"; 2 -> "bevel"; else -> "miter"
                            })
                            .put("strokeMiterlimit", parser.getAttributeFloatValue(ANDROID, "strokeMiterLimit", 4f)))
                    }
                }
            }
            icons.put(name, icon.put("paths", paths))
        }
        return "var chatTradeIcons = $icons;\n".also { cachedIcons = it }
    }

    fun source(context: Context) = icons(context) + """
    function chatTradeCards(env,ui){
        var R=env.React,N=env.RN,h=R.createElement,t=ui.t,S=env.require(1611),serial=0;
        var AnimatedPath=N.Animated.createAnimatedComponent(S.Path),abs={position:'absolute',top:0,bottom:0,left:0,right:0};
        var inout=N.Easing.bezier(0.42,0,0.58,1);
        function text(value,style,props){return h(N.Text,Object.assign({allowFontScaling:false,style:Object.assign({
            fontFamily:(env.threadFonts||{}).regular||'sans-serif',fontSize:14,lineHeight:20.8,color:'#b5bec8',includeFontPadding:false},style||{})},props||{}),value);}
        function accountId(value){
            value=String(value||'');var n;
            if(/^\d{17}$/.test(value))n=(Number(value.slice(0,-8))-765611979)*100000000+Number(value.slice(-8))-60265728;
            else if(/^\d{1,10}$/.test(value))n=Number(value);else return '';
            return n>0&&n<=4294967295?String(n):'';
        }
        function attrs(raw){var a={},m,re=/([a-z_][a-z0-9_]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s\]]+))/gi;
            while((m=re.exec(raw||'')))a[m[1].toLowerCase()]=(m[2]||m[3]||m[4]||'').replace(/&amp;/gi,'&');return a;}
        function link(url){return /^https:\/\/steamcommunity\.com\/tradeoffer\/new\/?\?[^<>"'\s]+$/i.test(url||'')?url:null;}
        function model(tag,a,note,props){
            var item=props.item||{},self=env.currentAccount(),id=a.id||a.tradeofferid||a.trade_offer_id||'';
            var isLink=tag==='tradeofferlink'||(!id&&a.url);
            if(isLink){
                var url=link(a.url);if(!url)return null;
                var partner=accountId(a.partner),sender=accountId(item.sender);
                if(!partner||partner!==sender)return {kind:'link',url:url};
                return {kind:'url',url:url,self:item.sender===self,profile:props.profile||env.profile(null,item.sender)};
            }
            if(!/^[1-9]\d{0,19}$/.test(id))return null;
            var sent=accountId(a.sender||a.steamid||item.sender)===accountId(self),peer=(props.target||{}).peer;
            var friend=env.profile(sent?(props.target||{}).profile:props.profile,peer||item.sender);
            return {kind:'offer',id:id,note:note,sent:sent,self:item.sender===self,friend:friend,
                profile:sent?env.profile(item.sender===self?props.profile:null,self):friend,
                url:sent?'https://steamcommunity.com/profiles/'+self+'/tradeoffers/sent/#tradeofferid_'+id:'https://steamcommunity.com/tradeoffer/'+id+'/'};
        }
        function parse(body,props){
            var parts=[],last=0,m,re=/\[(tradeofferlink|tradeoffer|trade_offer|incomingtradeoffer)\b([^\]]*)\](?:([\s\S]*?)\[\/\1\])?/gi;
            while((m=re.exec(body))&&parts.length<100){
                if(m.index&&body[m.index-1]==='\\')continue;
                var value=model(m[1].toLowerCase(),attrs(m[2]),m[3]||'',props);
                if(!value)continue;
                if(m.index>last)parts.push({text:body.slice(last,m.index)});
                parts.push({trade:value});last=re.lastIndex;
            }
            if(!last)return null;if(last<body.length)parts.push({text:body.slice(last)});return parts;
        }
        function open(url){N.Linking.openURL(url).catch(function(){N.Alert.alert(t('chat_open'),t('chat_error_connection'));});}
        function shadow(blur,x,y,color,spread){return Number(N.Platform.Version)>=28?
            {boxShadow:[{offsetX:x,offsetY:y,blurRadius:blur,spreadDistance:spread||0,color:color}]}:{elevation:blur/2};}
        function Icon(props){var icon=chatTradeIcons[props.name];return h(S.Svg,{width:props.size,height:props.size,viewBox:icon.viewBox,accessible:false},
            icon.paths.map(function(path,i){return h(S.Path,Object.assign({key:i},path));}));}
        function Backdrop(){
            var size=R.useState({width:1,height:1}),id=R.useRef('tradeBackground'+(++serial)).current;
            var w=size[0].width,ht=size[0].height;
            return h(N.View,{pointerEvents:'none',onLayout:function(e){size[1](e.nativeEvent.layout);},
                style:Object.assign({},abs,{borderRadius:8,overflow:'hidden',backgroundColor:'#24272e'})},
                h(S.Svg,{width:w,height:ht,viewBox:'0 0 '+w+' '+ht},h(S.Defs,null,h(S.RadialGradient,{id:id,gradientUnits:'userSpaceOnUse',
                    cx:w*0.2,cy:ht*0.2,rx:256,ry:256,fx:w*0.2,fy:ht*0.2},
                    [0,0.5,1].map(function(n,i){return h(S.Stop,{key:i,offset:n,stopColor:['#3d444e','#292c33','#24272e'][i],stopOpacity:1});}))),
                    h(S.Rect,{width:w,height:ht,rx:8,ry:8,fill:'url(#'+id+')'})));
        }
        function TradeButton(props){
            var hover=R.useState(false),pressed=R.useState(false);
            var size=R.useState({width:152,height:32}),id=R.useRef('tradeButton'+(++serial)).current;
            function gradient(colors){var w=size[0].width,ht=size[0].height;
                return h(N.View,{pointerEvents:'none',style:abs},h(S.Svg,{width:w,height:ht,viewBox:'0 0 '+w+' '+ht},
                h(S.Defs,null,h(S.LinearGradient,{id:id,x1:'0%',y1:'0%',x2:'100%',y2:'0%'},colors.map(function(c,i){return h(S.Stop,{key:i,offset:i,stopColor:c});}))),
                h(S.Rect,{width:w,height:ht,rx:3,ry:3,fill:'url(#'+id+')'})));}
            return h(N.Pressable,{onPress:function(){open(props.url);},accessibilityRole:'button',accessibilityLabel:props.title,
                onHoverIn:function(){hover[1](true);},onHoverOut:function(){hover[1](false);},onPressIn:function(){pressed[1](true);},onPressOut:function(){pressed[1](false);},
                onLayout:function(e){size[1](e.nativeEvent.layout);},
                style:Object.assign({minWidth:Math.min(120,props.available),maxWidth:'100%',minHeight:32,paddingHorizontal:16,paddingBottom:2,justifyContent:'center',
                    alignSelf:props.horizontal?'center':'flex-start',marginVertical:0,borderRadius:3,flexShrink:0},
                    shadow(18,1,2,'rgba(0,0,0,0.3)'))},
                h(N.View,{pointerEvents:'none',style:Object.assign({},abs,{borderRadius:3,overflow:'hidden'})},
                    gradient(pressed[0]?['#018eea','#185feb']:hover[0]?['#33c0ff','#4a91ff']:['#15a2fe','#3647e9'])),
                text(props.title,{fontSize:13.3333,lineHeight:20,color:'#ffffff',textAlign:'center'}));
        }
        function CopyButton(props){
            var hover=R.useState(false),pressed=R.useState(false),stroke=R.useRef(new N.Animated.Value(0)).current,fill=R.useRef(new N.Animated.Value(0)).current;
            R.useEffect(function(){var a=N.Animated.timing(stroke,{toValue:hover[0]||pressed[0]?1:0,duration:200,easing:inout,useNativeDriver:false});
                a.start();return function(){a.stop();};},[hover[0],pressed[0]]);
            R.useEffect(function(){var a=N.Animated.timing(fill,{toValue:pressed[0]?1:0,duration:50,easing:inout,useNativeDriver:false});
                a.start();return function(){a.stop();};},[pressed[0]]);
            return h(N.Pressable,{accessibilityRole:'button',accessibilityLabel:t('chat_trade_copy'),hitSlop:4,
                onHoverIn:function(){hover[1](true);},onHoverOut:function(){hover[1](false);},onPressIn:function(){pressed[1](true);},onPressOut:function(){pressed[1](false);},
                onPress:function(){env.request('copy',{text:props.url}).catch(function(){N.Alert.alert(t('chat_trade_copy'),t('chat_error_connection'));});},
                style:{width:14,height:14,marginLeft:4,marginTop:2,flexShrink:0}},
                h(S.Svg,{width:14,height:14,viewBox:chatTradeIcons.copy.viewBox,accessible:false},chatTradeIcons.copy.paths.map(function(path,i){
                    return h(AnimatedPath,Object.assign({},path,{key:i,stroke:stroke.interpolate({inputRange:[0,1],outputRange:['#8492a4','#ffffff']}),
                        fill:fill.interpolate({inputRange:[0,1],outputRange:['rgba(255,255,255,0)','rgba(255,255,255,0.467)']})}));})));
        }
        function UrlField(props){
            var width=R.useState(0),inputWidth=Math.min(200,Math.max(1,width[0]-34));
            var owner=R.useRef('trade-url-'+(++serial)).current;
            function configure(){env.request('tradeUrlField',{owner:owner}).catch(function(){});}
            return h(N.View,{onLayout:function(e){width[1](e.nativeEvent.layout.width);},style:{flexDirection:'row',flexWrap:'wrap',alignItems:'center',marginBottom:10}},
                text(t('chat_trade_url_label'),{fontSize:12,lineHeight:18.4,color:'rgba(181,190,200,0.75)',marginRight:2,marginTop:2,alignSelf:'flex-start',flexShrink:0,maxWidth:'100%'}),
                h(N.View,{style:{flexDirection:'row',flex:1,minWidth:inputWidth+18,marginRight:16}},
                    h(N.TextInput,{key:props.url,testID:owner,collapsable:false,value:props.url,onLayout:configure,
                        showSoftInputOnFocus:false,selectTextOnFocus:false,contextMenuHidden:true,caretHidden:true,focusable:false,
                        selection:{start:0,end:0},accessibilityLabel:t('chat_trade_url_label'),allowFontScaling:false,underlineColorAndroid:'transparent',
                        style:{width:inputWidth,height:20,paddingHorizontal:4,paddingVertical:0,fontSize:13.3333,includeFontPadding:false,flexShrink:1,minWidth:0,
                            fontFamily:(env.threadFonts||{}).regular||'sans-serif',color:'rgba(181,190,200,0.5)',backgroundColor:'rgba(173,174,175,0.1)',
                            borderWidth:1,borderColor:'rgba(181,190,200,0.1)',borderRadius:1}}),
                    h(CopyButton,{url:props.url})));
        }
        function Card(props){
            var m=props.trade,p=m.profile||{},isUrl=m.kind==='url',small=m.self,avatar=48,available=R.useState(0);
            var horizontal=(isUrl||m.sent)&&available[0]>0;
            if(m.kind==='link')return text(m.url,{color:'#6dcff6',textDecorationLine:'underline'},{accessibilityRole:'link',onPress:function(){open(m.url);}});
            var title;
            if(isUrl)title=t('chat_trade_accepting',p.name||p.personaName||p.id);
            else {var template=t(m.sent?'chat_trade_sent':'chat_trade_received','\u0001'),parts=template.split('\u0001');
                title=[parts[0],text((m.friend||{}).name||'',{color:'#ebf5ff'},{key:'friend'}),parts.slice(1).join('\u0001')];}
            return h(N.View,{style:Object.assign({width:'100%',maxWidth:656,borderRadius:8,marginBottom:16,flexDirection:'row',alignItems:'flex-start'},
                shadow(8,0,0,'rgba(0,0,0,0.5)'))},
                h(Backdrop),
                h(N.View,{style:{padding:16,width:avatar+32,height:avatar+32}},
                    h(N.View,{style:Object.assign({width:avatar,height:avatar,borderRadius:4,backgroundColor:'#313943'},shadow(8,2,2,'rgba(0,0,0,0.3)',1))},
                        p.avatar?h(N.Image,{source:{uri:p.avatar},fadeDuration:0,style:{width:avatar,height:avatar,borderWidth:1,borderColor:'#000000',borderRadius:4}}):null),
                    !isUrl?h(N.View,{style:Object.assign({position:'absolute',right:8,bottom:10,padding:small?2:6,borderRadius:16,backgroundColor:'#179cfd'},
                        shadow(16,0,2,'rgba(0,0,0,0.8)'))},h(Icon,{name:'trade',size:small?14:20})):null),
                h(N.View,{onLayout:function(e){available[1](Math.max(1,e.nativeEvent.layout.width-(isUrl?16:32)));},
                    style:{flex:1,minWidth:0,padding:16,paddingLeft:isUrl?0:16,flexDirection:horizontal?'row':'column',flexWrap:horizontal?'wrap':'nowrap',justifyContent:horizontal?'space-between':undefined}},
                    h(N.View,{style:{flexGrow:horizontal?1:0,flexBasis:horizontal?(isUrl?234:170):undefined,
                        minWidth:horizontal?Math.min(isUrl?234:170,Math.max(0,available[0]-16)):0,flexShrink:1,marginRight:16}},
                        text(title),isUrl?h(UrlField,{url:m.url}):null,
                        m.note?h(N.View,{style:{marginTop:4,marginBottom:6,position:'relative'}},
                            h(N.Image,{pointerEvents:'none',fadeDuration:0,source:{uri:"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABEAAAASCAYAAAC9+TVUAAAAGXRFWHRTb2Z0d2FyZQBBZG9iZSBJbWFnZVJlYWR5ccllPAAAAUFJREFUeNqc099OgzAUBvBT/o84hrg9AyRcuEv3/nfuZpJAeIoZ0TBgw9rvKIlujMyehIRQ+PVrexBPm43s+57atqW2aagsS0H/LBHHsbyPIrIsmx+cTkdqDgdGi6K4CRRSSkqSRC5XKxLCUB835DgO3wPsuo7RPM/FJIJK05QTtU1LVfVGtoJc11WXR6Zp0veSGzoqNMsyMYoMUPSw5JkBDWVZFmOu5/KypfzkyYAC/IOgHtdrGQQL+nivqK7ri+hIhYSOQrHsiyTnENIg1dW9EEKl88gYG3zZ7QQ+BmT/zDZWCID3jGsvbLfPDIVhyHsyVcbUICCcCk4Ne6GFoF73ez7ehUqEPdBC8BsAQhIkGoOMW9r6NzSfB3oICm0PyJvN+NS0kAFC7wDyfV8PGXoI0J1aFjAtZKwZtZDzZvwSYACl/LGe91WlQgAAAABJRU5ErkJggg=="},
                                style:{position:'absolute',left:-16,top:5,width:17,height:18}}),
                            text(m.note,{fontSize:14,lineHeight:19,fontStyle:'italic',color:'#d6d7d8',backgroundColor:'#262626',
                                borderWidth:1,borderColor:'#3c3d3e',borderRadius:5,marginBottom:5,padding:13})):null),
                    h(TradeButton,{url:m.url,horizontal:horizontal,available:available[0],title:isUrl?t('chat_trade_send'):t('chat_trade_view')})));
        }
        return {parse:parse,Card:Card};
    }
""".trimIndent()
}
