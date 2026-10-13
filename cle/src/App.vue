<script setup lang="ts">
import { RouterView } from 'vue-router'
import ToastStack from '@/components/ToastStack.vue'
import { useMotionPreferences } from '@/lib/motion'
import { useSurfaceRipples } from '@/lib/useSurfaceRipples'
useMotionPreferences()
useSurfaceRipples()
</script>

<template>
  <RouterView />
  <ToastStack />
</template>

