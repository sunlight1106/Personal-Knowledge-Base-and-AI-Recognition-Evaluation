<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import DocRow from '@/components/DocRow.vue'
import CodeListing from '@/components/CodeListing.vue'
import AppIcon from '@/components/AppIcon.vue'
import { authStore } from '@/stores/auth'
onMounted(() => { void authStore.ensureUser() })
const enter = computed(() => authStore.state.user ? '/app/home' : '/login')
const chapters = [
  { id: 'recognition', title: '识别与实验', intro: '从原始输入到结构化输出，记录每一次实验。', links: [{ title: '新建识别实验', to: '/app/upload', text: '个人图片识别与本地媒体演示' }, { title: '模型目录', to: '/models', text: '查看模型能力与调用方式' }, { title: '优化对比', to: '/app/comparisons', text: '并列检查基线与优化结果' }] },
  { id: 'knowledge', title: '知识与记录', intro: '把研究过程整理为可检索、可继续的笔记。', links: [{ title: '知识库', to: '/app/knowledge', text: '主题索引与学习资料' }, { title: '我的笔记', to: '/app/notes', text: 'Markdown 编辑与分享' }, { title: '素材库', to: '/app/images', text: '管理实验中的图片与视频' }] },
  { id: 'reproduce', title: '结果与复现', intro: '保留输入、配置和输出，让结论有据可查。', links: [{ title: '调用日志', to: '/app/logs', text: '查询任务状态与运行记录' }, { title: '下载中心', to: '/app/downloads', text: '导出实验文件与结果' }, { title: '使用文档', to: '/docs', text: '部署说明与操作指南' }] },
]
</script>
<template>
  <div class="lab-docs lab-home">
    <header class="lab-header home-header"><RouterLink to="/" class="home-brand"><AppIcon name="book" :size="22" /><span>个人知识库 <small>AI RECOGNITION & EVALUATION</small></span></RouterLink><nav class="lab-header-links" aria-label="首页导航"><RouterLink to="/docs">使用文档</RouterLink><RouterLink to="/models">模型目录</RouterLink><RouterLink :to="enter" class="home-enter">{{ authStore.state.user ? '进入工作台' : '登录平台' }} <AppIcon name="arrow" :size="15" /></RouterLink></nav></header>
    <main>
      <DocRow id="introduction" title="让知识有序，让探索有迹。" intro>
        <p class="home-intro-label">个人知识库与 AI 识别评测</p><p>一个安静的工作空间。记录所学，积累词汇，借助自己的 AI 理解图片与资料。</p>
        <div class="home-intro-actions"><RouterLink :to="enter" class="button button--dark">进入工作台 <AppIcon name="arrow" :size="16" /></RouterLink><RouterLink to="/docs">从使用指南开始 ↗</RouterLink></div>
        <template #detail><div class="home-reading-index"><p class="lab-margin-note">IN THIS WORKSPACE / 工作目录</p><a href="#knowledge"><span>01</span><div><strong>知识与笔记</strong><small>收集、整理，写下自己的理解</small></div><AppIcon name="arrow" :size="16" /></a><RouterLink to="/app/vocabulary"><span>02</span><div><strong>每日学习</strong><small>场景搭配、发音与间隔复习</small></div><AppIcon name="arrow" :size="16" /></RouterLink><a href="#recognition"><span>03</span><div><strong>AI 与识别实验</strong><small>理解输入，检查输出，保留记录</small></div><AppIcon name="arrow" :size="16" /></a></div></template>
      </DocRow>
      <DocRow v-for="chapter in chapters" :key="chapter.id" :id="chapter.id" :title="chapter.title"><p>{{ chapter.intro }}</p><p class="lab-section-description">{{ chapter.id === 'recognition' ? '个人模型用于图片识别；双路 DEMO 输出用于检查流程。' : chapter.id === 'knowledge' ? '按主题整理资料，在 Markdown 与 HTML 中记录，随时导出或分享。' : '从输入到结果，保留下一次实验需要的依据。' }}</p><template #detail><ul class="home-chapter-links"><li v-for="item in chapter.links" :key="item.to"><RouterLink :to="item.to"><div><strong>{{ item.title }}</strong><span>{{ item.text }}</span></div><AppIcon name="arrow" :size="16" /></RouterLink></li></ul></template></DocRow>
      <DocRow id="method" title="同一输入，两条对照路径"><p>分别记录基线和优化结果。置信度变化只是一条线索，真实标注才是判断准确率的依据。</p><RouterLink to="/docs#interactive-notes">逐行阅读实验配置 →</RouterLink><template #detail><CodeListing id="home-method" code="# 实验流程示意
