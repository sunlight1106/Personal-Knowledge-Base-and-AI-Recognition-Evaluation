<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { uiLanguage } from '@/i18n'
import { watchAppUpdate } from '@/lib/appUpdate'
const available = ref(false)
let stop = () => {}
const t = (zh:string, en:string) => uiLanguage.value === 'en' ? en : zh
onMounted(() => {
  const entry = document.querySelector<HTMLScriptElement>('script[type="module"][src^="/assets/"]')?.getAttribute('src') || ''
  stop = watchAppUpdate(entry, () => { available.value = true })
})
onBeforeUnmount(() => stop())
function reload() { window.location.reload() }
</script>
<template>
  <aside v-if="available" class="app-update-notice" role="status" aria-live="polite">
    <div><strong>{{ t('新版已就绪', 'An update is ready') }}</strong><p>{{ t('请先保存编辑或完成答题，再刷新使用新版。', 'Save your edits or finish your test before refreshing.') }}</p></div>
    <button class="button button--dark" @click="reload">{{ t('刷新使用新版', 'Refresh to update') }}</button>
    <button class="button" @click="available = false">{{ t('稍后', 'Later') }}</button>
  </aside>
</template>
<style scoped>
.app-update-notice{position:fixed;bottom:24px;left:50%;transform:translateX(-50%);z-index:100;display:flex;align-items:center;gap:16px;width:max-content;max-width:calc(100vw - 32px);padding:18px 22px;box-sizing:border-box;border:1px solid var(--line,#dce4dd);border-radius:18px;background:var(--paper,#fff);color:var(--ink,#203c31);box-shadow:0 12px 36px #17342624}
.app-update-notice p{margin:5px 0 0;font-size:13px;line-height:1.6}.app-update-notice button{flex-shrink:0}
@media(max-width:640px){.app-update-notice{flex-wrap:wrap;bottom:12px;gap:10px}.app-update-notice>div{width:100%}}
</style>
