<script setup lang="ts">
import { uiText, uiLanguage } from '@/i18n'

import { ref, watch, onBeforeUnmount, defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'
import { useNotifications } from '@/lib/useNotifications'
import { authStore } from '@/stores/auth'
import type { NotificationItem } from '@/api/research'
import AppIcon from './AppIcon.vue'
import { releaseApi } from '@/api/releases'
const ReleaseAnnouncementDialog = defineAsyncComponent(() => import('./ReleaseAnnouncementDialog.vue').then(module => module.default))
const router = useRouter(), menu = ref<HTMLDetailsElement | null>(null)
const { unreadItems, actions, actionTotal, popups, total, dismiss, pause, resume, refresh, acknowledge } = useNotifications()
const releaseOpen = ref(false), releaseBusy = ref(false), releaseError = ref('')
const t = (zh: string, en: string) => uiLanguage.value === 'en' ? en : zh
let releaseEpoch = 0
watch(() => authStore.state.user?.id, () => { releaseEpoch++; releaseOpen.value = false; releaseBusy.value = false; releaseError.value = '' }, { flush: 'sync' })
onBeforeUnmount(() => { releaseEpoch++ })
async function readRelease(version: string) {
  if (releaseBusy.value) return
  const epoch = releaseEpoch
  releaseBusy.value = true; releaseError.value = ''; pause('release-' + version)
  try {
    await releaseApi.read(version)
    if (epoch !== releaseEpoch) return
    acknowledge('release-' + version); releaseOpen.value = false
  } catch {
    if (epoch === releaseEpoch) releaseError.value = t('未能保存已读状态，请重试。', 'Could not save your read receipt. Please retry.')
  } finally { if (epoch === releaseEpoch) releaseBusy.value = false }
}
function notificationTitle(item: NotificationItem) {
  if (item.kind === 'RELEASE') return `v${item.version} · ${t('更新公告', 'Release announcement')}`
  if (item.id.startsWith('operation-')) return uiText(item.title)
  if (['mail-older', 'note-reviews', 'backup-warning', 'group-invites'].includes(item.id)) return uiText(item.title)
  // Translate only server-authored suffixes; group names, people and message subjects are content.
  if (item.id.startsWith('announcement-') && item.title.endsWith(' · 群公告')) return item.title.slice(0, -6) + ' · ' + uiText('群公告')
  if (item.id.startsWith('request-') && item.title.endsWith(' 请求添加联系人')) return item.title.slice(0, -8) + ' ' + uiText('请求添加联系人')
  return item.title
}
async function open(item: NotificationItem) {
  if (menu.value) menu.value.open = false
  dismiss(item.id)
  if (item.kind === 'RELEASE') { releaseError.value = ''; releaseOpen.value = true; return }
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
    <summary :aria-label="total ? (uiText('通知，') + (total) + uiText(' 条未读消息')) : uiText('通知，没有未读消息')" @click="refresh">
      <AppIcon name="bell" :size="16" />{{ uiText('通知') }} <span v-if="total" class="notification-count">{{ total > 99 ? '99+' : total }}</span>
    </summary>
    <div class="notification-list">
      <header><strong>{{ uiText('未读消息') }}</strong><small v-if="total">{{ total }} {{ uiText('条') }}</small></header>
      <p v-if="!unreadItems.length" class="notification-empty">{{ uiText('没有未读消息') }}</p>
      <template v-for="item in unreadItems" :key="item.id">
        <div v-if="item.kind === 'RELEASE'" class="release-menu-item"><strong>{{ notificationTitle(item) }}</strong><div class="release-actions"><button @click="open(item)">{{ t('查看详情', 'View details') }}</button><button :disabled="releaseBusy" @click="readRelease(item.version!)">{{ t('已读', 'Mark as read') }}</button></div></div>
        <button v-else @click="open(item)"><span>{{ notificationTitle(item) }}</span><small>{{ item.count }}</small></button>
      </template>
      <p v-if="releaseError && !releaseOpen" role="alert" class="release-error">{{ releaseError }}</p>
      <section v-if="actions.length" class="notification-actions" :aria-label="uiText('待处理事项')">
        <header><strong>{{ uiText('待处理事项') }}</strong><small>{{ actionTotal }} {{ uiText('项') }}</small></header>
        <p>{{ uiText('邀请、复习与系统提醒会保留到处理完成，不计入未读消息。') }}</p>
        <button v-for="item in actions" :key="item.id" @click="open(item)"><span>{{ notificationTitle(item) }}</span><small>{{ item.count }}</small></button>
      </section>
      <RouterLink v-if="authStore.has('research:use')" to="/app/research?tab=cards" @click="menu && (menu.open = false)">{{ uiText('查看复习计划 →') }}</RouterLink>
    </div>
  </details>
  <Teleport to="body">
    <div class="notification-popups" aria-live="polite" aria-relevant="additions">
      <TransitionGroup name="notification-popup">
        <article v-for="item in popups" :key="item.id" class="notification-popup" @mouseenter="pause(item.id)" @mouseleave="mouseLeave($event, item.id)" @focusin="pause(item.id)" @focusout="leave($event, item.id)">
          <span class="notification-symbol"><AppIcon :name="item.kind === 'RELEASE' || item.id.startsWith('announcement-') ? 'bell' : 'mail'" :size="18" /></span>
          <div v-if="item.kind === 'RELEASE'" class="release-popup-content"><small>{{ t('新版本已更新', 'Your app has been updated') }}</small><strong>{{ notificationTitle(item) }}</strong><p>{{ t('了解本次新增功能、体验优化和修复的问题。', 'See new features, improvements and fixes.') }}</p><div class="release-actions"><button @click="open(item)">{{ t('查看详情', 'View details') }}</button><button :disabled="releaseBusy" @click="readRelease(item.version!)">{{ releaseBusy ? t('保存中…', 'Saving…') : t('已读', 'Mark as read') }}</button></div><p v-if="releaseError" class="release-error" role="alert">{{ releaseError }}</p></div>
          <template v-else><button class="notification-open" @click="open(item)"><small>{{ item.id.startsWith('announcement-') ? uiText('新公告') : uiText('新通知') }}</small><strong>{{ notificationTitle(item) }}</strong><span>{{ item.count > 1 ? `${item.count} ${item.kind === 'ACTION' ? uiText('项待处理') : uiText('条未读')} · ` : '' }}{{ uiText('点击查看') }}</span></button><button class="notification-dismiss" :aria-label="uiText('收起提醒')" @click="dismiss(item.id)"><AppIcon name="close" :size="16" /></button></template>
        </article>
      </TransitionGroup>
    </div>
  </Teleport>
  <ReleaseAnnouncementDialog v-if="releaseOpen" :busy="releaseBusy" :error="releaseError" @close="releaseOpen = false" @read="readRelease" />
</template>

<style scoped>
.release-menu-item{padding:14px 8px;border-bottom:1px solid var(--line)}.release-menu-item>strong{font-size:13px;font-weight:550}.release-actions{display:flex;gap:10px;margin-top:12px}.release-actions button,.notification-list .release-actions button{width:auto;border:1px solid var(--line);border-radius:8px;background:var(--paper);padding:7px 12px;color:var(--green);font:inherit;font-size:12px;cursor:pointer}.release-actions button:first-child{background:var(--green-soft)}.release-actions button:disabled{opacity:.6;cursor:wait}.release-popup-content{flex:1;min-width:0;display:grid;gap:7px}.release-popup-content>small{font-size:11px;color:var(--green)}.release-popup-content>strong{font-size:15px;font-weight:550}.release-popup-content>p{font-size:12px;color:var(--muted);line-height:1.7;margin:0}.release-popup-content .release-actions{margin-top:5px}.release-error,.release-popup-content>.release-error{color:var(--danger,#a13131);font-size:12px;line-height:1.6}
.notification-menu{position:relative}.notification-menu summary{display:flex;align-items:center;gap:8px;list-style:none;font-size:13px;padding:8px 12px;cursor:pointer;color:var(--ink)}.notification-menu summary::-webkit-details-marker{display:none}.notification-count{background:var(--green);color:var(--on-dark);border-radius:20px;padding:1px 6px;font-size:10px;font-variant-numeric:tabular-nums}
.notification-actions{border-top:1px solid var(--line);margin-top:18px;padding-top:18px}.notification-actions>p{font-size:11px;line-height:1.7;color:var(--muted);margin:0 0 6px}
.notification-list{position:absolute;right:0;top:44px;z-index:80;width:340px;max-height:440px;overflow:auto;background:var(--paper);border:1px solid var(--line);border-radius:12px;padding:20px;box-shadow:0 16px 48px #20302816}.notification-list header{display:flex;align-items:center;justify-content:space-between;font-size:14px;padding-bottom:12px}.notification-list small{color:var(--muted);font-size:11px}.notification-list button{display:flex;gap:16px;align-items:center;width:100%;padding:13px 8px;border:0;border-bottom:1px solid var(--line);background:none;text-align:left;justify-content:space-between;cursor:pointer;color:var(--ink);font:inherit;font-size:13px}.notification-list button>span{overflow-wrap:anywhere}.notification-list button:hover{background:var(--green-soft)}.notification-empty,.notification-list a{font-size:12px;color:var(--muted)}.notification-list a{display:block;margin-top:16px}
.notification-popups{position:fixed;right:28px;top:86px;z-index:180;display:grid;gap:10px;width:min(360px,calc(100vw - 40px));pointer-events:none}.notification-popup{display:flex;align-items:flex-start;gap:12px;background:var(--paper);border:1px solid var(--line);border-radius:14px;box-shadow:0 16px 48px #18291f1c;padding:18px;pointer-events:auto;color:var(--ink)}.notification-symbol{width:32px;height:32px;display:grid;place-items:center;flex-shrink:0;background:var(--green-soft);color:var(--green);border-radius:50%}.notification-open{display:grid;gap:7px;flex:1;min-width:0;border:0;padding:0;background:none;color:inherit;text-align:left;cursor:pointer;font:inherit}.notification-open small{font-size:10px;color:var(--green);letter-spacing:.08em}.notification-open strong{font-size:14px;line-height:1.5;font-weight:550;overflow-wrap:anywhere;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.notification-open>span{font-size:11px;color:var(--muted)}.notification-dismiss{border:0;padding:3px;background:none;color:var(--muted);cursor:pointer;border-radius:4px}.notification-popup-enter-active,.notification-popup-leave-active{transition:opacity .22s,transform .22s}.notification-popup-enter-from,.notification-popup-leave-to{opacity:0;transform:translateY(-6px)}
@media(prefers-reduced-motion:reduce){.notification-popup-enter-active,.notification-popup-leave-active{transition:none}}
html[data-motion="off"] .notification-popup-enter-active,html[data-motion="off"] .notification-popup-leave-active{transition:none}
</style>
