package steam.nova.app.features.chat.voice

/**
 * WebRTC/Opus flow adapted from Monica Steam's SteamVoiceWebViewEngine.
 * Copyright 2025 JoyinJoester. SPDX-License-Identifier: GPL-3.0-or-later.
 * Local media only: no remote scripts, cookies, account credentials or page UI.
 */
internal object SteamChatVoicePage {
    val html = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; media-src blob:; connect-src 'none'">
        </head><body><script>
        (function(){
          var pc=null,stream=null,stopped=false,mic=false,output=false,timer=null,chain=Promise.resolve();
          function emit(kind,value){if(!stopped)window.NovaVoiceBridge.event(kind,String(value||''));}
          function fail(e){emit('error',e&&e.name==='NotAllowedError'?'permission':'media');}
          function normalize(d){
            var lines=d.sdp.split('\r\n'),opus=lines.map(function(l){return l.match(/^a=rtpmap:(\d+)\s+opus(?:\/|\s)/i);}).find(Boolean);
            if(!opus)return d;
            var prefix='a=fmtp:'+opus[1]+' ',fmtp=prefix+'minptime=10;useinbandfec=1;usedtx=1';
            var at=lines.findIndex(function(l){return l.indexOf(prefix)===0;});
            if(at>=0)lines[at]=fmtp;else lines.push(fmtp);
            d.sdp=lines.join('\r\n');return d;
          }
          window.novaVoice={
            async start(){try{
              stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true,
                autoGainControl:true,channelCount:{ideal:1},sampleRate:{ideal:48000}},video:false});
              if(stopped){stream.getTracks().forEach(function(t){t.stop();});stream=null;return;}
              try{pc=new RTCPeerConnection({sdpSemantics:'plan-b'});}catch(e){pc=new RTCPeerConnection();}
              stream.getAudioTracks().forEach(function(t){t.contentHint='speech';t.enabled=!mic;
                t.onended=function(){emit('error','track');};pc.addTrack(t,stream);});
              pc.oniceconnectionstatechange=function(){if(pc)emit('ice',pc.iceConnectionState);};
              pc.ontrack=function(e){
                if(e.track.kind!=='audio')return;
                var audio=document.createElement('audio');audio.autoplay=true;audio.muted=output;
                // A stream can contain multiple tracks; rendering that stream per track duplicates audio.
                audio.srcObject=new MediaStream([e.track]);document.body.appendChild(audio);
                audio.onloadedmetadata=function(){audio.play().catch(fail);};
                e.track.onended=function(){audio.pause();audio.srcObject=null;audio.remove();};
                audio.play().catch(fail);
              };
              var offer=normalize(await pc.createOffer({offerToReceiveAudio:true,voiceActivityDetection:true}));
              if(stopped)return;await pc.setLocalDescription(offer);
              emit('offer',JSON.stringify(pc.localDescription));
              timer=setInterval(async function(){
                if(!pc)return;try{var sent=0,received=0;
                  (await pc.getStats()).forEach(function(r){if(r.type==='outbound-rtp'&&!r.isRemote)sent+=Number(r.bytesSent||0);
                    if(r.type==='inbound-rtp'&&!r.isRemote)received+=Number(r.bytesReceived||0);});
                  emit('stats',JSON.stringify({sent:sent,received:received}));
                }catch(ignore){}
              },5000);
            }catch(e){fail(e);}},
            remote(raw,version){chain=chain.then(async function(){
              if(stopped||!pc)return;var d=JSON.parse(raw);await pc.setRemoteDescription(d);
              if(stopped||!pc)return;
              if(d.type==='offer'){
                var answer=normalize(await pc.createAnswer());await pc.setLocalDescription(answer);
                // Steam acknowledges updated offers after the local answer is set.
                if(version)emit('ack',version);
              }
            }).catch(fail);},
            mute(m,o){mic=!!m;output=!!o;if(stream)stream.getAudioTracks().forEach(function(t){t.enabled=!mic;});
              document.querySelectorAll('audio').forEach(function(a){a.muted=output;});},
            stop(){stopped=true;clearInterval(timer);if(pc)pc.close();if(stream)stream.getTracks().forEach(function(t){t.stop();});
              document.querySelectorAll('audio').forEach(function(a){a.pause();a.srcObject=null;a.remove();});pc=null;stream=null;}
          };
          window.addEventListener('load',function(){window.novaVoice.start();});
        })();
        </script></body></html>
    """.trimIndent()
}
