<script setup lang="ts">
import { uiText } from '@/i18n'

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/api/client'
import { researchApi, type Hit } from '@/api/research'
import { vocabularyApi, type VocabularyDashboard } from '@/api/vocabulary'
import type { DashboardSummary } from '@/types/api'
import AppIcon from '@/components/AppIcon.vue'
import AmbientWaves from '@/components/AmbientWaves.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import TodayLearning from '@/components/TodayLearning.vue'
import { authStore } from '@/stores/auth'
import { workspaceNavigation } from '@/stores/workspaceNavigation'
import { createRequestGuard } from '@/lib/requestGuard'

const summary = ref<DashboardSummary | null>(null)
const vocabulary = ref<VocabularyDashboard | null>(null)
const notes = ref<Hit[]>([])
const errors = ref({ experiments: false, vocabulary: false, notes: false })
const loading = ref(false)
const pending = ref({ experiments: false, vocabulary: false, notes: false })
const updatedAt = ref(0)
const guard = createRequestGuard()
const recent = workspaceNavigation.recent
const canReadNotes = computed(() => authStore.has('note:read') && authStore.has('research:use'))
const canReadExperiments = computed(() => authStore.hasAny('experiment:read', 'experiment:read:any'))
const book = computed(() => vocabulary.value?.books.find(book => book.id === vocabulary.value?.settings.selectedBookId))
const actions = computed(() => [
  { title: '记一篇笔记', detail: '写下想法，整理所学', to: '/app/notes/new', icon: 'edit', permission: 'note:write' },
  { title: '开始背单词', detail: '新词与复习，接着学', to: '/app/vocabulary', icon: 'book', permission: 'vocabulary:use' },
  { title: '浏览知识库', detail: '按主题查找学习资料', to: '/app/knowledge', icon: 'folder', permission: 'knowledge:read' },
  { title: '识别一张图片', detail: '提取文字，理解内容', to: '/app/upload', icon: 'images', permission: 'experiment:run' },
].filter(item => authStore.has(item.permission)))

async function load() {
  if (!authStore.state.user) return
  const run = guard.start()
  loading.value = true
  const settle = async <T,>(key: keyof typeof pending.value, work: () => Promise<T>, apply: (value: T) => void) => {
    pending.value[key] = true
    errors.value[key] = false
    try { const value = await work(); if (run.current()) apply(value) }
    catch { if (run.current()) errors.value[key] = true }
    finally { if (run.current()) pending.value[key] = false }
  }
  await Promise.all([
    settle('experiments', () => authStore.has('dashboard:read') ? api.dashboard(run.signal) : Promise.resolve(null), value => { summary.value = value }),
    settle('vocabulary', () => authStore.has('vocabulary:use') ? vocabularyApi.dashboard(run.signal) : Promise.resolve(null), value => { vocabulary.value = value }),
    settle('notes', () => canReadNotes.value ? researchApi.search({ q: '', type: 'NOTE', sort: 'recent', page: '0' }, run.signal) : Promise.resolve(null), value => { notes.value = value?.items.slice(0, 4) || [] }),
  ])
  if (!run.current()) return
  loading.value = false
  updatedAt.value = Date.now()
}
watch(() => [authStore.state.user?.id, authStore.state.user?.permissions.join('|')], () => {
  guard.cancel()
  summary.value = null; vocabulary.value = null; notes.value = []
  errors.value = { experiments: false, vocabulary: false, notes: false }
  if (authStore.state.user) void load()
}, { immediate: true })
function onVisible() { if (document.visibilityState === 'visible' && !loading.value && Date.now() - updatedAt.value > 60000) void load() }
onMounted(() => document.addEventListener('visibilitychange', onVisible))
onBeforeUnmount(() => { guard.cancel(); document.removeEventListener('visibilitychange', onVisible) })
const date = (value: string) => Number.isFinite(Date.parse(value)) ? new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric' }).format(new Date(value)) : '—'
</script>

