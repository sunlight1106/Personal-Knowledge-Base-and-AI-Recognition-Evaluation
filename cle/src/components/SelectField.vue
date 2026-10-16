<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, onUpdated, ref, useId, watch } from 'vue'
import { uiText, uiLanguage } from '@/i18n'

// The slotted select owns values, Vue's v-model modifiers, and form validation.
// This component only replaces its presentation; choosing an option follows the
// same native change path, including numeric/boolean values and existing guards.
type Choice = { index: number; text: string; disabled: boolean; group: string }
const id = `select-${useId()}`
const root = ref<HTMLElement>(), trigger = ref<HTMLButtonElement>(), popup = ref<HTMLElement>()
const search = ref<HTMLInputElement>(), list = ref<HTMLElement>()
const ready = ref(false), open = ref(false), disabled = ref(false), required = ref(false)
const wide = ref(false), compact = ref(false), invalid = ref(false)
const label = ref(''), labelledBy = ref(''), describedBy = ref(''), selected = ref(-1)
const choices = ref<Choice[]>([]), active = ref(-1), query = ref('')
const target = ref<HTMLElement | string>('body')
const position = ref<Record<string, string>>({ visibility: 'hidden', left: '0px', top: '0px' })
const searchable = computed(() => choices.value.length > 8)
const filtered = computed(() => {
  const words = query.value.trim().toLocaleLowerCase().split(/\s+/u).filter(Boolean)
  return choices.value.filter(item => words.every(word => `${item.text} ${item.group}`.toLocaleLowerCase().includes(word)))
})
const selectedText = computed(() => choices.value.find(item => item.index === selected.value)?.text || uiText('请选择'))
const activeId = computed(() => open.value && filtered.value.some(item => item.index === active.value) ? `${id}-${active.value}` : undefined)
let native: HTMLSelectElement | undefined, observer: MutationObserver | undefined
let form: HTMLFormElement | null = null, disposed = false, fingerprint = '', typeBuffer = '', typeTimer = 0
let anchorPosition: DOMRect | undefined

