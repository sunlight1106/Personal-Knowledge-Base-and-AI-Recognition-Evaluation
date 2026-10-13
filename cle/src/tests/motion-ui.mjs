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
let intersection,disconnected=false,clicks=0,app
window.matchMedia=()=>media
window.IntersectionObserver=globalThis.IntersectionObserver=class{constructor(fn){intersection=fn}observe(){}disconnect(){disconnected=true}}
HTMLElement.prototype.getBoundingClientRect=()=>({left:10,top:20,width:200,height:50,right:210,bottom:70,x:10,y:20,toJSON(){}})
const count=()=>document.querySelectorAll('.surface-ripple-layer').length
const pointer=(target,button=0)=>target.dispatchEvent(new window.MouseEvent('pointerdown',{bubbles:true,button,clientX:40,clientY:40}))
try{
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{render:()=>h('div')}}]})
  await router.push('/');await router.isReady()
  app=createApp({setup(){useMotionPreferences();useSurfaceRipples();return()=>h('div',[
    h(MotionToggle),h(AmbientWaves),h('button',{class:'button',id:'go',onClick:()=>clicks++},[h('span','Go')]),
    h('button',{class:'button',id:'disabled',disabled:true},'Disabled'),h('a',{class:'button',id:'forbidden',href:'#', 'aria-disabled':'true'},'Unavailable'),
    h('input',{id:'field'}),h('a',{href:'#',id:'prose'},'Plain text link')
  ])}})
  app.use(router);app.mount(fixture);await nextTick()
  assert(fixture.querySelector('.ambient-waves').dataset.running==='false','Offscreen waves do not animate before visibility is known')
  intersection([{isIntersecting:true}]);await nextTick()
  assert(fixture.querySelector('.ambient-waves').dataset.running==='true','Visible waves start when motion is allowed')
  const go=fixture.querySelector('#go')
  pointer(go.querySelector('span'))
  assert(count()===1,'Pointer activation on a nested icon or label finds its control')
  assert(document.querySelector('.surface-ripple-layer').getAttribute('aria-hidden')==='true','Decorative feedback is hidden from assistive technology')
  const nativeClick=new window.MouseEvent('click',{bubbles:true,cancelable:true,detail:1})
  go.dispatchEvent(nativeClick)
  assert(clicks===1&&!nativeClick.defaultPrevented&&count()===1,'Native activation proceeds exactly once without a duplicate click ripple')
  for(let i=0;i<12;i++)pointer(go)
  assert(count()<=3,'Rapid activation cannot accumulate unbounded overlays')
  document.dispatchEvent(new Event('scroll'));assert(count()===0,'Scrolling immediately removes viewport-positioned feedback')
  pointer(go,2);pointer(fixture.querySelector('#disabled'));pointer(fixture.querySelector('#forbidden'));pointer(fixture.querySelector('#field'));pointer(fixture.querySelector('#prose'))
  assert(count()===0,'Right click, unavailable controls, fields and prose links create no ripples')
  go.dispatchEvent(new window.MouseEvent('click',{bubbles:true,detail:0}))
  assert(count()===1&&document.querySelector('.surface-ripple').style.left==='100px','Keyboard activation places one ripple at the control center')
  document.querySelector('.surface-ripple').dispatchEvent(new Event('animationend'));assert(count()===0,'Completed animations remove their overlay')
  pointer(go);await router.push('/next');await nextTick();assert(count()===0,'Route changes remove feedback from the previous page')
  pointer(go);fixture.querySelector('.motion-toggle').click();await nextTick()
  assert(count()===0&&localStorage.getItem('pkb:ambient-motion')==='paused','Pausing stops current feedback and saves the preference')
  pointer(go);assert(count()===0&&fixture.querySelector('.ambient-waves').dataset.running==='false','Paused motion cannot be restarted by clicking controls')
  fixture.querySelector('.motion-toggle').click();await nextTick()
  media.matches=true;for(const fn of mediaListeners)fn();await nextTick()
  assert(fixture.querySelector('.motion-toggle').disabled&&fixture.querySelector('.ambient-waves').dataset.running==='false','A live system reduced-motion change disables decorative motion')
  pointer(go);assert(count()===0,'Reduced motion suppresses interaction ripples as well')
  media.matches=false;for(const fn of mediaListeners)fn();await nextTick()
  Object.defineProperty(document,'visibilityState',{configurable:true,value:'hidden'});document.dispatchEvent(new Event('visibilitychange'));await nextTick()
  pointer(go);assert(count()===0&&!motionState.visible&&fixture.querySelector('.ambient-waves').dataset.running==='false','Hidden tabs pause waves and ignore decorative activation')
  Object.defineProperty(document,'visibilityState',{configurable:true,value:'visible'});document.dispatchEvent(new Event('visibilitychange'));await nextTick()
  intersection([{isIntersecting:false}]);await nextTick();assert(fixture.querySelector('.ambient-waves').dataset.running==='false','Scrolling waves out of view pauses their animation')
  pointer(go);window.dispatchEvent(new Event('resize'));assert(count()===0,'Resizing removes overlays measured at old coordinates')
  pointer(go);app.unmount();app=undefined
  assert(count()===0&&disconnected&&mediaListeners.size===0,'Unmount removes overlays, observers and preference listeners')
  const after=document.createElement('button');after.className='button';document.body.append(after);pointer(after);assert(count()===0,'Unmounted event handlers cannot create later overlays');after.remove()
  document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${checks.length} assertions)\n${checks.join('\n')}`
}catch(error){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=error.stack;throw error}
finally{app?.unmount();window.matchMedia=originalMedia;HTMLElement.prototype.getBoundingClientRect=originalRect;delete window.IntersectionObserver;delete globalThis.IntersectionObserver;localStorage.removeItem('pkb:ambient-motion')}
