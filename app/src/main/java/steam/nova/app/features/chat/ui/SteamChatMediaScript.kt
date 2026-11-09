package steam.nova.app.features.chat.ui

internal object SteamChatMediaScript {
    fun source() = """
    function chatMedia(env,ui){
        var R=env.React,N=env.RN,h=R.createElement,t=ui.t,label=ui.label,Button=ui.Button,colors=ui.colors,presentation=chatMediaPresentation(env,ui),trades=chatTradeCards(env,ui);
        function safe(url){return /^https:\/\/(?:[a-z0-9-]+\.)*(?:steamusercontent\.com|steamcontent\.com|steamstatic\.com|akamaihd\.net|steamcommunity\.com)\//i.test(url)?url:null;}
        function openLink(url){if(/^https?:\/\/[^\s<>]+$/i.test(url))N.Linking.openURL(url).catch(function(){});}
        function attrs(raw){var result={},re=/([a-z_]+)=(?:"([^"]*)"|'([^']*)'|([^\s\]]+))/gi,m;while((m=re.exec(raw||''))!==null)result[m[1].toLowerCase()]=(m[2]||m[3]||m[4]||'').replace(/&amp;/g,'&');return result;}
        function MediaImage(props){
            var fail=R.useState(false),uri=safe(props.uri);if(!uri||fail[0])return label(t('chat_image_unavailable'),{color:'#a7b0bc'});
            return h(N.Pressable,{accessibilityRole:'button',accessibilityLabel:t('chat_view_image'),onPress:function(){props.open(uri);}},
                h(N.Image,{source:{uri:uri},resizeMode:'contain',onError:function(){fail[1](true);},style:{width:props.small?128:220,height:props.small?128:180,maxWidth:'100%'}}));
        }
        function Spoiler(props){var revealed=R.useState(false);return revealed[0]?h(RichText,props):
            h(N.Pressable,{onPress:function(){revealed[1](true);},accessibilityRole:'button',style:{backgroundColor:'#39414d',padding:18,borderRadius:4}},label(t('chat_spoiler_tap_to_reveal'),{color:'#b7bec8'}));}
        var gameMetadata={},gameRequests={},gameFailures={};
        function gameInfo(app,language){
            var key=language+':'+app;
            if(gameMetadata[key])return Promise.resolve(gameMetadata[key]);
            if(gameRequests[key])return gameRequests[key];
            if(gameFailures[key]&&Date.now()-gameFailures[key]<30000)return Promise.resolve(null);
            gameRequests[key]=env.request('gameInfo',{appId:app,language:language}).then(function(value){
                if(value&&value.appId===app){gameMetadata[key]=value;var keys=Object.keys(gameMetadata);if(keys.length>128)delete gameMetadata[keys[0]];return value;}return null;
            }).catch(function(){gameFailures[key]=Date.now();var keys=Object.keys(gameFailures);if(keys.length>128)delete gameFailures[keys[0]];return null;})
                .then(function(value){delete gameRequests[key];return value;});return gameRequests[key];
        }
        function Invite(props){
            var a=props.attributes||{},app=String(a.appid||a.app_id||a.gameid||a.game_id||''),lobby=a.lobbyid||a.lobby_id||a.lobby||'',inviter=a.steamid||a.steamid64||a.steam_id||a.steamid_inviter||a.inviter||'';
            var valid=/^[0-9]{1,10}$/.test(app)&&Number(app)>0&&Number(app)<=4294967295;
            if(valid)app=String(Number(app));
            var language=moduleI18n.steamLanguage,key=language+':'+app,info=R.useState(function(){return {key:key,value:gameMetadata[key]};}),failed=R.useState({key:key,index:0}),c=colors();
            R.useEffect(function(){
                var active=true;info[1]({key:key,value:gameMetadata[key]});failed[1]({key:key,index:0});
                if(valid)gameInfo(app,language).then(function(value){if(active)info[1]({key:key,value:value});});
                return function(){active=false;};
            },[key,valid]);
            var value=info[0].key===key?info[0].value:null,artwork=value&&Array.isArray(value.artwork)?value.artwork:[],index=failed[0].key===key?failed[0].index:0,uri=safe(artwork[index]||'');
            var fallback=(props.title||a.name||a.title||'').trim();
            if((moduleI18n.genericGameTitles.indexOf(fallback.toLowerCase())>=0||/^App(?: ID)? \d+$/i.test(fallback))||/steam:\/\//i.test(fallback))fallback='';
            var title=(value&&value.name)||fallback||t('chat_game_details_unavailable');
            var join=valid&&/^[0-9]{1,20}$/.test(lobby)&&(!inviter||/^7656119\d{10}$/.test(inviter));
            function openInvite(){N.Alert.alert(t('chat_game_invitation'),t('chat_game_invitation_confirm'),[
                {text:t('chat_cancel'),style:'cancel'},{text:t('chat_open'),onPress:function(){N.Linking.openURL('steam://joinlobby/'+app+'/'+lobby+(inviter?'/'+inviter:'')).catch(function(){});}}]);}
            return h(N.View,{style:{backgroundColor:c.panel||'#26374a',borderColor:c.line||'#343944',borderWidth:1,borderRadius:10,padding:12,width:320,maxWidth:'100%'}},
                h(join?N.Pressable:N.View,{onPress:join?openInvite:undefined,accessibilityRole:join?'button':undefined,accessibilityLabel:join?t('chat_open_game_invitation'):undefined,
                    style:{flexDirection:'row',alignItems:'center'}},
                    h(N.View,{style:{width:'48%',maxWidth:144,aspectRatio:1.6,flexShrink:0,borderRadius:6,overflow:'hidden',backgroundColor:c.bg||'#1b1e24',alignItems:'center',justifyContent:'center'}},
                        uri?h(N.Image,{source:{uri:uri},resizeMode:'contain',fadeDuration:0,accessibilityLabel:title,
                            onError:function(){failed[1]({key:key,index:index+1});},style:{width:'100%',height:'100%'}}):label(t('chat_game_artwork'),{color:c.muted||'#a7b0bc',fontSize:12,textAlign:'center',padding:6})),
                    h(N.View,{style:{flex:1,minWidth:0,marginLeft:16}},
                        label(t('chat_game_invitation'),{color:c.blue||'#66c0f4',fontSize:12,fontWeight:'500'}),
                        label(title,{fontSize:14,lineHeight:20,minHeight:40,fontWeight:'500',paddingTop:4},{numberOfLines:2}),
                        valid?label('App ID '+app,{fontSize:11,color:c.muted||'#a7b0bc',paddingTop:4}):null)),
                valid?h(N.Pressable,{onPress:function(){openLink('https://store.steampowered.com/app/'+app+'/');},accessibilityRole:'button',accessibilityLabel:t('chat_view_store'),
                    style:function(pressed){return {alignSelf:'flex-end',minHeight:48,minWidth:112,marginTop:6,justifyContent:'center',opacity:pressed.pressed?0.65:1};}},
                    h(N.View,{style:{minHeight:32,paddingVertical:6,paddingHorizontal:14,borderRadius:4,backgroundColor:'#1a6da0',alignItems:'center',justifyContent:'center'}},
                        label(t('chat_view_store'),{color:'#fff',fontSize:13}))):null);
        }
        function FileCard(props){var saving=R.useState(false),error=R.useState('');
            return h(N.View,{style:{padding:12,backgroundColor:'#29323f',borderRadius:4,maxWidth:'100%'}},
                label(props.title||t('chat_attachment'),{fontWeight:'500'}),
                h(N.View,{style:{flexDirection:'row'}},h(Button,{title:t('chat_open'),onPress:function(){openLink(props.url);}}),
                    safe(props.url)?h(Button,{title:saving[0]?t('chat_saving'):t('chat_save'),disabled:saving[0],onPress:function(){saving[1](true);error[1]('');env.request('saveMedia',{url:props.url}).then(function(){error[1](t('chat_saved'));}).catch(function(){error[1](t('chat_could_not_save'));}).then(function(){saving[1](false);});}}):null),
                error[0]?label(error[0],{fontSize:12,color:'#a7b0bc'}):null);
        }
        function RichText(props){
            var body=props.text||'',m;
            function richLabel(value,style,options){return props.detail?chatThreadAppearance(env,ui).text(value,Object.assign({},style||{},{fontSize:16,lineHeight:24,letterSpacing:0.5}),options):label(value,style,options);}
            if((props.depth||0)>8)return richLabel(body,{lineHeight:22},{selectable:true});
            if((m=/^\[spoiler(?:=[^\]]+)?\]([\s\S]*)\[\/spoiler\]$/i.exec(body.trim())))return h(Spoiler,Object.assign({},props,{text:m[1],depth:(props.depth||0)+1}));
            var tradeParts=!props.tradeParsed&&trades.parse(body,props);
            if(tradeParts)return h(N.View,{style:{minWidth:0}},tradeParts.map(function(part,index){return part.trade?
                h(trades.Card,{key:'trade'+index,trade:part.trade}):
                part.text.trim()?h(RichText,Object.assign({},props,{key:'tradeText'+index,text:part.text,tradeParsed:true})):null;}));
            var mediaParts=!props.mediaParsed&&presentation.parse(body);
            if(mediaParts)return h(N.View,{style:{minWidth:0}},mediaParts.map(function(part,index){return part.url?
                h(presentation.Card,{key:'media'+index,media:part,item:props.item,target:props.target,profile:props.profile,active:props.active,open:props.open}):
                part.text.trim()?h(RichText,Object.assign({},props,{key:'text'+index,text:part.text,mediaParsed:true})):null;}));
            if((m=/^\/(?:me)\s+([\s\S]+)/i.exec(body)))return richLabel(m[1],{fontStyle:'italic',color:'#b6c7da',lineHeight:22},{selectable:true});
            if((m=/^\/(sticker|roomeffect)\s+([^\s]+)\s*$/i.exec(body)))return m[1].toLowerCase()==='sticker'?
                h(MediaImage,{uri:'https://steamcommunity.com/economy/sticker/'+encodeURIComponent(m[2]),small:true,open:props.open}):
                label(t('chat_effect_prefix')+m[2],{color:'#66c0f4',fontStyle:'italic',paddingVertical:8});
            if((m=/^\[(sticker|roomeffect|emoticon)([^\]]*)\](?:([\s\S]*?)\[\/\1\])?\s*$/i.exec(body.trim()))){
                var a=attrs(m[2]),name=a.type||a.name||m[3]||'';name=name.replace(/^:|:$/g,'');
                if(m[1].toLowerCase()==='emoticon')body=':'+name+':';else return h(RichText,{text:'/'+m[1].toLowerCase()+' '+name,open:props.open,depth:(props.depth||0)+1,detail:props.detail});
            }
            if((m=/^\[(gameinvite|lobbyinvite)([^\]]*)\](?:([\s\S]*?)\[\/\1\])?\s*$/i.exec(body.trim())))return h(Invite,{attributes:attrs(m[2]),title:m[3]});
            if((m=/^steam:\/\/joinlobby\/(\d+)\/(\d+)\/(\d+)\/?$/i.exec(body.trim())))return h(Invite,{attributes:{appid:m[1],lobbyid:m[2],steamid:m[3]}});
            if((m=/^https?:\/\/store\.steampowered\.com\/app\/(\d+)(?:\/[^\s]*)?$/i.exec(body.trim())))return h(Invite,{attributes:{appid:m[1]},title:t('chat_steam_game')});
            if((m=/^\[steamstore([^\]]*)\]([\s\S]*?)\[\/steamstore\]$/i.exec(body.trim()))){var store=attrs(m[1]),app=store.appid||store.app_id||(/^\d+$/.test(m[2].trim())?m[2].trim():'');var link=/\/app\/(\d+)/.exec(m[2]);if(!app&&link)app=link[1];if(app)return h(Invite,{attributes:{appid:app},title:store.title||store.name||t('chat_steam_game')});}
            if((m=/^\[url=(?:"([^"]+)"|([^\]]+))\]([\s\S]*?)\[\/url\]$/i.exec(body.trim()))){var link=m[1]||m[2],store=/^https?:\/\/store\.steampowered\.com\/app\/(\d+)/i.exec(link);if(store)return h(Invite,{attributes:{appid:store[1]},title:m[3]});if(/\.(?:mp4|webm|mpg|mpeg|ogv|zip)(?:\?|$)/i.test(link))return h(FileCard,{url:link,title:m[3]});}
            if((m=/^\[(?:video|file|attachment)([^\]]*)\]([\s\S]*?)(?:\[\/(?:video|file|attachment)\])?$/i.exec(body.trim()))){
                var info=attrs(m[1]),url=info.src||info.url||m[2];if(/^https:\/\//i.test(url))return h(FileCard,{url:url,title:info.name||info.filename||t('chat_attachment')});
            }
            if((m=/^\[(tradeoffer|trade_offer|incomingtradeoffer|gift|giftreceived|giftnotification|friendinvite|friendrequest|groupinvite|claninvite|broadcastinvite|broadcastviewrequest|playtestinvite|remoteplayinvite|inventoryitem|itemnotification|newitem|eventnotification|commentnotification|marketnotification|invite|steam[_-][A-Za-z0-9_-]+)([^\]]*)\](?:([\s\S]*?)\[\/\1\])?\s*$/i.exec(body.trim()))){
                var info=attrs(m[2]),url=info.url||info.link;
                return h(N.View,{style:{padding:12,backgroundColor:'#29323f',borderLeftWidth:3,borderColor:'#66c0f4'}},
                    label(m[3]||info.title||info.name||t('chat_steam_notification'),{lineHeight:22},{selectable:true}),url?h(Button,{title:t('chat_view'),onPress:function(){openLink(url);}}):null);
            }
            body=body.replace(/\[img\s+([^\]]*)\](?:[^\[]*\[\/img\])?/gi,function(_,raw){var a=attrs(raw),url=a.src||a.thumbnail_src;return url?'[img]'+url+'[/img]':_;});
            body=body.replace(/\[emoticon(?:\s+[^\]]*)?\]([^\[]+)\[\/emoticon\]/gi,function(_,name){return ':'+name.replace(/^:|:$/g,'')+':';});
            if(props.detail)return detailBody(body,props);
            var parts=[],pattern=/\[img(?:[^\]]*)\](https:\/\/[^\s\[]+)\[\/img\]|\[(b|i|u|s)\]([\s\S]*?)\[\/\2\]|\[url=(?:"([^"]+)"|([^\]]+))\]([\s\S]*?)\[\/url\]|https?:\/\/[^\s<>"'\[\]]+|:[A-Za-z0-9_\-]{2,80}:/g,match,last=0;
            while((match=pattern.exec(body))!==null&&parts.length<100){
                if(match.index>last)parts.push(label(body.slice(last,match.index).replace(/\\\[/g,'['),{lineHeight:22},{key:'t'+last,selectable:true}));
                var token=match[0],image=match[1]&&safe(match[1]),key='p'+match.index;
                if(!image&&/^https:/.test(token)&&/(?:\/ugc\/|\.(?:png|jpe?g|gif|webp|avif)(?:\?|$))/i.test(token)&&! /\.(?:mp4|webm|mpg|mpeg|ogv|zip)(?:\?|$)/i.test(token))image=safe(token);
                if(image)parts.push(h(MediaImage,{key:key,uri:image,open:props.open}));
                else if(match[2])parts.push(label(match[3],{lineHeight:22,fontWeight:match[2]==='b'?'700':undefined,fontStyle:match[2]==='i'?'italic':undefined,textDecorationLine:match[2]==='u'?'underline':match[2]==='s'?'line-through':undefined},{key:key,selectable:true}));
                else if(match[4]||match[5]){var link=match[4]||match[5];parts.push(h(Button,{key:key,title:match[6],onPress:(function(url){return function(){openLink(url);};})(link)}));}
                else if(token[0]===':')parts.push(h(N.Image,{key:key,source:{uri:'https://steamcommunity.com/economy/emoticon/'+encodeURIComponent(token.slice(1,-1))},style:{width:24,height:24},accessibilityLabel:token}));
                else if(/\.(?:mp4|webm|mpg|mpeg|ogv|zip)(?:\?|$)/i.test(token))parts.push(h(FileCard,{key:key,url:token,title:t('chat_attachment')}));
                else parts.push(label(token,{color:'#66c0f4',lineHeight:22},{key:key,selectable:true,onPress:(function(url){return function(){openLink(url);};})(token)}));
                last=pattern.lastIndex;
            }
            if(last<body.length)parts.push(label(body.slice(last).replace(/\\\[/g,'['),{lineHeight:22},{key:'end',selectable:true}));
            return h(N.View,{style:{flexDirection:'row',flexWrap:'wrap',alignItems:'center',gap:3}},parts);
        }
        function detailBody(body,props){
            var skin=chatThreadAppearance(env,ui),blocks=[],inline=[],last=0,match;
            var pattern=/\[img(?:[^\]]*)\](https:\/\/[^\s\[]+)\[\/img\]|\[(b|i|u|s)\]([\s\S]*?)\[\/\2\]|\[url=(?:"([^"]+)"|([^\]]+))\]([\s\S]*?)\[\/url\]|https?:\/\/[^\s<>"'\[\]]+|:[A-Za-z0-9_\-]{2,80}:/g;
            function flush(){if(!inline.length)return;blocks.push(skin.text(inline,{letterSpacing:0.5},{key:'text'+blocks.length,selectable:true}));inline=[];}
            function plain(value){if(value)inline.push(value.replace(/\\\[/g,'['));}
            function link(value,url,key){inline.push(skin.text(value,{color:'#6dcff6',letterSpacing:0.5,textDecorationLine:'underline',textDecorationStyle:'dotted'},
                {key:key,accessibilityRole:'link',onPress:function(){openLink(url);}}));}
            while((match=pattern.exec(body))!==null&&blocks.length+inline.length<100){
                plain(body.slice(last,match.index));var token=match[0],key='p'+match.index,image=match[1]&&safe(match[1]);
                if(!image&&/^https:/.test(token)&&/(?:\/ugc\/|\.(?:png|jpe?g|gif|webp|avif)(?:\?|$))/i.test(token)&&! /\.(?:mp4|webm|mpg|mpeg|ogv|zip)(?:\?|$)/i.test(token))image=safe(token);
                if(image){flush();blocks.push(h(MediaImage,{key:key,uri:image,open:props.open}));}
                else if(match[2])inline.push(skin.text(match[3],{fontWeight:match[2]==='b'?'700':'400',fontStyle:match[2]==='i'?'italic':'normal',
                    textDecorationLine:match[2]==='u'?'underline':match[2]==='s'?'line-through':'none',letterSpacing:0.5},{key:key}));
                else if(match[4]||match[5])link(match[6],match[4]||match[5],key);
                else if(token[0]===':')inline.push(h(N.Image,{key:key,source:{uri:'https://steamcommunity.com/economy/emoticon/'+encodeURIComponent(token.slice(1,-1))},fadeDuration:0,style:{width:24,height:24},accessibilityLabel:token}));
                else if(/\.(?:mp4|webm|mpg|mpeg|ogv|zip)(?:\?|$)/i.test(token)){flush();blocks.push(h(FileCard,{key:key,url:token,title:t('chat_attachment')}));}
                else link(token,token,key);
                last=pattern.lastIndex;
            }
            plain(body.slice(last));flush();return h(N.View,{style:{minWidth:0}},blocks);
        }
        function Catalog(props){
            var state=env.useStore(),tab=R.useState('emoticons'),error=R.useState(''),query=R.useState('');
            function load(){env.load('catalog',true).catch(function(e){error[1](e.message);});}
            R.useEffect(load,[]);var kind=props.reactions&&tab[0]==='effects'?'emoticons':tab[0];
            var items=((state.catalog||{})[kind]||[]).filter(function(item){return item.name.toLowerCase().indexOf(query[0].toLowerCase())>=0;});
            return h(N.View,{style:{maxHeight:300,backgroundColor:'#23262e',borderTopWidth:1,borderColor:'#343944'}},
                h(N.View,{style:{flexDirection:'row'}},['emoticons','stickers'].concat(props.reactions?[]:['effects']).map(function(k,i){return h(Button,{key:k,title:[t('chat_emoticons'),t('chat_stickers'),t('chat_effects')][i],onPress:function(){tab[1](k);}});}),h(Button,{title:t('chat_close'),onPress:props.close})),
                h(N.TextInput,{value:query[0],onChangeText:query[1],placeholder:t('chat_search_items'),placeholderTextColor:'#a7b0bc',style:{minHeight:42,color:'#dcdedf',paddingHorizontal:12}}),
                error[0]?h(Button,{title:t('chat_could_not_load_retry'),onPress:load}):null,
                h(N.FlatList,{data:items,numColumns:kind==='effects'?2:5,key:kind,keyExtractor:function(item){return item.name;},keyboardShouldPersistTaps:'always',
                    ListEmptyComponent:label(t('chat_no_owned_items'),{padding:16,color:'#a7b0bc'}),
                    renderItem:function(row){var item=row.item;return h(N.Pressable,{style:{flex:1,minHeight:56,padding:8,alignItems:'center'},accessibilityRole:'button',accessibilityLabel:item.name,
                        onPress:function(){props.choose(kind==='emoticons'?':'+item.name+':':(kind==='stickers'?'/sticker ':'/roomeffect ')+item.name,kind,item);}},
                        kind==='effects'?label(item.name+(item.infinite?' ∞':' ×'+item.count),{fontSize:13}):h(N.Image,{source:{uri:'https://steamcommunity.com/economy/'+(kind==='emoticons'?'emoticonlarge/':'sticker/')+encodeURIComponent(item.name)},style:{width:40,height:40}}));}}));
        }
        return {RichText:RichText,Catalog:Catalog,Viewer:presentation.Viewer,MediaImage:MediaImage};
    }
""".trimIndent()
}
