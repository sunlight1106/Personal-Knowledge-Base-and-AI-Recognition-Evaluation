<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { motionEnabled, motionState } from '@/lib/motion'
const root = ref<HTMLElement | null>(null), inView = ref(false)
const running = computed(() => motionEnabled.value && motionState.visible && inView.value)
let observer: IntersectionObserver | undefined
onMounted(() => {
  if (!('IntersectionObserver' in window)) { inView.value = true; return }
  observer = new IntersectionObserver(entries => { inView.value = entries.some(entry => entry.isIntersecting) })
  if (root.value) observer.observe(root.value)
})
onBeforeUnmount(() => observer?.disconnect())
const wave = (y: number, amplitude: number) => `M-720 ${y} Q-540 ${y-amplitude} -360 ${y} T0 ${y} T360 ${y} T720 ${y} T1080 ${y} T1440 ${y} T1800 ${y} T2160 ${y}`
</script>
<template>
  <div ref="root" class="ambient-waves" :data-running="running" aria-hidden="true">
    <svg viewBox="0 0 1440 360" preserveAspectRatio="none" fill="none" focusable="false">
      <g class="wave-flow wave-flow--first"><path v-for="n in 5" :key="n" :d="wave(210+n*16, 52)" /></g>
      <g class="wave-flow wave-flow--second"><path v-for="n in 4" :key="n" :d="wave(255+n*14, 34)" /></g>
      <g class="water-rings"><ellipse v-for="n in 3" :key="n" cx="1110" cy="225" rx="210" ry="92" :style="{ animationDelay: `${(n-1)*-6}s` }" /></g>
    </svg>
  </div>
</template>
<style scoped>
.ambient-waves{position:absolute;inset:0;z-index:-1;overflow:hidden;pointer-events:none;color:var(--wave-ink,var(--green));opacity:.25;contain:paint;mask-image:linear-gradient(to right,transparent,#000 8%,#000 92%,transparent)}
.ambient-waves svg{display:block;width:100%;height:100%;overflow:hidden}.ambient-waves path,.ambient-waves ellipse{stroke:currentColor;stroke-width:.8;vector-effect:non-scaling-stroke}
.wave-flow{transform-box:view-box;animation:wave-drift 32s linear infinite}.wave-flow--second{opacity:.5;animation-duration:43s;animation-direction:reverse}.water-rings ellipse{transform-origin:1110px 225px;animation:water-spread 18s ease-out infinite}
[data-running=false] :is(.wave-flow,.water-rings ellipse){animation-play-state:paused}
@keyframes wave-drift{from{transform:translateX(0)}to{transform:translateX(-50%)}}
@keyframes water-spread{0%{transform:scale(.45);opacity:0}18%{opacity:.6}100%{transform:scale(1.6);opacity:0}}
@media(prefers-reduced-motion:reduce){.wave-flow,.water-rings ellipse{animation:none}.water-rings{display:none}}
:global([data-motion=off]) .wave-flow,:global([data-motion=off]) .water-rings ellipse{animation-play-state:paused}
</style>
