package steam.nova.app.features.store.banner

import org.json.JSONObject

/** Wraps Steam's existing WebView without changing its URL, user agent or viewport. */
internal object SteamStoreBannerScript {
    fun source(): String {
        val page = JSONObject.quote(SteamStoreBannerPage.source())
        return $$"""
    function storeBannerWebRuntime(base,require){
        var R=require(115),N=require(16),useIsFocused=require(1366).useIsFocused,wrappers=new WeakMap();
        function script(active){return ';('+$$page+')('+JSON.stringify(active)+');true;';}
        function wrapper(Type){
            var cached=wrappers.get(Type);if(cached)return cached;
            var Wrapped=R.forwardRef(function(props,forwardedRef){
                var focused=useIsFocused(),local=R.useRef({ref:null,active:false});
                var session=local.current;
                session.active=focused&&N.AppState.currentState==='active';
                function inject(){if(session.ref)session.ref.injectJavaScript(script(session.active));}
                var setRef=R.useCallback(function(value){
                    session.ref=value;
                    if(typeof forwardedRef==='function')forwardedRef(value);else if(forwardedRef)forwardedRef.current=value;
                },[forwardedRef,session]);
                R.useEffect(function(){
                    session.active=focused&&N.AppState.currentState==='active';inject();
                    var subscription=N.AppState.addEventListener('change',function(state){session.active=focused&&state==='active';inject();});
                    return function(){subscription.remove();session.active=false;inject();};
                },[focused,session]);
                var changed=Object.assign({},props,{
                    ref:setRef,
                    injectedJavaScriptBeforeContentLoaded:script(session.active)+(props.injectedJavaScriptBeforeContentLoaded||''),
                    injectedJavaScript:(props.injectedJavaScript||'')+script(session.active),
                    onLoadEnd:function(event){if(props.onLoadEnd)props.onLoadEnd(event);inject();},
                    onNavigationStateChange:function(state){if(props.onNavigationStateChange)props.onNavigationStateChange(state);inject();}
                });
                return R.createElement(Type,changed);
            });
            wrappers.set(Type,Wrapped);return Wrapped;
        }
        function convert(original,type,props,key){
            if(props&&props.source&&typeof props.source.uri==='string'&&props.onMessage&&props.injectedJavaScriptBeforeContentLoaded)
                return original(wrapper(type),props,key);
            return original(type,props,key);
        }
        return facade(facade(base,'jsx',function(type,props,key){return convert(base.jsx,type,props,key);}),
            'jsxs',function(type,props,key){return convert(base.jsxs,type,props,key);});
    }
""".trimIndent()
    }
}
