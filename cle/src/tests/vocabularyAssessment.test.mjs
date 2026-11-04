import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import { selectQuestions, scoreAssessment, isCorrect, recommendBooks } from '../lib/vocabularyAssessment.ts'
const bank = JSON.parse(fs.readFileSync(new URL('../lib/vocabulary-assessment-bank.json', import.meta.url)))
const random = () => .37
const answers = (items, value) => items.map(item => ({ id: item.id, value: typeof value === 'function' ? value(item) : value }))

test('all 33,614 translation band-score combinations remain bounded and monotonic', () => {
  const sample=bank.bands.flatMap((_,band)=>bank.items.filter(item=>item.band===band).slice(0,6))
  const size=7**bank.bands.length
  for(const mode of ['meaning','spelling']){
    const estimates=[]
    for(let code=0;code<size;code++){
      const counts=bank.bands.map((_,band)=>Math.floor(code/7**band)%7)
      const input=sample.map((item,index)=>({id:item.id,value:index%6<counts[item.band]?(mode==='meaning'?item.meaning:item.id):''}))
      const result=scoreAssessment(bank,mode,input)
      assert.equal(result.total,30);assert.equal(result.correct,counts.reduce((a,b)=>a+b,0))
      assert.ok(Number.isFinite(result.estimate)&&result.estimate>=0&&result.estimate<=bank.population)
      assert.ok(result.lower<=result.estimate&&result.estimate<=result.upper)
      for(let band=0;band<counts.length;band++)if(counts[band])assert.ok(result.estimate>=estimates[code-7**band])
      estimates.push(result.estimate)
    }
    assert.equal(estimates[0],0)
    assert.ok(estimates.at(-1)>=bank.population-100)
  }
})

test('licensed bank has five complete unique pools and valid disjoint choice sets', () => {
  assert.equal(bank.items.length, 500)
  assert.equal(new Set(bank.items.map(item => item.id)).size, 500)
  assert.equal(bank.population, bank.bands.reduce((n, b) => n + b.size, 0))
  for (let band = 0; band < 5; band++) assert.equal(bank.items.filter(i => i.band === band).length, 100)
  for (const item of bank.items) {
    assert.match(item.id, /^[a-z]{3,24}$/)
    assert.equal(new Set([item.meaning, ...item.distractors]).size, 4)
    assert.ok(!item.meaning.includes(item.id))
    const chars = new Set(item.meaning.match(/[\u4e00-\u9fff]/g))
    for (const other of item.distractors) assert.ok(![...other].some(c => chars.has(c)))
  }
})

test('recognition samples 40+120 without repeats and focuses on the appropriate boundary', () => {
  const first = selectQuestions(bank, 'recognition', [], random)
  assert.equal(first.length, 40)
  for (const value of [false, true]) {
    const second = selectQuestions(bank, 'recognition', answers(first, value), random)
    assert.equal(second.length, 120)
    assert.equal(new Set([...first, ...second].map(i => i.id)).size, 160)
    for (let band = 0; band < 5; band++) assert.ok(second.filter(i => i.band === band).length >= 8)
    assert.equal(second.filter(i => i.band === (value ? 4 : 0)).length, 58)
  }
})

test('translation follow-ups retain broad coverage and never reuse earlier questions', () => {
  for (const mode of ['meaning', 'spelling']) {
    let previous = answers(selectQuestions(bank, mode, [], random), '')
    for (let round = 0; round < 2; round++) {
      const followup = selectQuestions(bank, mode, previous, random)
      assert.equal(followup.length, 20)
      assert.ok(followup.every(item => !previous.some(a => a.id === item.id)))
      for (let band = 0; band < 5; band++) assert.ok(followup.filter(item => item.band === band).length >= 2)
      assert.equal(followup.filter(item => item.band === 0).length, 8)
      previous = [...previous, ...answers(followup, '')]
    }
    assert.equal(previous.length, 70)
  }
})

test('three basic spelling answers out of thirty produce a weighted point, not a broad interval as the estimate', () => {
  const items = selectQuestions(bank, 'spelling', [], random)
  let known = 0
  const result = scoreAssessment(bank, 'spelling', answers(items, item => item.band === 0 && known++ < 3 ? item.id : ''))
  assert.equal(result.correct, 3)
  assert.equal(result.estimate, 500)
  assert.ok(result.upper > result.estimate, 'sampling bounds remain distinct from the displayed estimate')
})

test('additional recognition samples reduce sampling uncertainty across synthetic low, middle and high profiles', () => {
  const seed = n => () => ((n = (Math.imul(n, 1664525) + 1013904223) >>> 0) / 2 ** 32)
  for (const rates of [[.4,.1,0,0,0], [1,.8,.4,.1,0], [1,1,.9,.7,.5]]) {
    let initialWidth = 0, finalWidth = 0
    for (let run = 1; run <= 40; run++) {
      const rng = seed(run)
      const first = selectQuestions(bank, 'recognition', [], rng)
      const a = answers(first, item => rng() < rates[item.band])
      const second = selectQuestions(bank, 'recognition', a, rng)
      const before = scoreAssessment(bank, 'recognition', a)
      const after = scoreAssessment(bank, 'recognition', [...a, ...answers(second, item => rng() < rates[item.band])])
      initialWidth += before.upper - before.lower; finalWidth += after.upper - after.lower
    }
    assert.ok(finalWidth < initialWidth * .75, 'more evidence must improve sampling resolution on these synthetic profiles')
  }
})

