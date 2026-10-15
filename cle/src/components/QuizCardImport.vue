<script setup lang="ts">
import { uiText } from '@/i18n'

import {ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {request} from '@/api/client'
import {authStore} from '@/stores/auth'
const props=defineProps<{text:string}>(),route=useRoute(),cards=ref<{question:string;answer:string}[]>([]),busy=ref(false),message=ref(''),requestId=ref(crypto.randomUUID()),saved=ref(false)
watch(()=>props.text,text=>{cards.value=[];saved.value=false;message.value='';requestId.value=crypto.randomUUID();try{const data=JSON.parse(text.replace(/^\s*```(?:json)?\s*/,'').replace(/\s*```\s*$/,''));if(Array.isArray(data))cards.value=data.filter(c=>typeof c.question==='string'&&typeof c.answer==='string').slice(0,20).map(c=>({question:c.question.slice(0,2000),answer:c.answer.slice(0,10000)}))}catch{}},{immediate:true})
async function save(){if(busy.value||saved.value||!cards.value.length)return;busy.value=true;message.value='';try{await request('/learning/cards/import',{method:'POST',body:JSON.stringify({requestId:requestId.value,cards:cards.value,noteId:typeof route?.params.id==='string'?route.params.id:null})});saved.value=true;message.value='已加入复习卡，可在学习空间继续。'}catch(e){message.value=(e as Error).message}finally{busy.value=false}}
</script>
<template><section v-if="cards.length && authStore.has('research:use')" class="quiz-card-import"><h4>{{ uiText('核对后加入复习') }}</h4><p>{{ uiText('可以先修改问题和答案。保存后按回忆情况安排后续复习。') }}</p><div v-for="(c,index) in cards" :key="index"><label>{{ uiText('问题') }} {{index+1}}<textarea v-model="c.question" class="field-input" maxlength="2000" :disabled="busy||saved"/></label><label>{{ uiText('答案与解析') }}<textarea v-model="c.answer" class="field-input" maxlength="10000" :disabled="busy||saved"/></label></div><button class="button button--dark" :disabled="busy||saved||cards.some(c=>!c.question.trim()||!c.answer.trim())" @click="save">{{saved?uiText('已加入复习卡'):busy?uiText('正在保存…'):uiText('保存这些复习卡')}}</button><p role="status">{{message}}</p></section></template>
<style scoped>.quiz-card-import{border-top:1px solid var(--line);margin-top:24px;padding-top:20px}.quiz-card-import>div{display:grid;gap:12px;margin:20px 0}.quiz-card-import p{color:var(--muted);font-size:13px}textarea{margin-top:8px;min-height:70px}</style>
