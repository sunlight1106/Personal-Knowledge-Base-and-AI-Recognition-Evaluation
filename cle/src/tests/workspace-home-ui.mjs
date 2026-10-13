import { createApp, nextTick } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import OverviewView from '../views/OverviewView.vue'
import { api } from '../api/client'
import { researchApi } from '../api/research'
import { vocabularyApi } from '../api/vocabulary'
import { authStore } from '../stores/auth'
import { workspaceNavigation as navigation } from '../stores/workspaceNavigation'

const checks = [], fixture = document.getElementById('fixture')
const assert = (value, message) => { if (!value) throw new Error(message); checks.push(message) }
const wait = async condition => { for (let i = 0; i < 150; i++) { await new Promise(r => setTimeout(r, 5)); await nextTick(); if (condition()) return } throw new Error('Workspace did not settle') }
const original = { dashboard: api.dashboard, providers: api.providerBudgets, vocabulary: vocabularyApi.dashboard, search: researchApi.search }
const permissions = ['dashboard:read', 'note:read', 'note:write', 'research:use', 'vocabulary:use', 'knowledge:read', 'experiment:run', 'experiment:read', 'admin:stats']
const account = id => ({ id, displayName: 'Test', permissions: [...permissions] })
const dashboard = { totalTasks: 4, completedTasks: 3, failedTasks: 1, recentTasks: [] }
const learning = { books: [{ id: 'book', title: 'My words' }], settings: { selectedBookId: 'book' }, today: { learned: 2 }, dueTotal: 3, streak: 4 }
const hit = { id: 'private-note', title: 'Continue this note', excerpt: 'Small steps', updatedAt: '2026-10-13T00:00:00Z' }
let app, searchCalls = 0, vocabularyCalls = 0, providerCalls = 0
try {
  localStorage.setItem('pkb:navigation:801', JSON.stringify({ version: 1, pins: ['/app/admin', '/app/admin', 'https://untrusted.test', '/app/notes/secret/edit'], recent: ['/app/notes/secret/edit', '/app/vocabulary'] }))
  authStore.state.user = account(801)
  assert(navigation.pinned.value.length === 1 && navigation.pinned.value[0].to === '/app/admin', 'Saved navigation deduplicates and rejects unknown or external routes')
  navigation.visit('/app/notes/private-note/edit')
  navigation.visit('/app/vocabulary')
  navigation.visit('/app/vocabulary')
  assert(navigation.recent.value.length === 2, 'Recent pages deduplicate at module level')
  assert(!localStorage.getItem('pkb:navigation:801').includes('private-note'), 'Private content identifiers never enter recent-page storage')
  authStore.state.user = { ...account(801), permissions: ['dashboard:read'] }
  assert(navigation.pinned.value.length === 0 && navigation.recent.value.length === 0, 'Permission revocation hides existing pinned and recent destinations immediately')
  assert(!navigation.toggle('/app/admin'), 'A forbidden destination cannot be pinned')
  authStore.state.user = account(802)
  assert(navigation.recent.value.length === 0 && !navigation.pinned.value.some(item => item.to === '/app/admin'), 'Account switching does not inherit another account navigation')
  assert(!navigation.toggle('/app/admin'), 'The five-entry limit is enforced')
  navigation.toggle('/app/home'); assert(navigation.toggle('/app/admin'), 'Removing an entry frees one slot')
  navigation.visit('/app/knowledge'); navigation.clearRecent()
  assert(JSON.parse(localStorage.getItem('pkb:navigation:802')).recent.length === 0, 'Clearing recent pages persists')
  authStore.state.user = account(801)
  assert(navigation.pinned.value[0].to === '/app/admin', 'Returning to an account restores its own saved preferences')

  let releaseNotes
  api.dashboard = async () => dashboard
  vocabularyApi.dashboard = async () => { vocabularyCalls++; return learning }
  researchApi.search = () => { searchCalls++; return new Promise(resolve => { releaseNotes = resolve }) }
  api.providerBudgets = async () => { providerCalls++; throw new Error('No automatic provider call') }
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
  await router.push('/app/home'); await router.isReady()
  app = createApp(OverviewView).use(router); app.mount(fixture)
  await wait(() => fixture.textContent.includes('My words'))
  assert(fixture.textContent.includes('正在读取最近记录'), 'Vocabulary renders while a slow notes request is still pending')
  assert(fixture.querySelector('.workspace-experiment-stats') && providerCalls === 0, 'Local experiment summary loads without calling legacy provider budgets')
  releaseNotes({ items: [hit] }); await wait(() => fixture.textContent.includes(hit.title))
  assert(fixture.querySelector('.workspace-note-list a').getAttribute('href') === '/app/notes/private-note/edit', 'Recent notes open the permitted editor')
  researchApi.search = async () => { throw new Error('Synthetic notes outage') }
  fixture.querySelector('.workspace-welcome button').click()
  await wait(() => fixture.querySelector('[role=alert]'))
  assert(fixture.textContent.includes('My words') && fixture.querySelector('.workspace-experiment-stats'), 'A notes failure does not hide learning or experiment data')
  researchApi.search = async () => ({ items: [hit] })
  fixture.querySelector('.workspace-placeholder button').click()
  await wait(() => fixture.textContent.includes(hit.title))
  assert(!fixture.querySelector('[role=alert]'), 'Retry clears a recovered error')

  let lateNotes, oldSignal
  researchApi.search = (_params, signal) => { oldSignal = signal; return new Promise(resolve => { lateNotes = resolve }) }
  fixture.querySelector('.workspace-welcome button').click(); await wait(() => lateNotes)
  const requestsBefore = [searchCalls, vocabularyCalls]
  authStore.state.user = { id: 803, displayName: 'Read only', permissions: ['dashboard:read'] }
  await wait(() => fixture.textContent.includes('Read only') && !fixture.querySelector('.workspace-welcome button').disabled)
  lateNotes({ items: [{ ...hit, title: 'Old account secret' }] }); await nextTick(); await nextTick()
  assert(oldSignal.aborted && !fixture.textContent.includes('Old account secret'), 'Changing account aborts and discards late private results')
  assert(!fixture.querySelector('.workspace-learning') && !fixture.querySelector('.workspace-action-grid') && !fixture.querySelector('a[href="/app/comparisons"]'), 'Read-only accounts do not receive unavailable action links')
  assert(requestsBefore[0] === searchCalls && requestsBefore[1] === vocabularyCalls, 'Restricted modules are not requested after account change')
  document.getElementById('results').textContent = `PASS (${checks.length} assertions)\n` + checks.join('\n')
  document.documentElement.dataset.result = 'passed'
} catch (error) {
  document.getElementById('results').textContent = error.stack
  document.documentElement.dataset.result = 'failed'
} finally {
  app?.unmount(); api.dashboard = original.dashboard; api.providerBudgets = original.providers
  vocabularyApi.dashboard = original.vocabulary; researchApi.search = original.search
  authStore.state.user = null
}
