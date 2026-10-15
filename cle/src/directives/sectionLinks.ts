import type { ObjectDirective } from 'vue'
import { watch, nextTick } from 'vue'
import { uiLanguage, uiText } from '@/i18n'
const cleanup = new WeakMap<HTMLElement, () => void>()
export const sectionLinks: ObjectDirective<HTMLElement> = {
  mounted(root) {
    let frame = 0
    const refresh = () => {
      frame = 0
      root.querySelectorAll<HTMLElement>('h2, h3').forEach(heading => {
        const existing = heading.querySelector<HTMLAnchorElement>(':scope > .heading-anchor')
        if (existing) {
          existing.setAttribute('aria-label', uiText('定位到') + ' ' + [...heading.childNodes].filter(node => node !== existing).map(node => node.textContent).join('').trim())
          return
        }
        if (heading.querySelector('a, button, input') || heading.closest('.modal-card, .lab-row')) return
        const label = heading.textContent?.trim()
        if (!label) return
        if (!heading.id) {
          const base = 'section-' + label.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-|-$/g, '')
          let id = base, n = 2
          while (document.getElementById(id)) id = `${base}-${n++}`
          heading.id = id
        }
        heading.classList.add('reading-heading')
        const anchor = document.createElement('a')
        anchor.className = 'heading-anchor'
        anchor.href = '#' + encodeURIComponent(heading.id)
        anchor.setAttribute('aria-label', uiText('定位到') + ' ' + label)
        anchor.textContent = '#'
        heading.prepend(anchor)
      })
    }
    const observer = new MutationObserver(() => { if (!frame) frame = requestAnimationFrame(refresh) })
    observer.observe(root, { childList: true, subtree: true })
    const stop = watch(uiLanguage, () => { void nextTick(refresh) })
    refresh()
    cleanup.set(root, () => { stop(); observer.disconnect(); cancelAnimationFrame(frame) })
  },
  beforeUnmount(root) { cleanup.get(root)?.(); cleanup.delete(root) },
}

