// Synthetic UI only. No sign-in requests, personal data or external models.
import { createApp, h, nextTick } from 'vue'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import HomeWorkspacePreview from '../components/HomeWorkspacePreview.vue'
import NotePreview from '../components/NotePreview.vue'
import LanguageToggle from '../components/LanguageToggle.vue'
import { initUiLanguage, setUiLanguage, uiLanguage, uiText, uiLocale } from '../i18n/index.ts'
import { navigationItems } from '../lib/navigation.ts'
import { themePalettes } from '../stores/theme.ts'

const fixture = document.getElementById('fixture'), checks = []
const assert = (value, name) => { if (!value) throw new Error(name); checks.push(name) }
const settle = async () => { await new Promise(resolve => setTimeout(resolve, 0)); await nextTick() }
let app, requests = 0
window.fetch = async () => { requests++; throw new Error('Language switching must not issue API requests') }
globalThis.requestAnimationFrame = window.requestAnimationFrame.bind(window)
globalThis.cancelAnimationFrame = window.cancelAnimationFrame.bind(window)
try {
  localStorage.setItem('pkb:ui-language', 'invalid')
  await initUiLanguage()
  assert(uiLanguage.value === 'zh' && document.documentElement.lang === 'zh-CN', 'Invalid preferences safely default to Chinese')
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/login', component: LoginView }, { path: '/:pathMatch(.*)*', component: { render: () => h('div') } }] })
  await router.push('/login?redirect=/app/notes'); await router.isReady()
  const original = '## 保存\n\n我的笔记\n\n```java\nString message = "开始学习";\n```'
  app = createApp({ render: () => h('div', [h(LanguageToggle), h(RouterView), h(HomeWorkspacePreview), h(NotePreview, { body: original })]) })
  app.use(router); app.mount(fixture); await settle()
  const username = fixture.querySelector('input[autocomplete=username]'), password = fixture.querySelector('input[autocomplete=current-password]')
  username.value = '保存'; username.dispatchEvent(new Event('input', { bubbles: true }))
  password.value = 'Synthetic-Only-42'; password.dispatchEvent(new Event('input', { bubbles: true }))
  const tabs = fixture.querySelectorAll('[role=tab]'); tabs[1].click(); await settle()
  const preview = fixture.querySelector('.markdown-body'), before = preview.textContent
  document.documentElement.dataset.accent = 'violet'
  fixture.querySelector('[data-language-toggle]').click(); await settle()
  assert(uiLanguage.value === 'en' && uiLocale() === 'en-US', 'Visible switch selects English')
  assert(document.documentElement.lang === 'en-US' && document.title.includes('Knowledge Base'), 'Document language and title follow the preference')
  assert(fixture.querySelector('h1').textContent === 'Welcome back', 'Login copy updates reactively')
  assert(username.placeholder === 'Username / PKB-… / Email', 'Input placeholder updates with the language')
  assert(fixture.querySelector('[data-language-toggle]').getAttribute('aria-label') === '切换为中文', 'Switch exposes its target language accessibly')
  assert(localStorage.getItem('pkb:ui-language') === 'en', 'Preference persists without credentials or content')
  assert(fixture.querySelector('input[autocomplete=username]') === username && username.value === '保存' && password.value === 'Synthetic-Only-42', 'Switching preserves the original form nodes and entered credentials')
  assert(router.currentRoute.value.fullPath === '/login?redirect=/app/notes', 'Switching preserves route and redirect query')
  assert(fixture.querySelectorAll('[role=tab]')[1] === tabs[1] && tabs[1].getAttribute('aria-selected') === 'true', 'Selected preview tab survives without remounting')
  assert(tabs[1].textContent.trim() === 'Learn', 'Metadata labels also change language')
  assert(preview.textContent === before && preview.textContent.includes('我的笔记') && preview.textContent.includes('开始学习'), 'User note text and code stay unchanged even when they match UI keys')
  assert(document.documentElement.dataset.accent === 'violet', 'Language switching preserves appearance settings')
  assert(uiText('  保存  ') === '  Save  ' && uiText('Custom content 甲乙') === 'Custom content 甲乙', 'Known UI copy preserves spacing and unknown copy remains intact')
  assert(uiText('constructor') === 'constructor' && uiText(undefined) === '', 'Catalog lookup does not resolve prototype properties')
  assert(navigationItems.every(item => uiText(item.label) !== item.label), 'Every registered navigation entry has English copy')
  assert(themePalettes.every(item => uiText(item.label) !== item.label && uiText(item.description) !== item.description), 'Every theme palette has an English name and description')
  await setUiLanguage('invalid'); assert(uiLanguage.value === 'en', 'Unsupported languages cannot replace the current choice')
  await Promise.all([setUiLanguage('en'), setUiLanguage('zh')]); await settle()
  assert(uiLanguage.value === 'zh' && fixture.querySelector('h1').textContent === '欢迎回来', 'The latest selection wins even during asynchronous loading')
  window.dispatchEvent(new StorageEvent('storage', { key: 'pkb:ui-language', newValue: 'en' })); await settle()
  assert(uiLanguage.value === 'en' && username.value === '保存', 'Another tab can update language without changing local form state')
  window.dispatchEvent(new StorageEvent('storage', { key: null, newValue: null })); await settle()
  assert(uiLanguage.value === 'zh', 'Clearing browser preferences restores Chinese')
  const originalSet = Object.getPrototypeOf(localStorage).setItem
  Object.getPrototypeOf(localStorage).setItem = () => { throw new Error('Storage blocked') }
  try { await setUiLanguage('en'); assert(uiLanguage.value === 'en', 'Language remains usable when persistent storage is blocked') }
  finally { Object.getPrototypeOf(localStorage).setItem = originalSet }
  assert(requests === 0, 'Switching makes no authentication, content or provider API calls')
  document.getElementById('results').textContent = `PASS ${checks.length}\n` + checks.join('\n')
  document.documentElement.dataset.result = 'passed'
} catch (error) {
  document.getElementById('results').textContent = `FAIL after ${checks.length}: ${error.stack}`
  document.documentElement.dataset.result = 'failed'
} finally { app?.unmount(); await setUiLanguage('zh'); localStorage.removeItem('pkb:ui-language') }
