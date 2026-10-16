<script setup lang="ts">
import SelectField from '@/components/SelectField.vue'
import { uiRoleName, uiText, uiLocale } from '@/i18n'

import { computed, nextTick, onMounted, onBeforeUnmount, ref, reactive, watch } from 'vue'
import { api, ApiClientError } from '@/api/client'
import { adminApi, type UserDetail } from '@/api/admin'
import type { PermissionView, RoleView, UserView } from '@/types/api'
import { authStore } from '@/stores/auth'
import IdentityCode from './IdentityCode.vue'
import LearningStatistics from './LearningStatistics.vue'
const props = defineProps<{ id: number; roles: RoleView[]; permissions: PermissionView[] }>()
const emit = defineEmits<{ close: []; updated: [user: UserView] }>()
const dialog = ref<HTMLDialogElement | null>(null), detail = ref<UserDetail | null>(null)
const error = ref(''), busy = ref(false), loading = ref(true), saved = ref(''), expiry = ref('')
const choices = reactive<Record<string, 'inherit' | 'allow' | 'deny'>>({})
const profile = reactive({ displayName: '', email: '', password: '', roleId: 0, status: 'ACTIVE' as 'ACTIVE' | 'DISABLED' })
const admin = computed(() => authStore.state.user?.roleCode === 'ADMIN'), self = computed(() => props.id === authStore.state.user?.id)
const groups = computed(() => [...new Set(props.permissions.map(p => p.group))])
const effective = computed(() => props.permissions.filter(p => choices[p.code] === 'allow' || (choices[p.code] !== 'deny' && detail.value?.access.rolePermissions.includes(p.code))).length)
let active = true
function localTime(value: string | null) { if (!value) return ''; const d = new Date(value); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16) }
async function load() {
  const data = await adminApi.detail(props.id); if (!active) return
  detail.value = data; Object.assign(profile, { displayName: data.user.displayName, email: data.user.email, roleId: data.user.roleId, status: data.user.status, password: '' }); expiry.value = localTime(data.access.expiresAt)
  props.permissions.forEach(p => choices[p.code] = data.access.denies.includes(p.code) ? 'deny' : data.access.grants.includes(p.code) ? 'allow' : 'inherit')
}
async function run(task: () => Promise<void>, message: string) {
  if (busy.value) return; busy.value = true; error.value = ''; saved.value = ''
  try { await task(); if (active) { await load(); saved.value = message } }
  catch (reason) { if (active) error.value = reason instanceof ApiClientError ? reason.message : '操作失败，请重试' }
  finally { profile.password = ''; if (active) busy.value = false }
}
function saveProfile() {
  void run(async () => {
    const payload: Partial<{ displayName: string; email: string; password: string; roleId: number; status: 'ACTIVE' | 'DISABLED' }> = { displayName: profile.displayName.trim() }
    if (admin.value && detail.value) {
      if (profile.email.trim() !== detail.value.user.email) payload.email = profile.email.trim()
      if (profile.password) payload.password = profile.password
      if (profile.roleId !== detail.value.user.roleId) payload.roleId = profile.roleId
      if (profile.status !== detail.value.user.status) payload.status = profile.status
    }
    const user = await api.updateUser(props.id, payload); if (active) { if (self.value) authStore.state.user = user; emit('updated', user) }
  }, '账号资料已保存。安全设置变化后，用户需重新登录。')
}
function saveAccess() {
  void run(async () => {
    await adminApi.access(props.id, { grants: props.permissions.filter(p => choices[p.code] === 'allow').map(p => p.code), denies: props.permissions.filter(p => choices[p.code] === 'deny').map(p => p.code), expiresAt: expiry.value ? new Date(expiry.value).toISOString() : null })
    const data = await adminApi.detail(props.id); if (active) emit('updated', data.user)
  }, '使用权限已保存，旧登录已撤销。重新登录后生效。')
}
function inheritAll() { props.permissions.forEach(p => choices[p.code] = 'inherit') }
watch(() => props.permissions, catalog => { if (detail.value) catalog.forEach(p => choices[p.code] = detail.value!.access.denies.includes(p.code) ? 'deny' : detail.value!.access.grants.includes(p.code) ? 'allow' : 'inherit') })
function backdrop(event: MouseEvent) {
  if (busy.value || event.target !== dialog.value || !dialog.value) return
  const box = dialog.value.getBoundingClientRect()
  if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) emit('close')
}
function revoke() { if (window.confirm(uiText('撤销此用户的全部登录设备？用户需要重新登录。'))) void run(() => adminApi.revoke(props.id), '全部登录会话已撤销。') }
onMounted(async () => { await nextTick(); dialog.value?.showModal(); try { await load() } catch (reason) { error.value = reason instanceof ApiClientError ? reason.message : '用户详情加载失败' } finally { if (active) loading.value = false } })
onBeforeUnmount(() => { active = false; profile.password = ''; dialog.value?.close() })
const date = (value: string | null) => value ? new Date(value).toLocaleString(uiLocale(), { hour12: false }) : '暂无记录'
</script>
<template>
  <dialog ref="dialog" class="user-detail-dialog" @cancel="busy ? $event.preventDefault() : emit('close')" @click="backdrop">
    <header><div><p class="page-kicker">{{ uiText('账号详情') }}</p><h3>{{ detail?.user.displayName || uiText('用户详情') }}</h3></div><button class="icon-button" :aria-label="uiText('关闭用户详情')" :disabled="busy" @click="emit('close')">×</button></header>
    <p v-if="error" class="inline-alert inline-alert--error" role="alert">{{ uiText(error) }}</p><p v-if="saved" class="inline-alert" role="status">{{ saved }}</p><p v-if="loading" role="status">{{ uiText('正在读取用户详情…') }}</p>
    <template v-if="detail">
      <div class="user-detail-identity"><span>@{{ detail.user.username }} · {{ uiRoleName(detail.user.roleName, detail.user.roleCode) }}</span><IdentityCode :value="detail.user.identityCode" /></div>
      <details v-if="authStore.has('admin:stats')" class="user-detail-section"><summary>{{ uiText('查看每天与累计学习统计') }}</summary><LearningStatistics :user-id="id" /></details>
      <div class="user-detail-metrics"><div><strong>{{ detail.notes }}</strong><small>{{ uiText('笔记') }}</small></div><div><strong>{{ detail.files }}</strong><small>{{ uiText('文件') }}</small></div><div><strong>{{ detail.aiCalls }}</strong><small>{{ uiText('AI 调用') }}</small></div><div><strong>{{ detail.experiments }}</strong><small>{{ uiText('实验') }}</small></div><div><strong>{{ detail.activeSessions }}</strong><small>{{ uiText('有效登录') }}</small></div></div>
      <p class="user-detail-meta">{{ uiText('最近登录：') }}{{ date(detail.lastLoginAt) }} {{ uiText('· 文件') }} {{ (detail.storageBytes / 1024 ** 2).toFixed(1) }} {{ uiText('MB · 创建于') }} {{ date(detail.user.createdAt) }}</p>
      <form class="user-detail-section" @submit.prevent="saveProfile"><h4>{{ uiText('账号资料') }}</h4><div class="user-detail-fields"><label class="field-label">{{ uiText('显示名称') }}<input v-model="profile.displayName" class="field-input" required maxlength="80" :disabled="busy || !authStore.has('user:write')" /></label><label class="field-label">{{ uiText('邮箱') }}<input v-model="profile.email" class="field-input" type="email" required maxlength="160" :disabled="busy || !admin || self" /></label><label v-if="admin && roles.length" class="field-label">{{ uiText('角色') }}<SelectField><select v-model.number="profile.roleId" class="field-input" :disabled="busy || self"><option v-for="r in roles" :key="r.id" :value="r.id">{{ uiRoleName(r.name, r.code) }}</option></select></SelectField></label><label v-if="admin" class="field-label">{{ uiText('账号状态') }}<SelectField><select v-model="profile.status" class="field-input" :disabled="busy || self"><option value="ACTIVE">{{ uiText('启用') }}</option><option value="DISABLED">{{ uiText('停用') }}</option></select></SelectField></label><label v-if="admin && !self" class="field-label">{{ uiText('重置密码') }}<input v-model="profile.password" class="field-input" type="password" minlength="8" maxlength="72" autocomplete="new-password" :placeholder="uiText('留空保留原密码')" :disabled="busy" /></label></div><div class="user-detail-actions"><button v-if="authStore.has('user:write')" class="button button--dark" :disabled="busy">{{ uiText('保存资料') }}</button><button v-if="admin && !self" type="button" class="button button--ghost" :disabled="busy" @click="revoke">{{ uiText('撤销全部登录') }}</button></div></form>
      <form v-if="admin && detail.user.roleCode !== 'ADMIN' && permissions.length" class="user-detail-section" @submit.prevent="saveAccess"><header class="access-header"><div><h4>{{ uiText('使用权限') }}</h4><p>{{ uiText('默认跟随角色。单独允许或禁止只影响此用户。') }}</p></div><button type="button" class="button button--ghost" :disabled="busy" @click="inheritAll">{{ uiText('全部恢复跟随角色') }}</button></header><label class="field-label expiry-field">{{ uiText('使用有效期') }}<input v-model="expiry" class="field-input" type="datetime-local" :disabled="busy" /><small>{{ uiText('留空表示长期可用。到期后登录和现有会话都不可继续使用。') }}</small></label><div class="individual-permissions"><fieldset v-for="group in groups" :key="group"><legend>{{ uiText(group) }}</legend><label v-for="p in permissions.filter(p => p.group === group)" :key="p.code"><span>{{ uiText(p.label) }}<small>{{ uiText('角色') }}{{ detail.access.rolePermissions.includes(p.code) ? uiText('允许') : uiText('不允许') }}</small></span><SelectField><select v-model="choices[p.code]" :aria-label="uiText(p.label)" :disabled="busy"><option value="inherit">{{ uiText('跟随角色') }}</option><option value="allow">{{ uiText('单独允许') }}</option><option value="deny">{{ uiText('单独禁止') }}</option></select></SelectField></label></fieldset></div><footer class="user-detail-actions"><span>{{ uiText('实际可用') }} {{ effective }} / {{ permissions.length }} {{ uiText('项') }}</span><button class="button button--dark" :disabled="busy">{{ busy ? uiText('保存中…') : uiText('保存使用权限') }}</button></footer><p class="user-detail-meta">{{ uiText('角色与使用权限调整由管理员执行。群内身份不会提升平台权限。') }}</p></form>
      <p v-else-if="detail.user.roleCode === 'ADMIN'" class="inline-alert">{{ uiText('管理员始终拥有全部平台权限，新增权限也会自动包含。') }}</p>
      <section v-else class="user-detail-section"><h4>{{ uiText('实际权限') }}</h4><p>{{ detail.access.effectivePermissions.length }} {{ uiText('项 · 使用期限：') }}{{ detail.access.expiresAt ? date(detail.access.expiresAt) : uiText('长期') }}</p><p class="user-detail-meta">{{ uiText('只有管理员可以调整单独权限。') }}</p></section>
    </template>
  </dialog>
