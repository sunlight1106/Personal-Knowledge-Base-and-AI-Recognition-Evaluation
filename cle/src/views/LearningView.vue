<script setup lang="ts">
import { uiText } from '@/i18n'

import DailyStudyPlan from '@/components/DailyStudyPlan.vue'
import LearningStatistics from '@/components/LearningStatistics.vue'
import TodayLearning from '@/components/TodayLearning.vue'
</script>
<template><div class="page-shell"><header class="page-heading"><p class="page-kicker">MY LEARNING</p><h1>{{ uiText('每一天，都有积累') }}</h1><p>{{ uiText('查看自己的学习记录，接着上次的进度继续。') }}</p></header><p><RouterLink to="/offline">{{ uiText('准备离线笔记与词汇 →') }}</RouterLink></p><DailyStudyPlan/><TodayLearning/><LearningStatistics/></div></template>
