<script setup lang="ts">
import { ref } from 'vue'
import { uiText } from '@/i18n'
const props = defineProps<{ message: string; retry: () => Promise<unknown>; disabled?: boolean }>()
const loading = ref(false)
async function reload() {
  if (loading.value || props.disabled) return
  loading.value = true
  try { await props.retry() } finally { loading.value = false }
}
</script>
<template>
  <div v-if="message" class="inline-alert inline-alert--error request-failure" role="alert">
    <p>{{ uiText(message) }}</p>
    <button type="button" class="button button--ghost" :disabled="loading || disabled" @click="reload">{{ loading ? uiText('正在重新加载…') : uiText('重新加载资料') }}</button>
  </div>
</template>
<style scoped>
.request-failure{display:flex;align-items:center;justify-content:space-between;gap:20px;flex-wrap:wrap}
.request-failure p{margin:0;flex:1;min-width:180px;color:inherit}
.request-failure button{flex-shrink:0}
</style>
