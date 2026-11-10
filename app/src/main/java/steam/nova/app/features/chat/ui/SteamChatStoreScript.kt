package steam.nova.app.features.chat.ui

/** UI state is hydrated from account-separated native storage; Steam's in-process runtime owns the socket. */
internal object SteamChatStoreScript {
    fun source() = """
    var chatEnvironments=null;
    function chatEnvironment(require){
        if(chatEnvironments)return chatEnvironments;
        var React=require(115),RN=require(16),listeners=[],generation=0,running=false,pollTimer=null,refreshPromise=null,groupsPromise=null;
        var saveTimers={},typingSent={},visibilityVersion=0,state=fresh(''),effectListeners=[],visibleKey='',presence=chatPresenceState();
        var reads=chatReadReceipts({state:function(){return state;},request:request,emit:emit,running:function(){return running&&RN.AppState.currentState==='active';}});
        function roomEffect(message){if(message&&!message.deleted&&!message.pending&&!message.failed)effectListeners.slice().forEach(function(fn){try{fn(message);}catch(ignore){}});}
        function subscribeRoomEffects(fn){effectListeners.push(fn);return function(){effectListeners=effectListeners.filter(function(value){return value!==fn;});};}
        function fresh(id){return {account:id,sessions:[],groups:[],friends:null,catalog:null,preferences:{},background:{},self:null,profiles:{},nicknames:null,hydrated:false,groupsLoaded:false,refreshing:false,
            threads:{},typing:{},readState:{},voice:{phase:'idle'},openTarget:null,loading:false,error:'',groupsError:false,groupsLoading:false,connected:false,revision:0,syncRevision:0};}
        function emit(){state.revision++;listeners.slice().forEach(function(fn){fn(state.revision);});}
        function remember(value,live){if(value&&value.id){if(live!==false)presence.remember(value);state.profiles[value.id]=Object.assign({},state.profiles[value.id]||{},value);}}
        function profile(value,id){
            var key=id||(value||{}).id,p=Object.assign({},state.profiles[key]||{},value||{});
            var persona=p.personaName||p.name||key||'?',nickname=state.nicknames!==null?(state.nicknames[key]||''):(p.nickname||'');
            return presence.apply(Object.assign({},p,{id:key,personaName:persona,nickname:nickname,name:nickname||persona}));
        }
        function hydrateProfiles(value,live){
            if(live!==false)presence.accept(value.presenceState);
            if(value.nicknames&&typeof value.nicknames==='object')state.nicknames=value.nicknames;
            (value.sessions||[]).forEach(function(row){remember(row.profile,live);});
            remember(value.self,live);remember(value.peerProfile,live);
        }
        function request(operation,args,account){
            var id=account||state.account;if(!id)return Promise.reject(new Error('LOGIN_REQUIRED'));
            var payload=Object.assign({},args||{},{account:id,operation:operation,epoch:generation});
            return RN.Linking.openURL(chatBridgePrefix+encodeURIComponent(JSON.stringify(payload))).then(function(raw){
                var result=JSON.parse(raw);if(!result.ok)throw new Error(result.error);
                if(operation!=='pause'&&state.account!==id)throw new Error('ACCOUNT_CHANGED');return result.value;
            });
        }
        function targetFor(key){var p=key.split(':');return p[0]==='p'?{key:key,peer:p[1],kind:'private'}:{key:key,group:p[1],room:p[2],kind:'group'};}
        function account(id){
            if(state.account===id)return;
            var old=state.account;Object.keys(saveTimers).forEach(function(k){clearTimeout(saveTimers[k]);});saveTimers={};
            generation++;running=false;reads.reset();visibleKey='';clearTimeout(pollTimer);pollTimer=null;refreshPromise=null;groupsPromise=null;state=fresh(id);presence=chatPresenceState();
            if(chatEnvironments){chatEnvironments.voiceCurrent=state.voice;chatEnvironments.voiceExpanded=false;chatEnvironments.voiceThread='';}emit();
            if(old)request('pause',{},old).catch(function(){});
        }
        function thread(target){
            if(!state.threads[target.key])state.threads[target.key]={messages:[],more:false,loaded:false,busy:false,loadingOlder:false,error:'',draft:'',position:null,cached:false};
            return state.threads[target.key];
        }
        function merge(target,incoming){
            var data=thread(target),map={};data.messages.concat(incoming).forEach(function(m){if(data.hidden&&data.hidden[m.id])m=Object.assign({},m,{locallyHidden:true,text:'',reactions:[]});map[m.id]=m;});
            var list=Object.keys(map).map(function(k){return map[k];});
            list.filter(function(m){return m.pending||m.failed;}).forEach(function(local){
                var pending=list.filter(function(m){return (m.pending||m.failed)&&m.text===local.text;});
                var matches=list.filter(function(m){return !m.pending&&!m.failed&&m.sender===state.account&&m.text===local.text&&m.timestamp>=local.timestamp-5&&m.timestamp<=local.timestamp+120;});
                if(pending.length===1&&matches.length===1)local.possibleMatch=matches[0].id;
            });
            data.messages=Object.keys(map).map(function(k){return map[k];}).sort(function(a,b){return b.timestamp-a.timestamp||b.ordinal-a.ordinal;}).slice(0,1500);
        }
        function cachedThread(target){
            var data=thread(target);if(data.cached)return Promise.resolve();if(data.cachePromise)return data.cachePromise;
            var id=state.account;
            data.cachePromise=request('threadCache',target).then(function(value){
                if(id!==state.account)return;
                // In-flight UI edits win over a slower disk response.
                data.hidden=Object.assign({},value.hidden||{},data.hidden||{});merge(target,value.messages||[]);if(!data.edited)data.draft=value.draft||'';
                data.position=data.position||value.position||null;if(!data.loaded)data.more=!!value.more;
            }).catch(function(){}).then(function(){if(id===state.account){data.cached=true;data.cachePromise=null;emit();}});
            return data.cachePromise;
        }
        function saveThread(target,immediate){
            clearTimeout(saveTimers[target.key]);var id=state.account,data=thread(target);
            function save(){delete saveTimers[target.key];if(id===state.account)request('threadState',Object.assign({},target,{draft:data.draft,position:data.position||{}})).catch(function(){});}
            if(immediate)save();else saveTimers[target.key]=setTimeout(save,350);
        }
        function editDraft(target,text){var data=thread(target);data.draft=text;data.edited=true;saveThread(target,false);}
        function preference(target,patch){
            var id=state.account,value=Object.assign({},state.preferences[target.key]||{},patch);
            return request('preferences',Object.assign({},target,value)).then(function(prefs){if(id===state.account){state.preferences=prefs;emit();}});
        }
        function refreshGroups(){
            if(!state.account||!running)return Promise.resolve();if(groupsPromise)return groupsPromise;
            var epoch=generation;state.groupsLoading=!state.groupsLoaded;
            groupsPromise=request('groups').then(function(groups){
                if(epoch!==generation)return;state.groups=reads.mergeGroups(groups);state.groupsLoaded=true;state.groupsError=false;state.connected=true;
            }).catch(function(){if(epoch===generation)state.groupsError=true;})
                .then(function(){if(epoch===generation){state.groupsLoading=false;groupsPromise=null;emit();}});return groupsPromise;
        }
        function refresh(manual){
            if(!state.account||!running)return Promise.resolve();refreshGroups();
            if(manual===true){state.refreshing=true;emit();}if(refreshPromise)return refreshPromise;
            var epoch=generation;state.loading=!state.hydrated;emit();
            refreshPromise=request('snapshot',{refreshPresence:manual===true}).then(function(data){
                if(epoch!==generation)return;hydrateProfiles(data);reads.hydrate(data.readState);state.sessions=reads.mergeRows(data.sessions,state.sessions);state.hydrated=true;if(data.self&&(data.self.avatar||!state.self))state.self=data.self;state.error='';
            }).catch(function(e){if(epoch===generation)state.error=e.message;})
                .then(function(){if(epoch===generation){state.loading=false;state.refreshing=false;refreshPromise=null;emit();}});return refreshPromise;
        }
        function onMessage(m){
            var target=targetFor(m.key),data=thread(target),known=data.messages.some(function(old){return old.id===m.id;});remember(m.profile);merge(target,[m]);
            delete state.typing[m.key];if(known)return;roomEffect(m);
            if(target.peer){var row=state.sessions.find(function(s){return s.key===m.key;});
                if(!row){row={key:m.key,kind:'private',peer:target.peer,profile:m.sender===target.peer?m.profile:{id:target.peer,name:target.peer},unread:0};state.sessions.unshift(row);}
                if(!row.timestamp||m.timestamp>=row.timestamp){row.timestamp=m.timestamp;row.preview=m.text;}
                if(m.sender!==state.account&&!reads.isRead(m))row.unread=(row.unread||0)+1;
            }else state.groups.forEach(function(g){g.rooms.forEach(function(r){if(r.key===m.key){
                if(!r.timestamp||m.timestamp>=r.timestamp){r.timestamp=m.timestamp;r.preview=m.text;}
                if(m.sender!==state.account&&!reads.isRead(m))r.unread=(r.unread||0)+1;
            }});});
        }
        function reaction(e){
            var data=thread(targetFor(e.key));data.messages.forEach(function(m){
                if(m.timestamp!==e.timestamp||m.ordinal!==e.ordinal)return;
                var reactions=m.reactions||[],found=reactions.find(function(r){return r.type===e.reactionType&&r.name===e.name;});
                if(!found){found={type:e.reactionType,name:e.name,reactors:[],count:0};reactions.push(found);}
                var people=(found.reactors||[]).filter(function(id){return id!==e.reactor;});if(e.add)people.push(e.reactor);
                found.reactors=people;found.count=people.length;found.selected=people.indexOf(state.account)>=0;m.reactions=reactions.filter(function(r){return r.count>0;});
            });
        }
        function poll(epoch){
            if(epoch!==generation||!running||!state.account)return;
            request('events').then(function(events){
                if(epoch!==generation)return;var resync=false;
                events.forEach(function(e){
                    if(e.type==='presence')presence.accept(e);
                    if(e.presenceEpoch)presence.reset(e.presenceEpoch);
                    if(e.type==='voice')applyVoice(e.value);
                    if(e.type==='message')onMessage(e.message);
                    if(e.type==='read'||e.type==='readState')reads.event(e);
                    if(e.type==='typing')state.typing[e.key]=Date.now()+6000;
                    if(e.type==='reaction')reaction(e);
                    if(e.type==='modified')thread(targetFor(e.key)).messages.forEach(function(m){e.changes.forEach(function(change){if(m.timestamp===change.timestamp&&m.ordinal===change.ordinal)m.deleted=change.deleted;});});
                    if(e.type==='threadChanged')history(targetFor(e.key),false);
                    if(e.type==='openThread')state.openTarget=targetFor(e.key);
                    if(e.type==='nicknames')state.nicknames=e.names||{};
                    if(e.type==='connected'){state.connected=true;state.error='';reads.resume();}
                    if(e.type==='resync'||e.type==='connected')resync=true;
                    if(e.type==='disconnected'){state.connected=false;state.error='OFFLINE';}
                });
                Object.keys(state.typing).forEach(function(k){if(state.typing[k]<Date.now())delete state.typing[k];});
                if(resync)state.syncRevision++;emit();if(resync)refresh();else if(!state.connected)refreshGroups();
            }).catch(function(e){if(epoch===generation){state.error=e.message;state.connected=false;emit();}})
                .then(function(){if(epoch===generation&&running)pollTimer=setTimeout(function(){poll(epoch);},state.connected?1000:5000);});
        }
        function start(id){
            account(id);if(!id)return;if(running)return;running=true;var epoch=++generation;pollTimer=-1;
            reads.resume();
            request('visibility',{foreground:true,version:++visibilityVersion}).catch(function(){});
            request('cache').then(function(value){if(epoch!==generation)return;
                hydrateProfiles(value,false);reads.hydrate(value.readState);
                if(!state.sessions.length)state.sessions=reads.mergeRows(value.sessions,[]);if(!state.groups.length)state.groups=reads.mergeGroups(value.groups);
                if(Array.isArray(value.sessions))state.hydrated=true;if(Array.isArray(value.groups))state.groupsLoaded=true;
                if(value.self&&(value.self.avatar||!state.self))state.self=value.self;
                state.preferences=value.preferences||{};state.background=value.background||{};state.syncRevision++;emit();
            }).catch(function(){}).then(function(){if(epoch===generation){refresh();poll(epoch);}});
        }
        function pause(){
            Object.keys(saveTimers).forEach(function(key){saveThread(targetFor(key),true);});
            running=false;reads.pause();visibleKey='';generation++;refreshPromise=null;groupsPromise=null;clearTimeout(pollTimer);pollTimer=null;state.loading=false;state.refreshing=false;state.groupsLoading=false;
            Object.keys(state.threads).forEach(function(k){state.threads[k].busy=false;state.threads[k].loadingOlder=false;});
            if(state.account)request('visibility',{foreground:false,key:'',version:++visibilityVersion}).catch(function(){});
        }
        function visible(target){visibleKey=target?target.key:'';if(state.account)request('visibility',{foreground:running,key:visibleKey,version:++visibilityVersion}).catch(function(){});}
        function history(target,older){
            var data=thread(target);if(data.busy)return data.historyPromise||Promise.resolve();var epoch=generation;
            data.busy=true;data.loadingOlder=!!older;data.error='';emit();
            data.historyPromise=cachedThread(target).then(function(){
                if(epoch!==generation)throw new Error('STALE_REQUEST');
                var confirmed=data.messages.filter(function(m){return !m.pending&&!m.failed;});
                return request('history',Object.assign({},target,{before:older&&confirmed.length?confirmed[confirmed.length-1]:undefined}));
            }).then(function(result){if(epoch!==generation)return;hydrateProfiles(result);merge(target,result.messages);
                if(!older){var page=(result.messages||[]).filter(function(m){return !m.pending&&!m.failed;}).sort(function(a,b){return a.timestamp-b.timestamp||a.ordinal-b.ordinal;});
                    data.latestFloor=page.length?{timestamp:page[0].timestamp,ordinal:page[0].ordinal}:{timestamp:0,ordinal:0};}
                if(result.self&&(result.self.avatar||!state.self))state.self=result.self;if(older||!data.loaded)data.more=result.more;data.loaded=true;})
                .catch(function(e){if(epoch===generation)data.error=e.message;})
                .then(function(){if(epoch===generation){data.busy=false;data.loadingOlder=false;data.historyPromise=null;emit();}});
            return data.historyPromise;
        }
        function send(target,text){
            text=chatLinkPreviewOutgoing(text);
            var id=state.account,data=thread(target),local='local:'+Date.now()+':'+Math.random();
            var item={key:target.key,id:local,sender:id,profile:state.self||{id:id},text:text,timestamp:Math.floor(Date.now()/1000),ordinal:2147483647,pending:true};
            merge(target,[item]);editDraft(target,'');emit();
            return request('send',Object.assign({},target,{text:text,localId:local,localTime:item.timestamp})).then(function(message){
                if(id!==state.account)return;var known=data.messages.some(function(m){return m.id===message.id;});data.messages=data.messages.filter(function(m){return m.id!==local;});merge(target,[message]);state.error='';if(!known)roomEffect(message);emit();return message;
            }).catch(function(e){if(id===state.account){item.pending=false;item.failed=true;item.error=e.message;emit();}});
        }
        function discard(target,item){var id=state.account;return request('discardPending',Object.assign({},target,{localId:item.id})).then(function(){
            if(id===state.account){var data=thread(target);data.messages=data.messages.filter(function(m){return m.id!==item.id;});emit();}
        });}
        function acknowledge(target){
            if(!running||visibleKey!==target.key||RN.AppState.currentState!=='active')return;
            var data=thread(target),latest=data.messages.find(function(m){return !m.pending&&!m.failed;});
            if(latest&&data.loaded)reads.mark(target,latest.timestamp);
        }
        function acknowledgeVisible(target,ids){
            if(!running||visibleKey!==target.key||RN.AppState.currentState!=='active')return;
            var data=thread(target);if(!data.loaded||!data.latestFloor)return;
            var visible={};(ids||[]).forEach(function(id){visible[id]=true;});
            var message=data.messages.find(function(m){return visible[m.id]&&!m.pending&&!m.failed&&!m.system&&!m.deleted;});
            if(message)reads.mark(target,message);
        }
        function unreadCount(target){var row=state.sessions.find(function(r){return r.key===target.key;});
            state.groups.some(function(g){var found=(g.rooms||[]).find(function(r){return r.key===target.key;});if(found){row=found;return true;}return false;});
            return row?Math.max(0,reads.project(row).unread||0):0;
        }
        function needsLatest(target){var data=thread(target),timestamp=0;
            state.sessions.forEach(function(r){if(r.key===target.key)timestamp=r.timestamp||0;});
            state.groups.forEach(function(g){(g.rooms||[]).forEach(function(r){if(r.key===target.key)timestamp=r.timestamp||0;});});
            var latest=data.messages.find(function(m){return !m.pending&&!m.failed;});
            return !data.loaded||!!(timestamp&&(!latest||latest.timestamp<timestamp));
        }
        function load(kind,force){
            var id=state.account;if(state[kind]&&!force)return Promise.resolve(state[kind]);
            if(kind==='catalog'&&!state.catalog)request('catalogCache').then(function(value){if(id===state.account&&value.emoticons&&!state.catalog){state.catalog=value;emit();}}).catch(function(){});
            return request(kind,{refreshPresence:kind==='friends'&&force===true}).then(function(value){if(id===state.account){if(kind==='friends')value.forEach(remember);state[kind]=value;emit();}return value;});
        }
        function typing(target){if(!target.peer||Date.now()-(typingSent[target.key]||0)<4000)return;typingSent[target.key]=Date.now();request('typing',target).catch(function(){});}
        function deleteMedia(target,item){return request(target.peer?'deleteLocalMedia':'deleteMessage',Object.assign({},target,item)).then(function(){
            var data=thread(target);if(target.peer){data.hidden=data.hidden||{};data.hidden[item.id]=true;}
            data.messages.forEach(function(m){if(m.id===item.id){if(target.peer)m.locallyHidden=true;else m.deleted=true;m.text='';m.reactions=[];}});emit();
        });}
        function useStore(){var tick=React.useState(0)[1];React.useEffect(function(){listeners.push(tick);return function(){listeners=listeners.filter(function(fn){return fn!==tick;});};},[]);return state;}
        function applyVoice(value){if(value&&Number(value.revision||0)>=Number(state.voice.revision||0)){state.voice=value;
            (value.profiles||[]).forEach(remember);if(chatEnvironments)chatEnvironments.voiceCurrent=value;}}
        function voiceUpdate(value){applyVoice(value);emit();}
        chatEnvironments={currentAccount:function(){return state.account;},React:React,RN:RN,require:require,useStore:useStore,request:request,start:start,pause:pause,visible:visible,
            reset:account,refresh:refresh,history:history,thread:thread,send:send,discard:discard,deleteMedia:deleteMedia,acknowledge:acknowledge,acknowledgeVisible:acknowledgeVisible,unreadCount:unreadCount,needsLatest:needsLatest,load:load,emit:emit,subscribeRoomEffects:subscribeRoomEffects,
            preference:preference,saveThread:saveThread,editDraft:editDraft,typing:typing,targetFor:targetFor,profile:profile,voiceUpdate:voiceUpdate};return chatEnvironments;
    }
""".trimIndent()
}
