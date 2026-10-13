import { createApp, h, nextTick, reactive } from 'vue'
import AdminTrendChart from '../components/AdminTrendChart.vue'

const fixture = document.getElementById('fixture'), checks = []
const assert = (value, message) => { if (!value) throw new Error(message); checks.push(message) }
const nativeTimeout = globalThis.setTimeout, nativeClear = globalThis.clearTimeout
let now = 0, sequence = 0, app
const timers = new Map()
globalThis.setTimeout = (callback, delay) => { const id = ++sequence; timers.set(id, { callback, at: now + delay }); return id }
globalThis.clearTimeout = id => timers.delete(id)
const advance = async milliseconds => {
  now += milliseconds
  for (const [id, timer] of [...timers]) if (timer.at <= now) { timers.delete(id); await timer.callback() }
  await nextTick()
}
const tip = () => document.querySelector('.chart-tooltip')
const point = index => fixture.querySelectorAll('[data-day]')[index]
const move = async (index, x = 300, y = 200) => {
  point(index).dispatchEvent(new window.MouseEvent('mousemove', { clientX: x, clientY: y })); await nextTick()
}
const leave = async () => { fixture.querySelector('svg').dispatchEvent(new Event('mouseleave')); await nextTick() }
const button = label => [...fixture.querySelectorAll('button')].find(el => el.textContent.includes(label))
const day = (date, registrations) => ({ date, registrations, loginUsers: 0, notes: 4, aiCalls: 5, experiments: 6 })
const chart = reactive({ days: [day('2026-10-12', 1234), day('2026-10-13', 9)] })
try {
  app = createApp({ render: () => h(AdminTrendChart, chart) }); app.mount(fixture)
  await move(0); await advance(999)
  assert(!tip(), 'No floating tooltip before one full second')
  await advance(1)
  assert(tip()?.textContent.includes('2026-10-12') && tip().textContent.includes('1,234') && tip().textContent.includes('登录用户0'), 'One-second hover shows the selected date and values including zero')
  assert(!tip().textContent.includes('新建笔记') && tip().style.left === '316px' && tip().style.top === '216px', 'Tooltip sits beside the pointer and only includes visible series')
  await move(0, 320, 210)
  assert(tip().style.left === '336px' && timers.size === 0, 'An open tooltip follows movement within the same date without another timer')
  await move(1)
  assert(!tip(), 'Moving to another date hides the old tooltip immediately')
  await advance(600); await move(1, 302, 202); await advance(400)
  assert(!tip(), 'Movement before opening restarts the dwell timer')
  await advance(600)
  assert(tip()?.textContent.includes('2026-10-13') && !tip().textContent.includes('1,234'), 'The new tooltip contains only the new date values')
  tip().getBoundingClientRect = () => ({ width: 220, height: 140 })
  await move(1, window.innerWidth - 2, window.innerHeight - 2)
  assert(parseFloat(tip().style.left) + 220 < window.innerWidth && parseFloat(tip().style.top) + 140 < window.innerHeight, 'Right and bottom edges flip the tooltip into the viewport')
  await leave()
  assert(!tip() && !fixture.querySelector('.chart-cursor'), 'Leaving removes the tooltip and crosshair')
  await move(0); await advance(500); await leave(); await advance(1000)
  assert(!tip() && timers.size === 0, 'Leaving before the deadline cancels the pending tooltip')
  for (const event of [new Event('scroll'), new Event('resize'), new Event('blur'), new window.KeyboardEvent('keydown', { key: 'Escape' })]) {
    await move(0); await advance(1000); window.dispatchEvent(event); await nextTick()
    assert(!tip(), `${event.type} dismisses the tooltip`)
  }
  await move(0); await advance(1000); button('新增用户').click(); button('登录用户').click(); await nextTick()
  await move(0); await advance(1000)
  assert(!tip() && timers.size === 0, 'No selected metrics means no tooltip or pending timer')
  button('新增用户').click(); await nextTick(); await move(0)
  chart.days = [day('2026-10-14', 7)]; await nextTick(); await advance(1000)
  assert(!tip(), 'Replacing the data cancels stale hover details')
  await move(0); app.unmount(); app = null; await advance(1000)
  assert(!tip() && timers.size === 0, 'Unmount cancels the timer and removes the teleported tooltip')
  document.documentElement.dataset.result = 'passed'
  document.getElementById('results').textContent = `PASS (${checks.length} assertions)\n${checks.join('\n')}`
} catch (error) {
  document.documentElement.dataset.result = 'failed'; document.getElementById('results').textContent = error.stack; throw error
} finally {
  app?.unmount(); globalThis.setTimeout = nativeTimeout; globalThis.clearTimeout = nativeClear
}
