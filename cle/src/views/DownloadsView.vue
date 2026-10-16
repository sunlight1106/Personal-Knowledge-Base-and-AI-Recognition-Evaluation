<script setup lang="ts">
import { uiText, uiLocale } from '@/i18n'

import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api, ApiClientError } from '@/api/client'
import type { FileView, InferenceView } from '@/types/api'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppIcon from '@/components/AppIcon.vue'
import { toastStore } from '@/stores/toast'
import { authStore } from '@/stores/auth'

const files = ref<FileView[]>([])
const tasks = ref<InferenceView[]>([])
const tab = ref<'files' | 'reports'>('files')
const loading = ref(true)
const error = ref(''), query = ref(''), downloading = ref(new Set<string>())
let active = true, version = 0
onBeforeUnmount(() => { active = false; version++ })
const matchingFiles = computed(() => files.value.filter(f => `${f.originalName} ${f.ownerName}`.toLowerCase().includes(query.value.trim().toLowerCase())))
const matchingTasks = computed(() => tasks.value.filter(t => `${t.inputFile.originalName} ${t.model.name} ${t.traceId}`.toLowerCase().includes(query.value.trim().toLowerCase())))
const taskLabel = (type: string) => uiText(({ LICENSE_PLATE: '车牌识别报告', RECEIPT: '票据识别报告', OCR: '票据识别报告', VIDEO_ANALYSIS: '视频分析报告', IMAGE: '图片理解报告', IMAGE_UNDERSTANDING: '图片理解报告' } as Record<string,string>)[type] || '实验报告')
const canReadFiles = computed(() => authStore.hasAny('file:read', 'file:read:any'))
const canReadReports = computed(() => authStore.hasAny('experiment:read', 'experiment:read:any'))
const canDownloadReports = computed(() => authStore.has('report:download'))

async function load() {
  const current = ++version; loading.value = true; error.value = ''
  try {
    if (!canReadFiles.value && canReadReports.value) tab.value = 'reports'
    const [fileList, taskList] = await Promise.all([
      canReadFiles.value ? api.files() : Promise.resolve([] as FileView[]),
      canReadReports.value ? api.tasks() : Promise.resolve([] as InferenceView[]),
    ])
    if (!active || current !== version) return
    files.value = fileList
    tasks.value = taskList
  } catch (reason) {
    if (active && current === version) error.value = reason instanceof ApiClientError ? reason.message : '下载列表加载失败'
  } finally { if (active && current === version) loading.value = false }
}
onMounted(load)

async function download(path: string, filename: string) {
  if (downloading.value.has(path)) return
  downloading.value.add(path)
  try { await api.download(path, filename); active && toastStore.success(`已下载 ${filename}`) }
  catch (reason) { active && toastStore.error(reason instanceof ApiClientError ? reason.message : '下载失败') }
  finally { downloading.value.delete(path) }
}

