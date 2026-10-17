<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import LanguageToggle from '@/components/LanguageToggle.vue'
import AmbientWaves from '@/components/AmbientWaves.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppLogo from '@/components/AppLogo.vue'
import { uiText } from '@/i18n'
import { authStore } from '@/stores/auth'

const route = useRoute()
const isPublic = computed(() => route.path === '/models')
// Provider families, not task-specific demo records or claims about model availability.
const providers = [
  { id: 'MOONSHOT', name: 'Kimi', initial: 'K', subtitle: 'Moonshot AI', tone: 'ink' },
  { id: 'QWEN', name: '千问', initial: 'Q', subtitle: 'Alibaba Cloud', tone: 'violet' },
  { id: 'DEEPSEEK', name: 'DeepSeek', initial: 'D', subtitle: 'DeepSeek', tone: 'blue' },
  { id: 'OPENAI', name: 'OpenAI', initial: 'O', subtitle: 'OpenAI', tone: 'sage' },
  { id: 'GEMINI', name: 'Gemini', initial: 'G', subtitle: 'Google', tone: 'blue' },
  { id: 'ANTHROPIC', name: 'Claude', initial: 'C', subtitle: 'Anthropic', tone: 'clay' },
  { id: 'XAI', name: 'Grok', initial: 'x', subtitle: 'xAI', tone: 'ink' },
  { id: 'LLAMA', name: 'Llama', initial: 'L', subtitle: 'Groq · Together', tone: 'sage' },
  { id: 'CUSTOM', name: '自定义服务', initial: '+', subtitle: 'OpenAI compatible', tone: 'violet' },
]
const canConfigure = computed(() => !authStore.state.user || authStore.has('personal-ai:manage'))
function target(id: string) {
  const path = `/app/settings?section=ai&provider=${id}`
  return authStore.state.user ? path : { path: '/login', query: { redirect: path } }
}
</script>
<template>
  <div :class="{ 'models-public-shell': isPublic }">
    <header v-if="isPublic" class="docs-public-nav"><RouterLink to="/"><AppLogo /></RouterLink><nav><RouterLink to="/">{{ uiText('首页') }}</RouterLink><RouterLink to="/docs">{{ uiText('文档') }}</RouterLink></nav><div><LanguageToggle /><RouterLink :to="authStore.state.user ? '/app/home' : '/login'" class="pp-enter-button">{{ uiText('进入平台') }} <AppIcon name="arrow" :size="15" /></RouterLink></div></header>
    <component :is="isPublic ? 'main' : 'div'" class="page-stack models-page provider-catalog" :class="{ 'models-page--public': isPublic }">
      <section class="provider-intro water-surface"><AmbientWaves variant="pool" /><div><p class="page-kicker">YOUR MODELS, YOUR SPACE</p><h2>{{ uiText('选择你的 AI') }}</h2><p>{{ uiText('从熟悉的名字开始。选择服务，再连接自己的模型。') }}</p></div><span class="provider-intro-note"><i />{{ uiText('按账号独立配置') }}</span></section>
      <p v-if="!canConfigure" class="inline-alert" role="status">{{ uiText('当前账号没有个人 AI 配置权限，可联系管理员调整。') }}</p>
      <section class="provider-grid" :aria-label="uiText('AI 服务')">
        <article v-for="(item, index) in providers" :key="item.id" class="provider-card" :class="`provider-tone--${item.tone}`">
          <div class="provider-card-top"><span class="provider-monogram" aria-hidden="true">{{ item.initial }}</span><small>{{ String(index + 1).padStart(2, '0') }}</small></div>
          <div><h3>{{ uiText(item.name) }}</h3><p>{{ item.subtitle }}</p></div>
          <RouterLink v-if="canConfigure" :to="target(item.id)" class="provider-select" :aria-label="`${uiText('选择服务')} ${uiText(item.name)}`"><span>{{ uiText('选择服务') }}</span><AppIcon name="arrow" :size="19" /></RouterLink>
          <span v-else class="provider-readonly">{{ uiText('仅浏览') }}</span>
        </article>
      </section>
      <footer class="provider-footer"><AppIcon name="shield" :size="21" /><p>{{ uiText('密钥仅保存在你的账号下。进入设置后可查询模型目录，选择实际可用的模型。') }}</p><RouterLink to="/docs#model-contract">{{ uiText('配置说明') }} <AppIcon name="arrow" :size="16" /></RouterLink></footer>
    </component>
  </div>
