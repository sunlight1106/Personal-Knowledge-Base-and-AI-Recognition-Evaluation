<script setup lang="ts">
import { onMounted,onBeforeUnmount,watch } from 'vue'
import { useRoute } from 'vue-router'
import { authStore } from '@/stores/auth'
import { learningApi } from '@/api/learning'
const route=useRoute()
let timer:ReturnType<typeof setInterval>|undefined,lastAction=0,lastTick=Date.now(),seconds=0,lastSent=0,busy=false
function context(){if(route.path==='/app/vocabulary'&&authStore.has('vocabulary:use'))return 'VOCABULARY';if(route.path.startsWith('/app/notes/')&&authStore.has('note:read'))return 'NOTES';if(route.path==='/app/research'&&route.query.tab==='cards'&&authStore.has('research:use'))return 'CARDS';if(route.path==='/app/knowledge'&&authStore.has('knowledge:read'))return 'KNOWLEDGE';return ''}
function interact(){lastAction=Date.now()}
function reset(){seconds=0;lastTick=Date.now();lastAction=0;lastSent=0}
watch(()=>[route.fullPath,authStore.state.user?.id],reset)
async function tick(){const now=Date.now(),elapsed=Math.min(5,(now-lastTick)/1000);lastTick=now
 if(document.visibilityState==='visible'&&now-lastAction<60000&&context())seconds+=elapsed
 if(busy||!authStore.state.user||!context()||now-lastSent<20000)return
 const value=Math.min(30,Math.floor(seconds));seconds=0;busy=true;lastSent=now
 try{await learningApi.pulse(context(),value)}catch{/* Time reporting must not interrupt learning. */}finally{busy=false}
}
onMounted(()=>{for(const event of ['pointerdown','keydown','scroll'])window.addEventListener(event,interact,{passive:true});timer=setInterval(tick,5000)})
onBeforeUnmount(()=>{if(timer)clearInterval(timer);for(const event of ['pointerdown','keydown','scroll'])window.removeEventListener(event,interact)})
</script>
<template><span hidden aria-hidden="true" /></template>
