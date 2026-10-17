import {createApp,h,nextTick,ref} from 'vue'
import CodeLabAnalysis from '../components/CodeLabAnalysis.vue'
const fixture=document.getElementById('fixture'),checks=[]
const assert=(ok,label)=>{if(!ok)throw Error(label);checks.push(label)}
const analysis=ref({version:1,status:'derived',size:{name:'n',value:8,symbol:'n',origin:'literal'},minimum:1,maximum:512,complexity:'Θ(n^2)',degree:2,notes:[{line:6,kind:'always-false'}],records:[
 {id:'a',line:4,kind:'loop',summation:'n − 1',formula:'n − 1',coefficients:[-1,1],measured:7,predicted:7,degree:1,verification:'matched'},
 {id:'b',line:5,kind:'loop',summation:'Σ(i=1…n − 1) [n − i]',formula:'n(n − 1)/2',coefficients:[0,-.5,.5],measured:28,predicted:28,degree:2,verification:'matched'}
]})
let app
try{
 app=createApp({render:()=>h(CodeLabAnalysis,{analysis:analysis.value})});app.mount(fixture);await nextTick()
 assert(fixture.textContent.includes('n(n − 1)/2')&&fixture.textContent.includes('Θ(n²)'),'Triangular formula and derived order are visible')
 assert(fixture.querySelector('output').textContent.includes('28'),'Current size n=8 gives 28 iterations')
 assert(fixture.textContent.includes('保持常量不变')&&fixture.textContent.includes('始终不成立'),'Fixed-input and unreachable-branch explanations are present')
 const range=fixture.querySelector('input[type=range]');range.value='16';range.dispatchEvent(new Event('input',{bubbles:true}));await nextTick()
 assert(fixture.querySelector('output').textContent.includes('120'),'Changing exploration size evaluates the formula without execution')
 const number=fixture.querySelector('input[type=number]');number.value='-3';number.dispatchEvent(new Event('input',{bubbles:true}));await nextTick()
 assert(fixture.querySelector('output').textContent.includes('T(1)'),'Exploration input is clamped to the valid domain')
 const chart=fixture.querySelector('.math-chart');chart.getBoundingClientRect=()=>({left:0,width:660})
 const move=new Event('pointermove');Object.defineProperty(move,'clientX',{value:330});chart.dispatchEvent(move);await nextTick()
 assert(!fixture.querySelector('.math-tooltip'),'Chart does not flash a tooltip immediately')
 await new Promise(r=>setTimeout(r,1050));await nextTick()
 assert(!!fixture.querySelector('.math-tooltip'),'Chart tooltip appears after one second')
 chart.dispatchEvent(new Event('pointerleave'));await nextTick();assert(!fixture.querySelector('.math-tooltip'),'Leaving the chart clears its tooltip')
 fixture.querySelector('tbody button').click();await nextTick()
 assert(fixture.querySelector('.math-formula').textContent.includes('n − 1'),'Formula rows select the corresponding curve')
 analysis.value={...analysis.value,partial:true,records:analysis.value.records.map(r=>({...r,verification:'partial'}))};await nextTick()
 assert(fixture.textContent.includes('本次执行未完成'),'Partial measured counts are labeled separately from full formula counts')
 analysis.value={version:1,status:'unavailable',reason:'EARLY_EXIT',records:[]};await nextTick()
 assert(!fixture.querySelector('svg')&&fixture.textContent.includes('break'),'Unsupported control flow shows a reason without inventing a graph')
 analysis.value=undefined;await nextTick()
 assert(fixture.textContent.includes('重新运行代码'),'Old saved runs explain how to obtain new analysis')
 document.documentElement.dataset.result='passed';document.getElementById('results').textContent=`PASS (${checks.length} assertions)\n`+checks.join('\n')
}catch(e){document.documentElement.dataset.result='failed';document.getElementById('results').textContent=e.stack}
finally{app?.unmount()}
