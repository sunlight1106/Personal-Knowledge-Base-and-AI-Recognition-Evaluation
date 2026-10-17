import {createApp,h,nextTick} from 'vue'
import {createRouter,createMemoryHistory,RouterView} from 'vue-router'
import CodeLabView from '../views/CodeLabView.vue'
import {tokenStorage} from '../api/client.ts'
import {authStore} from '../stores/auth.ts'
const fixture=document.getElementById('fixture'),checks=[]
const assert=(ok,label)=>{if(!ok)throw new Error(label);checks.push(label)}
const wait=async fn=>{for(let i=0;i<150;i++){await new Promise(r=>setTimeout(r,5));await nextTick();if(fn())return}throw Error('Code Lab did not settle')}
const button=text=>[...fixture.querySelectorAll('button')].find(b=>b.textContent.trim().replace(/\s*↗$/,'')===text)
const set=async(el,value)=>{el.value=value;el.dispatchEvent(new Event('input',{bubbles:true}));await nextTick()}
const code='class Test { public static void main(String[] args) { int n = 8; } }'
let app,requests=[],held,delay=false
const reply=data=>new Response(JSON.stringify({success:true,data}),{headers:{'Content-Type':'application/json'}})
window.fetch=async(url,init={})=>{
 if(!String(url).includes('/code-lab/runs'))throw Error('Unexpected request')
 if(init.method!=='POST')return reply([])
 const input=JSON.parse(init.body);requests.push(input)
 const result=input.entry==='main'?{language:'java',status:'completed',steps:[{line:1,event:'return',frames:[{name:'main',variables:{n:8}}],outputLength:0}],output:'',result:null}:{language:'java',status:'error',steps:[],error:{line:1,message:'找不到指定入口函数。'}}
 const response=()=>reply({...input,id:'synthetic-run',result})
 if(delay)return new Promise(resolve=>{held=()=>resolve(response())})
 return response()
}
try{
 tokenStorage.set('synthetic-code-lab');authStore.state.user={id:991,permissions:['code-lab:use']}
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:CodeLabView}]})
 await router.push('/');await router.isReady();app=createApp({render:()=>h(RouterView)}).use(router);app.mount(fixture);await nextTick()
 const source=fixture.querySelector('#code-source'),entry=fixture.querySelector('.lab-inputs input'),args=fixture.querySelectorAll('.lab-inputs input')[1]
 await set(source,code)
 assert(!!button('使用 main 和空参数'),'Pasting Java main with stale sort setup offers a correction')
 assert(entry.value==='sort'&&args.value==='[[5, 2, 8, 1]]'&&requests.length===0,'Pasting does not silently change parameters or run code')
 button('运行并可视化').click();await wait(()=>fixture.querySelector('.lab-status')?.textContent.includes('执行中断'))
 assert(!fixture.querySelector('.lab-replay-notice'),'An unchanged failed run is not marked stale')
 button('使用 main 和空参数').click();await nextTick()
 assert(entry.value==='main'&&args.value==='[]'&&source.value===code,'Correction sets main and empty arguments without editing source')
 assert(requests.length===1&&!button('使用 main 和空参数'),'Correction needs an explicit run and clears its hint')
 assert(!!fixture.querySelector('.lab-replay-notice'),'Old failure is labeled as an earlier result after correction')
 delay=true;button('运行并可视化').click();button('运行并可视化').click();await wait(()=>held)
 assert(requests.length===2&&source.disabled&&entry.disabled&&args.disabled,'Repeated clicks submit once and lock run inputs')
 held();await wait(()=>fixture.querySelector('.lab-status')?.textContent.includes('执行完成'))
 assert(requests.at(-1).language==='java'&&requests.at(-1).entry==='main'&&requests.at(-1).arguments.length===0,'Corrected payload is sent to the execution API')
 assert(!fixture.querySelector('.lab-replay-notice'),'Completed run matches the current editor')
 assert(fixture.querySelector('.lab-output').textContent.includes('没有打印输出或返回值'),'A program without printing explains its empty output')
 await set(args,'[[5,2,8,1]]');assert(fixture.querySelector('.lab-entry-hint').textContent.includes('参数与当前输入不匹配'),'Stale sort arguments are detected even with main selected')
 await set(args,'[["hello"]]');assert(!fixture.querySelector('.lab-entry-hint'),'Valid Java string arguments are preserved')
 await set(source,code+' static int sort(int n) { return n; }');await set(entry,'sort');await set(args,'[3]')
 assert(!fixture.querySelector('.lab-entry-hint'),'An explicit helper function is kept when main also exists')
 document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${checks.length} assertions)\n`+checks.join('\n')
}catch(e){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=e.stack}
finally{app?.unmount();authStore.state.user=null;tokenStorage.clear()}
