import {createApp,nextTick} from 'vue'
import {createRouter,createMemoryHistory} from 'vue-router'
import NotePersonalTools from '../components/NotePersonalTools.vue'
import LearningStatistics from '../components/LearningStatistics.vue'
import {authStore} from '../stores/auth.ts'
import {tokenStorage} from '../api/client.ts'
const fixture=document.getElementById('fixture'),passed=[]
const assert=(yes,label)=>{if(!yes)throw Error(label);passed.push(label)}
const wait=async f=>{for(let i=0;i<100;i++){await new Promise(r=>setTimeout(r,5));await nextTick();if(f())return}throw Error('UI timed out')}
const button=text=>[...fixture.querySelectorAll('button')].find(b=>b.textContent.includes(text))
const envelope=data=>new Response(JSON.stringify({success:true,data}),{headers:{'Content-Type':'application/json'}})
let action='summarize',cancelled=0,controller,app,applied=0
const routes=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{template:'<div/>'}}]})
window.fetch=async(url,init={})=>{
 const path=String(url)
 if(path.endsWith('/providers'))return envelope([{provider:'QWEN',remoteEnabled:true}])
 if(path.endsWith('/settings'))return envelope([{provider:'QWEN',model:'fixture',configured:true,enabled:true}])
 if(path.endsWith('/preview')){action=JSON.parse(init.body).action;return envelope({previewToken:'fixture',streaming:true,provider:'QWEN',model:'fixture',action,context:'approved',expiresAt:new Date(Date.now()+60000).toISOString(),outboundBytes:1})}
 if(path.endsWith('/cancel')){cancelled++;return envelope(null)}
 if(path.endsWith('/stream'))return new Response(new ReadableStream({start(c){controller=c;c.enqueue(new TextEncoder().encode(JSON.stringify({type:'delta',text:'第一段实际输出'})+'\n'))}}),{headers:{'Content-Type':'application/x-ndjson'}})
 if(path.includes('/learning/statistics'))return envelope({userId:8,zoneId:'Asia/Shanghai',activeDays:1,total:{answers:4,correct:3,words:2,cards:1,notes:1,seconds:120},days:[{date:'2026-10-14',metrics:{answers:4,correct:3,words:2,cards:1,notes:1,seconds:120}}]})
 throw Error('Unexpected synthetic request '+path)
}
try{
 tokenStorage.set('synthetic-expansion');authStore.state.user={id:8,permissions:['personal-ai:use','note:write']};await routes.push('/app/notes');await routes.isReady()
 app=createApp(NotePersonalTools,{title:'Test',body:'Synthetic notebook',onAppend:()=>applied++}).use(routes);app.mount(fixture)
 await wait(()=>fixture.querySelector('option[value=QWEN]'));const select=fixture.querySelector('select');select.value='QWEN';select.dispatchEvent(new Event('change',{bubbles:true}));await nextTick()
 async function start(){button('摘要').click();await wait(()=>fixture.querySelector('[aria-label="发送前确认"]'));const consent=fixture.querySelector('[aria-label="发送前确认"] input');consent.checked=true;consent.dispatchEvent(new Event('change',{bubbles:true}));await nextTick();button('确认发送并生成').click();await wait(()=>fixture.textContent.includes('第一段实际输出'))}
 await start();assert(!fixture.querySelector('[aria-label="生成结果预览"]'),'Stream deltas remain visibly in progress until final confirmation')
 button('停止生成').click();await nextTick();assert(fixture.textContent.includes('内容未完成')&&fixture.textContent.includes('第一段实际输出'),'Stopping preserves partial text with an incomplete marker');assert(cancelled===1&&applied===0,'Stopping cancels upstream and never applies a partial result')
 await start();controller.enqueue(new TextEncoder().encode(JSON.stringify({type:'result',data:{action,engine:'PERSONAL_AI:QWEN',result:'完整结果',items:[],persistenceStatus:'SAVED'}})+'\n'));controller.close();await wait(()=>fixture.querySelector('[aria-label="生成结果预览"]'));assert(fixture.textContent.includes('完整结果')&&applied===0,'Completed output still requires manual application')
 authStore.state.user={id:9,permissions:['personal-ai:use']};await nextTick();assert(!fixture.textContent.includes('完整结果'),'Account switch clears streamed private content');app.unmount()
 app=createApp(LearningStatistics).use(routes);app.mount(fixture);await wait(()=>fixture.textContent.includes('累计学习单词'));assert(fixture.textContent.includes('0 小时 2 分钟')&&fixture.querySelectorAll('tbody tr').length===1,'Daily table and cumulative active duration render independently');assert(fixture.querySelector('.learning-bar-track').title.includes('2026-10-14'),'Daily chart exposes exact date and value');app.unmount()
 document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS ${passed.length}\n${passed.join('\n')}`
}catch(e){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=e.stack}
finally{app?.unmount()}
