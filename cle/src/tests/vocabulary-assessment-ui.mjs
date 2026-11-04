import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import VocabularyAssessmentView from '../views/VocabularyAssessmentView.vue'
import VocabularyView from '../views/VocabularyView.vue'
import { authStore } from '../stores/auth'
import { vocabularyApi } from '../api/vocabulary'
import { setUiLanguage } from '../i18n'
import bank from '../lib/vocabulary-assessment-bank.json'

const fixture = document.getElementById('fixture'), passed = []
const assert = (value, message) => { if (!value) throw new Error(message); passed.push(message) }
const settle = async () => { await nextTick(); await new Promise(r => setTimeout(r, 5)); await nextTick() }
const button = text => [...fixture.querySelectorAll('button')].find(b => b.textContent.includes(text))
const click = async text => { const b = button(text); if (!b) throw new Error('Missing button ' + text); b.click(); await settle() }
let app, calls = 0
const original = authStore.ensureUser
authStore.ensureUser = async () => authStore.state.user
window.fetch = async () => { calls++; throw new Error('Unexpected network call') }
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/vocabulary-test', component: VocabularyAssessmentView }, { path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
try {
  authStore.state.user = null
  await router.push('/vocabulary-test'); await router.isReady()
  app = createApp(RouterView); app.use(router); app.mount(fixture); await settle()
  assert(fixture.querySelectorAll('input[name=assessment-mode]').length === 3, 'Guest sees all three modes without login')
  await click('开始测试')
  const originalConfirm = window.confirm; window.confirm = () => false
  await router.push('/leave-test'); await settle()
  assert(router.currentRoute.value.path === '/vocabulary-test', 'Declining navigation keeps unfinished answers on the page')
  window.confirm = originalConfirm
  const unload = new Event('beforeunload', { cancelable: true }); window.dispatchEvent(unload)
  assert(unload.defaultPrevented, 'Refreshing an unfinished test requests a native discard warning')
  const first = [...fixture.querySelectorAll('.assessment-word-grid input')].map(i => i.getAttribute('aria-label'))
  assert(first.length === 40, 'Recognition starts with 40 words')
  fixture.querySelector('.assessment-word-grid input').click(); await settle()
  assert(fixture.querySelectorAll('.assessment-word-grid .checked').length === 1, 'A word click updates visible selection')
  fixture.querySelector('.assessment-word-grid input').click(); await settle(); await click('下一轮')
  const second = [...fixture.querySelectorAll('.assessment-word-grid input')].map(i => i.getAttribute('aria-label'))
  assert(second.length === 40 && !second.some(id => first.includes(id)), 'Adaptive second round has 40 new words even with zero known')
  await click('查看结果')
  assert(fixture.querySelector('.assessment-range') && fixture.textContent.includes('不代表你的词汇量为零'), 'All-unknown result gives a bounded range without declaring zero ability')
  assert(fixture.querySelectorAll('.assessment-book-grid article').length === 3, 'Results recommend actual books')
  const guestLinks = [...fixture.querySelectorAll('.assessment-book-grid a')].map(a => a.getAttribute('href'))
  assert(guestLinks.every(link => link.startsWith('/login?redirect=') && decodeURIComponent(link).includes('?book=vocab-ec-')), 'Guest book links preserve a specific book through login')
  assert(fixture.querySelectorAll('.assessment-review li').length === 80, 'Unfamiliar words and meanings are available for review')
  await setUiLanguage('en'); await settle()
  assert(fixture.textContent.includes('Your vocabulary snapshot') && fixture.textContent.includes('Sign in to study'), 'Results and recommendation actions switch to English')
  await setUiLanguage('zh'); await settle()
  await click('尝试另一种模式'); fixture.querySelector('input[value=meaning]').click(); await settle(); await click('开始测试')
  assert(button('下一题').disabled, 'Meaning question cannot advance with no answer')
  fixture.querySelector('.assessment-options input').click(); await settle(); await click('下一题'); await click('上一题')
  assert(fixture.querySelector('.assessment-options input:checked'), 'Going back preserves the prior answer')
  for (let n = 0; n < 30; n++) {
    const term = fixture.querySelector('.assessment-translation h1').textContent.trim()
    const item = bank.items.find(i => i.id === term)
    const input = [...fixture.querySelectorAll('.assessment-options input')].find(i => i.value === item.meaning)
    input.click(); await settle(); fixture.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); await settle()
  }
  assert(fixture.textContent.includes('30 / 30') && fixture.textContent.includes('本次全部答对'), 'Thirty meaning answers complete the test with ceiling guidance')
  await click('尝试另一种模式'); fixture.querySelector('input[value=spelling]').click(); await settle(); await click('开始测试')
  const firstMeaning = fixture.querySelector('h1').textContent.trim(), target = bank.items.find(i => i.meaning === firstMeaning)
  const input = fixture.querySelector('.assessment-spelling input'); input.value = ` ${target.id.toUpperCase()} `; input.dispatchEvent(new Event('input', { bubbles: true })); await settle()
  fixture.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); await settle()
  for (let n = 1; n < 30; n++) await click('不认识，跳过')
  assert(fixture.textContent.includes('1 / 30') && fixture.querySelectorAll('.assessment-review li').length === 29, 'Spelling grades case-insensitive recall and explicit skips')
  authStore.state.user = { id: 'restricted', permissions: [] }; await settle()
  assert(fixture.querySelector('.assessment-mode-picker') && !fixture.querySelector('.assessment-range'), 'Switching accounts clears previous answers and result')
  await click('开始测试'); await click('不认识，跳过')
  fixture.querySelector('.assessment-quiz-top a').click(); await settle()
  assert(fixture.querySelector('.assessment-confirm'), 'Reset asks before discarding work')
  await click('继续测试'); assert(fixture.querySelector('.assessment-translation'), 'Cancel reset preserves the active quiz')
  for (let n = 1; n < 30; n++) await click('不认识，跳过')
  assert(!fixture.querySelector('.assessment-book-grid a') && fixture.textContent.includes('未开放词汇学习权限'), 'Restricted user cannot use the recommendation to bypass study permission')
  assert(calls === 0, 'Assessment never sends answers, calls AI or writes learning progress')
  app.unmount()
  let writes = 0
  authStore.state.user = { id: 'learner', permissions: ['vocabulary:use'] }
  const book = { id: 'vocab-ec-cet4', title: '大学英语四级', description: 'Synthetic book', level: '四级', owned: false, totalWords: 100, learned: 2, due: 0, mistakes: 0 }
  vocabularyApi.dashboard = async () => ({ settings: { zoneId: 'Asia/Shanghai', dailyGoal: 10, selectedBookId: null, masteryTarget: 4 }, books: [book], today: { answers: 0, correct: 0, learned: 0 }, history: [], streak: 0, dueTotal: 0, starredTotal: 0 })
  vocabularyApi.settings = async () => { writes++; return {} }
  await router.push('/app/vocabulary?book=vocab-ec-cet4')
  app = createApp(VocabularyView); app.use(router); app.mount(fixture); await settle()
  assert(fixture.querySelector('.shelf-book.is-current')?.textContent.includes('大学英语四级'), 'Recommended book is highlighted in the real book shelf')
  assert(writes === 0, 'Opening a recommendation does not silently replace the saved plan')
  app.unmount(); await router.push('/app/vocabulary?book=private-or-deleted')
  app = createApp(VocabularyView); app.use(router); app.mount(fixture); await settle()
  assert(fixture.textContent.includes('这本推荐词书暂不可用'), 'Unknown or inaccessible recommended book falls back safely')
  assert(writes === 0 && calls === 0, 'Book fallback cannot write settings or request private content')
  document.documentElement.dataset.result = 'passed'; document.getElementById('results').textContent = `PASS ${passed.length}\n${passed.join('\n')}`
} catch (error) { document.documentElement.dataset.result = 'failed'; document.getElementById('results').textContent = error.stack }
finally { app?.unmount(); authStore.ensureUser = original; authStore.state.user = null; await setUiLanguage('zh') }
