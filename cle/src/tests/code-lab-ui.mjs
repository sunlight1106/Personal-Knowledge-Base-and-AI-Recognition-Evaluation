import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import ts from 'typescript'
const source=readFileSync(new URL('../lib/codeLab.ts',import.meta.url),'utf8')
const js=ts.transpile(source,{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022})
const lib=await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'))
assert.deepEqual(lib.occurrences({steps:[{line:2},{line:3},{line:2}]},2),[0,2])
assert.equal(lib.arrayScale([-5,2,0]),5)
assert.equal(lib.arrayScale([]),1)
assert.equal(lib.valueText([3,2]),'[3,2]')
const svg=lib.traceSvg({steps:[{line:2,event:'statement',frames:[{name:'<module>',variables:{x:'<script>alert(1)</script>'}}]}]},0)
assert(!svg.includes('<script>'));assert(svg.includes('&lt;script&gt;'));assert(svg.includes('&lt;module&gt;'))
assert.equal(lib.traceSvg({steps:[]},0),'')
for(const sample of Object.values(lib.samples)){assert(sample.source.length<16000);assert(Array.isArray(JSON.parse(sample.args)))}
assert.deepEqual(JSON.parse(readFileSync(new URL('../../../src/code-lab/samples.json',import.meta.url),'utf8')),lib.samples)
console.log('Code lab UI: occurrence selection, scale, safe SVG export and four sample inputs passed')
