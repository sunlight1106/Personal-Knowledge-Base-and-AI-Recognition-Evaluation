<script setup lang="ts">
import { uiText } from '@/i18n'

import { computed,onMounted,onBeforeUnmount,ref } from 'vue'
import { workflowApi,type WorkflowPreferences } from '@/api/workflows'
import type { VocabularyDashboard } from '@/api/vocabulary'
const props=defineProps<{dashboard:VocabularyDashboard}>(),plan=ref<WorkflowPreferences>();let active=true
onBeforeUnmount(()=>{active=false})
onMounted(async()=>{try{const p=await workflowApi.preferences();if(active)plan.value=p}catch{ /* The study session remains usable while preferences are unavailable. */ }})
const date=computed(()=>props.dashboard.today.date||new Intl.DateTimeFormat('sv',{timeZone:props.dashboard.settings.zoneId||'UTC'}).format(new Date()))
const monday=computed(()=>{const d=new Date(`${date.value}T12:00:00Z`);d.setUTCDate(d.getUTCDate()-((d.getUTCDay()+6)%7));return d.toISOString().slice(0,10)})
const answers=computed(()=>props.dashboard.history.filter(d=>d.date&&d.date>=monday.value&&d.date<=date.value).reduce((sum,d)=>sum+d.answers,0))
const target=computed(()=>plan.value?.weeklyTarget||70),progress=computed(()=>Math.min(100,answers.value/target.value*100))
</script>
<template>
  <section class="weekly-plan" :aria-label="uiText('本周学习计划')">
    <div class="weekly-plan-summary">
      <strong>{{ uiText('本周练习') }}</strong><span>{{ answers }} / {{ target }} {{ uiText('次') }}</span>
      <progress :value="progress" max="100" :aria-label="(uiText('本周已完成 ') + (answers) + uiText(' 次练习'))" />
      <a href="/app/settings?section=workflow">{{ uiText('调整计划 →') }}</a>
    </div>
    <details><summary>{{ uiText('如何统计') }}</summary><p>{{ uiText('周一至周日统计答题次数；掌握程度另按独立回忆和复习表现记录。') }}</p></details>
  </section>
</template>
<style scoped>
.weekly-plan{display:flex;flex-wrap:wrap;gap:12px 24px;align-items:baseline;padding:14px 22px;border-top:1px solid var(--line-soft);font-size:12px;color:var(--muted)}
.weekly-plan-summary{display:flex;flex:1;flex-wrap:wrap;gap:16px;align-items:center}
.weekly-plan strong{font-weight:500;color:var(--ink-soft)}
.weekly-plan a{margin-left:auto;text-decoration:none;white-space:nowrap}
.weekly-plan progress{display:block;width:150px;height:4px;border:0;border-radius:3px;overflow:hidden;accent-color:var(--green);background:var(--line)}
.weekly-plan progress::-webkit-progress-bar{background:var(--line)}
.weekly-plan progress::-webkit-progress-value{background:var(--green);border-radius:3px}
.weekly-plan progress::-moz-progress-bar{background:var(--green);border-radius:3px}
.weekly-plan details{flex-basis:auto}.weekly-plan details[open]{flex-basis:100%}
.weekly-plan summary{cursor:pointer}.weekly-plan p{margin:8px 0 0;font-size:12px;line-height:1.7}
@media(max-width:760px){.weekly-plan progress{width:80px}.weekly-plan{padding:14px 16px}}
</style>
