import {createApp,h,nextTick} from 'vue'
import {createRouter,createMemoryHistory,RouterView} from 'vue-router'
import NotesView from '../views/NotesView.vue'
import MailboxView from '../views/MailboxView.vue'
import ImagesView from '../views/ImagesView.vue'
import GettingStarted from '../components/GettingStarted.vue'
import OperationsPanel from '../components/OperationsPanel.vue'
import WorkspaceTasks from '../components/WorkspaceTasks.vue'
import {api,tokenStorage} from '../api/client'
import {workflowApi} from '../api/workflows'
import {authStore} from '../stores/auth'
import {setUiLanguage} from '../i18n'
const fixture=document.getElementById('fixture'),checks=[]
const assert=(value,name)=>{if(!value)throw new Error(name);checks.push(name)}
const wait=async predicate=>{for(let i=0;i<240;i++){await new Promise(r=>setTimeout(r,5));await nextTick();if(predicate())return}throw new Error('Timed out')}
const button=text=>[...fixture.querySelectorAll('button')].find(n=>n.textContent.includes(text))
const envelope=data=>new Response(JSON.stringify({success:true,data}),{headers:{'Content-Type':'application/json'}})
const note=id=>({id,title:'Entry '+id,library:'English',excerpt:'Private draft',status:{code:'DRAFT',label:'草稿'},tags:[],contentFormat:'MARKDOWN',updatedAt:'2026-10-15T00:00:00Z'})
let app,guideCalls=0,lastTask='',observed=[],previews=0
const revoked=[];URL.createObjectURL=()=> 'blob:synthetic';URL.revokeObjectURL=url=>revoked.push(url)
globalThis.IntersectionObserver=class{constructor(callback){this.callback=callback;observed.push(this)}observe(node){this.node=node}unobserve(){}disconnect(){this.disconnected=true}}
window.fetch=async url=>{const path=String(url)
 if(path.includes('/workspace/getting-started')){guideCalls++;return envelope([{id:'note',title:'写下第一篇笔记',description:'记录一个想法，也可以从模板开始。',url:'/app/notes/new',completed:false}])}
 if(path.includes('/operations'))return envelope({checkedAt:'2026-10-15T00:00:00Z',requests:30,errors:0,averageMs:80,p95Ms:100,freeBytes:2147483648,totalBytes:4294967296,failedJobs:0,alerts:[]})
 if(path.includes('/workspace-jobs?')){lastTask=path;return envelope([])}
 throw Error('Unexpected synthetic request '+path)
}
async function mount(component){app?.unmount();fixture.innerHTML='';const router=createRouter({history:createMemoryHistory(),routes:[{path:'/test',name:'notes',component},{path:'/app/notes/new',name:'note-create',component:{template:'<p>new</p>'}},{path:'/app/notes/:id/edit',name:'note-edit',component:{template:'<p>edit</p>'}},{path:'/app/knowledge',name:'knowledge',component:{template:'<p>knowledge</p>'}},{path:'/:pathMatch(.*)*',component:{template:'<p>other</p>'}}]});await router.push('/test');await router.isReady();app=createApp({render:()=>h(RouterView)}).use(router);app.mount(fixture);await nextTick();return router}
try{
 tokenStorage.set('synthetic-upgrade');authStore.state.user={id:7,roleCode:'RESEARCHER',permissions:['note:read','note:write','research:use','message:read','file:read']};workflowApi.due=async()=>[]
 const requests=[];api.notePage=async p=>{requests.push({...p});return {results:{items:[note(p.page)],total:101},facets:[{library:'English',status:'DRAFT',count:101}]}}
 await mount(NotesView);await wait(()=>fixture.querySelector('.learning-note-row'))
 assert(requests[0].size==='50','Notes request a bounded server page');assert(fixture.textContent.includes('101'),'Full scoped count is shown independently of page size')
 button('下一页').click();await wait(()=>fixture.textContent.includes('Entry 1'));assert(requests.at(-1).page==='1','Next page fetches the server instead of slicing a full list')
 const query=fixture.querySelector('input[type=search]');query.value='needle';query.dispatchEvent(new Event('input',{bubbles:true}));await wait(()=>requests.at(-1).keyword==='needle');assert(requests.at(-1).page==='0','Search starts again at page zero')
 assert(!fixture.querySelector('input[type=checkbox]')?.checked,'Changing pages clears batch selection')
 let release;api.notePage=()=>new Promise(r=>release=r);const sort=fixture.querySelector('select');sort.value='title';sort.dispatchEvent(new Event('change',{bubbles:true}));await wait(()=>release);app.unmount();app=null;release({results:{items:[note('late')],total:1},facets:[]});await nextTick();assert(!fixture.textContent.includes('Entry late'),'Unmounted note responses cannot restore private content')
 let mailPage=0;api.messageDirectory=async()=>[];api.mailPage=async(tab,page)=>{mailPage=page;return {results:{items:[{id:'m'+page,senderName:'Sender',subject:'Message '+page,body:'Preview',read:false,createdAt:'2026-10-15T00:00:00Z',attachments:[],recipients:[]}],total:61},unread:61}};api.message=async id=>({id,subject:'Message detail',body:'Full message',senderId:99,senderName:'Sender',read:true,attachments:[],recipients:[]})
 await mount(MailboxView);await wait(()=>fixture.querySelector('.mail-row'));assert(fixture.querySelector('.mail-list b').textContent==='61','Inbox shows total unread across pages')
 fixture.querySelector('.mail-row').click();await wait(()=>fixture.textContent.includes('Full message'));assert(fixture.querySelector('.mail-list b').textContent==='60','Reading one message decreases unread immediately')
 button('下一页').click();await wait(()=>mailPage===1);assert(mailPage===1,'Mailbox has server page navigation')
 await wait(()=>fixture.querySelector('.mail-row')?.textContent.includes('Message 1'))
 let releaseRefresh;const originalMailPage=api.mailPage;api.mailPage=(tab,page)=>{assert(page===1,'Live refresh stays on the visible page');return new Promise(resolve=>releaseRefresh=resolve)}
 window.dispatchEvent(new Event('pkb:live-update'));await wait(()=>releaseRefresh)
 fixture.querySelector('.mail-row').click();await wait(()=>fixture.querySelector('.mail-list b').textContent==='60')
 api.mailPage=async()=>({results:{items:[],total:61},unread:60});releaseRefresh({results:{items:[],total:61},unread:61});await nextTick();await new Promise(r=>setTimeout(r,15));assert(fixture.querySelector('.mail-list b').textContent==='60','A stale live response cannot resurrect an already read count')
 api.mailPage=originalMailPage
 api.filePage=async()=>({items:[{id:'asset',originalName:'Visible.png',contentType:'image/png',source:'UPLOAD',sizeBytes:200,createdAt:'2026-10-15T00:00:00Z',contentUrl:'/asset'}],total:25});api.blobUrl=async()=>{previews++;return 'blob:asset'}
 await mount(ImagesView);await wait(()=>observed.at(-1)?.node);assert(previews===0,'Media does not download previews outside the viewport')
 observed.at(-1).callback([{target:observed.at(-1).node,isIntersecting:true}]);await wait(()=>previews===1);assert(previews===1,'An intersecting image downloads once')
 app.unmount();app=null;assert(revoked.includes('blob:asset'),'Leaving the media library releases blob URLs')
 await mount(GettingStarted);await wait(()=>fixture.textContent.includes('写下第一篇笔记'));assert(fixture.textContent.includes('0 / 1'),'An available guide step is not marked completed just by showing it')
 button('暂时收起').click();await nextTick();assert(!fixture.querySelector('.guide-steps'),'Guide can be dismissed')
 assert(JSON.parse(localStorage.getItem('pkb:getting-started:7')).hidden===true,'Dismissal is stored only for the current account')
 authStore.state.user={...authStore.state.user,id:8};await wait(()=>guideCalls>=2&&fixture.querySelector('.guide-steps'));assert(fixture.querySelector('.guide-steps'),'Another account has its own guide preference')
 await setUiLanguage('en');await nextTick();assert(fixture.textContent.includes('Get started'),'Guide follows interface language');await setUiLanguage('zh')
 await mount(OperationsPanel);await wait(()=>fixture.textContent.includes('80 ms'));assert(fixture.textContent.includes('100 ms'),'Health shows bounded histogram P95 separately from mean')
 assert(fixture.textContent.includes('当前没有需要处理'),'Healthy state does not fabricate alerts')
 await mount(WorkspaceTasks);await wait(()=>lastTask.includes('status=ALL'));const filter=fixture.querySelector('select');filter.value='FAILED';filter.dispatchEvent(new Event('change',{bubbles:true}));await wait(()=>lastTask.includes('status=FAILED'));assert(lastTask.includes('page=0'),'Failed-task filter restarts from page zero')
 document.getElementById('results').textContent='PASS '+checks.length+'\n'+checks.join('\n');document.documentElement.dataset.result='passed'
}catch(e){document.getElementById('results').textContent='FAIL '+checks.length+': '+(e.stack||e);document.documentElement.dataset.result='failed'}finally{app?.unmount()}