const formatSize = (bytes: number) => bytes > 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(2)} MB` : `${(bytes / 1024).toFixed(1)} KB`
const formatDate = (value: string) => new Intl.DateTimeFormat(uiLocale(), { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
</script>

<template>
  <div class="page-stack">
    <section class="page-intro page-intro--split"><div><p class="page-kicker">{{ uiText('实验资产') }}</p><h2>{{ uiText('下载中心') }}</h2><p>{{ uiText('下载有权访问的原始图片、优化图片与结构化实验报告。') }}</p></div><div class="tab-control"><button v-if="canReadFiles" :class="{ active: tab === 'files' }" @click="tab = 'files'">{{ uiText('文件') }} {{ files.length }}</button><button v-if="canReadReports" :class="{ active: tab === 'reports' }" @click="tab = 'reports'">{{ uiText('报告') }} {{ tasks.length }}</button></div></section>
    <div class="download-toolbar"><label><AppIcon name="search" :size="16" /><input v-model="query" class="field-input" type="search" :aria-label="uiText('搜索已加载的文件或报告')" :placeholder="uiText('搜索已加载的文件或报告')" /></label><button v-if="query" class="table-action" @click="query = ''">{{ uiText('清空筛选') }}</button><button class="button button--ghost" :disabled="loading" @click="load">{{ uiText('刷新列表') }}</button></div><div v-if="loading" class="large-skeleton" />
    <div v-else-if="error" class="inline-alert inline-alert--error" role="alert">{{ uiText(error) }} <button class="table-action" @click="load">{{ uiText('重试') }}</button></div>
    <section v-else class="panel">
      <template v-if="tab === 'files'">
        <div class="panel-header"><div><h3>{{ uiText('文件资产') }}</h3><p>{{ uiText('下载操作由后端重新鉴权') }}</p></div></div>
        <EmptyState v-if="!matchingFiles.length" :title="uiText('暂无文件')" :description="uiText('上传或生成优化图片后会显示在这里。')" />
        <div v-else class="data-table-wrap"><table class="data-table"><thead><tr><th>{{ uiText('文件名') }}</th><th>{{ uiText('类型') }}</th><th>{{ uiText('大小') }}</th><th>{{ uiText('所有者') }}</th><th>{{ uiText('创建时间') }}</th><th /></tr></thead><tbody>
          <tr v-for="file in matchingFiles" :key="file.id"><td><div class="table-primary"><span class="file-avatar"><AppIcon name="file" :size="17" /></span><span><strong>{{ file.originalName }}</strong><small>{{ file.sha256.slice(0, 12) }}…</small></span></div></td><td><StatusBadge :status="file.source" /></td><td>{{ formatSize(file.sizeBytes) }}</td><td>{{ file.ownerName }}</td><td>{{ formatDate(file.createdAt) }}</td><td><button class="table-action" :disabled="downloading.has(file.downloadUrl)" @click="download(file.downloadUrl, file.originalName)"><AppIcon name="download" :size="15" /> {{ uiText('下载') }}</button></td></tr>
        </tbody></table></div>
      </template>
      <template v-else>
        <div class="panel-header"><div><h3>{{ uiText('实验报告') }}</h3><p>{{ uiText('JSON 包含模型、指标、输出和追踪信息') }}</p></div></div>
        <EmptyState v-if="!matchingTasks.length" :title="uiText('暂无报告')" :description="uiText('完成实验后即可下载结构化报告。')" icon="docs" />
        <div v-else class="data-table-wrap"><table class="data-table"><thead><tr><th>{{ uiText('实验') }}</th><th>{{ uiText('模型') }}</th><th>{{ uiText('状态') }}</th><th>trace_id</th><th>{{ uiText('创建时间') }}</th><th /></tr></thead><tbody>
          <tr v-for="task in matchingTasks" :key="task.id"><td><div class="table-primary"><span class="file-avatar"><AppIcon name="docs" :size="17" /></span><span><strong>{{ taskLabel(task.taskType) }}</strong><small>{{ task.inputFile.originalName }}</small></span></div></td><td>{{ task.model.name }} <small class="table-muted">v{{ task.model.version }}</small></td><td><StatusBadge :status="task.status" /></td><td><code class="trace-short">{{ task.traceId.slice(0, 8) }}</code></td><td>{{ formatDate(task.createdAt) }}</td><td><button v-if="canDownloadReports" class="table-action" :disabled="downloading.has(task.reportUrl)" @click="download(task.reportUrl, `experiment-${task.id}.json`)"><AppIcon name="download" :size="15" /> {{ uiText('下载') }}</button></td></tr>
        </tbody></table></div>
      </template>
    </section>
  </div>
</template>

<style scoped>.download-toolbar{display:flex;align-items:center;gap:16px}.download-toolbar label{display:flex;align-items:center;gap:12px;max-width:460px;flex:1}.download-toolbar input{min-width:0}</style>