</template>
<style scoped>
.user-detail-dialog{width:min(980px,calc(100vw - 100px));max-height:88vh;overflow-y:auto;border:1px solid var(--line);border-radius:10px;background:var(--paper);color:var(--ink);padding:30px 36px;box-shadow:0 20px 90px #0002}.user-detail-dialog::backdrop{background:rgba(20,29,36,.32)}.user-detail-dialog>header,.access-header{display:flex;justify-content:space-between;align-items:center;gap:24px}.user-detail-dialog h3{font-size:24px;margin:6px 0 20px}.user-detail-dialog h4{font-size:17px;margin:0 0 20px}.user-detail-identity{display:flex;align-items:center;flex-wrap:wrap;gap:20px;color:var(--muted);font-size:13px}.user-detail-metrics{display:grid;grid-template-columns:repeat(5,1fr);border-block:1px solid var(--line);padding:22px 0;margin:24px 0 12px}.user-detail-metrics div{display:flex;flex-direction:column;gap:8px}.user-detail-metrics strong{font-size:24px;font-weight:550}.user-detail-metrics small,.user-detail-meta,.access-header p{font-size:12px;color:var(--muted)}.user-detail-section{margin-top:30px;padding-top:26px;border-top:1px solid var(--line)}.user-detail-fields{display:grid;grid-template-columns:1fr 1fr;gap:20px}.user-detail-actions{display:flex;gap:16px;align-items:center;margin-top:22px;font-size:13px}.access-header h4{margin-bottom:8px}.access-header p{margin:0 0 16px}.expiry-field{max-width:390px;margin:20px 0}.individual-permissions{display:grid;grid-template-columns:1fr 1fr;gap:20px 32px}.individual-permissions fieldset{border:0;padding:0;margin:0;min-width:0}.individual-permissions legend{font-size:13px;color:var(--muted);margin-bottom:10px}.individual-permissions label{display:flex;justify-content:space-between;align-items:center;gap:14px;padding:11px 0;border-bottom:1px solid var(--line-soft);font-size:12px}.individual-permissions small{display:block;color:var(--muted);font-size:11px;margin-top:3px}.individual-permissions select{flex:none;max-width:125px;background:var(--canvas);color:var(--ink);border:1px solid var(--line);border-radius:4px;padding:7px;font:inherit}

.individual-permissions .select-field{flex:none;max-width:125px}
</style>
