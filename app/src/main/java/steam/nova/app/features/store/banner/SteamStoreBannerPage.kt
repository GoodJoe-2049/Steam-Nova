package steam.nova.app.features.store.banner

/** Restores only the homepage takeover video; seasonal media stays Steam-owned. */
internal object SteamStoreBannerPage {
    fun source() = """
function(active){
    'use strict';
    function home(){return location.protocol==='https:'&&location.hostname==='store.steampowered.com'&&location.pathname==='/';}
    var previous=window.__steamNovaStoreBanner;
    if(!home()){if(previous)previous.dispose();return;}
    if(previous){previous.setActive(active);return;}
    var root,video,link,style,observer,domObserver,original,patched,homepage;
    var prepared=false,mounted=false,disposed=false,visible=false,failed=false,pending=false,hostActive=!!active;
    var idleTimer=null,cancelFrame=null,lastActive=Date.now(),cleanups=[];
    var narrow=matchMedia('(max-width: 500px)'),reduced=matchMedia('(prefers-reduced-motion: reduce)');
    function listen(target,event,fn){target.addEventListener(event,fn,{passive:true});cleanups.push(function(){target.removeEventListener(event,fn);});}
    function permitted(){
        return home()&&!reduced.matches&&!(window.GDynamicStore&&GDynamicStore.s_preferences&&GDynamicStore.s_preferences.disable_animated_marketing);
    }
    function mode(){return prepared&&narrow.matches&&permitted();}
    function clearStyle(){if(root)root.classList.remove('sn-store-banner','sn-store-banner-ready');}
    function syncStyle(){if(mode())root.classList.add('sn-store-banner');else clearStyle();}
    function canPlay(){return mounted&&mode()&&!failed&&hostActive&&!document.hidden&&visible&&Date.now()-lastActive<30000;}
    function pause(){
        if(cancelFrame){cancelFrame();cancelFrame=null;}
        if(video&&!video.paused)video.pause();
    }
    function fallback(){pending=false;failed=true;root.classList.remove('sn-store-banner-ready');pause();}
    function showFrame(){
        if(cancelFrame||root.classList.contains('sn-store-banner-ready'))return;
        function ready(){cancelFrame=null;if(!disposed&&canPlay())root.classList.add('sn-store-banner-ready');}
        // Reveal decoded video over the matching poster, never an empty video surface.
        if(typeof video.requestVideoFrameCallback==='function'){
            var frame=video.requestVideoFrameCallback(ready);
            cancelFrame=function(){video.cancelVideoFrameCallback(frame);};
        }else{
            var paint=requestAnimationFrame(ready);
            cancelFrame=function(){cancelAnimationFrame(paint);};
        }
    }
    function play(){
        syncStyle();
        if(!canPlay()){pause();return;}
        if(pending)return;
        if(!video.paused){showFrame();return;}
        video.muted=true;pending=true;
        try{
            var result=video.play();
            if(result&&result.then)result.then(function(){
                pending=false;
                if(!disposed&&(!hostActive||document.hidden||(narrow.matches&&!canPlay())))pause();
            },function(){
                pending=false;if(!disposed&&canPlay())fallback();
            });
            else pending=false;
        }catch(error){fallback();}
    }
    function refresh(){
        if(disposed)return;
        if(!home()){api.dispose();return;}
        if(!mounted){mount();return;}
        syncStyle();
        if(narrow.matches)play();
        else if(!hostActive||document.hidden)pause();
        else if(visible&&Date.now()-lastActive<30000)original.call(homepage);
    }
    function activity(){
        lastActive=Date.now();
        clearTimeout(idleTimer);
        idleTimer=setTimeout(function(){if(narrow.matches)pause();},30000);
        // Retry a browser autoplay denial on the next real interaction.
        if(video&&!video.error)failed=false;
        refresh();
    }
    function prepareLayout(){
        if(!style){
            if(!document.documentElement)return;
            style=document.createElement('style');
            // Reserve the final geometry before GHomepage or video data is ready.
            // Keep Steam's desktop poster beneath the video, with the same crop.
            style.textContent=`
@media (max-width:500px){
html.responsive .sn-store-banner #takeover_videos{display:block!important;pointer-events:none;}
html.responsive .sn-store-banner #home_video_desktop{display:block!important;height:50vw!important;width:auto!important;max-width:none!important;opacity:0!important;}
html.responsive .sn-store-banner.sn-store-banner-ready #home_video_desktop{opacity:1!important;}
html.responsive .sn-store-banner .page_background_holder{display:block!important;height:50vw!important;background-size:auto 100%,cover!important;background-position:center top!important;opacity:1!important;}
html.responsive .sn-store-banner .page_background_holder_mobile{display:none!important;}
html.responsive .sn-store-banner .home_page_takeover_link .home_page_takeover_sizer{height:50vw!important;}
}`;
            (document.head||document.documentElement).appendChild(style);
        }
        root=document.querySelector('.home_page_body_ctn.has_takeover');
        var container=root&&root.querySelector('#takeover_videos');
        video=container&&container.querySelector('#home_video_desktop');
        link=root&&root.querySelector('.home_page_takeover_link .home_page_takeover_sizer');
        // Keep an official mobile video, when supplied. Never invent a media URL.
        prepared=!!(video&&!container.querySelector('#home_video_mobile')&&
            (video.getAttribute('src')||video.querySelector('source[src]'))&&root.querySelector('.page_background_holder'));
        syncStyle();
    }
    function mount(){
        if(disposed||mounted||!home())return;
        prepareLayout();
        homepage=window.GHomepage;
        if(!prepared||!link||!homepage||typeof homepage.PlayTakeoverVideo!=='function')return;
        mounted=true;original=homepage.PlayTakeoverVideo;
        if(domObserver)domObserver.disconnect();
        patched=function(){
            if(!home()){api.dispose();return original.apply(this,arguments);}
            if(narrow.matches){play();return;}
            return original.apply(this,arguments);
        };
        homepage.PlayTakeoverVideo=patched;
        listen(video,'playing',function(){
            pending=false;
            if(disposed)return;
            if(canPlay())showFrame();else if(narrow.matches)pause();
        });
        listen(video,'error',fallback);
        listen(video,'emptied',function(){pause();pending=false;failed=false;root.classList.remove('sn-store-banner-ready');refresh();});
        observer=new IntersectionObserver(function(entries){visible=entries[0].isIntersecting&&entries[0].intersectionRatio>=0.5;refresh();},{threshold:[0,0.5]});
        // Observe the clickable banner area, not the much wider cropped video.
        observer.observe(link);
        listen(narrow,'change',refresh);listen(reduced,'change',refresh);
        listen(document,'visibilitychange',function(){if(document.hidden)pause();else activity();});
        ['touchstart','mousemove','scroll','keydown','resize'].forEach(function(event){listen(window,event,activity);});
        activity();
    }
    var api={setActive:function(value){
        var resumed=!hostActive&&!!value;hostActive=!!value;
        if(resumed)activity();else refresh();
    },dispose:function(){
        if(disposed)return;disposed=true;
        clearTimeout(idleTimer);if(observer)observer.disconnect();if(domObserver)domObserver.disconnect();
        if(cancelFrame){cancelFrame();cancelFrame=null;}
        cleanups.forEach(function(remove){remove();});
        if(homepage&&homepage.PlayTakeoverVideo===patched)homepage.PlayTakeoverVideo=original;
        if(narrow.matches)pause();clearStyle();if(style)style.remove();
        if(window.__steamNovaStoreBanner===api)delete window.__steamNovaStoreBanner;
    }};
    window.__steamNovaStoreBanner=api;
    // Parser mutations run before the next paint; do not wait for DOMContentLoaded
    // to give the banner its final size. Stop observing once playback is attached.
    if(document.readyState==='loading'){
        domObserver=new MutationObserver(mount);
        domObserver.observe(document,{childList:true,subtree:true});
    }
    listen(document,'DOMContentLoaded',mount);listen(window,'load',mount);
    listen(window,'load',function(){if(domObserver)domObserver.disconnect();});
    listen(window,'popstate',refresh);
    listen(window,'pagehide',function(event){if(event.persisted)pause();else api.dispose();});
    listen(window,'pageshow',activity);
    mount();
}
""".trimIndent()
}
