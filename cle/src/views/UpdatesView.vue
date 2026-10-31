<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { uiText } from '@/i18n'
import BrandMark from '@/components/BrandMark.vue'
import LanguageToggle from '@/components/LanguageToggle.vue'
import ReleaseHistory from '@/components/ReleaseHistory.vue'
</script>
<template>
  <div class="updates-page"><header class="updates-nav"><RouterLink to="/"><BrandMark />{{ uiText('个人知识库') }}</RouterLink><nav><RouterLink to="/docs">{{ uiText('使用文档') }}</RouterLink><LanguageToggle /><RouterLink to="/app/settings?section=about">{{ uiText('进入平台') }} →</RouterLink></nav></header><main><ReleaseHistory /></main></div>
</template>
<style scoped>
.updates-page{max-width:1100px;margin:auto;padding:0 32px 70px}.updates-nav{display:flex;justify-content:space-between;align-items:center;gap:24px;min-height:100px;border-bottom:1px solid var(--line);margin-bottom:48px}.updates-nav>a{display:flex;align-items:center;gap:12px}.updates-nav nav{display:flex;align-items:center;gap:22px}.updates-nav a{text-decoration:none;color:var(--ink);font-size:14px}@media(max-width:600px){.updates-page{padding:0 20px 40px}.updates-nav{flex-wrap:wrap;padding:20px 0;gap:12px}.updates-nav nav{gap:14px}}
</style>
