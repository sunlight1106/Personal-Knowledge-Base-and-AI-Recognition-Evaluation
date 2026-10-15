import { createApp, h, nextTick } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import HelpHint from '../components/HelpHint.vue'
import GettingStarted from '../components/GettingStarted.vue'
import { firstSteps } from '../lib/firstSteps'
import { authStore } from '../stores/auth'
import { tokenStorage } from '../api/client'
import { setUiLanguage, uiText } from '../i18n'

const fixture = document.getElementById('fixture'), checks = []
const assert = (condition, label) => { if (!condition) throw Error(label); checks.push(label) }
const pause = async (ms = 0) => { await new Promise(resolve => setTimeout(resolve, ms)); await nextTick() }
const pointer = (element, type) => element.dispatchEvent(new Event(type, { bubbles: true }))
const tooltip = () => document.querySelector('[role=tooltip]')
let app, submitted = 0, calls = 0, zoom = 1
const rect = (x,y,w,height) => ({ x,y,left:x,top:y,right:x+w,bottom:y+height,width:w,height })
const originalRect = HTMLElement.prototype.getBoundingClientRect
const originalWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetWidth')
Object.defineProperty(HTMLElement.prototype, 'offsetWidth', { configurable: true, get() { return this.classList.contains('help-hint__bubble') ? 280 : 21 } })
HTMLElement.prototype.getBoundingClientRect = function () {
  return this.classList.contains('help-hint__bubble') ? rect((parseFloat(this.style.left)||0)*zoom,(parseFloat(this.style.top)||0)*zoom,280*zoom,100*zoom) : rect(990,710,21,21)
}
async function mount(component) { app?.unmount(); fixture.innerHTML = ''; app = createApp(component); app.mount(fixture); await nextTick() }
try {
  await mount({ render: () => h('form', { onSubmit: event => { event.preventDefault(); submitted++ } }, [
    h(HelpHint, { label: '学习库', text: '简易说明' }), h(HelpHint, { label: '复习卡', text: '怎么操作' }),
  ]) })
  const [first, second] = fixture.querySelectorAll('button')
  assert(first.type === 'button' && first.getAttribute('aria-label').includes('学习库'), 'Help is a named non-submitting button')
  assert(!tooltip(), 'No tooltip or visible help text on initial render')
  pointer(first, 'pointerenter'); await pause(70); assert(!tooltip(), 'Passing briefly over the icon does not flash a tooltip')
  pointer(first, 'pointerleave'); await pause(200); assert(!tooltip(), 'Leaving cancels a pending reveal')
  pointer(first, 'pointerenter'); await pause(210)
  assert(tooltip()?.textContent.includes('简易说明'), 'Hover reveals the explanation')
  assert(document.getElementById(first.getAttribute('aria-describedby')) === tooltip(), 'Visible help is linked to its trigger for assistive technology')
  assert(tooltip().parentElement === document.body, 'Help escapes clipping containers')
  assert(Number.parseFloat(tooltip().style.left) + 280 <= window.innerWidth - 12, 'Right-edge tooltip remains within the viewport')
  assert(Number.parseFloat(tooltip().style.top) + 100 < 710, 'Tooltip opens above an icon near the bottom edge')
  pointer(first, 'pointerleave'); pointer(tooltip(), 'pointerenter'); await pause(180)
  assert(tooltip(), 'Pointer can move into the help text without it disappearing')
  pointer(tooltip(), 'pointerleave'); await pause(180); assert(!tooltip(), 'Leaving both trigger and help dismisses it')
  first.focus(); await pause(); assert(tooltip(), 'Keyboard focus reveals help immediately')
  pointer(first, 'pointerleave'); await pause(180); assert(tooltip(), 'Keyboard-focused help stays open when the pointer leaves')
  const escape = new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })
  first.dispatchEvent(escape); await pause()
  assert(!tooltip() && document.activeElement === first, 'Escape dismisses help without moving keyboard focus')
  assert(escape.defaultPrevented, 'Escape is consumed so a parent dialog does not also close')
  second.focus(); await pause(); assert(tooltip()?.textContent.includes('复习卡'), 'The next help button shows its own explanation')
  pointer(first, 'pointerenter'); await pause(210); assert(document.querySelectorAll('[role=tooltip]').length === 1, 'Only one help bubble is shown at a time')
  pointer(document.body, 'pointerdown'); await pause(); assert(!tooltip(), 'Clicking outside closes the help')
  first.click(); await pause(); assert(tooltip() && submitted === 0, 'Click can reveal help without submitting its surrounding form')
  window.dispatchEvent(new Event('scroll')); await pause(); assert(!tooltip(), 'Scroll removes help instead of leaving it at a stale position')
  first.click(); await pause(); window.dispatchEvent(new Event('resize')); await pause(); assert(!tooltip(), 'Resizing removes stale tooltip placement')
  zoom = 2; first.click(); await pause(); const enlarged = tooltip().getBoundingClientRect()
  assert(enlarged.right <= window.innerWidth - 12 && enlarged.left >= 12 && enlarged.bottom < 710, 'Tooltip converts viewport coordinates at 200 percent layout zoom')
  window.dispatchEvent(new Event('resize')); await pause(); zoom = 1
  first.click(); await pause(); await setUiLanguage('en'); await pause()
  assert(tooltip()?.textContent.includes('Library') && first.getAttribute('aria-label').includes('Quick help'), 'Help and its accessible label switch language together')
  assert(!/[\u3400-\u9fff]/u.test(tooltip().textContent), 'English help does not leak Chinese interface text')
  app.unmount(); app = null; await pause(); assert(!tooltip(), 'Unmount removes teleported help')
  await setUiLanguage('zh')
  await mount({ render: () => h('dialog', { open: true }, [h(HelpHint, { label: '学习设置', text: '简易说明' })]) })
  fixture.querySelector('button').focus(); await pause()
  assert(tooltip()?.parentElement === fixture.querySelector('dialog'), 'Help inside a dialog stays in the same top-layer container')
  app.unmount(); app = null
  await mount({render:()=>h(HelpHint,{label:'学习库',text:'简易说明'})}); pointer(fixture.querySelector('button'), 'pointerenter'); app.unmount(); app = null; await pause(220)
  assert(!tooltip(), 'A pending hover cannot reopen help after unmount')

  tokenStorage.set('synthetic-help'); authStore.state.user = { id: 78, permissions: ['note:read','note:write'] }
  window.fetch = async url => {
    assert(String(url).includes('/workspace/getting-started'), 'Guide only reads its permission-scoped checklist')
    calls++
    return new Response(JSON.stringify({success:true,data:[{id:'note',title:'写下第一篇笔记',description:'说明',url:'/app/notes/new',completed:false}]}), {headers:{'Content-Type':'application/json'}})
  }
  const router = createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{template:'<p>destination</p>'}}]})
  await router.push('/'); await router.isReady()
  app = createApp(GettingStarted).use(router); app.mount(fixture)
  for (let i=0;i<50 && !fixture.querySelector('.guide-step-detail');i++) await pause(10)
  const detail = fixture.querySelector('.guide-step-detail'); detail.open = true
  assert(detail.querySelectorAll('li').length === 3, 'Each entry contains concrete click-by-click instructions')
  assert(detail.textContent.includes('完成后会看到') && detail.textContent.includes('已保存到服务器'), 'Guide explains the saved outcome to check')
  assert(fixture.querySelectorAll('.guide-steps article').length === 1, 'Guide does not add entries absent from the server permission scope')
  assert(fixture.textContent.includes('0 / 1') && calls === 1, 'Opening instructions does not write data or count as completion')
  assert(!fixture.querySelector('a details') && !fixture.querySelector('a button'), 'Help controls are not nested inside navigation links')
  assert(fixture.querySelector('a[href="/docs#first-session"]'), 'Workspace links directly to the beginner manual')
  await setUiLanguage('en'); await pause()
  for (const item of Object.values(firstSteps)) {
    for (const copy of [item.title,item.purpose,...item.steps,item.outcome]) assert(!/[\u3400-\u9fff]/u.test(uiText(copy)), 'Beginner instructions have an English translation: '+copy.slice(0,20))
  }
  assert(fixture.textContent.includes('How to do it') && fixture.textContent.includes('What success looks like'), 'Expanded guide is bilingual')
  document.getElementById('results').textContent = 'PASS '+checks.length+'\n'+checks.join('\n'); document.documentElement.dataset.result='passed'
} catch(error) { document.getElementById('results').textContent='FAIL '+checks.length+': '+error.stack; document.documentElement.dataset.result='failed' }
finally { app?.unmount(); HTMLElement.prototype.getBoundingClientRect=originalRect; Object.defineProperty(HTMLElement.prototype,'offsetWidth',originalWidth); await setUiLanguage('zh') }
