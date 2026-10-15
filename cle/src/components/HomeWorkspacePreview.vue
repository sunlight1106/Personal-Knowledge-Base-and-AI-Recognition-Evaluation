<script setup lang="ts">
import { uiText } from '@/i18n'

import { computed, nextTick, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from './AppIcon.vue'

const tabs = [
  { id: 'notes', label: '记录', icon: 'docs', to: '/app/notes', action: '打开我的笔记' },
  { id: 'words', label: '学习', icon: 'book', to: '/app/vocabulary', action: '开始每日学习' },
  { id: 'images', label: '探索', icon: 'images', to: '/app/upload', action: '进入识别实验' },
]
const selected = ref(0)
const active = computed(() => tabs[selected.value]!)
const tablist = ref<HTMLElement>()
async function navigate(event: KeyboardEvent) {
  const offsets: Record<string, number> = { ArrowRight: 1, ArrowLeft: -1 }
  if (event.key === 'Home') selected.value = 0
  else if (event.key === 'End') selected.value = tabs.length - 1
  else if (event.key in offsets) selected.value = (selected.value + offsets[event.key]! + tabs.length) % tabs.length
  else return
  event.preventDefault()
  await nextTick()
  tablist.value?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[selected.value]?.focus()
}
</script>

<template>
  <section class="workspace-preview" :aria-label="uiText('工作空间预览')">
    <header class="preview-caption"><span>YOUR NEXT PAGE</span><span>{{ uiText('界面示例') }} <i aria-hidden="true" /></span></header>
    <div ref="tablist" class="preview-tabs" role="tablist" :aria-label="uiText('预览内容')" @keydown="navigate">
      <button v-for="(tab, index) in tabs" :id="`preview-tab-${tab.id}`" :key="tab.id" role="tab" :aria-selected="selected === index" :aria-controls="`preview-panel-${tab.id}`" :tabindex="selected === index ? 0 : -1" @click="selected = index"><AppIcon :name="tab.icon" :size="15" />{{ uiText(tab.label) }}</button>
    </div>
    <div v-for="(tab, index) in tabs" v-show="selected === index" :id="`preview-panel-${tab.id}`" :key="tab.id" class="preview-page" role="tabpanel" :aria-labelledby="`preview-tab-${tab.id}`" tabindex="0">
      <template v-if="tab.id === 'notes'">
        <div class="preview-path"><AppIcon name="folder" :size="13" />{{ uiText('我的知识库') }}<span>/</span>{{ uiText('思考与记录') }}</div>
        <span class="preview-overline">{{ uiText('一页笔记，一点新的理解') }}</span>
        <h2>{{ uiText('把想法，连成自己的知识。') }}</h2>
        <p>{{ uiText('读到一句话，记下一个问题。') }}<br />{{ uiText('从零散的片段，慢慢长出自己的答案。') }}</p>
        <blockquote>{{ uiText('记录，是思考的另一种方式。') }}</blockquote>
        <div class="preview-tags"><span>{{ uiText('阅读笔记') }}</span><span>{{ uiText('灵感') }}</span><span>{{ uiText('知识连接') }}</span></div>
      </template>
      <template v-else-if="tab.id === 'words'">
        <div class="preview-path"><AppIcon name="book" :size="13" />{{ uiText('每日学习') }}<span>/</span>{{ uiText('场景与搭配') }}</div>
        <span class="preview-overline">{{ uiText('从一个词，走进一种表达') }}</span>
        <h2 class="preview-word">a little, every day.</h2>
        <p>{{ uiText('每天一点，慢慢积累。') }}</p>
        <blockquote><strong>make progress</strong><br />{{ uiText('取得进步 · We make progress by practising.') }}</blockquote>
        <div class="preview-tags"><span>{{ uiText('听读') }}</span><span>{{ uiText('搭配回忆') }}</span><span>{{ uiText('间隔复习') }}</span></div>
      </template>
      <template v-else>
        <div class="preview-path"><AppIcon name="spark" :size="13" />{{ uiText('识别实验') }}<span>/</span>{{ uiText('从观察开始') }}</div>
        <span class="preview-overline">{{ uiText('让每一次探索，都有据可循') }}</span>
        <h2>{{ uiText('带着问题，看懂一张图片。') }}</h2>
        <p>{{ uiText('选择图片与模型，查看识别结果。') }}<br />{{ uiText('将值得留下的发现，接着写进笔记。') }}</p>
        <div class="preview-flow"><span><AppIcon name="images" :size="19" />{{ uiText('图片') }}</span><AppIcon name="arrow" :size="15" /><span><AppIcon name="model" :size="19" />{{ uiText('理解') }}</span><AppIcon name="arrow" :size="15" /><span><AppIcon name="docs" :size="19" />{{ uiText('记录') }}</span></div>
        <div class="preview-tags"><span>{{ uiText('图片理解') }}</span><span>{{ uiText('结果对照') }}</span><span>{{ uiText('保存发现') }}</span></div>
      </template>
    </div>
    <footer class="preview-footer"><span>{{ uiText('留给下一次思考') }}</span><RouterLink :to="active.to">{{ uiText(active.action) }}<AppIcon name="arrow" :size="16" /></RouterLink></footer>
  </section>
</template>

<style scoped>
.workspace-preview{position:relative;min-width:0;background:var(--paper);border:1px solid var(--line);border-radius:18px;box-shadow:var(--shadow-raised);isolation:isolate}
.workspace-preview::before{content:'';position:absolute;inset:12px -10px -12px 10px;background:var(--code-paper);border:1px solid var(--line);border-radius:18px;z-index:-2}
.workspace-preview::after{content:'';position:absolute;inset:0;z-index:-1;background:var(--paper);border-radius:inherit}
.preview-caption{display:flex;align-items:center;justify-content:space-between;padding:23px 28px 18px;color:var(--muted);font:10px/1.5 ui-monospace,Consolas,monospace;letter-spacing:.1em}
.preview-caption>span:last-child{display:flex;align-items:center;gap:9px;letter-spacing:0;font-size:11px}
.preview-caption i{width:5px;height:5px;border-radius:50%;background:var(--green)}
.preview-tabs{display:flex;gap:6px;padding:0 28px 18px;border-bottom:1px solid var(--line-soft)}
.preview-tabs button{display:flex;align-items:center;justify-content:center;gap:8px;flex:1;padding:10px;border:1px solid transparent;border-radius:8px;background:transparent;color:var(--muted);font:inherit;font-size:13px;cursor:pointer;transition:background .18s,color .18s}
.preview-tabs button:hover{background:var(--hover-paper);color:var(--green)}
.preview-tabs button[aria-selected=true]{background:var(--green-soft);border-color:var(--line);color:var(--green);font-weight:600}
.preview-page{padding:24px 32px 30px;min-height:338px;outline-offset:-4px}
.preview-path{display:flex;gap:9px;align-items:center;color:var(--muted);font-size:11px;margin-bottom:30px}
.preview-path>span{color:var(--line)}
.preview-overline{font-size:11px;color:var(--muted);letter-spacing:.07em}
.preview-page h2{font-size:23px;line-height:1.6;font-weight:500;letter-spacing:-.025em;color:var(--ink-strong);margin:12px 0}
.preview-page .preview-word{font:italic 32px/1.5 Georgia,serif;letter-spacing:-.025em}
.preview-page p{font-size:13px;line-height:2;color:var(--ink-soft);margin:0}
.preview-page blockquote{border:0;border-left:2px solid var(--green);margin:22px 0;padding:11px 15px;border-radius:0 6px 6px 0;background:var(--code-paper);font-size:13px;line-height:1.8;color:var(--ink-soft)}
.preview-page blockquote strong{color:var(--green);font-weight:500}
.preview-tags{display:flex;gap:7px;flex-wrap:wrap;margin-top:22px}
.preview-tags span{padding:4px 9px;background:var(--canvas);border:1px solid var(--line-soft);border-radius:5px;font-size:10px;color:var(--muted)}
.preview-flow{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:22px;color:var(--muted)}
.preview-flow>span{display:flex;align-items:center;justify-content:center;gap:8px;flex:1;padding:14px 8px;border:1px solid var(--line);border-radius:8px;color:var(--green);background:var(--code-paper);font-size:12px}
.preview-footer{display:flex;align-items:center;justify-content:space-between;gap:15px;padding:20px 28px;border-top:1px solid var(--line-soft);font-size:11px;color:var(--muted)}
.preview-footer a{display:flex;align-items:center;gap:12px;font-size:12px;color:var(--green);text-decoration:none}
.preview-footer a:hover{color:var(--ink-strong)}
@media(prefers-reduced-motion:no-preference){:global([data-motion=on]) .preview-page{animation:preview-appear .25s ease-out}@keyframes preview-appear{from{opacity:.5;translate:0 4px}to{opacity:1;translate:0 0}}}
@media(prefers-reduced-motion:reduce){.preview-tabs button{transition:none}}
:global([data-motion=off]) .preview-tabs button{transition:none}
@media(max-width:800px){.preview-page{padding-inline:24px}.preview-caption,.preview-tabs,.preview-footer{padding-inline:24px}}
</style>
