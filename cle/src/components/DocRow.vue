<script setup lang="ts">
import { uiText } from '@/i18n'

defineProps<{ id: string; title: string; intro?: boolean }>()
</script>
<template><section :id="id" class="lab-row" :class="{ 'lab-row-intro': intro }"><div class="lab-prose"><component :is="intro ? 'h1' : 'h2'" class="lab-heading"><a class="heading-anchor" :href="`#${id}`" :aria-label="(uiText('定位到') + uiText(title))">#</a>{{ uiText(title) }}</component><slot /></div><div class="lab-detail"><slot name="detail" /></div></section></template>
