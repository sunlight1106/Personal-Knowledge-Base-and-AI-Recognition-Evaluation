<script setup lang="ts">
import LearningStatistics from '@/components/LearningStatistics.vue'
import TodayLearning from '@/components/TodayLearning.vue'
</script>
<template><div class="page-shell"><header class="page-heading"><p class="page-kicker">MY LEARNING</p><h1>每一天，都有积累</h1><p>查看自己的学习记录，接着上次的进度继续。</p></header><p><RouterLink to="/offline">准备离线笔记与词汇 →</RouterLink></p><TodayLearning/><LearningStatistics/></div></template>
