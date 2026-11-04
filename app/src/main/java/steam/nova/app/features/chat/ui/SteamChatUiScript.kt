package steam.nova.app.features.chat.ui

/** Native chat screens; the conversation appearance is scoped separately from the list. */
internal object SteamChatUiScript {
    fun source() = """
    function chatUi(require) {
        var env=chatEnvironment(require);
        if(env.ui)return env.ui;
        var R=env.React,N=env.RN,h=R.createElement;
        var t=moduleText;
        function colors(){
            var theme=require(1590).useTheme(),c=theme.color||{};
            return {bg:(c.background||{}).primary||'#1b1e24',panel:(c.background||{}).secondary||'#23262e',
                text:(c.foreground||{}).standard||'#dcdedf',muted:'#a7b0bc',blue:'#1a9fff',line:'#343944'};
        }
        function label(text,style,props){return h(N.Text,Object.assign({style:Object.assign({color:'#dcdedf',fontSize:15},style||{})},props||{}),text);}
        function errorText(code){
            if(code==='LOGIN_REQUIRED'||code==='AUTH_EXPIRED'||code==='ACCOUNT_CHANGED')return t('chat_error_sign_in');
            if(code==='IMAGE_TOO_LARGE'||code==='ATTACHMENT_TOO_LARGE')return t('chat_error_attachment_size');
            if(code==='REQUEST_REJECTED')return t('chat_error_rejected');
            if(code==='AVATAR_TOO_LARGE')return t('chat_error_avatar_size');
            if(code==='IMAGE_EXPIRED')return t('chat_error_select_image');
            if(code==='RATE_LIMITED')return t('chat_error_rate_limit');
            return t('chat_error_connection');
        }
        function Button(props){
            return h(N.Pressable,{onPress:props.onPress,disabled:props.disabled,accessibilityRole:'button',accessibilityLabel:props.title,
                accessibilityState:{disabled:!!props.disabled},style:function(s){return {minHeight:48,minWidth:48,paddingHorizontal:12,
                    justifyContent:'center',alignItems:'center',opacity:props.disabled ? 0.4 : s.pressed ? 0.65 : 1,backgroundColor:props.primary?'#1a6da0':'transparent',borderRadius:3};}},
                props.icon?h(props.icon,{size:24}):label(props.title,{color:props.primary?'#fff':'#66c0f4',fontSize:14}));
        }
        function Avatar(props){
            var profile=props.profile||{},size=props.size||44;
            var custom=typeof useProfileAvatar==='function'?useProfileAvatar(require,profile.id):null;
            var uri=custom&&custom.avatar?(custom.avatar.poster||custom.avatar.image):profile.avatar;
            return h(N.View,{style:{width:size,height:size,backgroundColor:'#313943',borderRadius:2,alignItems:'center',justifyContent:'center'}},
                uri?h(require(1750).Image,{source:{uri:uri},autoplay:false,transition:0,style:{width:size,height:size},accessibilityIgnoresInvertColors:true}):label((profile.name||'?').slice(0,1),{fontSize:20}));
        }
        function Banner(props){return h(N.View,{style:{paddingHorizontal:12,paddingVertical:4,backgroundColor:'#293443',flexDirection:'row',alignItems:'center'}},
            label(errorText(props.error),{flex:1,fontSize:13}),props.retry?h(Button,{title:t('chat_retry'),onPress:props.retry}):null);}
        function Empty(props){return h(N.View,{style:{padding:32,alignItems:'center'}},props.loading?h(N.ActivityIndicator,{color:'#66c0f4'}):null,
            label(props.loading?t('chat_loading'):props.title,{color:'#a7b0bc',paddingTop:16,textAlign:'center'}));}
        function allowed(){
            var settings=require(2125).useParentalSettings(),lock=require(2125).useParentalLock();
            var value=settings.data&&settings.data.settings,feature=require(962).k_EParentalFeature_k_EFeatureFriends;
            return !!value && !require(2117).BIsParentalFeatureBlocked(value,feature,lock.bLocked);
        }
        var components={t:t,label:label,Button:Button,Avatar:Avatar,colors:colors,errorText:errorText};
        var media=chatMedia(env,components),actions=chatActions(env,components,media),groupsUi=chatGroups(env,components,actions);
        var layout=chatThreadLayout(env,components);
        var effects=chatRoomEffects(env,components);
        var RichText=media.RichText;
        function Thread(props){
            var state=env.useStore(),target=props.target,data=env.thread(target),c=colors(),skin=chatThreadAppearance(env,components);
            skin.useFonts();
            var draft=R.useState(data.draft||''),picker=R.useState(false),attachment=R.useState(null),busy=R.useState(false),error=R.useState(''),viewer=R.useState(null);
            var menu=R.useState(null),info=R.useState(false),admin=R.useState(false),bottom=R.useState(true),spoiler=R.useState(false),progress=R.useState(0);
            var nearBottom=R.useRef(true),mounted=R.useRef(true),focused=require(1366).useIsFocused(),list=R.useRef(null),restored=R.useRef(false),jump=R.useRef(null),progressTimer=R.useRef(null),uncertain=R.useRef(false);
            var input=R.useRef(null),inputHeight=R.useState(52),attachMenu=R.useState(false),createGroup=R.useState(false),composerHeight=R.useState(69);
            function closePopup(){picker[1](false);attachMenu[1](false);}
            R.useEffect(function(){if(focused&&state.openTarget){var next=state.openTarget;state.openTarget=null;
                props.navigation.setParams({target:next,account:state.account});env.emit();}},[focused,state.openTarget]);
            R.useEffect(function(){mounted.current=true;env.history(target,false);return function(){mounted.current=false;clearTimeout(progressTimer.current);env.saveThread(target,true);env.visible(null);};},[target.key]);
            R.useEffect(function(){if(data.cached&&!data.edited)draft[1](data.draft||'');},[data.cached]);
            R.useEffect(function(){env.visible(focused?target:null);if(focused&&state.connected)env.history(target,false);return function(){env.visible(null);};},[focused,state.connected,state.syncRevision]);
            R.useEffect(function(){if(focused&&nearBottom.current)env.acknowledge(target);},[focused,data.messages.length]);
            function scrollTo(id){var index=data.messages.findIndex(function(m){return m.id===id;});if(index<0||!list.current)return;
                jump.current={index:index,attempts:0};list.current.scrollToIndex({index:index,animated:false,viewPosition:0.5});}
            R.useEffect(function(){if(restored.current||!data.cached||!data.messages.length)return;restored.current=true;
                if(data.position&&data.position.id&&!data.position.bottom){nearBottom.current=false;bottom[1](false);var timer=setTimeout(function(){scrollTo(data.position.id);},40);return function(){clearTimeout(timer);};}
            },[data.cached,data.messages.length]);
            var viewability=R.useRef(function(result){var item=result.viewableItems&&result.viewableItems[0];if(!restored.current||!item)return;
                data.position={id:item.item.id,bottom:nearBottom.current};env.saveThread(target,false);}).current;
            function edit(value){draft[1](value);env.editDraft(target,value);if(value.trim())env.typing(target);}
            function latest(){nearBottom.current=true;bottom[1](true);if(list.current)list.current.scrollToOffset({offset:0,animated:true});env.acknowledge(target);}
            function send(){if(busy[0]||!draft[0].trim())return;var text=draft[0];edit('');picker[1](false);env.send(target,text);latest();}
            function pick(operation){if(input.current)input.current.blur();N.Keyboard.dismiss();closePopup();busy[1](true);error[1]('');env.request(operation||'pickFile').then(function(file){if(mounted.current&&!file.cancelled){attachment[1](file);spoiler[1](false);uncertain.current=false;}})
                .catch(function(e){if(mounted.current)error[1](e.message);}).then(function(){if(mounted.current)busy[1](false);});}
            function shareTrade(){closePopup();busy[1](true);error[1]('');env.request('chatTradeUrl').then(function(value){
                if(mounted.current&&value.url){var previous=env.thread(target).draft||'';edit(previous+(previous?'\n':'')+value.url);}
            }).catch(function(e){if(mounted.current)error[1](e.message);}).then(function(){if(mounted.current)busy[1](false);});}
            function upload(){
                if(!attachment[0]||busy[0])return;
                if(uncertain.current){N.Alert.alert(t('chat_attachment_retry_title'),t('chat_attachment_retry_body'),[{text:t('chat_cancel'),style:'cancel'},{text:t('chat_attachment_send_again'),onPress:function(){uncertain.current=false;upload();}}]);return;}
                busy[1](true);error[1]('');progress[1](0);var selection=attachment[0].selection,finished=false;
                function pollProgress(){if(finished||!mounted.current)return;env.request('uploadProgress',{selection:selection}).then(function(value){if(mounted.current)progress[1](value.percent);}).catch(function(){})
                    .then(function(){if(!finished&&mounted.current)progressTimer.current=setTimeout(pollProgress,400);});}pollProgress();
                env.request('upload',Object.assign({},target,{selection:selection,spoiler:spoiler[0]})).then(function(){if(mounted.current){attachment[1](null);env.history(target,false);latest();}})
                    .catch(function(e){uncertain.current=true;if(mounted.current)error[1](e.message);}).then(function(){finished=true;clearTimeout(progressTimer.current);if(mounted.current)busy[1](false);});
            }
            function retry(item){env.history(target,false).then(function(){if(!data.messages.some(function(m){return m.id===item.id;}))return;
                N.Alert.alert(t('chat_message_retry_title'),t('chat_message_retry_body'),[
                    {text:t('chat_cancel'),style:'cancel'},{text:t('chat_message_send_again'),onPress:function(){env.discard(target,item).then(function(){env.send(target,item.text);}).catch(function(e){error[1](e.message);});}}]);});}
            var session=state.sessions.find(function(s){return s.key===target.key;}),friend=(state.friends||[]).find(function(f){return f.id===target.peer;});
            var group=state.groups.find(function(g){return g.id===target.group;}),room=group&&(group.rooms||[]).find(function(r){return r.room===target.room;});
            // History may fill an avatar missing from the list entry retained in this route.
            var peer=target.peer?env.profile(Object.assign({},target.profile||(session||{}).profile||friend,state.profiles[target.peer]),target.peer)
                :{name:(group||{}).title||target.groupTitle,avatar:(group||{}).avatar};
            var title=target.peer?peer.name:((room||{}).title||target.title||t('chat_group_conversation'));
            var status=state.typing[target.key]?t('chat_typing'):target.peer?(peer.game||''):(peer.name||'');
            return h(N.View,{style:{flex:1,backgroundColor:'#22252b'}},
                h(layout.Header,{target:target,navigation:props.navigation,title:title,profile:peer,status:status,voice:function(){closePopup();chatVoiceUi(env).start(target);},info:function(){closePopup();info[1](true);},
                    invite:function(){closePopup();N.Keyboard.dismiss();if(target.group)admin[1]('invite');else createGroup[1](true);}}),
                h(layout.KeyboardViewport,null,
                h(skin.Background),
                h(chatVoiceUi(env).Bar,{target:target,avatar:skin.Avatar}),
                state.error?h(Banner,{error:state.error,retry:env.refresh}):null,
                error[0]?h(Banner,{error:error[0]}):null,data.error?h(Banner,{error:data.error,retry:function(){env.history(target,false);}}):null,
                h(N.View,{style:{flex:1,overflow:'hidden'}},
                h(N.FlatList,{ref:list,style:{flex:1},data:data.messages,inverted:true,contentContainerStyle:{flexGrow:1,justifyContent:'flex-end',paddingHorizontal:10,paddingVertical:12},keyExtractor:function(m){return m.id;},keyboardShouldPersistTaps:'handled',
                    keyboardDismissMode:'on-drag',maintainVisibleContentPosition:{minIndexForVisible:0},initialNumToRender:24,windowSize:9,
                    onViewableItemsChanged:viewability,viewabilityConfig:{itemVisiblePercentThreshold:30,minimumViewTime:300},
                    onScroll:function(e){var near=e.nativeEvent.contentOffset.y<60;if(near!==nearBottom.current){nearBottom.current=near;bottom[1](near);}if(near&&focused)env.acknowledge(target);},scrollEventThrottle:120,
                    onScrollToIndexFailed:function(e){var pending=jump.current;if(!pending||pending.attempts++>3)return;if(list.current)list.current.scrollToOffset({offset:e.averageItemLength*e.index,animated:false});setTimeout(function(){if(mounted.current&&list.current)list.current.scrollToIndex({index:pending.index,animated:false,viewPosition:0.5});},120);},
                    onEndReachedThreshold:0.4,onEndReached:function(){if(data.loaded&&data.more&&!data.busy)env.history(target,true);},
                    ListEmptyComponent:!data.loaded&&!data.cached&&!data.error?h(N.View,{style:{padding:32,transform:[{scaleY:-1}]}},h(N.ActivityIndicator,{color:'#66c0f4'})):null,
                    ListHeaderComponent:data.messages.length&&skin.dayKey(data.messages[0].timestamp)!==skin.dayKey(Date.now()/1000)?h(skin.DateDivider,{timestamp:Date.now()/1000}):null,
                    ListFooterComponent:data.more?h(Button,{title:data.loadingOlder?t('chat_loading'):t('chat_load_earlier_messages'),disabled:data.loadingOlder,onPress:function(){env.history(target,true);}}):null,
                    renderItem:function(row){var item=row.item,self=item.sender===state.account;
                        var profile=env.profile(self?Object.assign({},state.self||{},state.profiles[item.sender]||{},item.profile||{}):item.profile,item.sender),effect=effects.parse(item.text);
                        return h(skin.Message,{item:item,older:data.messages[row.index+1],newer:data.messages[row.index-1],profile:profile,menu:function(){menu[1](item);}},
                            item.deleted?skin.text(t('chat_message_deleted'),{fontStyle:'italic'}):effect?h(effects.Card,{name:effect,sender:profile.name,targetKey:target.key,disabled:!!item.pending||!!item.failed}):h(RichText,{text:item.text,open:viewer[1],detail:true}),
                            !item.deleted&&(item.reactions||[]).some(function(r){return r.count>0;})?h(actions.Reactions,{target:target,item:item}):null,
                            item.pending?skin.text(t('chat_sending'),{fontSize:11,lineHeight:16,color:'#72808d'}):null,
                            item.failed?h(Button,{title:t('chat_delivery_unconfirmed_retry'),onPress:function(){retry(item);}}):null);}}),
                h(effects.Layer,{key:state.account+':'+target.key,target:target,focused:focused})),
                !bottom[0]?h(Button,{title:t('chat_jump_to_latest'),onPress:latest}):null,
                state.typing[target.key]?label(t('chat_typing'),{fontSize:12,color:c.blue,paddingHorizontal:14,paddingVertical:4}):null,
                attachment[0]?h(N.View,{style:{backgroundColor:c.panel,padding:10}},
                    h(N.View,{style:{flexDirection:'row',alignItems:'center'}},attachment[0].image?h(N.Image,{source:{uri:attachment[0].uri},style:{width:64,height:64},resizeMode:'contain'}):null,
                        label(attachment[0].name||t('chat_attachment'),{flex:1,padding:8},{numberOfLines:2}),
                        h(Button,{title:busy[0]?String(progress[0])+'%':t('chat_send_attachment'),primary:true,disabled:busy[0],onPress:upload}),
                        h(Button,{title:t('chat_cancel'),disabled:busy[0],onPress:function(){attachment[1](null);}})),
                    h(actions.SwitchRow,{title:t('chat_mark_as_spoiler'),value:spoiler[0],disabled:busy[0],onChange:spoiler[1]})):null,
                h(skin.Composer,{target:target,input:input,draft:draft[0],edit:edit,height:inputHeight[0],busy:busy[0],send:send,close:closePopup,pickerOpen:picker[0],attachOpen:attachMenu[0],
                    onLayout:function(e){composerHeight[1](e.nativeEvent.layout.height);},file:function(){pick('pickFile');},
                    resize:function(e){inputHeight[1](Math.max(52,Math.min(144,e.nativeEvent.contentSize.height)));},
                    attach:function(){if(input.current)input.current.blur();N.Keyboard.dismiss();picker[1](false);attachMenu[1](!attachMenu[0]);},
                    emotes:function(){if(input.current)input.current.blur();N.Keyboard.dismiss();attachMenu[1](false);picker[1](!picker[0]);}}),
                attachMenu[0]||picker[0]?h(skin.Popover,{bottom:composerHeight[0],wide:picker[0],close:closePopup},
                    picker[0]?h(skin.Catalog,{choose:function(code,kind){closePopup();if(kind==='effects'){
                        env.send(target,code).then(function(sent){if(sent)env.load('catalog',true).catch(function(){});});env.editDraft(target,draft[0]);latest();
                    }else edit(/^\/sticker /.test(code)?code:draft[0]+code);}}):h(skin.AttachmentMenu,{pick:pick,trade:shareTrade})):null,
                createGroup[0]?h(groupsUi.Create,{initialName:peer.name,invitees:target.peer?[target.peer]:[],close:function(){createGroup[1](false);},created:function(id){
                    createGroup[1](false);var g=state.groups.find(function(value){return value.id===id;});
                    if(g&&g.rooms&&g.rooms[0])props.navigation.setParams({target:g.rooms[0],account:state.account});}}):null,
                viewer[0]?h(media.Viewer,{uri:viewer[0],close:function(){viewer[1](null);}}):null,
                menu[0]?h(actions.MessageMenu,{target:target,item:menu[0],account:state.account,close:function(){menu[1](null);}}):null,
                info[0]?h(actions.Info,{target:target,close:function(){info[1](false);},jump:scrollTo,manage:function(){info[1](false);admin[1](true);}}):null,
                admin[0]?h(groupsUi.Admin,{group:target.group,room:target.room,initialTab:admin[0]==='invite'?'invite':'info',close:function(){admin[1](false);}}):null));
        }
        function ChatHome(props){
            var state=env.useStore(),c=colors(),section=R.useState('sessions'),group=R.useState(null),search=R.useState(''),error=R.useState('');
            var focused=require(1366).useIsFocused(),create=R.useState(false),manage=R.useState(false);
            function open(target){N.Keyboard.dismiss();props.navigation.navigate('MillenniumChatThread',{target:target,account:state.account});}
            R.useEffect(function(){if(focused&&state.openTarget){var next=state.openTarget;state.openTarget=null;open(next);env.emit();}},[focused,state.openTarget]);
            function back(){if(group[0]){group[1](null);return true;}return false;}
            R.useEffect(function(){var sub=N.BackHandler.addEventListener('hardwareBackPress',function(){return focused&&back();});return function(){sub.remove();};},[focused,group[0]]);
            function friends(){error[1]('');env.load('friends').catch(function(e){error[1](e.message);});}
            function notifications(){
                function save(value){state.background=value;env.emit();}
                function settings(){N.Alert.alert(t('chat_notification_permission_title'),t('chat_notification_permission_body'),[
                    {text:t('chat_cancel'),style:'cancel'},{text:t('chat_settings'),onPress:function(){N.Linking.openSettings().catch(function(){});}}]);}
                if(state.background.enabled&&state.background.permitted){
                    N.Alert.alert(t('chat_notification_disable_title'),'', [{text:t('chat_cancel'),style:'cancel'},
                        {text:t('chat_disable'),onPress:function(){env.request('backgroundDisable').then(save).catch(function(e){error[1](e.message);});}}]);return;
                }
                N.Alert.alert(t('chat_notification_enable_title'),t('chat_notification_background_body'),[
                    {text:t('chat_cancel'),style:'cancel'},{text:t('chat_enable'),onPress:function(){
                        var account=state.account;
                        var permission=N.Platform.OS==='android'&&Number(N.Platform.Version)>=33
                            ?N.PermissionsAndroid.request('android.permission.POST_NOTIFICATIONS'):Promise.resolve('granted');
                        permission.then(function(result){
                            if(env.currentAccount()!==account)return;
                            if(result!=='granted'){settings();return;}
                            return env.request('backgroundEnable').then(function(value){save(value);if(!value.permitted)settings();});
                        }).catch(function(e){error[1](e.message);});
                    }}]);
            }
            R.useEffect(function(){if(section[0]==='friends'&&!state.friends)friends();},[section[0]]);
            var selectedGroup=group[0]?(state.groups.find(function(g){return g.id===group[0].id;})||group[0]):null;
            var rows=section[0]==='groups'?(selectedGroup?selectedGroup.rooms:state.groups):section[0]==='friends'?(state.friends||[]):state.sessions.slice().sort(function(a,b){var ap=(state.preferences[a.key]||{}).pinned?1:0,bp=(state.preferences[b.key]||{}).pinned?1:0;return bp-ap||(b.timestamp||0)-(a.timestamp||0);});
            rows=rows.slice().sort(function(a,b){return ((state.preferences[b.key]||{}).pinned?1:0)-((state.preferences[a.key]||{}).pinned?1:0);});
            rows=rows.filter(function(row){var p=env.profile(row.profile||row,row.peer||row.id);return [row.title,p.name,p.personaName].join(' ').toLowerCase().indexOf(search[0].toLowerCase())>=0;});
            var heading=selectedGroup?selectedGroup.title:t('chat_title');
            var navigation=Object.create(props.navigation);
            Object.defineProperty(navigation,'canGoBack',{value:function(){return !!group[0]||props.navigation.canGoBack();}});
            Object.defineProperty(navigation,'goBack',{value:function(){if(!back())props.navigation.goBack();}});
            return h(N.View,{style:{flex:1,backgroundColor:c.bg}},h(require(2212).Header,{title:heading,navigation:navigation}),
                state.error?h(Banner,{error:state.error,retry:env.refresh}):null,
                create[0]?h(groupsUi.Create,{close:function(){create[1](false);},created:function(id){create[1](false);section[1]('groups');group[1](state.groups.find(function(g){return g.id===id;})||null);}}):null,
                manage[0]&&group[0]?h(groupsUi.Admin,{group:group[0].id,close:function(){manage[1](false);}}):null,
                h(N.View,{style:{flex:1}},
                    h(N.View,{style:{flexDirection:'row',backgroundColor:c.panel}},['sessions','groups','friends'].map(function(key,i){return h(N.Pressable,{key:key,
                        onPress:function(){section[1](key);group[1](null);search[1]('');},accessibilityRole:'tab',accessibilityState:{selected:section[0]===key},
                        style:{flex:1,minHeight:48,alignItems:'center',justifyContent:'center',borderBottomWidth:2,borderColor:section[0]===key?c.blue:'transparent'}},
                        label([t('chat_messages_tab'),t('chat_groups_tab'),t('chat_friends')][i],{color:section[0]===key?c.blue:c.text}));})),
                    h(N.View,{style:{flexDirection:'row',justifyContent:'space-between'}},h(Button,{title:t('chat_create_group'),onPress:function(){create[1](true);}}),
                        group[0]?h(Button,{title:t('chat_manage_group'),onPress:function(){manage[1](true);}}):h(Button,{
                            title:state.background.enabled&&state.background.permitted?t('chat_notification_background_on'):t('chat_notification_enable_title'),
                            onPress:notifications})),
                    h(N.TextInput,{value:search[0],onChangeText:search[1],placeholder:t('chat_search'),placeholderTextColor:c.muted,accessibilityLabel:t('chat_search_chats'),
                        style:{minHeight:48,margin:12,paddingHorizontal:12,color:c.text,fontSize:16,backgroundColor:c.panel,borderRadius:3}}),
                    error[0]?h(Banner,{error:error[0],retry:friends}):null,
                    section[0]==='groups'&&state.groupsError?h(Banner,{error:'REQUEST_FAILED',retry:env.refresh}):null,
                    h(N.FlatList,{data:rows,keyExtractor:function(row){return row.key||row.id;},keyboardShouldPersistTaps:'handled',refreshing:state.refreshing,
                        onRefresh:section[0]==='friends'?function(){state.friends=null;friends();}:function(){env.refresh(true);},
                        ListEmptyComponent:h(Empty,{loading:state.loading||(section[0]==='groups'&&state.groupsLoading)||(section[0]==='friends'&&!state.friends&&!error[0]),title:t('chat_no_conversations_yet')}),
                        renderItem:function(row){var item=row.item,profile=env.profile(item.profile||item,item.peer||item.id),title=item.title||profile.name||item.peer,unread=item.unread||0;
                            if(item.rooms)unread=item.rooms.reduce(function(n,r){return n+(r.unread||0);},0);
                            return h(N.Pressable,{onPress:function(){if(item.rooms){group[1](item);search[1]('');}else open(item.key?item:{key:'p:'+item.id,peer:item.id,kind:'private',profile:item});},
                                accessibilityRole:'button',accessibilityLabel:title,style:function(s){return {paddingHorizontal:16,paddingVertical:14,flexDirection:'row',alignItems:'center',gap:12,backgroundColor:s.pressed?c.panel:'transparent',borderBottomWidth:1,borderColor:c.line};}},
                                h(Avatar,{profile:profile}),h(N.View,{style:{flex:1}},label(title,{color:profile.online?'#66c0f4':c.text,fontWeight:'500'},{numberOfLines:1}),
                                    label(item.rooms?moduleChannelCount(item.rooms.length):(item.preview||profile.game||(profile.online?t('chat_online'):t('chat_offline'))),{color:c.muted,fontSize:13,marginTop:4},{numberOfLines:1})),
                                unread?label(String(Math.min(unread,99)),{color:'#fff',backgroundColor:'#1a6da0',paddingHorizontal:7,paddingVertical:3,borderRadius:10,fontSize:12}):null);}})));
        }
        // This source is injected directly into Hermes, without Metro/Babel class transforms.
        function ChatBoundary(props,context,updater){
            R.Component.call(this,props,context,updater);
            this.state={failed:false};
        }
        ChatBoundary.prototype=Object.create(R.Component.prototype);
        Object.defineProperty(ChatBoundary.prototype,'constructor',{value:ChatBoundary,writable:true,configurable:true});
        ChatBoundary.getDerivedStateFromError=function(){return {failed:true};};
        ChatBoundary.prototype.render=function(){
            var self=this;return this.state.failed?h(N.View,{style:{flex:1,paddingTop:32,backgroundColor:'#1b1e24'}},
                label(t('chat_title'),{paddingHorizontal:16,fontSize:20,fontWeight:'600'}),
                h(Empty,{title:t('chat_ui_unavailable')}),
                h(Button,{title:t('chat_retry'),onPress:function(){self.setState({failed:false});}}),
                h(Button,{title:t('chat_back'),onPress:function(){self.props.navigation.goBack();}})):this.props.children;
        };
        function ScreenContent(props){
            var auth=require(1136).useAuth(),canChat=allowed(),state=env.useStore(),c=colors();
            var isThread=props.route&&props.route.name==='MillenniumChatThread',params=(props.route||{}).params||{};
            if(!auth.bLoggedIn||!canChat||state.account!==auth.activeSteamID||(isThread&&params.account!==auth.activeSteamID))return h(N.View,{style:{flex:1,backgroundColor:c.bg}},
                h(require(2212).Header,{title:t('chat_title'),navigation:props.navigation}),h(Empty,{loading:!!auth.bLoggedIn&&canChat,title:auth.bLoggedIn?t('chat_unavailable'):t('chat_sign_in_required')}));
            return isThread&&params.target?h(Thread,{key:auth.activeSteamID+':'+params.target.key,target:params.target,navigation:props.navigation}):h(ChatHome,Object.assign({},props,{key:auth.activeSteamID}));
        }
        function Screen(props){
            // Include the auth/theme hooks and every header state inside the page boundary.
            // The fallback above uses only native primitives, so a broken host header cannot escape it.
            return h(ChatBoundary,{navigation:props.navigation},h(ScreenContent,props));
        }
        env.ui={Screen:Screen,allowed:allowed,t:t,colors:colors,label:label,layout:layout};
        return env.ui;
    }
""".trimIndent()
}
