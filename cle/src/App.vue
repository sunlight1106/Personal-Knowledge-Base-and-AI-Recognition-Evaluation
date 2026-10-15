<script setup lang="ts">
import { RouterView, useRoute } from 'vue-router'
import LanguageToggle from '@/components/LanguageToggle.vue'
import ToastStack from '@/components/ToastStack.vue'
import { useMotionPreferences } from '@/lib/motion'
import { useSurfaceRipples } from '@/lib/useSurfaceRipples'
useMotionPreferences()
useSurfaceRipples()
const route = useRoute()
</script>

<template>
  <RouterView />
  <div v-if="!['/', '/docs', '/models'].includes(route.path) && !route.path.startsWith('/app')" class="public-language"><LanguageToggle /></div>
  <ToastStack />
</template>

<style>
.public-language{position:fixed;z-index:30;top:22px;right:28px}
html[lang=en-US] .workspace-header{flex-wrap:wrap;row-gap:10px}
html[lang=en-US] .workspace-quick{flex-wrap:wrap}
html[lang=en-US] .workspace-brand small{letter-spacing:.08em}
html[lang=en-US] .home-navigation{gap:20px}
html[lang=en-US] .home-hero h1{font-size:clamp(40px,4.6vw,72px);line-height:1.14}
html[lang=en-US] .home-hero-description{max-width:36em;line-height:1.8}
html[lang=en-US] .directory-item a{white-space:normal}
html[lang=en-US] .page-header h1{overflow-wrap:break-word}
</style>

