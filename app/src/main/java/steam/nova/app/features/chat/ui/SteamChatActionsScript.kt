package steam.nova.app.features.chat.ui

internal object SteamChatActionsScript {
    fun source() = """
    function chatActions(env,ui,media){
        var R=env.React,N=env.RN,h=R.createElement,t=ui.t,label=ui.label,Button=ui.Button;
        function Panel(props){return h(N.Modal,{visible:true,animationType:'slide',onRequestClose:props.close},
            h(N.SafeAreaView,{style:{flex:1,backgroundColor:'#1b1e24'}},
                h(N.View,{style:{minHeight:52,flexDirection:'row',alignItems:'center',borderBottomWidth:1,borderColor:'#343944'}},h(Button,{title:t('chat_back'),onPress:props.close}),label(props.title,{fontSize:18,fontWeight:'600',flex:1,paddingRight:12})),props.children));}
        function SwitchRow(props){return h(N.View,{style:{paddingHorizontal:16,minHeight:54,flexDirection:'row',alignItems:'center',gap:12}},label(props.title,{flex:1}),
            h(N.Switch,{value:!!props.value,onValueChange:props.onChange,disabled:props.disabled,accessibilityLabel:props.title,trackColor:{false:'#46505d',true:'#1a6da0'},thumbColor:'#dcdedf'}));}
        function MessageMenu(props){
            var item=props.item,choose=R.useState(false),busy=R.useState(false),error=R.useState('');
            function execute(operation,args){busy[1](true);error[1]('');env.request(operation,Object.assign({},props.target,item,args||{})).then(function(){
                if(operation==='deleteMessage')item.deleted=true;env.history(props.target,false);props.close();
            }).catch(function(e){error[1](ui.errorText(e.message));}).then(function(){busy[1](false);});}
            function report(reason){N.Alert.alert(t('chat_report_confirm_title'),t('chat_report_confirm_body'),[
                {text:t('chat_cancel'),style:'cancel'},{text:t('chat_report'),onPress:function(){execute('report',{reason:reason});}}]);}
            return h(Panel,{title:t('chat_message_actions'),close:props.close},h(N.ScrollView,{keyboardShouldPersistTaps:'handled'},
                label(item.deleted?t('chat_message_deleted'):item.text,{padding:16,color:'#a7b0bc'},{numberOfLines:5}),error[0]?label(error[0],{padding:12,color:'#e8a49d'}):null,
                h(Button,{title:t('chat_copy'),disabled:busy[0]||item.deleted,onPress:function(){env.request('copy',{text:item.text}).then(props.close).catch(function(e){error[1](ui.errorText(e.message));});}}),
                item.failed?h(Button,{title:t('chat_dismiss_unconfirmed'),disabled:busy[0],onPress:function(){env.discard(props.target,item).then(props.close).catch(function(e){error[1](ui.errorText(e.message));});}}):null,
                !item.pending&&!item.failed&&!item.deleted?h(Button,{title:t('chat_add_a_reaction'),disabled:busy[0],onPress:function(){choose[1](!choose[0]);}}):null,
                choose[0]?h(media.Catalog,{reactions:true,close:function(){choose[1](false);},choose:function(code,kind,emote){execute('reaction',{name:emote.name,type:kind==='stickers'?2:1,add:true});}}):null,
                !item.pending&&!item.failed&&!item.deleted&&item.sender!==props.account&&!item.system? h(N.View,null,label(t('chat_report_reason'),{padding:16,fontWeight:'600'}),
                    [[3,t('chat_harassment')],[13,t('chat_scam')],[28,t('chat_spam')],[2,t('chat_other')]].map(function(row){return h(Button,{key:row[0],title:row[1],disabled:busy[0],onPress:function(){report(row[0]);}});})):null,
                props.target.group&&!item.pending&&!item.failed&&!item.deleted?h(Button,{title:t('chat_delete_group_message'),disabled:busy[0],onPress:function(){N.Alert.alert(t('chat_delete_confirm_title'),t('chat_delete_confirm_body'),[
                    {text:t('chat_cancel'),style:'cancel'},{text:t('chat_delete'),style:'destructive',onPress:function(){execute('deleteMessage');}}]);}}):null));
        }
        function Reactions(props){var error=R.useState(''),busy=R.useState(false);return h(N.View,null,
            h(N.View,{style:{flexDirection:'row',flexWrap:'wrap',gap:6,paddingTop:6}},(props.item.reactions||[]).filter(function(r){return r.count>0;}).map(function(r){return h(N.Pressable,{key:r.type+':'+r.name,disabled:busy[0],accessibilityRole:'button',accessibilityLabel:r.name+' '+r.count,
                onPress:function(){busy[1](true);env.request('reaction',Object.assign({},props.target,props.item,{name:r.name,type:r.type,add:!r.selected})).then(function(){env.history(props.target,false);}).catch(function(e){error[1](ui.errorText(e.message));}).then(function(){busy[1](false);});},
                style:{flexDirection:'row',alignItems:'center',gap:5,paddingHorizontal:7,paddingVertical:3,borderRadius:5,borderWidth:1,borderColor:r.selected?'#66c0f4':'#454d59',backgroundColor:r.selected?'#263f56':'#252d38'}},
                h(N.Image,{source:{uri:'https://steamcommunity.com/economy/'+(r.type===2?'sticker/':'emoticon/')+encodeURIComponent(r.name)},style:{width:22,height:22}}),label(String(r.count),{fontSize:12}));})),error[0]?label(error[0],{fontSize:12,color:'#e8a49d'}):null);}
        function Info(props){
            var state=env.useStore(),prefs=state.preferences[props.target.key]||{},query=R.useState(''),search=R.useState(false),error=R.useState(''),busy=R.useState(false);
            var target=props.target,data=env.thread(target),profile=env.profile(target.profile,target.peer),title=target.title||profile.name||target.peer;
            function preference(value){busy[1](true);env.preference(target,value).catch(function(e){error[1](ui.errorText(e.message));}).then(function(){busy[1](false);});}
            var results=data.messages.filter(function(m){return !m.deleted&&(m.text+' '+((m.profile||{}).name||m.sender)).toLowerCase().indexOf(query[0].toLowerCase())>=0;});
            return h(Panel,{title:search[0]?t('chat_search_messages'):t('chat_conversation_info'),close:function(){if(search[0])search[1](false);else props.close();}},
                search[0]?h(N.View,{style:{flex:1}},h(N.TextInput,{autoFocus:true,value:query[0],onChangeText:query[1],placeholder:t('chat_search_loaded_messages_or_senders'),placeholderTextColor:'#a7b0bc',style:{padding:16,color:'#dcdedf',fontSize:16,minHeight:52}}),
                    h(N.FlatList,{data:results,keyExtractor:function(m){return m.id;},ListEmptyComponent:label(t('chat_no_matching_messages'),{padding:24,color:'#a7b0bc'}),
                        ListFooterComponent:data.more?h(Button,{title:data.busy?t('chat_loading'):t('chat_load_earlier_history'),disabled:data.busy,onPress:function(){env.history(target,true);}}):null,
                        renderItem:function(row){return h(N.Pressable,{onPress:function(){props.jump(row.item.id);props.close();},style:{padding:16,borderBottomWidth:1,borderColor:'#343944'}},label((row.item.profile||{}).name||row.item.sender,{fontSize:12,color:'#66c0f4'}),label(row.item.text,{paddingTop:6},{numberOfLines:4}));}})):
                h(N.ScrollView,null,label(title,{padding:20,fontSize:22,fontWeight:'600'}),
                    target.peer?h(chatPresenceUi(env).Status,{profile:profile,fontSize:13,style:{paddingHorizontal:20}}):null,
                    error[0]?label(error[0],{padding:16,color:'#e8a49d'}):null,
                    h(SwitchRow,{title:t('chat_pin_conversation'),value:prefs.pinned,disabled:busy[0],onChange:function(value){preference({pinned:value});}}),
                    h(SwitchRow,{title:t('chat_mute_notifications'),value:prefs.muted,disabled:busy[0],onChange:function(value){preference({muted:value});}}),
                    h(Button,{title:t('chat_search_messages'),onPress:function(){search[1](true);}}),
                    target.peer?h(Button,{title:t('chat_view_steam_profile'),onPress:function(){N.Linking.openURL('https://steamcommunity.com/profiles/'+target.peer+'/').catch(function(){});}}):null,
                    target.group?h(Button,{title:t('chat_group_info_and_management'),onPress:props.manage}):null));
        }
        return {Panel:Panel,SwitchRow:SwitchRow,MessageMenu:MessageMenu,Reactions:Reactions,Info:Info};
    }
""".trimIndent()
}
