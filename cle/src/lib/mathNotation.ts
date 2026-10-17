// Small, bounded typesetter for the count expressions produced by Code Lab.
// No JavaScript evaluation, HTML input, remote fonts or general TeX commands.
type Node = {tag:string;text?:string;children?:Node[]}
const row=(...children:Node[]):Node=>({tag:'mrow',children})
const leaf=(tag:string,text:string):Node=>({tag,text})
const escape=(s:string)=>s.replace(/[<>&"']/g,c=>({'<':'&lt;','>':'&gt;','&':'&amp;','"':'&quot;',"'":'&#39;'}[c]!))
function parse(source:string):Node {
  if(source.length>4000)throw Error('Formula limit')
  const input=source.replace(/[²³⁴]/g,c=>'^'+({'²':'2','³':'3','⁴':'4'}[c])).replace(/−/g,'-')
  const tokens=input.match(/[A-Za-z_][A-Za-z_0-9]*|\d+(?:\.\d+)?|[ΣΘΟO+\-·*/^=≥≤…()[\]]|\s+|./gu)?.filter(t=>t.trim())||[]
  if(tokens.length>600)throw Error('Formula token limit')
  let i=0,depth=0
  const peek=()=>tokens[i],take=()=>tokens[i++]
  const expect=(s:string)=>{if(take()!==s)throw Error('Formula syntax')}
  function atom():Node {
    if(++depth>40)throw Error('Formula depth')
    const t=take();let result:Node
    if(t==='Σ'){
      expect('(');const lower=expression();expect('…');const upper=expression();expect(')');expect('[');const body=expression();expect(']')
      result=row({tag:'munderover',children:[leaf('mo','∑'),lower,upper]},leaf('mo','('),body,leaf('mo',')'))
    }else if(t==='('||t==='['){const end=t==='('?')':']';result=row(leaf('mo',t),expression(),leaf('mo',end));expect(end)}
    else if(t==='-'||t==='+')result=row(leaf('mo',t==='-'?'−':t),atom())
    else if(t&&/^\d+(\.\d+)?$/.test(t))result=leaf('mn',t)
    else if(t&&/^(?:[A-Za-z_][A-Za-z_0-9]*|Θ|Ο)$/.test(t))result=leaf('mi',t)
    else throw Error('Formula token')
    if(peek()==='^'){take();result={tag:'msup',children:[result,atom()]}}
    depth--;return result
  }
  function product():Node {
    let result=atom()
    while(i<tokens.length&&!['+','-','=', '≥','≤','…',')',']'].includes(peek())){
      const op=peek()
      if(op==='/'){take();result={tag:'mfrac',children:[result,atom()]}}
      else if(op==='*'||op==='·'){take();result=row(result,leaf('mo','·'),atom())}
      else result=row(result,atom())
    }
    return result
  }
  function expression():Node {
    let result=product()
    while(['+','-','=','≥','≤'].includes(peek())){const op=take();result=row(result,leaf('mo',op==='-'?'−':op),product())}
    return result
  }
  const result=expression();if(i!==tokens.length)throw Error('Formula remainder');return result
}
function xml(node:Node):string{return `<${node.tag}>${node.text?escape(node.text):(node.children||[]).map(xml).join('')}</${node.tag}>`}
function tex(node:Node):string {
  const children=node.children||[],a=children.map(tex)
  if(node.tag==='mfrac')return `\\frac{${a[0]}}{${a[1]}}`
  if(node.tag==='msup')return `{${a[0]}}^{${a[1]}}`
  if(node.tag==='munderover')return `\\sum_{${a[1]}}^{${a[2]}}`
  if(node.tag==='mrow')return a.join(' ')
  const text=node.text||''
  if(node.tag==='mi'&&text.length>1)return `\\mathrm{${text.replace(/_/g,'\\_')}}`
  return ({'Θ':'\\Theta','Ο':'O','−':'-','·':'\\cdot','≥':'\\ge','≤':'\\le'}[text]||text.replace(/_/g,'\\_'))
}
export function mathMarkup(source:string,display=false):string {
  try{return `<math xmlns="http://www.w3.org/1998/Math/MathML" display="${display?'block':'inline'}" aria-label="${escape(source)}">${xml(parse(source))}</math>`}
  catch{return `<span>${escape(source)}</span>`}
}
export function mathLatex(source:string):string {try{return tex(parse(source))}catch{return source.replace(/[\\{}$]/g,'')}}
export function readableMath(source:string){return source.replace(/\^([234])/g,(_,n:string)=>({'2':'²','3':'³','4':'⁴'}[n]||n))}
