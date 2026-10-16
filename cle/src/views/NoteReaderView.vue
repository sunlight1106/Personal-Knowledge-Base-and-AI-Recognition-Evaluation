<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api/client'
import { uiText, uiLocale } from '@/i18n'
import { authStore } from '@/stores/auth'
import { toastStore } from '@/stores/toast'
import NotePreview from '@/components/NotePreview.vue'
import SelectField from '@/components/SelectField.vue'
import type { NoteView, NoteExportFormat } from '@/types/api'
const route = useRoute(), note = ref<NoteView | null>(null), loading = ref(false), error = ref(''), exporting = ref(false), format = ref<NoteExportFormat>('md')
let active = true, version = 0
onBeforeUnmount(() => { active = false; version++ })
async function load() {
  const current = ++version; note.value = null; loading.value = true; error.value = ''
  try { const value = await api.note(String(route.params.id)); if (active && current === version) note.value = value }
  catch (e) { if (active && current === version) error.value = (e as Error).message }
  finally { if (active && current === version) loading.value = false }
}
async function download() {
  if (!note.value || exporting.value) return
  exporting.value = true
  try { await api.exportNote(note.value.id, format.value, `${note.value.title}.${format.value}`) }
  catch (e) { if (active) toastStore.error((e as Error).message) }
  finally { if (active) exporting.value = false }
}
watch(() => route.params.id, load, { immediate: true })
</script>
<template><div class="page-stack note-reader-page"><RouterLink class="table-action" to="/app/notes">← {{ uiText('返回笔记列表') }}</RouterLink><p v-if="error" role="alert" class="inline-alert inline-alert--error">{{ uiText(error) }} <button class="table-action" @click="load">{{ uiText('重试') }}</button></p><p v-if="loading" role="status">{{ uiText('正在加载…') }}</p><template v-else-if="note"><header class="page-intro"><p class="page-kicker">{{ note.library }} / {{ uiText('阅读模式') }}</p><h2>{{ note.title }}</h2><p>{{ uiText('更新于') }} {{ new Date(note.updatedAt).toLocaleString(uiLocale()) }}</p></header><div class="reader-actions"><RouterLink v-if="authStore.has('note:write')" class="button button--dark" :to="{ name: 'note-edit', params: { id: note.id } }">{{ uiText('编辑笔记') }}</RouterLink><label>{{ uiText('导出格式') }}<SelectField><select v-model="format" class="field-input"><option value="md">Markdown</option><option value="pdf">PDF</option><option value="docx">Word</option><option value="html">HTML</option><option value="txt">TXT</option></select></SelectField></label><button class="button button--ghost" :disabled="exporting" @click="download">{{ exporting ? uiText('正在导出…') : uiText('导出笔记') }}</button></div><NotePreview :key="note.id" :body="note.body" :format="note.contentFormat" :note-id="note.id" /></template></div></template>
<style scoped>.note-reader-page{max-width:1100px;margin:0 auto}.reader-actions{display:flex;align-items:end;gap:14px;padding-bottom:24px;border-bottom:1px solid var(--line)}.reader-actions label{display:grid;gap:8px;font-size:12px;color:var(--muted)}.reader-actions .select-field{min-width:150px}</style>
