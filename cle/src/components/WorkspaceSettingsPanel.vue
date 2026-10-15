<script setup lang="ts">
import { uiText } from '@/i18n'

import { RouterLink } from 'vue-router'
</script>
<template>
  <article class="panel settings-card">
    <header><div><h3>{{ uiText('群组与成员') }}</h3><p>{{ uiText('工作空间现在也可用于群组交流、回复和分享资料。') }}</p></div></header>
    <p>{{ uiText('在群组页面统一管理成员与权限，现有工作空间会保留。') }}</p>
    <RouterLink class="button button--ghost" to="/app/groups">{{ uiText('打开群组协作 →') }}</RouterLink>
  </article>
</template>
