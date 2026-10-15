import {createApp,nextTick,h} from 'vue'
import {createRouter,createMemoryHistory} from 'vue-router'
import DailyStudyPlan from '../components/DailyStudyPlan.vue'
import WorkspaceTasks from '../components/WorkspaceTasks.vue'
import ReadingSelection from '../components/ReadingSelection.vue'
import {authStore} from '../stores/auth.ts'
import {tokenStorage} from '../api/client.ts'
const fixture=document.getElementById('fixture'),passed=[]
const assert=(value,text)=>{if(!value)throw Error(text);passed.push(text)}
const wait=async f=>{for(let i=0;i<100;i++){await new Promise(r=>setTimeout(r,5));await nextTick();if(f())return}throw Error('UI timed out')}
const button=text=>[...fixture.querySelectorAll('button')].find(b=>b.textContent.includes(text))
const envelope=data=>new Response(JSON.stringify({success:true,data}),{headers:{'Content-Type':'application/json'}})
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{template:'<div/>'}}]})
let app,saved,cancelled=0,captured=[],phase='QUEUED',holdLoads=false,jobLoads=[],deletes=0,releaseDelete
globalThis.confirm=window.confirm=()=>true
window.fetch=async(url,init={})=>{
 const path=String(url)
 if(path.endsWith('/learning/plan')){if(init.method==='PUT'){saved=JSON.parse(init.body);return envelope(saved)}return envelope({settings:{minutes:20,spreadDays:7,revision:3},usedSeconds:120,remainingSeconds:1080,backlog:24,studyDay:true,steps:[{name:'到期词汇',count:4,seconds:120,url:'/app/vocabulary?mode=REVIEW'}],explanation:'计划说明'})}
 if(path.includes('/workspace-jobs?page=')){if(holdLoads)return new Promise(resolve=>jobLoads.push(resolve));return envelope([{id:'job-1',title:'Scan.pdf',kind:'DOCUMENT_OCR',status:phase,progress:12,attempts:0,createdAt:'2026-10-15T10:00:00Z'}])}
 if(path.endsWith('/workspace-jobs/job-1')&&init.method==='DELETE'){deletes++;return new Promise(resolve=>releaseDelete=()=>resolve(envelope(null)))}
 if(path.endsWith('/workspace-jobs/job-1/cancel')){phase='CANCELLED';cancelled++;return envelope({})}
 if(path.endsWith('/learning/clips')){captured.push(JSON.parse(init.body));return envelope({saved:true})}
 throw Error('Unexpected fixture request '+path)
}
try{
 tokenStorage.set('synthetic-workflow');authStore.state.user={id:8,permissions:['note:write','note:read','research:use','vocabulary:use']};await router.push('/app/learning');await router.isReady()
 app=createApp(DailyStudyPlan).use(router);app.mount(fixture);await wait(()=>fixture.textContent.includes('24 项'))
 assert(fixture.querySelector('a').getAttribute('href').includes('mode=REVIEW'),'Plan launches the corresponding practice mode')
 button('调整时间').click();await nextTick();const input=fixture.querySelector('input');input.value='35';input.dispatchEvent(new Event('input',{bubbles:true}));fixture.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await wait(()=>saved)
 assert(saved.minutes===35&&saved.revision===3,'Plan saves the user value with its loaded revision');app.unmount()
 app=createApp(WorkspaceTasks).use(router);app.mount(fixture);await wait(()=>fixture.textContent.includes('Scan.pdf'));button('取消').click();await wait(()=>fixture.textContent.includes('已取消'))
 assert(cancelled===1&&button('重试'),'Cancelled document remains available for explicit retry');assert(!button('取消'),'Cancel action disappears when the job finishes')
 holdLoads=true;button('刷新').click();button('刷新').click();await wait(()=>jobLoads.length===2)
 const row=title=>[{id:'job-1',title,kind:'DOCUMENT_OCR',status:'CANCELLED',progress:0,attempts:0,createdAt:'2026-10-15T10:00:00Z'}]
 jobLoads[1](envelope(row('Latest task')));await wait(()=>fixture.textContent.includes('Latest task'));jobLoads[0](envelope(row('Stale task')));await new Promise(r=>setTimeout(r,20));await nextTick()
 assert(fixture.textContent.includes('Latest task')&&!fixture.textContent.includes('Stale task'),'A late task refresh cannot replace newer results')
 holdLoads=false;button('移除记录').click();button('移除记录').click();await wait(()=>deletes===1);releaseDelete();await wait(()=>fixture.textContent.includes('Scan.pdf'))
 assert(deletes===1,'Repeated removal sends only one delete request');app.unmount()
 // Use a normal parent slot so browser selection can be scoped to source text.
 app=createApp({render:()=>h(ReadingSelection,{kind:'NOTE',sourceId:'note-1'},()=>h('p',{id:'source'},'A useful phrase.'))}).use(router);app.mount(fixture)
 const source=fixture.querySelector('#source'),range=document.createRange();range.selectNodeContents(source);window.getSelection().removeAllRanges();window.getSelection().addRange(range);source.dispatchEvent(new window.MouseEvent('mouseup',{bubbles:true}));await nextTick()
 assert(fixture.textContent.includes('留下这一段'),'Selecting source text opens the reading actions')
 button('制作复习卡').click();await wait(()=>captured.length===1)
 assert(captured[0].quote==='A useful phrase.'&&captured[0].sourceId==='note-1'&&captured[0].action==='CARD','Captured text carries source identity and a request key')
 app.unmount();document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS ${passed.length}\n${passed.join('\n')}`
}catch(e){document.getElementById('results').textContent=e.stack}
finally{app?.unmount()}
