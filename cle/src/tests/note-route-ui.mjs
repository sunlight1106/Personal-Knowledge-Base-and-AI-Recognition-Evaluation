import { createApp, h, nextTick } from 'vue'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import NoteEditorView from '../views/NoteEditorView.vue'
import { api, tokenStorage } from '../api/client.ts'
import { authStore } from '../stores/auth.ts'
const fixture=document.getElementById('fixture'),checks=[]
const assert=(ok,label)=>{if(!ok)throw new Error(label);checks.push(label)}
const wait=async fn=>{for(let i=0;i<250;i++){await new Promise(r=>setTimeout(r,10));await nextTick();if(fn())return}throw new Error('Note route did not settle')}
const original={...api};let app,created=0,saved
globalThis.requestAnimationFrame=window.requestAnimationFrame.bind(window)
const set=async(el,text)=>{el.value=text;el.dispatchEvent(new Event('input',{bubbles:true}));await nextTick()}
try{
 tokenStorage.set('synthetic-note-route');authStore.state.user={id:991,permissions:['note:read','note:write']}
 window.fetch=async()=>new Response(JSON.stringify({success:true,data:[]}),{headers:{'Content-Type':'application/json'}})
 api.notes=async()=>[];api.noteShares=async()=>[]
 api.createNote=async body=>{created++;saved={...body,id:'saved-note',revision:0,tags:[],status:{code:'DRAFT'},references:[],shareCount:0,updatedAt:new Date().toISOString()};return saved}
 api.updateNote=async(id,body)=>{saved={...saved,...body,tags:[],status:{code:'DRAFT'},revision:saved.revision+1};return saved}
 api.note=async()=>saved
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/app/notes/new',name:'note-create',component:NoteEditorView},{path:'/app/notes/:id/edit',name:'note-edit',component:NoteEditorView},{path:'/app/notes',name:'notes',component:{template:'<p>Notes</p>'}}]})
 await router.push('/app/notes/new');await router.isReady();app=createApp({render:()=>h(RouterView)}).use(router);app.mount(fixture)
 await wait(()=>fixture.querySelector('.note-title-input')&&!fixture.querySelector('.note-title-input').disabled)
 await set(fixture.querySelector('.note-title-input'),'Saved route test');await set(fixture.querySelector('textarea.note-editor-textarea'),'First saved body')
 await wait(()=>router.currentRoute.value.params.id==='saved-note')
 assert(router.currentRoute.value.name==='note-edit','First autosave replaces the new route with the saved note URL')
 assert(created===1,'Automatic route replacement creates exactly one note')
 assert(fixture.querySelector('textarea.note-editor-textarea').value==='First saved body','Internal navigation preserves the current text')
 await set(fixture.querySelector('textarea.note-editor-textarea'),'Updated body');await wait(()=>saved.body==='Updated body')
 assert(created===1,'Further edits update the existing note instead of creating another')
 await router.push('/app/notes');await router.push('/app/notes/saved-note/edit');await wait(()=>fixture.querySelector('textarea.note-editor-textarea')?.value==='Updated body')
 assert(fixture.querySelector('.note-title-input').value==='Saved route test','Reopening the saved URL loads the persisted title and body')
 document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${checks.length} assertions)\n`+checks.join('\n')
}catch(e){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=e.stack}finally{app?.unmount();Object.assign(api,original)}
