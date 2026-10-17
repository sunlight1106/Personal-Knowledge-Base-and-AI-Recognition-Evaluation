<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue'
import {request} from '@/api/client'
import {authStore} from '@/stores/auth'
import {uiLanguage,uiText} from '@/i18n'
import {codeReport,matchingRun,runNoteId,snapshotKey,type CodeSnapshot} from '@/lib/codeLabReport'
import type {Run} from '@/lib/codeLab'
import DialogPanel from './DialogPanel.vue'
const props=defineProps<{snapshot:CodeSnapshot;run?:Run;step:number;disabled?:boolean}>()
const emit=defineEmits<{archived:[Run];saved:[string];busy:[boolean]}>()
const current=computed(()=>matchingRun(props.snapshot,props.run))
const working=ref(false),message=ref(''),error=ref(''),noteId=ref(''),archiveOpen=ref(false),title=ref(''),memo=ref('')
const noteInTrash=ref(false)
let draftKey='',draftId='',disposed=false
const controller=new AbortController()
const defaultTitle=()=>current.value?.title||`${uiText('代码记录')} · ${current.value?.result.language||props.snapshot.language} · ${props.snapshot.entry||uiText('顶层执行')}`
const lock=computed(()=>props.disabled||working.value)
watch(()=>snapshotKey(props.snapshot),()=>{message.value='';error.value='';noteId.value='';noteInTrash.value=false;draftKey='';draftId=''})
watch(()=>props.run?.id,()=>{message.value='';error.value='';noteId.value='';noteInTrash.value=false})
function setBusy(value:boolean){working.value=value;emit('busy',value)}
function report(note=false){return codeReport(props.snapshot,{run:current.value,title:defaultTitle(),memo:current.value?.memo,step:props.step,note,english:uiLanguage.value==='en'})}
async function saveNote(){
  if(lock.value||!authStore.has('note:write')||!props.snapshot.source.trim())return
  setBusy(true);error.value='';message.value='';noteInTrash.value=false
  const key=snapshotKey(props.snapshot),body=report(true),name=defaultTitle()
  try{
    if(body.length>200000)throw Error(uiText('记录过大，请导出回放 JSON 保存完整数据。'))
    if(draftKey!==key){draftKey=key;draftId=crypto.randomUUID()}
    const clientId=current.value?await runNoteId(current.value.id):draftId
    const note=await request<{id:string}>('/notes',{method:'POST',signal:controller.signal,body:JSON.stringify({clientId,title:name,body,tags:'Code Lab',status:'DRAFT',library:'计算机学习',contentFormat:'MARKDOWN'})})
    if(!disposed){noteId.value=note.id;message.value='已存入计算机学习库。再次保存同一运行会打开原笔记，保留你的修改。';emit('saved',key)}
  }catch(e){if(!disposed){error.value=(e as Error).message;noteInTrash.value=(e as {code?:string}).code==='NOTE_CREATE_TRASHED'}}
  finally{if(!disposed)setBusy(false)}
}
function editArchive(){if(lock.value||!current.value)return;title.value=defaultTitle();memo.value=current.value.memo||'';error.value='';archiveOpen.value=true}
async function archive(){
  if(lock.value||!current.value||!title.value.trim())return
  setBusy(true);error.value=''
  try{
    const value=await request<Run>(`/code-lab/runs/${current.value.id}/archive`,{method:'PATCH',signal:controller.signal,body:JSON.stringify({archived:true,title:title.value.trim(),memo:memo.value})})
    if(!disposed){emit('archived',value);archiveOpen.value=false;message.value='已保存到长期存档。'}
  }catch(e){if(!disposed)error.value=(e as Error).message}
  finally{if(!disposed)setBusy(false)}
}
function download(){
  if(lock.value||!props.snapshot.source.trim())return
  const url=URL.createObjectURL(new Blob([report()],{type:'text/markdown;charset=utf-8'})),a=document.createElement('a')
  a.href=url;a.download=defaultTitle().replace(/[\\/:*?"<>|\x00-\x1f]/g,'_').slice(0,80)+'.md';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
}
async function copy(){
  if(lock.value||!props.snapshot.source.trim())return
  error.value='';setBusy(true)
  try{await navigator.clipboard.writeText(report());if(!disposed)message.value='已复制 Markdown，可粘贴分享。'}
  catch{if(!disposed)error.value='浏览器未允许复制，请使用导出 Markdown。'}
  finally{if(!disposed)setBusy(false)}
}
onBeforeUnmount(()=>{disposed=true;controller.abort()})
</script>
<template>
  <section class="lab-save" :aria-label="uiText('保存与分享')">
    <div class="lab-save-heading"><span class="lab-save-kicker">KEEP / REFLECT / SHARE</span><h2>{{uiText('留下这一次思考')}}</h2><p>{{uiText(current?'保存当前代码、输入、运行结果、计数公式与所选步骤。':'保存当前编辑的代码草稿；不会混入上一次运行结果。')}}</p></div>
    <div class="lab-save-actions">
      <button v-if="authStore.has('note:write')" class="button button--dark" :disabled="lock||!snapshot.source.trim()" @click="saveNote">{{uiText(working&&!archiveOpen?'正在保存…':'存入笔记')}}</button>
      <button class="button button--ghost" :disabled="lock||!current" @click="editArchive">{{uiText(current?.archived?'编辑存档':'长期存档')}}</button>
      <button class="button button--ghost" :disabled="lock||!snapshot.source.trim()" @click="download">{{uiText('导出 Markdown')}}</button>
      <button class="button button--ghost" :disabled="lock||!snapshot.source.trim()" @click="copy">{{uiText('复制分享内容')}}</button>
    </div>
    <p v-if="!current" class="lab-save-hint">{{uiText('运行后可存档完整回放；现在可以先保存代码笔记。')}}</p>
    <p v-if="message" class="lab-save-feedback" role="status">{{uiText(message)}} <a v-if="noteId&&authStore.has('note:read')" :href="`/app/notes/${noteId}`" target="_blank" rel="noopener">{{uiText('打开笔记')}} ↗</a></p>
    <p v-if="error&&!archiveOpen" class="inline-alert inline-alert--error" role="alert">{{uiText(error)}} <a v-if="noteInTrash&&authStore.has('note:read')" href="/app/notes/trash" target="_blank" rel="noopener">{{uiText('打开回收站')}} ↗</a></p>
    <DialogPanel v-if="archiveOpen" :title="uiText(current?.archived?'编辑存档':'保存长期存档')" :busy="working" @close="archiveOpen=false">
      <form class="lab-archive-form" @submit.prevent="archive"><p>{{uiText('存档仅自己可见，保留完整回放，不随最近记录清理。每个账号最多 50 条。')}}</p>
        <label>{{uiText('存档名称')}}<input v-model="title" class="field-input" maxlength="120" required :disabled="working" /></label>
        <label>{{uiText('学习心得（可选）')}}<textarea v-model="memo" class="field-input" maxlength="2000" rows="5" :disabled="working" :placeholder="uiText('记下思路、边界情况，或下次想验证的问题。')" /></label>
        <p v-if="error" class="inline-alert inline-alert--error" role="alert">{{uiText(error)}}</p>
        <div><button type="button" class="button button--ghost" :disabled="working" @click="archiveOpen=false">{{uiText('取消')}}</button><button class="button button--dark" :disabled="working||!title.trim()">{{uiText(working?'正在保存…':'保存存档')}}</button></div>
      </form>
    </DialogPanel>
  </section>
</template>
<style scoped>
.lab-save{margin:24px 0;padding:24px 28px;border:1px solid var(--line);border-radius:16px;background:linear-gradient(110deg,color-mix(in srgb,var(--green) 5%,var(--paper)),var(--paper));display:flex;flex-wrap:wrap;align-items:center;gap:16px 24px}.lab-save-heading{flex:1;min-width:240px}.lab-save-kicker{font:9px Consolas,monospace;letter-spacing:.14em;color:var(--muted)}.lab-save h2{font-size:18px;font-weight:500;margin:8px 0}.lab-save p{font-size:12px;color:var(--muted);line-height:1.9;margin:0}.lab-save-actions{display:flex;flex-wrap:wrap;gap:8px}.lab-save-actions .button{font-size:12px;padding:10px 14px}.lab-save-hint,.lab-save-feedback{width:100%}.lab-save-feedback a{color:var(--green);margin-left:12px}.lab-save .inline-alert{width:100%}.lab-archive-form{display:grid;gap:18px;font-size:13px;line-height:1.9;color:var(--muted)}.lab-archive-form p{margin:0}.lab-archive-form label{display:grid;gap:8px}.lab-archive-form>div{display:flex;gap:10px;justify-content:flex-end}.lab-archive-form textarea{resize:vertical;min-height:100px}
</style>
