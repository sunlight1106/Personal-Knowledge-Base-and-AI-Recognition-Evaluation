<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, RouterLink, useRoute } from 'vue-router'
import { uiLanguage } from '@/i18n'
import LanguageToggle from '@/components/LanguageToggle.vue'
import BrandMark from '@/components/BrandMark.vue'
import AmbientWaves from '@/components/AmbientWaves.vue'
import SelectField from '@/components/SelectField.vue'
import { authStore } from '@/stores/auth'
import bank from '@/lib/vocabulary-assessment-bank.json'
import { isCorrect, recommendBooks, scoreAssessment, selectQuestions, shuffled, type AssessmentAnswer, type AssessmentItem, type AssessmentMode, type LearningGoal } from '@/lib/vocabularyAssessment'

const t = (zh: string, en: string) => uiLanguage.value === 'en' ? en : zh
const route = useRoute()
const modes = computed(() => [
  { id: 'recognition' as const, title: t('勾选认识的词', 'Word recognition'), subtitle: t('先看广度，再细测', 'A broad check, then a closer look'), detail: t('勾选能说出至少一个意思的词；眼熟但不确定，就留空。两轮共 80 词。', 'Select words you can define, leaving uncertain ones unchecked. Two rounds, 80 words.'), time: t('约 3–5 分钟', 'About 3–5 minutes'), badge: t('快速自测', 'Quick check') },
  { id: 'meaning' as const, title: t('英译汉', 'English → Chinese'), subtitle: t('看英文，选择中文意思', 'Choose the Chinese meaning'), detail: t('30 道释义选择题。不认识可以跳过，估算时会考虑四选一猜测的影响。', '30 meaning questions. Skip unfamiliar words; the estimate adjusts for four-choice guessing.'), time: t('约 4–6 分钟', 'About 4–6 minutes'), badge: t('理解词义', 'Understand') },
  { id: 'spelling' as const, title: t('汉译英', 'Chinese → English'), subtitle: t('看中文，写出英文', 'Write the English word'), detail: t('30 道拼写题，用首字母与长度区分目标词。检验提示下的回忆，不等同于自由写作能力。', '30 spelling questions with a first letter and length to identify the target. Tests cued recall, not free writing.'), time: t('约 6–10 分钟', 'About 6–10 minutes'), badge: t('回忆拼写', 'Recall & spell') },
])
const bandNames = computed(() => [t('核心常用', 'Core words'), t('日常扩展', 'Everyday range'), t('阅读进阶', 'Reading range'), t('学术拓展', 'Advanced range'), t('低频挑战', 'Less frequent words')])
const englishBookTitles: Record<string, string> = {
  'vocab-ec-primary': 'Primary bridge · Common 500', 'vocab-ec-gk-core': 'High school · Core 1,000',
  'vocab-ec-zk': 'Middle school English', 'vocab-ec-gk': 'High school English',
  'vocab-ec-cet4': 'College English · CET-4', 'vocab-ec-cet6': 'College English · CET-6',
  'vocab-ec-ky-core': 'Postgraduate · Core 1,500', 'vocab-ec-ky': 'Postgraduate English',
  'vocab-ec-ielts': 'IELTS vocabulary', 'vocab-ec-toefl': 'TOEFL vocabulary',
  'vocab-ec-academic': 'Academic reading · Advanced 1,500', 'vocab-ec-gre': 'GRE vocabulary',
}
const initialMode = ['recognition', 'meaning', 'spelling'].includes(String(route.query.mode)) ? String(route.query.mode) as AssessmentMode : 'recognition'
const mode = ref<AssessmentMode>(initialMode), screen = ref<'intro' | 'quiz' | 'result'>('intro'), stage = ref(1)
const questions = ref<AssessmentItem[]>([]), previous = ref<AssessmentAnswer[]>([]), values = ref<Record<string, string | boolean>>({})
const index = ref(0), confirmReset = ref(false), goal = ref<LearningGoal>('general'), notice = ref('')
const heading = ref<HTMLElement>(), spelling = ref<HTMLInputElement>()
const selectedMode = computed(() => modes.value.find(item => item.id === mode.value)!)
const current = computed(() => questions.value[index.value])
const optionSets = ref<Record<string, string[]>>({})
const answers = computed(() => [...previous.value, ...questions.value.map(item => ({ id: item.id, value: values.value[item.id] ?? (mode.value === 'recognition' ? false : '') }))])
const result = computed(() => scoreAssessment(bank, mode.value, answers.value))
const nextStep = computed(() => result.value.bands[0]!.rate < .5
  ? t('建议先打牢常用词基础', 'Build a stronger everyday foundation')
  : result.value.bands[1]!.rate < .6 ? t('从日常用词，走向更完整的阅读', 'Grow from everyday words into reading')
  : result.value.bands[3]!.rate < .5 ? t('已有阅读基础，可以继续拓展词汇', 'Build on your reading vocabulary')
  : t('可以尝试更进阶的阅读与表达', 'Explore more advanced reading and expression'))
