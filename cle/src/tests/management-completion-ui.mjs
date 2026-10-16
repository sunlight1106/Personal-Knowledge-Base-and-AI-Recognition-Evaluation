// Synthetic accounts and content only; no real data is changed.
import { createApp, h, nextTick } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import GroupLifecycle from '../components/GroupLifecycle.vue'
import KnowledgeView from '../views/KnowledgeView.vue'
import NoteTrashPanel from '../components/NoteTrashPanel.vue'
import NoteReaderView from '../views/NoteReaderView.vue'
import DownloadsView from '../views/DownloadsView.vue'
import { api, tokenStorage } from '../api/client.ts'
import { researchApi } from '../api/research.ts'
import { authStore } from '../stores/auth.ts'
const fixture = document.getElementById('fixture'), checks = []
const assert = (ok, text) => { if (!ok) throw new Error(text); checks.push(text) }
const wait = async fn => { for(let i=0;i<150;i++){ await new Promise(r=>setTimeout(r,5)); await nextTick(); if(fn())return } throw new Error('Timed out waiting for UI') }
const button = (label, root=document) => [...root.querySelectorAll('button')].find(b=>b.textContent.trim()===label)
const enter = async (el,value) => { el.value=value; el.dispatchEvent(new Event('input',{bubbles:true})); await nextTick() }
const change = async (el,value) => { el.value=value; el.dispatchEvent(new Event('change',{bubbles:true})); await nextTick() }
const response = data => new Response(JSON.stringify({success:true,data}),{headers:{'Content-Type':'application/json'}})
let app, router, refreshed=0
async function mount(component,props={},path='/test') {
 router=createRouter({history:createMemoryHistory(),routes:[{path:'/app/notes',name:'notes',component:{template:'<div />'}},{path:'/notes/new',name:'note-create',component:{template:'<div />'}},{path:'/notes/:id/edit',name:'note-edit',component:{template:'<div />'}},{path:component===NoteReaderView?'/app/notes/:id':'/:pathMatch(.*)*',component:{render:()=>h(component,props)}}]})
 await router.push(path);await router.isReady();app=createApp({render:()=>h(RouterView)});app.use(router);app.mount(fixture);await nextTick()
}
function unmount(){app?.unmount();app=null}
const user=permissions=>{authStore.state.user={id:7,roleCode:'RESEARCHER',permissions}}
const group={id:4,name:'Synthetic group',ownerId:7,currentPermissions:['CONTENT_READ','SETTINGS_WRITE'],members:[{userId:7,displayName:'Owner',username:'owner',role:'OWNER'},{userId:8,displayName:'Member',username:'member',role:'MEMBER'}]}
let state={archived:false,dissolved:false,acceptRequests:false}, mutations=[], deferred, failing=false
window.fetch=async(url,init={})=>{
 const path=String(url),method=init.method||'GET'
 if(method!=='GET'){
  mutations.push({path,method,body:JSON.parse(init.body)})
  if(deferred===true)return new Promise(resolve=>{deferred=()=>{state={...state,archived:true,dissolved:true};resolve(response(null))}})
  if(failing)return new Response(JSON.stringify({success:false,error:{code:'DENIED',message:'Wrong password'}}),{status:400,headers:{'Content-Type':'application/json'}})
  if(path.endsWith('/lifecycle'))state={...state,...JSON.parse(init.body)}
  return response(null)
 }
 if(path.endsWith('/lifecycle'))return response(state)
 if(path.endsWith('/invitations')||path.endsWith('/reports'))return response([])
 throw new Error('Unexpected fetch '+path)
}
try{
 tokenStorage.set('synthetic-management');user(['message:read','group:use','knowledge:read','knowledge:write','note:read','note:write'])
 await mount(GroupLifecycle,{group,owner:true,manager:true,onRefresh:()=>refreshed++});await wait(()=>button('解散群组'))
 assert(!button('解散群组').closest('details'),'Dissolve is visible without opening nested menus')
 button('解散群组').click();await nextTick()
 assert(button('确认解散').disabled,'Dissolution requires both typed name and current password')
 await enter(document.querySelector('.lifecycle-form input:not([type=password])'),'Wrong group')
 await enter(document.querySelector('.lifecycle-form input[type=password]'),'synthetic-password')
 assert(button('确认解散').disabled,'Wrong group name cannot submit dissolution')
 button('取消').click();await nextTick();assert(!document.querySelector('.lifecycle-form')&&mutations.length===0,'Cancelling a destructive operation never writes')
 assert(document.activeElement===button('解散群组'),'Cancel restores focus to the triggering control')
 button('转让群主').click();await nextTick();await change(document.querySelector('.lifecycle-form select'),'8');await enter(document.querySelector('.lifecycle-form input'),'synthetic-password')
 failing=true;document.querySelector('.lifecycle-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await wait(()=>document.querySelector('[role=alert]'))
 assert(document.querySelector('.lifecycle-form input').value===''&&button('确认转让').disabled,'Failed proof clears password and requires re-entry')
 assert(mutations[0].body.targetId===8&&mutations[0].path.endsWith('/transfer'),'Transfer sends the chosen existing member only')
 failing=false;button('取消').click();await nextTick();button('解散群组').click();await nextTick();await enter(document.querySelector('.lifecycle-form input:not([type=password])'),group.name);await enter(document.querySelector('.lifecycle-form input[type=password]'),'synthetic-password')
 deferred=true;const f=document.querySelector('.lifecycle-form');f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await wait(()=>typeof deferred==='function')
 assert(mutations.filter(m=>m.path.endsWith('/dissolve')).length===1,'Double submission causes only one dissolution request')
 assert(document.querySelector('.lifecycle-form fieldset').disabled,'Proof fields lock during submission')
 deferred();await wait(()=>!button('解散群组'))
 assert(fixture.textContent.includes('已解散')&&refreshed===1,'Successful dissolution shows read-only history state and refreshes parent')
 unmount();state={archived:false,dissolved:false,acceptRequests:false};await mount(GroupLifecycle,{group,owner:false,manager:true});await wait(()=>button('申请、邀请与举报 · 0 +'))
 assert(!button('解散群组')&&!button('转让群主')&&!button('归档群组'),'Group managers cannot see owner-only lifecycle actions');unmount()
 const topics=[{id:1,name:'Public',domain:'Study',builtin:true,entryCount:1},{id:2,name:'Personal',domain:'Study',description:'Before',builtin:false,entryCount:1}]
 const cards=[{id:'card',topicId:2,topicName:'Personal',domain:'Study',title:'Original',body:'Safe body',summary:'Summary',tags:['tag'],builtin:false,updatedAt:'2026-10-16'}]
 const changes=[]
 api.knowledgeTopics=async()=>topics;api.knowledgeEntries=async()=>cards;api.knowledgeEntry=async()=>cards[0]
 api.updateKnowledgeTopic=async(id,payload)=>{changes.push({id,...payload});Object.assign(topics[1],payload);return topics[1]}
 api.updateKnowledgeEntry=async(id,payload)=>{changes.push({id,...payload});Object.assign(cards[0],payload,{tags:payload.tags?payload.tags.split(','):[]});return cards[0]}
 await mount(KnowledgeView);await wait(()=>document.querySelector('.kb-card'));[...document.querySelectorAll('.kb-topic')].find(b=>b.textContent.includes('Personal')).click();await wait(()=>button('编辑主题'))
 assert(button('删除主题').disabled,'Nonempty topic deletion is disabled with a move-or-delete explanation')
 button('编辑主题').click();await wait(()=>document.querySelector('dialog[open]'))
 await enter(document.querySelector('dialog input[maxlength="120"]'),'Renamed');document.querySelector('dialog form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await wait(()=>!document.querySelector('dialog'))
 assert(changes[0].id===2&&changes[0].name==='Renamed','Editing a topic updates its existing ID instead of creating a duplicate')
 document.querySelector('.kb-card').click();await wait(()=>button('编辑知识卡'));button('编辑知识卡').click();await wait(()=>document.querySelector('dialog'))
 assert(document.querySelector('dialog input[maxlength="180"]').value==='Original','Editing pre-fills the current knowledge card')
 await enter(document.querySelector('dialog input[maxlength="180"]'),'Revised');await enter(document.querySelector('dialog input[maxlength="500"]'),'')
 document.querySelector('dialog form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await wait(()=>!document.querySelector('dialog'))
 assert(changes[1].id==='card'&&changes[1].title==='Revised'&&changes[1].tags==='','Existing card edits support clearing tags')
 assert(document.querySelector('.kb-reader').textContent.includes('Revised'),'Saved card remains open with current content');unmount()
 user(['knowledge:read']);await mount(KnowledgeView);await wait(()=>document.querySelector('.kb-card'));document.querySelector('.kb-card').click();await wait(()=>document.querySelector('.kb-reader'))
 assert(!button('编辑知识卡')&&!button('新建主题'),'Read-only knowledge permissions hide write actions');unmount()
 let trash=[{id:'deleted',title:'Deleted synthetic note',deletedAt:'2026-10-16'}],restores=0,purges=0
 researchApi.trash=async()=>trash;researchApi.restore=async()=>{restores++;trash=[]};researchApi.purge=async()=>{purges++;trash=[]}
 user(['note:read']);await mount(NoteTrashPanel);await wait(()=>fixture.textContent.includes('Deleted synthetic note'))
 assert(!button('恢复')&&!button('永久删除'),'Read-only trash access does not expose restoration or deletion');unmount()
 user(['note:read','note:write']);await mount(NoteTrashPanel);await wait(()=>button('恢复'));button('永久删除').click();await nextTick()
 assert(document.querySelector('[role=alertdialog]')&&purges===0,'Permanent deletion opens a named confirmation before writing')
 button('取消').click();await nextTick();assert(purges===0,'Cancelling purge preserves the note');button('恢复').click();await wait(()=>fixture.textContent.includes('回收站是空的'))
 assert(restores===1&&purges===0,'Restore updates trash without permanently deleting');unmount()
 user(['note:read']);api.note=async()=>({id:'readable',title:'Read only',body:'A **safe** note.',library:'Study',contentFormat:'MARKDOWN',updatedAt:'2026-10-16'})
 // Reader uses route.params.id; catch-all mounting does not require the live router or a real account.
 await mount(NoteReaderView,{},'/app/notes/readable');await wait(()=>fixture.textContent.includes('Read only'))
 assert(!button('编辑笔记')&&button('导出笔记'),'Read-only users can read and export without receiving edit controls')
 assert(document.querySelector('.note-preview')||fixture.textContent.includes('safe'),'Note reader renders note content');unmount()
 user(['file:read']);let attempts=0;api.files=async()=>{if(++attempts===1)throw new Error('Temporary failure');return []}
 await mount(DownloadsView);await wait(()=>button('重试'));button('重试').click();await wait(()=>fixture.textContent.includes('暂无文件'))
 assert(attempts===2&&!document.querySelector('[role=alert]'),'Failed downloads listing can recover through retry');unmount()
 document.getElementById('results').textContent=`PASS ${checks.length}\n`+checks.join('\n');document.documentElement.dataset.result='passed'
}catch(e){document.getElementById('results').textContent=e.stack;document.documentElement.dataset.result='failed'}finally{unmount();tokenStorage.clear()}
