<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue'
import {uiText} from '@/i18n'
import SelectField from './SelectField.vue'
import {formulaValue,functionPlot,functionSvg,type MathAnalysis} from '@/lib/codeLab'

const props=defineProps<{analysis?:MathAnalysis}>()
const selected=ref(''),size=ref(8),extent=ref(32),hover=ref<number>(),timer=ref<ReturnType<typeof setTimeout>>()
const minimum=computed(()=>props.analysis?.minimum||0)
const records=computed(()=>props.analysis?.records||[])
const record=computed(()=>records.value.find(r=>r.id===selected.value)||records.value[0])
const plot=computed(()=>functionPlot(record.value?.coefficients||[],minimum.value,extent.value))
const value=computed(()=>formulaValue(record.value?.coefficients||[],size.value))
const order=computed(()=>props.analysis?.complexity?.replace(/\^([234])/g,(_,power:string)=>({'2':'²','3':'³','4':'⁴'}[power]||power)))
const labels={loop:'循环体执行',condition:'循环条件判断',update:'循环变量更新',branch:'分支条件判断'}
const reasons:Record<string,string>={
  WHILE:'while 循环的终止方式尚未推导。',CALL_OR_RECURSION:'包含函数调用或递归，尚未建立递推关系。',CALL_COST:'包含尚未建模的内置操作。',BULK_OPERATION:'包含批量操作，不能按一次标量操作计算。',
  CHANGING_BOUND:'循环中可能修改边界或计数变量。',EARLY_EXIT:'包含提前返回、break 或 continue，次数取决于执行路径。',MULTIPLE_SIZES:'涉及多个独立输入规模，暂不简化为单变量公式。',LOOP_FORM:'当前公式分析支持步长为 1 或 −1 的 for / range 循环。',
  BOUND:'循环边界不是当前可证明的线性表达式。',CONDITIONAL_LOOP:'分支会影响循环是否执行。',COUNTER_SHADOW:'循环计数变量存在重名或规模变量冲突。',EMPTY_DOMAIN:'循环区间需要分段讨论，暂不生成统一公式。',OUTSIDE_DOMAIN:'当前输入不在可推导公式的适用区间内。',NEGATIVE_SIZE:'当前规模为负数，暂不生成增长曲线。',
  POLYNOMIAL_LIMIT:'表达式超出当前四次多项式分析范围。',ANALYSIS_LIMIT:'达到符号分析上限。',VERIFICATION:'公式未通过本次运行计数核对，因此不展示。',RUN_ERROR:'请先修复执行错误，再生成数学分析。',UNSUPPORTED:'这段代码暂未建立可靠的计数公式。'
}
function cancelHover(){if(timer.value)clearTimeout(timer.value);timer.value=undefined;hover.value=undefined;candidate=undefined}
let candidate:number|undefined
function inspect(event:PointerEvent){
  const rect=(event.currentTarget as SVGSVGElement).getBoundingClientRect()
  const x=(event.clientX-rect.left)/rect.width*660
  if(x<52||x>608){cancelHover();return}
  const n=Math.max(plot.value.min,Math.min(plot.value.max,Math.round(plot.value.min+(x-52)/556*(plot.value.max-plot.value.min))))
  if(n===candidate)return
  cancelHover();candidate=n;timer.value=setTimeout(()=>{hover.value=n;timer.value=undefined},1000)
}
function setSize(raw:unknown){const n=Number(raw);size.value=Number.isFinite(n)?Math.max(minimum.value,Math.min(plot.value.max,Math.round(n))):minimum.value}
watch(()=>props.analysis,()=>{
  cancelHover();selected.value=records.value.filter(r=>r.kind==='loop').at(-1)?.id||records.value[0]?.id||''
  const current=props.analysis?.size?.value??8
  extent.value=[16,32,64,128,512].find(n=>n>=Math.max(minimum.value+1,current*2))||512
  setSize(current)
},{immediate:true})
watch([selected,extent],()=>{cancelHover();setSize(size.value)})
onBeforeUnmount(cancelHover)
function exportGraph(){
  if(!record.value)return
  const url=URL.createObjectURL(new Blob([functionSvg(record.value,plot.value.min,plot.value.max)],{type:'image/svg+xml'}))
  const a=document.createElement('a');a.href=url;a.download='code-count-function.svg';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
}
const short=(n:number)=>new Intl.NumberFormat(undefined,{notation:n>=100000?'compact':'standard',maximumFractionDigits:1}).format(n)
</script>

