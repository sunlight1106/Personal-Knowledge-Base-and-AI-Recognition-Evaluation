import { createApp, h, nextTick, reactive } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import WorkspaceQuickNav from '../components/WorkspaceQuickNav.vue'
import { setUiLanguage } from '../i18n'

const fixture=document.getElementById('fixture'), checks=[]
const assert=(value,message)=>{if(!value)throw new Error(message);checks.push(message)}
const originalRect=HTMLElement.prototype.getBoundingClientRect
const originalObserver=globalThis.ResizeObserver
let width=1300,disconnected=false,app,callback
globalThis.ResizeObserver=window.ResizeObserver=class { constructor(fn){callback=fn} observe(){} disconnect(){disconnected=true} }
HTMLElement.prototype.getBoundingClientRect=function(){
  const w=this.classList.contains('workspace-quick')?width:this.classList.contains('quick-more-measure')?80:this.classList.contains('quick-measure-item')?100:0
  return {width:w,height:40,x:0,y:0,left:0,top:0,right:w,bottom:40,toJSON(){}}
}
const state=reactive({items:Array.from({length:12},(_,i)=>({to:`/page-${i}`,label:`Page ${i}`,icon:'docs'}))})
const links=()=>[...fixture.querySelectorAll('.workspace-quick>a')]
const settle=async()=>{await nextTick();await nextTick();await nextTick()}
try {
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{render:()=>h('div')}}]})
  await router.push('/page-11');await router.isReady()
  app=createApp({render:()=>h(WorkspaceQuickNav,{items:state.items})}).use(router);app.mount(fixture);await settle()
  assert(links().length===12&&!fixture.querySelector('details'),'Wide navigation displays all twelve pins')
  width=500;callback();await settle()
  assert(links().length===3&&fixture.querySelectorAll('.quick-overflow-list a').length===9,'Limited width moves only the remaining links into More')
  assert(fixture.querySelector('summary').classList.contains('has-current'),'More indicates when the current page is inside its list')
  await setUiLanguage('en',false);await settle()
  assert(fixture.querySelector('summary').getAttribute('aria-label')==='More pinned pages'&&fixture.querySelector('summary span').textContent==='More','Overflow labels translate to English without losing pins')
  await setUiLanguage('zh',false);await settle()
  let menu=fixture.querySelector('details');menu.open=true
  menu.dispatchEvent(new window.KeyboardEvent('keydown',{key:'Escape',bubbles:true}));await settle()
  assert(!menu.open&&document.activeElement===menu.querySelector('summary'),'Escape closes overflow and restores focus to its trigger')
  menu.open=true;document.body.dispatchEvent(new window.MouseEvent('pointerdown',{bubbles:true}));await settle()
  assert(!menu.open,'An outside pointer closes overflow')
  menu.open=true;await router.push('/page-0');await settle()
  assert(!menu.open&&!fixture.querySelector('summary').classList.contains('has-current'),'Navigation closes overflow and updates current-page indication')
  width=75;callback();await settle()
  assert(links().length===0&&fixture.querySelectorAll('.quick-overflow-list a').length===12,'Very narrow navigation keeps all pages reachable from More')
  state.items=state.items.slice(0,2);await settle()
  assert(fixture.querySelectorAll('.quick-overflow-list a').length===2,'Removed or revoked pins immediately disappear from overflow')
  width=500;window.dispatchEvent(new Event('resize'));await settle()
  assert(links().length===2&&!fixture.querySelector('details'),'Resizing wider restores links without a stale dropdown')
  state.items=[];await settle()
  assert(!!fixture.querySelector('.workspace-quick-hint')&&!fixture.querySelector('details'),'Empty pin lists show guidance and no empty overflow')
  assert(fixture.querySelector('.quick-measure').hasAttribute('inert')&&fixture.querySelector('.quick-measure').getAttribute('aria-hidden')==='true','Measurement content is neither focusable nor announced')
  app.unmount();app=undefined;assert(disconnected,'Unmount releases the resize observer')
  document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${checks.length} assertions)\n${checks.join('\n')}`
}catch(error){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=error.stack;throw error}
finally{app?.unmount();HTMLElement.prototype.getBoundingClientRect=originalRect;globalThis.ResizeObserver=originalObserver;delete window.ResizeObserver}
