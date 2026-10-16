<script setup lang="ts">
import SelectField from '@/components/SelectField.vue'
import { uiText } from '@/i18n'

import { ref, onBeforeUnmount, onMounted } from 'vue'
import { workflowApi } from '@/api/workflows'
const props=defineProps<{id:string}>()
const date=ref(''),repeat=ref(7),saved=ref(false),busy=ref(false),error=ref('')
let active=true
onBeforeUnmount(()=>active=false)
onMounted(async()=>{try{const row=await workflowApi.reminder(props.id);if(active){date.value=row.date||'';repeat.value=row.repeatDays??7;saved.value=!!row.date}}catch(e){if(active)error.value=(e as Error).message}})
async function action(kind:string){if(busy.value)return;busy.value=true;error.value='';try{if(kind==='save'){const p=await workflowApi.preferences();if(!p.revision)await workflowApi.savePreferences({...p,zoneId:Intl.DateTimeFormat().resolvedOptions().timeZone});await workflowApi.remind(props.id,date.value,repeat.value)}else if(kind==='complete')await workflowApi.completeReminder(props.id);else await workflowApi.clearReminder(props.id);const row=await workflowApi.reminder(props.id);if(active){date.value=row.date||'';saved.value=!!row.date}}catch(e){if(active)error.value=(e as Error).message}finally{if(active)busy.value=false}}
</script>
<template><details class="note-reminder"><summary>{{ uiText('安排复习') }} <small v-if="saved">{{ date }}</small></summary><div><label>{{ uiText('复习日期') }}<input v-model="date" class="field-input" type="date" :disabled="busy" /></label><label>{{ uiText('复习完成后') }}<SelectField><select v-model.number="repeat" class="field-input" :disabled="busy"><option :value="0">{{ uiText('仅本次') }}</option><option :value="1">{{ uiText('次日再复习') }}</option><option :value="7">{{ uiText('一周后') }}</option><option :value="30">{{ uiText('一个月后') }}</option></select></SelectField></label><button class="button button--ghost" :disabled="!date||busy" @click="action('save')">{{ uiText('保存安排') }}</button><button v-if="saved" class="table-action" :disabled="busy" @click="action('complete')">{{ uiText('本次已复习') }}</button><button v-if="saved" class="table-action" :disabled="busy" @click="action('clear')">{{ uiText('取消提醒') }}</button></div><p v-if="error" class="form-error" role="alert">{{ uiText(error) }}</p></details></template>
<style scoped>.note-reminder{border:1px solid var(--line);padding:16px 20px;font-size:13px}.note-reminder summary{cursor:pointer;color:var(--green)}small{margin-left:12px;color:var(--muted)}.note-reminder>div{display:flex;align-items:end;gap:14px;padding-top:18px;flex-wrap:wrap}label{display:grid;gap:8px;font-size:12px;color:var(--muted)}input,select{width:165px;padding:9px}
.select-field{width:165px}
</style>
