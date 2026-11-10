package steam.nova.app.features.chat.ui

/** Steam web conversation metrics, rendered entirely with native RN views and native SVG paths. */
internal object SteamChatThreadAppearanceScript {
    fun source() = """
    function chatThreadAppearance(env,ui){
        if(env.threadAppearance)return env.threadAppearance;
        // Resolve only after entering a conversation, never while Metro modules are registering.
        var R=env.React,N=env.RN,h=R.createElement,S=env.require(1611),t=ui.t,effects=chatRoomEffects(env,ui);
        var icons={"voice_call":{"viewBox":"0 0 36 36","paths":[{"d":"M31.7,15.75C31.703,13.104 30.94,10.515 29.503,8.294C28.066,6.072 26.017,4.315 23.603,3.233C21.189,2.151 18.513,1.791 15.899,2.197C13.285,2.602 10.844,3.756 8.872,5.519C6.899,7.281 5.479,9.578 4.783,12.13C4.087,14.682 4.144,17.381 4.949,19.901C5.754,22.421 7.271,24.655 9.317,26.331C11.363,28.008 13.851,29.056 16.48,29.35V35.05L27.31,25.79C28.695,24.51 29.799,22.957 30.555,21.229C31.31,19.501 31.7,17.636 31.7,15.75ZM14.25,9.8C14.25,8.805 14.645,7.852 15.348,7.148C16.052,6.445 17.005,6.05 18,6.05C18.994,6.05 19.948,6.445 20.652,7.148C21.355,7.852 21.75,8.805 21.75,9.8V14.8C21.75,15.795 21.355,16.748 20.652,17.452C19.948,18.155 18.994,18.55 18,18.55C17.005,18.55 16.052,18.155 15.348,17.452C14.645,16.748 14.25,15.795 14.25,14.8V9.8ZM19.25,23.45V26.05H16.75V23.45C15.209,23.207 13.767,22.535 12.589,21.511C11.411,20.487 10.545,19.153 10.09,17.66L12.49,16.94C12.849,18.119 13.578,19.15 14.568,19.884C15.558,20.617 16.758,21.013 17.99,21.013C19.222,21.013 20.422,20.617 21.412,19.884C22.402,19.15 23.131,18.119 23.49,16.94L25.89,17.66C25.436,19.15 24.573,20.482 23.399,21.506C22.225,22.529 20.788,23.203 19.25,23.45Z","fill":"#898989","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"}]},"invite_to_chat":{"viewBox":"0 0 256 256","paths":[{"d":"M165.68,20.53c-17.25,0 -31.39,14.14 -31.39,31.39c0,17.25 14.14,31.39 31.39,31.39c17.25,0 31.39,-14.13 31.39,-31.39C197.06,34.67 182.93,20.53 165.68,20.53z","fill":"#3E4450","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"},{"d":"M165.68,93.12c25,0 45.33,20.34 45.33,45.33v52.31c0,3.85 -3.12,6.97 -6.97,6.97h-13.95v34.87c0,3.85 -3.12,6.97 -6.97,6.97h-34.87c-3.85,-0 -6.97,-3.12 -6.97,-6.97v-34.87h-13.95c-3.85,0 -6.97,-3.12 -6.97,-6.97v-52.31C120.34,113.46 140.68,93.12 165.68,93.12z","fill":"#3E4450","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"},{"d":"M14,128L101.5,128","fill":"none","stroke":"#5C6575","strokeWidth":22.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"},{"d":"M57.75,84.25L57.75,171.75","fill":"none","stroke":"#5C6575","strokeWidth":22.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"}]},"attachments":{"viewBox":"0 0 36 36","paths":[{"d":"M25.118,30.192C23.836,31.469 22.205,32.338 20.43,32.688C18.655,33.039 16.816,32.856 15.145,32.163C13.474,31.469 12.046,30.296 11.041,28.792C10.036,27.288 9.499,25.52 9.498,23.71L9.498,7.914C9.653,6.563 10.299,5.316 11.314,4.411C12.329,3.506 13.641,3.006 15,3.006C16.36,3.006 17.672,3.506 18.687,4.411C19.702,5.316 20.348,6.563 20.503,7.914L20.503,23.972H16.809L16.795,7.914C16.786,7.437 16.592,6.983 16.255,6.646C15.918,6.309 15.464,6.115 14.987,6.106C14.511,6.115 14.057,6.309 13.72,6.646C13.383,6.983 13.189,7.437 13.179,7.914L13.186,23.717C13.218,25.145 13.808,26.503 14.829,27.501C15.85,28.499 17.221,29.058 18.649,29.058C20.077,29.058 21.449,28.499 22.47,27.501C23.491,26.503 24.081,25.145 24.113,23.717V6.458L27.827,6.426L27.808,23.717C27.805,26.146 26.837,28.476 25.118,30.192Z","fill":"#898989","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"}]},"send_message":{"viewBox":"0 0 36 36","paths":[{"d":"M4.167,8.982C4.107,8.391 3.838,7.427 4.155,7.18C4.469,6.935 4.78,6.905 5.901,7.313L31.193,17.228C32.269,17.65 32.269,18.335 31.193,18.756L5.901,28.671C4.78,29.124 4.469,29.05 4.155,28.805C3.838,28.558 4.095,27.594 4.155,27.003L4.78,21.215C4.899,20.037 5.926,18.98 7.071,18.854L15.467,18.443C24.169,17.992 24.169,17.992 15.467,17.542L7.071,17.131C5.924,17.005 4.898,15.948 4.78,14.771L4.167,8.982Z","fill":"#5F5F5F","stroke":"none","strokeWidth":2.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"evenodd"}]},"emoticons":{"viewBox":"0 0 36 36","paths":[{"d":"M18,3C15.033,3 12.133,3.88 9.666,5.528C7.2,7.176 5.277,9.519 4.142,12.26C3.007,15.001 2.709,18.017 3.288,20.926C3.867,23.836 5.296,26.509 7.393,28.607C9.491,30.704 12.164,32.133 15.074,32.712C17.983,33.291 20.999,32.993 23.74,31.858C26.481,30.723 28.824,28.8 30.472,26.334C32.12,23.867 33,20.967 33,18C33,16.03 32.612,14.08 31.858,12.26C31.104,10.44 30,8.786 28.607,7.393C27.214,6.001 25.56,4.896 23.74,4.142C21.92,3.388 19.97,3 18,3ZM9,15C9,14.407 9.176,13.827 9.506,13.333C9.835,12.84 10.304,12.455 10.852,12.228C11.4,12.001 12.003,11.942 12.585,12.058C13.167,12.173 13.702,12.459 14.121,12.879C14.541,13.298 14.827,13.833 14.942,14.415C15.058,14.997 14.999,15.6 14.772,16.148C14.545,16.696 14.16,17.165 13.667,17.494C13.173,17.824 12.593,18 12,18C11.204,18 10.441,17.684 9.879,17.121C9.316,16.559 9,15.796 9,15ZM24,18C23.407,18 22.827,17.824 22.333,17.494C21.84,17.165 21.455,16.696 21.228,16.148C21.001,15.6 20.942,14.997 21.058,14.415C21.173,13.833 21.459,13.298 21.879,12.879C22.298,12.459 22.833,12.173 23.415,12.058C23.997,11.942 24.6,12.001 25.148,12.228C25.696,12.455 26.165,12.84 26.494,13.333C26.824,13.827 27,14.407 27,15C27,15.796 26.684,16.559 26.121,17.121C25.559,17.684 24.796,18 24,18ZM26.315,23.679C26.767,22.83 27,21.919 27,21H18H9C9,21.919 9.233,22.83 9.685,23.679C10.137,24.528 10.8,25.3 11.636,25.95C12.472,26.6 13.464,27.115 14.556,27.467C15.648,27.819 16.818,28 18,28C19.182,28 20.352,27.819 21.444,27.467C22.536,27.115 23.528,26.6 24.364,25.95C25.2,25.3 25.863,24.528 26.315,23.679Z","fill":"#898989","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"evenodd"}]},"recent_emoticons":{"viewBox":"0 0 24 24","paths":[{"d":"M15.999,15c-0.15,0 -0.303,-0.034 -0.446,-0.105l-4,-2A1.001,1.001 0,0 1,11 12L11,5a1,1 0,0 1,2 0v6.382l3.447,1.724A1,1 0,0 1,15.999 15zM12,24C5.383,24 0,18.617 0,12S5.383,0 12,0s12,5.383 12,12 -5.383,12 -12,12zM12,2C6.486,2 2,6.486 2,12s4.486,10 10,10 10,-4.486 10,-10S17.514,2 12,2z","fill":"#FFFFFF","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"}]},"stickers":{"viewBox":"0 0 36 36","paths":[{"d":"M8,4C5.791,4 4,5.791 4,8V27C4,29.209 5.791,31 8,31H13V20C13,16.134 16.134,13 20,13H31V8C31,5.791 29.209,4 27,4H8Z","fill":"#FFFFFF","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"},{"d":"M16,20C16,17.791 17.791,16 20,16H31L16,31V20Z","fill":"#FFFFFF","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"},{"d":"M29,24.063V25C29,25.267 28.974,25.528 28.924,25.781L30.886,26.169C30.961,25.789 31,25.398 31,25V24.063H29Z","fill":"#FFFFFF","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"},{"d":"M28.326,27.222C28.034,27.659 27.659,28.034 27.222,28.326L28.335,29.988C28.989,29.551 29.551,28.989 29.988,28.335L28.326,27.222Z","fill":"#FFFFFF","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"},{"d":"M21,29H22.188V31H19L21,29Z","fill":"#FFFFFF","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"},{"d":"M24.063,29H25C25.267,29 25.528,28.974 25.781,28.924L26.169,30.886C25.789,30.961 25.398,31 25,31H24.063V29Z","fill":"#FFFFFF","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"},{"d":"M29,22.188V21L31,19V22.188H29Z","fill":"#FFFFFF","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"}]},"chat_effects":{"viewBox":"0 0 36 36","paths":[{"d":"M14.716,7.688L17.248,15.534C17.564,16.61 18.45,17.433 19.526,17.813L27.373,20.344L19.526,22.875C18.45,23.191 17.627,24.077 17.248,25.153L14.716,33L12.185,25.153C11.869,24.077 10.983,23.255 9.907,22.875L2.06,20.344L9.907,17.813C10.983,17.496 11.805,16.61 12.185,15.534L14.716,7.688Z","fill":"#FFFFFF","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"},{"d":"M24.488,3L25.786,7.065C25.959,7.633 26.392,8.07 26.954,8.245L30.978,9.556L26.954,10.868C26.392,11.043 25.959,11.48 25.786,12.048L24.488,16.113L23.19,12.048C23.017,11.48 22.584,11.043 22.022,10.868L17.998,9.556L22.022,8.245C22.584,8.07 23.017,7.633 23.19,7.065L24.488,3Z","fill":"#FFFFFF","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"},{"d":"M8.118,3.938L8.767,5.992C8.853,6.254 9.07,6.473 9.329,6.56L11.363,7.216L9.329,7.871C9.07,7.959 8.853,8.177 8.767,8.44L8.118,10.494L7.469,8.44C7.382,8.177 7.166,7.959 6.906,7.871L4.873,7.216L6.906,6.56C7.166,6.473 7.382,6.254 7.469,5.992L8.118,3.938Z","fill":"#FFFFFF","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"},{"d":"M30.618,12.375L31.267,14.429C31.353,14.692 31.57,14.91 31.829,14.998L33.863,15.653L31.829,16.309C31.57,16.396 31.353,16.615 31.267,16.877L30.618,18.931L29.969,16.877C29.882,16.615 29.666,16.396 29.406,16.309L27.373,15.653L29.406,14.998C29.666,14.91 29.882,14.692 29.969,14.429L30.618,12.375Z","fill":"#FFFFFF","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"},{"d":"M25.93,24.563L26.579,26.617C26.666,26.879 26.882,27.098 27.142,27.185L29.176,27.841L27.142,28.496C26.882,28.584 26.666,28.802 26.579,29.065L25.93,31.119L25.281,29.065C25.195,28.802 24.978,28.584 24.719,28.496L22.685,27.841L24.719,27.185C24.978,27.098 25.195,26.879 25.281,26.617L25.93,24.563Z","fill":"#FFFFFF","stroke":"none","strokeWidth":1.0,"strokeLinecap":"butt","strokeLinejoin":"miter","fillRule":"nonzero"}]}};
        function text(value,style,props){
            var weight=(style||{}).fontWeight||'400',fonts=env.threadFonts||{};
            var family=weight==='700'?fonts.bold:weight==='500'||weight==='600'?fonts.medium:fonts.regular;
            return h(N.Text,Object.assign({style:Object.assign({fontSize:16,lineHeight:24,color:'#969aa1',includeFontPadding:false,
                fontFamily:family||'sans-serif',fontWeight:family?'400':weight},style||{},family?{fontFamily:family,fontWeight:'400'}:{})},props||{}),value);
        }
        function useFonts(){
            R.useEffect(function(){
                if((env.threadFonts&&env.threadFonts.regular&&env.threadFonts.medium&&env.threadFonts.bold)||env.threadFontsPending)return;
                env.threadFontsPending=true;
                env.request('chatFonts').then(function(value){env.threadFonts=value;env.emit();}).catch(function(){})
                    .then(function(){env.threadFontsPending=false;});
            },[]);
        }
        function Icon(props){var icon=icons[props.name];if(!icon)return null;
            return h(S.Svg,{width:24,height:24,viewBox:icon.viewBox,accessible:false},icon.paths.map(function(path,index){
                var p=Object.assign({key:index},path);
                if(props.color){if(p.fill&&p.fill!=='none')p.fill=props.color;if(p.stroke&&p.stroke!=='none')p.stroke=props.color;}
                return h(S.Path,p);
            }));
        }
        function IconButton(props){return h(N.Pressable,{onPress:props.onPress,onLongPress:props.onLongPress,disabled:props.disabled,
            accessibilityRole:'button',accessibilityLabel:props.title,accessibilityState:{disabled:!!props.disabled,expanded:props.expanded},
            style:function(s){return Object.assign({width:48,height:48,alignItems:'center',justifyContent:'center',
                backgroundColor:props.box?(props.send?'#24272b':'#2c3036'):'transparent',borderWidth:props.box?1:0,
                borderColor:'#1b1c20'},props.style||{},s.pressed&&props.box?{backgroundColor:'#3a3f47'}:{});}},
            function(s){return h(Icon,{name:props.name,color:props.disabled?'#5f5f5f':s.pressed?'#ffffff':props.color});});}
        var avatarItems={},avatarRequests={};
        function Avatar(props){var p=props.profile||{},size=props.size||23,account=env.currentAccount(),key=account+':'+p.id;
            var custom=typeof useProfileAvatar==='function'?useProfileAvatar(env.require,p.id):null;
            var extra=R.useState({key:key,value:avatarItems[key]||{}}),failed=R.useState('');
            R.useEffect(function(){var live=true;failed[1]('');extra[1]({key:key,value:avatarItems[key]||{}});
                if(!/^7656119\d{10}$/.test(p.id||''))return;
                if(!avatarRequests[key]&&!avatarItems[key])avatarRequests[key]=env.request('chatProfileAppearance',{id:p.id}).then(function(value){
                    avatarItems[key]=value;var keys=Object.keys(avatarItems);if(keys.length>256)delete avatarItems[keys[0]];return value;
                }).catch(function(){return {};}).then(function(value){delete avatarRequests[key];return value;});
                if(avatarRequests[key])avatarRequests[key].then(function(value){if(live&&env.currentAccount()===account)extra[1]({key:key,value:value});});
                return function(){live=false;};
            },[key]);
            var decoration=extra[0].key===key?extra[0].value:{},animated=decoration.animatedAvatar,still=p.avatar,frame=decoration.avatarFrame||p.avatarFrame;
            if(custom&&custom.avatar){animated=custom.avatar.image;still=custom.avatar.poster||p.avatar;}
            if(custom&&custom.frame)frame=custom.frame.image||custom.frame.poster;
            var avatar=animated&&failed[0]!==animated?animated:still;
            var Image=env.require(1750).Image;
            return h(N.View,{style:{width:size,height:size,backgroundColor:'#313943'}},
                avatar?h(Image,{source:{uri:avatar},autoplay:true,onError:function(){failed[1](avatar);},transition:0,style:{width:size,height:size},accessibilityIgnoresInvertColors:true}):
                    text((p.name||'?').slice(0,1),{fontSize:size>30?20:14,lineHeight:size,textAlign:'center'}),
                frame?h(Image,{pointerEvents:'none',source:{uri:frame},autoplay:true,transition:0,style:{position:'absolute',left:0,top:0,width:size,height:size,transform:size>30?[]:[{scale:1.2}]}}):null,
                size>30?h(N.View,{pointerEvents:'none',style:{position:'absolute',right:0,top:0,bottom:0,width:2,backgroundColor:p.playing||p.game?'#90ba3c':p.online?'#6dcff6':'#898989',opacity:p.personaState===3||p.personaState===4?0.5:1}}):null);
        }
        function Header(props){var state=env.useStore(),voice=chatVoiceUi(env),calling=voice.inThread(state.voice,props.target),insets=env.require(1486).useSafeAreaInsets(),p=props.profile||{},color=p.playing||p.game?'#90ba3c':p.online?'#6dcff6':'#898989';
            return h(N.View,{style:{paddingTop:insets.top||0,backgroundColor:'#1c232c'}},
                h(N.View,{style:{height:56,borderBottomWidth:1,borderBottomColor:'#363b43',flexDirection:'row',alignItems:'center',paddingLeft:14,paddingRight:4}},
                    h(N.View,{pointerEvents:'none',style:{position:'absolute',top:0,left:0,right:0,height:1,backgroundColor:'rgba(71,71,71,0.3)'}}),
                    h(N.Pressable,{onPress:props.info,onAccessibilityEscape:function(){props.navigation.goBack();},accessibilityRole:'button',
                        accessibilityLabel:t('chat_conversation_info'),style:{flex:1,minWidth:0,flexDirection:'row',alignItems:'center',minHeight:48}},
                        h(voice.HeaderIndicator,{target:props.target}),h(Avatar,{profile:p,size:42}),h(N.View,{style:{flex:1,minWidth:0,marginLeft:12,opacity:p.online===false||p.personaState===3||p.personaState===4?0.5:1}},
                            text(props.title,{color:color,fontWeight:'700'},{numberOfLines:1}),
                            props.target.peer?h(chatPresenceUi(env).Status,{profile:p,typing:props.typing?props.status:'',fontSize:12,color:color}):
                            props.status?text(props.status,{fontSize:12,lineHeight:16,color:color},{numberOfLines:1}):null)),
                    h(IconButton,{name:'voice_call',title:t(calling?'chat_voice_leave':'chat_voice_call'),color:calling?'#c8f1c2':undefined,onPress:props.voice}),
                    h(IconButton,{name:'invite_to_chat',title:t('chat_invite_to_chat'),onPress:props.invite})));
        }
        function Background(props){var size=R.useState({width:1,height:1}),w=size[0].width,ht=size[0].height,menu=props&&props.popup,id=menu?'chatPopupBackground':'chatHistoryBackground';
            return h(N.View,{pointerEvents:'none',onLayout:function(e){var box=e.nativeEvent.layout;size[1]({width:Math.max(1,box.width),height:Math.max(1,box.height)});},
                style:{position:'absolute',left:0,right:0,top:0,bottom:0,backgroundColor:menu?'#33363d':'#1c1d22'}},
                h(S.Svg,{width:'100%',height:'100%'},h(S.Defs,null,h(S.RadialGradient,{id:id,gradientUnits:'userSpaceOnUse',cx:w*0.5,cy:menu?0:ht*0.3,rx:w*0.5*Math.SQRT2,ry:ht*(menu?1:0.7)*Math.SQRT2},
                    h(S.Stop,{offset:'0%',stopColor:menu?'#3c4047':'#212329'}),h(S.Stop,{offset:'50%',stopColor:menu?'#3c4047':'#1e2025'}),h(S.Stop,{offset:'100%',stopColor:menu?'#33363d':'#1c1d22'}))),
                    h(S.Rect,{width:'100%',height:'100%',fill:'url(#'+id+')'})));
        }
        function Shadow(props){
            // Sample the web 12 px blur above the edge. Unlike Android elevation it casts upward on API 26 too.
            var inset=props.inset,id=inset?'chatInputInset':'chatComposerShadow';
            if(Number(N.Platform.Version)>=(inset?29:28))return null;
            return h(N.View,{pointerEvents:'none',style:inset?{position:'absolute',left:0,right:0,top:0,bottom:0}:{position:'absolute',left:0,right:0,top:-21,height:21}},
                h(S.Svg,{width:'100%',height:'100%'},h(S.Defs,null,
                    h(S.LinearGradient,{id:id,x1:0,y1:0,x2:0,y2:1},(inset?[[0,0.7],[0.3,0.25],[1,0]]:[[0,0],[0.33,0.006],[0.5,0.02],[0.66,0.04],[0.83,0.065],[1,0.092]]).map(function(stop,i){return h(S.Stop,{key:i,offset:stop[0],stopColor:'#000000',stopOpacity:stop[1]});})),
                    inset?h(S.LinearGradient,{id:id+'Side',x1:0,y1:0,x2:1,y2:0},h(S.Stop,{offset:0,stopColor:'#000000',stopOpacity:0.5}),h(S.Stop,{offset:1,stopColor:'#000000',stopOpacity:0})):null),
                    h(S.Rect,{width:'100%',height:inset?4:'100%',fill:'url(#'+id+')'}),
                    inset?h(S.Rect,{width:4,height:'100%',fill:'url(#'+id+'Side)'}):null));
        }
        function dayKey(timestamp){var d=new Date(timestamp*1000);return d.getFullYear()+'-'+d.getMonth()+'-'+d.getDate();}
        function sameRun(a,b){return !!a&&!!b&&a.sender===b.sender&&dayKey(a.timestamp)===dayKey(b.timestamp)&&Math.abs(a.timestamp-b.timestamp)<300;}
        function dateLabel(timestamp){var d=new Date(timestamp*1000);return d.toLocaleDateString(moduleI18n.locale,{year:'numeric',month:'long',day:'numeric',weekday:'long'});}
        function timeLabel(timestamp){var d=new Date(timestamp*1000),now=new Date(),clock=String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0');
            if(dayKey(timestamp)===dayKey(now.getTime()/1000))return clock;
            var days=(new Date(now.getFullYear(),now.getMonth(),now.getDate())-new Date(d.getFullYear(),d.getMonth(),d.getDate()))/86400000;
            return (days>0&&days<7?d.toLocaleDateString(moduleI18n.locale,{weekday:'long'}):dateLabel(timestamp))+' '+clock;}
        function DateDivider(props){return h(N.View,{style:{height:18,marginLeft:32,marginRight:16,marginBottom:6,flexDirection:'row',alignItems:'center'}},
            h(N.View,{style:{flex:1,height:1,backgroundColor:'rgba(79,82,87,0.5)'}}),
            text(dateLabel(props.timestamp),{fontSize:11,lineHeight:18,color:'#72808d',marginHorizontal:8,flexShrink:1},{numberOfLines:1}),
            h(N.View,{style:{flex:1,height:1,backgroundColor:'rgba(79,82,87,0.5)'}}));}
        function Message(props){var item=props.item,older=props.older,newer=props.newer,p=props.profile||{};
            var first=!sameRun(item,older),last=!sameRun(item,newer);
            return h(N.View,null,!older||dayKey(item.timestamp)!==dayKey(older.timestamp)?h(DateDivider,{timestamp:item.timestamp}):null,
                first?h(N.View,{style:{height:30,marginTop:12,flexDirection:'row',alignItems:'flex-start'}},
                    h(N.View,{style:{marginTop:4}},h(Avatar,{profile:p,size:23})),
                    h(N.View,{style:{flex:1,minWidth:0,marginLeft:11,flexDirection:'row',alignItems:'baseline'}},
                        text(p.name||item.sender||t('chat_system'),{color:p.game?'#90ba3c':p.online===false?'#898989':'#4f96b3',fontWeight:'500',flexShrink:1},{numberOfLines:1}),
                        text(timeLabel(item.timestamp),{color:'#555555',fontSize:11,lineHeight:16,letterSpacing:0.5,marginLeft:6,flexShrink:2},{numberOfLines:1}))):null,
                h(N.Pressable,{onLongPress:props.menu,delayLongPress:350,accessibilityLabel:t('chat_chat_message'),accessibilityActions:[{name:'activate',label:t('chat_message_actions')}],onAccessibilityAction:props.menu,
                    style:{paddingLeft:32,marginRight:14,paddingTop:first?10:7,paddingBottom:last?9:7,marginBottom:3}},props.children));
        }
        function Composer(props){var compact=N.useWindowDimensions().width<380,button=compact?42:48,motion=chatComposer(env,props);return h(N.View,{testID:motion.owner+'.panel',collapsable:false,onLayout:props.onLayout,style:{backgroundColor:'#22252b',borderTopLeftRadius:8,borderTopRightRadius:8,borderTopWidth:1,borderTopColor:'#15181c',paddingHorizontal:compact?6:10,paddingTop:8,paddingBottom:8+motion.bottomInset,flexDirection:'row',alignItems:'flex-end',
            boxShadow:Number(N.Platform.Version)>=28?[{offsetX:0,offsetY:-3,blurRadius:12,color:'rgba(0,0,0,0.1333)'}]:undefined}},
            h(Shadow),h(IconButton,{name:'attachments',title:t('chat_send_special_content'),box:true,style:{width:button,marginRight:8,marginBottom:2,borderRadius:8,overflow:'hidden'},onPress:props.attach,onLongPress:props.file,disabled:props.busy,expanded:props.attachOpen}),
            h(N.View,{style:{flex:1,minWidth:0,flexDirection:'row',alignItems:'flex-end',backgroundColor:'#1b1c20',borderRadius:8,overflow:'hidden',
                boxShadow:Number(N.Platform.Version)>=29?[{inset:true,offsetX:0,offsetY:0,blurRadius:4,color:'#000000'}]:undefined}},
                h(N.Animated.View,{style:{flex:1,minWidth:0,height:motion.height,overflow:'hidden'}},
                    h(N.TextInput,{ref:props.input,testID:motion.owner,collapsable:false,value:props.draft,onChangeText:props.edit,multiline:true,maxLength:2000,underlineColorAndroid:'transparent',allowFontScaling:false,
                        onFocus:props.close,onLayout:motion.layout,accessibilityLabel:t('chat_message_input'),
                        style:{position:'absolute',left:0,right:0,bottom:0,height:motion.fieldHeight,minHeight:44,borderRadius:8,fontFamily:(env.threadFonts||{}).regular||'sans-serif',fontSize:18,color:'#aaaaaa',
                            paddingLeft:compact?8:16,paddingRight:compact?44:56,paddingTop:9,paddingBottom:10,includeFontPadding:false,textAlignVertical:'bottom'}}),
                    h(IconButton,{name:'emoticons',title:t('chat_emotes'),expanded:props.pickerOpen,style:{position:'absolute',right:4,bottom:2,width:compact?40:48,borderRadius:8},onPress:props.emotes})),
                h(IconButton,{name:'send_message',title:t('chat_send'),send:true,box:true,style:{width:button,marginLeft:8,marginRight:2,marginBottom:2,borderRadius:8,overflow:'hidden'},onPress:props.send,disabled:!props.draft.trim()||props.busy}),h(Shadow,{inset:true})),
            h(chatVoiceUi(env).Dock,{target:props.target}));}
        function Popover(props){var height=R.useState(1),opacity=R.useRef(new N.Animated.Value(0)).current;
            R.useEffect(function(){var live=true,animation;
                function enter(reduced){if(!live)return;animation=N.Animated.timing(opacity,{toValue:1,duration:reduced?0:200,easing:N.Easing.bezier(0.25,0.1,0.25,1),useNativeDriver:true});animation.start();}
                N.AccessibilityInfo.isReduceMotionEnabled().then(enter).catch(function(){enter(false);});
                return function(){live=false;if(animation)animation.stop();};
            },[]);
            R.useEffect(function(){var sub=N.BackHandler.addEventListener('hardwareBackPress',function(){props.close();return true;});return function(){sub.remove();};},[props.close]);
            return h(N.View,{onLayout:function(e){height[1](e.nativeEvent.layout.height);},style:{position:'absolute',left:0,right:0,top:0,bottom:0,zIndex:10}},
                h(N.Pressable,{accessibilityLabel:t('chat_dismiss_popup'),onPress:props.close,style:{position:'absolute',left:0,right:0,top:0,bottom:0}}),
                h(N.Animated.View,{accessibilityViewIsModal:true,style:Object.assign({position:'absolute',left:10,bottom:props.bottom+3,maxWidth:'95%',maxHeight:Math.max(0,height[0]-props.bottom-7),backgroundColor:'#383c44',padding:4,opacity:opacity,
                    boxShadow:Number(N.Platform.Version)>=28?[{offsetX:0,offsetY:10,blurRadius:32,color:'rgba(0,0,0,0.67)'}]:undefined,elevation:Number(N.Platform.Version)<28?12:0},props.wide?{right:10}:{})},
                    h(Background,{popup:true}),props.wide?R.cloneElement(props.children,{maxHeight:Math.max(0,height[0]-props.bottom-15)}):props.children));
        }
        function AttachmentMenu(props){return h(N.View,{style:{alignSelf:'flex-start'}},[
            {name:t('chat_upload_an_image'),operation:'pickImage'},
            {name:t('chat_share_your_trade_offer_url'),operation:'trade'}].map(function(row){return h(N.Pressable,{key:row.operation,onPress:function(){if(row.operation==='trade')props.trade();else props.pick(row.operation);},accessibilityRole:'button',accessibilityLabel:row.name,
                style:function(s){return {minHeight:44,paddingHorizontal:16,paddingVertical:12,justifyContent:'center',backgroundColor:s.pressed?'#4b515c':'transparent'};}},
                text(row.name,{fontSize:15,lineHeight:20,color:'#9ca4a7'}));}));}
        function Catalog(props){var state=env.useStore(),tab=R.useState('recent'),query=R.useState(''),error=R.useState(''),width=R.useState(300);
            effects.useUpdate();
            function load(){error[1]('');env.load('catalog',true).catch(function(e){error[1](e.message);});}
            R.useEffect(load,[]);
            var kind=tab[0],catalog=state.catalog||{},recent=state.threadRecentEmotes||[],items=kind==='recent'?recent:(catalog[kind]||[]).map(function(item){return {kind:kind,item:item};});
            items=items.filter(function(entry){return entry.item.name.toLowerCase().indexOf(query[0].toLowerCase())>=0;});
            var columns=Math.max(1,Math.floor((width[0]+6)/62));
            function choose(entry){var item=entry.item,k=entry.kind;
                state.threadRecentEmotes=[entry].concat(recent.filter(function(r){return r.kind!==k||r.item.name!==item.name;})).slice(0,40);
                props.choose(k==='emoticons'?':'+item.name+':':(k==='stickers'?'/sticker ':'/roomeffect ')+item.name,k,item);}
            return h(N.View,{style:{height:Math.min(416,props.maxHeight===undefined?416:props.maxHeight),padding:12,gap:10,backgroundColor:'transparent'}},
                h(N.View,{style:{height:48,flexDirection:'row'}},['recent','emoticons','stickers','effects'].map(function(k,i){var title=[t('chat_recent_emoticons'),t('chat_emoticons'),t('chat_stickers'),t('chat_effects')][i];
                    return h(N.View,{key:k,style:{borderBottomWidth:2,borderBottomColor:kind===k?'#66c0f4':'transparent'}},
                        h(IconButton,{name:['recent_emoticons','emoticons','stickers','chat_effects'][i],title:title,color:kind===k?'#ffffff':'#898989',style:{height:46},onPress:function(){tab[1](k);}}));})),
                h(N.View,{onLayout:function(e){width[1](e.nativeEvent.layout.width);},style:{flex:1,minHeight:0,maxHeight:268}},
                    error[0]?h(ui.Button,{title:t('chat_could_not_load_retry'),onPress:load}):null,
                    h(N.FlatList,{style:{flex:1},data:items,numColumns:columns,key:columns,keyExtractor:function(entry){return entry.kind+':'+entry.item.name;},keyboardShouldPersistTaps:'always',
                        ListHeaderComponent:kind==='recent'?text(t('chat_recent_emoticons'),{fontSize:13,lineHeight:20,color:'#a7b0bc',marginBottom:6}):null,
                        ListEmptyComponent:text(t('chat_no_items_yet'),{fontSize:13,color:'#a7b0bc',paddingVertical:12}),
                        renderItem:function(row){var entry=row.item,item=entry.item,meta=effects.metadata(item.name)||{};return h(N.Pressable,{onPress:function(){choose(entry);},disabled:entry.kind==='effects'&&!item.infinite&&item.count<=0,accessibilityRole:'button',accessibilityLabel:meta.title||item.name,
                            style:function(s){return {width:(width[0]+6)/columns-6,minHeight:56,marginRight:6,marginBottom:6,justifyContent:'center',alignItems:'center',backgroundColor:s.pressed?'#3d4450':'transparent'};}},
                            entry.kind==='effects'?h(N.View,{style:{alignItems:'center'}},meta.icon?h(N.Image,{source:{uri:meta.icon},resizeMode:'contain',fadeDuration:0,style:{width:48,height:48}}):text(meta.title||item.name,{fontSize:12,lineHeight:16,textAlign:'center'}),text(item.infinite?'∞':'×'+item.count,{fontSize:11,lineHeight:16,color:'#a7b0bc'})):
                                h(N.Image,{source:{uri:'https://steamcommunity.com/economy/'+(entry.kind==='emoticons'?'emoticonlarge/':'sticker/')+encodeURIComponent(item.name)},resizeMode:'contain',fadeDuration:0,style:{width:entry.kind==='emoticons'?32:48,height:entry.kind==='emoticons'?32:48}}));}})),
                h(N.TextInput,{value:query[0],onChangeText:query[1],placeholder:t('chat_search'),accessibilityLabel:t('chat_search_items'),placeholderTextColor:'#7e8794',underlineColorAndroid:'transparent',
                    style:{height:56,minHeight:56,padding:16,fontSize:16,lineHeight:24,color:'#dfe3e6',backgroundColor:'#2b2c2f',fontFamily:(env.threadFonts||{}).regular||'sans-serif',includeFontPadding:false}}));
        }
        env.threadAppearance={text:text,useFonts:useFonts,Icon:Icon,Avatar:Avatar,Header:Header,Background:Background,Message:Message,DateDivider:DateDivider,dayKey:dayKey,sameRun:sameRun,
            Composer:Composer,Popover:Popover,AttachmentMenu:AttachmentMenu,Catalog:Catalog};return env.threadAppearance;
    }
""".trimIndent()
}
