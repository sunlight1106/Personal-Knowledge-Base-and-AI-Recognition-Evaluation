// Simulated failure and recovery; no real credentials or provider requests.
import {createApp,nextTick} from 'vue'
import {createMemoryHistory,createRouter} from 'vue-router'
import ResearchView from '../views/ResearchView.vue'
import {researchApi} from '../api/research'
import {personalApi} from '../api/personal'
import {ApiClientError} from '../api/client'
import {authStore} from '../stores/auth'
const fixture=document.getElementById('fixture'),passed=[]
const assert=(v,m)=>{if(!v)throw new Error(m);passed.push(m)}
const wait=async f=>{for(let i=0;i<100;i++){await new Promise(r=>setTimeout(r,5));await nextTick();if(f())return}throw new Error('UI timed out')}
const button=t=>[...fixture.querySelectorAll('button')].find(b=>b.textContent.trim()===t)
let app,executions=0,previews=0,failPreview=false,rejectAnswer
const hit={id:'one',kind:'NOTE',title:'Selected source',excerpt:'Synthetic source',updatedAt:'2026-10-31'}
try{
 authStore.state.user={id:1,roleCode:'USER',permissions:['research:use','note:write','personal-ai:use']}
 personalApi.settings=async()=>[{provider:'MOONSHOT',model:'kimi-k3',enabled:true,configured:true}]
 researchApi.bookmarkKeys=async()=>[];researchApi.savedSearches=async()=>[]
 researchApi.search=async()=>({items:[hit],page:0,hasMore:false})
 researchApi.preview=async()=>{previews++;if(failPreview)throw new ApiClientError('PERSONAL_AI_CONFIG_REQUIRED','请先配置个人 AI',400);return {previewToken:'token-'+previews,provider:'MOONSHOT',model:'kimi-k3',context:'Approved synthetic content'}}
 researchApi.answer=async()=>{executions++;return new Promise((resolve,reject)=>{rejectAnswer=reject})}
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{template:'<div />'}}]});await router.push('/app/research');await router.isReady()
 app=createApp(ResearchView);app.use(router);app.mount(fixture);await wait(()=>fixture.querySelector('.search-result'))
 fixture.querySelector('.search-result input').click();const question=fixture.querySelector('textarea');question.value='My original question';question.dispatchEvent(new Event('input',{bubbles:true}));await nextTick()
 button('预览发送内容').click();await wait(()=>fixture.querySelector('.answer-preview'))
 assert(fixture.querySelector('.answer-preview').textContent.includes('8192 Tokens'),'Kimi generation budget is shown before consent')
 assert(executions===0,'Creating a preview does not call the model')
 fixture.querySelector('.answer-preview input').click();await nextTick();button('确认并提问').click();await wait(()=>rejectAnswer)
 assert(!fixture.querySelector('.answer-preview'),'The one-shot confirmation is removed while a request is executing')
 assert(fixture.textContent.includes('模型正在生成回答'),'Slow generation displays a clear waiting status')
 assert(button('预览发送内容').getAttribute('aria-disabled')==='true','A pending generation cannot be submitted again')
 rejectAnswer(new ApiClientError('PERSONAL_AI_TIMEOUT','等待模型回复超时，请先查看使用记录',504,'trace-synthetic'))
 await wait(()=>fixture.querySelector('.answer-error'))
 assert(fixture.querySelector('.research-answer .answer-error'),'AI failure is displayed next to the question')
 assert(!fixture.querySelector('.request-failure'),'AI failure does not show the unrelated reload-sources action')
 assert(fixture.querySelector('textarea').value==='My original question'&&fixture.querySelector('.source-chips button'),'Question and source selection survive failure')
 assert(fixture.querySelector('.answer-error a[href="/app/settings?section=usage"]'),'Failure links directly to the usage record')
 assert(fixture.querySelector('.answer-error').textContent.includes('trace-synthetic'),'Sanitized request identifier is available for diagnosis')
 assert(!button('确认并提问')&&executions===1,'Failed calls are not retried or replayed with the consumed confirmation')
 button('搜索').click();await wait(()=>fixture.querySelector('.search-result'))
 assert(!!fixture.querySelector('.answer-error')&&executions===1,'Reloading sources neither conceals the AI error nor repeats a paid call')
 button('预览发送内容').click();await wait(()=>fixture.querySelector('.answer-preview'))
 assert(!fixture.querySelector('.answer-error')&&button('确认并提问').disabled,'A new preview clears the old error but requires fresh consent')
 assert(executions===1&&previews===2,'Previewing again is still separate from sending')
 fixture.querySelector('.answer-preview input').click();await nextTick();button('确认并提问').click();await wait(()=>executions===2)
 rejectAnswer(new ApiClientError('NETWORK_UNAVAILABLE','Generic write recovery',0));await wait(()=>fixture.querySelector('.answer-error'))
 assert(fixture.querySelector('.answer-error').textContent.includes('系统不会自动重发'),'Network ambiguity uses AI-specific guidance')
 assert(!fixture.querySelector('.answer-error').textContent.includes('Generic write recovery'),'Generic reload advice is not shown for an uncertain provider outcome')
 failPreview=true;button('预览发送内容').click();await wait(()=>fixture.querySelector('.answer-error strong')?.textContent==='暂时无法生成预览')
 assert(!fixture.querySelector('.answer-error a[href="/app/settings?section=usage"]'),'Local preview failure does not imply a billed model call')
 assert(executions===2&&!!fixture.querySelector('.answer-error a[href="/app/settings?section=ai"]'),'Missing configuration is explained without another execution')
 document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${passed.length} assertions)\n`+passed.join('\n')
}catch(e){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=e.stack}
finally{app?.unmount();authStore.state.user=null}
