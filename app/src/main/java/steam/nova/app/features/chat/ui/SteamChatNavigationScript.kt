package steam.nova.app.features.chat.ui

internal object SteamChatNavigationScript {
    fun source() = """
    function chatNavigationRuntime(base,require,id) {
        var navigatorCache=new WeakMap(),rootNavigatorCache=new WeakMap();
        function ChatRoute(props){
            var env=chatEnvironment(require),ui=chatUi(require);
            return env.React.createElement(ui.Screen,props);
        }
        function ThreadRoute(props){
            var env=chatEnvironment(require),R=env.React,params=(props.route||{}).params||{};
            return R.createElement(R.Fragment,null,R.createElement(ChatRoute,props),
                R.createElement(chatVoiceOverlay(env).Host,{threadKey:(params.target||{}).key,account:params.account}));
        }
        function Coordinator(){
            var env=chatEnvironment(require),R=env.React,N=env.RN,auth=require(1136).useAuth(),canChat=chatUi(require).allowed();
            var account=auth.bReady&&auth.bLoggedIn&&canChat?auth.activeSteamID:'';
            R.useEffect(function(){
                env.reset(account);
                if(account&&N.AppState.currentState==='active')env.start(account);
                var subscription=N.AppState.addEventListener('change',function(value){if(value==='active')env.start(account);else env.pause();});
                return function(){subscription.remove();env.pause();};
            },[account]);
            return null;
        }
        function ChatTab(props){
            var env=chatEnvironment(require),R=env.React,N=env.RN,ui=chatUi(require),chatState=env.useStore();
            var state=props.state,active=state.routes[state.index].name==='MillenniumChat';
            var navigate=require(2127).useNavigateWithParentalCheck();
            R.useEffect(function(){if(chatState.openTarget)navigate(function(){props.navigation.navigate('MillenniumChat');},require(962).k_EParentalFeature_k_EFeatureFriends);},[chatState.openTarget]);
            return R.createElement(require(1836).Column,null,R.createElement(require(1836).Box,{
                flexDirection:'column',alignItems:'center',position:'relative',style:{paddingVertical:17},
                accessibilityRole:'tab',accessibilityLabel:ui.t('chat_title'),accessibilityState:{selected:active},
                hitSlop:{top:10,bottom:30},onPress:function(){
                    navigate(function(){props.navigation.navigate('MillenniumChat');},require(962).k_EParentalFeature_k_EFeatureFriends);
                }
            },active?R.createElement(N.View,{style:{position:'absolute',top:0,width:24,height:2,backgroundColor:'#1a9fff'}}):null,
                R.createElement(N.View,{style:{transform:[{scale:1.12}]}},R.createElement(require(1821).MenuIconChatApp,{size:'large',tone:active?'info':undefined}))));
        }
        function convert(original,type,props,key){
            if(id===1476&&props&&props.initialRouteName==='MainTabs'&&Array.isArray(props.children)){
                var mainTabs=props.children.find(function(child){return child&&child.props&&child.props.name==='MainTabs';});
                if(mainTabs){
                    var RootWrapper=rootNavigatorCache.get(type);
                    if(!RootWrapper){RootWrapper=function(p){
                        var R=require(115),options=chatUi(require).layout.useTransitionOptions();
                        var main=p.children.find(function(child){return child&&child.props&&child.props.name==='MainTabs';});
                        return R.createElement(type,Object.assign({},p,{children:p.children.concat([
                            R.createElement(main.type,{name:'MillenniumChatThread',component:ThreadRoute,options:options,key:'millennium-chat-thread'})])}));
                    };rootNavigatorCache.set(type,RootWrapper);}
                    return original(RootWrapper,props,key);
                }
            }
            if(id===1476&&props&&props.tabBar&&Array.isArray(props.children)){
                var notification=props.children.find(function(child){return child&&child.props&&child.props.name==='Notifications';});
                if(notification){
                    var Wrapper=navigatorCache.get(type);
                    if(!Wrapper){Wrapper=function(p){var env=chatEnvironment(require),R=env.React;
                        return R.createElement(R.Fragment,null,R.createElement(Coordinator),R.createElement(type,p),
                            R.createElement(chatVoiceOverlay(env).Host));
                    };navigatorCache.set(type,Wrapper);}
                    var children=props.children.concat([require(115).createElement(notification.type,{name:'MillenniumChat',component:ChatRoute,key:'millennium-chat'})]);
                    return original(Wrapper,Object.assign({},props,{children:children}),key);
                }
            }
            if(id===1892&&props&&props.tab==='notifications'&&props.navigation&&props.state)return original(ChatTab,props,key);
            if(id===2455&&props){
                if(props.icon&&props.icon.type===require(1821).MenuIconChatApp&&props.label===require(1450).Localize('#MainMenu_ChatLink'))return null;
                if(Array.isArray(props.children)){
                    var children=props.children,copy=null;
                    for(var i=1;i<children.length-1;i++){
                        if(children[i]===null&&children[i-1]&&children[i+1]&&children[i-1].type===require(1590).Divider&&children[i+1].type===require(1590).Divider){
                            copy=copy||children.slice();copy[i+1]=null;
                        }
                    }
                    if(copy)props=Object.assign({},props,{children:copy});
                }
            }
            return original(type,props,key);
        }
        return facade(facade(base,'jsx',function(type,props,key){return convert(base.jsx,type,props,key);}),
            'jsxs',function(type,props,key){return convert(base.jsxs,type,props,key);});
    }
    function chatStoreHeader(base,require){
        function StoreBell(props){
            var R=require(115),N=require(16),go=require(1892).useNavigateToTabBarItem(),notifications=require(1894).useSteamNotifications();
            var name=require(1450).Localize('#NotificationsScreen_Title');
            if(!props.bShow)return null;
            return R.createElement(require(1590).Box,{onPress:function(){go('notifications');},hitSlop:10,
                accessibilityRole:'button',accessibilityLabel:name,style:{minWidth:44,minHeight:44,alignItems:'center',justifyContent:'center'}},
                R.createElement(require(1833).IconBell2,{size:'large'}),notifications.nUnviewed>0?R.createElement(N.View,{style:{position:'absolute',right:3,top:4,width:8,height:8,borderRadius:4,backgroundColor:'#1a9fff'}}):null);
        }
        return facade(base,'HeaderMenuButton',function(props){
            var R=require(115);
            return R.createElement(/^https:\/\/store\.steampowered\.com(?:\/|$)/i.test(props.url||'')?StoreBell:base.HeaderMenuButton,props);
        });
    }
""".trimIndent()
}
