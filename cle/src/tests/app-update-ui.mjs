import {createApp,nextTick} from 'vue'
import AppUpdateNotice from '../components/AppUpdateNotice.vue'
import {watchAppUpdate} from '../lib/appUpdate.ts'
const checks=[],fixture=document.getElementById('fixture'),script=document.createElement('script')
const assert=(ok,label)=>{if(!ok)throw Error(label);checks.push(label)}
const settle=async()=>{await new Promise(r=>setTimeout(r,5));await nextTick()}
const realNow=Date.now;let now=100000,entry='/assets/old.js',calls=0,app,stop=()=>{}
Date.now=()=>now
script.type='module';script.src='/assets/old.js';document.head.append(script)
window.fetch=async(url,options)=>{calls++;assert(url==='/build-info.json'&&options.cache==='no-store'&&options.credentials==='omit','Build check uses public uncached metadata without credentials');return new Response(JSON.stringify({entry}))}
try{
 app=createApp(AppUpdateNotice);app.mount(fixture);await settle()
 assert(!fixture.querySelector('aside'),'Matching build does not show a notice')
 window.dispatchEvent(new Event('focus'));await settle();assert(calls===1,'Repeated focus is throttled')
 entry='/assets/new.js';now+=60001;window.dispatchEvent(new Event('focus'));await settle()
 assert(fixture.textContent.includes('新版已就绪')&&fixture.textContent.includes('请先保存编辑或完成答题'),'New build prompts explicitly without refreshing or clearing work')
 assert([...fixture.querySelectorAll('button')].some(b=>b.textContent==='刷新使用新版'),'Refresh requires the user to click the action')
 ;[...fixture.querySelectorAll('button')].find(b=>b.textContent==='稍后').click();await nextTick()
 assert(!fixture.querySelector('aside'),'Notice can be deferred')
 const previous=calls;now+=60001;window.dispatchEvent(new Event('focus'));await settle();assert(calls===previous,'A detected update stops background checks')
 app.unmount();app=null
 for(const invalid of ['https://elsewhere.test/app.js','/index.html',null]){
   entry=invalid;let triggered=false;stop=watchAppUpdate('/assets/old.js',()=>{triggered=true});await settle();stop()
   assert(!triggered,'Invalid build metadata does not offer an update')
 }
 window.fetch=async()=>{throw Error('offline')};let triggered=false
 stop=watchAppUpdate('/assets/old.js',()=>{triggered=true});await settle();stop()
 assert(!triggered,'Offline checks leave the current page usable')
 document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${checks.length} assertions)\n`+checks.join('\n')
}catch(e){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=e.stack}
finally{stop();app?.unmount();script.remove();Date.now=realNow}