function sync() {
  if (!native) return
  disabled.value = native.matches(':disabled')
  required.value = native.required
  wide.value = native.classList.contains('field-input')
  compact.value = native.classList.contains('compact-select')
  labelledBy.value = native.getAttribute('aria-labelledby') || ''
  describedBy.value = native.getAttribute('aria-describedby') || ''
  label.value = native.getAttribute('aria-label') || Array.from(native.labels || []).map(item => {
    const copy = item.cloneNode(true) as HTMLElement
    copy.querySelectorAll('select,button,input,textarea,.help-hint,.select-field').forEach(child => child.remove())
    return copy.textContent?.replace(/\s+/gu, ' ').trim()
  }).filter(Boolean).join(' ') || native.title || uiText('选择选项')
  const values = Array.from(native.options).map((item, index) => ({
    index, text: item.label, disabled: item.disabled || (item.parentElement?.tagName === 'OPTGROUP' && (item.parentElement as HTMLOptGroupElement).disabled),
    group: item.parentElement?.tagName === 'OPTGROUP' ? (item.parentElement as HTMLOptGroupElement).label : '',
    hidden: item.hidden || !!item.parentElement?.hidden,
  })).filter(item => !item.hidden)
  const signature = JSON.stringify(values)
  if (signature !== fingerprint) { fingerprint = signature; choices.value = values }
  selected.value = native.selectedIndex
  if (invalid.value && native.validity.valid) invalid.value = false
  if (open.value && disabled.value) close()
  if (open.value && !filtered.value.some(item => item.index === active.value && !item.disabled)) resetActive()
}
function resetActive() {
  active.value = filtered.value.find(item => item.index === selected.value && !item.disabled)?.index
    ?? filtered.value.find(item => !item.disabled)?.index ?? -1
}
function revealActive() {
  const row = document.getElementById(`${id}-${active.value}`)
  if (!row || !list.value) return
  const rowBox = row.getBoundingClientRect(), listBox = list.value.getBoundingClientRect()
  const scale = list.value.offsetHeight ? listBox.height / list.value.offsetHeight : 1
  if (rowBox.top < listBox.top) list.value.scrollTop -= (listBox.top - rowBox.top) / scale
  else if (rowBox.bottom > listBox.bottom) list.value.scrollTop += (rowBox.bottom - listBox.bottom) / scale
}
async function place() {
  if (!open.value || !trigger.value || !popup.value) return
  const anchor = trigger.value.getBoundingClientRect(), box = popup.value.getBoundingClientRect()
  anchorPosition = anchor
  const scale = popup.value.offsetWidth ? box.width / popup.value.offsetWidth : 1
  const originX = box.left - parseFloat(position.value.left || '0') * scale
  const originY = box.top - parseFloat(position.value.top || '0') * scale
  const width = document.documentElement.clientWidth || window.innerWidth, height = document.documentElement.clientHeight || window.innerHeight
  const below = height - anchor.bottom - 20, above = anchor.top - 20
  const upwards = below < box.height && above > below
  const menuWidth = Math.min(Math.max(anchor.width / scale, 240), 560, (width - 24) / scale)
  position.value = { ...position.value, width: `${menuWidth}px`, maxHeight: `${Math.min(384, Math.max(80, (upwards ? above : below) / scale))}px` }
  await nextTick()
  if (!open.value || !popup.value) return
  const measured = popup.value.getBoundingClientRect()
  const left = Math.max(12, Math.min(anchor.left, width - measured.width - 12))
  const top = upwards ? Math.max(12, anchor.top - measured.height - 8) : anchor.bottom + 8
  position.value = { ...position.value, left: `${(left - originX) / scale}px`, top: `${(top - originY) / scale}px`, visibility: 'visible' }
  revealActive()
}
async function show() {
  sync()
  if (disabled.value || open.value || disposed || !native) return
  window.dispatchEvent(new CustomEvent('pkb:select-open', { detail: id }))
  window.dispatchEvent(new CustomEvent('pkb:help-open', { detail: id }))
  query.value = ''; resetActive()
  target.value = native.closest('dialog') || 'body'
  position.value = { visibility: 'hidden', left: '0px', top: '0px' }
  open.value = true
  window.addEventListener('pointerdown', outside, true)
  window.addEventListener('focusin', outside, true)
  window.addEventListener('keydown', escape, true)
  window.addEventListener('scroll', scroll, true)
  window.addEventListener('resize', dismiss)
  window.addEventListener('blur', dismiss)
  window.addEventListener('pkb:select-open', otherSelect)
  await nextTick()
  if (!open.value || disposed) return
  await place()
  if (open.value) (search.value || trigger.value)?.focus({ preventScroll: true })
}
function close(restoreFocus = false) {
  open.value = false
  typeBuffer = ''; window.clearTimeout(typeTimer)
  window.removeEventListener('pointerdown', outside, true)
  window.removeEventListener('focusin', outside, true)
  window.removeEventListener('keydown', escape, true)
  window.removeEventListener('scroll', scroll, true)
  window.removeEventListener('resize', dismiss)
  window.removeEventListener('blur', dismiss)
  window.removeEventListener('pkb:select-open', otherSelect)
  if (restoreFocus) trigger.value?.focus({ preventScroll: true })
}
function dismiss() { close() }
function outside(event: Event) {
  if (event.target instanceof Node && !root.value?.contains(event.target) && !popup.value?.contains(event.target)) close()
}
function scroll(event: Event) {
  if (event.target instanceof Node && popup.value?.contains(event.target)) return
  // A click can arrive just before the browser reports its focus scroll. Do not
  // immediately dismiss a correctly placed menu when its anchor has not moved.
  const current = trigger.value?.getBoundingClientRect()
  if (event.target === window || !current || !anchorPosition || Math.abs(current.top - anchorPosition.top) > 1 || Math.abs(current.left - anchorPosition.left) > 1) close()
}
function otherSelect(event: Event) { if ((event as CustomEvent).detail !== id) close() }
function escape(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(true) }
}
async function choose(index: number) {
  sync()
  const item = filtered.value.find(option => option.index === index)
  if (!native || disabled.value || !item || item.disabled) return
  const changed = native.selectedIndex !== index
  close(true)
  if (!changed) return
  native.selectedIndex = index
  native.dispatchEvent(new Event('input', { bubbles: true }))
  native.dispatchEvent(new Event('change', { bubbles: true }))
  await nextTick()
  sync()
}
async function onKey(event: KeyboardEvent) {
  if (event.isComposing || event.ctrlKey || event.metaKey || disabled.value) return
  const typing = event.target === search.value
  if (event.key === 'Tab') { if (open.value) close(true); return }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp' || (!typing && ['Home', 'End'].includes(event.key))) {
    event.preventDefault(); event.stopPropagation()
    if (!open.value) { await show(); if (event.key.startsWith('Arrow')) return }
    const enabled = filtered.value.filter(item => !item.disabled)
    const current = enabled.findIndex(item => item.index === active.value)
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? enabled.length - 1
      : Math.max(0, Math.min(enabled.length - 1, current + (event.key === 'ArrowDown' ? 1 : -1)))
    active.value = enabled[next]?.index ?? -1
    await nextTick(); revealActive(); return
  }
  if (event.key === 'Enter' || (!typing && event.key === ' ')) {
    event.preventDefault(); event.stopPropagation()
    if (open.value) await choose(active.value); else await show()
    return
  }
  if (!typing && event.key.length === 1 && !event.altKey) {
    event.preventDefault()
    await show()
    if (searchable.value) { query.value += event.key; return }
    window.clearTimeout(typeTimer); typeBuffer += event.key.toLocaleLowerCase()
    const enabled = filtered.value.filter(item => !item.disabled)
    const match = enabled.find(item => item.text.toLocaleLowerCase().startsWith(typeBuffer))
    if (match) { active.value = match.index; await nextTick(); revealActive() }
    typeTimer = window.setTimeout(() => { typeBuffer = '' }, 700)
  }
}
function nativeFocus() { trigger.value?.focus({ preventScroll: true }) }
function nativeClick(event: Event) { event.preventDefault(); void show() }
function validate(event: Event) { event.preventDefault(); invalid.value = true; nativeFocus() }
function reset() { queueMicrotask(sync) }
onMounted(() => {
  native = root.value?.querySelector('select') || undefined
  if (!native) return
  native.setAttribute('tabindex', '-1'); native.setAttribute('aria-hidden', 'true')
  native.addEventListener('change', sync); native.addEventListener('focus', nativeFocus)
  native.addEventListener('click', nativeClick); native.addEventListener('invalid', validate)
  observer = new MutationObserver(sync)
  observer.observe(native, { attributes: true, childList: true, characterData: true, subtree: true })
  form = native.form; form?.addEventListener('reset', reset)
  sync(); ready.value = true
})
onUpdated(sync)
watch(query, async () => { resetActive(); await nextTick(); revealActive() })
watch(uiLanguage, async () => { await nextTick(); sync(); if (open.value) await place() })
onBeforeUnmount(() => {
  disposed = true; close(); observer?.disconnect(); form?.removeEventListener('reset', reset)
  native?.removeEventListener('change', sync); native?.removeEventListener('focus', nativeFocus)
  native?.removeEventListener('click', nativeClick); native?.removeEventListener('invalid', validate)
})
</script>

