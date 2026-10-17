import { createApp, h, nextTick } from 'vue'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import ResearchView from '../views/ResearchView.vue'
import { request, fetchBlob, streamRequest, tokenStorage, ApiClientError } from '../api/client.ts'
import { authStore } from '../stores/auth.ts'
const fixture=document.getElementById('fixture'), passed=[]
const assert=(value,message)=>{if(!value)throw new Error(message);passed.push(message)}
const wait=async check=>{for(let i=0;i<120;i++){await new Promise(r=>setTimeout(r,10));await nextTick();if(check())return}throw new Error('UI timed out: '+fixture.textContent.slice(0,300))}
const envelope=data=>new Response(JSON.stringify({success:true,data}),{headers:{'Content-Type':'application/json'}})
let offline=false,calls=[],app
const hit={id:'search-only',kind:'NOTE',title:'Search result, not a bookmark',excerpt:'Synthetic content',updatedAt:'2026-10-17'}
window.fetch=async(url,init={})=>{
 const path=String(url).replace('/api/v1','');calls.push({path,method:init.method||'GET'})
 if(offline)throw new TypeError('Failed to fetch')
 if(path.startsWith('/research/search?'))return envelope({items:[hit],hasMore:false})
 if(path.startsWith('/research/bookmarks?'))return envelope({items:[],hasMore:false})
 if(path==='/research/recovery-status')return envelope({database:'UP',backup:null})
 return envelope([])
}
try{
 tokenStorage.set('synthetic-session');authStore.state.user={id:901,username:'synthetic',displayName:'Synthetic',roleCode:'ADMIN',permissions:['note:read','note:write']}
 offline=true
 for(const run of [()=>request('/research/bookmarks?page=0'),()=>fetchBlob('/notes/example/export')]){
  let failure;try{await run()}catch(e){failure=e}
  assert(failure instanceof ApiClientError&&failure.code==='NETWORK_UNAVAILABLE','Read transport failures have a stable error code')
  assert(failure.message.includes('服务已启动')&&!failure.message.includes('Failed to fetch'),'Read transport failures explain service recovery')
 }
 const before=calls.length
 for(const run of [()=>request('/research/records/CARD',{method:'POST',body:'{}'}),()=>streamRequest('/notes/assist',{},()=>{})]){
  let failure;try{await run()}catch(e){failure=e}
  assert(failure?.message.includes('无法确认操作是否完成'),'Interrupted writes do not claim that nothing was saved')
 }
 assert(calls.length===before+2,'Failed writes and AI requests are not automatically replayed')
 const controller=new AbortController();controller.abort();let cancelled
 try{await request('/research/search',{signal:controller.signal})}catch(e){cancelled=e}
 assert(cancelled?.name==='AbortError','Explicit cancellation stays distinct from connectivity failure')
 assert(tokenStorage.get()==='synthetic-session','Connectivity failure does not sign out the account')
 offline=false
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/app/research',component:ResearchView},{path:'/:pathMatch(.*)*',component:{template:'<div />'}}]})
 await router.push('/app/research?tab=search');await router.isReady();app=createApp({render:()=>h(RouterView)});app.use(router);app.mount(fixture)
 await wait(()=>fixture.querySelector('.search-result'))
 offline=true;await router.push('/app/research?tab=bookmarks');await wait(()=>fixture.querySelector('[role=alert]'))
 assert(!fixture.textContent.includes(hit.title),'A failed bookmark load cannot display the previous search results')
 assert(!fixture.querySelector('.research-empty'),'A failed load is not presented as an empty collection')
 const retry=()=>[...fixture.querySelectorAll('button')].find(b=>b.textContent==='重新加载资料')
 assert(!!retry(),'Bookmarks provide an in-place retry')
 offline=false;retry().click();await wait(()=>!fixture.querySelector('[role=alert]'))
 assert(fixture.textContent.includes('还没有可查看的收藏'),'Only a successful empty response displays the empty state')
 for(const tab of ['documents','clips','tasks','cards','collections','evaluations','trash']){
  offline=true;await router.push('/app/research?tab='+tab);await wait(()=>fixture.querySelector('[role=alert]'))
  assert(!fixture.textContent.includes('Failed to fetch'),tab+': no raw browser network error')
  const input=fixture.querySelector('textarea:not([readonly])');if(input){input.value='Unsaved recovery draft';input.dispatchEvent(new Event('input',{bubbles:true}));await nextTick()}
  assert(!!retry(),tab+': failed reads can be retried')
  offline=false;const readStart=calls.length
  for(const button of [...fixture.querySelectorAll('.request-failure button')])button.click()
  await wait(()=>!fixture.querySelector('[role=alert]'))
  assert(calls.slice(readStart).every(c=>c.method==='GET'),tab+': recovery only reads; it never submits content')
  if(input)assert(fixture.querySelector('textarea:not([readonly])')===input && fixture.querySelector('textarea:not([readonly])').value==='Unsaved recovery draft',tab+': retry preserves unsaved input without remounting')
 }
 offline=true;await router.push('/app/research?tab=search');await wait(()=>fixture.querySelectorAll('.request-failure').length===2)
 assert(!fixture.querySelector('.search-result'),'Returning to search during an outage does not revive stale results')
 offline=false;for(const button of [...fixture.querySelectorAll('.request-failure button')])button.click();await wait(()=>!fixture.querySelector('[role=alert]')&&fixture.querySelector('.search-result'))
 assert(!fixture.querySelector('.bookmark-button').disabled,'Recovered bookmark state enables the matching bookmark action')
 document.getElementById('results').textContent=`PASS (${passed.length} assertions)\n`+passed.join('\n');document.documentElement.dataset.result='passed'
}catch(error){document.getElementById('results').textContent=error.stack;document.documentElement.dataset.result='failed'}finally{app?.unmount();authStore.state.user=null;tokenStorage.clear()}