test('both translation modes sample 30 questions across all bands', () => {
  for (const mode of ['meaning', 'spelling']) {
    const items = selectQuestions(bank, mode, [], random)
    assert.equal(items.length, 30)
    for (let b = 0; b < 5; b++) assert.equal(items.filter(i => i.band === b).length, 6)
  }
})

test('score ignores unknown and duplicate IDs and never exceeds catalog bounds', () => {
  const items = selectQuestions(bank, 'recognition', [], random)
  const correct = answers(items, true)
  const result = scoreAssessment(bank, 'recognition', [...correct, ...correct, { id: 'not-a-word', value: true }])
  assert.equal(result.total, 40)
  assert.equal(result.correct, 40)
  assert.ok(result.lower <= result.estimate && result.estimate <= result.upper && result.upper <= bank.population)
  const zero = scoreAssessment(bank, 'recognition', answers(items, false))
  assert.equal(zero.estimate, 0)
  assert.equal(zero.lower, 0)
  assert.ok(zero.upper > 0)
  const empty = scoreAssessment(bank, 'recognition', [])
  assert.equal(empty.total, 0)
  assert.equal(empty.upper, bank.population)
})

test('four-choice chance level is corrected while observed correct counts remain visible', () => {
  const input = bank.items.filter(item => item.band === 0).slice(0, 4)
  const result = scoreAssessment(bank, 'meaning', input.map((item, i) => ({ id: item.id, value: i === 0 ? item.meaning : '' })))
  assert.equal(result.bands[0].correct, 1)
  assert.equal(result.bands[0].rate, 0)
})

test('spelling accepts case, outer space and listed variants, but not typos or oversized input', () => {
  const item = { ...bank.items[0], id: 'color' }
  assert.ok(isCorrect(item, 'spelling', ' COLOUR '))
  assert.ok(isCorrect(item, 'spelling', 'ｃｏｌｏｒ'))
  for (const value of ['colr', 'col or', '', 'color'.repeat(100), true]) assert.equal(isCorrect(item, 'spelling', value), false)
  assert.equal(isCorrect(item, 'recognition', 'true'), false)
})

test('recommendations include foundational help for weak scores and advancement for high scores', () => {
  const items = selectQuestions(bank, 'recognition', [], random)
  const low = scoreAssessment(bank, 'recognition', answers(items, false))
  const high = scoreAssessment(bank, 'recognition', answers(items, true))
  for (const goal of ['general', 'school', 'college', 'graduate', 'abroad', 'academic', 'daily', 'travel', 'work', 'science']) {
    const recommended = recommendBooks(bank, low, goal)
    assert.equal(recommended[0].id, 'vocab-ec-primary')
    assert.equal(new Set(recommended.map(b => b.id)).size, recommended.length)
    assert.ok(recommended.every(b => bank.books.some(book => b.id === book.id)))
  }
  assert.notEqual(recommendBooks(bank, high, 'general')[0].id, 'vocab-ec-primary')
  assert.ok(recommendBooks(bank, low, 'general').every(book => ['vocab-ec-primary', 'vocab-ec-gk-core', 'vocab-ec-zk', 'vocab-ec-common-1000'].includes(book.id)), 'An all-unknown beginner result must not promote a short advanced book just because it has fewer words')
  assert.ok(recommendBooks(bank, high, 'abroad').some(b => b.id === 'vocab-ec-toefl'))
})

test('expanded book goals recommend only matching public books without changing the assessment population', () => {
  assert.equal(bank.books.length, 30)
  assert.equal(new Set(bank.books.map(b => b.id)).size, 30)
  assert.equal(bank.population, 12221)
  const high = scoreAssessment(bank, 'recognition', bank.items.map(item => ({id:item.id,value:true})))
  const groups = {daily:['common-1000','everyday','food','health','feelings'], travel:['travel','everyday','food','common-1000'], work:['business','computing','ai-reading','writing'], science:['science','math','environment','ai-reading','academic']}
  for (const [goal, ids] of Object.entries(groups)) {
    const picks = recommendBooks(bank, high, goal)
    assert.equal(picks.length, 3)
    assert.ok(picks.every(b => ids.includes(b.id.replace('vocab-ec-',''))))
    assert.ok(picks.every(b => b.bands.reduce((sum,n)=>sum+n,0) <= b.total))
  }
})

test('randomized answer patterns keep estimates and ranges finite and bounded', () => {
  for (let i = 0; i < 100; i++) for (const mode of ['recognition', 'meaning', 'spelling']) {
    const items = selectQuestions(bank, mode)
    const sample = answers(items, item => Math.random() < .5 ? mode === 'recognition' ? true : mode === 'meaning' ? item.meaning : item.id : mode === 'recognition' ? false : '')
    const result = scoreAssessment(bank, mode, sample)
    assert.ok(result.lower >= 0 && result.lower <= result.upper && result.upper <= bank.population)
    assert.ok(Number.isFinite(result.estimate) && result.estimate >= 0 && result.estimate <= bank.population)
  }
})
