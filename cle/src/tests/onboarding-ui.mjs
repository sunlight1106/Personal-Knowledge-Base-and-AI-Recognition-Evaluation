import { createApp, nextTick } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import GettingStarted from '../components/GettingStarted.vue'
import { authStore } from '../stores/auth'
import { tokenStorage } from '../api/client'
import { setUiLanguage } from '../i18n'

const fixture=document.getElementById('fixture'), checks=[]
const assert=(value,label)=>{if(!value)throw Error(label);checks.push(label)}
const pause=async()=>{await new Promise(r=>setTimeout(r,10));await nextTick()}
const wait=async(test)=>{for(let i=0;i<80;i++){if(test())return;await pause()}throw Error('Timed out')}
const note={id:'note',title:'写下第一篇笔记',description:'说明',url:'/app/notes/new',completed:false}
const vocab={id:'vocabulary',title:'完成一次词汇练习',description:'说明',url:'/app/vocabulary',completed:false}
let rows=[note,vocab], fail=false, pending=null, calls=0, app
const reply=data=>new Response(JSON.stringify({success:true,data}),{headers:{'Content-Type':'application/json'}})
window.fetch=async()=>{calls++;if(pending){const fn=pending;pending=null;return fn()}if(fail)throw Error('Network failure');return reply(rows)}
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{template:'<p>destination</p>'}}]})
const button=text=>[...fixture.querySelectorAll('button')].find(b=>b.textContent.includes(text))
function account(id){tokenStorage.set('synthetic-onboarding-'+id);authStore.state.user={id,permissions:['note:read','note:write','vocabulary:use']}}
const mount=async()=>{app?.unmount();app=createApp(GettingStarted).use(router);app.mount(fixture);await wait(()=>!fixture.querySelector('[aria-busy=true]'))}
const originalSet=Object.getPrototypeOf(localStorage).setItem
try{
 await router.push('/');await router.isReady();await setUiLanguage('zh');account(810);await mount()
 assert(fixture.querySelector('.guide-next').textContent.includes(note.title),'First incomplete task is the primary next step')
 assert(fixture.querySelectorAll('.guide-step-detail[open]').length===1,'One concrete walkthrough opens automatically')
 assert(fixture.querySelector('progress').value===0&&calls===1,'Reading the guide does not complete tasks or write data')
 const choice=fixture.querySelector('select');choice.value='vocabulary';choice.dispatchEvent(new Event('change',{bubbles:true}));await pause()
 assert(fixture.querySelector('.guide-next').textContent.includes(vocab.title),'Selecting a goal changes the next step')
 assert(JSON.parse(localStorage.getItem('pkb:getting-started:810')).focus==='vocabulary','Goal preference belongs to the current account')
 rows=[note,{...vocab,completed:true}];button('检查完成情况').click();await wait(()=>fixture.querySelector('progress')?.value===1)
 assert(fixture.querySelector('.guide-next').textContent.includes(note.title),'After server confirmation the guide advances to an unfinished task')
 rows=[{...note,completed:true},{...vocab,completed:true}];button('检查完成情况').click();await wait(()=>!fixture.querySelector('.guide-next'))
 assert(fixture.textContent.includes('入门任务已完成'),'Completion has an explicit outcome without inventing more work')
 assert(fixture.querySelector('a[href="/app/settings?section=security"]')&&fixture.querySelector('a[href="/app/settings?section=privacy"]'),'Account and privacy controls remain easy to find')
 button('暂时收起').click();await pause();assert(!fixture.querySelector('.guide-basics'),'Guide can be collapsed')
 rows=[];account(811);await wait(()=>fixture.textContent.includes('当前账号暂未开放'))
 assert(!fixture.querySelector('.guide-next')&&!fixture.querySelector('a[href="/app/notes/new"]'),'A limited account gets explanations without unauthorized task links')
 assert(button('暂时收起'),'Another account does not inherit a collapsed guide')
 rows=[note];fail=true;account(812);await wait(()=>fixture.querySelector('[role=alert]'))
 assert(!fixture.querySelector('.guide-finished'),'A failed request is never shown as successful completion')
 fail=false;button('重试').click();await wait(()=>fixture.querySelector('.guide-next'))
 assert(fixture.querySelector('.guide-next').textContent.includes(note.title),'Retry recovers the guide')
 Object.getPrototypeOf(localStorage).setItem=function(){throw Error('Storage blocked')}
 button('暂时收起').click();await pause();button('展开引导').click();await pause()
 assert(fixture.querySelector('.guide-next'),'Blocked browser storage does not disable onboarding')
 Object.getPrototypeOf(localStorage).setItem=originalSet
 let resolveOld;pending=()=>new Promise(resolve=>{resolveOld=resolve});button('检查完成情况').click();await wait(()=>!!resolveOld)
 rows=[];account(813);await wait(()=>fixture.textContent.includes('当前账号暂未开放'))
 resolveOld(reply([note]));await pause();await pause()
 assert(!fixture.querySelector('.guide-next'),'An old account response cannot restore its task list after switching')
 await setUiLanguage('en');await pause()
 assert(!/[\u3400-\u9fff]/u.test(fixture.textContent),'Empty and safety guidance are fully translated')
 rows=[note];account(814);await wait(()=>fixture.querySelector('.guide-next'))
 assert(!/[\u3400-\u9fff]/u.test(fixture.textContent),'Next-step guidance and detailed instructions are translated')
 document.getElementById('results').textContent='PASS '+checks.length+'\n'+checks.join('\n');document.documentElement.dataset.result='passed'
}catch(error){document.getElementById('results').textContent='FAIL '+checks.length+': '+error.stack;document.documentElement.dataset.result='failed'}
finally{Object.getPrototypeOf(localStorage).setItem=originalSet;app?.unmount();await setUiLanguage('zh')}
