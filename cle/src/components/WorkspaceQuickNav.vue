<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { uiText } from '@/i18n'
import type { NavItem } from '@/lib/navigation'
import AppIcon from './AppIcon.vue'

const props = defineProps<{ items: NavItem[] }>()
const emit = defineEmits<{ open: [] }>()
const route = useRoute()
const root = ref<HTMLElement | null>(null), measure = ref<HTMLElement | null>(null)
const overflow = ref<HTMLDetailsElement | null>(null)
const visibleCount = ref(0)
const visibleItems = computed(() => props.items.slice(0, visibleCount.value))
const hiddenItems = computed(() => props.items.slice(visibleCount.value))
const current = (item: NavItem) => route.path === item.to || route.path.startsWith(item.to + '/')
const hiddenCurrent = computed(() => hiddenItems.value.some(current))
let observer: ResizeObserver | undefined
let active = false

function close() { overflow.value?.removeAttribute('open') }
function escape() { close(); overflow.value?.querySelector('summary')?.focus() }
function outside(event: PointerEvent) {
  if (overflow.value?.open && event.target instanceof Node && !overflow.value.contains(event.target)) close()
}
function fit() {
  if (!active || !root.value || !measure.value) return
  const widths = [...measure.value.querySelectorAll<HTMLElement>('.quick-measure-item')].map(item => item.getBoundingClientRect().width)
  const width = root.value.getBoundingClientRect().width
  const gap = parseFloat(window.getComputedStyle(root.value).columnGap) || 6
  const total = widths.reduce((sum, item) => sum + item, 0) + gap * Math.max(0, widths.length - 1)
  if (total <= width) { visibleCount.value = props.items.length; return }
  const more = measure.value.querySelector<HTMLElement>('.quick-more-measure')?.getBoundingClientRect().width || 90
  let used = more, count = 0
  for (const item of widths) {
    if (used + gap + item > width) break
    used += gap + item; count++
  }
  visibleCount.value = count
}
watch(() => [route.path, props.items.map(item => `${item.to}:${uiText(item.label)}`).join('|')], async () => {
  close(); await nextTick(); fit()
}, { flush: 'post' })
onMounted(() => {
  active = true
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(fit)
    if (root.value) observer.observe(root.value)
    if (measure.value) observer.observe(measure.value)
  }
  window.addEventListener('resize', fit)
  document.addEventListener('pointerdown', outside)
  void nextTick(fit)
})
onBeforeUnmount(() => {
  active = false; observer?.disconnect()
  window.removeEventListener('resize', fit)
  document.removeEventListener('pointerdown', outside)
})
defineExpose({ close })
</script>

<template>
  <nav ref="root" class="workspace-quick workspace-quick--adaptive" :aria-label="uiText('常用页面')">
    <RouterLink v-for="item in visibleItems" :key="item.to" class="quick-link" :to="item.to"><AppIcon :name="item.icon" :size="15" /><span>{{ uiText(item.label) }}</span></RouterLink>
    <details v-if="hiddenItems.length" ref="overflow" class="quick-overflow" @toggle="overflow?.open && emit('open')" @keydown.esc.prevent.stop="escape">
      <summary :class="{'has-current':hiddenCurrent}" :aria-label="uiText('更多常用页面')"><AppIcon name="menu" :size="15" /><span>{{ uiText('更多') }}</span><small>{{ hiddenItems.length }}</small></summary>
      <div class="quick-overflow-list">
        <p>{{ uiText('已固定的页面') }}</p>
        <RouterLink v-for="item in hiddenItems" :key="item.to" :to="item.to" @click="close"><AppIcon :name="item.icon" :size="15" /><span>{{ uiText(item.label) }}</span><AppIcon v-if="current(item)" name="check" :size="14" /></RouterLink>
      </div>
    </details>
    <span v-if="!items.length" class="workspace-quick-hint">{{ uiText('在「目录与账户」中固定常用页面') }}</span>
    <div ref="measure" class="quick-measure" aria-hidden="true" inert>
      <span v-for="item in items" :key="item.to" class="quick-measure-item" :class="{'is-current':current(item)}"><AppIcon :name="item.icon" :size="15" /><span>{{ uiText(item.label) }}</span></span>
      <span class="quick-more-measure"><AppIcon name="menu" :size="15" /><span>{{ uiText('更多') }}</span><small>{{ items.length }}</small></span>
    </div>
  </nav>
</template>

<style scoped>
.workspace-quick.workspace-quick--adaptive{position:relative;flex-wrap:nowrap;align-items:center;min-width:0;min-height:40px}
.quick-link,.quick-measure-item{flex-shrink:0;white-space:nowrap}
.quick-measure{position:absolute;left:0;top:0;display:flex;width:max-content;max-width:100%;height:0;overflow:hidden;gap:6px;visibility:hidden;pointer-events:none;contain:layout style}
.quick-measure-item,.quick-overflow>summary,.quick-more-measure{display:flex;flex-shrink:0;align-items:center;gap:8px;border:1px solid transparent;border-radius:7px;padding:9px 13px;line-height:inherit;font-size:inherit;white-space:nowrap}
.quick-measure-item.is-current{font-weight:600}
.quick-overflow{position:relative;flex-shrink:0;margin-left:auto}
.quick-overflow>summary{cursor:pointer;list-style:none;color:var(--ink-soft);user-select:none}
.quick-overflow>summary::-webkit-details-marker{display:none}
.quick-overflow>summary:hover,.quick-overflow[open]>summary{background:var(--hover-paper);color:var(--green);border-color:var(--line)}
.quick-overflow>summary.has-current{background:var(--green-soft);color:var(--green)}
.quick-overflow small,.quick-more-measure small{font-size:10px;min-width:17px;text-align:center;border-radius:5px;background:var(--green-soft);color:var(--green);padding:1px 4px;font-variant-numeric:tabular-nums}
.quick-overflow-list{position:absolute;right:0;top:calc(100% + 10px);z-index:85;min-width:220px;max-width:calc(100vw - 64px);max-height:min(440px,60vh);overflow:auto;padding:10px;border:1px solid var(--line);border-radius:14px;background:var(--paper);box-shadow:var(--shadow-raised)}
.quick-overflow-list>p{margin:3px 10px 8px;font-size:11px;color:var(--muted)}
.workspace-quick .quick-overflow-list a{padding:10px;white-space:nowrap;gap:10px}
.quick-overflow-list a>span{flex:1}
.quick-overflow>summary:focus-visible{outline:2px solid var(--green);outline-offset:2px}
</style>
