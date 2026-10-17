import {mathMarkup,mathLatex} from './mathNotation'
import {replayMatchesEditor,type Language,type Run} from './codeLab'
export interface CodeSnapshot {source:string;entry:string;args:string;language:Language}
export const snapshotKey=(value:CodeSnapshot)=>JSON.stringify([value.source,value.entry,value.args,value.language])
export function matchingRun(value:CodeSnapshot,run?:Run){return run&&replayMatchesEditor(run,value.source,value.entry,value.args,value.language)?run:undefined}
const plain=(s:string)=>s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/[\\`*_{}[\]()#+.!|~-]/g,'\\$&')
export function fence(value:string,language='text'){
  const ticks='`'.repeat(Math.max(3,...(value.match(/`+/g)||[]).map(s=>s.length+1)))
  return `${ticks}${language}\n${value}\n${ticks}`
}
export function codeReport(snapshot:CodeSnapshot,options:{run?:Run;title:string;memo?:string;step?:number;note?:boolean;english?:boolean}){
  const {title,memo,step=0,note=false,english=false}=options,run=matchingRun(snapshot,options.run)
  const t=(zh:string,en:string)=>english?en:zh
  const equation=(text:string)=>note?mathMarkup(text,true):`$$\n${mathLatex(text)}\n$$`
  const parts=[`# ${plain(title)}`,t('## 代码与输入','## Code and input'),
    `${t('语言','Language')}: ${run?.result.language||snapshot.language} · ${t('入口','Entry')}: ${plain(snapshot.entry||t('自动 / 顶层','Auto / top level'))}`,
    fence(snapshot.source,run?.result.language||(snapshot.language==='auto'?'text':snapshot.language)),
    t('### 调用参数','### Arguments'),fence(snapshot.args,'json')]
  if(memo?.trim())parts.push(t('## 学习记录','## Learning notes'),plain(memo))
  if(!run){parts.push(t('> 代码草稿，尚未附加对应的执行结果。','> Code draft; no matching execution result attached.'));return parts.join('\n\n')+'\n'}
  const trace=run.result,analysis=trace.analysis
  parts.push(t('## 本次执行','## Recorded execution'),
    `${t('状态','Status')}: ${{completed:t('执行完成','Completed'),error:t('执行中断','Error'),limited:t('达到限制','Limit reached')}[trace.status]} · ${t('步骤','Steps')}: ${trace.steps.length} · ${t('解释器求值','Interpreter evaluations')}: ${trace.operations||0}`,
    `${t('记录 ID','Run ID')}: ${run.id}${run.createdAt?' · '+plain(run.createdAt):''}`)
  if(trace.error)parts.push(fence(`L${trace.error.line||'?'}: ${trace.error.message}`))
  parts.push(t('### 输出与返回值','### Output and return value'),fence((trace.output||'')+(trace.result!=null?'\nReturn: '+JSON.stringify(trace.result):'')||t('无打印输出或返回值。','No printed output or return value.')))
  if(analysis?.status==='derived'){
    parts.push(t('## 数学分析','## Mathematical analysis'),equation(analysis.complexity||'O(1)'),
      t('循环增长模型；按标量操作为常数成本计数，不代表运行耗时或 CPU 指令数。','Loop growth model with constant-cost scalar operations, not elapsed time or CPU instructions.'))
    if(analysis.size)parts.push(`n = ${analysis.size.value} · ${plain(analysis.size.name)} · n ≥ ${analysis.minimum||0}`)
    if(analysis.size?.origin==='literal')parts.push(t('规模在源码中为常量；这里讨论改变它后的程序族。保持该常量不变时，固定规模程序为 O(1)。','The size is a literal in the source. This model varies that literal; the fixed-size program is O(1).'))
    if(analysis.partial)parts.push(t('执行未完成：实测为部分计数，公式为完整运行模型。','Execution is incomplete: measurements are partial; formulas describe a full run.'))
    for(const record of analysis.records.slice(0,20)){
      const kinds={loop:t('循环体执行','Loop body'),condition:t('循环条件判断','Loop condition'),update:t('循环变量更新','Counter update'),branch:t('分支条件判断','Branch condition')}
      parts.push(`### L${record.line} · ${kinds[record.kind]}`,equation('T(n) = '+(record.summation===record.formula?record.formula:`${record.summation} = ${record.formula}`)),
        `${t('当前规模代入','Predicted at current size')}: ${record.predicted} · ${t('实际记录','Measured')}: ${record.measured} · ${record.verification==='matched'?t('计数一致','Matched'):t('部分执行','Partial')}`)
    }
    if(analysis.records.length>20)parts.push(t('此笔记保留前 20 项公式；完整数据可导出回放 JSON。','This note includes the first 20 formulas. Export replay JSON for all records.'))
    for(const item of analysis.notes||[])parts.push(`L${item.line} · `+t(item.kind==='always-false'?'该分支条件始终不成立。':'分支体次数取决于数据，不能合并为固定公式。',item.kind==='always-false'?'This branch condition is always false.':'The branch body count depends on input data.'))
  }else parts.push(t('## 数学分析','## Mathematical analysis'),t('尚未建立可靠的通用计数公式。','No reliable general count formula has been derived.'))
  const frame=trace.steps[step]
  if(frame)parts.push(`## ${t('观察位置','Observation')} · ${step+1} / ${trace.steps.length} · L${frame.line}`,fence(JSON.stringify(frame.frames,null,2),'json'))
  parts.push(t('## 我的结论','## My conclusions'),t('在这里记录算法思路、边界情况和下一步改进。','Record the algorithm idea, edge cases and next improvements here.'))
  return parts.join('\n\n')+'\n'
}
// Stable creation IDs let the existing notes API safely retry a lost response.
export async function runNoteId(runId:string){
  const bytes=new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode('code-lab-note:'+runId))).slice(0,16)
  bytes[6]=(bytes[6]&15)|0x80;bytes[8]=(bytes[8]&63)|0x80
  const s=Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('')
  return `${s.slice(0,8)}-${s.slice(8,12)}-${s.slice(12,16)}-${s.slice(16,20)}-${s.slice(20)}`
}
