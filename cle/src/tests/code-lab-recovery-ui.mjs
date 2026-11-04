import {createApp,h,nextTick} from 'vue'
import {createRouter,createMemoryHistory,RouterView} from 'vue-router'
import CodeLabView from '../views/CodeLabView.vue'
import {tokenStorage} from '../api/client.ts'
import {authStore} from '../stores/auth.ts'
const fixture=document.getElementById('fixture'),checks=[],pending=[]
const assert=(ok,label)=>{if(!ok)throw Error(label);checks.push(label)}
const wait=async fn=>{for(let i=0;i<150;i++){await new Promise(r=>setTimeout(r,5));await nextTick();if(fn())return}throw Error('Code history did not settle')}
const button=text=>[...fixture.querySelectorAll('button')].find(b=>b.textContent.trim().replace(/\s*↗$/,'')===text)
const reply=data=>new Response(JSON.stringify({success:true,data}),{headers:{'Content-Type':'application/json'}})
const summary=id=>({id,language:'python',entry:'sort',status:'completed',title:id,createdAt:'2026-01-01T00:00:00Z'})
let app,posts=0
window.fetch=async(url,init={})=>{
 if(!String(url).endsWith('/code-lab/runs'))throw Error('Unexpected request')
 if(init.method==='POST'){
  posts++;const input=JSON.parse(init.body)
  return reply({...input,id:'fresh',result:{language:'python',status:'completed',steps:[],output:'done'}})
 }
 // Deliberately ignore cancellation to simulate a response already queued by transport.
 return new Promise((resolve,reject)=>pending.push({signal:init.signal,resolve:items=>resolve(reply(items)),reject}))
}
try{
 tokenStorage.set('synthetic-history');authStore.state.user={id:991,permissions:['code-lab:use']}
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:CodeLabView}]})
 await router.push('/');await router.isReady();app=createApp({render:()=>h(RouterView)}).use(router);app.mount(fixture)
 await wait(()=>pending.length===1)
 assert(button('刷新').disabled,'Refresh locks while history is loading')
 button('刷新').click();await nextTick();assert(pending.length===1,'Repeated refresh does not create duplicate requests')
 button('运行并可视化').click();await wait(()=>pending.length===2)
 assert(posts===1&&pending[0].signal.aborted,'Finishing a run cancels the older history request')
 pending[1].resolve([summary('fresh')]);await wait(()=>fixture.querySelector('.lab-history-open'))
 pending[0].resolve([summary('stale')]);await new Promise(r=>setTimeout(r,20));await nextTick()
 assert(fixture.querySelector('.lab-history').textContent.includes('fresh')&&!fixture.querySelector('.lab-history').textContent.includes('stale'),'Late old response cannot replace the new history')
 assert(!button('刷新').disabled,'Refresh unlocks after the current request finishes')
 button('刷新').click();await wait(()=>pending.length===3);pending[2].reject(Error('Synthetic offline'))
 await wait(()=>fixture.querySelector('[role=alert]'))
 assert(fixture.querySelector('.lab-history').textContent.includes('fresh')&&!button('刷新').disabled,'Offline refresh keeps existing records and permits retry')
 button('刷新').click();await wait(()=>pending.length===4);pending[3].resolve([summary('recovered')])
 await wait(()=>fixture.querySelector('.lab-history').textContent.includes('recovered'))
 assert(!fixture.querySelector('[role=alert]'),'Successful retry clears the old error')
 button('刷新').click();await wait(()=>pending.length===5);app.unmount();app=null
 assert(pending[4].signal.aborted,'Leaving the page cancels history transport')
 pending[4].resolve([summary('after-leave')]);await nextTick()
 assert(!fixture.textContent.includes('after-leave'),'Late response after leaving cannot render private history')
 document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${checks.length} assertions)\n`+checks.join('\n')
}catch(e){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=e.stack}
finally{app?.unmount();tokenStorage.clear();authStore.state.user=null}
