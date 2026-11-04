<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { uiLanguage } from '@/i18n'
import AmbientWaves from './AmbientWaves.vue'
const t = (zh: string, en: string) => uiLanguage.value === 'en' ? en : zh
defineProps<{ compact?: boolean }>()
</script>
<template>
  <section class="assessment-entry water-surface" :class="{ compact }" aria-labelledby="assessment-entry-title">
    <AmbientWaves variant="pool" class="surface-watermark" />
    <div><p class="entry-kicker">FIND YOUR STARTING POINT</p><h2 id="assessment-entry-title">{{ t('现在的词汇，能走多远？', 'Find where your words can take you.') }}</h2><p>{{ t('测一测词汇量，找到适合自己的下一本词书。', 'Explore your vocabulary and find your next word book.') }}</p><div class="entry-modes"><span>{{ t('勾选认识的词', 'Word recognition') }}</span><span>{{ t('英译汉', 'English → Chinese') }}</span><span>{{ t('汉译英', 'Chinese → English') }}</span></div></div>
    <RouterLink to="/vocabulary-test" class="entry-link">{{ t('测试词汇量', 'Test my vocabulary') }}<span aria-hidden="true">↗</span><small>{{ t('无需登录 · 约 3–10 分钟', 'No sign-in · About 3–10 minutes') }}</small></RouterLink>
  </section>
</template>
<style scoped>
.assessment-entry{position:relative;isolation:isolate;display:flex;align-items:center;justify-content:space-between;gap:30px;padding:32px 38px;border:1px solid var(--line);background:var(--surface-sheen);border-radius:18px;margin-block:24px 38px;overflow:hidden}.assessment-entry h2{font-size:clamp(22px,2.4vw,30px);font-weight:500;letter-spacing:-.04em;margin:9px 0}.assessment-entry p{font-size:14px;color:var(--ink-soft)}.assessment-entry .entry-kicker{font:10px/1.8 ui-monospace,monospace;letter-spacing:.14em;color:var(--green);margin:0}.entry-modes{display:flex;flex-wrap:wrap;gap:9px;margin-top:18px}.entry-modes span{padding:5px 10px;border:1px solid var(--line);border-radius:30px;font-size:11px;color:var(--ink-soft);background:var(--paper)}.entry-link{flex-shrink:0;text-decoration:none;color:var(--green);font-size:18px;font-weight:500;display:grid;grid-template-columns:1fr auto;column-gap:22px;padding:18px 22px;background:var(--paper);border:1px solid var(--line);border-radius:12px;transition:border-color .2s,transform .2s}.entry-link:hover{border-color:var(--green);transform:translateY(-2px)}.entry-link small{grid-column:1/-1;font-size:11px;font-weight:400;color:var(--muted);margin-top:8px}.compact{padding:24px}.compact h2{font-size:23px}@media(max-width:650px){.assessment-entry{align-items:stretch;flex-direction:column;padding:24px;gap:16px}.entry-link{font-size:16px}.entry-link small{margin-top:5px}}@media(prefers-reduced-motion:reduce){.entry-link{transition:none}.entry-link:hover{transform:none}}
</style>
