<script setup lang="ts">
import { computed } from 'vue'
import { motionEnabled, motionState, toggleMotion } from '@/lib/motion'
const label = computed(() => motionState.reduced ? '系统已开启减少动态效果' : motionEnabled.value ? '暂停动态效果' : '开启动态效果')
</script>
<template>
  <button class="motion-toggle" type="button" :aria-label="label" :title="label" :aria-pressed="motionEnabled" :disabled="motionState.reduced" @click="toggleMotion">
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M3 7c3-4 6 4 9 0s6 4 9 0M3 12c3-4 6 4 9 0s6 4 9 0M3 17c3-4 6 4 9 0s6 4 9 0" stroke="currentColor" stroke-width="1.25" stroke-linecap="round" /></svg>
  </button>
</template>
<style scoped>
.motion-toggle{display:inline-grid;place-items:center;width:32px;height:32px;flex-shrink:0;padding:7px;border:1px solid transparent;border-radius:6px;background:transparent;color:var(--muted);cursor:pointer}
.motion-toggle svg{width:18px;height:18px}.motion-toggle:hover:not(:disabled){background:var(--green-soft);color:var(--green)}.motion-toggle[aria-pressed=false]{opacity:.6}.motion-toggle:disabled{cursor:default}
</style>
