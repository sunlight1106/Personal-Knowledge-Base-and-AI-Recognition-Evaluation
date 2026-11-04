export type AssessmentMode = 'recognition' | 'meaning' | 'spelling'
export interface AssessmentItem { id: string; band: number; meaning: string; pos: string; ipa: string; distractors: string[] }
export interface AssessmentBook { id: string; title: string; level: string; total: number; bands: number[] }
export interface AssessmentBank { version: number; population: number; bands: { size: number }[]; items: AssessmentItem[]; books: AssessmentBook[] }
export interface AssessmentAnswer { id: string; value: string | boolean }
export interface AssessmentBandResult { band: number; total: number; correct: number; rate: number; low: number; high: number }
export interface AssessmentResult { mode: AssessmentMode; total: number; correct: number; population: number; lower: number; upper: number; estimate: number; bands: AssessmentBandResult[] }

export function shuffled<T>(items: readonly T[], random: () => number = Math.random): T[] {
  const result = [...items]
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.min(i, Math.max(0, Math.floor(random() * (i + 1))))
    ;[result[i], result[j]] = [result[j]!, result[i]!]
  }
  return result
}

/** Every band is represented, even when refining a very low/high first-pass score. */
export function selectQuestions(bank: AssessmentBank, mode: AssessmentMode, previous: AssessmentAnswer[] = [], random = Math.random) {
  const used = new Set(previous.map(answer => answer.id))
  const pools = bank.bands.map((_, band) => shuffled(bank.items.filter(item => item.band === band && !used.has(item.id)), random))
  const counts = bank.bands.map(() => mode === 'recognition' ? (previous.length ? 4 : 8) : 6)
  if (mode === 'recognition' && previous.length) {
    const scores = scoreAssessment(bank, mode, previous).bands
    const focus = [...scores].sort((a, b) => Math.abs(a.rate - .5) - Math.abs(b.rate - .5)).slice(0, 2)
    for (const band of focus) counts[band.band]! += 10
  }
  return shuffled(pools.flatMap((pool, i) => pool.slice(0, counts[i])), random)
}

const variants = [['color', 'colour'], ['favorite', 'favourite'], ['center', 'centre'], ['theater', 'theatre'], ['analyze', 'analyse'], ['organize', 'organise'], ['recognize', 'recognise'], ['behavior', 'behaviour'], ['labor', 'labour'], ['honor', 'honour'], ['neighbor', 'neighbour'], ['traveling', 'travelling'], ['gray', 'grey']]
export function normalizeSpelling(value: string) { return value.normalize('NFKC').trim().toLowerCase() }
export function isCorrect(item: AssessmentItem, mode: AssessmentMode, value: string | boolean) {
  if (mode === 'recognition') return value === true
  if (typeof value !== 'string' || value.length > 80) return false
  if (mode === 'meaning') return value === item.meaning
  const text = normalizeSpelling(value)
  return text === item.id || variants.some(pair => pair.includes(item.id) && pair.includes(text))
}

/** Wilson sampling bounds, transformed for four-choice guessing when applicable.
 * These do NOT include all self-report, item-bank or clue effects; UI says so.
 */
export function scoreAssessment(bank: AssessmentBank, mode: AssessmentMode, answers: AssessmentAnswer[]): AssessmentResult {
  const byId = new Map(bank.items.map(item => [item.id, item]))
  const valid = new Map<string, AssessmentAnswer>()
  for (const answer of answers) if (byId.has(answer.id) && !valid.has(answer.id)) valid.set(answer.id, answer)
  const corrected = (p: number) => Math.max(0, Math.min(1, mode === 'meaning' ? (p - .25) / .75 : p))
  const bands = bank.bands.map((_, band) => {
    const sample = [...valid.values()].filter(a => byId.get(a.id)!.band === band)
    const total = sample.length, correct = sample.filter(a => isCorrect(byId.get(a.id)!, mode, a.value)).length
    if (!total) return { band, total, correct, rate: 0, low: 0, high: 1 }
    const p = correct / total, z2 = 1.96 ** 2, denominator = 1 + z2 / total
    const middle = (p + z2 / (2 * total)) / denominator
    const margin = 1.96 * Math.sqrt(p * (1 - p) / total + z2 / (4 * total * total)) / denominator
    return { band, total, correct, rate: corrected(p), low: corrected(middle - margin), high: corrected(middle + margin) }
  })
  const sum = (key: 'rate' | 'low' | 'high') => bands.reduce((value, band) => value + band[key] * bank.bands[band.band]!.size, 0)
  return { mode, total: valid.size, correct: bands.reduce((n, b) => n + b.correct, 0), population: bank.population,
    estimate: Math.min(bank.population, Math.round(sum('rate') / 100) * 100), lower: Math.max(0, Math.floor(sum('low') / 100) * 100),
    upper: Math.min(bank.population, Math.ceil(sum('high') / 100) * 100), bands }
}

export type LearningGoal = 'general' | 'school' | 'college' | 'graduate' | 'abroad' | 'academic'
const goalBooks: Record<LearningGoal, string[]> = {
  general: ['primary', 'gk-core', 'zk', 'gk', 'cet4', 'cet6', 'academic'],
  school: ['primary', 'gk-core', 'zk', 'gk'], college: ['gk-core', 'cet4', 'cet6'],
  graduate: ['gk-core', 'ky-core', 'ky'], abroad: ['gk-core', 'ielts', 'toefl'], academic: ['cet4', 'academic', 'gre'],
}
export function recommendBooks(bank: AssessmentBank, result: AssessmentResult, goal: LearningGoal) {
  const coverage = (book: AssessmentBook) => book.bands.reduce((n, size, i) => n + size * result.bands[i]!.rate, 0) / Math.max(1, book.bands.reduce((a, b) => a + b, 0))
  const primary = bank.books.find(book => book.id === 'vocab-ec-primary')!
  const candidates = bank.books.filter(book => goalBooks[goal].includes(book.id.replace('vocab-ec-', '')))
  // If basic coverage is weak, lead with a foundation book regardless of exam goal.
  if (result.bands[0]!.rate < .5 && !candidates.includes(primary)) candidates.push(primary)
  return candidates.map(book => ({ ...book, coverage: coverage(book) })).sort((a, b) => {
    if (result.bands[0]!.rate < .5) {
      if (a.id === primary.id) return -1
      if (b.id === primary.id) return 1
    }
    const difficulty = (book: AssessmentBook) => book.bands.reduce((n, size, i) => n + size * i, 0) / Math.max(1, book.bands.reduce((n, size) => n + size, 0))
    return Math.abs(a.coverage - .7) - Math.abs(b.coverage - .7)
      || (a.coverage > .9 && b.coverage > .9 ? difficulty(b) - difficulty(a) : a.total - b.total)
  }).slice(0, 3)
}
