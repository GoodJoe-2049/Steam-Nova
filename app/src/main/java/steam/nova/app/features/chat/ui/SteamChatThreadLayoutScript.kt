package steam.nova.app.features.chat.ui

/** Conversation navigation and a single owner of keyboard/bottom insets. */
internal object SteamChatThreadLayoutScript {
    fun source() = """
    function chatThreadLayout(env,components){
        if(env.threadLayout)return env.threadLayout;
        var R=env.React,N=env.RN,h=R.createElement;
        function ThreadHeader(props){return h(chatThreadAppearance(env,components).Header,props);}
        function KeyboardViewport(props){
            var focused=env.require(1366).useIsFocused();
            var owner=R.useRef('chat-ime-'+Math.random().toString(36).slice(2)+Date.now().toString(36)).current;
            R.useEffect(function(){
                if(!focused)return;
                env.request('chatIme',{enabled:true,owner:owner}).catch(function(){});
                return function(){env.request('chatIme',{enabled:false,owner:owner}).catch(function(){});};
            },[focused,owner]);
            R.useEffect(function(){
                function change(event){
                    if(event&&event.duration>0&&N.Keyboard.scheduleLayoutAnimation)N.Keyboard.scheduleLayoutAnimation(event);
                }
                var subscriptions=[N.Keyboard.addListener('keyboardWillShow',change),
                    N.Keyboard.addListener('keyboardWillHide',change),
                    N.Keyboard.addListener('keyboardDidShow',change),
                    N.Keyboard.addListener('keyboardDidHide',change)];
                return function(){subscriptions.forEach(function(subscription){subscription.remove();});};
            },[]);
            // The composer owns only the native bottom inset still overlapping this viewport.
            return h(N.KeyboardAvoidingView,{style:{flex:1},behavior:'padding',enabled:focused,keyboardVerticalOffset:0},
                h(N.View,{collapsable:false,style:{flex:1}},props.children));
        }
        function useTransitionOptions(){
            var reduced=R.useState(false);
            R.useEffect(function(){var alive=true;
                N.AccessibilityInfo.isReduceMotionEnabled().then(function(value){if(alive)reduced[1](value);}).catch(function(){});
                var sub=N.AccessibilityInfo.addEventListener('reduceMotionChanged',reduced[1]);
                return function(){alive=false;sub.remove();};
            },[]);
            return R.useMemo(function(){
                var ease=N.Easing.bezier(0.4,0,0.2,1),input=[],opening=[],closing=[];
                // Monica uses separate 300 ms opacity and 400 ms scale tweens.
                for(var i=0;i<=40;i++){var time=i/40;input.push(ease(time));opening.push(ease(Math.min(time*4/3,1)));closing.push(1-ease(Math.min((1-time)*4/3,1)));}
                function opacity(progress,isClosing){
                    if(reduced[0])return progress;
                    var enter=progress.interpolate({inputRange:input,outputRange:opening,extrapolate:'clamp'});
                    var leave=progress.interpolate({inputRange:input,outputRange:closing,extrapolate:'clamp'});
                    return N.Animated.add(N.Animated.multiply(enter,N.Animated.subtract(1,isClosing)),N.Animated.multiply(leave,isClosing));
                }
                return {headerShown:false,gestureEnabled:false,detachPreviousScreen:true,cardOverlayEnabled:false,cardShadowEnabled:false,
                    cardStyle:{backgroundColor:'#1b1e24'},transitionSpec:{
                        open:{animation:'timing',config:{duration:reduced[0]?120:400,easing:ease}},
                        close:{animation:'timing',config:{duration:reduced[0]?90:400,easing:ease}}},
                    cardStyleInterpolator:function(p){
                        var alpha=opacity(p.current.progress,p.closing),scale=p.current.progress.interpolate({inputRange:[0,1],outputRange:[0.9,1],extrapolate:'clamp'});
                        if(p.next){alpha=N.Animated.multiply(alpha,N.Animated.subtract(1,opacity(p.next.progress,p.closing)));
                            scale=N.Animated.multiply(scale,p.next.progress.interpolate({inputRange:[0,1],outputRange:[1,0.9],extrapolate:'clamp'}));}
                        return {cardStyle:{opacity:alpha,transform:reduced[0]?[]:[{scale:scale}]}};
                    }};
            },[reduced[0]]);
        }
        env.threadLayout={Header:ThreadHeader,KeyboardViewport:KeyboardViewport,useTransitionOptions:useTransitionOptions};
        return env.threadLayout;
    }
""".trimIndent()
}
