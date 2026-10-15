<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import { uiText, uiLanguage } from '@/i18n'

defineProps<{ label: string; text: string }>()
const id = useId()
const trigger = ref<HTMLButtonElement | null>(null)
const bubble = ref<HTMLElement | null>(null)
const open = ref(false)
const target = ref<HTMLElement | string>('body')
const position = ref({ left: '0px', top: '0px', visibility: 'hidden' as 'hidden' | 'visible' })
let timer = 0
let hovered = false
let keyboardFocus = false
let pointerFocus = false
let disposed = false

function clearTimer() { window.clearTimeout(timer); timer = 0 }
function close() {
  clearTimer()
  open.value = false
  window.removeEventListener('keydown', onKey, true)
  window.removeEventListener('pointerdown', outside, true)
  window.removeEventListener('scroll', close, true)
  window.removeEventListener('resize', close)
  window.removeEventListener('pkb:help-open', otherHelp)
}
function place() {
  if (!trigger.value || !bubble.value || !open.value) return
  const anchor = trigger.value.getBoundingClientRect()
  const box = bubble.value.getBoundingClientRect()
  // Rectangles use viewport pixels; fixed offsets can be scaled by page zoom
  // or a transformed dialog. Convert back to the tooltip's own coordinates.
  const scale = bubble.value.offsetWidth ? box.width / bubble.value.offsetWidth : 1
  const originX = box.left - Number.parseFloat(position.value.left) * scale
  const originY = box.top - Number.parseFloat(position.value.top) * scale
  const width = document.documentElement.clientWidth || window.innerWidth
  const height = document.documentElement.clientHeight || window.innerHeight
  const left = Math.max(12, Math.min(anchor.left + anchor.width / 2 - box.width / 2, width - box.width - 12))
  const below = anchor.bottom + 10
  const top = below + box.height <= height - 12 ? below : Math.max(12, anchor.top - box.height - 10)
  position.value = { left: `${(left - originX) / scale}px`, top: `${(top - originY) / scale}px`, visibility: 'visible' }
}
async function show() {
  clearTimer()
  if (disposed || open.value || !trigger.value) return
  window.dispatchEvent(new CustomEvent('pkb:help-open', { detail: id }))
  target.value = trigger.value.closest('dialog') || 'body'
  position.value = { left: '0px', top: '0px', visibility: 'hidden' }
  open.value = true
  window.addEventListener('keydown', onKey, true)
  window.addEventListener('pointerdown', outside, true)
  window.addEventListener('scroll', close, true)
  window.addEventListener('resize', close)
  window.addEventListener('pkb:help-open', otherHelp)
  await nextTick()
  if (!disposed) place()
}
function enter() { hovered = true; clearTimer(); timer = window.setTimeout(show, 180) }
function leave() { hovered = false; clearTimer(); if (!keyboardFocus) timer = window.setTimeout(close, 140) }
function focus() { if (!pointerFocus) { keyboardFocus = true; void show() } }
function blur() { keyboardFocus = false; pointerFocus = false; if (!hovered) close() }
function onKey(event: KeyboardEvent) { if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close() } }
function outside(event: PointerEvent) {
  if (event.target instanceof Node && !trigger.value?.contains(event.target) && !bubble.value?.contains(event.target)) close()
}
function otherHelp(event: Event) { if ((event as CustomEvent).detail !== id) close() }
watch(uiLanguage, async () => { await nextTick(); place() })
onBeforeUnmount(() => { disposed = true; close() })
</script>

<template>
  <span class="help-hint">
    <button ref="trigger" type="button" class="help-hint__trigger"
      :aria-label="`${uiText(label)} · ${uiText('简易说明')}`" :aria-describedby="open ? id : undefined"
      @pointerenter="enter" @pointerleave="leave" @pointerdown="pointerFocus = true; keyboardFocus = false"
      @focus="focus" @blur="blur" @click.stop="show">?</button>
    <Teleport v-if="open" :to="target">
      <span :id="id" ref="bubble" class="help-hint__bubble" role="tooltip" :style="position"
        @pointerenter="hovered = true; clearTimer()" @pointerleave="leave">
        <strong>{{ uiText(label) }}</strong><span>{{ uiText(text) }}</span>
      </span>
    </Teleport>
  </span>
</template>

<style scoped>
.help-hint{display:inline-flex;vertical-align:middle;margin-inline-start:8px;font-size:13px;font-weight:400;line-height:1;letter-spacing:normal}
.help-hint .help-hint__trigger{display:inline-grid;place-items:center;flex:none;width:21px;height:21px;min-height:0;min-width:0;padding:0;border:1px solid var(--line);border-radius:50%;background:transparent;color:var(--muted);font:500 12px/1 system-ui,sans-serif;cursor:help;box-shadow:none;transform:none;transition:color .16s,border-color .16s,background .16s}
.help-hint .help-hint__trigger:hover,.help-hint .help-hint__trigger:focus-visible{color:var(--green);border-color:var(--green);background:var(--green-soft)}
.help-hint .help-hint__trigger:focus-visible{outline:2px solid var(--green);outline-offset:3px}
.help-hint__bubble{position:fixed;z-index:10000;box-sizing:border-box;display:grid;gap:7px;width:max-content;max-width:min(300px,calc(100vw - 24px));padding:15px 18px;border:1px solid var(--line);border-radius:12px;background:var(--paper,#fff);color:var(--ink);box-shadow:0 8px 30px rgb(0 0 0 / .10);font:400 13px/1.75 system-ui,sans-serif;letter-spacing:normal;text-align:start;white-space:normal;overflow-wrap:anywhere;text-transform:none;animation:help-appear .14s ease-out}
.help-hint__bubble strong{font-size:12px;font-weight:600;color:var(--green)}
@keyframes help-appear{from{opacity:0}to{opacity:1}}
@media(prefers-reduced-motion:reduce){.help-hint .help-hint__trigger{transition:none}.help-hint__bubble{animation:none}}
</style>
