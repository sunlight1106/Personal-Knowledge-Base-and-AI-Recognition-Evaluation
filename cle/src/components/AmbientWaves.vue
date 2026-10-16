<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId } from 'vue'
import { motionEnabled, motionState } from '@/lib/motion'
withDefaults(defineProps<{ variant?: 'flow' | 'pool' }>(), { variant: 'flow' })
const glowId = useId()
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
  <div ref="root" class="ambient-waves" :class="`ambient-waves--${variant}`" :data-running="running" aria-hidden="true">
    <svg :viewBox="variant === 'pool' ? '450 -90 1200 600' : '0 0 1440 360'" :preserveAspectRatio="variant === 'pool' ? 'xMidYMid slice' : 'none'" fill="none" focusable="false">
      <defs><radialGradient :id="glowId"><stop stop-color="currentColor" stop-opacity=".2" /><stop offset=".55" stop-color="currentColor" stop-opacity=".06" /><stop offset="1" stop-color="currentColor" stop-opacity="0" /></radialGradient></defs>
      <ellipse class="water-light" cx="1040" cy="210" rx="410" ry="210" :fill="`url(#${glowId})`" />
      <template v-if="variant === 'flow'">
        <g class="wave-flow wave-flow--first"><path v-for="n in 7" :key="n" :d="wave(170+n*18, 62)" /></g>
        <g class="wave-flow wave-flow--second"><path v-for="n in 4" :key="n" :d="wave(220+n*18, 40)" /></g>
      </template>
      <g v-else class="water-contours"><ellipse v-for="n in 5" :key="n" cx="1050" cy="210" :rx="95+n*72" :ry="35+n*26" /></g>
      <g class="water-rings"><ellipse v-for="n in 4" :key="n" cx="1050" cy="210" rx="240" ry="100" :style="{ animationDelay: `${(n-1)*-3.5}s` }" /></g>
      <g v-if="variant === 'flow'" class="water-rings water-rings--echo"><ellipse v-for="n in 2" :key="n" cx="420" cy="280" rx="160" ry="65" :style="{ animationDelay: `${-4-(n-1)*7}s` }" /></g>
    </svg>
  </div>
</template>
<style scoped>
.ambient-waves{position:absolute;inset:0;z-index:-1;overflow:hidden;border-radius:inherit;pointer-events:none;color:var(--wave-ink,var(--green));opacity:.5;contain:paint;mask-image:linear-gradient(to right,transparent,#000 12%,#000 90%,transparent)}
.ambient-waves svg{display:block;width:100%;height:100%;overflow:hidden}.ambient-waves path,.water-rings ellipse,.water-contours ellipse{stroke:currentColor;stroke-width:1;vector-effect:non-scaling-stroke}
.wave-flow{transform-box:view-box;animation:wave-drift 27s linear infinite;opacity:.65}.wave-flow--second{opacity:.3;animation-duration:37s;animation-direction:reverse}.water-rings ellipse{transform-origin:1050px 210px;animation:water-spread 14s ease-out infinite;stroke-width:1.2}.water-rings--echo ellipse{transform-origin:420px 280px;opacity:.5}.water-contours{opacity:.22;transform-origin:1050px 210px;animation:water-breathe 16s ease-in-out infinite alternate}.water-light{animation:water-light 12s ease-in-out infinite alternate;transform-origin:1040px 210px}
[data-running=false] :is(.wave-flow,.water-rings ellipse,.water-contours,.water-light){animation-play-state:paused}
@keyframes wave-drift{from{transform:translateX(0)}to{transform:translateX(-50%)}}
@keyframes water-spread{0%{transform:scale(.35);opacity:0}16%{opacity:.8}70%{opacity:.22}100%{transform:scale(1.9);opacity:0}}
@keyframes water-breathe{from{transform:scale(.92) translateY(2px)}to{transform:scale(1.08) translateY(-4px)}}
@keyframes water-light{from{opacity:.4;transform:translateX(-24px)}to{opacity:.85;transform:translateX(24px)}}
@media(prefers-reduced-motion:reduce){.wave-flow,.water-rings ellipse,.water-contours,.water-light{animation:none}.water-rings{display:none}}
:global([data-motion=off]) :is(.wave-flow,.water-rings ellipse,.water-contours,.water-light){animation-play-state:paused}
</style>
