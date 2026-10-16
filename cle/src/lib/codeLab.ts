export type Language = 'auto' | 'python' | 'c' | 'java' | 'javascript'
export type Value = null | string | number | boolean | Value[]
export interface Frame {name:string;variables:Record<string,Value>}
export interface Step {line:number;event:string;frames:Frame[];outputLength:number;detail?:Value}
export interface Trace {
  language?:Language;entry?:string;status:'completed'|'error'|'limited';steps:Step[];output?:string;result?:Value;
  error?:{code:string;message:string;line:number}|null;operations?:number;
  lineCounts?:Record<string,number>;loopCounts?:Record<string,number>;callCounts?:Record<string,number>;
  complexity?:{estimate:string;loopDepth:number;basis:string;measured:string}
}
export interface Run {id:string;source:string;entry:string;arguments:Value[];result:Trace}
export interface RunSummary {id:string;language:string;entry:string;status:string;createdAt:string}
export const languageNames:Record<Language,string>={auto:'自动识别',c:'C',python:'Python',java:'Java',javascript:'JavaScript'}
export const samples:Record<Exclude<Language,'auto'>,{source:string;entry:string;args:string}>={
  python:{entry:'sort',args:'[[5, 2, 8, 1]]',source:`def sort(a):
    n = len(a)
    for i in range(n):
        for j in range(n - i - 1):
            if a[j] > a[j + 1]:
                a[j], a[j + 1] = a[j + 1], a[j]
    return a`},
  c:{entry:'sort',args:'[[5, 2, 8, 1], 4]',source:`int sort(int a[], int n) {
    for (int i = 0; i < n; i++) {
        for (int j = 0; j < n - i - 1; j++) {
            if (a[j] > a[j + 1]) {
                int temp = a[j];
                a[j] = a[j + 1];
                a[j + 1] = temp;
            }
        }
    }
    return 0;
}`},
  java:{entry:'sort',args:'[[5, 2, 8, 1]]',source:`static int[] sort(int[] a) {
    int n = a.length;
    for (int i = 0; i < n; i++) {
        for (int j = 0; j < n - i - 1; j++) {
            if (a[j] > a[j + 1]) {
                int temp = a[j];
                a[j] = a[j + 1];
                a[j + 1] = temp;
            }
        }
    }
    return a;
}`},
  javascript:{entry:'sort',args:'[[5, 2, 8, 1]]',source:`function sort(a) {
    const n = a.length;
    for (let i = 0; i < n; i++) {
        for (let j = 0; j < n - i - 1; j++) {
            if (a[j] > a[j + 1]) {
                let temp = a[j];
                a[j] = a[j + 1];
                a[j + 1] = temp;
            }
        }
    }
    return a;
}`}
}
export function occurrences(trace:Trace,line:number){return trace.steps.flatMap((step,index)=>step.line===line?[index]:[])}
export function valueText(value:Value|undefined){return value===undefined?'—':JSON.stringify(value)}
export function arrayScale(values:Value[]){return Math.max(1,...values.map(v=>typeof v==='number'?Math.abs(v):0))}
export function escapeXml(value:unknown){return String(value).replace(/[<>&"']/g,c=>({'<':'&lt;','>':'&gt;','&':'&amp;','"':'&quot;',"'":'&apos;'}[c]!))}
export function traceSvg(trace:Trace,stepIndex:number){
  const step=trace.steps[stepIndex];if(!step)return ''
  let y=75
  const parts=[`<text x="30" y="34">Code Lab · Step ${stepIndex+1} · Line ${step.line} · ${escapeXml(step.event)}</text>`]
  for(const frame of step.frames){
    const start=y,body:string[]=[]
    body.push(`<text x="48" y="${y+25}" font-weight="bold">${escapeXml(frame.name)}</text>`);y+=45
    for(const [name,value] of Object.entries(frame.variables)){
      if(Array.isArray(value)){
        body.push(`<text x="48" y="${y+18}">${escapeXml(name)} [${value.length}]</text>`);y+=32
        const scale=arrayScale(value)
        value.forEach((v,i)=>{
          const x=48+(i%16)*55,top=y+Math.floor(i/16)*112,h=typeof v==='number'?Math.max(4,Math.abs(v)/scale*55):4
          body.push(`<rect x="${x}" y="${top}" width="48" height="102" rx="5" fill="#edf4f1" stroke="#cbdcd6"/><rect x="${x+14}" y="${top+60-h}" width="20" height="${h}" rx="2" fill="#48776e"/><text x="${x+24}" y="${top+78}" text-anchor="middle" font-size="11">${escapeXml(valueText(v).slice(0,6))}</text><text x="${x+24}" y="${top+94}" text-anchor="middle" fill="#82948e" font-size="9">${i}</text>`)
        });y+=Math.max(1,Math.ceil(value.length/16))*112
      }else{body.push(`<text x="48" y="${y+20}">${escapeXml(name)} = ${escapeXml(valueText(value).slice(0,90))}</text>`);y+=32}
    }
    y+=10;parts.push(`<rect x="30" y="${start}" width="940" height="${y-start}" rx="10" fill="#fff" stroke="#cbdcd6"/>`,...body);y+=20
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="${y}" viewBox="0 0 1000 ${y}"><rect width="100%" height="100%" fill="#f7faf9"/><g fill="#23433f" font-family="monospace" font-size="15">${parts.join('')}</g></svg>`
}
