<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue'
import { uiText } from '@/i18n'
const props = defineProps<{ title: string; busy?: boolean; wide?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>(), titleId = useId()
let opener: HTMLElement | null = null, overflow = '', frame = 0
let observer: MutationObserver | undefined, resizeObserver: ResizeObserver | undefined
function fit() {
  const node = dialog.value
  if (!node) return
  const scale = node.offsetWidth ? node.getBoundingClientRect().width / node.offsetWidth : 1
  const width = (window.visualViewport?.width || window.innerWidth) / (scale || 1) - 48
  const height = (window.visualViewport?.height || window.innerHeight) / (scale || 1) - 48
  node.style.width = `${Math.max(120, Math.min(props.wide ? 1100 : 580, width))}px`
  node.style.maxWidth = `${Math.max(120, width)}px`
  node.style.maxHeight = `${Math.max(80, height)}px`
}
function scheduleFit() { window.cancelAnimationFrame(frame); frame = window.requestAnimationFrame(fit) }
function close() { if (!props.busy) emit('close') }
onMounted(async () => {
  opener = document.activeElement as HTMLElement; overflow = document.body.style.overflow; document.body.style.overflow = 'hidden'
  if (dialog.value?.showModal) dialog.value.showModal(); else dialog.value?.setAttribute('open', '')
  fit()
  window.addEventListener('resize', scheduleFit); window.visualViewport?.addEventListener('resize', scheduleFit)
  observer = new MutationObserver(scheduleFit); observer.observe(document.documentElement, { attributes: true, attributeFilter: ['style', 'class'] })
  if (typeof ResizeObserver !== 'undefined') { resizeObserver = new ResizeObserver(scheduleFit); resizeObserver.observe(document.documentElement) }
  await nextTick(); dialog.value?.querySelector<HTMLElement>('[autofocus], input:not(:disabled), textarea:not(:disabled), button:not(:disabled)')?.focus()
})
onBeforeUnmount(() => { observer?.disconnect(); resizeObserver?.disconnect(); window.cancelAnimationFrame(frame); window.removeEventListener('resize', scheduleFit); window.visualViewport?.removeEventListener('resize', scheduleFit); dialog.value?.close?.(); document.body.style.overflow = overflow; if (opener?.isConnected) opener.focus() })
</script>
<template><Teleport to="body"><dialog ref="dialog" class="dialog-panel" :class="{ 'dialog-panel--wide': wide }" :aria-labelledby="titleId" :aria-busy="busy" @cancel.prevent="close" @click="event => { if (event.target === dialog) { const r = dialog!.getBoundingClientRect(); if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) close() } }"><header class="dialog-panel-heading"><h3 :id="titleId">{{ title }}</h3><button type="button" class="icon-button" :aria-label="uiText('关闭')" :disabled="busy" @click="close">×</button></header><slot /></dialog></Teleport></template>
<style scoped>
.dialog-panel{position:fixed;inset:0;margin:auto;width:min(580px,calc(100vw - 48px));max-width:calc(100vw - 48px);max-height:calc(100dvh - 64px);box-sizing:border-box;padding:28px;border:1px solid var(--line);border-radius:16px;background:var(--paper);color:var(--ink);box-shadow:0 24px 80px #142c3b26;overflow:auto}.dialog-panel--wide{width:min(1100px,calc(100vw - 48px))}.dialog-panel::backdrop{background:#12263055;backdrop-filter:blur(4px)}.dialog-panel-heading{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:24px}.dialog-panel-heading h3{margin:0;font-size:22px;font-weight:550}.dialog-panel-heading button{font-size:24px}.dialog-panel :deep(fieldset){min-width:0;padding:0;border:0}
.dialog-panel :deep(.button--dark){background:var(--green);color:var(--on-dark,#fff);border-color:var(--green)}.dialog-panel :deep(.button--dark:hover:not(:disabled)){background:var(--green);color:var(--on-dark,#fff);filter:brightness(.94)}
</style>
