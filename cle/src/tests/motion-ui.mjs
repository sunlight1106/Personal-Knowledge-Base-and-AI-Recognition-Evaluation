import { createApp, h, nextTick } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import AmbientWaves from '../components/AmbientWaves.vue'
import MotionToggle from '../components/MotionToggle.vue'
import { useMotionPreferences, motionState } from '../lib/motion'
import { useSurfaceRipples } from '../lib/useSurfaceRipples'

const checks=[],fixture=document.getElementById('fixture')
const assert=(value,message)=>{if(!value)throw new Error(message);checks.push(message)}
const mediaListeners=new Set(), media={matches:false,addEventListener:(_,fn)=>mediaListeners.add(fn),removeEventListener:(_,fn)=>mediaListeners.delete(fn)}
const originalMedia=window.matchMedia,originalRect=HTMLElement.prototype.getBoundingClientRect
const observers=new Set()
const intersection=entries=>{for(const fn of observers)fn(entries)}
let disconnected=false,clicks=0,app
window.matchMedia=()=>media
window.IntersectionObserver=globalThis.IntersectionObserver=class{constructor(fn){this.callback=fn;observers.add(fn)}observe(){}disconnect(){disconnected=true;observers.delete(this.callback)}}
HTMLElement.prototype.getBoundingClientRect=()=>({left:10,top:20,width:200,height:50,right:210,bottom:70,x:10,y:20,toJSON(){}})
const count=()=>document.querySelectorAll('.surface-ripple-layer').length
const pointer=(target,button=0)=>target.dispatchEvent(new window.MouseEvent('pointerdown',{bubbles:true,button,clientX:40,clientY:40}))
try{
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{render:()=>h('div')}}]})
  await router.push('/');await router.isReady()
  app=createApp({setup(){useMotionPreferences();useSurfaceRipples();return()=>h('div',[
    h(MotionToggle),h(AmbientWaves),h('button',{class:'button',id:'go',onClick:()=>clicks++},[h('span','Go')]),
    h('button',{class:'button',id:'disabled',disabled:true},'Disabled'),h('a',{class:'button',id:'forbidden',href:'#', 'aria-disabled':'true'},'Unavailable'),
    h('input',{id:'field'}),h('a',{href:'#',id:'prose'},'Plain text link'),
    h(AmbientWaves,{variant:'pool'}),h('button',{class:'theme-palette',id:'palette'},'Blue'),
    h('details',{class:'guide-step-detail'},[h('summary',{id:'guide'},'Help')]),
    h('div',{contenteditable:'true'},[h('button',{class:'button',id:'editable'},'Editor content')]),
    h('div',{class:'markdown-body'},[h('a',{class:'button',id:'markdown',href:'#'},'Document link')]),
    h('dialog',{open:true},[h('button',{class:'button',id:'dialog-action'},'Dialog action')])
  ])}})
  app.use(router);app.mount(fixture);await nextTick()
  assert(fixture.querySelector('.ambient-waves').dataset.running==='false','Offscreen waves do not animate before visibility is known')
  intersection([{isIntersecting:true}]);await nextTick()
  assert(fixture.querySelector('.ambient-waves').dataset.running==='true','Visible waves start when motion is allowed')
  const pool=fixture.querySelector('.ambient-waves--pool')
  assert(pool.querySelectorAll('.water-rings ellipse').length===5&&pool.querySelector('.water-drop')&&pool.querySelector('.water-splash')&&!pool.querySelector('.wave-flow'),'Local pools show a falling drop, impact and five expanding rings')
  const gradients=[...fixture.querySelectorAll('radialGradient')].map(node=>node.id)
  assert(new Set(gradients).size===gradients.length&&gradients.every(Boolean),'Multiple water surfaces have independent SVG gradient IDs')
  assert([...fixture.querySelectorAll('.ambient-waves')].every(node=>node.getAttribute('aria-hidden')==='true'),'All water decorations stay out of the accessibility tree')
  const go=fixture.querySelector('#go')
  pointer(go.querySelector('span'))
  assert(document.querySelectorAll('.surface-ripple').length===2&&document.querySelector('.surface-ripple--echo'),'Activation creates an outer ripple and a softer echo')
  assert(count()===1,'Pointer activation on a nested icon or label finds its control')
  assert(document.querySelector('.surface-ripple-layer').getAttribute('aria-hidden')==='true','Decorative feedback is hidden from assistive technology')
  const nativeClick=new window.MouseEvent('click',{bubbles:true,cancelable:true,detail:1})
  go.dispatchEvent(nativeClick)
  assert(clicks===1&&!nativeClick.defaultPrevented&&count()===1,'Native activation proceeds exactly once without a duplicate click ripple')
  for(let i=0;i<12;i++)pointer(go)
  assert(count()<=3,'Rapid activation cannot accumulate unbounded overlays')
  document.dispatchEvent(new Event('scroll'));assert(count()===0,'Scrolling immediately removes viewport-positioned feedback')
  pointer(go,2);pointer(fixture.querySelector('#disabled'));pointer(fixture.querySelector('#forbidden'));pointer(fixture.querySelector('#field'));pointer(fixture.querySelector('#prose'));pointer(fixture.querySelector('#editable'));pointer(fixture.querySelector('#markdown'))
  assert(count()===0,'Right click, unavailable controls, fields, editors and prose links create no ripples')
  for(const id of ['palette','guide']){
    pointer(fixture.querySelector(`#${id}`))
    assert(count()===1,`${id} controls participate in shared ripple feedback`)
    document.dispatchEvent(new Event('scroll'))
  }
  const standardRect=HTMLElement.prototype.getBoundingClientRect
  HTMLElement.prototype.getBoundingClientRect=function(){return this.classList.contains('surface-ripple-layer')
    ?{left:100,top:150,width:200,height:200,right:300,bottom:350}
    :{left:160,top:210,width:240,height:80,right:400,bottom:290}}
  const offsetDescriptor=Object.getOwnPropertyDescriptor(HTMLElement.prototype,'offsetWidth')
  Object.defineProperty(HTMLElement.prototype,'offsetWidth',{configurable:true,get(){return 100}})
  try{
    fixture.querySelector('#dialog-action').dispatchEvent(new window.MouseEvent('pointerdown',{bubbles:true,button:0,clientX:200,clientY:230}))
    const layer=fixture.querySelector('dialog .surface-ripple-layer'),ring=layer?.querySelector('.surface-ripple')
    assert(layer?.style.left==='30px'&&layer.style.top==='30px'&&layer.style.width==='120px','Ripple bounds account for 200% zoom and a dialog containing block')
    assert(ring?.style.left==='20px'&&ring.style.top==='10px','Zoomed ripple starts at the pointer location')
  }finally{
    HTMLElement.prototype.getBoundingClientRect=standardRect
    if(offsetDescriptor)Object.defineProperty(HTMLElement.prototype,'offsetWidth',offsetDescriptor)
    else delete HTMLElement.prototype.offsetWidth
    document.dispatchEvent(new Event('scroll'))
  }
  go.dispatchEvent(new window.MouseEvent('click',{bubbles:true,detail:0}))
  assert(count()===1&&document.querySelector('.surface-ripple').style.left==='100px','Keyboard activation places one ripple at the control center')
  document.querySelector('.surface-ripple').dispatchEvent(new Event('animationend'));assert(count()===0,'Completed animations remove their overlay')
  pointer(go);await router.push('/next');await nextTick();assert(count()===0,'Route changes remove feedback from the previous page')
  pointer(go);fixture.querySelector('.motion-toggle').click();await nextTick()
  assert(count()===0&&localStorage.getItem('pkb:ambient-motion')==='paused','Pausing stops current feedback and saves the preference')
  pointer(go);assert(count()===0&&fixture.querySelector('.ambient-waves').dataset.running==='false','Paused motion cannot be restarted by clicking controls')
  fixture.querySelector('.motion-toggle').click();await nextTick()
  motionState.preference='system'
  media.matches=true;for(const fn of mediaListeners)fn();await nextTick()
  assert(!fixture.querySelector('.motion-toggle').disabled&&fixture.querySelector('.ambient-waves').dataset.running==='false','System reduced motion pauses decorations by default without disabling the user control')
  pointer(go);assert(count()===0,'Reduced motion suppresses interaction ripples as well')
  fixture.querySelector('.motion-toggle').click();await nextTick()
  assert(motionState.preference==='enabled'&&localStorage.getItem('pkb:ambient-motion')==='enabled'&&fixture.querySelector('.ambient-waves').dataset.running==='true','The user can explicitly enable and remember site motion under system reduced motion')
  pointer(go);assert(count()===1,'Explicitly enabled motion restores interaction ripples')
  media.matches=false;for(const fn of mediaListeners)fn();media.matches=true;for(const fn of mediaListeners)fn();await nextTick()
  assert(fixture.querySelector('.ambient-waves').dataset.running==='true','Later system preference changes do not replace an explicit site choice')
  fixture.querySelector('.motion-toggle').click();await nextTick()
  assert(count()===0&&fixture.querySelector('.ambient-waves').dataset.running==='false','The same control can pause manually enabled motion immediately')
  media.matches=false;for(const fn of mediaListeners)fn();await nextTick()
  assert(fixture.querySelector('.ambient-waves').dataset.running==='false','Explicit pause remains paused when system motion is restored')
  fixture.querySelector('.motion-toggle').click();await nextTick()
  Object.defineProperty(document,'visibilityState',{configurable:true,value:'hidden'});document.dispatchEvent(new Event('visibilitychange'));await nextTick()
  pointer(go);assert(count()===0&&!motionState.visible&&fixture.querySelector('.ambient-waves').dataset.running==='false','Hidden tabs pause waves and ignore decorative activation')
  Object.defineProperty(document,'visibilityState',{configurable:true,value:'visible'});document.dispatchEvent(new Event('visibilitychange'));await nextTick()
  intersection([{isIntersecting:false}]);await nextTick();assert(fixture.querySelector('.ambient-waves').dataset.running==='false','Scrolling waves out of view pauses their animation')
  pointer(go);window.dispatchEvent(new Event('resize'));assert(count()===0,'Resizing removes overlays measured at old coordinates')
  pointer(go);app.unmount();app=undefined
  assert(count()===0&&disconnected&&observers.size===0&&mediaListeners.size===0,'Unmount removes overlays, observers and preference listeners')
  const after=document.createElement('button');after.className='button';document.body.append(after);pointer(after);assert(count()===0,'Unmounted event handlers cannot create later overlays');after.remove()
  media.matches=true;motionState.preference='system'
  const restored=createApp({setup(){useMotionPreferences();return()=>h('div')}})
  restored.mount(fixture);await nextTick()
  assert(motionState.preference==='enabled'&&document.documentElement.dataset.motion==='on','A new mount restores the explicit enabled choice even with system reduced motion')
  restored.unmount()
  document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${checks.length} assertions)\n${checks.join('\n')}`
}catch(error){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=error.stack;throw error}
finally{app?.unmount();window.matchMedia=originalMedia;HTMLElement.prototype.getBoundingClientRect=originalRect;delete window.IntersectionObserver;delete globalThis.IntersectionObserver;localStorage.removeItem('pkb:ambient-motion')}
