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
      <g class="water-rest"><ellipse v-for="n in 3" :key="n" cx="1050" cy="210" :rx="110+n*100" :ry="40+n*36" /></g>
      <g class="water-rings"><ellipse v-for="n in 5" :key="n" cx="1050" cy="210" rx="450" ry="165" :style="{ animationDelay: `${(n-1)*.32}s` }" /></g>
      <ellipse class="water-impact" cx="1050" cy="210" rx="30" ry="9" />
      <path class="water-drop" d="M1050 189 C1048 196 1044 200 1044 204 A6 6 0 0 0 1056 204 C1056 200 1052 196 1050 189Z" fill="currentColor" />
      <g class="water-splash"><path d="M1039 208 Q1029 187 1018 193 M1061 208 Q1071 187 1082 193" /><circle cx="1020" cy="189" r="2" fill="currentColor" /><circle cx="1080" cy="189" r="2" fill="currentColor" /></g>
    </svg>
  </div>
</template>
<style scoped>
.ambient-waves{position:absolute;inset:0;z-index:-1;overflow:hidden;border-radius:inherit;pointer-events:none;color:var(--wave-ink,var(--green));opacity:.5;contain:paint;mask-image:linear-gradient(to right,transparent,#000 12%,#000 90%,transparent)}
.ambient-waves svg{display:block;width:100%;height:100%;overflow:hidden}.ambient-waves path,.water-rings ellipse,.water-contours ellipse{stroke:currentColor;stroke-width:1;vector-effect:non-scaling-stroke}
.wave-flow{animation:wave-drift 20s linear infinite;opacity:.62}.wave-flow--second{opacity:.3;animation-duration:29s;animation-direction:reverse}
.water-rings ellipse{transform-origin:1050px 210px;animation:water-spread 9s cubic-bezier(.12,.5,.32,1) infinite;stroke-width:1.25;opacity:0}
.water-rest{opacity:0;stroke:currentColor;stroke-width:1}.water-impact{transform-origin:1050px 210px;animation:water-impact 9s ease-out infinite;fill:currentColor;opacity:0}
.water-drop{animation:water-drop 9s cubic-bezier(.55,.06,.85,.45) infinite;transform-origin:1050px 210px;opacity:0}
.water-splash{animation:water-splash 9s ease-out infinite;transform-origin:1050px 210px;opacity:0;stroke:currentColor;stroke-width:1.2}
.water-light{animation:water-light 9s ease-in-out infinite alternate;transform-origin:1040px 210px}
[data-running=false] :is(.wave-flow,.water-rings ellipse,.water-light,.water-drop,.water-splash,.water-impact){animation-play-state:paused}
@keyframes wave-drift{from{transform:translateX(0)}to{transform:translateX(-720px)}}
@keyframes water-spread{0%,10%{transform:scale(.025);opacity:0}12%{opacity:.8}45%{opacity:.38}88%,100%{transform:scale(1.3);opacity:0}}
@keyframes water-drop{0%{transform:translateY(-150px) scale(.8,1.3);opacity:0}3%{opacity:.85}10%{transform:translateY(0) scale(1);opacity:.9}12%,100%{transform:scale(1.6,.15);opacity:0}}
@keyframes water-impact{0%,9%{transform:scale(.25);opacity:0}12%{opacity:.2}24%,100%{transform:scale(3);opacity:0}}
@keyframes water-splash{0%,10%{transform:scale(.2);opacity:0}12%{opacity:.6}20%,100%{transform:scale(1.3) translateY(5px);opacity:0}}
@keyframes water-light{from{opacity:.4;transform:translateX(-24px)}to{opacity:.85;transform:translateX(24px)}}
@media(prefers-reduced-motion:reduce){html:not([data-motion=on]) .ambient-waves :is(.wave-flow,.water-rings ellipse,.water-light,.water-drop,.water-splash,.water-impact){animation:none}html:not([data-motion=on]) .water-rest{opacity:.2}html:not([data-motion=on]) .water-rings{display:none}}
[data-motion=off] .ambient-waves :is(.wave-flow,.water-rings ellipse,.water-light,.water-drop,.water-splash,.water-impact){animation:none}
[data-motion=off] .water-rest{opacity:.2}
[data-motion=off] .water-rings{display:none}
</style>
