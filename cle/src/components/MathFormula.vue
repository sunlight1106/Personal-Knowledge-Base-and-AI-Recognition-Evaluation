<script setup lang="ts">
import {computed} from 'vue'
import {mathMarkup} from '@/lib/mathNotation'
const props=defineProps<{value:string;display?:boolean}>()
const markup=computed(()=>mathMarkup(props.value,props.display))
</script>
<template><span class="formula-typeset" v-html="markup" /></template>
<style scoped>
.formula-typeset{display:inline-block;max-width:100%;overflow-x:auto;vertical-align:middle;padding:.2em .1em;line-height:1.7}.formula-typeset :deep(math){font-family:'Cambria Math','STIX Two Math',math;white-space:nowrap}
</style>
