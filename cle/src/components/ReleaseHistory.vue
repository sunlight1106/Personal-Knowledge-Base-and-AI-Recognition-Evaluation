<script setup lang="ts">
import { uiLanguage } from '@/i18n'
import ledger from '@/data/releases.json'
const text = (value: { zh: string; en: string }) => value[uiLanguage.value]
const words = (zh: string, en: string) => uiLanguage.value === 'en' ? en : zh
const groups = [
  { key: 'added', zh: '新增', en: 'Added' },
  { key: 'fixed', zh: '修复', en: 'Fixed' },
  { key: 'improved', zh: '优化', en: 'Improved' },
  { key: 'notes', zh: '使用与更新说明', en: 'Update notes' },
] as const
const status = (value: string) => value === 'published' ? words('已发布', 'Published') : value === 'ready' ? words('待发布 · 已定版', 'Ready for release') : words('待发布', 'Not yet published')
const releaseUrl = (version: string) => `https://github.com/sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation/releases/tag/v${version}`
</script>
<template>
  <section class="release-history" :aria-label="words('版本与更新', 'Version history')">
    <header class="release-intro">
      <div><p class="page-kicker">RELEASE NOTES</p><h2>{{ words('每次更新，都有记录。', 'A record of every update.') }}</h2><p>{{ words('了解新增功能、修复与使用变化。', 'Explore new features, fixes and changes to your workflow.') }}</p></div>
      <div class="release-current"><small>{{ words('当前界面版本', 'Current interface version') }}</small><strong>v{{ ledger.current }}</strong><span>{{ status(ledger.releases[0]!.status) }}</span></div>
    </header>
    <div class="release-timeline">
      <article v-for="(release, index) in ledger.releases" :key="release.version" class="release-entry">
        <div class="release-meta"><span class="release-version">v{{ release.version }}</span><span class="release-status">{{ status(release.status) }}</span><time v-if="release.date" :datetime="release.date">{{ release.date }}</time></div>
        <details :open="index === 0">
          <summary><span>{{ text(release.title) }}</span><span class="release-expand" aria-hidden="true">+</span></summary>
          <div class="release-body">
            <template v-for="group in groups" :key="group.key"><section v-if="release[group.key].length"><h3>{{ words(group.zh, group.en) }}</h3><ul><li v-for="item in release[group.key]" :key="item.zh">{{ text(item) }}</li></ul></section></template>
            <a v-if="release.status === 'published'" :href="releaseUrl(release.version)" target="_blank" rel="noopener noreferrer">{{ words('查看发布文件', 'View release files') }} ↗</a>
          </div>
        </details>
      </article>
    </div>
  </section>
</template>
<style scoped>
.release-history{min-width:0}.release-intro{display:flex;justify-content:space-between;align-items:center;gap:28px;padding:12px 0 34px;border-bottom:1px solid var(--line)}.release-intro h2{font-size:clamp(26px,3vw,40px);font-weight:500;letter-spacing:-.04em;margin:16px 0}.release-intro p{color:var(--muted);line-height:1.8}.release-current{display:grid;gap:8px;flex-shrink:0;min-width:142px;padding:22px 26px;background:var(--green-soft);border:1px solid var(--line);border-radius:20px}.release-current strong{font:500 34px Georgia,serif;color:var(--green)}.release-current small,.release-current span{font-size:12px;color:var(--muted)}.release-entry{display:grid;grid-template-columns:145px minmax(0,1fr);gap:26px;padding:32px 0;border-bottom:1px solid var(--line)}.release-meta{display:flex;align-items:flex-start;flex-direction:column;gap:10px}.release-version{font:24px Georgia,serif;color:var(--ink)}.release-status{font-size:11px;padding:4px 9px;border:1px solid var(--line);border-radius:20px;color:var(--green)}.release-meta time{font-size:12px;color:var(--muted)}summary{display:flex;justify-content:space-between;gap:16px;cursor:pointer;list-style:none;font-size:20px;line-height:1.5;color:var(--ink)}summary::-webkit-details-marker{display:none}summary:focus-visible{outline:2px solid var(--green);outline-offset:6px;border-radius:4px}.release-expand{color:var(--muted);transition:transform .2s}details[open] .release-expand{transform:rotate(45deg)}.release-body{display:grid;gap:22px;padding-top:26px}.release-body h3{font-size:13px;font-weight:600;color:var(--green);margin:0 0 10px}.release-body ul{margin:0;padding-left:20px;color:var(--ink-soft);font-size:14px;line-height:1.9}.release-body li+li{margin-top:7px}.release-body a{justify-self:start;font-size:13px;color:var(--green);text-decoration:none;border-bottom:1px solid var(--line)}@media(max-width:650px){.release-intro{align-items:flex-start;flex-direction:column}.release-current{align-self:stretch;grid-template-columns:1fr auto;align-items:center}.release-current strong{grid-column:2;grid-row:1 / span 2}.release-entry{grid-template-columns:1fr;gap:17px}.release-meta{flex-direction:row;align-items:center;flex-wrap:wrap}}@media(prefers-reduced-motion:reduce){.release-expand{transition:none}}
</style>