<template>
  <span ref="root" class="select-field" :class="{ 'select-field--ready': ready, 'select-field--wide': wide, 'select-field--compact': compact }">
    <slot />
    <button v-if="ready" ref="trigger" type="button" class="select-field__trigger" role="combobox"
      :disabled="disabled" :aria-label="labelledBy ? undefined : label" :aria-labelledby="labelledBy || undefined"
      :aria-describedby="invalid ? `${id}-error` : describedBy || undefined" :aria-expanded="open"
      :aria-controls="open ? `${id}-list` : undefined" aria-haspopup="listbox" :aria-activedescendant="activeId"
      :aria-required="required || undefined" :aria-invalid="invalid || undefined"
      @click="open ? close(true) : show()" @keydown="onKey">
      <span class="select-field__value">{{ selectedText }}</span>
      <svg class="select-field__chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m7 10 5 5 5-5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
    </button>
    <span v-if="invalid" :id="`${id}-error`" class="select-field__error" role="alert">{{ uiText('请选择一个选项') }}</span>
    <Teleport v-if="open" :to="target">
      <div ref="popup" class="select-menu" :style="position" @keydown="onKey" @click.stop>
        <div v-if="searchable" class="select-menu__search">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" stroke="currentColor" stroke-width="1.5" /><path d="m16 16 4 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" /></svg>
          <input ref="search" v-model="query" type="search" :placeholder="uiText('搜索选项…')" :aria-label="uiText('搜索选项')"
            :aria-controls="`${id}-list`" :aria-activedescendant="activeId" autocomplete="off" @keydown.esc.prevent.stop="close(true)" />
        </div>
        <div :id="`${id}-list`" ref="list" class="select-menu__list" role="listbox" :aria-label="label" :aria-labelledby="labelledBy || undefined">
          <template v-for="(item, index) in filtered" :key="item.index">
            <div v-if="item.group && item.group !== filtered[index - 1]?.group" class="select-menu__group" role="presentation">{{ item.group }}</div>
            <div :id="`${id}-${item.index}`" class="select-menu__option" :class="{ 'is-active': active === item.index }"
              role="option" :aria-selected="selected === item.index" :aria-disabled="item.disabled || undefined"
              @pointermove="!item.disabled && (active = item.index)" @mousedown.prevent @click.stop="choose(item.index)">
              <span>{{ item.text || uiText('请选择') }}</span>
              <svg v-if="selected === item.index" width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m5 12 4 4L19 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </div>
          </template>
        </div>
        <p v-if="!filtered.length" class="select-menu__empty" role="status">{{ uiText('没有匹配的选项') }}</p>
        <div class="select-menu__footer" aria-hidden="true"><span>{{ uiText('↑ ↓ 选择') }}</span><span>{{ uiText('Enter 确认') }}</span></div>
      </div>
    </Teleport>
  </span>
