<script setup lang="ts">
import FileLifecyclePanel from '@/components/FileLifecyclePanel.vue'
import DialogPanel from '@/components/DialogPanel.vue'
import { uiText, uiLocale } from '@/i18n'

import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { api, ApiClientError } from '@/api/client'
import type { FileView } from '@/types/api'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { authStore } from '@/stores/auth'
import { toastStore } from '@/stores/toast'

const files = ref<FileView[]>([])
const previewErrors = reactive<Record<string, boolean>>({}), downloading = ref(new Set<string>())
const previews = reactive<Record<string, string>>({})
const loading = ref(true)
const error = ref('')
const query = ref('')
const source = ref<'ALL' | 'UPLOAD' | 'ENHANCED'>('ALL')
const selected = ref<FileView | null>(null)
const manageFile=ref<FileView>(),showStorage=ref(false)
function manage(file?:FileView){manageFile.value=file;selected.value=null;showStorage.value=true}
async function storageChanged(){showStorage.value=false;manageFile.value=undefined;await load()}

const filtered = computed(() => files.value)
const page=ref(0),total=ref(0)
let epoch=0,active=true,observer:IntersectionObserver|undefined,timer:ReturnType<typeof setTimeout>|undefined
const pending=new Map<string,number>()
function releasePreviews(){Object.values(previews).forEach(url=>{if(url)URL.revokeObjectURL(url)});Object.keys(previews).forEach(k=>delete previews[k])}
async function preview(file:FileView){
 if(previews[file.id]||pending.get(file.id)===epoch)return
 const version=epoch;pending.set(file.id,version);previewErrors[file.id]=false
 try{const url=await api.blobUrl(file.contentUrl);if(active&&version===epoch)previews[file.id]=url;else URL.revokeObjectURL(url)}catch{if(active&&version===epoch){previews[file.id]='';previewErrors[file.id]=true}}finally{if(pending.get(file.id)===version)pending.delete(file.id)}
}
async function select(file:FileView){selected.value=file;await preview(file)}
async function load(){
 const version=++epoch;loading.value=true;error.value='';observer?.disconnect();releasePreviews();selected.value=null
 try{
  const result=await api.filePage({q:query.value.trim(),source:source.value,page:String(page.value),size:'24'})
  if(!active||epoch!==version)return
  files.value=result.items;total.value=result.total
  if(page.value>0 && page.value>=Math.max(1,Math.ceil(total.value/24))){page.value=Math.max(0,Math.ceil(total.value/24)-1);return}
 }catch(e){if(active&&epoch===version){files.value=[];error.value=(e as Error).message}}
 finally{if(active&&epoch===version)loading.value=false}
 await nextTick();if(!active||version!==epoch)return
 if(typeof IntersectionObserver==='undefined')return
 observer=new IntersectionObserver(entries=>{for(const entry of entries)if(entry.isIntersecting){observer?.unobserve(entry.target);const file=files.value.find(f=>f.id===(entry.target as HTMLElement).dataset.fileId);if(file?.contentType.startsWith('image/'))void preview(file)}},{rootMargin:'80px'})
 document.querySelectorAll('.asset-card[data-file-id]').forEach(node=>observer?.observe(node))
}
watch([query,source],()=>{epoch++;clearTimeout(timer);timer=setTimeout(()=>{if(page.value)page.value=0;else void load()},250)})
watch(page,()=>void load())
onMounted(load)
onBeforeUnmount(()=>{active=false;epoch++;clearTimeout(timer);observer?.disconnect();releasePreviews()})

async function download(file: FileView) {
  if (downloading.value.has(file.id)) return
  downloading.value.add(file.id)
  try {
    await api.download(file.downloadUrl, file.originalName)
    active && toastStore.success(`已下载 ${file.originalName}`)
  } catch (reason) {
    active && toastStore.error(reason instanceof ApiClientError ? reason.message : '下载失败')
  } finally { downloading.value.delete(file.id) }
}

function playPreview(event: Event) {
  const video = (event.currentTarget as HTMLElement).querySelector('video')
  video?.play().catch(() => undefined)
}

function pausePreview(event: Event) {
  const video = (event.currentTarget as HTMLElement).querySelector('video')
  if (!video) return
  video.pause()
  video.currentTime = 0
}

const formatSize = (bytes: number) => bytes > 1024 * 1024
  ? `${(bytes / 1024 / 1024).toFixed(2)} MB` : `${(bytes / 1024).toFixed(1)} KB`
