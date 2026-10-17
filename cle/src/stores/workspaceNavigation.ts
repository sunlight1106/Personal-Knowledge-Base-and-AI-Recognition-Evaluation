import { computed, reactive, watch } from 'vue'
import { authStore } from './auth'
import { navigationItems, canVisit, type NavItem } from '@/lib/navigation'

const defaults = ['/app/home', '/app/notes', '/app/vocabulary', '/app/knowledge', '/app/upload']
export const PIN_LIMIT = 12
const state = reactive({ owner: '', pins: [...defaults], recent: [] as string[], persistent: true })
const known = new Set(navigationItems.map(item => item.to))
const clean = (value: unknown, limit: number) => Array.isArray(value)
  ? [...new Set(value.filter((path): path is string => typeof path === 'string' && known.has(path)))].slice(0, limit) : []

watch(() => authStore.state.user?.id, id => {
  state.owner = id == null ? '' : String(id)
  state.pins = [...defaults]
  state.recent = []
  state.persistent = true
  if (!state.owner) return
  try {
    const raw = localStorage.getItem(`pkb:navigation:${state.owner}`)
    if (!raw) return
    const saved = JSON.parse(raw)
    if (saved?.version !== 1) return
    if (Array.isArray(saved.pins)) state.pins = clean(saved.pins, PIN_LIMIT)
    state.recent = clean(saved.recent, 6)
  } catch { state.persistent = false }
}, { immediate: true, flush: 'sync' })

function save() {
  if (!state.owner) return
  try {
    localStorage.setItem(`pkb:navigation:${state.owner}`, JSON.stringify({ version: 1, pins: state.pins, recent: state.recent }))
    state.persistent = true
  } catch { state.persistent = false }
}
const allowed = (item: NavItem) => !!authStore.state.user && canVisit(item, authStore.state.user.permissions)
const resolve = (paths: string[]) => paths.map(path => navigationItems.find(item => item.to === path)).filter((item): item is NavItem => !!item && allowed(item))

export const workspaceNavigation = {
  state,
  pinned: computed(() => resolve(state.pins)),
  recent: computed(() => resolve(state.recent)),
  toggle(path: string) {
    const item = navigationItems.find(item => item.to === path)
    if (!item || !allowed(item)) return false
    // Discard revoked entries when editing so invisible pins never consume capacity.
    const pins = resolve(state.pins).map(item => item.to)
    if (pins.includes(path)) state.pins = pins.filter(value => value !== path)
    else if (pins.length < PIN_LIMIT) state.pins = [...pins, path]
    else return false
    save()
    return true
  },
  visit(path: string) {
    // Save module names only; note IDs, search terms and message contents stay out of storage.
    const item = navigationItems.find(item => path === item.to || path.startsWith(item.to + '/'))
    if (!item || !allowed(item) || item.to === '/app/home') return
    state.recent = [item.to, ...state.recent.filter(value => value !== item.to)].slice(0, 6)
    save()
  },
  clearRecent() { state.recent = []; save() },
}
