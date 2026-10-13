import { createApp, nextTick } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import NotificationCenter from '../components/NotificationCenter.vue'
import { researchApi } from '../api/research.ts'
import { authStore } from '../stores/auth.ts'
import { tokenStorage } from '../api/client.ts'
const fixture=document.getElementById('fixture'), checks=[]
const assert=(ok,label)=>{if(!ok)throw new Error(label);checks.push(label)}
const sleep=ms=>new Promise(r=>setTimeout(r,ms))
const wait=async fn=>{for(let i=0;i<180;i++){await sleep(10);await nextTick();if(fn())return}throw new Error('Notification UI did not settle')}
const popup=()=>document.querySelector('.notification-popup'), badge=()=>fixture.querySelector('.notification-count')
const row=(version='1',count=1)=>({id:'contact-1',title:'Test conversation',url:'/app/contacts?contact=1',version,count,popup:true})
const old=researchApi.notifications;let app,rows=[],calls=0,resolveDelayed,delay=false
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{template:'<div />'}}]})
const refresh=()=>window.dispatchEvent(new Event('personal-platform:notifications-changed'))
globalThis.requestAnimationFrame=window.requestAnimationFrame.bind(window)
globalThis.cancelAnimationFrame=window.cancelAnimationFrame.bind(window)
globalThis.getComputedStyle=window.getComputedStyle.bind(window)
try{
 window.sessionStorage.clear();tokenStorage.set('synthetic-notification-session')
 authStore.state.user={id:901,permissions:['research:use']}
 window.fetch=async()=>{throw new Error('Synthetic offline stream')}
 researchApi.notifications=async()=>{calls++;if(delay){delay=false;return new Promise(r=>resolveDelayed=r)}return rows.map(r=>({...r}))}
 await router.push('/app/home');await router.isReady()
 app=createApp(NotificationCenter).use(router);app.mount(fixture);await wait(()=>calls>0)
 assert(!badge(),'No unread counter for an empty inbox')
 rows=[row()];refresh();await wait(()=>popup());assert(badge().textContent==='1','Incoming content displays both a popup and an unread counter')
 await sleep(5150);await nextTick();assert(!popup()&&badge().textContent==='1','Popup expires after five seconds without marking content read')
 refresh();await sleep(700);assert(!popup(),'Repeated refresh does not replay an old notification')
 rows=[row('2')];refresh();await wait(()=>popup());assert(!!popup(),'A new message still alerts when the unread count stays the same')
 popup().dispatchEvent(new Event('mouseenter'));await sleep(5150);assert(!!popup(),'Hover pauses auto-dismiss so the popup remains usable')
 popup().querySelector('.notification-dismiss').click();await wait(()=>!popup());assert(!popup()&&!!badge(),'Closing a popup leaves the source unread')
 rows=[row('3')];refresh();await wait(()=>popup());rows=[];refresh();await wait(()=>!badge()&&!popup());assert(!popup(),'Reading in another view removes its popup and unread count together')
 // A read while a refresh is in flight must not reintroduce a stale unread entry.
 delay=true;refresh();await wait(()=>resolveDelayed);refresh();resolveDelayed([row('stale')]);await sleep(750);assert(!badge()&&!popup(),'An in-flight response is discarded and refreshed after a read event')
 rows=[{...row('4'),title:'<img src=x onerror=alert(1)>'}];refresh();await wait(()=>popup());assert(!popup().querySelector('img'),'Untrusted titles render as text')
 popup().querySelector('.notification-open').click();await wait(()=>router.currentRoute.value.path==='/app/contacts'&&!popup());assert(!popup()&&!!badge(),'Opening routes to the conversation; only its read receipt clears the badge')
 app.unmount();app=createApp(NotificationCenter).use(router);app.mount(fixture);await sleep(750);assert(!popup(),'Remounting the header does not replay delivered notifications')
 rows=[row('5')];refresh();await wait(()=>popup());rows=[];authStore.state.user={id:902,permissions:[]};await wait(()=>!popup()&&!badge());assert(!popup()&&!badge(),'Account switching immediately clears private notification UI')
 rows=[row('5')];refresh();await wait(()=>popup());assert(!!popup(),'Delivery state is isolated per account')
 Object.defineProperty(document,'hidden',{configurable:true,value:true});rows=[row('6')];refresh();await sleep(700);assert(popup()?.querySelector('strong')?.textContent==='Test conversation','Hidden-page refresh does not add a new popup')
 Object.defineProperty(document,'hidden',{configurable:true,value:false});document.dispatchEvent(new Event('visibilitychange'));await sleep(750)
 rows=[{...row('reminder'),id:'note-reviews',kind:'ACTION',popup:false}];refresh();await sleep(750);assert(!popup()&&!badge()&&!!fixture.querySelector('.notification-actions'),'Study reminders remain visible as actions without unread badges or popups')
 assert(!fixture.querySelector('.notification-list a'),'Review link respects research permissions')
 const backup={id:'backup-warning',title:'备份需要处理',url:'/app/research?tab=trash',count:1,kind:'ACTION',popup:false}
 rows=[backup];refresh();await wait(()=>fixture.querySelector('.notification-actions button')?.textContent.includes(backup.title))
 fixture.querySelector('.notification-actions button').click();await wait(()=>router.currentRoute.value.fullPath===backup.url);refresh();await sleep(700)
 assert(!badge()&&!popup()&&fixture.querySelector('.notification-actions button').textContent.includes(backup.title),'Opening a backup action reaches its actual page and does not reintroduce an unread badge or erase the warning')
 rows=[backup,{...row('older'),id:'mail-older',count:7,popup:false,kind:'MESSAGE'}];refresh();await wait(()=>badge()?.textContent==='7')
 assert(!popup(),'Older unread mail still counts even though its popup is disabled; pending actions do not count')
 rows=[backup];refresh();await wait(()=>!badge());assert(!!fixture.querySelector('.notification-actions'),'Reading the last message clears the badge even while a backup task is unresolved')
 rows=Array.from({length:7},(_,i)=>({...row(),id:'mail-'+i}));refresh();await wait(()=>document.querySelectorAll('.notification-popup').length===3);assert(document.querySelectorAll('.notification-popup').length===3,'Notification bursts are capped at three visible cards')
 let expired=false
 const onExpired=()=>{expired=true;authStore.state.user=null}
 window.addEventListener('personal-platform:session-expired',onExpired)
 window.fetch=async()=>new Response(null,{status:401})
 rows=[];authStore.state.user={id:903,permissions:[]};await wait(()=>expired)
 assert(tokenStorage.get()===null&&!badge(),'An expired stream immediately clears the rejected session instead of retrying unauthorized requests')
 window.removeEventListener('personal-platform:session-expired',onExpired)
 app.unmount();assert(!popup(),'Unmounting removes teleported private content and timers')
 document.getElementById('results').textContent=`PASS (${checks.length} assertions)\n`+checks.join('\n');document.documentElement.dataset.result='passed'
}catch(e){document.getElementById('results').textContent=e.stack;document.documentElement.dataset.result='failed'}finally{app?.unmount();researchApi.notifications=old}
