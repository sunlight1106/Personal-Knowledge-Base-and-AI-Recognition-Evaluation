<script setup lang="ts">
import RequestFailure from '@/components/RequestFailure.vue'
import { uiText } from '@/i18n'

import { onMounted,onBeforeUnmount,ref } from 'vue'
import { researchApi,type SearchFilters,type SavedSearch } from '@/api/research'
import { createRequestGuard } from '@/lib/requestGuard'
const props=defineProps<{filters:SearchFilters}>(),emit=defineEmits<{apply:[filters:SearchFilters]}>()
const rows=ref<SavedSearch[]>([]),name=ref(''),busy=ref(false),error=ref(''),guard=createRequestGuard()
const panel=ref<HTMLDetailsElement|null>(null)
const editing=ref<SavedSearch|null>(null),editName=ref('')
function rename(row:SavedSearch){editing.value=row;editName.value=row.name}
async function saveName(){if(!editing.value||!editName.value.trim()||busy.value)return;const request=guard.start();busy.value=true;error.value='';try{const row=await researchApi.saveSearch(editName.value.trim(),editing.value.filters);if(request.current()){rows.value=rows.value.map(r=>r.id===row.id?row:r);editing.value=null}}catch(e){if(request.current())error.value=(e as Error).message}finally{if(request.current())busy.value=false}}
function exportSearches(){const blob=new Blob([JSON.stringify({format:'pkb-searches-v1',searches:rows.value.map(({name,filters})=>({name,filters}))},null,2)],{type:'application/json'});const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download='saved-searches.json';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
async function load(){const request=guard.start();error.value='';try{const result=await researchApi.savedSearches(request.signal);if(request.current())rows.value=result}catch(e){if(request.current())error.value=(e as Error).message}}
async function save(){if(busy.value||!name.value.trim())return;const request=guard.start();busy.value=true;error.value='';try{const row=await researchApi.saveSearch(name.value.trim(),props.filters);if(request.current()){rows.value=[row,...rows.value.filter(r=>r.id!==row.id)];name.value='';panel.value?.removeAttribute('open')}}catch(e){if(request.current())error.value=(e as Error).message}finally{if(request.current())busy.value=false}}
async function remove(row:SavedSearch){if(busy.value)return;const request=guard.start();busy.value=true;error.value='';try{await researchApi.removeSearch(row.id);if(request.current())rows.value=rows.value.filter(r=>r.id!==row.id)}catch(e){if(request.current())error.value=(e as Error).message}finally{if(request.current())busy.value=false}}
onMounted(load);onBeforeUnmount(()=>guard.cancel())
</script>
<template>
 <section class="saved-searches" :aria-label="uiText('常用搜索')"><div class="saved-search-row"><span>{{ uiText('常用搜索') }}</span><span v-if="!rows.length&&!error" class="research-hint">{{ uiText('把常查的关键词与筛选条件保存在这里。') }}</span><div v-for="row in rows" :key="row.id" class="saved-search-chip"><button :disabled="busy" @click="emit('apply',row.filters)">{{ row.name }}</button><button :disabled="busy" :aria-label="uiText('重命名')+row.name" @click="rename(row)">{{uiText('重命名')}}</button><button :disabled="busy" :aria-label="'删除常用搜索 '+row.name" @click="remove(row)">×</button></div><details ref="panel" class="save-search-form"><summary>{{ uiText('＋ 保存当前搜索') }}</summary><form @submit.prevent="save"><label>{{ uiText('搜索名称') }}<input v-model="name" class="field-input" maxlength="80" :placeholder="uiText('例如：上周的实验笔记')"/></label><button class="button button--dark" :disabled="busy||!name.trim()">{{ uiText('保存') }}</button></form></details></div><button v-if="rows.length" class="table-action" @click="exportSearches">{{uiText('导出常用搜索')}}</button><form v-if="editing" class="research-actions" @submit.prevent="saveName"><label>{{uiText('新名称')}}<input v-model="editName" class="field-input" maxlength="80" required /></label><button class="button" :disabled="busy||!editName.trim()">{{uiText('保存')}}</button><button type="button" class="button button--ghost" :disabled="busy" @click="editing=null">{{uiText('取消')}}</button></form><RequestFailure :message="error" :retry="load" :disabled="!!busy" /></section>
</template>