<template>
  <section class="math-analysis" :aria-label="uiText('数学分析')">
    <header><div><span class="math-kicker">COUNT / DERIVE / EXPLORE</span><h3>{{uiText('从执行次数，到增长规律')}}</h3></div><strong v-if="analysis?.status==='derived'" class="math-order">{{order}}</strong></header>
    <p v-if="!analysis" class="math-note">{{uiText('这条旧记录没有数学分析，重新运行代码即可生成。')}}</p>
    <p v-else-if="analysis.status==='unavailable'" class="math-note">{{uiText(reasons[analysis.reason||'']||reasons.UNSUPPORTED)}} {{uiText('仍可查看本次实测次数；不会用一条运行记录拟合通用公式。')}}</p>
    <template v-else>
      <p class="math-note">{{uiText('循环增长阶，按标量操作为常数成本建模。')}} {{uiText('不等于实际耗时；输出、内置操作和大整数的内部成本未计入。')}}</p>
      <p v-if="analysis.size" class="math-size-note"><code>n = {{analysis.size.value}}</code><span>{{uiText(analysis.size.origin==='length'?'对应数组长度':'对应规模变量')}} <code>{{analysis.size.name}}</code></span></p>
      <p v-if="analysis.size?.origin==='literal'" class="math-note math-warning">{{uiText('源码把规模写成了常量。这里讨论改变这个常量后的增长；保持常量不变时，这段固定规模程序为 O(1)。')}}</p>
      <p v-if="analysis.partial" class="math-note math-warning">{{uiText('本次执行未完成：实测仅为已执行部分，公式表示满足假设时的完整次数。')}}</p>
      <template v-if="record">
        <label class="math-metric-label">{{uiText('选择计数对象')}}<SelectField><select v-model="selected" class="field-input" :aria-label="uiText('选择计数对象')"><option v-for="item in records" :key="item.id" :value="item.id">L{{item.line}} · {{uiText(labels[item.kind])}}</option></select></SelectField></label>
        <div class="math-proof">
          <span>{{uiText('由循环边界求和')}}</span><code class="math-sum">{{record.summation}}</code>
          <div class="math-formula"><span>T(n) =</span> {{record.formula}}</div>
          <p v-if="record.exampleTerms?.length" class="math-note"><code>{{record.exampleTerms.join(' + ')}} = {{record.predicted}}</code></p>
          <div class="math-check"><span>{{uiText('当前规模代入')}} <b>{{record.predicted}}</b></span><span>{{uiText('实际记录')}} <b>{{record.measured}}</b></span><span :class="{'math-match':record.verification==='matched'}">{{uiText(record.verification==='matched'?'计数一致':'部分执行')}}</span></div>
        </div>
        <div class="math-plot-heading"><h4>{{uiText('次数随规模的变化')}}</h4><button class="button button--ghost" @click="exportGraph">{{uiText('导出函数图')}}</button></div>
        <div class="math-chart-wrap">
          <svg class="math-chart" viewBox="0 0 660 240" role="img" :aria-label="`${uiText('次数随规模的变化')} T(n) = ${record.formula}`" @pointermove="inspect" @pointerleave="cancelHover">
            <g v-for="fraction in [0,.5,1]" :key="fraction"><path :d="`M52 ${plot.y(plot.ceiling*fraction)}H608`" class="math-grid"/><text x="43" :y="plot.y(plot.ceiling*fraction)+4" text-anchor="end">{{short(plot.ceiling*fraction)}}</text></g>
            <text x="52" y="224">{{plot.min}}</text><text x="608" y="224" text-anchor="end">{{plot.max}}</text><text x="628" y="207">n</text><text x="52" y="13">T(n)</text>
            <path :d="`${plot.path} L608,204 L52,204 Z`" class="math-area"/><path :d="plot.path" class="math-curve"/>
            <path :d="`M${plot.x(size)},24V204`" class="math-guide"/><circle :cx="plot.x(size)" :cy="plot.y(value)" r="4.5" class="math-dot"/>
            <g v-if="hover!==undefined" class="math-tooltip" :transform="`translate(${Math.min(450,Math.max(55,plot.x(hover)-65))},30)`"><rect width="160" height="47" rx="7"/><text x="12" y="18">n = {{hover}}</text><text x="12" y="36">T(n) = {{formulaValue(record.coefficients,hover)}}</text></g>
          </svg>
        </div>
        <div class="math-explore"><label>{{uiText('探索规模')}} n<input type="number" :value="size" :min="minimum" :max="plot.max" :aria-label="uiText('探索规模')" @input="setSize(($event.target as HTMLInputElement).value)" /></label><label>{{uiText('绘图上限')}}<SelectField><select v-model.number="extent" class="compact-select" :aria-label="uiText('绘图上限')"><option v-for="n in [16,32,64,128,512].filter(n=>n>minimum)" :key="n" :value="n">{{n}}</option></select></SelectField></label><output aria-live="polite">T({{size}}) = <b>{{value}}</b></output></div>
        <input class="math-range" type="range" :min="minimum" :max="plot.max" :value="size" :aria-label="uiText('调整输入规模')" @input="setSize(($event.target as HTMLInputElement).value)" />
        <p class="math-note">{{uiText('拖动滑块探索；悬停一秒查看曲线数值。这里只计算公式，不重新执行代码。')}} <code>n ≥ {{minimum}}</code></p>
        <details class="math-table"><summary>{{uiText('全部计数公式与实测值')}}</summary><div><table><thead><tr><th>{{uiText('计数对象')}}</th><th>{{uiText('公式')}}</th><th>{{uiText('实测')}}</th></tr></thead><tbody><tr v-for="item in records" :key="item.id" :class="{selected:item.id===selected}"><td><button @click="selected=item.id">L{{item.line}} · {{uiText(labels[item.kind])}}</button></td><td><code>{{item.formula}}</code></td><td>{{item.measured}}</td></tr></tbody></table></div></details>
      </template>
      <p v-else class="math-note">{{uiText('没有可计数的循环或分支，当前单位操作模型为常数阶。')}}</p>
      <p v-for="note in analysis.notes" :key="`${note.line}:${note.kind}`" class="math-note math-branch">L{{note.line}} · {{uiText(note.kind==='always-false'?'由循环边界可知，这个条件始终不成立，其分支体执行 0 次。':'条件判断次数可推导；分支体的执行次数取决于数据，未合并为固定公式。')}}</p>
      <details class="math-assumptions"><summary>{{uiText('计数口径与推导范围')}}</summary><p>{{uiText('公式来自语法树与有限求和，支持单一规模变量、固定边界、步长 ±1 的最多四层 for / range。顺序循环取最高增长阶，嵌套循环逐层求和。')}}</p><p>{{uiText('循环体次数、循环条件判断和变量更新分别计数，不相加冒充 CPU 指令。回放步骤与解释器求值采用另外的口径。空间复杂度尚未推导。')}}</p><p>{{uiText('曲线是假设边界和控制流保持不变时的数学模型；超出执行限制的规模不会在后台运行。')}}</p></details>
    </template>
  </section>
