import {createApp,h,nextTick,ref} from 'vue'
import CodeLabSave from '../components/CodeLabSave.vue'
import {authStore} from '../stores/auth.ts'
import {tokenStorage} from '../api/client.ts'
import {mathMarkup,mathLatex} from '../lib/mathNotation.ts'
import {codeReport,runNoteId} from '../lib/codeLabReport.ts'
import {renderMarkdown} from '../lib/markdown.ts'
const fixture=document.getElementById('fixture'),checks=[]
const assert=(ok,label)=>{if(!ok)throw Error(label);checks.push(label)}
const wait=async fn=>{for(let i=0;i<150;i++){await new Promise(r=>setTimeout(r,5));await nextTick();if(fn())return}throw Error('Save UI did not settle')}
const button=text=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===text)
const snapshot=ref({source:'print(1)',entry:'',args:'[]',language:'python'})
const run=ref(),analysis={status:'derived',complexity:'Θ(n^2)',size:{name:'n',value:8,origin:'literal'},minimum:1,records:[{line:3,kind:'loop',formula:'n(n − 1)/2',summation:'Σ(i=1…n − 1) [n − i]',measured:28,predicted:28,verification:'matched'}]}
let app,requests=[],held,hold=true,trash=false
const response=data=>new Response(JSON.stringify({success:true,data}),{headers:{'Content-Type':'application/json'}})
window.fetch=async(url,init)=>{
 const body=JSON.parse(init.body);requests.push({url:String(url),body})
 if(String(url).endsWith('/notes')){if(trash)return new Response(JSON.stringify({success:false,error:{code:'NOTE_CREATE_TRASHED',message:'对应笔记在回收站中，请先恢复，或永久删除后再保存。'}}),{status:409,headers:{'Content-Type':'application/json'}});const reply=()=>response({id:body.clientId});if(hold)return new Promise(resolve=>held=()=>resolve(reply()));return reply()}
 if(String(url).endsWith('/archive'))return response({...run.value,...body})
 throw Error('Unexpected API call')
}
try{
 fixture.innerHTML=mathMarkup('Θ(n^2)')+mathMarkup('Σ(i=1…n − 1) [Σ(j=i…n) [1]] = n(n − 1)/2',true)
 assert(fixture.querySelector('msup')&&fixture.querySelectorAll('munderover').length===2&&fixture.querySelector('mfrac'),'Powers, nested sums and fractions use mathematical layout')
 assert(mathLatex('n(n − 1)/2').includes('\\frac')&&mathLatex('Θ(n^2)').includes('\\Theta'),'Markdown export uses standard TeX notation')
 fixture.innerHTML=mathMarkup('<img src=x onerror=alert(1)>')
 assert(!fixture.querySelector('img'),'Formula fallback escapes unsupported HTML input')
 const report=codeReport({...snapshot.value,source:'```\n<script>alert(1)</script>'},{title:'<img src=x>',note:true})
 fixture.innerHTML=renderMarkdown(report)
 assert(!fixture.querySelector('script,img')&&fixture.querySelector('pre code').textContent.includes('<script>'),'User code and titles stay inert, including embedded fences')
 const result={id:'00000000-0000-4000-8000-000000000001',source:'print(1)',entry:'',arguments:[],result:{language:'python',status:'completed',steps:[],output:'1',analysis}}
 const note=codeReport(snapshot.value,{run:result,title:'Loop',note:true})
 fixture.innerHTML=renderMarkdown(note)
 assert(fixture.querySelector('mfrac')&&fixture.querySelector('msup'),'Note preview retains the safe mathematical notation')
 assert((await runNoteId(result.id))===(await runNoteId(result.id)),'Run note identifiers stay stable across reloads and retries')
 const stale=codeReport({...snapshot.value,source:'print(2)'},{run:result,title:'Draft'})
 assert(!stale.includes('数学分析')&&stale.includes('代码草稿'),'Edited drafts never inherit a previous run result')
 fixture.innerHTML='';tokenStorage.set('synthetic-save');authStore.state.user={id:991,permissions:['code-lab:use','note:write','note:read']}
 app=createApp({render:()=>h(CodeLabSave,{snapshot:snapshot.value,run:run.value,step:0,onArchived:value=>run.value=value})});app.mount(fixture);await nextTick()
 assert(button('长期存档').disabled&&!button('存入笔记').disabled,'Unexecuted code can be noted but cannot pretend to have a replay archive')
 button('存入笔记').click();await wait(()=>held);button('正在保存…').click();assert(requests.length===1,'Repeated clicks while saving submit once')
 held();await wait(()=>button('打开笔记')||fixture.querySelector('a'));hold=false
 const first=requests[0].body;assert(first.library==='计算机学习'&&first.contentFormat==='MARKDOWN'&&first.body.includes('代码草稿'),'Draft is saved to the computer-learning notebook')
 button('存入笔记').click();await wait(()=>requests.length===2&&!button('正在保存…'))
 assert(requests[1].body.clientId===first.clientId,'Retry of the same draft is idempotent')
 run.value=result;await nextTick();button('存入笔记').click();await wait(()=>requests.length===3&&!button('正在保存…'))
 assert(requests[2].body.body.includes('<math')&&requests[2].body.clientId===await runNoteId(result.id),'A matching run saves its formulas under a stable note identity')
 button('长期存档').click();await nextTick()
 const title=document.querySelector('dialog input'),memo=document.querySelector('dialog textarea')
 title.value='My loop';title.dispatchEvent(new Event('input',{bubbles:true}));memo.value='Boundary case n=1';memo.dispatchEvent(new Event('input',{bubbles:true}));await nextTick()
 document.querySelector('dialog form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await wait(()=>run.value.archived)
 assert(requests.at(-1).body.title==='My loop'&&requests.at(-1).body.memo.includes('n=1')&&button('编辑存档'),'Archive stores a name and learning notes without altering source')
 snapshot.value={...snapshot.value,source:'print(2)'};await nextTick()
 assert(button('长期存档').disabled&&!fixture.querySelector('a'),'Editing invalidates the previous result and its note shortcut')
 trash=true;button('存入笔记').click();await wait(()=>fixture.querySelector('a[href="/app/notes/trash"]'));assert(fixture.textContent.includes('对应笔记在回收站'),'A deleted note offers recovery instead of claiming a successful save');
 authStore.state.user={id:991,permissions:['code-lab:use']};await nextTick()
 assert(!button('存入笔记'),'Note action respects the separate note-writing permission')
 document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${checks.length} assertions)\n`+checks.join('\n')
}catch(e){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=e.stack}
finally{app?.unmount();authStore.state.user=null;tokenStorage.clear()}
