package steam.nova.app.features.chat.media

/** Media-only UI. Parsing, transfer state and actions are shared by cards and the full-screen viewer. */
internal object SteamChatMediaPresentationScript {
    fun source() = """
    function chatMediaPresentation(env,ui){
        if(env.mediaPresentation)return env.mediaPresentation;
        var R=env.React,N=env.RN,h=R.createElement,t=ui.t,records={},subscribers={},serial=0;
        var abs={position:'absolute',left:0,top:0,right:0,bottom:0};
        function safe(url){return /^https:\/\/(?:[a-z0-9-]+\.)*(?:steamusercontent\.com|steamcontent\.com|steamstatic\.com|akamaihd\.net|steamcommunity\.com)\//i.test(url||'')?url.replace(/&amp;/g,'&'):null;}
        function attributes(raw){var out={},re=/([a-z_]+)=(?:"([^"]*)"|'([^']*)'|([^\s\]]+))/gi,m;while((m=re.exec(raw||'')))out[m[1].toLowerCase()]=(m[2]||m[3]||m[4]||'').replace(/&amp;/g,'&');return out;}
        function parse(text){
            var parts=[],last=0,m,re=/\[(img|video)([^\]]*)\]([\s\S]*?)\[\/\1\]/gi;
            while((m=re.exec(text))&&parts.length<80){
                var a=attributes(m[2]),inside=/\[url=(?:"([^"]+)"|([^\]]+))\]/i.exec(m[3]);
                var url=safe(a.src||a.url||(inside?(inside[1]||inside[2]):m[3].trim()));
                if(!url)continue;
                if(m.index>last)parts.push({text:text.slice(last,m.index)});
                parts.push({kind:m[1].toLowerCase()==='video'?'video':'image',url:url,poster:safe(a.thumbnail_src||a.poster),
                    width:Number(a.width)||0,height:Number(a.height)||0,duration:Number(a.duration)||0,size:Number(a.size)||0,raw:m[0]});
                last=re.lastIndex;
            }
            if(last){if(last<text.length)parts.push({text:text.slice(last)});return parts;}
            var wrapped=/^\[url=(?:"([^"]+)"|([^\]]+))\]([\s\S]*?)\[\/url\]$/i.exec(text.trim());
            var direct=safe(wrapped?(wrapped[1]||wrapped[2]):text.trim());
            if(direct&&/(?:\/ugc\/|\.(?:mp4|webm|mpg|mpeg|ogv|png|jpe?g|webp|gif|avif)(?:\?|$))/i.test(direct)){
                return [{url:direct,kind:/\.(?:mp4|webm|mpg|mpeg|ogv)(?:\?|$)/i.test(direct)?'video':/\.(?:png|jpe?g|webp|gif|avif)(?:\?|$)/i.test(direct)?'image':'auto',raw:text}];
            }
            return null;
        }
        function duration(value){var n=Math.max(0,Math.floor(Number(value)||0));return Math.floor(n/60)+':'+String(n%60).padStart(2,'0');}
        function size(value){var n=Number(value)||0;if(!n)return t('chat_media_unknown_size');return n>=1048576?(n/1048576).toFixed(1)+' MB':Math.max(1,Math.round(n/1024))+' KB';}
        function record(media){var key=env.currentAccount()+':'+media.url;if(!records[key])records[key]={key:key,account:env.currentAccount(),url:media.url,state:'idle',kind:media.kind,duration:media.duration||0,size:media.size||0,poster:media.poster||'',width:media.width||0,height:media.height||0};return records[key];}
        function publish(value,patch){if(value.account!==env.currentAccount())return;Object.assign(value,patch);(subscribers[value.key]||[]).forEach(function(fn){fn(function(n){return n+1;});});}
        function poll(value){clearTimeout(value.timer);if(value.state!=='downloading'||value.account!==env.currentAccount())return;
            value.timer=setTimeout(function(){env.request('mediaStatus',{url:value.url}).then(function(v){publish(value,v);poll(value);}).catch(function(e){publish(value,{state:'failed',error:e.message});});},160);}
        function download(value){if(value.state==='ready')return Promise.resolve(value);return env.request('mediaDownload',{url:value.url}).then(function(v){publish(value,v);poll(value);return value;}).catch(function(e){publish(value,{state:'failed',error:e.message});throw e;});}
        function cancel(value){return env.request('mediaCancel',{url:value.url}).then(function(v){publish(value,v);poll(value);});}
        function prepare(media){var value=record(media);function wait(){if(value.account!==env.currentAccount())return Promise.reject(new Error('ACCOUNT_CHANGED'));if(value.state==='ready')return Promise.resolve();if(value.state==='failed')return Promise.reject(new Error(value.error||'MEDIA_FAILED'));if(value.state!=='downloading')return Promise.reject(new Error('MEDIA_CANCELLED'));return new Promise(function(resolve){setTimeout(resolve,180);}).then(wait);}return (value.state==='ready'||value.state==='downloading'?Promise.resolve():download(value)).then(wait);}
        function useMedia(media){var value=record(media),tick=R.useState(0)[1];R.useEffect(function(){
            var key=value.key;subscribers[key]=(subscribers[key]||[]).concat(tick);
            if(!value.requested){value.requested=true;env.request('mediaInfo',{url:value.url,kind:media.kind}).then(function(v){publish(value,v);if(v.state==='downloading')poll(value);}).catch(function(e){publish(value,{infoError:e.message});});}
            return function(){subscribers[key]=(subscribers[key]||[]).filter(function(fn){return fn!==tick;});};
        },[value.key]);return value;}
        function useMotion(){var reduced=R.useState(false);R.useEffect(function(){var live=true;N.AccessibilityInfo.isReduceMotionEnabled().then(function(v){if(live)reduced[1](v);}).catch(function(){});var s=N.AccessibilityInfo.addEventListener('reduceMotionChanged',reduced[1]);return function(){live=false;s.remove();};},[]);return reduced[0];}
        function NativeIcon(p){var owner=R.useRef('media-icon-'+(++serial)),ready=R.useState(0),mounted=R.useRef(true);
            R.useEffect(function(){mounted.current=true;return function(){mounted.current=false;env.request('mediaIcon',{owner:owner.current,detach:true}).catch(function(){});};},[]);
            R.useEffect(function(){if(!ready[0])return;env.request('mediaIcon',{owner:owner.current,name:p.name,progress:p.progress||0,circle:!!p.circle,pressed:!!p.pressed,animated:true}).catch(function(){});},[ready[0],p.name,p.progress,p.circle,p.pressed]);
            return h(N.View,{testID:owner.current,collapsable:false,pointerEvents:'none',onLayout:function(){ready[1](function(n){return n+1;});},style:{width:p.size||24,height:p.size||24}});
        }
        function Icon(p){if(['play','pause','cancel'].indexOf(p.name)>=0||p.animated)return h(NativeIcon,p);
            var uri=chatMediaIcons[p.name];return uri?h(N.Image,{source:{uri:uri},resizeMode:'contain',accessible:false,style:{width:p.size||24,height:p.size||24}}):null;
        }
        function IconButton(p){return h(N.Pressable,{ref:p.buttonRef,onPress:p.onPress,disabled:p.disabled,hitSlop:4,accessibilityRole:'button',accessibilityLabel:p.label,accessibilityState:{disabled:!!p.disabled},style:function(s){return Object.assign({width:48,height:48,alignItems:'center',justifyContent:'center',opacity:p.disabled?0.4:s.pressed?0.6:1},p.style||{});}},h(Icon,{name:p.name,size:p.size,progress:p.progress}));}
        function VideoBadge(p){var owner=R.useRef('media-icon-'+(++serial)).current,ready=R.useState(0),hitWidth=R.useState(0);
            R.useEffect(function(){return function(){env.request('mediaIcon',{owner:owner,detach:true}).catch(function(){});};},[owner]);
            R.useEffect(function(){if(!ready[0])return;var live=true;env.request('mediaIcon',{owner:owner,name:'videoBadge',ready:p.ready,downloading:p.downloading,
                time:p.time,size:p.size||0,received:p.received||0,muted:p.muted,width:p.width,unknownSize:t('chat_media_unknown_size')})
                .then(function(v){if(live)hitWidth[1](v.hitWidth||0);}).catch(function(){});return function(){live=false;};
            },[ready[0],p.ready,p.downloading,p.time,p.size,p.received,p.muted,p.width]);
            return h(R.Fragment,null,h(N.View,{testID:owner,collapsable:false,pointerEvents:'none',onLayout:function(){ready[1](function(n){return n+1;});},style:{position:'absolute',top:0,left:0,width:p.width,height:40}}),
                h(N.Pressable,{onPress:p.onPress,accessibilityRole:'button',accessibilityLabel:p.label,style:{position:'absolute',top:p.ready?4:8,left:p.ready?4:8,width:hitWidth[0],height:p.ready?16.5:30}}));
        }
        function Popup(p){var owner=R.useRef('media-menu-'+(++serial)).current,current=R.useRef(p);current.current=p;
            R.useEffect(function(){var live=true,event=N.DeviceEventEmitter.addListener('SteamNovaChatMediaMenu',function(raw){
                var e;try{e=typeof raw==='string'?JSON.parse(raw):raw;}catch(ignore){return;}if(!live||e.owner!==owner)return;
                var row=current.current.rows.find(function(r){return r.key===e.action;});current.current.close();if(row)row.run();
            });
                env.request('mediaMenu',{owner:owner,sourceTag:p.anchor.sourceTag,x:p.anchor.x||0,y:p.anchor.y||0,longPress:!!p.anchor.longPress,
                    rows:p.rows.map(function(r){return {key:r.key,label:r.label};})}).catch(function(e){if(live){current.current.close();N.Alert.alert(t('chat_attachment'),ui.errorText(e.message));}});
                return function(){live=false;event.remove();env.request('mediaMenu',{owner:owner,detach:true}).catch(function(){});};
            },[owner]);return null;
        }
        function Actions(p){var state=env.useStore(),forward=R.useState(false),search=R.useState(''),busy=R.useState(false),error=R.useState('');
            R.useEffect(function(){if(p.blocked)p.blocked(forward[0]);return function(){if(p.blocked)p.blocked(false);};},[forward[0]]);
            function done(){if(p.done)p.done();}function fail(e){done();if(e.message==='MEDIA_CANCELLED')return;N.Alert.alert(t('chat_attachment'),e.message==='MEDIA_DOWNLOADING'?t('chat_media_downloading'):ui.errorText(e.message));}
            function exportTo(destination){prepare(p.media).then(function(){return env.request('mediaExport',{url:p.media.url,destination:destination});}).then(function(){N.Alert.alert(t('chat_saved'));done();}).catch(fail);}
            function remove(){if(!p.item)return;N.Alert.alert(t('chat_delete_confirm_title'),p.target.peer?t('chat_media_delete_local'):t('chat_delete_confirm_body'),[
                {text:t('chat_cancel'),style:'cancel',onPress:done},{text:t('chat_delete'),style:'destructive',onPress:function(){env.deleteMedia(p.target,p.item).then(function(){if(p.removed)p.removed();else done();}).catch(fail);}}]);}
            function chooseForward(){forward[1](true);env.load('friends').catch(function(){});}
            var options=[{key:'copy',icon:'copy',label:t('chat_media_copy_link'),run:function(){env.request('copy',{text:p.media.url}).catch(fail);}},
                {key:'gallery',icon:'save',label:t('chat_media_save_gallery'),run:function(){exportTo('gallery');}},
                {key:'downloads',icon:'download',label:t('chat_media_save_downloads'),run:function(){exportTo('downloads');}},
                {key:'share',icon:'share',label:t('chat_media_share'),run:function(){prepare(p.media).then(function(){return env.request('mediaShare',{url:p.media.url});}).then(done).catch(fail);}},
                {key:'forward',icon:'forward',label:t('chat_media_forward'),run:chooseForward},
                {key:'delete',icon:'delete',label:t('chat_delete'),run:remove}];
            var rows=p.full?options.filter(function(o){return ['gallery','share','delete'].indexOf(o.key)>=0;}):options;
            var destinations=[],seen={};(state.sessions||[]).concat((state.friends||[]).map(function(f){return {key:'p:'+f.id,peer:f.id,profile:f};})).forEach(function(row){if(row.peer&&!seen[row.peer]){seen[row.peer]=true;destinations.push({key:'p:'+row.peer,peer:row.peer,title:env.profile(row.profile,row.peer).name});}});
            (state.groups||[]).forEach(function(g){(g.rooms||[]).forEach(function(r){destinations.push(Object.assign({},r,{title:g.title+' · '+r.title}));});});
            R.useEffect(function(){if(!p.command)return;var row=options.find(function(o){return o.key===p.command.action;});if(row)row.run();},[p.command&&p.command.id]);
            function send(destination){N.Alert.alert(t('chat_media_forward'),destination.title,[{text:t('chat_cancel'),style:'cancel'},{text:t('chat_media_send'),onPress:function(){busy[1](true);error[1]('');
                var draft=env.thread(destination).draft||'';
                env.send(destination,p.media.raw||(p.media.kind==='video'?'[video src='+p.media.url+' type=video/mp4]'+p.media.url+'[/video]':'[img]'+p.media.url+'[/img]')).then(function(sent){if(sent){forward[1](false);done();}else error[1](t('chat_delivery_unconfirmed_retry'));}).catch(function(e){error[1](ui.errorText(e.message));}).then(function(){if(draft&&!env.thread(destination).draft)env.editDraft(destination,draft);busy[1](false);});}}]);}
            return h(R.Fragment,null,p.children?p.children(chooseForward):null,p.open?h(Popup,{rows:rows,anchor:p.open,close:p.close}):null,
                forward[0]?h(N.Modal,{visible:true,animationType:'slide',onRequestClose:function(){forward[1](false);done();}},h(N.SafeAreaView,{style:{flex:1,backgroundColor:'#1b1e24'}},
                    h(N.View,{style:{flexDirection:'row',alignItems:'center'}},h(IconButton,{name:'back',label:t('chat_back'),onPress:function(){forward[1](false);done();}}),ui.label(t('chat_media_forward'),{fontSize:19})),
                    h(N.TextInput,{value:search[0],onChangeText:search[1],placeholder:t('chat_media_search'),placeholderTextColor:'#a7b0bc',style:{height:48,paddingHorizontal:16,color:'#fff'}}),
                    error[0]?ui.label(error[0],{padding:16,color:'#f1a7a0'}):null,
                    h(N.FlatList,{data:destinations.filter(function(d){return d.title.toLowerCase().indexOf(search[0].toLowerCase())>=0;}),keyExtractor:function(d){return d.key;},renderItem:function(row){return h(N.Pressable,{disabled:busy[0],onPress:function(){send(row.item);},style:{padding:16,minHeight:56,opacity:busy[0]?0.5:1}},ui.label(row.item.title));}}))):null);
        }
        var videoComponent;
        function Video(){if(videoComponent)return videoComponent;try{videoComponent=env.require(2551).Video;}catch(ignore){}return videoComponent;}
        function useForeground(){var active=R.useState(N.AppState.currentState==='active');R.useEffect(function(){var sub=N.AppState.addEventListener('change',function(s){active[1](s==='active');});return function(){sub.remove();};},[]);return active[0];}
        function Card(p){var media=p.media,value=useMedia(media),menu=R.useState(false),status=R.useState({}),paused=R.useState(false),failed=R.useState(false),muted=R.useState(true),blocked=R.useState(false),reduced=useMotion(),foreground=useForeground();
            R.useEffect(function(){if(!p.active)return;env.request('mediaStatus',{url:media.url}).then(function(v){publish(value,v);poll(value);}).catch(function(){});},[p.active,media.url]);
            var video=media.kind==='video'||value.mime&&value.mime.indexOf('video/')===0,ready=value.state==='ready',downloading=value.state==='downloading';
            var ratio=(value.width||media.width)/(value.height||media.height),dims=N.useWindowDimensions(),maxWidth=Math.min(360,dims.width-68),height=Math.min(420,maxWidth/(ratio||4/3)),width=Math.min(maxWidth,height*(ratio||4/3));
            var Player=video?Video():null,playing=!!Player&&ready&&p.active&&foreground&&!paused[0]&&!menu[0]&&!blocked[0]&&!failed[0];
            var frame=video?value.poster:(value.uri||media.poster||media.url),seconds=status[0].durationMillis?Math.floor(status[0].durationMillis/1000)-Math.floor((status[0].positionMillis||0)/1000):value.duration||0;
            R.useEffect(function(){if(video||media.width||value.width)return;var live=true;N.Image.getSize(media.url,function(w,ht){if(live)publish(value,{width:w,height:ht});},function(){});return function(){live=false;};},[media.url,video]);
            var cardTag=R.useRef('media-card-'+(++serial)).current;
            function open(){menu[1](false);p.open(Object.assign({},media,{kind:video?'video':'image',width:value.width||media.width,height:value.height||media.height,item:p.item,target:p.target,profile:p.profile,position:status[0].positionMillis||0,sourceTag:cardTag}));}
            function start(){if(!ready){download(value).catch(function(){});paused[1](false);}else {paused[1](!paused[0]);failed[1](false);}}
            return h(N.View,{testID:cardTag,collapsable:false,style:{width:width,height:height,maxWidth:'100%',borderRadius:16,overflow:'hidden',backgroundColor:'#11161c',marginVertical:3}},
                frame?h(N.Image,{source:{uri:frame},resizeMode:'cover',style:abs,fadeDuration:reduced?0:160}):null,
                video&&ready&&Player?h(Player,{source:{uri:value.uri},resizeMode:'cover',style:abs,shouldPlay:playing,isMuted:muted[0],isLooping:true,useNativeControls:false,progressUpdateIntervalMillis:200,
                    onPlaybackStatusUpdate:status[1],onError:function(){failed[1](true);},onReadyForDisplay:function(event){var n=event.naturalSize||{};if(n.width&&n.height)publish(value,{width:n.width,height:n.height});}}):null,
                h(N.Pressable,{onPress:open,onLongPress:function(e){menu[1]({sourceTag:cardTag,x:e.nativeEvent.locationX,y:e.nativeEvent.locationY,longPress:true});},accessibilityRole:'button',accessibilityLabel:video?t('chat_media_view_video'):t('chat_view_image'),style:abs}),
                video?h(N.View,{pointerEvents:'box-none',style:abs},
                    h(VideoBadge,{ready:ready,downloading:downloading,time:value.duration||status[0].durationMillis?duration(ready?seconds:value.duration):'--:--',
                        size:value.size,received:value.received,muted:muted[0],width:width,
                        onPress:function(){if(downloading)cancel(value).catch(function(){});else if(ready)muted[1](!muted[0]);else start();},
                        label:downloading?t('chat_media_cancel_download'):ready?(muted[0]?t('chat_media_unmute'):t('chat_media_mute')):t('chat_media_download')}),
                    h(N.Pressable,{pointerEvents:ready&&!paused[0]&&!failed[0]?'none':'auto',disabled:ready&&!paused[0]&&!failed[0],accessible:!ready||paused[0]||failed[0],accessibilityRole:'button',accessibilityLabel:t('chat_media_play'),
                        onPress:function(){if(downloading)open();else start();},style:{position:'absolute',left:'50%',top:'50%',marginLeft:-24,marginTop:-24,width:48,height:48}},
                        function(s){return h(NativeIcon,{name:!ready||paused[0]||failed[0]?'play':'none',size:48,circle:true,pressed:s.pressed});})):null,
                video?h(N.Pressable,{accessibilityRole:'button',accessibilityLabel:t('chat_message_actions'),onPress:function(){menu[1]({sourceTag:cardTag,y:8.1});},
                    style:{position:'absolute',top:4.1,right:-6,width:40,height:34}},
                    h(N.Image,{source:{uri:chatMediaIcons.videoMore},resizeMode:'contain',accessible:false,style:{position:'absolute',left:20,top:4,width:6,height:18}})):null,
                value.state==='failed'||failed[0]||video&&!Player?ui.label(t('chat_media_failed'),{position:'absolute',bottom:6,left:6,right:6,fontSize:12,color:'#fff',backgroundColor:'rgba(10,15,21,.8)',padding:5}):null,
                h(Actions,{media:media,item:p.item,target:p.target,open:menu[0],blocked:blocked[1],close:function(){menu[1](false);}}));
        }
        function Viewer(p){
            var initial=typeof p.media==='string'?{url:p.media,kind:'image'}:p.media,owner=R.useRef('media-viewer-'+(++serial)).current;
            var selected=R.useState(initial),command=R.useState(null),current=R.useRef(p),entries=R.useRef(null),alive=R.useRef(true);current.current=p;
            if(!entries.current){
                var list=[];if(initial.target)(env.thread(initial.target).messages||[]).slice().reverse().forEach(function(item){
                    if(item.deleted||item.locallyHidden)return;(parse(item.text)||[]).forEach(function(m){if(!m.url)return;
                        list.push(Object.assign({},m,{item:item,target:initial.target,profile:env.profile(item.profile,item.sender)}));});});
                if(!list.some(function(m){return m.url===initial.url;}))list.push(initial);
                var found=list.findIndex(function(m){return m.url===initial.url&&(m.item||{}).id===(initial.item||{}).id;});if(found<0)found=list.findIndex(function(m){return m.url===initial.url;});
                var start=Math.max(0,Math.min(found-100,list.length-200));list=list.slice(start,start+200);found-=start;list[found]=initial;entries.current={list:list,index:found};
            }
            function wire(m){var date=new Date(((m.item||{}).timestamp||0)*1000),parts=parse((m.item||{}).text||'')||[];return {url:m.url,kind:m.kind,width:m.width||0,height:m.height||0,position:m.position||0,sourceTag:m.sourceTag||'',caption:parts.filter(function(part){return part.text;}).map(function(part){return part.text;}).join('').trim(),
                sender:(m.profile||{}).name||t('chat_attachment'),date:(m.item||{}).timestamp?date.toLocaleString(moduleI18n.steamLanguage==='schinese'?'zh-CN':'en-US',{month:'long',day:'numeric',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}):''};}
            R.useEffect(function(){alive.current=true;var event=N.DeviceEventEmitter.addListener('SteamNovaChatMediaViewer',function(raw){var e;try{e=typeof raw==='string'?JSON.parse(raw):raw;}catch(ignore){return;}if(e.owner!==owner)return;
                if(e.type==='closed'){current.current.close();return;}if(e.type==='action'){selected[1](entries.current.list[e.index]);command[1]({id:Date.now(),action:e.action});}});
                var labels={};['chat_back','chat_media_forward','chat_message_actions','chat_media_save_gallery','chat_media_share','chat_delete','chat_media_failed'].forEach(function(k){labels[k]=t(k);});
                env.request('mediaViewer',{owner:owner,action:'open',items:entries.current.list.map(wire),index:entries.current.index,labels:labels}).catch(function(e){N.Alert.alert(t('chat_attachment'),ui.errorText(e.message));current.current.close();});
                return function(){alive.current=false;event.remove();env.request('mediaViewer',{owner:owner,action:'close'}).catch(function(){});};
            },[owner]);
            function done(){if(alive.current)env.request('mediaViewer',{owner:owner,action:'resume'}).catch(function(){});}
            return h(Actions,{media:selected[0],item:selected[0].item,target:selected[0].target,full:true,command:command[0],done:done,removed:function(){env.request('mediaViewer',{owner:owner,action:'close'}).catch(function(){});current.current.close();}});
        }
        env.mediaPresentation={parse:parse,Card:Card,Viewer:Viewer,duration:duration,size:size};return env.mediaPresentation;
    }
    """.trimIndent()
}
