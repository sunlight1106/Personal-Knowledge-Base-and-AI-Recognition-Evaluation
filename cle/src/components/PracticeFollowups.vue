<script setup lang="ts">
import { ref,onMounted,onBeforeUnmount } from 'vue'
import { request } from '@/api/client'
export interface Followup {term:string;kind:string;detail:string;dueDate:string;passes:number;bookId:string}
const emit=defineEmits<{practice:[item:Followup]}>(),items=ref<Followup[]>([]),error=ref(''),controller=new AbortController()
const names:Record<string,string>={MEANING:'释义辨认',SPELLING:'拼写',LISTENING:'听写',COLLOCATION:'搭配回忆'}
async function load(){try{const result=await request<Followup[]>('/vocabulary/statistics/followups',{signal:controller.signal});if(!controller.signal.aborted)items.value=result}catch(e){if(!controller.signal.aborted)error.value=(e as Error).message}}
onMounted(load);onBeforeUnmount(()=>controller.abort());defineExpose({load})
</script>
<template><section class="practice-followups"><h3>隔日再检查</h3><p>根据实际错题安排。提示后或刚看过答案的正确不抵消薄弱项；不同日期独立通过两次后结束跟进。</p><p v-if="error" role="alert">{{error}}</p><p v-if="!items.length">今天没有到期的薄弱项，可继续普通学习。</p><article v-for="i in items" :key="i.term+i.kind"><div><strong>{{i.term}} · {{names[i.kind]}}</strong><small>独立通过 {{i.passes}} / 2 次</small></div><button class="button" @click="emit('practice',i)">练这一项 →</button></article></section></template>
<style scoped>.practice-followups{border-top:1px solid var(--line);padding:24px 0}.practice-followups p,.practice-followups small{font-size:13px;color:var(--muted)}article{display:flex;justify-content:space-between;align-items:center;padding:16px 0;border-bottom:1px solid var(--line)}strong,small{display:block}small{margin-top:8px}</style>
