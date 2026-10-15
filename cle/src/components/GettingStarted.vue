<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import {authStore} from '@/stores/auth'
import {request} from '@/api/client'
import {uiText} from '@/i18n'
import {createRequestGuard} from '@/lib/requestGuard'
import HelpHint from './HelpHint.vue'
import {firstSteps} from '@/lib/firstSteps'
interface Step {id:string;title:string;description:string;url:string;completed:boolean}
const steps=ref<Step[]>([]),hidden=ref(false),focus=ref('all'),error=ref(''),busy=ref(false),guard=createRequestGuard()
const key=computed(()=>`pkb:getting-started:${authStore.state.user?.id}`)
const done=computed(()=>steps.value.filter(s=>s.completed).length)
const ordered=computed(()=>[...steps.value].sort((a,b)=>Number(b.id===focus.value)-Number(a.id===focus.value)))
function store(){try{localStorage.setItem(key.value,JSON.stringify({hidden:hidden.value,focus:focus.value}))}catch{/* This preference is optional. */}}
function toggle(){hidden.value=!hidden.value;store()}
async function load(){const run=guard.start();busy.value=true;error.value='';try{const value=await request<Step[]>('/workspace/getting-started',{signal:run.signal});if(run.current()){steps.value=value}}catch(e){if(run.current())error.value=(e as Error).message}finally{if(run.current())busy.value=false}}
watch(()=>[authStore.state.user?.id,authStore.state.user?.permissions.join('|')],()=>{guard.cancel();steps.value=[];hidden.value=false;focus.value='all';try{const p=JSON.parse(localStorage.getItem(key.value)||'{}');hidden.value=p.hidden===true;if(['all','note','vocabulary','document','review'].includes(p.focus))focus.value=p.focus}catch{}if(authStore.state.user)void load()},{immediate:true})
onBeforeUnmount(()=>guard.cancel())
</script>
<template>
  <section v-if="steps.length || error" class="getting-started panel">
    <header><div><p class="page-kicker">YOUR FIRST STEPS</p><h3>{{ uiText('从这里开始') }} <small>{{ done }} / {{ steps.length }}</small><HelpHint label="入门进度" text="只统计已经保存的笔记、练习、文档和复习卡。点开页面不会完成任务；完成后可以点击「检查完成情况」。" /></h3></div><button class="button button--ghost" :aria-expanded="!hidden" @click="toggle">{{ hidden ? uiText('展开引导') : uiText('暂时收起') }}</button></header>
    <p v-if="error" role="alert">{{ uiText(error) }} <button class="table-action" :disabled="busy" @click="load">{{ uiText('重试') }}</button></p>
    <template v-if="!hidden">
      <p class="guide-welcome">{{ uiText('不用一次学会全部功能。先选一件想做的事，跟着步骤完成；这些入门操作都不需要 AI 密钥。') }}</p>
      <div class="guide-options"><label>{{ uiText('这次想先做什么') }}<select v-model="focus" class="field-input" @change="store"><option value="all">{{ uiText('按顺序开始') }}</option><option v-for="s in steps" :key="s.id" :value="s.id">{{ uiText(s.title) }}</option></select></label><button class="table-action" :disabled="busy" @click="load">{{ uiText('检查完成情况') }}</button></div>
      <div class="guide-steps">
        <article v-for="(s, i) in ordered" :key="s.id" :class="{ complete: s.completed }">
          <RouterLink class="guide-step-link" :to="s.url"><span>{{ s.completed ? '✓' : String(i + 1).padStart(2, '0') }}</span><div><strong>{{ uiText(s.title) }}</strong><p>{{ uiText(firstSteps[s.id]?.purpose || s.description) }}</p><small>{{ s.completed ? uiText('已完成') : uiText('开始 →') }}</small></div></RouterLink>
          <details v-if="firstSteps[s.id]" class="guide-step-detail"><summary>{{ uiText('怎么操作') }}</summary><ol><li v-for="line in firstSteps[s.id]!.steps" :key="line">{{ uiText(line) }}</li></ol><p class="guide-outcome"><strong>{{ uiText('完成后会看到') }}</strong>{{ uiText(firstSteps[s.id]!.outcome) }}</p><RouterLink :to="`/docs#${firstSteps[s.id]!.help}`">{{ uiText('查看完整说明 →') }}</RouterLink></details>
        </article>
      </div>
      <footer><p class="research-hint">{{ uiText('完成状态来自已保存的记录；打开页面不会被算作完成。引导可以随时收起。') }}</p><RouterLink to="/docs#first-session">{{ uiText('第一次使用？看分步指南 →') }}</RouterLink></footer>
    </template>
  </section>
</template>
<style scoped>
.getting-started{padding:26px}.getting-started header{display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap}.getting-started h3{margin:8px 0}.getting-started small{font-size:12px;color:var(--muted);font-weight:400}.guide-welcome{color:var(--ink-soft);font-size:14px;line-height:1.8;margin:18px 0 0}.guide-options{display:flex;align-items:center;justify-content:space-between;gap:20px;margin:24px 0;flex-wrap:wrap}.guide-options label{display:flex;align-items:center;gap:16px;font-size:13px;flex-wrap:wrap}.guide-options select{width:auto;max-width:100%}.guide-steps{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;align-items:start}.guide-steps article{border:1px solid var(--line);border-radius:12px;overflow:hidden}.guide-step-link{display:flex;gap:16px;padding:20px;text-decoration:none;color:var(--ink);transition:background .2s}.guide-step-link:hover{background:var(--green-soft)}.guide-step-link>span{color:var(--green);font-size:13px}.guide-step-link p{font-size:13px;color:var(--muted);line-height:1.7}.guide-step-detail{border-top:1px solid var(--line-soft);padding:0 20px 16px;font-size:13px;line-height:1.85}.guide-step-detail:not([open]){padding-bottom:0}.guide-step-detail summary{cursor:pointer;color:var(--green);padding:12px 0}.guide-step-detail ol{padding-left:20px;margin:2px 0 14px}.guide-step-detail li{margin:8px 0}.guide-outcome{padding:12px;background:var(--green-soft);border-radius:8px}.guide-outcome strong{display:block;margin-bottom:4px;font-size:12px}.getting-started footer{display:flex;gap:16px;align-items:center;justify-content:space-between;flex-wrap:wrap;margin-top:20px;font-size:13px}.getting-started footer p{margin:0;flex:1;min-width:220px}.guide-steps .complete{background:var(--paper)}@media(max-width:700px){.guide-steps{grid-template-columns:1fr}}@media(prefers-reduced-motion:reduce){.guide-step-link{transition:none}}
</style>