input → baseline
input → preprocessing → optimized

# 比较输出，保留记录
compare(baseline, optimized)" /></template></DocRow>
    </main><footer class="lab-footer"><span>个人知识库与 AI 识别评测</span><a href="#introduction">返回顶部 ↑</a></footer>
  </div>
</template>

<style scoped>
.home-header { display:flex;align-items:center;justify-content:space-between;gap:30px;min-height:88px; }
.home-brand { display:flex;align-items:center;gap:12px;color:var(--ink-strong);font-size:16px;font-weight:600;text-decoration:none; }
.home-brand small { display:block;margin-top:3px;font:9px/1.6 ui-monospace,monospace;letter-spacing:.08em;color:var(--muted); }
.home-header .lab-header-links { margin:0;display:flex;align-items:center;gap:28px;font-size:13px;line-height:1.5; }
.home-enter { display:flex;align-items:center;gap:12px;border-left:1px solid var(--line);padding-left:28px; }
.lab-home :deep(.lab-row-intro) { grid-template-columns:52% minmax(0,48%); }
.lab-home :deep(.lab-row-intro .lab-prose) { padding:78px 50px 72px; }
.lab-home :deep(.lab-row-intro .lab-detail) { background:transparent;border-left:0;display:flex;align-items:center;padding:50px; }
.lab-home :deep(h1.lab-heading) { max-width:12em;text-wrap:balance;font-size:44px;line-height:1.5;letter-spacing:-.04em;margin-bottom:22px; }
.home-intro-label { color:var(--green);font-size:13px!important;letter-spacing:.04em; }
.home-intro-actions { display:flex;align-items:center;gap:24px;flex-wrap:wrap;margin-top:28px; }
.lab-home :deep(.home-intro-actions .button) { padding:11px 20px;background:var(--green);color:var(--on-dark);border-color:var(--green);text-decoration:none;font-size:13px; }
.home-intro-actions>a:last-child { font-size:12px;text-decoration:none; }
.home-reading-index { width:100%;border:1px solid var(--line);border-radius:12px;background:var(--paper);padding:24px 28px;box-shadow:0 8px 32px #17293505; }
.home-reading-index>a { display:flex;align-items:center;gap:20px;padding:18px 10px;margin:0 -10px;border-bottom:1px solid var(--line-soft);border-radius:6px;text-decoration:none!important;transition:background .15s; }
.home-reading-index>a:last-child { border-bottom:0;padding-bottom:4px; }.home-reading-index>a>span { font:11px ui-monospace,monospace;color:var(--green);background:var(--green-soft);padding:8px;border-radius:6px; }.home-reading-index>a>div { flex:1; }.home-reading-index strong { color:var(--ink);font-size:16px;font-weight:500; }.home-reading-index small { display:block;font-size:12px;color:var(--muted);margin-top:5px; }.home-reading-index>a:hover strong { color:var(--green); }.home-reading-index>a:hover { background:var(--hover-paper); }
.home-chapter-links { list-style:none;margin:0;padding:0; }.home-chapter-links li+li { border-top:1px solid var(--line); }.home-chapter-links a { display:flex;align-items:center;justify-content:space-between;gap:20px;padding:15px 10px;text-decoration:none!important;border-radius:4px; }.home-chapter-links a:hover { background:var(--paper); }.home-chapter-links strong { font-size:15px;font-weight:500;color:var(--ink); }.home-chapter-links span { display:block;font-size:12px;margin-top:4px;color:var(--muted); }.home-chapter-links svg { flex-shrink:0; }
@media(max-width:800px) { .home-header { flex-wrap:wrap; }.lab-home :deep(.lab-row-intro) { grid-template-columns:1fr; }.lab-home :deep(.lab-row-intro .lab-prose),.lab-home :deep(.lab-row-intro .lab-detail) { padding:32px; }.lab-home :deep(h1.lab-heading) { font-size:32px; }.home-header .lab-header-links { gap:18px; }.home-enter { padding-left:18px; } }
</style>
