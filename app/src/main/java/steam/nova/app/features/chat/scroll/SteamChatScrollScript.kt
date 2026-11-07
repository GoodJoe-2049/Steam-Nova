package steam.nova.app.features.chat.scroll

/** RN owns data/lifecycle; the native slot owns geometry, counter drawing and motion. */
internal object SteamChatScrollScript {
    fun source() = """
    function chatScrollControl(env,options){
        var R=env.React,N=env.RN;
        var owner=R.useRef('scroll-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2)).current;
        var live=R.useRef(null),mounted=R.useRef(false),timer=R.useRef(null),sending=R.useRef(false),queued=R.useRef(null);
        var frozen=R.useState(null),loading=R.useState(false),jumping=R.useRef(false),jumpTimer=R.useRef(null);
        var reduced=R.useState(false),foreground=R.useState(N.AppState.currentState==='active');
        var messages=frozen[0]||options.data.messages;
        var prefix='millennium.chat.scroll.'+owner,latest=messages[0];
        live.current=Object.assign({},options,{active:options.focused&&foreground[0],latest:latest?latest.id:'',count:env.unreadCount(options.target),reduced:reduced[0],loading:loading[0]});
        function payload(){var p=live.current;return {owner:owner,action:'update',active:p.active,ready:p.ready&&p.data.loaded,blocked:p.blocked,
            count:p.count,latest:encodeURIComponent(p.latest),loading:p.loading,reduced:p.reduced,restoreAway:!!(p.data.position&&!p.data.position.bottom)};}
        function update(){
            if(!mounted.current)return;queued.current=payload();if(sending.current)return;
            var value=queued.current;queued.current=null;sending.current=true;
            env.request('chatScroll',value).then(function(){clearTimeout(timer.current);}).catch(function(){
                if(mounted.current&&live.current.active){clearTimeout(timer.current);timer.current=setTimeout(update,250);}
            }).then(function(){sending.current=false;if(mounted.current&&queued.current)update();});
        }
        function toLatest(){
            var p=live.current;if(!mounted.current||!p.active||p.blocked||!p.ready||jumping.current)return;
            jumping.current=true;
            if(env.needsLatest(p.target)){
                loading[1](true);
                env.history(p.target,false).then(function(){if(env.needsLatest(p.target))return env.history(p.target,false);}).then(function(){
                    if(!mounted.current)return;loading[1](false);jumping.current=false;
                    if(!env.needsLatest(p.target))jumpTimer.current=setTimeout(toLatest,0);
                }).catch(function(){if(mounted.current){loading[1](false);jumping.current=false;}});
                return;
            }
            frozen[1](p.data.messages);
            // No synthetic read on tap. Only the destination's real visible rows acknowledge messages.
            env.request('chatScroll',{owner:owner,action:'latest',latest:encodeURIComponent((p.data.messages[0]||{}).id||'')}).catch(function(){
                if(mounted.current){jumping.current=false;frozen[1](null);
                    if(live.current.active){update();if(p.list.current)p.list.current.scrollToOffset({offset:0,animated:true});}}
            });
        }
        R.useEffect(function(){
            mounted.current=true;
            var subscription=N.DeviceEventEmitter.addListener('SteamNovaChatScroll',function(raw){
                if(!mounted.current)return;var event;try{event=typeof raw==='string'?JSON.parse(raw):raw;}catch(ignore){return;}
                if(!event||event.owner!==owner)return;var p=live.current;
                if(event.type==='settled'){jumping.current=false;frozen[1](null);return;}
                if(!p.active||p.blocked||!p.ready||!p.data.loaded)return;
                if(event.type==='jump')toLatest();
                if(event.type==='visible'&&!jumping.current){
                    var ids=(event.ids||[]).map(function(id){try{return decodeURIComponent(id);}catch(ignore){return '';}});
                    p.nearBottom.current=!!event.atBottom;
                    env.acknowledgeVisible(p.target,ids);
                    if(ids.length){p.data.position={id:ids[0],bottom:!!event.atBottom};env.saveThread(p.target,false);}
                }
            });
            var app=N.AppState.addEventListener('change',function(value){foreground[1](value==='active');});
            N.AccessibilityInfo.isReduceMotionEnabled().then(function(value){if(mounted.current)reduced[1](value);}).catch(function(){});
            var motion=N.AccessibilityInfo.addEventListener('reduceMotionChanged',reduced[1]);
            update();
            return function(){mounted.current=false;clearTimeout(timer.current);clearTimeout(jumpTimer.current);queued.current=null;subscription.remove();app.remove();motion.remove();
                env.request('chatScroll',{owner:owner,action:'detach'}).catch(function(){});};
        },[owner]);
        R.useEffect(function(){if(!live.current.active||live.current.blocked){jumping.current=false;frozen[1](null);loading[1](false);}update();},
            [options.focused,foreground[0],options.ready,options.data.loaded,options.blocked,live.current.count,live.current.latest,options.syncRevision,reduced[0],loading[0]]);
        return {latest:toLatest,messages:messages,viewportTag:prefix+'.viewport',listTag:prefix+'.list',rowTag:function(id){return prefix+'.row.'+encodeURIComponent(id);},
            slot:R.createElement(N.View,{testID:prefix+'.slot',collapsable:false,pointerEvents:'box-none',onLayout:update,
                style:{position:'absolute',right:1,bottom:4,width:56,height:64,overflow:'visible'}})};
    }
    """.trimIndent()
}
