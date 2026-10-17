import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { motionEnabled, motionState } from './motion'

// Only surfaces, never fields, editor text, or arbitrary document links.
const surfaces = '.button,.provider-select,.home-primary,.home-enter,.home-folio>a,.home-chapter-links a,.workspace-action-grid>a,.workspace-quick a,.workspace-directory>summary,.directory-item>a,.vocab-tabs button,.learning-toolbar nav button,.learning-sidebar>a,.learning-library-links>a,.shelf-actions button,.shelf-categories button,.settings-navigation button,.research-tabs>a,.experiment-tasks button,.mail-list>header button,.people-tabs button,.preview-tabs button,.preview-recall,.preview-practice-actions button,.ai-setting-tabs button,.theme-palette,.guide-step-link,.guide-step-detail>summary,.beginner-walkthrough>details>summary'

export function useSurfaceRipples() {
  const route = useRoute()
  const active = new Map<HTMLElement, ReturnType<typeof setTimeout>>()
  function remove(layer: HTMLElement) { clearTimeout(active.get(layer)); active.delete(layer); layer.remove() }
  function clear() { for (const layer of active.keys()) remove(layer) }
  function ripple(event: MouseEvent, centered = false) {
    if (!motionEnabled.value || !motionState.visible || !(event.target instanceof Element)) return
    const target = event.target.closest<HTMLElement>(surfaces)
    if (!target || target.closest('[inert],[aria-disabled=true],:disabled,[contenteditable=true],.markdown-body,.note-preview-pane') || !target.matches('a,button,summary')) return
    const box = target.getBoundingClientRect()
    if (box.width <= 0 || box.height <= 0) return
    // Scroll/resize/navigation clear these viewport-positioned, non-interactive overlays.
    if (active.size >= 3) remove(active.keys().next().value!)
    const layer = document.createElement('span'), circle = document.createElement('span'), echo = document.createElement('span')
    layer.className = 'surface-ripple-layer'; circle.className = 'surface-ripple'; echo.className = 'surface-ripple surface-ripple--echo'
    layer.setAttribute('aria-hidden', 'true')
    const style = window.getComputedStyle(target), size = Math.min(900, Math.hypot(box.width, box.height) * 2)
    Object.assign(layer.style, { left: '0px', top: '0px', width: '100px', height: '100px', borderRadius: style.borderRadius, color: style.color })
    ;(target.closest('dialog[open]') || document.body).append(layer)
    // Resolve zoom and the fixed-position origin, including transformed dialogs.
    const origin = layer.getBoundingClientRect(), scale = layer.offsetWidth ? origin.width / layer.offsetWidth : 1
    Object.assign(layer.style, { left: `${(box.left-origin.left)/scale}px`, top: `${(box.top-origin.top)/scale}px`, width: `${box.width/scale}px`, height: `${box.height/scale}px` })
    const x = centered ? box.width/2 : Math.max(0, Math.min(box.width, event.clientX-box.left))
    const y = centered ? box.height/2 : Math.max(0, Math.min(box.height, event.clientY-box.top))
    for (const ring of [circle, echo]) Object.assign(ring.style, { width: `${size/scale}px`, height: `${size/scale}px`, left: `${x/scale}px`, top: `${y/scale}px` })
    layer.append(circle, echo)
    active.set(layer, setTimeout(() => remove(layer), 1350))
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
