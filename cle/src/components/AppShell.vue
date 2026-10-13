<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { sectionLinks as vSectionLinks } from '@/directives/sectionLinks'
import WorkspaceTools from './WorkspaceTools.vue'
import SafetyBanner from './SafetyBanner.vue'
import AppIcon from './AppIcon.vue'
import { authStore } from '@/stores/auth'
import { themeStore } from '@/stores/theme'
import { mainItems, adminItems, supportItems, canVisit, type NavItem } from '@/lib/navigation'
import { workspaceNavigation } from '@/stores/workspaceNavigation'

const route = useRoute()
const router = useRouter()
function handleSessionExpired() { void router.replace('/login') }
onMounted(() => window.addEventListener('personal-platform:session-expired', handleSessionExpired))
onBeforeUnmount(() => window.removeEventListener('personal-platform:session-expired', handleSessionExpired))
const menu = ref<HTMLDetailsElement | null>(null)
function closeMenu() { menu.value?.removeAttribute('open') }
watch(() => route.fullPath, () => { closeMenu(); pinMessage.value = '' })
watch(() => [route.path, authStore.state.user?.id], () => workspaceNavigation.visit(route.path), { immediate: true })
function visible(items: NavItem[]) {
  return items.filter(item => canVisit(item, authStore.state.user?.permissions))
}

const navGroups = computed(() => [
  { label: '探索与创作', items: visible(mainItems.filter(item => ['/app/home', '/app/knowledge', '/app/vocabulary', '/app/notes', '/app/research'].includes(item.to))) },
  { label: '识别与评测', items: visible(mainItems.filter(item => ['/app/upload', '/app/models', '/app/images', '/app/comparisons'].includes(item.to))) },
  { label: '记录与账户', items: visible(mainItems.filter(item => ['/app/logs', '/app/downloads', '/app/billing', '/app/contacts', '/app/mail', '/app/groups'].includes(item.to))) },
])
const title = computed(() => {
  const all = [...mainItems, ...adminItems, ...supportItems]
  return all.find(item => route.path === item.to || route.path.startsWith(item.to + '/'))?.label || '工作空间'
})

const quickItems = workspaceNavigation.pinned
const pinMessage = ref('')
function togglePin(item: NavItem) {
  pinMessage.value = workspaceNavigation.toggle(item.to) ? '常用入口已更新' : '最多固定 5 个入口，请先取消一个'
}

function outside(event: PointerEvent) { if (menu.value?.open && event.target instanceof Node && !menu.value.contains(event.target)) closeMenu() }
onMounted(() => document.addEventListener('pointerdown', outside))
onBeforeUnmount(() => document.removeEventListener('pointerdown', outside))
</script>

<template>
  <div class="portal-app">
    <a class="portal-skip" href="#workspace-content">跳转到内容</a>
    <header class="workspace-header">
      <nav class="workspace-breadcrumb" aria-label="当前位置"><RouterLink to="/" class="workspace-brand"><AppIcon name="book" :size="19" /><span>个人知识库 <small>WORKSPACE</small></span></RouterLink><span>/</span><span>{{ title }}</span></nav>
      <nav class="workspace-quick" aria-label="常用页面">
        <RouterLink v-for="item in quickItems" :key="item.to" :to="item.to">{{ item.label }}</RouterLink>
        <span v-if="!quickItems.length" class="workspace-quick-hint">在「目录与账户」中固定常用页面</span>
      </nav>
      <div class="workspace-tools"><WorkspaceTools v-if="authStore.state.user" :key="authStore.state.user.id" />
        <button class="topbar-tool" :aria-label="themeStore.isDark() ? '切换到浅色' : '切换到暗色'" @click="themeStore.toggle()"><AppIcon :name="themeStore.isDark() ? 'sun' : 'moon'" :size="16" /></button>
        <details ref="menu" class="workspace-directory" @keydown.esc="closeMenu(); menu?.querySelector('summary')?.focus()">
          <summary><AppIcon name="menu" :size="16" />目录与账户</summary>
          <div class="workspace-directory-body">
            <div class="directory-caption"><span>全部页面</span><small>固定到顶部 · {{ quickItems.length }}/5</small></div>
            <nav aria-label="全部页面">
              <section v-for="group in [...navGroups, {label:'系统管理',items:visible(adminItems)}, {label:'帮助',items:supportItems}].filter(group => group.items.length)" :key="group.label">
                <p>{{ group.label }}</p>
                <div v-for="item in group.items" :key="item.to" class="directory-item">
                  <RouterLink :to="item.to">{{ item.label }}</RouterLink>
                  <button class="directory-pin" :aria-label="`${quickItems.some(pin => pin.to === item.to) ? '取消固定' : '固定'}${item.label}`" :aria-pressed="quickItems.some(pin => pin.to === item.to)" @click="togglePin(item)"><AppIcon :name="quickItems.some(pin => pin.to === item.to) ? 'check' : 'plus'" :size="13" /></button>
                </div>
              </section>
            </nav>
            <p class="directory-feedback" role="status">{{ !workspaceNavigation.state.persistent ? '浏览器无法保存设置，本次打开期间仍可使用。' : pinMessage }}</p>
            <footer class="workspace-account">
              <div class="workspace-account-identity"><span class="account-monogram">{{ authStore.state.user?.displayName?.slice(0, 1) || 'U' }}</span><div><strong>{{ authStore.state.user?.displayName }}</strong><small>@{{ authStore.state.user?.username }}</small></div><span class="account-role">{{ authStore.state.user?.roleName }}</span></div>
              <nav class="workspace-account-links" aria-label="账户操作">
                <RouterLink to="/app/settings?section=profile"><AppIcon name="users" :size="15" />个人资料</RouterLink>
                <RouterLink to="/app/settings?section=security"><AppIcon name="shield" :size="15" />密码与登录设备</RouterLink>
                <RouterLink to="/app/settings?section=privacy"><AppIcon name="key" :size="15" />隐私与数据</RouterLink>
                <RouterLink to="/app/settings?section=appearance"><AppIcon name="eye" :size="15" />外观设置</RouterLink>
              </nav>
              <div class="workspace-account-exit"><RouterLink to="/account/switch"><AppIcon name="users" :size="15" />切换账号</RouterLink><RouterLink to="/account/logout"><AppIcon name="logout" :size="15" />退出登录</RouterLink></div>
            </footer>
          </div>
        </details>
      </div>
    </header>
    <main id="workspace-content" v-section-links class="app-content" :class="{'app-content--reader':route.path === '/app/upload'}" tabindex="-1"><SafetyBanner v-if="authStore.state.user" :key="'safety-'+authStore.state.user.id" /><RouterView v-if="authStore.state.user" :key="authStore.state.user.id" /></main>
  </div>
</template>