const formatDate = (value: string) => new Intl.DateTimeFormat(uiLocale(), { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
</script>

<template>
  <div class="page-stack images-page">
    <div class="media-list-actions"><button v-if="authStore.has('file:write')" class="button button--ghost" @click="manage()">{{uiText('空间与回收站')}}</button></div><section class="page-intro page-intro--split">
      <div><p class="page-kicker">MEDIA LIBRARY</p><h2>Images & Video</h2><p>{{ uiText('真实图片、视频、增强输出、病毒扫描与对象存储状态集中在同一个媒体库。') }}</p></div>
      <RouterLink v-if="authStore.has('file:write')" to="/app/upload" class="button button--dark"><AppIcon name="upload" :size="17" /> {{ uiText('上传媒体') }}</RouterLink>
    </section>

    <div class="asset-toolbar panel">
      <label class="asset-search"><AppIcon name="search" :size="18" /><input v-model="query" :aria-label="uiText('搜索文件名')" type="search" :placeholder="uiText('搜索文件名')" /></label>
      <div class="asset-filters"><button :class="{ active: source === 'ALL' }" @click="source = 'ALL'">{{ uiText('全部') }} {{ total }}</button><button :class="{ active: source === 'UPLOAD' }" @click="source = 'UPLOAD'">{{ uiText('原始输入') }}</button><button :class="{ active: source === 'ENHANCED' }" @click="source = 'ENHANCED'">{{ uiText('增强输出') }}</button></div>
    </div>

    <div class="media-list-actions"><button v-if="query || source !== 'ALL'" class="table-action" @click="query = ''; source = 'ALL'">{{ uiText('清空筛选') }}</button><button class="table-action" :disabled="loading" @click="load">{{ uiText('刷新列表') }}</button></div>
    <div v-if="loading" class="image-loading-grid"><span v-for="i in 8" :key="i" /></div>
    <div v-else-if="error" class="inline-alert inline-alert--error" role="alert">{{ uiText(error) }} <button class="table-action" @click="load">{{ uiText('重试') }}</button></div>
    <EmptyState v-else-if="!filtered.length" :title="uiText('没有匹配的媒体')" :description="uiText('调整筛选条件，或上传一个新的真实样本。')" icon="images" />
    <section v-else class="asset-grid">
      <article v-for="file in filtered" :key="file.id" class="asset-card" :data-file-id="file.id">
        <button class="asset-card-image" type="button" @mouseenter="playPreview" @mouseleave="pausePreview" @focus="playPreview" @blur="pausePreview" @click="select(file)">
          <video v-if="previews[file.id] && file.contentType.startsWith('video/')" :src="previews[file.id]" muted playsinline />
          <img v-else-if="previews[file.id]" :src="previews[file.id]" :alt="file.originalName" />
          <span v-else><AppIcon name="images" :size="28" /></span>
          <i class="asset-hover-action"><AppIcon name="eye" :size="17" /> {{ uiText('预览') }}</i>
          <StatusBadge :status="file.source" />
        </button>
        <div class="asset-card-copy"><div><strong>{{ file.originalName }}</strong><small>{{ formatSize(file.sizeBytes) }} · {{ formatDate(file.createdAt) }}</small></div><button class="icon-button" :title="uiText('下载')" :disabled="downloading.has(file.id)" @click="download(file)"><AppIcon name="download" :size="18" /></button></div>
      </article>
    </section>

    <nav class="research-pagination" :aria-label="uiText('素材分页')"><button class="button button--ghost" :disabled="!page||loading" @click="page--">{{uiText('上一页')}}</button><span>{{page+1}} / {{Math.max(1,Math.ceil(total/24))}}</span><button class="button button--ghost" :disabled="(page+1)*24>=total||loading" @click="page++">{{uiText('下一页')}}</button></nav>
    <DialogPanel v-if="showStorage" :title="uiText('空间与回收站')" @close="showStorage=false"><FileLifecyclePanel :file="manageFile" @changed="storageChanged" /></DialogPanel>
    <DialogPanel v-if="selected" :title="selected.originalName" wide @close="selected = null">
      <div class="media-preview"><p class="field-hint">{{ selected.ownerName }} · {{ selected.scanStatus }} · {{ formatSize(selected.sizeBytes) }}</p><div v-if="previewErrors[selected.id]" role="alert"><p>{{ uiText('预览加载失败，原文件仍可尝试下载。') }}</p><button class="button button--ghost" @click="preview(selected)">{{ uiText('重试') }}</button></div><p v-else-if="!previews[selected.id]" role="status">{{ uiText('正在加载…') }}</p><video v-else-if="selected.contentType.startsWith('video/')" :src="previews[selected.id]" controls playsinline /><img v-else :src="previews[selected.id]" :alt="selected.originalName" /><footer><button v-if="authStore.has('file:write')" class="button button--ghost" @click="manage(selected)">{{uiText('引用与删除')}}</button><StatusBadge :status="selected.source" /><button class="button button--ghost" :disabled="downloading.has(selected.id)" @click="download(selected)"><AppIcon name="download" :size="16" /> {{ uiText('下载原文件') }}</button></footer></div>
    </DialogPanel>
  </div>
</template>

<style scoped>.media-list-actions{display:flex;gap:16px;justify-content:flex-end}.media-preview>img,.media-preview>video{display:block;max-width:100%;max-height:65vh;margin:auto;object-fit:contain}.media-preview footer{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-top:20px}</style>
