import { createApp, nextTick } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import ModelsView from '../views/ModelsView.vue'
import PersonalAiProviders from '../components/PersonalAiProviders.vue'
import { authStore } from '../stores/auth'
import { personalApi } from '../api/personal'
import { setUiLanguage } from '../i18n'
const checks=[],fixture=document.getElementById('fixture')
const assert=(ok,label)=>{if(!ok)throw Error(label);checks.push(label)}
let app,requests=0
window.fetch=async()=>{requests++;throw Error('Catalog must not request private models or credentials')}
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{template:'<div />'}}]})
try {
 await router.push('/models');await router.isReady();authStore.state.user=null
 app=createApp(ModelsView).use(router);app.mount(fixture);await nextTick()
 assert(fixture.querySelectorAll('.provider-card').length===9,'Nine distinct provider families replace repeated task models')
 assert(!fixture.textContent.includes('车牌')&&!fixture.textContent.includes('票据'),'Catalog headings do not expose legacy task-specific model titles')
 assert(requests===0,'Public catalog makes no protected or external API calls')
 const kimi=fixture.querySelector('.provider-card a')
 assert(kimi.getAttribute('href').includes('redirect=')&&decodeURIComponent(kimi.getAttribute('href')).includes('provider=MOONSHOT'),'Signed-out selection preserves the chosen provider through sign-in')
 authStore.state.user={id:123,username:'test',displayName:'Test',permissions:[],roleCode:'VIEWER'};await nextTick()
 assert(!fixture.querySelector('.provider-select')&&fixture.querySelector('[role=status]'),'Users lacking AI management permission can browse but cannot configure')
 authStore.state.user={...authStore.state.user,permissions:['personal-ai:manage']};await nextTick()
 assert(fixture.querySelector('.provider-card a').getAttribute('href')==='/app/settings?section=ai&provider=MOONSHOT','Authorized selection opens the matching configuration')
 await setUiLanguage('en',false);await nextTick();assert(fixture.textContent.includes('Choose your AI')&&fixture.textContent.includes('Qwen'),'Provider catalog supports English without translating user content');app.unmount();app=null
 personalApi.providers=async()=>[{provider:'OPENAI',displayName:'OpenAI',baseUrls:['https://api.openai.com/v1']},{provider:'MOONSHOT',displayName:'Kimi',baseUrls:['https://api.moonshot.cn/v1']}]
 personalApi.settings=async()=>[]
 await router.push('/app/settings?section=ai&provider=MOONSHOT');app=createApp(PersonalAiProviders).use(router);app.mount(fixture);await new Promise(r=>setTimeout(r,10));await nextTick()
 assert(fixture.querySelector('select').value==='MOONSHOT','Settings validates and applies a recognized provider query')
 assert(!fixture.querySelector('input[type=password]')?.value,'Selecting a service never fills an API key');app.unmount();app=null
 await router.push('/app/settings?section=ai&provider=INVALID');app=createApp(PersonalAiProviders).use(router);app.mount(fixture);await new Promise(r=>setTimeout(r,10));await nextTick()
 assert(fixture.querySelector('select').value==='OPENAI','An unrecognized provider falls back to the server catalog')
 document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${checks.length} assertions)\n${checks.join('\n')}`
}catch(e){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=e.stack;throw e}
finally{app?.unmount();authStore.state.user=null;await setUiLanguage('zh',false)}
