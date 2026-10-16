import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { motionEnabled, motionState } from './motion'

const cards = '.workspace-action-grid>a,.workspace-preview,.home-chapter,.shelf-book,.learning-library-links>a,.theme-palette'
const waterHosts = '.water-surface,.workspace-welcome,.home-hero,.app-content'
const excluded = 'input,textarea,select,[contenteditable]:not([contenteditable=false]),.markdown-body,.note-preview-pane,pre,code,[inert],:disabled,[aria-disabled=true]'

// One delegated listener and one pending frame for the whole workspace. No idle loop.
export function useSurfaceInteraction() {
  const route = useRoute()
  let finePointer: MediaQueryList | undefined
  let card: HTMLElement | null = null, light: HTMLElement | null = null, water: HTMLElement | null = null
  let frame = 0, pending: { source: Element; x: number; y: number } | undefined
  const allowed = () => motionEnabled.value && motionState.visible && !!finePointer?.matches
  function clearCard() { light?.remove(); light = null; card = null }
  function clearWater() {
    water?.style.removeProperty('--surface-water-x')
    water?.style.removeProperty('--surface-water-y')
    water = null
  }
  function clear() {
    window.cancelAnimationFrame(frame); frame = 0; pending = undefined
    clearCard(); clearWater()
  }
  const within = (box: DOMRect, x: number, y: number) => box.width > 0 && box.height > 0 && x >= box.left && x <= box.right && y >= box.top && y <= box.bottom
  function paint() {
    frame = 0
    const point = pending; pending = undefined
    if (!point || !allowed() || !point.source.isConnected || point.source.closest(excluded)) { clear(); return }
    const nextCard = point.source.closest<HTMLElement>(cards)
    if (nextCard !== card) clearCard()
    if (nextCard) {
      const box = nextCard.getBoundingClientRect()
      if (within(box, point.x, point.y)) {
        if (!light) {
          card = nextCard
          light = document.createElement('span')
          light.className = 'surface-light'
          light.setAttribute('aria-hidden', 'true')
          card.append(light)
        }
        // Percentages also work at browser / CSS zoom without changing target geometry.
        light.style.setProperty('--surface-x', `${(point.x - box.left) / box.width * 100}%`)
        light.style.setProperty('--surface-y', `${(point.y - box.top) / box.height * 100}%`)
      } else clearCard()
    }
    const host = point.source.closest<HTMLElement>(waterHosts)
    const nextWater = host?.querySelector<HTMLElement>(':scope > .ambient-waves[data-running=true]') || null
    if (nextWater !== water) clearWater()
    if (nextWater) {
      const box = nextWater.getBoundingClientRect()
      if (within(box, point.x, point.y)) {
        water = nextWater
        water.style.setProperty('--surface-water-x', `${((point.x - box.left) / box.width - .5) * 20}px`)
        water.style.setProperty('--surface-water-y', `${((point.y - box.top) / box.height - .5) * 8}px`)
      } else clearWater()
    }
  }
  function move(event: PointerEvent) {
    if (!allowed() || event.pointerType === 'touch' || event.buttons || !(event.target instanceof Element)
      || event.target.closest(excluded) || !event.target.closest(`${cards},${waterHosts}`)) { clear(); return }
    pending = { source: event.target, x: event.clientX, y: event.clientY }
    if (!frame) frame = window.requestAnimationFrame(paint)
  }
  const leave = (event: PointerEvent) => { if (!event.relatedTarget) clear() }
  const keyboard = (event: KeyboardEvent) => { if (event.key === 'Tab' || event.key === 'Escape') clear() }
  const capabilityChanged = () => { if (!allowed()) clear() }
  watch(() => route.fullPath, clear)
  watch(() => motionEnabled.value && motionState.visible, enabled => { if (!enabled) clear() }, { flush: 'sync' })
  onMounted(() => {
    finePointer = window.matchMedia('(hover: hover) and (pointer: fine)')
    finePointer.addEventListener('change', capabilityChanged)
    document.addEventListener('pointermove', move, { passive: true })
    document.addEventListener('pointerout', leave, { passive: true })
    document.addEventListener('pointerdown', clear, { passive: true })
    document.addEventListener('scroll', clear, { passive: true, capture: true })
    document.addEventListener('keydown', keyboard)
    window.addEventListener('resize', clear, { passive: true })
    window.addEventListener('blur', clear)
  })
  onBeforeUnmount(() => {
    clear(); finePointer?.removeEventListener('change', capabilityChanged)
    document.removeEventListener('pointermove', move)
    document.removeEventListener('pointerout', leave)
    document.removeEventListener('pointerdown', clear)
    document.removeEventListener('scroll', clear, true)
    document.removeEventListener('keydown', keyboard)
    window.removeEventListener('resize', clear)
    window.removeEventListener('blur', clear)
  })
}
