<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import DialogPanel from './DialogPanel.vue'
import { releaseApi, type ReleaseNotice } from '@/api/releases'
import { uiLanguage } from '@/i18n'
defineProps<{ busy: boolean; error: string }>()
const emit = defineEmits<{ close: []; read: [version: string] }>()
const notice = ref<ReleaseNotice>(), loading = ref(false), loadError = ref('')
const controller = new AbortController()
const t = (zh: string, en: string) => uiLanguage.value === 'en' ? en : zh
const groups = [{ key: 'added', zh: '新增功能', en: 'New features' }, { key: 'improved', zh: '体验优化', en: 'Improvements' }, { key: 'fixed', zh: '问题修复', en: 'Fixes' }, { key: 'notes', zh: '更新后需要注意', en: 'After updating' }] as const
async function load() {
  if (loading.value) return
  loading.value = true; loadError.value = ''
  try { const value = await releaseApi.current(controller.signal); if (!controller.signal.aborted) notice.value = value }
  catch { if (!controller.signal.aborted) loadError.value = t('更新说明加载失败，请重试。', 'Could not load release notes. Please retry.') }
  finally { if (!controller.signal.aborted) loading.value = false }
}
onMounted(load)
onBeforeUnmount(() => controller.abort())
</script>
<template>
  <DialogPanel :title="t('更新公告', 'Release announcement')" :busy="busy" @close="emit('close')">
    <p v-if="loading" role="status">{{ t('正在加载更新说明…', 'Loading release notes…') }}</p>
    <div v-if="loadError" role="alert"><p>{{ loadError }}</p><button class="button" @click="load">{{ t('重试', 'Retry') }}</button></div>
    <article v-if="notice" class="release-announcement-detail">
      <div class="release-stamp"><strong>v{{ notice.release.version }}</strong><time>{{ notice.release.date }}</time></div>
      <h4>{{ notice.release.title[uiLanguage] }}</h4>
      <template v-for="group in groups" :key="group.key">
        <section v-if="notice.release[group.key].length"><h5>{{ t(group.zh, group.en) }}</h5><ul><li v-for="item in notice.release[group.key]" :key="item.zh">{{ item[uiLanguage] }}</li></ul></section>
      </template>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <footer><RouterLink to="/updates" @click="emit('close')">{{ t('查看全部版本记录', 'All version history') }} ↗</RouterLink><button class="button button--dark" :disabled="busy || notice.read" @click="emit('read', notice.release.version)">{{ busy ? t('正在保存…', 'Saving…') : t('已读', 'Mark as read') }}</button></footer>
    </article>
  </DialogPanel>
</template>
<style scoped>
.release-stamp{display:flex;align-items:center;gap:14px;color:var(--muted);font-size:13px}.release-stamp strong{font-size:26px;color:var(--green);font-weight:500}.release-announcement-detail h4{font-size:21px;line-height:1.6;margin:20px 0 26px;font-weight:550}.release-announcement-detail section+section{margin-top:24px}.release-announcement-detail h5{font-size:14px;color:var(--green);margin:0 0 9px}.release-announcement-detail ul{padding-left:20px;margin:0;font-size:14px;line-height:1.9;color:var(--ink-soft)}.release-announcement-detail li+li{margin-top:10px}.release-announcement-detail footer{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap;border-top:1px solid var(--line);padding-top:22px;margin-top:28px}.release-announcement-detail footer a{font-size:13px;color:var(--green);text-decoration:none}.form-error{color:var(--danger,#a13131)}
</style>
