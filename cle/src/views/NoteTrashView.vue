<script setup lang="ts">
import NoteTrashPanel from '@/components/NoteTrashPanel.vue'
import { uiText } from '@/i18n'
</script>
<template><div class="page-stack"><RouterLink class="table-action" to="/app/notes">← {{ uiText('返回笔记列表') }}</RouterLink><NoteTrashPanel /></div></template>
