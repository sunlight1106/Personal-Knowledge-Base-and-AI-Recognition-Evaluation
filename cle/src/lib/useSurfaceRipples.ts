import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { motionEnabled, motionState } from './motion'

// Only surfaces, never fields, editor text, or arbitrary document links.
const surfaces = '.button,.home-primary,.home-enter,.home-folio>a,.home-chapter-links a,.workspace-action-grid>a,.workspace-directory>summary,.directory-item>a,.vocab-tabs button,.learning-toolbar nav button,.learning-sidebar>a,.learning-library-links>a,.shelf-actions button,.shelf-categories button,.settings-navigation button,.research-tabs>a,.experiment-tasks button,.mail-list>header button,.people-tabs button'

export function useSurfaceRipples() {
  const route = useRoute()
  const active = new Map<HTMLElement, ReturnType<typeof setTimeout>>()
  function remove(layer: HTMLElement) { clearTimeout(active.get(layer)); active.delete(layer); layer.remove() }
  function clear() { for (const layer of active.keys()) remove(layer) }
  function ripple(event: MouseEvent, centered = false) {
    if (!motionEnabled.value || !motionState.visible || !(event.target instanceof Element)) return
    const target = event.target.closest<HTMLElement>(surfaces)
    if (!target || target.closest('[inert],[aria-disabled=true],:disabled') || !target.matches('a,button,summary')) return
    const box = target.getBoundingClientRect()
    if (box.width <= 0 || box.height <= 0) return
    // Scroll/resize/navigation clear these viewport-positioned, non-interactive overlays.
    if (active.size >= 3) remove(active.keys().next().value!)
    const layer = document.createElement('span'), circle = document.createElement('span')
    layer.className = 'surface-ripple-layer'; circle.className = 'surface-ripple'
    layer.setAttribute('aria-hidden', 'true')
    const style = window.getComputedStyle(target), size = Math.min(900, Math.hypot(box.width, box.height) * 2)
    Object.assign(layer.style, { left: `${box.left}px`, top: `${box.top}px`, width: `${box.width}px`, height: `${box.height}px`, borderRadius: style.borderRadius, color: style.color })
    Object.assign(circle.style, { width: `${size}px`, height: `${size}px`, left: `${centered ? box.width/2 : event.clientX-box.left}px`, top: `${centered ? box.height/2 : event.clientY-box.top}px` })
    layer.append(circle)
    ;(target.closest('dialog[open]') || document.body).append(layer)
    active.set(layer, setTimeout(() => remove(layer), 850))
    circle.addEventListener('animationend', () => remove(layer), { once: true })
  }
  const pointer = (event: PointerEvent) => { if (event.button === 0 && event.isPrimary !== false) ripple(event) }
  const keyboard = (event: MouseEvent) => { if (event.detail === 0 && event.button === 0) ripple(event, true) }
  watch(() => route.fullPath, clear)
  watch(() => motionEnabled.value && motionState.visible, allowed => { if (!allowed) clear() })
  onMounted(() => {
    document.addEventListener('pointerdown', pointer, { passive: true })
    document.addEventListener('click', keyboard, { passive: true })
    document.addEventListener('scroll', clear, { passive: true, capture: true })
    window.addEventListener('resize', clear, { passive: true })
  })
  onBeforeUnmount(() => {
    clear()
    document.removeEventListener('pointerdown', pointer)
    document.removeEventListener('click', keyboard)
    document.removeEventListener('scroll', clear, true)
    window.removeEventListener('resize', clear)
  })
}
