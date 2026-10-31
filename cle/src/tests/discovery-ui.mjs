import { createApp,h,nextTick } from 'vue'
import ContactDiscoverySettings from '../components/ContactDiscoverySettings.vue'
import PhoneBinding from '../components/PhoneBinding.vue'
import { authStore } from '../stores/auth'
import { tokenStorage } from '../api/client.ts'
const fixture=document.querySelector('#fixture'),checks=[]
const assert=(ok,label)=>{if(!ok)throw new Error(label);checks.push(label)}
const wait=async fn=>{for(let i=0;i<400;i++){await new Promise(r=>setTimeout(r,5));await nextTick();if(fn())return}throw new Error('Timed out: '+fixture.textContent)}
const envelope=data=>new Response(JSON.stringify({success:true,data}),{headers:{'Content-Type':'application/json'}})
const fill=async(node,value)=>{node.value=value;node.dispatchEvent(new Event('input',{bubbles:true}));await nextTick()}
const submit=async form=>{form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await nextTick()}
let settings={contactNumber:'sample123',discoverable:true,searchByNumber:true,searchByEmail:false,searchByPhone:false,protectedAccount:false}
let phoneState={smsAvailable:false,maskedPhone:null,verified:false},writes=[],duplicate=false,pending=null,app
window.fetch=async(url,init={})=>{
 const path=String(url);const body=init.body?JSON.parse(init.body):null
 if(path.endsWith('/social/discovery')){if(body){writes.push(body);settings={...settings,...body}}return envelope(settings)}
 if(path.endsWith('/social/number')){writes.push(body);if(pending==='wait')return new Promise(resolve=>{pending=()=>resolve(envelope({...settings,contactNumber:'oldresponse'}))});if(duplicate)return new Response(JSON.stringify({success:false,error:{code:'CONTACT_NUMBER_TAKEN',message:'此用户号不可用，请换一个'}}),{status:409,headers:{'Content-Type':'application/json'}});settings.contactNumber=body.contactNumber.toLowerCase();return envelope(settings)}
 if(path.endsWith('/account/phone'))return envelope(phoneState)
 if(path.endsWith('/account/phone/code')){writes.push(body);return envelope(null)}
 if(path.endsWith('/account/phone/confirm')){writes.push(body);phoneState={smsAvailable:true,maskedPhone:'+86****5678',verified:true};return envelope(null)}
 throw new Error('Unexpected route: '+path)
}
try{
 authStore.state.user={id:1,permissions:['contacts:use']};tokenStorage.set('synthetic-discovery')
 app=createApp({render:()=>h(ContactDiscoverySettings)});app.mount(fixture)
 await wait(()=>fixture.querySelector('input[pattern]'))
 assert(fixture.textContent.includes('6–24')&&fixture.querySelectorAll('input[type=checkbox]').length===4,'Unique number rules and separate discovery switches are visible')
 await fill(fixture.querySelector('input[pattern]'),'NewNumber123');await submit(fixture.querySelector('form'));await wait(()=>fixture.textContent.includes('用户号已保存'))
 assert(fixture.querySelector('input[pattern]').value==='newnumber123','Server-normalized number replaces the draft')
 duplicate=true;await fill(fixture.querySelector('input[pattern]'),'TakenNumber');await submit(fixture.querySelector('form'));await wait(()=>fixture.querySelector('[role=alert]'))
 assert(fixture.querySelector('input[pattern]').value==='TakenNumber','Conflicting number preserves editable draft');duplicate=false
 const toggle=fixture.querySelector('input[type=checkbox]');toggle.checked=false;toggle.dispatchEvent(new Event('change',{bubbles:true}));await nextTick();await submit(fixture.querySelectorAll('form')[1]);await wait(()=>fixture.textContent.includes('查找权限已保存'))
 assert(writes.at(-1).discoverable===false&&fixture.querySelectorAll('input[type=checkbox]:disabled').length===3,'Master switch closes all channels without discarding preferences')
 settings={...settings,protectedAccount:true};authStore.state.user={id:2,permissions:['contacts:use','moderation:review']};await wait(()=>fixture.querySelector('fieldset')?.disabled)
 assert(fixture.textContent.includes('个人开关不会解除此限制'),'Protected accounts have an explicit locked state')
 settings={...settings,protectedAccount:false};authStore.state.user={id:3,permissions:['contacts:use']};await wait(()=>!fixture.querySelector('fieldset')?.disabled)
 pending='wait';await fill(fixture.querySelector('input[pattern]'),'latevalue');await submit(fixture.querySelector('form'));await wait(()=>typeof pending==='function');authStore.state.user={id:4,permissions:['contacts:use']};await nextTick();pending();await wait(()=>!fixture.querySelector('input[pattern]')?.disabled)
 assert(!fixture.textContent.includes('用户号已保存')&&fixture.querySelector('input[pattern]').value!=='oldresponse','Late save cannot overwrite another account')
 app.unmount();app=createApp({render:()=>h(PhoneBinding)});app.mount(fixture);await wait(()=>fixture.textContent.includes('手机号绑定暂不可用'))
 assert(fixture.querySelector('input[type=tel]').disabled,'Unconfigured SMS is unavailable rather than pretending to send')
 phoneState={...phoneState,smsAvailable:true};authStore.state.user={id:5,permissions:['contacts:use']};await wait(()=>fixture.querySelector('input[type=tel]')&&!fixture.querySelector('input[type=tel]').disabled)
 await fill(fixture.querySelector('input[type=tel]'),'+8613812345678');await fill(fixture.querySelector('input[type=password]'),'synthetic-only');await submit(fixture.querySelector('form'));await wait(()=>fixture.querySelector('input[pattern]'))
 assert(fixture.querySelector('input[type=password]').value==='','Password is cleared after requesting a code')
 await fill(fixture.querySelector('input[pattern]'),'123456');await submit(fixture.querySelectorAll('form')[1]);await wait(()=>fixture.textContent.includes('手机号已验证并绑定'))
 assert(fixture.textContent.includes('+86****5678')&&!fixture.querySelector('input[pattern]'),'Successful verification shows a masked number and clears the code form')
 document.documentElement.dataset.result="passed";document.querySelector('#results').textContent=`discovery-ui: PASS (${checks.length})`
}finally{app?.unmount();tokenStorage.clear()}
