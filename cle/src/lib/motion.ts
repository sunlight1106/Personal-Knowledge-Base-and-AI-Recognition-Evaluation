import { computed, onBeforeUnmount, onMounted, reactive, watchEffect } from 'vue'

const preferenceKey = 'pkb:ambient-motion'
type MotionPreference = 'system' | 'enabled' | 'paused'
export const motionState = reactive({ preference: 'system' as MotionPreference, reduced: false, visible: true })
export const motionEnabled = computed(() => motionState.preference === 'enabled' || (motionState.preference === 'system' && !motionState.reduced))

export function toggleMotion() {
  motionState.preference = motionEnabled.value ? 'paused' : 'enabled'
  try { localStorage.setItem(preferenceKey, motionState.preference) } catch { /* The current session still honors the choice. */ }
}

export function useMotionPreferences() {
  let media: MediaQueryList | undefined
  const updateReduced = () => { motionState.reduced = !!media?.matches }
  const updateVisibility = () => { motionState.visible = document.visibilityState !== 'hidden' }
  watchEffect(() => { document.documentElement.dataset.motion = motionEnabled.value ? 'on' : 'off' })
  onMounted(() => {
    motionState.preference = 'system'
    try {
      const saved = localStorage.getItem(preferenceKey)
      if (saved === 'enabled' || saved === 'paused') motionState.preference = saved
    } catch { /* Use the system preference. */ }
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
