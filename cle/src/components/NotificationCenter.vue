<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useNotifications } from '@/lib/useNotifications'
import { authStore } from '@/stores/auth'
import type { NotificationItem } from '@/api/research'
import AppIcon from './AppIcon.vue'
const router = useRouter(), menu = ref<HTMLDetailsElement | null>(null)
const { unreadItems, actions, actionTotal, popups, total, dismiss, pause, resume, refresh } = useNotifications()
async function open(item: NotificationItem) {
  if (menu.value) menu.value.open = false
  dismiss(item.id)
  await router.push(item.url)
  window.dispatchEvent(new Event('pkb:live-update'))
}
function leave(event: FocusEvent, id: string) {
  const element = event.currentTarget as HTMLElement
  if (!element.contains(event.relatedTarget as Node) && !element.matches(':hover')) resume(id)
}
function mouseLeave(event: MouseEvent, id: string) { if (!(event.currentTarget as HTMLElement).contains(document.activeElement)) resume(id) }
</script>

<template>
  <details ref="menu" class="notification-menu" @keydown.esc="menu && (menu.open = false)">
    <summary :aria-label="total ? `通知，${total} 条未读消息` : '通知，没有未读消息'" @click="refresh">
      <AppIcon name="bell" :size="16" />通知 <span v-if="total" class="notification-count">{{ total > 99 ? '99+' : total }}</span>
    </summary>
    <div class="notification-list">
      <header><strong>未读消息</strong><small v-if="total">{{ total }} 条</small></header>
      <p v-if="!unreadItems.length" class="notification-empty">没有未读消息</p>
      <button v-for="item in unreadItems" :key="item.id" @click="open(item)"><span>{{ item.title }}</span><small>{{ item.count }}</small></button>
      <section v-if="actions.length" class="notification-actions" aria-label="待处理事项">
        <header><strong>待处理事项</strong><small>{{ actionTotal }} 项</small></header>
        <p>邀请、复习与系统提醒会保留到处理完成，不计入未读消息。</p>
        <button v-for="item in actions" :key="item.id" @click="open(item)"><span>{{ item.title }}</span><small>{{ item.count }}</small></button>
      </section>
      <RouterLink v-if="authStore.has('research:use')" to="/app/research?tab=cards" @click="menu && (menu.open = false)">查看复习计划 →</RouterLink>
    </div>
  </details>
  <Teleport to="body">
    <div class="notification-popups" aria-live="polite" aria-relevant="additions">
      <TransitionGroup name="notification-popup">
        <article v-for="item in popups" :key="item.id" class="notification-popup" @mouseenter="pause(item.id)" @mouseleave="mouseLeave($event, item.id)" @focusin="pause(item.id)" @focusout="leave($event, item.id)">
          <span class="notification-symbol"><AppIcon :name="item.id.startsWith('announcement-') ? 'bell' : 'mail'" :size="18" /></span>
          <button class="notification-open" @click="open(item)"><small>{{ item.id.startsWith('announcement-') ? '新公告' : '新通知' }}</small><strong>{{ item.title }}</strong><span>{{ item.count > 1 ? `${item.count} ${item.kind === 'ACTION' ? '项待处理' : '条未读'} · ` : '' }}点击查看</span></button>
          <button class="notification-dismiss" aria-label="收起提醒" @click="dismiss(item.id)"><AppIcon name="close" :size="16" /></button>
        </article>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.notification-menu{position:relative}.notification-menu summary{display:flex;align-items:center;gap:8px;list-style:none;font-size:13px;padding:8px 12px;cursor:pointer;color:var(--ink)}.notification-menu summary::-webkit-details-marker{display:none}.notification-count{background:var(--green);color:var(--on-dark);border-radius:20px;padding:1px 6px;font-size:10px;font-variant-numeric:tabular-nums}
.notification-actions{border-top:1px solid var(--line);margin-top:18px;padding-top:18px}.notification-actions>p{font-size:11px;line-height:1.7;color:var(--muted);margin:0 0 6px}
.notification-list{position:absolute;right:0;top:44px;z-index:80;width:340px;max-height:440px;overflow:auto;background:var(--paper);border:1px solid var(--line);border-radius:12px;padding:20px;box-shadow:0 16px 48px #20302816}.notification-list header{display:flex;align-items:center;justify-content:space-between;font-size:14px;padding-bottom:12px}.notification-list small{color:var(--muted);font-size:11px}.notification-list button{display:flex;gap:16px;align-items:center;width:100%;padding:13px 8px;border:0;border-bottom:1px solid var(--line);background:none;text-align:left;justify-content:space-between;cursor:pointer;color:var(--ink);font:inherit;font-size:13px}.notification-list button>span{overflow-wrap:anywhere}.notification-list button:hover{background:var(--green-soft)}.notification-empty,.notification-list a{font-size:12px;color:var(--muted)}.notification-list a{display:block;margin-top:16px}
.notification-popups{position:fixed;right:28px;top:86px;z-index:180;display:grid;gap:10px;width:min(360px,calc(100vw - 40px));pointer-events:none}.notification-popup{display:flex;align-items:flex-start;gap:12px;background:var(--paper);border:1px solid var(--line);border-radius:14px;box-shadow:0 16px 48px #18291f1c;padding:18px;pointer-events:auto;color:var(--ink)}.notification-symbol{width:32px;height:32px;display:grid;place-items:center;flex-shrink:0;background:var(--green-soft);color:var(--green);border-radius:50%}.notification-open{display:grid;gap:7px;flex:1;min-width:0;border:0;padding:0;background:none;color:inherit;text-align:left;cursor:pointer;font:inherit}.notification-open small{font-size:10px;color:var(--green);letter-spacing:.08em}.notification-open strong{font-size:14px;line-height:1.5;font-weight:550;overflow-wrap:anywhere;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.notification-open>span{font-size:11px;color:var(--muted)}.notification-dismiss{border:0;padding:3px;background:none;color:var(--muted);cursor:pointer;border-radius:4px}.notification-popup-enter-active,.notification-popup-leave-active{transition:opacity .22s,transform .22s}.notification-popup-enter-from,.notification-popup-leave-to{opacity:0;transform:translateY(-6px)}
@media(prefers-reduced-motion:reduce){.notification-popup-enter-active,.notification-popup-leave-active{transition:none}}
:global(html[data-motion="off"]) .notification-popup-enter-active,:global(html[data-motion="off"]) .notification-popup-leave-active{transition:none}
</style>
