// Synthetic users only; this suite never sends requests to a running platform.
import { createApp, h, nextTick } from 'vue'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import ContactsView from '../views/ContactsView.vue'
import { authStore } from '../stores/auth.ts'
import { tokenStorage } from '../api/client.ts'
const fixture = document.getElementById('fixture'), passed = []
const assert = (ok, label) => { if (!ok) throw new Error(label); passed.push(label) }
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const wait = async predicate => { for (let i=0;i<600;i++) { await sleep(5); await nextTick(); if(predicate()) return } throw new Error('UI wait timed out after: '+passed.at(-1)+'; '+fixture.textContent.slice(-700)+'; '+(dialog()?.textContent||'')) }
const button = text => [...fixture.querySelectorAll('button')].find(n => n.textContent.includes(text))
const dialog = () => document.querySelector('[role=alertdialog]')
const confirm = () => [...dialog().querySelectorAll('button')].at(-1).click()
const cancel = () => dialog().querySelector('button').click()
const envelope = data => new Response(JSON.stringify({success:true,data}), {headers:{'Content-Type':'application/json'}})
const input = async (selector, value) => { const node=fixture.querySelector(selector); node.value=value; node.dispatchEvent(new Event('input',{bubbles:true})); await nextTick() }
const base = {id:11,userId:2,username:'blocked_one',displayName:'Same name',identityCode:'PKB-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',remark:'Research partner',status:'ACCEPTED',incoming:false,blockedByMe:true,available:false,pinned:false,muted:false,unreadCount:0,clearedThrough:0}
const stranger = {id:4,username:'stranger',displayName:'<img src=x>',identityCode:'PKB-CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC'}
let contacts=[base,{...base,id:12,userId:3,username:'blocked_two',remark:'',identityCode:'PKB-BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB'}]
let blockCalls=0, unblockCalls=0, failUnblock=false, deferred, pendingSearch, app
window.fetch = async (url,init={}) => {
  const path=String(url)
  if(path.endsWith('/social/settings')) return envelope({discoverable:true})
  if(path.endsWith('/social/contacts')) return envelope(authStore.state.user?.id===9?[]:contacts.filter(c=>c.blockedByMe||c.status==='ACCEPTED'))
  if(path.includes('/social/people?')) {
    if (pendingSearch === 'wait') return new Promise(resolve => { pendingSearch = () => resolve(envelope([stranger])) })
    return envelope([stranger])
  }
  if(path.endsWith('/social/blacklist')) {
    blockCalls++; assert(JSON.parse(init.body).userId===4,'Direct block sends the person ID, not a contact ID')
    contacts.push({...base,...stranger,id:13,userId:4,status:'REMOVED',remark:''})
    return envelope(null)
  }
  if(/\/social\/contacts\/\d+$/.test(path)) {
    unblockCalls++
    if(failUnblock) throw new TypeError('Synthetic offline')
    if(deferred==='wait') return new Promise(resolve=>{deferred=()=>resolve(envelope(null))})
    contacts=contacts.map(c=>c.id===Number(path.split('/').at(-1))?{...c,blockedByMe:false,available:true}:c)
    return envelope(null)
  }
  throw new Error('Unexpected request: '+path)
}
try {
  authStore.state.user={id:1,permissions:['contacts:use']};tokenStorage.set('synthetic-blacklist')
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:ContactsView}]})
  await router.push('/app/contacts?tab=blacklist');await router.isReady()
  app=createApp({render:()=>h(RouterView)});app.use(router);app.mount(fixture)
  await wait(()=>fixture.querySelectorAll('.blacklist-card').length===2)
  assert(!fixture.querySelector('.chat-compose'),'Privacy deep link opens the blacklist, without a composer')
  pendingSearch='wait';await input('#people-query','stranger');fixture.querySelector('.people-search').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))
  await wait(()=>typeof pendingSearch==='function');button('黑名单').click();await nextTick();pendingSearch();await sleep(30);pendingSearch=null
  assert(!!fixture.querySelector('#blacklist-query')&&!button('申请添加'),'A delayed directory search cannot replace a newly selected blacklist tab')
  await input('#blacklist-query','pkb-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb')
  assert(fixture.querySelectorAll('.blacklist-card').length===1&&fixture.querySelector('.blacklist-card').textContent.includes('blocked_two'),'Identity code distinguishes duplicate display names, case-insensitively')
  await input('#blacklist-query','Research partner')
  assert(fixture.querySelectorAll('.blacklist-card').length===1,'Private remarks can be searched in the blacklist')
  await input('#blacklist-query','not found')
  assert(fixture.textContent.includes('没有匹配的黑名单用户')&&!fixture.querySelector('.blacklist-card'),'No-match state does not claim the blacklist is empty')
  await input('#blacklist-query','')
  button('解除拉黑').click();await nextTick();cancel();await nextTick()
  assert(unblockCalls===0&&fixture.querySelectorAll('.blacklist-card').length===2,'Cancelling unblock sends no mutation')
  failUnblock=true;button('解除拉黑').click();await nextTick();confirm()
  await wait(()=>dialog()?.textContent.includes('连接已中断'))
  assert(fixture.querySelectorAll('.blacklist-card').length===2,'A failed unblock preserves the block and offers retry')
  failUnblock=false;confirm();await wait(()=>!dialog())
  assert(fixture.querySelectorAll('.blacklist-card').length===1&&fixture.querySelector('[role=status]').textContent.includes('已解除拉黑'),'Successful unblock updates the list and announces completion')
  await input('#blacklist-query','blocked_two')
  await input('#people-query','stranger');fixture.querySelector('.people-search').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))
  await wait(()=>button('加入黑名单'));button('加入黑名单').click();await nextTick()
  assert(blockCalls===0&&dialog().textContent.includes('不会向对方发送好友申请'),'Direct stranger block explains its effect before sending')
  confirm();await wait(()=>!dialog()&&blockCalls===1)
  assert(fixture.querySelectorAll('.blacklist-card').length===2&&!fixture.querySelector('.blacklist-card img'),'Blocked stranger is listed safely without interpreting their name as markup')
  deferred='wait';button('解除拉黑').click();await nextTick();confirm();await wait(()=>typeof deferred==='function')
  tokenStorage.set('synthetic-switched');authStore.state.user={id:9,permissions:['contacts:use']};await nextTick();deferred();await sleep(40)
  assert(!fixture.querySelector('.blacklist-card')&&!dialog()&&!fixture.querySelector('[role=status]'),'Late unblock completion cannot restore the old account list, modal or success message')
  document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${passed.length} assertions)`
} catch(error) {document.documentElement.dataset.result='failed';document.getElementById('results').textContent=error.stack||String(error)}
finally {app?.unmount();tokenStorage.clear();authStore.state.user=null}
