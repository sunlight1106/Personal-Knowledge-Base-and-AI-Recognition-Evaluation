import {createApp,h,nextTick,ref} from 'vue'
import VocabularyRecallCard from '../components/VocabularyRecallCard.vue'
import PersonalAiDiagnostics from '../components/PersonalAiDiagnostics.vue'
import PersonalAiProviders from '../components/PersonalAiProviders.vue'
import GroupInvitations from '../components/GroupInvitations.vue'
import {diffLines,mergeDraft} from '../lib/noteDiff'
import {renderNote} from '../lib/markdown'
import {tokenStorage} from '../api/client'
const fixture=document.getElementById('fixture'),passed=[]
const assert=(ok,label)=>{if(!ok)throw Error(label);passed.push(label)}
const wait=async test=>{for(let i=0;i<100;i++){await new Promise(r=>setTimeout(r,5));await nextTick();if(test())return}throw Error('Timed out')}
const button=text=>[...fixture.querySelectorAll('button')].find(b=>b.textContent.includes(text))
const envelope=data=>new Response(JSON.stringify({success:true,data}),{headers:{'Content-Type':'application/json'}})
let app;const requests=[]
try {
 tokenStorage.set('synthetic-completion-session')
 assert(mergeDraft('a\nb\nc','A\nb\nc','a\nb\nC')==='A\nb\nC','Disjoint edits merge without losing either change')
 assert(mergeDraft('a\nb','a\nB','a\nother')===null,'Overlapping edits require manual review')
 assert(diffLines('a\nb','a\nB').some(l=>l.kind==='remove'&&l.text==='b'),'Version diff marks removed text')
 assert(diffLines('a\nb','a\nB').some(l=>l.kind==='add'&&l.text==='B'),'Version diff marks added text')
 assert(!renderNote('![x](https://example.invalid/track.png)<script>alert(1)</script>').includes('<img'),'Remote images and scripts remain blocked')
 const img='10000000-0000-0000-0000-000000000001';assert(renderNote(`![image](attachment:${img})`).includes('data-note-attachment'),'Owned attachment markers survive sanitization without a remote image URL')
 window.fetch=async(url,init={})=>{requests.push({url:String(url),body:init.body});if(String(url).includes('/diagnostics/preview'))return envelope({token:'synthetic-consent',model:'synthetic-model',endpoint:'https://example.invalid',request:'Fixed short request only'});if(String(url).includes('/diagnostics/execute'))return envelope({success:true,code:'CONNECTED',message:'Connected synthetic model',milliseconds:10,models:[],usageStatus:'SAVED'});if(String(url).endsWith('/workspaces/invitations'))return envelope([{id:'synthetic-invite',groupId:1,groupName:'Synthetic group',role:'MEMBER'}]);if(String(url).includes('/decision'))return envelope(null);throw Error('Unexpected request')}
 const originalFetch=window.fetch
 app=createApp({render:()=>h(VocabularyRecallCard,{question:{id:'listening',term:'work',ipa:'/wɜːk/',practiceKind:'LISTENING',introduced:false,hintLevel:0,prompt:'听一听，写下你听到的单词'},answer:null,busy:false})});app.mount(fixture);await nextTick()
 assert(!fixture.textContent.includes('work')&&!fixture.textContent.includes('wɜːk'),'Dictation does not reveal the word or its phonetics')
 assert(fixture.querySelector('[aria-label="播放本题语音"]'),'Dictation accessible label does not reveal the answer')
 assert(!requests.length,'Dictation does not request a lesson before answering');app.unmount();fixture.replaceChildren()
 app=createApp({render:()=>h(PersonalAiDiagnostics,{setting:{provider:'DEEPSEEK',model:'synthetic-model',revision:1,configured:true,enabled:true},disabled:false})});app.mount(fixture);button('测试当前模型').click();await wait(()=>fixture.textContent.includes('Fixed short request'))
 assert(!requests.some(r=>r.url.endsWith('/execute')),'Connection test only previews before explicit confirmation')
 assert(button('确认检查').disabled,'Sending remains disabled without confirmation')
 const consent=fixture.querySelector('input[type=checkbox]');consent.checked=true;consent.dispatchEvent(new Event('change',{bubbles:true}));await nextTick();button('确认检查').click();await wait(()=>fixture.textContent.includes('Connected synthetic'))
 const outbound=JSON.parse(requests.find(r=>r.url.endsWith('/execute')).body);assert(outbound.confirmed&&outbound.token==='synthetic-consent'&&Object.keys(outbound).length===2,'Execution submits only the one-use consent token')
 for(const usageStatus of ['NOT_SENT','NOT_APPLICABLE','UNCONFIRMED']){
  window.fetch=async url=>String(url).includes('/preview')?envelope({token:'synthetic-consent',model:'synthetic-model',endpoint:'https://example.invalid',request:'Fixed short request only'}):envelope({success:false,code:'SYNTHETIC',message:'Synthetic failure',milliseconds:5,models:[],usageStatus})
  button('查询模型目录').click();await wait(()=>fixture.textContent.includes('Fixed short request'))
  const confirm=fixture.querySelector('input[type=checkbox]');confirm.checked=true;confirm.dispatchEvent(new Event('change',{bubbles:true}));await nextTick();button('确认检查').click();await wait(()=>fixture.textContent.includes('Synthetic failure'))
  assert(fixture.textContent.includes('请查看供应商账单')===(usageStatus==='UNCONFIRMED'),usageStatus+' only warns about billing when storage is unconfirmed')
  assert(fixture.textContent.includes('未调用供应商模型')===(usageStatus==='NOT_SENT'),usageStatus+' correctly explains preflight rejection')
 }
 window.fetch=originalFetch
 app.unmount();fixture.replaceChildren();requests.length=0
 let savedProfile={provider:'MOONSHOT',model:'synthetic-model',baseUrl:'https://api.moonshot.ai/v1',enabled:true,configured:true,revision:1},savedBody
 window.fetch=async(url,init={})=>{
  const path=String(url);requests.push({url:path,body:init.body})
  if(path.endsWith('/providers'))return envelope([{provider:'MOONSHOT',displayName:'Moonshot Kimi',baseUrls:['https://api.moonshot.ai/v1','https://api.moonshot.cn/v1'],remoteEnabled:true,customEndpointAllowed:false}])
  if(path.endsWith('/settings'))return envelope([savedProfile])
  if(path.endsWith('/settings/MOONSHOT')&&init.method==='PUT'){savedBody=JSON.parse(init.body);savedProfile={...savedProfile,...savedBody,revision:2};return envelope(savedProfile)}
  if(path.endsWith('/diagnostics/preview'))return envelope({token:'synthetic-region-consent',request:'Query saved region only',model:savedProfile.model,endpoint:savedProfile.baseUrl})
  if(path.endsWith('/diagnostics/execute'))return envelope({success:true,code:'MODELS_RECEIVED',message:'Synthetic model catalog',milliseconds:5,models:['kimi-k3','kimi-k2.6'],usageStatus:'NOT_APPLICABLE'})
  throw Error('Unexpected region request')
 }
 app=createApp({render:()=>h(PersonalAiProviders)});app.mount(fixture);await wait(()=>button('查询模型目录'))
 const endpoint=()=>fixture.querySelectorAll('select')[1]
 assert([...endpoint().options].some(o=>o.value==='https://api.moonshot.cn/v1'&&o.textContent.includes('国内开放平台')),'Moonshot endpoint labels identify the key origin')
 button('查询模型目录').click();await wait(()=>fixture.textContent.includes('Query saved region only'))
 endpoint().value='https://api.moonshot.cn/v1';endpoint().dispatchEvent(new Event('change',{bubbles:true}));await nextTick()
 assert(!button('确认检查')&&!button('查询模型目录')&&fixture.textContent.includes('未保存的修改'),'Changing region removes old consent and requires saving before diagnostics')
 assert(!requests.some(r=>r.url.endsWith('/execute')),'Editing the endpoint never sends a provider request')
 button('取消更改').click();await nextTick()
 assert(endpoint().value==='https://api.moonshot.ai/v1'&&button('查询模型目录')&&!button('确认检查'),'Cancel restores the saved endpoint without reviving old consent')
 endpoint().value='https://api.moonshot.cn/v1';endpoint().dispatchEvent(new Event('change',{bubbles:true}));await nextTick()
 fixture.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await wait(()=>savedBody&&button('查询模型目录'))
 assert(savedBody.baseUrl==='https://api.moonshot.cn/v1'&&!Object.hasOwn(savedBody,'apiKey'),'Saving a region change preserves the existing secret')
 button('查询模型目录').click();await wait(()=>fixture.textContent.includes('Query saved region only'))
 assert(fixture.querySelector('.diagnostic-preview code').textContent==='https://api.moonshot.cn/v1','New diagnostic preview uses the saved China endpoint')
 const catalogConsent=fixture.querySelector('.diagnostic-preview input[type=checkbox]');catalogConsent.checked=true;catalogConsent.dispatchEvent(new Event('change',{bubbles:true}));await nextTick();button('确认检查').click();await wait(()=>fixture.querySelector('[aria-label="使用模型 kimi-k3"]'))
 assert(fixture.querySelector('details').open,'Returned model catalog is expanded for direct selection')
 const savesBeforeSelection=requests.filter(r=>r.url.endsWith('/settings/MOONSHOT')).length
 fixture.querySelector('[aria-label="使用模型 kimi-k3"]').click();await nextTick()
 const modelInput=()=>fixture.querySelector('input[aria-describedby="personal-ai-model-hint"]')
 assert(modelInput().value==='kimi-k3'&&fixture.textContent.includes('未保存的修改'),'Selecting a returned model fills the draft and requires an explicit save')
 assert(requests.filter(r=>r.url.endsWith('/settings/MOONSHOT')).length===savesBeforeSelection&&requests.filter(r=>r.url.endsWith('/execute')).length===1,'Selecting a model neither saves automatically nor generates content')
 fixture.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await wait(()=>savedProfile.model==='kimi-k3'&&button('查询模型目录'))
 assert(savedBody.model==='kimi-k3'&&!Object.hasOwn(savedBody,'apiKey'),'Saving a selected model keeps the encrypted key untouched')
 const savesBeforeInvalid=requests.filter(r=>r.url.endsWith('/settings/MOONSHOT')).length
 for(const wrongModel of ['ak-synthetic-record-id','sk-synthetic-secret']){
  modelInput().value=wrongModel;modelInput().dispatchEvent(new Event('input',{bubbles:true}));await nextTick()
  fixture.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await nextTick()
  assert(modelInput().getAttribute('aria-invalid')==='true'&&requests.filter(r=>r.url.endsWith('/settings/MOONSHOT')).length===savesBeforeInvalid,wrongModel.startsWith('ak-')?'Key record IDs cannot be saved as a model':'Secret keys cannot be saved as a model')
 }
 app.unmount();fixture.replaceChildren();savedProfile={...savedProfile,model:'ak-synthetic-old-record'}
 app=createApp({render:()=>h(PersonalAiProviders)});app.mount(fixture);await wait(()=>button('查询模型目录'))
 assert(button('测试当前模型').disabled&&!button('查询模型目录').disabled,'An existing wrong model blocks generation while leaving catalog recovery available')
 app.unmount();fixture.replaceChildren();requests.length=0;window.fetch=originalFetch
 app=createApp({render:()=>h(GroupInvitations)});app.mount(fixture);await wait(()=>fixture.textContent.includes('Synthetic group'))
 assert(!requests.some(r=>r.url.includes('/decision')),'Displaying an invitation does not silently join the group')
 button('拒绝').click();await wait(()=>requests.some(r=>r.url.includes('/decision')));assert(JSON.parse(requests.find(r=>r.url.includes('/decision')).body).accept===false,'Rejecting an invitation sends an explicit negative decision')
}finally{app?.unmount();fixture.replaceChildren();tokenStorage.clear()}
document.getElementById('results').textContent='PASS '+passed.length+' assertions\n'+passed.join('\n')
document.documentElement.dataset.result='passed'
