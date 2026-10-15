<script setup lang="ts">
import { uiText } from '@/i18n'

import AppIcon from './AppIcon.vue'
withDefaults(defineProps<{ title: string; description?: string; icon?: string }>(), { description: '', icon: 'file' })
</script>

<template>
  <div class="empty-state">
    <span class="empty-icon"><AppIcon :name="icon" :size="25" /></span>
    <strong>{{ uiText(title) }}</strong>
    <p v-if="description">{{ uiText(description) }}</p>
    <slot />
  </div>
</template>
