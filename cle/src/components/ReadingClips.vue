<script setup lang="ts">
import RequestFailure from '@/components/RequestFailure.vue'
import { uiText } from '@/i18n'

import { ref,onMounted,onBeforeUnmount } from 'vue'
import { request } from '@/api/client'
const clips=ref<{id:string;sourceKind:string;sourceId:string;quoteText:string;sourceTitle:string;term?:string}[]>([]),error=ref(''),controller=new AbortController()
async function load(){error.value='';try{clips.value=await request('/learning/clips',{signal:controller.signal})}catch(e){if(!controller.signal.aborted)error.value=(e as Error).message}}
async function remove(id:string){try{await request('/learning/clips/'+id,{method:'DELETE'});await load()}catch(e){error.value=(e as Error).message}}
onMounted(load);onBeforeUnmount(()=>controller.abort())
</script>
<template><section><h2>{{ uiText('阅读摘录') }}</h2><p class="research-hint">{{ uiText('最近 100 条摘录。单词收藏保留当时的原句，已保存的笔记和复习卡可以分别编辑。') }}</p><RequestFailure :message="error" :retry="load" /><p v-if="!clips.length&&!error" class="research-empty">{{ uiText('在文档或已保存的笔记预览中选中文字，开始积累。') }}</p><article v-for="clip in clips" :key="clip.id" class="clip"><strong>{{clip.term||clip.sourceTitle}}</strong><blockquote>{{clip.quoteText}}</blockquote><div class="research-actions"><RouterLink :to="clip.sourceKind==='NOTE'?'/app/notes/'+clip.sourceId+'/edit':'/app/research?tab=documents&passage='+clip.sourceId">{{clip.sourceTitle}} ↗</RouterLink><button class="table-action" @click="remove(clip.id)">{{ uiText('移除摘录') }}</button></div></article></section></template>
<style scoped>.clip{padding:24px 0;border-bottom:1px solid var(--line)}blockquote{margin:16px 0;white-space:pre-wrap;line-height:1.8}.clip strong{font-weight:500}</style>
