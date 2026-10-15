<script setup lang="ts">
import { uiText } from '@/i18n'

import { RouterLink } from 'vue-router'
import { authStore } from '@/stores/auth'
</script>
<template>
  <section class="research-workflow">
    <p class="research-eyebrow">EXPERIMENT NOTES</p><h2>{{ uiText('从问题出发，留下可复现的记录。') }}</h2>
    <p>{{ uiText('选择输入与模型，检查基线和优化输出，记录观察与结论。') }}</p>
    <ol><li><span>01</span> {{ uiText('准备素材与输入') }}</li><li><span>02</span> {{ uiText('运行识别与对照') }}</li><li><span>03</span> {{ uiText('整理结果与笔记') }}</li></ol>
    <RouterLink v-if="authStore.has('experiment:run')" to="/app/upload">{{ uiText('开始一次实验 →') }}</RouterLink>
    <RouterLink v-else to="/docs">{{ uiText('阅读实验指南 →') }}</RouterLink>
  </section>
</template>
