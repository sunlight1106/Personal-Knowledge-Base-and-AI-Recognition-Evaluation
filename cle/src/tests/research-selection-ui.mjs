// Synthetic sources and AI transport: never calls a model or uses a real key.
import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import ResearchView from '../views/ResearchView.vue'
import { researchApi } from '../api/research'
import { personalApi } from '../api/personal'
import { authStore } from '../stores/auth'
import { setUiLanguage } from '../i18n'

const fixture=document.getElementById('fixture'),passed=[]
const assert=(value,message)=>{if(!value)throw new Error(message);passed.push(message)}
const wait=async check=>{for(let i=0;i<100;i++){await new Promise(r=>setTimeout(r,5));await nextTick();if(check())return}throw new Error('UI timed out')}
const button=text=>[...fixture.querySelectorAll('button')].find(b=>b.textContent.trim()===text)
const selectPage=()=>fixture.querySelector('.research-selection input')
const checked=()=>fixture.querySelectorAll('.search-result input:checked').length
const chips=()=>fixture.querySelectorAll('.source-chips button').length
const hits=Array.from({length:15},(_,i)=>({id:'note'+i,kind:'NOTE',title:'Source '+i,excerpt:'Synthetic text',url:'/notes/'+i,updatedAt:'2026-10-31'}))
const previews=[];let app,resolvePreview,hold=false
try{
 authStore.state.user={id:91,roleCode:'USER',permissions:['research:use','note:read','note:write','personal-ai:use']}
 personalApi.settings=async()=>[{provider:'QWEN',configured:true,enabled:true,model:'synthetic'}]
 researchApi.savedSearches=async()=>[];researchApi.bookmarkKeys=async()=>[]
 researchApi.search=async params=>({items:params.q==='empty'?[]:params.page==='1'?hits.slice(10):hits.slice(0,12),hasMore:params.page!=='1',page:Number(params.page)})
 researchApi.bookmarks=async()=>({items:[hits[0],hits[14]],hasMore:false,page:0})
 researchApi.preview=async(provider,question,sources)=>{
  previews.push(sources.map(s=>s.id));
  const result={previewToken:'synthetic',context:'Exact synthetic source passages',provider:'QWEN',model:'synthetic'}
  return hold?new Promise(resolve=>{resolvePreview=()=>resolve(result)}):result
 }
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{template:'<div />'}}]})
 await router.push('/app/research');await router.isReady();app=createApp(ResearchView);app.use(router);app.mount(fixture)
 await wait(()=>fixture.querySelectorAll('.search-result').length===12)
 assert(button('预览发送内容').getAttribute('aria-disabled')==='true','Empty selection blocks preview')
 assert(fixture.querySelector('[role=tooltip]').textContent==='至少选择一个资料','The send tooltip explains the minimum selection')
 assert(button('预览发送内容').getAttribute('aria-describedby')==='research-send-hint','The disabled action remains focusable with an accessible reason')
 button('预览发送内容').click();await nextTick();assert(previews.length===0,'Clicking the unavailable action cannot send a request')
 selectPage().click();await nextTick()
 assert(checked()===12&&chips()===12,'Select all includes twelve sources, beyond the former eight-source limit')
 assert(selectPage().checked&&!selectPage().indeterminate,'All-selected state matches the current page')
 fixture.querySelector('.search-result input').click();await nextTick()
 assert(chips()===11&&selectPage().indeterminate,'Individual deselection shows a mixed state')
 selectPage().click();await nextTick();assert(chips()===12,'Selecting the rest does not duplicate already selected sources')
 button('下一页').click();await wait(()=>fixture.querySelectorAll('.search-result').length===5)
 assert(checked()===2&&chips()===12,'Pagination preserves selections, including overlapping results')
 selectPage().click();await nextTick();assert(chips()===15,'Selecting another page accumulates unique sources')
 selectPage().click();await nextTick();assert(chips()===10&&checked()===0,'Deselecting a page preserves selections from other pages')
 selectPage().click();await nextTick()
 const question=fixture.querySelector('textarea[aria-label="问题"]');question.value='What connects these sources?';question.dispatchEvent(new Event('input',{bubbles:true}));await nextTick()
 assert(button('预览发送内容').getAttribute('aria-disabled')==='false','A selection, question and provider enable preview')
 hold=true;button('预览发送内容').click();await wait(()=>resolvePreview)
 assert(selectPage().disabled&&button('清空选择').disabled,'Selection controls lock during a pending preview')
 assert([...fixture.querySelectorAll('.search-result input')].every(el=>el.disabled),'Individual selections lock during preview too')
 resolvePreview();await wait(()=>fixture.querySelector('.answer-preview'))
 assert(previews[0].length===15,'Preview sends every selected source')
 const consent=fixture.querySelector('.answer-preview input');consent.click();await nextTick()
 button('清空选择').click();await nextTick()
 assert(chips()===0&&!fixture.querySelector('.answer-preview'),'Clearing all pages invalidates the previous preview and consent')
 assert(fixture.querySelector('[role=tooltip]').textContent==='至少选择一个资料','The minimum-selection hint returns after clearing')
 await router.push('/app/research?tab=bookmarks');await wait(()=>fixture.querySelectorAll('.search-result').length===2)
 selectPage().click();await nextTick();assert(chips()===2,'Bookmarks offer the same bulk-selection behavior')
 await router.push('/app/research?tab=search&q=empty');await wait(()=>fixture.querySelector('.research-empty'))
 assert(selectPage().disabled&&chips()===2,'An empty result page cannot select hidden items and keeps prior choices')
 button('清空选择').click();await nextTick();assert(!fixture.querySelector('.research-selection'),'Empty results with no selections do not show an unusable toolbar')
 await setUiLanguage('en',false);await nextTick()
 assert(fixture.querySelector('[role=tooltip]').textContent==='Select at least one source','The hover hint supports English')
 authStore.state.user={id:91,roleCode:'USER',permissions:['research:use']};await nextTick()
 assert(!fixture.querySelector('.research-send'),'Selection changes do not expose AI actions without permission')
 document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${passed.length} assertions)\n`+passed.join('\n')
}catch(error){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=error.stack}
finally{app?.unmount();authStore.state.user=null}
