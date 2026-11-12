import {createApp,nextTick} from 'vue'
import {createRouter,createMemoryHistory} from 'vue-router'
import NotificationCenter from '../components/NotificationCenter.vue'
import {researchApi} from '../api/research.ts'
import {releaseApi} from '../api/releases.ts'
import {authStore} from '../stores/auth.ts'
import {tokenStorage} from '../api/client.ts'
import {setUiLanguage} from '../i18n'
const fixture=document.getElementById('fixture'),checks=[]
const assert=(ok,label)=>{if(!ok)throw Error(label);checks.push(label)}
const sleep=ms=>new Promise(r=>setTimeout(r,ms))
const wait=async fn=>{for(let i=0;i<250;i++){await sleep(10);await nextTick();if(fn())return}throw Error('Announcement UI did not settle')}
globalThis.requestAnimationFrame=window.requestAnimationFrame.bind(window)
globalThis.cancelAnimationFrame=window.cancelAnimationFrame.bind(window)
globalThis.getComputedStyle=window.getComputedStyle.bind(window)
const entry=version=>({id:'release-'+version,title:'Server title',url:'/updates',count:1,version,popup:true,kind:'RELEASE'})
const text=zh=>({zh,en:'English '+zh})
const notice=version=>({read:false,release:{version,date:'2026-11-12',title:text('本次更新'),added:[text('新增完整更新公告')],fixed:[text('修复旧列表覆盖')],improved:[text('减少回放计算')],notes:[text('保留个人资料')]}})
const popup=()=>document.querySelector('.notification-popup'),dialog=()=>document.querySelector('dialog'),badge=()=>fixture.querySelector('.notification-count')
const button=(root,label)=>[...root.querySelectorAll('button')].find(b=>b.textContent.trim()===label)
const refresh=()=>window.dispatchEvent(new Event('personal-platform:notifications-changed'))
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{template:'<div />'}}]})
let rows=[entry('0.15')],current=notice('0.15'),readCalls=0,failRead=false,failLoad=false,resolveRead,holdRead=false,app
const oldNotifications=researchApi.notifications,oldCurrent=releaseApi.current,oldRead=releaseApi.read
try {
 window.sessionStorage.clear();tokenStorage.set('synthetic-release');authStore.state.user={id:901,permissions:[]}
 window.fetch=async()=>{throw Error('Synthetic offline stream')}
 researchApi.notifications=async()=>rows.map(row=>({...row}))
 releaseApi.current=async()=>{if(failLoad)throw Error('offline');return structuredClone(current)}
 releaseApi.read=async version=>{readCalls++;if(holdRead)return new Promise(resolve=>resolveRead=resolve);if(failRead)throw Error('offline');rows=rows.filter(row=>row.version!==version)}
 await router.push('/app/home');await router.isReady();app=createApp(NotificationCenter).use(router);app.mount(fixture)
 await wait(()=>popup());assert(badge()?.textContent==='1','An unread release contributes one notification')
 assert(popup().querySelectorAll('button').length===2&&button(popup(),'查看详情')&&button(popup(),'已读'),'Announcement popup offers exactly View details and Mark as read')
 await sleep(5150);await nextTick();assert(!popup()&&!!badge(),'Auto-dismiss leaves the announcement unread and available in the center')
 button(fixture,'查看详情').click();await wait(()=>dialog()?.textContent.includes('保留个人资料'))
 assert(readCalls===0&&!!badge(),'Viewing full details does not acknowledge the release')
 for(const label of ['新增功能','体验优化','问题修复','更新后需要注意'])assert(dialog().textContent.includes(label),'Details include '+label)
 await setUiLanguage('en',false);await nextTick();assert(dialog().textContent.includes('English 本次更新')&&button(dialog(),'Mark as read'),'Details and actions switch to English')
 await setUiLanguage('zh',false);await nextTick()
 failRead=true;button(dialog(),'已读').click();await wait(()=>dialog()?.querySelector('[role=alert]'))
 assert(!!badge()&&button(dialog(),'已读')&&!button(dialog(),'已读').disabled,'Failed receipt retains unread state and offers retry')
 failRead=false;button(dialog(),'已读').click();await wait(()=>!dialog()&&!badge())
 assert(readCalls===2,'Successful retry removes both dialog and badge')
 refresh();await sleep(700);assert(!popup()&&!badge(),'A read release stays hidden after refresh')
 rows=[entry('0.16')];current=notice('0.16');refresh();await wait(()=>popup())
 assert(popup().textContent.includes('0.16'),'A later version produces a new announcement')
 failLoad=true;button(popup(),'查看详情').click();await wait(()=>dialog()?.textContent.includes('加载失败'))
 assert(!!badge()&&button(dialog(),'重试'),'Failed details do not lose the unread announcement')
 failLoad=false;button(dialog(),'重试').click();await wait(()=>dialog()?.textContent.includes('0.16'))
 dialog().querySelector('[aria-label="关闭"]').click();await wait(()=>!dialog())
 assert(!!badge(),'Closing details leaves the release unread')
 holdRead=true;const before=readCalls;button(fixture,'已读').click();button(fixture,'已读').click();await wait(()=>resolveRead)
 assert(readCalls===before+1&&button(fixture,'已读').disabled,'Repeated clicks send only one receipt')
 rows=[entry('0.16')];authStore.state.user={id:902,permissions:[]};await wait(()=>popup())
 resolveRead();await sleep(50);assert(!!badge()&&popup()?.textContent.includes('0.16'),'A late receipt from another account cannot clear the new account notification')
 holdRead=false;rows=[];refresh();await wait(()=>!badge()&&!popup())
 assert(!popup(),'A read receipt from another tab clears the popup and badge on refresh')
 rows=[entry('0.17')];current=notice('0.17');refresh();await wait(()=>popup());button(popup(),'查看详情').click();await wait(()=>dialog()?.textContent.includes('0.17'))
 authStore.state.user=null;await wait(()=>!dialog()&&!badge());assert(!dialog(),'Logout closes the announcement and clears account-owned UI')
 document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${checks.length} assertions)\n`+checks.join('\n')
}catch(e){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=e.stack}
finally{app?.unmount();researchApi.notifications=oldNotifications;releaseApi.current=oldCurrent;releaseApi.read=oldRead}
