<script setup lang="ts">
import LanguageToggle from '@/components/LanguageToggle.vue'
import { uiText } from '@/i18n'

import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import BrandMark from '@/components/BrandMark.vue'
import AmbientWaves from '@/components/AmbientWaves.vue'
import MotionToggle from '@/components/MotionToggle.vue'
import CodeListing from '@/components/CodeListing.vue'
import AppIcon from '@/components/AppIcon.vue'
import HomeWorkspacePreview from '@/components/HomeWorkspacePreview.vue'
import { authStore } from '@/stores/auth'
onMounted(() => { void authStore.ensureUser() })
const enter = computed(() => authStore.state.user ? '/app/home' : '/login')
const chapters = [
  { id: 'knowledge', number: '01', label: 'COLLECT & CONNECT', title: '知识，慢慢成形。', intro: '从一条灵感到完整笔记，把零散的理解连接起来。', links: [{ title: '我的笔记', to: '/app/notes', text: '写作、预览与导出' }, { title: '知识库', to: '/app/knowledge', text: '按主题整理所学' }, { title: '学习资料', to: '/app/research', text: '检索、收藏与复习' }] },
  { id: 'learning', number: '02', label: 'A LITTLE, EVERY DAY', title: '学习，自有节奏。', intro: '用场景理解词义，用搭配建立记忆。每天多记住一点。', links: [{ title: '每日词汇', to: '/app/vocabulary', text: '听读、拼写与间隔复习' }, { title: '英语学习库', to: '/app/notes?library=%E8%8B%B1%E8%AF%AD%E5%AD%A6%E4%B9%A0', text: '积累表达与学习笔记' }, { title: '学习设置', to: '/app/settings?section=workflow', text: '安排自己的练习目标' }] },
  { id: 'recognition', number: '03', label: 'EXPLORE WITH AI', title: '探索，有迹可循。', intro: '带着问题理解图片与资料，保存输入、配置和实验结果。', links: [{ title: '识别实验', to: '/app/upload', text: '图片理解与识别' }, { title: '模型目录', to: '/models', text: '查看模型能力' }, { title: '优化对比', to: '/app/comparisons', text: '并列查看实验结果' }] },
]
</script>
<template>
  <div class="lab-docs lab-home">
    <header class="home-header">
      <RouterLink to="/" class="home-brand"><BrandMark /><span>{{ uiText('个人知识库') }}<small>A SPACE FOR YOUR MIND</small></span></RouterLink>
      <nav class="home-navigation" :aria-label="uiText('首页导航')"><a href="#collection">{{ uiText('探索空间') }}</a><RouterLink to="/docs">{{ uiText('使用文档') }}</RouterLink><RouterLink to="/models">{{ uiText('模型目录') }}</RouterLink><LanguageToggle /><MotionToggle /><RouterLink :to="enter" class="home-enter">{{ authStore.state.user ? uiText('进入工作台') : uiText('登录平台') }}<AppIcon name="arrow" :size="16" /></RouterLink></nav>
    </header>
    <main>
      <section id="introduction" class="home-hero" aria-labelledby="home-title">
        <AmbientWaves />
        <div class="home-hero-copy">
          <p class="home-eyebrow"><span aria-hidden="true" />{{ uiText('个人知识库与 AI 识别评测') }}</p>
          <h1 id="home-title">{{ uiText('让知识有序。') }}<br /><span>{{ uiText('让探索有迹。') }}</span></h1>
          <p class="home-hero-description">{{ uiText('一个安静的空间，收藏知识与灵感。') }}<br />{{ uiText('记录所学，积累词汇，让每一次思考都有下一页。') }}</p>
          <div class="home-intro-actions"><RouterLink :to="enter" class="home-primary">{{ uiText('进入工作台') }} <AppIcon name="arrow" :size="18" /></RouterLink><RouterLink to="/docs">{{ uiText('从使用指南开始') }} <span aria-hidden="true">↗</span></RouterLink></div>
          <p class="home-hero-footnote"><span>{{ uiText('记录') }}</span><i aria-hidden="true" /><span>{{ uiText('学习') }}</span><i aria-hidden="true" /><span>{{ uiText('探索') }}</span></p>
        </div>
        <div class="home-folio-wrap">
          <HomeWorkspacePreview />
          <span class="home-folio-caption" aria-hidden="true">A personal collection. Always growing.</span>
        </div>
      </section>
      <section id="collection" class="home-collection" aria-labelledby="collection-title">
        <header class="home-section-head"><div><p class="home-eyebrow">A PLACE FOR WHAT MATTERS</p><h2 id="collection-title">{{ uiText('为日常积累，留出空间。') }}</h2></div><p>{{ uiText('从今天的小小一步开始。') }}</p></header>
        <div class="home-chapters">
          <section v-for="chapter in chapters" :id="chapter.id" :key="chapter.id" class="home-chapter" :aria-labelledby="`${chapter.id}-title`">
            <div class="home-chapter-label"><span>{{ chapter.number }}</span><small>{{ chapter.label }}</small></div>
            <h3 :id="`${chapter.id}-title`" class="lab-heading"><a class="heading-anchor" :href="`#${chapter.id}`" :aria-label="(uiText('定位到') + (chapter.title))">#</a>{{ uiText(chapter.title) }}</h3><p>{{ uiText(chapter.intro) }}</p>
            <ul class="home-chapter-links"><li v-for="item in chapter.links" :key="item.to"><RouterLink :to="item.to"><div><strong>{{ uiText(item.title) }}</strong><span>{{ uiText(item.text) }}</span></div><AppIcon name="arrow" :size="15" /></RouterLink></li></ul>
          </section>
        </div>
      </section>
      <section id="method" class="home-method" aria-labelledby="method-title">
        <div><p class="home-eyebrow">THOUGHTFUL EXPERIMENTS</p><h2 id="method-title">{{ uiText('不只得到答案，') }}<br />{{ uiText('也看清它如何产生。') }}</h2><p>{{ uiText('对照同一输入的基线与优化输出，保留可回看的记录。置信度变化只是线索，真实标注才是判断准确率的依据。') }}</p><RouterLink to="/docs#interactive-notes">{{ uiText('逐行阅读实验配置 ↗') }}</RouterLink></div>
        <div class="home-method-detail"><div class="home-method-label"><span>{{ uiText('一份实验记录') }}</span><span>INPUT → INSIGHT</span></div><CodeListing id="home-method" code="# 实验流程示意
input → baseline
input → preprocessing → optimized

# 比较输出，保留记录
compare(baseline, optimized)" /><p>{{ uiText('个人模型用于图片识别；双路 DEMO 用于体验对照流程。') }}</p><nav :aria-label="uiText('实验记录')"><RouterLink to="/app/logs">{{ uiText('调用日志 →') }}</RouterLink><RouterLink to="/app/downloads">{{ uiText('下载结果 →') }}</RouterLink></nav></div>
      </section>
    </main>
    <footer class="home-footer"><RouterLink to="/" class="home-brand"><BrandMark /><span>{{ uiText('个人知识库') }}<small>{{ uiText('学习 · 记录 · 探索') }}</small></span></RouterLink><p>{{ uiText('把每一次思考，留给未来的自己。') }}</p><a href="#introduction">{{ uiText('返回顶部 ↑') }}</a></footer>
  </div>
</template>

<style scoped src="@/styles/home.css"></style>
