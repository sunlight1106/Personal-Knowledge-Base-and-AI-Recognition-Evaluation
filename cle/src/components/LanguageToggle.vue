<script setup lang="ts">
import { setUiLanguage, uiLanguage, languageLoadError } from '@/i18n'
</script>

<template>
  <button type="button" class="language-toggle" data-language-toggle
    :aria-label="uiLanguage === 'zh' ? 'Switch to English' : '切换为中文'"
    :title="languageLoadError ? '语言加载失败，请重试 / Could not load language. Retry.' : uiLanguage === 'zh' ? 'Switch to English' : '切换为中文'"
    @click="setUiLanguage(uiLanguage === 'zh' ? 'en' : 'zh')">
    <span aria-hidden="true">{{ uiLanguage === 'zh' ? 'EN' : '中文' }}</span>
  </button>
  <span v-if="languageLoadError" role="alert" class="language-error">语言加载失败，请重试 / Could not load language. Retry.</span>
</template>

<style scoped>
.language-toggle{display:inline-flex;align-items:center;justify-content:center;min-width:40px;min-height:36px;padding:5px 9px;border:1px solid var(--line,#dce4e2);border-radius:9px;background:var(--paper,#fff);color:var(--ink-strong,#243d42);font:500 12px/1.4 system-ui,sans-serif;letter-spacing:.02em;cursor:pointer;flex-shrink:0;transition:background .16s,border-color .16s}
.language-toggle:hover{background:var(--green-soft,#edf3ef);border-color:var(--green,#476f64)}
.language-toggle:focus-visible{outline:2px solid var(--green,#476f64);outline-offset:3px}
.language-error{font:12px/1.5 system-ui,sans-serif;color:var(--ink-strong);max-width:220px}
@media(prefers-reduced-motion:reduce){.language-toggle{transition:none}}
</style>
