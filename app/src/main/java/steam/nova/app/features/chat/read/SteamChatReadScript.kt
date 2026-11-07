package steam.nova.app.features.chat.read

/** Monotonic CM seconds and finer local message cursors; native code owns durable retries. */
internal object SteamChatReadScript {
    fun source() = """
    function chatReadReceipts(config){
        var pending={},sending={},persisted={},timer=null,MAX=4294967295;
        function state(){return config.state();}
        function point(key){return state().readState[key]||(state().readState[key]={read:0,confirmed:0});}
        function cursor(value){var read=Math.max(value.read||0,value.confirmed||0),time=Math.max(read,value.seenTimestamp||0);
            return {timestamp:time,ordinal:time===read?MAX:Number(value.seenOrdinal===undefined?MAX:value.seenOrdinal)};}
        function compare(a,b){return Number(a.timestamp)-Number(b.timestamp)||Number(a.ordinal||0)-Number(b.ordinal||0);}
        function real(m){return !m.pending&&!m.failed&&m.timestamp>0;}
        function isRead(m){return compare(m,cursor(point(m.key)))<=0;}
        function project(row){
            var p=point(row.key),seen=cursor(p),data=state().threads[row.key];
            if(p.read&&Number(row.timestamp||0)<=p.read){row.unread=0;return row;}
            var messages=data?data.messages.filter(real):[];
            // Only a fresh latest-history page proves coverage. Merged cached pages may have gaps.
            if(data&&data.loaded&&data.latestFloor&&messages.length&&compare(seen,data.latestFloor)>=0&&messages[0].timestamp>=row.timestamp){
                row.unread=messages.filter(function(m){return m.sender!==state().account&&!m.system&&!m.deleted&&compare(m,seen)>0;}).length;
            }
            return row;
        }
        function projectAll(){var changed=false;function update(row){var unread=row.unread;project(row);if(row.unread!==unread)changed=true;}
            state().sessions.forEach(update);state().groups.forEach(function(g){(g.rooms||[]).forEach(update);});return changed;}
        function apply(key,patch,durable){
            var value=point(key),old=cursor(value),next=cursor(patch);
            value.confirmed=Math.max(value.confirmed||0,Number(patch.confirmed)||0);
            value.read=Math.max(value.read||0,Number(patch.read)||0,value.confirmed);
            if(compare(next,old)>=0){value.seenTimestamp=next.timestamp;value.seenOrdinal=next.ordinal;}
            if(durable){var saved=persisted[key];if(!saved||compare(next,cursor(saved))>=0)persisted[key]=Object.assign({},patch);}
            if(pending[key]&&persisted[key]&&compare(cursor(persisted[key]),cursor(pending[key].value))>=0)delete pending[key];
        }
        function hydrate(values){Object.keys(values||{}).forEach(function(key){apply(key,values[key],true);});projectAll();}
        function event(value){apply(value.key,value.type==='read'?{read:value.timestamp,confirmed:value.timestamp}:value,true);projectAll();}
        function mergeRows(rows,previous){
            var old={},present={};(previous||[]).forEach(function(row){old[row.key]=row;});
            var merged=(rows||[]).map(function(row){var prior=old[row.key];present[row.key]=true;
                if(row.lastRead)apply(row.key,{read:row.lastRead,confirmed:row.lastRead},true);
                var next=Object.assign({},prior||{},row);
                if(prior&&Number(prior.timestamp)>Number(row.timestamp)){next.timestamp=prior.timestamp;next.preview=prior.preview;next.unread=prior.unread;}
                return project(next);});
            (previous||[]).forEach(function(row){if(!present[row.key]&&row.unread)merged.push(project(Object.assign({},row)));});return merged;
        }
        function mergeGroups(groups){return (groups||[]).map(function(group){var old=state().groups.find(function(g){return g.id===group.id;});
            return Object.assign({},group,{rooms:mergeRows(group.rooms,old&&old.rooms)});});}
        function schedule(delay){if(timer!==null||!config.running())return;timer=setTimeout(function(){timer=null;flush();},delay);}
        function flush(){if(!config.running())return;Object.keys(pending).forEach(function(key){
            if(sending[key])return;var item=pending[key],owner=state();sending[key]=item;
            config.request('ack',Object.assign({},item.target,item.value,{timestamp:item.value.read})).then(function(value){
                if(state()!==owner)return;apply(key,Object.assign({},item.value,value),true);projectAll();config.emit();
            }).catch(function(){if(state()===owner)item.attempts=(item.attempts||0)+1;}).then(function(){
                if(state()!==owner)return;delete sending[key];if(pending[key])schedule(Math.min(30000,1000*Math.pow(2,Math.min(item.attempts||0,5))));
            });
        });}
        function mark(target,message){
            if(!config.running()||!message)return;
            var data=state().threads[target.key],numeric=typeof message==='number';
            if(numeric)message={timestamp:message,ordinal:MAX};
            var laterInSecond=!numeric&&data&&data.messages.some(function(m){return real(m)&&m.timestamp===message.timestamp&&compare(m,message)>0;});
            var value={read:message.timestamp-(laterInSecond?1:0),seenTimestamp:message.timestamp,seenOrdinal:laterInSecond?message.ordinal:MAX};
            var changed=compare(cursor(value),cursor(point(target.key)))>0;
            apply(target.key,value,false);var projected=projectAll();
            if(changed||projected)config.emit();
            // Successful local persistence transfers retry ownership to the native CM worker.
            if(!persisted[target.key]||compare(cursor(persisted[target.key]),cursor(value))<0){
                var old=pending[target.key];if(!old||compare(cursor(old.value),cursor(value))<=0)pending[target.key]={target:target,value:value,attempts:0};flush();
            }
        }
        function pause(){if(timer!==null)clearTimeout(timer);timer=null;}
        function reset(){pause();pending={};sending={};persisted={};}
        return {mark:mark,event:event,hydrate:hydrate,mergeRows:mergeRows,mergeGroups:mergeGroups,project:project,isRead:isRead,
            readThrough:function(key){return point(key).read||0;},pause:pause,resume:flush,reset:reset};
    }
    """.trimIndent()
}
