<script setup lang="ts">
import RequestFailure from '@/components/RequestFailure.vue'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { uiText, uiLocale } from '@/i18n'
import { researchApi } from '@/api/research'
import { authStore } from '@/stores/auth'
import ActionConfirmDialog from '@/components/ActionConfirmDialog.vue'
import { toastStore } from '@/stores/toast'
const items = ref<Awaited<ReturnType<typeof researchApi.trash>>>([]), error = ref(''), busy = ref(false), loading = ref(true), page = ref(0)
const removal = ref<{ id: string; title: string } | null>(null)
let active = true, version = 0
onBeforeUnmount(() => { active = false; version++ })
async function load() {
  const current = ++version; loading.value = true; error.value = ''
  try {
    const rows = await researchApi.trash(page.value)
    if (!active || current !== version) return
    if (!rows.length && page.value) { page.value--; await load(); return }
    items.value = rows
  } catch (e) { if (active && current === version) error.value = (e as Error).message }
  finally { if (active && current === version) loading.value = false }
}
async function change(id: string, permanent = false) {
  if (busy.value || !authStore.has('note:write')) return
  busy.value = true; error.value = ''
  try {
    if (permanent) await researchApi.purge(id); else await researchApi.restore(id)
    if (active) { removal.value = null; toastStore.success(permanent ? '笔记已永久删除' : '笔记已恢复'); await load() }
  } catch (e) { if (active) error.value = (e as Error).message }
  finally { if (active) busy.value = false }
}
onMounted(load)
</script>
<template><section class="note-trash-panel" :aria-busy="loading || busy"><header><div><h2>{{ uiText('笔记回收站') }}</h2><p>{{ uiText('恢复后回到笔记列表顶层。旧分享链接保持撤销，避免恢复时意外扩大访问范围。') }}</p></div><button class="button button--ghost" :disabled="busy || loading" @click="load">{{ uiText('刷新') }}</button></header><RequestFailure v-if="!removal" :message="error" :retry="load" :disabled="!!busy" /><p v-if="loading" role="status">{{ uiText('正在加载…') }}</p><p v-else-if="!items.length && !error" class="research-empty">{{ uiText('回收站是空的。') }}</p><article v-for="item in items" :key="item.id" class="trash-row"><div><strong>{{ item.title }}</strong><small>{{ new Date(item.deletedAt).toLocaleString(uiLocale()) }} · {{ uiText('移入') }}</small></div><div v-if="authStore.has('note:write')" class="trash-actions"><button class="button button--ghost" :disabled="busy || loading" @click="change(item.id)">{{ uiText('恢复') }}</button><button class="table-action" :disabled="busy || loading" @click="error = ''; removal = item">{{ uiText('永久删除') }}</button></div></article><nav v-if="page || items.length >= 30" class="trash-actions" :aria-label="uiText('回收站分页')"><button class="button button--ghost" :disabled="!page || busy || loading" @click="page--; load()">{{ uiText('上一页') }}</button><span>{{ page + 1 }}</span><button class="button button--ghost" :disabled="items.length < 30 || busy || loading" @click="page++; load()">{{ uiText('下一页') }}</button></nav><ActionConfirmDialog v-if="removal" :title="uiText('永久删除笔记')" :description="removal.title + '\n' + uiText('永久删除这篇笔记及历史版本？此操作无法撤销。')" :confirm-label="uiText('永久删除')" :busy="busy" :error="error" @cancel="removal = null; error = ''" @confirm="change(removal.id, true)" /></section></template>
<style scoped>.note-trash-panel header{display:flex;align-items:start;justify-content:space-between;gap:24px}.note-trash-panel h2{font-size:24px;font-weight:550}.note-trash-panel p{font-size:14px;color:var(--muted);line-height:1.8}.trash-row{display:flex;justify-content:space-between;align-items:center;gap:24px;padding:22px 0;border-bottom:1px solid var(--line)}.trash-row small{display:block;color:var(--muted);font-size:12px;margin-top:8px}.trash-actions{display:flex;align-items:center;gap:16px}nav{margin-top:24px}</style>
