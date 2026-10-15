import { readonly, ref, watch } from 'vue'

export type UiLanguage = 'zh' | 'en'
const language = ref<UiLanguage>('zh')
const loadError = ref(false)
let messages: Record<string, string> = {}
let englishReady: Promise<void> | undefined
let changeVersion = 0
const storageKey = 'pkb:ui-language'
let initialized = false

export const uiLanguage = readonly(language)
export const languageLoadError = readonly(loadError)
export function uiLocale() { return language.value === 'en' ? 'en-US' : 'zh-CN' }

export function uiRoleName(name?: string, code?: string) {
  const defaults: Record<string, string> = { ADMIN: '管理员', RESEARCHER: '研究员', VIEWER: '查看者' }
  return code && defaults[code] === name ? uiText(name) : name || ''
}

/** Translate application copy only. Never pass user documents or message bodies here. */
export function uiText(source: string | null | undefined): string {
  if (source == null) return ''
  if (language.value === 'zh') return source
  const key = source.trim()
  return Object.hasOwn(messages, key) ? source.replace(key, () => messages[key]!) : source
}

function applyLanguage() {
  if (typeof document === 'undefined') return
  document.documentElement.lang = uiLocale()
  document.title = language.value === 'en'
    ? 'Personal Knowledge Base & AI Evaluation'
    : '个人知识库与 AI 识别评测'
}

export async function setUiLanguage(value: unknown, persist = true) {
  if (value !== 'zh' && value !== 'en') return
  const version = ++changeVersion
  loadError.value = false
  if (value === 'en') {
    // Chinese startup does not download the English catalog.
    englishReady ??= import('./en.json').then(module => { messages = module.default }).catch(error => { englishReady = undefined; throw error })
    try { await englishReady } catch { if (version === changeVersion) loadError.value = true; return }
  }
  if (version !== changeVersion) return
  language.value = value
  applyLanguage()
  if (persist) try { localStorage.setItem(storageKey, value) } catch { /* Still usable without browser storage. */ }
}

export async function initUiLanguage() {
  if (initialized) return
  initialized = true
  let saved: UiLanguage = 'zh'
  try { saved = localStorage.getItem(storageKey) === 'en' ? 'en' : 'zh' } catch { /* Use Chinese by default. */ }
  await setUiLanguage(saved, false)
  applyLanguage()
  watch(language, applyLanguage, { flush: 'sync' })
  window.addEventListener('storage', event => {
    if (event.storageArea && event.storageArea !== localStorage) return
    if (event.key === storageKey || event.key === null) void setUiLanguage(event.newValue === 'en' ? 'en' : 'zh', false)
  })
}