const recommendations = computed(() => recommendBooks(bank, result.value, goal.value))
const selectedCount = computed(() => questions.value.filter(item => values.value[item.id] === true).length)
const unknown = computed(() => answers.value.map(answer => ({ answer, item: bank.items.find(item => item.id === answer.id)! })).filter(({ answer, item }) => !isCorrect(item, mode.value, answer.value)))
const progress = computed(() => mode.value === 'recognition' ? (stage.value === 1 ? 0 : 50) : Math.round(index.value / questions.value.length * 100))
const canStudy = computed(() => !authStore.state.user || authStore.has('vocabulary:use'))
let startedAt = 0, completedAt = 0, accountReady = false

async function focusHeading() { await nextTick(); heading.value?.focus() }
function start() {
  previous.value = []; values.value = {}; index.value = 0; stage.value = 1; confirmReset.value = false; notice.value = ''
  questions.value = selectQuestions(bank, mode.value)
  optionSets.value = Object.fromEntries(questions.value.map(item => [item.id, shuffled([item.meaning, ...item.distractors])]))
  startedAt = Date.now(); screen.value = 'quiz'; void focusHeading()
}
function finishOrRefine() {
  if (screen.value !== 'quiz') return
  if (mode.value === 'recognition' && stage.value === 1) {
    previous.value = [...answers.value]
    questions.value = selectQuestions(bank, mode.value, previous.value); values.value = {}; stage.value = 2
  } else { screen.value = 'result'; confirmReset.value = false; completedAt = Date.now() }
  void focusHeading()
}
async function next(skip = false) {
  if (screen.value !== 'quiz' || !current.value) return
  if (!skip && !String(values.value[current.value.id] ?? '').trim()) return
  if (skip) values.value[current.value.id] = ''
  if (index.value + 1 === questions.value.length) { finishOrRefine(); return }
  index.value++; await nextTick(); if (mode.value === 'spelling') spelling.value?.focus(); else heading.value?.focus()
}
function reset() { screen.value = 'intro'; questions.value = []; previous.value = []; values.value = {}; optionSets.value = {}; confirmReset.value = false; void focusHeading() }
function bookLink(id: string) {
  const target = `/app/vocabulary?book=${encodeURIComponent(id)}`
  return authStore.state.user ? target : `/login?redirect=${encodeURIComponent(target)}`
}
function exportResult() {
  const report = { format: 'pkb-vocabulary-assessment', version: 1, bankVersion: bank.version, completedAt: new Date(completedAt).toISOString(), durationSeconds: Math.max(0, Math.round((completedAt - startedAt) / 1000)),
    ...result.value, goal: goal.value, recommendations: recommendations.value.map(book => ({ id: book.id, title: book.title })),
    note: 'Educational estimate within the eligible built-in headword population, not a standardized proficiency score. Recognition is self-report; meaning is four-choice; spelling uses letter/length cues. No mastery progress was changed.' }
  const url = URL.createObjectURL(new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' }))
  const link = document.createElement('a'); link.href = url; link.download = `vocabulary-assessment-${new Date().toISOString().slice(0, 10)}.json`; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
  notice.value = t('结果已生成下载。', 'Your result download is ready.')
}
function beforeUnload(event: BeforeUnloadEvent) { if (screen.value === 'quiz') { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(() => screen.value !== 'quiz' || window.confirm(t('测试尚未完成，离开会清除本次答案。确定离开？', 'This test is unfinished. Leave and clear these answers?')))
watch(() => authStore.state.user?.id, () => { if (accountReady) reset() })
onMounted(async () => { window.addEventListener('beforeunload', beforeUnload); await authStore.ensureUser(); accountReady = true })
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <div class="assessment-page">
    <header class="assessment-header"><RouterLink to="/" class="assessment-brand"><BrandMark />{{ t('个人知识库', 'Personal Knowledge Base') }}</RouterLink><nav><RouterLink :to="authStore.state.user ? '/app/home' : '/'">{{ t('返回首页', 'Back home') }}</RouterLink><LanguageToggle /></nav></header>
    <main>
      <template v-if="screen === 'intro'">
        <section class="assessment-intro"><AmbientWaves /><p class="assessment-kicker">A FEW WORDS. A CLEARER START.</p><h1 ref="heading" tabindex="-1">{{ t('从认识的词，', 'Start with what you know. ') }}<br v-if="uiLanguage === 'zh'" /><span>{{ t('找到下一步。', 'Find your next step.') }}</span></h1><p>{{ t('不用先选词书。花几分钟了解自己的词汇基础，再决定从哪里开始。', 'Before choosing a word book, take a few minutes to explore your vocabulary.') }}</p></section>
        <fieldset class="assessment-mode-picker"><legend>{{ t('选择测试方式', 'Choose a test') }}</legend><label v-for="(item, number) in modes" :key="item.id" :class="{ selected: mode === item.id }"><input v-model="mode" type="radio" name="assessment-mode" :value="item.id" /><div class="mode-top"><span>0{{ number + 1 }}</span><small>{{ item.badge }}</small></div><h2>{{ item.title }}</h2><strong>{{ item.subtitle }}</strong><p>{{ item.detail }}</p><span class="mode-time">{{ item.time }}</span></label></fieldset>
        <div class="assessment-start"><button class="button button--dark" @click="start">{{ t('开始测试', 'Start test') }} <span aria-hidden="true">→</span></button><p>{{ t('无需注册 · 无 AI 调用 · 答案仅在当前页面处理', 'No sign-up · No AI calls · Answers stay on this page') }}</p></div>
        <details class="assessment-method"><summary>{{ t('结果能告诉我什么？', 'What does the result mean?') }}</summary><p>{{ t('这是帮助选书的学习自测。按内置词库的频率层级抽样，估算其中可能认识的词条数量，并给出宽范围。它不等于英语全部词汇量，也不用于认定考试分数或 CEFR 等级。', 'This learning check samples frequency bands from the built-in catalog to estimate familiar headwords within that catalog. Its broad range is not your total English vocabulary, an exam score or a CEFR certification.') }}</p><p>{{ t('三种模式分别涉及自我判断、选项辨认和有提示的拼写，结果不宜直接互相比高低。测试不会改变熟词、复习进度或学习统计；刷新或离开页面会清除本次答案。', 'The modes measure self-report, recognition among choices and cued spelling. Their scores are not directly interchangeable. Tests never change mastery, reviews or study statistics. Reloading or leaving clears the answers.') }}</p></details>
      </template>

      <template v-else-if="screen === 'quiz'">
        <div class="assessment-quiz-top"><RouterLink to="/vocabulary-test" @click.prevent="confirmReset = true">{{ t('重新选择模式', 'Choose another mode') }}</RouterLink><span>{{ selectedMode.title }} · {{ mode === 'recognition' ? `${stage} / 2` : `${index + 1} / ${questions.length}` }}</span></div>
        <div class="assessment-progress" role="progressbar" :aria-label="t('测试进度', 'Test progress')" :aria-valuenow="progress" aria-valuemin="0" aria-valuemax="100"><span :style="{ width: `${progress}%` }" /></div>
        <section v-if="confirmReset" class="assessment-confirm" role="alert"><p>{{ t('重新开始会清除本次答案，学习记录不受影响。', 'Restarting clears these answers. Your study history stays unchanged.') }}</p><button class="button button--light" @click="confirmReset = false">{{ t('继续测试', 'Keep testing') }}</button><button class="button button--dark" @click="reset">{{ t('清除并返回', 'Clear and return') }}</button></section>
        <template v-if="mode === 'recognition'">
          <div class="assessment-question-heading"><p class="assessment-kicker">{{ stage === 1 ? '01 / EXPLORE' : '02 / REFINE' }}</p><h1 ref="heading" tabindex="-1">{{ stage === 1 ? t('哪些词，你真的认识？', 'Which words do you know?') : t('再看一组，让范围更清楚。', 'A closer look at your range.') }}</h1><p>{{ t('能说出至少一个意思才勾选。不确定就留空，一个都不认识也可以继续。', 'Select a word only if you can give a meaning. Leave uncertain words unchecked; zero selections is fine.') }}</p></div>
          <fieldset class="assessment-word-grid"><legend class="sr-only">{{ t('选择认识的单词', 'Select familiar words') }}</legend><label v-for="item in questions" :key="item.id" :class="{ checked: values[item.id] === true }"><input v-model="values[item.id]" type="checkbox" :aria-label="item.id" /><span>{{ item.id }}</span><span class="word-tick" aria-hidden="true">{{ values[item.id] ? '✓' : '+' }}</span></label></fieldset>
          <div class="assessment-quiz-footer"><span aria-live="polite">{{ t('已勾选', 'Selected') }} {{ selectedCount }} / {{ questions.length }}</span><button class="button button--dark" @click="finishOrRefine">{{ stage === 1 ? t('下一轮', 'Next round') : t('查看结果', 'See results') }} →</button></div>
        </template>
        <form v-else-if="current" class="assessment-translation" @submit.prevent="next()">
          <p class="assessment-kicker">{{ mode === 'meaning' ? 'READ & UNDERSTAND' : 'RECALL & SPELL' }}</p>
          <h1 ref="heading" tabindex="-1" :class="{ 'english-word': mode === 'meaning' }">{{ mode === 'meaning' ? current.id : current.meaning }}</h1>
          <p>{{ current.pos }} · {{ mode === 'meaning' ? t('选择对应的中文意思', 'Choose the Chinese meaning') : t('根据释义，填写目标英文单词', 'Write the target English word for this meaning') }}</p>
          <fieldset v-if="mode === 'meaning'" class="assessment-options"><legend class="sr-only">{{ t('中文意思', 'Chinese meaning') }}</legend><label v-for="(option, n) in optionSets[current.id]" :key="option" :class="{ selected: values[current.id] === option }"><input v-model="values[current.id]" type="radio" :name="current.id" :value="option" /><span class="option-letter">{{ String.fromCharCode(65 + n) }}</span><span>{{ option }}</span></label></fieldset>
          <label v-else class="assessment-spelling"><span>{{ t('首字母', 'First letter') }} <b>{{ current.id[0] }}</b> · {{ current.id.length }} {{ t('个字母', 'letters') }}</span><input :key="current.id" ref="spelling" v-model="values[current.id]" type="text" maxlength="80" autocomplete="off" autocapitalize="none" :spellcheck="false" :aria-label="t('输入英文单词', 'Enter the English word')" :placeholder="t('在这里输入…', 'Type here…')" /><small>{{ t('英美拼写变体均可；大小写和两端空格不影响评分。', 'Common US/UK variants are accepted; case and surrounding spaces do not affect scoring.') }}</small></label>
          <div class="assessment-quiz-footer"><button type="button" class="button button--light" :disabled="index === 0" @click="index--">{{ t('上一题', 'Previous') }}</button><div><button type="button" class="button button--light" @click="next(true)">{{ t('不认识，跳过', 'I don’t know') }}</button><button class="button button--dark" :disabled="!String(values[current.id] ?? '').trim()">{{ index + 1 === questions.length ? t('查看结果', 'See results') : t('下一题', 'Next') }} →</button></div></div>
          <p class="assessment-footnote">{{ t('完成后统一查看答案，避免刚看过答案影响后续测试。', 'Answers appear after completion so they do not influence later questions.') }}</p>
        </form>
      </template>

      <template v-else>
        <section class="assessment-result-hero water-surface"><AmbientWaves variant="pool" class="surface-watermark" /><p class="assessment-kicker">YOUR NEXT CHAPTER</p><h1 ref="heading" tabindex="-1">{{ t('你的词汇画像', 'Your vocabulary snapshot') }}</h1><p>{{ selectedMode.title }} · {{ result.correct }} / {{ result.total }} {{ mode === 'recognition' ? t('自评认识', 'self-reported known') : t('题答对', 'correct answers') }}</p><div class="assessment-range">{{ result.lower.toLocaleString() }}<span>–</span>{{ result.upper.toLocaleString() }}</div><strong>{{ t('词条 · 学习参考范围', 'headwords · indicative range') }}</strong><p class="assessment-placement">{{ nextStep }}</p><p class="result-scope">{{ t('仅针对内置可测词库中的', 'Within the') }} {{ result.population.toLocaleString() }} {{ t('个词条；不是精确词汇量。', 'eligible built-in headwords; not an exact vocabulary size.') }}</p><p v-if="result.correct === result.total" class="result-scope">{{ t('本次全部答对，已接近这份测试的覆盖上限，可以尝试更进阶的阅读材料。', 'All answers were correct. You are approaching this test’s ceiling; try more advanced reading.') }}</p><p v-else-if="result.correct === 0" class="result-scope">{{ t('这组题未显示出已掌握的词，不代表你的词汇量为零。从基础词书开始，再用日常阅读检验。', 'This sample did not show known words; that does not mean you know none. Start with a foundation book and check your progress through reading.') }}</p></section>
        <section class="assessment-breakdown"><h2>{{ t('从常用词，到更宽的表达', 'From everyday words to a wider range') }}</h2><p>{{ t('下面是各层实际答对或勾选的比例，不是考试等级。', 'These are observed correct or selected proportions by band, not proficiency levels.') }}</p><div v-for="band in result.bands" :key="band.band" class="assessment-band"><span>{{ bandNames[band.band] }}</span><meter :min="0" :max="band.total" :value="band.correct" :aria-label="bandNames[band.band]" /><span>{{ band.correct }} / {{ band.total }}</span></div></section>
        <section class="assessment-recommendations"><header><div><p class="assessment-kicker">A BOOK TO BEGIN WITH</p><h2>{{ t('下一本，可以从这里选。', 'A good place for your next chapter.') }}</h2></div><label>{{ t('学习方向', 'Learning goal') }}<SelectField><select v-model="goal" :aria-label="t('学习方向', 'Learning goal')"><option value="general">{{ t('日常积累', 'Everyday learning') }}</option><option value="school">{{ t('中高考', 'School English') }}</option><option value="college">{{ t('大学四六级', 'CET-4 / CET-6') }}</option><option value="graduate">{{ t('考研英语', 'Postgraduate entrance') }}</option><option value="abroad">{{ t('雅思 / 托福', 'IELTS / TOEFL') }}</option><option value="academic">{{ t('学术与进阶阅读', 'Academic reading') }}</option></select></SelectField></label></header><p>{{ t('根据各层表现与词书组成排序，优先兼顾基础和新词；不是考试通过预测。', 'Ranked using your band results and each book’s composition, balancing familiar and new words. This does not predict exam success.') }}</p><div class="assessment-book-grid"><article v-for="(book, n) in recommendations" :key="book.id"><span class="book-index">0{{ n + 1 }} · {{ n === 0 ? t('建议先看', 'Start here') : t('也可尝试', 'Also consider') }}</span><h3>{{ t(book.title, englishBookTitles[book.id] || book.title) }}</h3><p>{{ book.total.toLocaleString() }} {{ t('词 · 内置词书', 'words · Built-in book') }}</p><p>{{ book.coverage < .45 ? t('新词较多，建议放慢节奏，先理解再回忆。', 'Many words may be new. Start slowly, learning before recalling.') : book.coverage > .9 ? t('多数层级已有基础，适合查漏补缺或保持复习。', 'You have a foundation in most bands; use this for gaps and review.') : t('已有基础与待学词汇并存，适合渐进学习。', 'A mix of familiar and new vocabulary for steady progress.') }}</p><RouterLink v-if="canStudy" :to="bookLink(book.id)" class="button button--light">{{ authStore.state.user ? t('查看这本词书', 'Open this book') : t('登录后学习', 'Sign in to study') }} →</RouterLink><span v-else class="assessment-footnote">{{ t('当前账号未开放词汇学习权限，可联系管理员。', 'Vocabulary study is not enabled for this account. Contact your administrator.') }}</span></article></div></section>
        <details v-if="unknown.length" class="assessment-review"><summary>{{ t('回看本次不熟悉的词', 'Review unfamiliar words') }} · {{ unknown.length }}</summary><ul><li v-for="entry in unknown" :key="entry.item.id"><div><strong>{{ entry.item.id }}</strong><small>{{ entry.item.ipa }}</small></div><span>{{ entry.item.meaning }}</span></li></ul><p>{{ t('这里仅供回看，未自动标记为熟词。再次遇到这些题，成绩可能受记忆影响。', 'Reviewing does not mark words as mastered. Retesting these items can be influenced by memory.') }}</p></details>
        <details class="assessment-method"><summary>{{ t('估算依据与使用建议', 'How to read this estimate') }}</summary><p>{{ t('将词库按频率分为五层，用各层样本的比例乘以该层词条数，再合并成范围。勾选模式补测边界层；英译汉对四选一猜测做校正；汉译英采用目标词匹配，含首字母与长度提示。', 'The catalog is split into five frequency bands. Sample proportions are weighted by each band’s size. Recognition refines boundary bands; meaning adjusts for four-choice guessing; spelling matches target words with letter/length cues.') }}</p><p>{{ t('范围考虑样本波动，但无法完全覆盖自评偏差、提示影响和题库偏差。这套题尚未经大规模人群标定；不要据此判断 CEFR、学习能力或考试成绩。汉译英的同义词未必是本题目标词，可在回看中核对。', 'The range reflects sampling variation but cannot fully capture self-report, clue and item-bank bias. The test is not population-calibrated and cannot establish CEFR, ability or exam scores. A spelling synonym may differ from the target; check the review when needed.') }}</p><p>{{ t('建议隔一段实际学习时间再测，避免靠记住题目抬高结果。不同模式分别看待。', 'Retest after a period of actual study, not immediate repetition. Interpret each mode separately.') }}</p></details>
        <div class="assessment-result-actions"><button class="button button--dark" @click="exportResult">{{ t('保存测试结果', 'Save result') }}</button><button class="button button--light" @click="reset">{{ t('尝试另一种模式', 'Try another mode') }}</button><RouterLink to="/">{{ t('回到首页', 'Back home') }}</RouterLink></div><p v-if="notice" role="status">{{ notice }}</p>
      </template>
    </main>
    <footer class="assessment-page-footer">{{ t('认识多少词，只是起点。能在生活里用出来，才是下一步。', 'Knowing words is a starting point. Using them is your next step.') }}</footer>
  </div>
</template>
<style scoped src="@/styles/vocabulary-assessment.css"></style>