<template>
  <div class="workspace-home">
    <header class="workspace-welcome">
      <AmbientWaves />
      <div><p class="page-kicker">{{ uiText('个人工作空间 / OVERVIEW') }}</p><h1>{{ uiText('欢迎回来，') }}<span>{{ authStore.state.user?.displayName || uiText('你') }}</span>。</h1><p>{{ uiText('继续学习，记录想法，把每一次探索留在这里。') }}</p></div>
      <button class="button button--ghost" :disabled="loading" @click="load"><AppIcon name="clock" :size="15" />{{ loading ? uiText('正在更新…') : uiText('刷新概览') }}</button>
    </header>
    <nav v-if="actions.length" class="workspace-action-grid" :style="{ '--action-count': actions.length }" :aria-label="uiText('快速开始')">
      <RouterLink v-for="(item, index) in actions" :key="item.to" :to="item.to"><span class="workspace-action-top"><AppIcon :name="item.icon" :size="21" /><small>0{{ index + 1 }}</small></span><strong>{{ uiText(item.title) }}</strong><span>{{ uiText(item.detail) }}<AppIcon name="arrow" :size="16" /></span></RouterLink>
    </nav>
    <TodayLearning />
    <div class="workspace-home-columns">
      <section class="workspace-notes" aria-labelledby="recent-notes-title">
        <header class="workspace-section-head"><div><p class="page-kicker">CONTINUE WRITING</p><h2 id="recent-notes-title">{{ uiText('接着上次的思路') }}</h2></div><RouterLink v-if="authStore.has('note:read')" to="/app/notes" class="text-arrow">{{ uiText('全部笔记') }} <AppIcon name="arrow" :size="14" /></RouterLink></header>
        <p v-if="pending.notes" class="workspace-placeholder" role="status">{{ uiText('正在读取最近记录…') }}</p>
        <div v-else-if="errors.notes" class="workspace-placeholder" role="alert"><p>{{ uiText('最近笔记暂时未能加载，其他功能仍可使用。') }}</p><button class="button" @click="load">{{ uiText('重新加载') }}</button></div>
        <div v-else-if="notes.length" class="workspace-note-list">
          <RouterLink v-for="note in notes" :key="note.id" :to="authStore.has('note:write') ? `/app/notes/${encodeURIComponent(note.id)}/edit` : '/app/notes'"><span class="workspace-note-icon"><AppIcon name="note" :size="18" /></span><span><strong>{{ note.title || uiText('未命名笔记') }}</strong><small>{{ note.excerpt || uiText('打开笔记，继续整理。') }}</small></span><time :datetime="note.updatedAt">{{ date(note.updatedAt) }}</time><AppIcon name="arrow" :size="15" /></RouterLink>
        </div>
        <div v-else class="workspace-placeholder"><AppIcon name="note" :size="27" /><h3>{{ canReadNotes ? uiText('你的下一篇笔记，从这里开始。') : uiText('留一处空间，整理你的想法。') }}</h3><p>{{ canReadNotes ? uiText('最近编辑的笔记会出现在这里，方便随时接着写。') : uiText('从知识库阅读资料，或进入笔记列表查看已有内容。') }}</p><RouterLink v-if="authStore.has('note:write')" to="/app/notes/new" class="button button--dark">{{ uiText('新建笔记') }} <AppIcon name="plus" :size="15" /></RouterLink><RouterLink v-else-if="authStore.has('note:read')" to="/app/notes" class="text-arrow">{{ uiText('打开笔记 →') }}</RouterLink><RouterLink v-else to="/docs" class="text-arrow">{{ uiText('阅读使用指南 →') }}</RouterLink></div>
        <div v-if="authStore.has('knowledge:read')" class="workspace-library-link"><AppIcon name="folder" :size="18" /><div><strong>{{ uiText('把零散的知识，放回主题里。') }}</strong><span>{{ uiText('英语、计算机与自己的学习资料') }}</span></div><RouterLink to="/app/knowledge">{{ uiText('打开知识库') }} <span aria-hidden="true">↗</span></RouterLink></div>
      </section>
      <aside class="workspace-home-aside">
        <section v-if="authStore.has('vocabulary:use')" class="workspace-learning" aria-labelledby="today-learning-title">
          <div class="workspace-learning-label"><span class="page-kicker">DAILY PRACTICE</span><AppIcon name="book" :size="19" /></div><h2 id="today-learning-title">{{ uiText('今天，也学一点。') }}</h2>
          <p v-if="pending.vocabulary" role="status">{{ uiText('正在读取学习进度…') }}</p><p v-else-if="errors.vocabulary" role="alert">{{ uiText('学习进度暂未加载，可进入词书查看。') }}</p>
          <template v-else-if="vocabulary"><p>{{ book?.title || uiText('选一本词书，开始积累') }}</p><div class="workspace-learning-stats"><div><strong>{{ vocabulary.today.learned }}</strong><span>{{ uiText('今日学习') }}</span></div><div><strong>{{ vocabulary.dueTotal }}</strong><span>{{ uiText('待复习') }}</span></div><div><strong>{{ vocabulary.streak }}<small> {{ uiText('天') }}</small></strong><span>{{ uiText('连续学习') }}</span></div></div></template>
          <RouterLink to="/app/vocabulary" class="button button--dark">{{ vocabulary?.dueTotal ? uiText('去复习') : uiText('继续学习') }}<AppIcon name="arrow" :size="16" /></RouterLink>
        </section>
        <section class="workspace-recent" aria-labelledby="recent-pages-title"><header><h2 id="recent-pages-title">{{ uiText('最近访问') }}</h2><button v-if="recent.length" @click="workspaceNavigation.clearRecent()" :aria-label="uiText('清除最近访问')">{{ uiText('清除') }}</button></header><nav v-if="recent.length" :aria-label="uiText('最近访问页面')"><RouterLink v-for="item in recent" :key="item.to" :to="item.to"><AppIcon :name="item.icon" :size="16" />{{ uiText(item.label) }}<AppIcon name="chevron" :size="13" /></RouterLink></nav><p v-else>{{ uiText('访问过的页面会出现在这里。') }}</p><small>{{ uiText('仅在此浏览器保存，按账号分开。') }}</small></section>
      </aside>
    </div>
    <section class="workspace-experiments" aria-labelledby="recent-experiments-title">
      <header class="workspace-section-head"><div><p class="page-kicker">EXPERIMENT LOG</p><h2 id="recent-experiments-title">{{ uiText('实验记录') }}</h2></div><RouterLink v-if="canReadExperiments" to="/app/comparisons" class="text-arrow">{{ uiText('全部实验') }} <AppIcon name="arrow" :size="14" /></RouterLink></header>
      <p v-if="errors.experiments" role="alert">{{ uiText('实验概览暂时未能加载。') }}<button class="button button--small" :disabled="loading" @click="load">{{ uiText('重试') }}</button></p>
      <template v-else-if="summary"><div class="workspace-experiment-stats"><span>{{ uiText('累计') }} <strong>{{ summary.totalTasks }}</strong> {{ uiText('次') }}</span><span>{{ uiText('已完成') }} <strong>{{ summary.completedTasks }}</strong> {{ uiText('次') }}</span><span>{{ uiText('失败') }} <strong>{{ summary.failedTasks }}</strong> {{ uiText('次') }}</span><small>{{ uiText('当前可访问范围 · 演示结果不代表真实识别准确率') }}</small></div>
        <div v-if="canReadExperiments && summary.recentTasks.length" class="data-table-wrap"><table class="data-table"><thead><tr><th>{{ uiText('文件') }}</th><th>{{ uiText('模型') }}</th><th>{{ uiText('状态') }}</th><th>{{ uiText('创建日期') }}</th><th :aria-label="uiText('操作')"></th></tr></thead><tbody><tr v-for="task in summary.recentTasks.slice(0, 5)" :key="task.id"><td>{{ task.inputFile.originalName }}</td><td>{{ task.model.name }}</td><td><StatusBadge :status="task.status" /></td><td>{{ date(task.createdAt) }}</td><td><RouterLink :to="`/app/comparisons?task=${task.id}`">{{ uiText('查看 →') }}</RouterLink></td></tr></tbody></table></div>
        <p v-else class="workspace-experiment-empty">{{ canReadExperiments ? uiText('还没有实验记录。需要识别图片时，从实验台开始。') : uiText('当前账号可查看汇总，实验明细需要相应权限。') }}<RouterLink v-if="authStore.has('experiment:run')" to="/app/upload">{{ uiText('打开实验台 →') }}</RouterLink></p>
      </template><p v-else-if="pending.experiments" role="status">{{ uiText('正在读取实验概览…') }}</p>
    </section>
    <footer class="workspace-home-footer"><span>{{ uiText('学习 · 记录 · 探索') }}</span><nav :aria-label="uiText('工作台帮助')"><RouterLink to="/docs">{{ uiText('使用指南') }}</RouterLink><RouterLink v-if="authStore.has('billing:read')" to="/app/billing">{{ uiText('账单与用量') }}</RouterLink><RouterLink to="/app/settings">{{ uiText('个人设置') }}</RouterLink></nav></footer>
  </div>
</template>