</template>

<style>
.select-field{position:relative;display:inline-grid;vertical-align:middle;max-width:100%;min-width:0;flex-shrink:1;text-align:start}
.select-field--wide{width:100%}
.select-field.select-field--ready>select{position:absolute!important;width:1px!important;height:1px!important;min-width:0!important;min-height:0!important;max-width:1px!important;margin:0!important;padding:0!important;border:0!important;clip-path:inset(50%)!important;overflow:hidden!important;white-space:nowrap!important;pointer-events:none!important}
.select-field .select-field__trigger{box-sizing:border-box;display:flex;align-items:center;justify-content:space-between;gap:22px;width:100%;min-width:0;min-height:42px;margin:0;padding:10px 14px;border:1px solid var(--line);border-radius:10px;background:var(--paper);color:var(--ink);font-family:inherit;font-size:14px;line-height:1.5;font-weight:400;letter-spacing:normal;text-align:start;box-shadow:0 1px 2px rgb(0 0 0 / .02);cursor:pointer;transition:border-color .18s,box-shadow .18s,background .18s;transform:none;text-decoration:none}
.select-field .select-field__trigger:hover:not(:disabled){border-color:color-mix(in srgb,var(--green) 50%,var(--line));background:var(--hover-paper,var(--paper))}
.select-field .select-field__trigger:focus-visible,.select-field .select-field__trigger[aria-expanded=true]{border-color:var(--green);outline:none;box-shadow:0 0 0 3px color-mix(in srgb,var(--green) 12%,transparent)}
.select-field .select-field__trigger:disabled{opacity:.5;cursor:not-allowed;box-shadow:none}
.select-field .select-field__value{display:block;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.select-field .select-field__chevron{flex:none;color:var(--muted);transition:rotate .18s}
.select-field [aria-expanded=true] .select-field__chevron{rotate:180deg;color:var(--green)}
.select-field--compact .select-field__trigger{min-height:34px;padding:6px 10px;font-size:12px;gap:12px}
.select-field__error{display:block;padding-top:6px;font-size:12px;color:var(--red,#a74343)}
.select-menu{position:fixed;z-index:10010;box-sizing:border-box;display:flex;flex-direction:column;width:240px;max-height:360px;padding:6px;border:1px solid var(--line);border-radius:14px;background:var(--paper);color:var(--ink);box-shadow:0 16px 50px rgb(0 0 0 / .12),0 3px 10px rgb(0 0 0 / .04);font-family:inherit;font-size:14px;font-weight:400;line-height:1.5;letter-spacing:normal;text-align:start;isolation:isolate;animation:select-menu-appear .14s ease-out}
.select-menu__list{overflow:auto;min-height:0;overscroll-behavior:contain;scrollbar-width:thin;scrollbar-color:var(--line) transparent;scroll-padding:6px}
.select-menu .select-menu__option{display:flex;align-items:center;justify-content:space-between;gap:20px;min-height:40px;box-sizing:border-box;margin:2px 0;padding:10px 12px;border-radius:8px;color:var(--ink);font:inherit;cursor:pointer;overflow-wrap:anywhere;transition:background .1s,color .1s}
.select-menu__option>svg{flex:none;color:var(--green)}
.select-menu .select-menu__option[aria-selected=true]{color:var(--green);font-weight:550}
.select-menu .select-menu__option.is-active:not([aria-disabled=true]){background:var(--green-soft);color:var(--green)}
.select-menu .select-menu__option[aria-disabled=true]{opacity:.4;cursor:not-allowed}
.select-menu .select-menu__group{margin:8px 12px 4px;color:var(--muted);font-size:11px;font-weight:600;letter-spacing:.04em}
.select-menu .select-menu__search{display:flex;align-items:center;gap:10px;margin:2px 6px 7px;padding:5px 5px 9px;border-bottom:1px solid var(--line-soft);color:var(--muted)}
.select-menu__search>svg{flex:none}
.select-menu .select-menu__search input{box-sizing:border-box;min-width:0;width:100%;min-height:30px;margin:0;padding:4px 0;border:0;border-radius:0;outline:0;background:transparent;color:var(--ink);font:inherit;font-size:13px;box-shadow:none}
.select-menu__search input::placeholder{color:var(--muted)}
.select-menu .select-menu__empty{margin:16px 12px;color:var(--muted);font-size:13px}
.select-menu .select-menu__footer{display:flex;gap:16px;flex:none;justify-content:flex-end;margin:5px 6px 0;padding:9px 5px 3px;border-top:1px solid var(--line-soft);font-size:10px;color:var(--muted);user-select:none}
@keyframes select-menu-appear{from{opacity:0}to{opacity:1}}
[data-motion=off] .select-menu{animation:none}
[data-motion=off] :is(.select-field__trigger,.select-field__chevron,.select-menu__option){transition:none}
@media(prefers-reduced-motion:reduce){.select-menu{animation:none}.select-field .select-field__trigger,.select-field .select-field__chevron,.select-menu .select-menu__option{transition:none}}
@media(forced-colors:active){.select-menu .select-menu__option.is-active{outline:1px solid Highlight}.select-menu__option[aria-selected=true]{border-inline-start:2px solid Highlight}}
</style>
