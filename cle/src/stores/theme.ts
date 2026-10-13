import { reactive } from 'vue'

export type Theme = 'light' | 'dark'
export type ThemePreference = Theme | 'system'
export type Accent = 'rose' | 'sage' | 'blue' | 'violet'
export type Density = 'comfortable' | 'compact'
export const themePalettes: ReadonlyArray<{ id: Accent; label: string; description: string }> = [
  { id: 'sage', label: '鼠尾草', description: '暖纸 · 草木绿' },
  { id: 'rose', label: '玫瑰', description: '柔白 · 灰玫瑰' },
  { id: 'blue', label: '雾蓝', description: '冷纸 · 湖蓝' },
  { id: 'violet', label: '鸢尾', description: '淡灰 · 烟紫' },
]
const defaultAccent: Accent = 'sage'
const state = reactive<{ theme: Theme; preference: ThemePreference; accent: Accent; density: Density }>({ theme: 'light', preference: 'system', accent: defaultAccent, density: 'comfortable' })
let scope = 'guest'
let listening = false
function apply(persist = true) {
  state.theme = state.preference === 'system' ? window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light' : state.preference
  document.documentElement.dataset.theme = state.theme
  document.documentElement.dataset.accent = state.accent
  document.body.dataset.density = state.density
  if (persist) {
    try { localStorage.setItem(`pp:appearance:${scope}`, JSON.stringify({ theme: state.preference, accent: state.accent, density: state.density })) } catch { /* Memory-only fallback. */ }
  }
}
function loadScope(userId?: number) {
  scope = userId == null ? 'guest' : String(userId)
  state.preference = 'system'; state.accent = defaultAccent; state.density = 'comfortable'
  try {
    const saved = JSON.parse(localStorage.getItem(`pp:appearance:${scope}`) || '{}')
    if (['light', 'dark', 'system'].includes(saved.theme)) state.preference = saved.theme
    if (themePalettes.some(palette => palette.id === saved.accent)) state.accent = saved.accent
    if (['comfortable', 'compact'].includes(saved.density)) state.density = saved.density
  } catch { /* Invalid preferences use defaults. */ }
  apply(false)
}
export const themeStore = {
  state,
  init() {
    loadScope()
    if (!listening) {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => { if (state.preference === 'system') apply(false) })
      listening = true
    }
  },
  useAccount: loadScope,
  setPreference(preference: ThemePreference) { state.preference = preference; apply() },
  setAccent(accent: Accent) { state.accent = accent; apply() },
  setDensity(density: Density) { state.density = density; apply() },
  reset() { state.preference = 'system'; state.accent = defaultAccent; state.density = 'comfortable'; apply() },
  toggle() { state.preference = state.theme === 'dark' ? 'light' : 'dark'; apply() },
  isDark() { return state.theme === 'dark' },
}