</template>

<style scoped>
.math-analysis{margin-top:26px;padding-top:22px;border-top:1px solid var(--line);font-size:12px;line-height:1.8;color:var(--ink)}.math-analysis header{display:flex;align-items:center;justify-content:space-between;gap:14px}.math-kicker{font:9px/1.8 Consolas,monospace;letter-spacing:.12em;color:var(--muted)}.math-analysis h3{font-size:17px!important;margin:5px 0 12px!important;font-weight:500}.math-order{font:24px Georgia,serif;white-space:nowrap;color:var(--green)}.math-note{font-size:11px;color:var(--muted);line-height:1.9;margin:10px 0}.math-size-note{display:flex;flex-wrap:wrap;gap:12px;align-items:center;margin:16px 0 8px}.math-size-note>code{padding:4px 10px;background:var(--green-soft);border-radius:5px;color:var(--green)}.math-size-note span{color:var(--muted)}.math-warning{border-inline-start:2px solid var(--green);padding-left:12px}.math-metric-label{display:grid;gap:7px;margin:20px 0 14px;color:var(--muted)}.math-metric-label :deep(.field-input){font-size:12px;min-height:40px}.math-proof{padding:20px;border:1px solid var(--line);border-radius:12px;background:color-mix(in srgb,var(--green) 3%,var(--paper))}.math-proof>span{font-size:10px;color:var(--muted)}.math-sum{display:block;font:13px/1.8 Consolas,monospace;margin-top:8px;overflow-wrap:anywhere}.math-formula{font:26px/1.6 Georgia,'Times New Roman',serif;color:var(--green);padding:8px 0 14px;overflow-wrap:anywhere}.math-formula span{font-size:19px;margin-right:8px}.math-check{display:flex;gap:12px;flex-wrap:wrap;border-top:1px solid var(--line);padding-top:12px;font-size:10px;color:var(--muted)}.math-check b{font:14px Consolas,monospace;margin-left:6px;color:var(--ink)}.math-match{margin-left:auto;color:var(--green)}.math-plot-heading{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:23px}.math-plot-heading h4{font-size:13px;font-weight:500;margin:0}.math-plot-heading .button{font-size:10px;min-height:30px;padding:5px 9px}.math-chart-wrap{margin:16px 0 4px}.math-chart{width:100%;display:block;overflow:visible}.math-chart text{font:11px Consolas,monospace;fill:var(--muted)}.math-grid{stroke:var(--line);stroke-dasharray:3 5;fill:none}.math-area{fill:var(--green);opacity:.055}.math-curve{stroke:var(--green);stroke-width:2.4;fill:none;stroke-linejoin:round}.math-guide{stroke:var(--green);stroke-dasharray:4 5;opacity:.3;fill:none}.math-dot{fill:var(--green);stroke:var(--paper);stroke-width:2}.math-tooltip{pointer-events:none}.math-tooltip rect{fill:var(--paper);stroke:var(--line)}.math-tooltip text{fill:var(--ink);font-size:12px}.math-explore{display:flex;align-items:end;flex-wrap:wrap;gap:14px;font-size:10px;color:var(--muted)}.math-explore label{display:grid;gap:4px;max-width:100px}.math-explore input{box-sizing:border-box;width:92px;padding:6px 8px;border:1px solid var(--line);border-radius:5px;background:var(--paper);color:var(--ink);font:12px Consolas,monospace}.math-explore :deep(.compact-select){font-size:12px}.math-explore output{margin-left:auto;font:14px Consolas,monospace;color:var(--ink);padding-bottom:6px}.math-range{width:100%;accent-color:var(--green);margin-top:16px;cursor:pointer}.math-table,.math-assumptions{border-top:1px solid var(--line);margin-top:18px;padding-top:13px;font-size:11px}.math-table summary,.math-assumptions summary{cursor:pointer;color:var(--ink)}.math-table>div{overflow:auto;max-height:280px}.math-table table{width:100%;border-collapse:collapse;font-size:10px;margin-top:12px;text-align:left}.math-table th{color:var(--muted);font-weight:400}.math-table td,.math-table th{padding:8px 5px;border-bottom:1px solid var(--line)}.math-table button{border:0;background:none;color:var(--ink);font:inherit;cursor:pointer;text-align:left;padding:0}.math-table .selected{background:var(--green-soft)}.math-branch{padding-left:12px;border-left:2px solid var(--line)}.math-assumptions p{color:var(--muted)}
</style>
