// Only synthetic files, users and requests.
import {createApp,nextTick,h} from 'vue'
import FileLifecyclePanel from '../components/FileLifecyclePanel.vue'
import GroupInvitations from '../components/GroupInvitations.vue'
import {tokenStorage} from '../api/client.ts'
const fixture=document.getElementById('fixture'),checks=[]
const assert=(value,text)=>{if(!value)throw new Error(text);checks.push(text)}
const wait=async predicate=>{for(let i=0;i<150;i++){await new Promise(r=>setTimeout(r,5));await nextTick();if(predicate())return}throw new Error('Timed out')}
const button=text=>[...fixture.querySelectorAll('button')].find(b=>b.textContent.trim()===text)
const reply=data=>new Response(JSON.stringify({success:true,data}),{headers:{'Content-Type':'application/json'}})
let app,operations=[],pending=false,resolveRequest,fail=false,refCount=1
let rows=[{id:'synthetic-file',originalName:'example.txt',sizeBytes:123}],history=[{id:'invite',groupName:'Fixture',displayName:'Test',identityCode:'TEST7',kind:'INVITE',status:'PENDING'}]
window.fetch=async(url,init={})=>{
 const path=String(url),method=init.method||'GET'
 if(method!=='GET'){
  operations.push({path,body:JSON.parse(init.body)})
  if(pending)return new Promise(resolve=>{resolveRequest=()=>{rows=[];resolve(reply(null))}})
  if(path.endsWith('/cancel'))history[0].status='CANCELLED';else rows=rows.filter(row=>!path.endsWith('/'+row.id+'/lifecycle'))
  return reply(null)
 }
 if(fail)return new Response(JSON.stringify({success:false,error:{code:'FAILED',message:'Temporary failure'}}),{status:503,headers:{'Content-Type':'application/json'}})
 if(path.includes('/files/usage'))return reply({files:1,totalBytes:123,trashBytes:123})
 if(path.includes('/files/trash')){const page=Number(new URL(path,location.origin).searchParams.get('page')||0);return reply({items:rows.slice(page*24,(page+1)*24),total:rows.length})}
 if(path.endsWith('/references'))return reply(refCount?[{kind:'笔记',count:refCount}]:[])
 if(path.includes('/invitations/history'))return reply({items:history,hasNext:false})
 if(path.endsWith('/invitations'))return reply([])
 throw new Error('Unexpected request '+path)
}
const mount=component=>{app=createApp(component);app.mount(fixture)}
try{
 tokenStorage.set('synthetic-platform-test')
 mount({render:()=>h(FileLifecyclePanel,{file:rows[0]})});await wait(()=>button('移入回收站'))
 assert(button('移入回收站').disabled,'Referenced files cannot be moved to trash')
 button('永久删除').click();await nextTick()
 assert(fixture.querySelector('form button:last-child').disabled,'Deletion requires exact filename confirmation')
 const input=fixture.querySelector('form input');input.value='wrong.txt';input.dispatchEvent(new Event('input',{bubbles:true}));await nextTick()
 assert(fixture.querySelector('form button:last-child').disabled,'Incorrect filename prevents permanent deletion')
 button('取消').click();await nextTick();assert(!fixture.querySelector('form')&&!operations.length,'Cancelling deletion changes no data')
 pending=true;button('恢复').click();button('恢复').click();await wait(()=>resolveRequest)
 assert(operations.length===1,'Repeated restore clicks send one request')
 assert(button('永久删除').disabled,'Other file mutations are disabled during restoration')
 resolveRequest();await wait(()=>fixture.textContent.includes('回收站为空'));app.unmount();pending=false
 fail=true;mount(FileLifecyclePanel);await wait(()=>fixture.querySelector('[role=alert]'))
 assert(!!button('重试'),'Storage failure provides a retry action');fail=false;button('重试').click();await wait(()=>!fixture.querySelector('[role=alert]'));app.unmount()
 rows=Array.from({length:25},(_,i)=>({id:'page-'+i,originalName:'fixture-'+i+'.txt',sizeBytes:123}))
 mount(FileLifecyclePanel);await wait(()=>button('下一页')&&!button('下一页').disabled);button('下一页').click();await wait(()=>fixture.querySelectorAll('article').length===1)
 button('恢复').click();await wait(()=>fixture.querySelectorAll('article').length===24)
 assert(button('上一页').disabled&&fixture.querySelector('nav').textContent.includes('1 / 1'),'Restoring the last item returns to the remaining page');app.unmount()
 mount(GroupInvitations);await wait(()=>button('撤回'));button('撤回').click();await wait(()=>fixture.textContent.includes('已撤回'))
 assert(!button('撤回'),'Withdrawn invitations no longer offer withdrawal')
 assert(operations.at(-1).path.endsWith('/invitations/invite/cancel'),'Withdrawal uses the selected invitation ID')
 assert(button('下一页').disabled&&button('上一页').disabled,'History pagination respects response boundaries')
 document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS ${checks.length}\n${checks.join('\n')}`
}catch(e){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=`FAIL ${e.stack}`}
finally{app?.unmount();tokenStorage.clear()}
