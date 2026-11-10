package steam.nova.app.features.chat.linkpreview

import android.content.Context

internal object SteamChatLinkPreviewScript {
    fun source(context: Context): String = "var chatLinkPreviewProviders = ${SteamChatLinkPreviewRegistry.configuration(context)};\n" + """
    function chatLinkPreviewOEmbed(body,start){
        var quote='',escaped=false,end=start+7;
        for(;end<body.length;end++){
            var c=body[end];
            if(escaped){escaped=false;continue;}
            if(quote){if(c==='\\')escaped=true;else if(c===quote)quote='';}
            else if(c==='"'||c==="'")quote=c;
            else if(c===']')break;
        }
        if(end===body.length)return null;
        var attributes=body.slice(start+7,end),url='',a;
        var pattern=/([a-z_]+)\s*=\s*(?:"((?:\\.|[^"\\])*)"|'((?:\\.|[^'\\])*)'|([^\s\]]+))/gi;
        while((a=pattern.exec(attributes)))if(a[1].toLowerCase()==='url'){
            url=(a[2]||a[3]||a[4]||'').replace(/\\([\\"'])/g,'$1').replace(/&amp;/g,'&');break;
        }
        end++;
        var tail=body.slice(end),close=/\[\/oembed\]/i.exec(tail),next=/\[oembed\b/i.exec(tail);
        if(close&&(!next||close.index<next.index))end+=close.index+close[0].length;
        return {url:url,end:end};
    }
    function chatLinkPreviewOutgoing(body){
        var pattern=/\[(code|pre)\b[^\]]*\][\s\S]*?\[\/\1\]|\[oembed\b/gi,parts=[],last=0,m;
        while((m=pattern.exec(body))){
            if(m[1]||(m.index>0&&body[m.index-1]==='\\'))continue;
            var embedded=chatLinkPreviewOEmbed(body,m.index);
            if(!embedded)break;
            pattern.lastIndex=embedded.end;
            if(!/^https?:\/\/[^\s<>"'\[\]\\]+$/i.test(embedded.url))continue;
            parts.push(body.slice(last,m.index),embedded.url);last=embedded.end;
        }
        return last?parts.join('')+body.slice(last):body;
    }
    function chatLinkPreviews(env,ui){
        if(env.linkPreviews)return env.linkPreviews;
        var R=env.React,N=env.RN,h=R.createElement,t=ui.t;
        var providers=chatLinkPreviewProviders.map(function(provider){
            try{
            return {name:provider.name,provider:provider.provider,hosts:provider.hosts,types:provider.types.map(function(type){
                return {kind:type.kind,id:new RegExp('^(?:'+type.idPattern+')$'),canonicalPath:type.canonicalPath,layout:type.embed.layout,
                    routes:type.routes.map(function(route){return {path:new RegExp(route.path),idGroup:route.idGroup||1,idQuery:route.idQuery||''};})};
            })};
            }catch(ignore){return null;}
        }).filter(function(provider){return !!provider;});
        function matchUrl(raw){
            if(typeof raw!=='string'||raw.length>4096||/[\s\\\x00-\x1f\x7f]/.test(raw))return null;
            var url=/^https?:\/\/([a-z0-9.-]+)(\/[^?#]*)?(?:\?([^#]*))?(?:#.*)?$/i.exec(raw);
            if(!url)return null;
            var host=url[1].toLowerCase(),path=url[2]||'',query={};
            (url[3]||'').split('&').forEach(function(part){var at=part.indexOf('=');if(at<0)return;try{
                var key=decodeURIComponent(part.slice(0,at).replace(/\+/g,' '));
                if(!Object.prototype.hasOwnProperty.call(query,key))Object.defineProperty(query,key,{value:decodeURIComponent(part.slice(at+1).replace(/\+/g,' ')),enumerable:true});
            }catch(ignore){}});
            for(var p=0;p<providers.length;p++){
                var provider=providers[p];if(provider.hosts.indexOf(host)<0)continue;
                for(var k=0;k<provider.types.length;k++){
                    var type=provider.types[k];
                    for(var r=0;r<type.routes.length;r++){
                        var route=type.routes[r],found=route.path.exec(path);if(!found||found[0]!==path)continue;
                        var id=route.idQuery?query[route.idQuery]:found[route.idGroup];
                        if(typeof id!=='string'||!type.id.test(id))continue;
                        return {provider:provider.provider,name:provider.name,kind:type.kind,id:id,key:provider.provider+':'+type.kind+':'+id,
                            url:'https://'+host+type.canonicalPath.replace('{id}',encodeURIComponent(id)),source:raw,layout:type.layout};
                    }
                }
            }
            return null;
        }
        function parse(body){
            var pattern=/\[(code|pre|spoiler|img|video|file|attachment|b|i|u|s)\b[^\]]*\][\s\S]*?\[\/\1\]|\[url=(?:"([^"]+)"|([^\]]+))\]([\s\S]*?)\[\/url\]|\[url\](https?:\/\/[^\s<>\[\]]+)\[\/url\]|\[oembed\b|https?:\/\/[^\s<>"'\[\]，。！？；：）】]+/gi;
            var parts=[],last=0,count=0,m,seen={};
            while((m=pattern.exec(body))&&count<16){
                if(m[1])continue;
                var embedded=/^\[oembed\b/i.test(m[0])?chatLinkPreviewOEmbed(body,m.index):null;
                if(/^\[oembed\b/i.test(m[0])&&!embedded){pattern.lastIndex=body.length;continue;}
                var wrapped=!!(m[2]||m[3]||m[5]),raw=embedded?embedded.url:wrapped?(m[2]||m[3]||m[5]):m[0].replace(/[.,!?;:)\]}。，！？；：）】]+$/g,'');
                if(embedded)pattern.lastIndex=embedded.end;
                var reference=matchUrl(raw.replace(/&amp;/g,'&'));
                if(!reference&&!embedded)continue;
                if(m.index>last)parts.push({text:body.slice(last,m.index)});
                if(reference){if(!seen[reference.key]){parts.push({reference:reference});seen[reference.key]=true;}}
                else if(/^https?:\/\/[^\s<>"'\[\]]+$/i.test(raw))parts.push({text:raw});
                else parts.push({text:body.slice(m.index,embedded.end)});
                count++;
                last=embedded?embedded.end:m.index+(wrapped?m[0].length:raw.length);
            }
            if(!count)return null;
            if(last<body.length)parts.push({text:body.slice(last)});
            return parts;
        }
        function Card(props){
            var ref=props.reference,account=env.currentAccount();
            var owner=R.useMemo(function(){return 'link-preview-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2);},[account,ref.key]);
            var cacheKey=JSON.stringify([props.targetKey||'',props.messageId||owner,ref.key]);
            var sized=R.useState(false),status=R.useState('loading'),retry=R.useState(0);
            var foreground=R.useState(N.AppState.currentState==='active');
            var active=props.active!==false&&foreground[0],latest=R.useRef(active),attached=R.useRef(false),connect=R.useRef(null);
            latest.current=active;
            R.useEffect(function(){var sub=N.AppState.addEventListener('change',function(value){foreground[1](value==='active');});return function(){sub.remove();};},[]);
            R.useEffect(function(){
                if(!sized[0])return;
                var alive=true,timer=null,attempts=0,waiting=false,events=0;
                var sub=N.DeviceEventEmitter.addListener('SteamNovaChatLinkPreview',function(raw){
                    if(!alive)return;try{var event=typeof raw==='string'?JSON.parse(raw):raw;if(event.owner===owner){events++;status[1](event.state);}}catch(ignore){}
                });
                function attach(){
                    if(!alive||waiting)return;
                    waiting=true;
                    var before=events;
                    env.request('linkPreview',{owner:owner,cacheKey:cacheKey,url:ref.url,active:latest.current,retry:retry[0]>0},account).then(function(value){
                        waiting=false;
                        if(!alive)return;
                        if(!value.mounted){if(latest.current){if(++attempts<10)timer=setTimeout(attach,100);else status[1]('failed');}}
                        else {attached.current=true;if(events===before)status[1](value.state);env.request('linkPreview',{owner:owner,update:true,active:latest.current},account).catch(function(){});}
                    }).catch(function(error){waiting=false;if(!alive||!latest.current)return;
                        if(error.message==='STALE_REQUEST'&&++attempts<10)timer=setTimeout(attach,100);else status[1]('failed');});
                }
                connect.current=function(){attempts=0;clearTimeout(timer);attach();};
                attach();
                return function(){alive=false;attached.current=false;connect.current=null;clearTimeout(timer);sub.remove();env.request('linkPreview',{owner:owner,detach:true},account).catch(function(){});};
            },[owner,cacheKey,ref.url,sized[0],retry[0]]);
            R.useEffect(function(){if(attached.current)env.request('linkPreview',{owner:owner,update:true,active:active},account).catch(function(){});
                else if(active&&connect.current)connect.current();},[owner,active]);
            var layout=ref.layout,style={width:'100%',minWidth:0,borderRadius:layout.radius||0,overflow:'hidden',backgroundColor:'#1b1c20',marginVertical:4};
            if(layout.mode==='fixedHeight')style.height=layout.height;else style.aspectRatio=layout.ratio;
            var failed=status[0]==='failed',overlay={position:'absolute',left:0,top:0,right:0,bottom:0,alignItems:'center',justifyContent:'center'};
            return h(N.View,{style:style},
                h(N.View,{testID:owner,collapsable:false,onLayout:function(e){sized[1](e.nativeEvent.layout.width>0&&e.nativeEvent.layout.height>0);},
                    style:{position:'absolute',left:0,top:0,right:0,bottom:0}}),
                failed?h(N.View,{style:overlay},ui.label(ref.name,{color:'#dcdedf',fontWeight:'500'}),
                    h(ui.Button,{title:t('chat_could_not_load_retry'),onPress:function(){retry[1](function(n){return n+1;});}}),
                    h(ui.Button,{title:t('chat_open'),onPress:function(){N.Linking.openURL(ref.url).catch(function(){});}})):
                status[0]==='loading'?h(N.View,{pointerEvents:'none',style:overlay},h(N.ActivityIndicator,{color:'#66c0f4',accessibilityLabel:t('chat_loading')})):null);
        }
        return env.linkPreviews={parse:parse,match:matchUrl,Card:Card};
    }
    """.trimIndent()
}
