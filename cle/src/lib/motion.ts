import { computed, onBeforeUnmount, onMounted, reactive, watchEffect } from 'vue'

const preferenceKey = 'pkb:ambient-motion'
export const motionState = reactive({ paused: false, reduced: false, visible: true })
export const motionEnabled = computed(() => !motionState.paused && !motionState.reduced)

export function toggleMotion() {
  if (motionState.reduced) return
  motionState.paused = !motionState.paused
  try { localStorage.setItem(preferenceKey, motionState.paused ? 'paused' : 'enabled') } catch { /* The current session still honors the choice. */ }
}

export function useMotionPreferences() {
  let media: MediaQueryList | undefined
  const updateReduced = () => { motionState.reduced = !!media?.matches }
  const updateVisibility = () => { motionState.visible = document.visibilityState !== 'hidden' }
  watchEffect(() => { document.documentElement.dataset.motion = motionEnabled.value ? 'on' : 'off' })
  onMounted(() => {
    try { motionState.paused = localStorage.getItem(preferenceKey) === 'paused' } catch { /* Use the system preference. */ }
    media = window.matchMedia('(prefers-reduced-motion: reduce)')
    updateReduced(); updateVisibility()
    media.addEventListener('change', updateReduced)
    document.addEventListener('visibilitychange', updateVisibility)
  })
  onBeforeUnmount(() => {
    media?.removeEventListener('change', updateReduced)
    document.removeEventListener('visibilitychange', updateVisibility)
    delete document.documentElement.dataset.motion
  })
}
