<script setup lang="ts">
import { uiText, uiLocale } from '@/i18n'

import { onMounted,onBeforeUnmount,ref } from 'vue'
import { accountApi,type AccountActivity } from '@/api/accounts'
import { authStore } from '@/stores/auth'
const rows=ref<AccountActivity[]>([]),page=ref(0),busy=ref(false),error=ref('')
let active=true;const owner=authStore.state.user?.id
onBeforeUnmount(()=>{active=false})
async function load(next=0){if(busy.value)return;busy.value=true;error.value='';try{const result=await accountApi.activity(next);if(active&&owner===authStore.state.user?.id){rows.value=result;page.value=next}}catch(e){if(active)error.value=(e as Error).message}finally{busy.value=false}}
onMounted(()=>load())
</script>
<template><article class="panel settings-card"><header><div><h3>{{ uiText('登录记录') }}</h3><p>{{ uiText('保留最近 90 天的登录尝试。网络地址已遮蔽，设备信息由浏览器提供。') }}</p></div><button class="button button--ghost" :disabled="busy" @click="load(page)">{{ uiText('刷新') }}</button></header><p v-if="error" class="form-error" role="alert">{{ uiText(error) }}</p><p v-if="!rows.length&&!busy">{{ uiText('暂无记录。') }}</p><ol class="activity-list"><li v-for="(row,i) in rows" :key="row.createdAt+i"><div><strong>{{row.action==='REACTIVATE'?uiText('恢复账号'):uiText('登录')}} · {{row.outcome==='SUCCESS'?uiText('成功'):uiText('未通过验证')}}</strong><time>{{new Date(row.createdAt).toLocaleString(uiLocale())}}</time></div><span>{{row.network}} · {{row.device}}</span></li></ol><nav v-if="page||rows.length===30" class="security-actions" :aria-label="uiText('登录记录分页')"><button class="button button--ghost" :disabled="busy||!page" @click="load(page-1)">{{ uiText('上一页') }}</button><span>{{ uiText('第') }} {{page+1}} {{ uiText('页') }}</span><button class="button button--ghost" :disabled="busy||rows.length<30" @click="load(page+1)">{{ uiText('下一页') }}</button></nav></article></template>
<style scoped>.activity-list{list-style:none;padding:0}.activity-list li{padding:16px 0;border-bottom:1px solid var(--line)}li div{display:flex;gap:20px;justify-content:space-between}li span{display:block;margin-top:8px;overflow-wrap:anywhere;color:var(--muted);font-size:13px}time{font-size:13px;white-space:nowrap}.security-actions{display:flex;align-items:center;gap:14px;margin-top:18px}</style>
