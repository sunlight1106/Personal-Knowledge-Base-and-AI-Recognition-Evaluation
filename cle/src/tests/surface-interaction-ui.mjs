import { createApp, h, nextTick } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { useSurfaceInteraction } from '../lib/useSurfaceInteraction'
import { motionState } from '../lib/motion'
import HomeWorkspacePreview from '../components/HomeWorkspacePreview.vue'
import { setUiLanguage } from '../i18n'

const checks=[], fixture=document.getElementById('fixture')
const assert=(ok,message)=>{if(!ok)throw Error(message);checks.push(message)}
const original={media:window.matchMedia,rect:HTMLElement.prototype.getBoundingClientRect,raf:window.requestAnimationFrame,cancel:window.cancelAnimationFrame}
const listeners=new Set(), frames=new Map(), media={matches:true,addEventListener:(_,fn)=>listeners.add(fn),removeEventListener:(_,fn)=>listeners.delete(fn)}
let sequence=0,app,requests=0
window.matchMedia=()=>media
window.requestAnimationFrame=fn=>{frames.set(++sequence,fn);return sequence}
window.cancelAnimationFrame=id=>frames.delete(id)
HTMLElement.prototype.getBoundingClientRect=()=>({left:100,top:50,right:500,bottom:250,width:400,height:200})
window.fetch=async()=>{requests++;throw Error('Preview must stay local')}
const flush=()=>{const queue=[...frames.values()];frames.clear();queue.forEach(fn=>fn(0))}
const move=(target,x=400,y=150,options={})=>{
  const event=new window.MouseEvent('pointermove',{bubbles:true,clientX:x,clientY:y,buttons:options.buttons||0})
  Object.defineProperty(event,'pointerType',{value:options.type||'mouse'})
  target.dispatchEvent(event)
}
const light=()=>fixture.querySelector('.surface-light')
try{
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{render:()=>h('div')}}]})
  await router.push('/');await router.isReady()
  app=createApp({setup(){useSurfaceInteraction();return()=>h('div',[
    h('section',{class:'home-chapter water-surface',id:'first'},[
      h('div',{class:'ambient-waves','data-running':'true',id:'water'}),h('span',{id:'label'},'Card'),h('input',{id:'input'}),
      h('div',{contenteditable:'true'},[h('span',{id:'editor'},'Editable')])
    ]),
    h('article',{class:'shelf-book',id:'second'},'Book'),h('p',{id:'outside'},'Text'),h(HomeWorkspacePreview)
  ])}}).use(router)
  app.mount(fixture);await nextTick()
  const first=fixture.querySelector('#first'),label=fixture.querySelector('#label'),water=fixture.querySelector('#water')
  for(let i=0;i<100;i++)move(label,100+i*3,150)
  assert(frames.size===1&&!light(),'Pointer bursts schedule one frame instead of creating an idle animation loop')
  flush()
  assert(light()?.parentElement===first&&light().getAttribute('aria-hidden')==='true','Nested labels activate only one decorative card light')
  assert(parseFloat(light().style.getPropertyValue('--surface-x'))>70,'Pointer light follows the latest event position')
  assert(parseFloat(water.style.getPropertyValue('--surface-water-x'))<=10,'Water displacement remains bounded')
  assert(frames.size===0,'No frame is scheduled while the pointer is stationary')
  move(fixture.querySelector('#second'));flush()
  assert(fixture.querySelectorAll('.surface-light').length===1&&!first.querySelector('.surface-light'),'Moving between cards does not leave duplicate lights')
  assert(!water.style.getPropertyValue('--surface-water-x'),'Leaving a water surface restores its neutral position')
  for(const id of ['input','editor','outside']){
    move(label);flush();move(fixture.querySelector(`#${id}`));flush()
    assert(!light(),`${id} remains free of pointer decoration`)
  }
  move(label,400,150,{buttons:1});flush();assert(!light(),'Dragging and selecting text do not trigger pointer motion')
  move(label,400,150,{type:'touch'});flush();assert(!light(),'Touch input does not inherit desktop hover decoration')
  move(label);first.setAttribute('aria-disabled','true');flush()
  assert(!light(),'A control disabled before the next frame is not decorated');first.removeAttribute('aria-disabled')
  move(label);first.remove();flush();assert(!light(),'Detached targets cannot receive delayed decoration');fixture.firstElementChild.prepend(first)
  for(const type of ['scroll','pointerdown']){
    move(label);flush();document.dispatchEvent(new window.Event(type));assert(!light()&&!water.style.getPropertyValue('--surface-water-x'),`${type} clears hover feedback`)
  }
  move(label);flush();document.dispatchEvent(new window.KeyboardEvent('keydown',{key:'Tab',bubbles:true}));assert(!light(),'Keyboard navigation clears stale pointer feedback')
  move(label);flush();document.dispatchEvent(new window.MouseEvent('pointerout',{relatedTarget:null}));assert(!light(),'Leaving the browser clears feedback')
  move(label);flush();window.dispatchEvent(new window.Event('resize'));assert(!light(),'Resize clears old pointer coordinates')
  move(label);flush();window.dispatchEvent(new window.Event('blur'));assert(!light(),'Window blur clears feedback')
  move(label);flush();await router.push('/next');await nextTick();assert(!light(),'Route changes clear feedback')
  move(label);flush();motionState.paused=true;await nextTick();move(label);flush();assert(!light(),'Pause stops active and future hover feedback');motionState.paused=false
  move(label);flush();motionState.reduced=true;await nextTick();move(label);flush();assert(!light(),'Reduced motion disables hover decoration');motionState.reduced=false
  move(label);flush();motionState.visible=false;await nextTick();assert(!light(),'A hidden tab releases active feedback');motionState.visible=true
  move(label);flush();media.matches=false;for(const fn of listeners)fn();assert(!light(),'Switching to a coarse pointer clears feedback');media.matches=true

  fixture.querySelector('#preview-tab-words').click();await nextTick()
  const reveal=fixture.querySelector('.preview-recall'),next=fixture.querySelector('.preview-practice-actions button')
  assert(reveal.getAttribute('aria-expanded')==='false'&&fixture.querySelector('.preview-answer').style.display==='none','Practice starts with its answer hidden')
  reveal.click();await nextTick()
  assert(reveal.getAttribute('aria-expanded')==='true'&&fixture.querySelector('.preview-answer').textContent.includes('取得进步'),'Reveal shows the phrase meaning and example')
  reveal.click();await nextTick();assert(reveal.getAttribute('aria-expanded')==='false','A revealed answer can be hidden again')
  next.click();await nextTick();assert(fixture.querySelector('.preview-word').textContent==='pay attention to'&&reveal.getAttribute('aria-expanded')==='false','Next phrase resets recall without saving progress')
  next.click();await nextTick();next.click();await nextTick();assert(fixture.querySelector('.preview-word').textContent==='make progress','Phrase preview wraps safely')
  await setUiLanguage('en',false);await nextTick()
  assert(reveal.textContent.includes('Recall the meaning')&&next.textContent.includes('Try another phrase'),'Practice controls translate to English')
  assert(requests===0,'Practice uses no API requests or learning-record mutations')
  move(label);assert(frames.size===1,'A pending frame exists for unmount cleanup');app.unmount();app=undefined
  assert(frames.size===0&&!light()&&listeners.size===0,'Unmount removes pending work, decoration and capability listeners')
  move(document.body);assert(frames.size===0,'Unmounted listeners cannot schedule further work')
  document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${checks.length} assertions)\n${checks.join('\n')}`
}catch(error){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=error.stack;throw error}
finally{
  app?.unmount();Object.assign(motionState,{paused:false,reduced:false,visible:true})
  window.matchMedia=original.media;HTMLElement.prototype.getBoundingClientRect=original.rect;window.requestAnimationFrame=original.raf;window.cancelAnimationFrame=original.cancel
}
