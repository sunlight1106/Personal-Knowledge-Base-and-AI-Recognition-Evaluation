import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { tokenStorage } from '@/api/client'
import { researchApi, type NotificationItem } from '@/api/research'
import { authStore } from '@/stores/auth'

// A popup is a delivery hint, never a read receipt. Only the source page marks content read.
export function useNotifications() {
  const items = ref<NotificationItem[]>([]), popups = ref<NotificationItem[]>([])
  const total = computed(() => items.value.reduce((sum, item) => sum + item.count, 0))
  let epoch = 0, stopped = false, running = false, pending = false
  let transport: AbortController | undefined, stream: AbortController | undefined
  let retry: ReturnType<typeof setTimeout> | undefined, poll: ReturnType<typeof setInterval> | undefined
  const timers = new Map<string, ReturnType<typeof setTimeout>>()
  const staged = new Map<string, ReturnType<typeof setTimeout>>()
  let seen = new Map<string, string>()
  const version = (item: NotificationItem) => item.version ?? String(item.count)
  const storageKey = () => `pkb:notification-delivery:${authStore.state.user?.id}`
  function persist() {
    // Store IDs only, bounded per tab and account. Never persist message text.
    seen = new Map([...seen].slice(-600))
    try { window.sessionStorage.setItem(storageKey(), JSON.stringify([...seen])) } catch { /* Storage may be disabled. */ }
  }
  function dismiss(id: string) {
    clearTimeout(timers.get(id)); timers.delete(id)
    popups.value = popups.value.filter(item => item.id !== id)
  }
  function pause(id: string) { clearTimeout(timers.get(id)); timers.delete(id) }
  function resume(id: string) {
    pause(id)
    if (!document.hidden && popups.value.some(item => item.id === id)) timers.set(id, setTimeout(() => dismiss(id), 5000))
  }
  function reconcile(rows: NotificationItem[]) {
    items.value = rows.filter(item => item.count > 0)
    const visible = new Map(items.value.map(item => [item.id, item]))
    for (const popup of popups.value) {
      if (!visible.has(popup.id)) dismiss(popup.id)
      else Object.assign(popup, visible.get(popup.id))
    }
    for (const [id, timer] of staged) if (!visible.has(id)) { clearTimeout(timer); staged.delete(id) }
    if (document.hidden) return
    const arrivals = items.value.filter(item => item.popup !== false && seen.get(item.id) !== version(item))
    for (const item of arrivals) seen.set(item.id, version(item))
    persist()
    for (const item of arrivals.slice(0, 3)) {
      clearTimeout(staged.get(item.id))
      // Give an open conversation time to acknowledge the newly rendered message.
      staged.set(item.id, setTimeout(() => {
        staged.delete(item.id)
        const current = items.value.find(row => row.id === item.id)
        if (!current || stopped) return
        if (document.hidden) { seen.delete(item.id); persist(); return }
        dismiss(item.id)
        if (popups.value.length >= 3) dismiss(popups.value[0]!.id)
        popups.value.push({ ...current }); resume(item.id)
      }, 600))
    }
  }
  async function refresh() {
    if (stopped || !authStore.state.user) return
    if (running) { pending = true; return }
    running = true
    const generation = epoch
    try {
      do {
        pending = false; transport = new AbortController()
        const rows = await researchApi.notifications(transport.signal)
        if (stopped || generation !== epoch) return
        // A read happened while this request was in flight: discard the stale response.
        if (!pending) reconcile(rows)
      } while (pending && !stopped && generation === epoch)
    } catch { /* Keep the last known state until the bounded poll/reconnect retries. */ }
    finally { if (generation === epoch) { running = false; if (pending) { pending = false; void refresh() } } }
  }
  async function connect() {
    if (stopped || !authStore.state.user) return
    const generation = epoch, session = tokenStorage.generation(), controller = new AbortController()
    stream = controller
    try {
      const response = await fetch((import.meta.env.VITE_API_BASE_URL || '/api/v1') + '/notifications/events', {
        headers: { Authorization: `Bearer ${tokenStorage.get()}` }, signal: controller.signal, cache: 'no-store',
      })
      if (response.status === 401 && generation === epoch && session === tokenStorage.generation()) {
        tokenStorage.clear()
        window.dispatchEvent(new Event('personal-platform:session-expired'))
        return
      }
      if (!response.ok || !response.body) return
      // Catch changes in the reconnect gap, rather than waiting for the next event.
      void refresh()
      window.dispatchEvent(new Event('pkb:live-update'))
      const reader = response.body.getReader(), decoder = new TextDecoder()
      try {
        let buffer = ''
        while (!stopped && generation === epoch && session === tokenStorage.generation()) {
          const { done, value } = await reader.read(); if (done) break
          buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
          let end: number
          while ((end = buffer.indexOf('\n\n')) >= 0) {
            const event = buffer.slice(0, end); buffer = buffer.slice(end + 2)
            if (/^event:\s*refresh\s*$/m.test(event)) {
              void refresh(); window.dispatchEvent(new Event('pkb:live-update'))
            }
          }
        }
      } finally { await reader.cancel().catch(() => {}) }
    } catch { /* Polling remains available when the stream is temporarily unavailable. */ }
    finally { if (!stopped && generation === epoch && session === tokenStorage.generation()) retry = setTimeout(connect, 2500) }
  }
  function reset() {
    epoch++; transport?.abort(); stream?.abort(); clearTimeout(retry)
    running = false; pending = false
    for (const timer of timers.values()) clearTimeout(timer)
    for (const timer of staged.values()) clearTimeout(timer)
    timers.clear(); staged.clear(); items.value = []; popups.value = []; seen.clear()
  }
  function start() {
    reset()
    if (!authStore.state.user) return
    try { seen = new Map(JSON.parse(window.sessionStorage.getItem(storageKey()) || '[]')) } catch { seen = new Map() }
    void refresh(); void connect()
  }
  function visibility() {
    if (document.hidden) { for (const popup of popups.value) pause(popup.id) }
    else { for (const popup of popups.value) resume(popup.id); void refresh(); window.dispatchEvent(new Event('pkb:live-update')) }
  }
  watch(() => authStore.state.user?.id, start, { flush: 'sync' })
  onMounted(() => {
    start(); poll = setInterval(refresh, 30000)
    window.addEventListener('personal-platform:notifications-changed', refresh)
    document.addEventListener('visibilitychange', visibility)
  })
  onBeforeUnmount(() => {
    stopped = true; reset(); clearInterval(poll)
    window.removeEventListener('personal-platform:notifications-changed', refresh)
    document.removeEventListener('visibilitychange', visibility)
  })
  return { items, popups, total, dismiss, pause, resume, refresh }
}