</template>
<style scoped>
.provider-catalog{gap:32px}.provider-intro{display:flex;align-items:end;justify-content:space-between;gap:24px;padding:48px 0 38px;border-bottom:1px solid var(--line)}.provider-intro>.ambient-waves{left:35%;opacity:.55}.provider-intro h2{font-size:clamp(32px,3.4vw,52px);font-weight:500;letter-spacing:-.045em;margin:14px 0 20px;line-height:1.2}.provider-intro p{max-width:540px;color:var(--ink-soft)}.provider-intro .page-kicker{font-size:11px;letter-spacing:.18em;color:var(--muted)}.provider-intro-note{display:flex;align-items:center;gap:9px;white-space:nowrap;color:var(--muted);font-size:12px;padding-bottom:6px}.provider-intro-note i{width:6px;height:6px;border-radius:50%;background:var(--green)}
.provider-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}.provider-card{--provider-color:var(--green);position:relative;display:flex;flex-direction:column;gap:24px;min-width:0;padding:30px;border:1px solid var(--line);border-radius:22px;background:var(--surface-sheen);box-shadow:var(--shadow-soft)}.provider-card-top{display:flex;justify-content:space-between;align-items:start}.provider-card-top small{font:12px ui-monospace,monospace;color:var(--muted);opacity:.7}.provider-monogram{display:grid;place-items:center;width:52px;height:52px;border-radius:17px;background:color-mix(in srgb,var(--provider-color) 9%,var(--paper));color:var(--provider-color);font:500 27px Georgia,serif;border:1px solid color-mix(in srgb,var(--provider-color) 16%,var(--line-soft))}.provider-tone--violet{--provider-color:var(--violet)}.provider-tone--blue{--provider-color:var(--blue)}.provider-tone--clay{--provider-color:var(--amber)}.provider-tone--ink{--provider-color:var(--ink)}.provider-card h3{font-size:26px;font-weight:500;letter-spacing:-.03em;line-height:1.3;margin:0 0 5px}.provider-card p{font-size:13px;color:var(--muted);margin:0}.provider-select{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:auto;padding-top:19px;border-top:1px solid var(--line-soft);text-decoration:none;font-size:13px;color:var(--green)}.provider-select::after{content:'';position:absolute;inset:0;border-radius:22px}.provider-select:focus-visible{outline:none}.provider-card:has(.provider-select:focus-visible){outline:2px solid var(--green);outline-offset:3px}.provider-readonly{font-size:13px;color:var(--muted);padding-top:19px;border-top:1px solid var(--line-soft)}
.provider-footer{display:flex;align-items:center;gap:14px;padding:12px 0 28px;color:var(--muted);font-size:13px}.provider-footer p{flex:1;line-height:1.8}.provider-footer a{display:flex;gap:8px;align-items:center;white-space:nowrap;text-decoration:none}.provider-footer>svg{flex-shrink:0}
@media(prefers-reduced-motion:no-preference){[data-motion=on] .provider-card{transition:border-color .3s,box-shadow .3s,background .3s}[data-motion=on] .provider-card:has(a):hover{box-shadow:var(--shadow-raised);border-color:color-mix(in srgb,var(--green) 45%,var(--line))}}
@media(max-width:900px){.provider-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.provider-intro-note{display:none}}@media(max-width:560px){.provider-grid{grid-template-columns:1fr}.provider-intro{padding-top:24px}.provider-footer{flex-wrap:wrap}.provider-card{padding:24px}}
</style>
