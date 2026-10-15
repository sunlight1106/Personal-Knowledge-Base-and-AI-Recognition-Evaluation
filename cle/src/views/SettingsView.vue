<script setup lang="ts">
import { uiRoleName, uiText, uiLocale, uiLanguage, setUiLanguage } from '@/i18n'

import AccountSecurity from '@/components/AccountSecurity.vue'
import AccountBindings from '@/components/AccountBindings.vue'
import AccountActivity from '@/components/AccountActivity.vue'
import AccountClosure from '@/components/AccountClosure.vue'
import WorkflowPreferences from '@/components/WorkflowPreferences.vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiClientError } from '@/api/client'
import { personalApi } from '@/api/personal'
import type { AccountUsage, AccountSession, PersonalAiUsage } from '@/types/personal'
import { authStore } from '@/stores/auth'
import { themePalettes, themeStore } from '@/stores/theme'
import type { Density, ThemePreference } from '@/stores/theme'
import { toastStore } from '@/stores/toast'
import AppIcon from '@/components/AppIcon.vue'
import PersonalAiSettings from '@/components/PersonalAiSettings.vue'
import WorkspaceSettingsPanel from '@/components/WorkspaceSettingsPanel.vue'
import IdentityCode from '@/components/IdentityCode.vue'

const router = useRouter(), route = useRoute()
const tabs = computed(() => [
  { id: 'profile', label: '个人资料', icon: 'users' },
  ...(authStore.hasAny('personal-ai:manage', 'training:use') ? [{ id: 'ai', label: '个人 AI', icon: 'ai' }] : []),
  { id: 'usage', label: '使用情况', icon: 'logs' },
  { id: 'security', label: '安全与登录', icon: 'shield' },
  { id: 'account', label: '账号与绑定', icon: 'users' },
  { id: 'workflow', label: '通知与学习计划', icon: 'settings' },
  { id: 'privacy', label: '隐私与数据', icon: 'key' },
  { id: 'appearance', label: '外观', icon: 'eye' },
  ...(authStore.has('workspace:manage') && authStore.has('group:use') ? [{ id: 'workspace', label: '工作空间', icon: 'settings' }] : []),
])
const section = computed(() => tabs.value.some(tab => tab.id === route.query.section) ? String(route.query.section) : 'profile')
const profile = reactive({ displayName: '', email: '', currentPassword: '' })
const passwords = reactive({ current: '', next: '', confirm: '' })
const securityPassword = ref(''), exportPassword = ref('')
const busy = ref(''), error = ref(''), loading = ref(false)
const sessions = ref<AccountSession[]>([]), usage = ref<PersonalAiUsage[]>([])
const usageSummary = ref<AccountUsage | null>(null)
const usageLoaded = ref(false), sessionsLoaded = ref(false)
let active = true
let loadVersion = 0
const usageTotals = computed(() => ({ count: usage.value.length, succeeded: usage.value.filter(item => item.status === 'SUCCEEDED').length, input: usage.value.reduce((sum, item) => sum + (item.inputTokens || 0), 0), output: usage.value.reduce((sum, item) => sum + (item.outputTokens || 0), 0), unknown: usage.value.filter(item => item.inputTokens == null || item.outputTokens == null).length }))
const emailChanged = computed(() => profile.email.trim() !== authStore.state.user?.email)
const fmt = (date?: string) => date ? new Date(date).toLocaleString(uiLocale(), { hour12: false }) : '—'
const formatBytes = (bytes: number) => bytes >= 1024 ** 3 ? `${(bytes / 1024 ** 3).toFixed(2)} GB` : bytes >= 1024 ** 2 ? `${(bytes / 1024 ** 2).toFixed(2)} MB` : `${(bytes / 1024).toFixed(1)} KB`
const actionLabel: Record<string, string> = { summarize: '摘要', outline: '大纲', tags: '标签', tidy: '格式整理', draft: '报告草稿', recognize_receipt: '票据识别', recognize_plate: '车牌识别', understand_image: '图片理解' }
function clearSecrets() { profile.currentPassword = ''; passwords.current = ''; passwords.next = ''; passwords.confirm = ''; securityPassword.value = ''; exportPassword.value = '' }
function syncProfile() { profile.displayName = authStore.state.user?.displayName || ''; profile.email = authStore.state.user?.email || ''; profile.currentPassword = '' }
async function select(id: string) { if (busy.value) return; clearSecrets(); await router.replace({ query: { ...route.query, section: id } }) }
async function loadSection() {
  const version = ++loadVersion, current = section.value
  loading.value = true; error.value = ''
  try {
    if (current === 'profile') {
      const user = await personalApi.profile()
      if (!active || version !== loadVersion) return
      authStore.state.user = user; syncProfile()
    } else if (current === 'usage') {
      const [rows, aggregate] = await Promise.all([personalApi.usage(), personalApi.accountUsage()])
      if (!active || version !== loadVersion) return
      usage.value = rows; usageSummary.value = aggregate; usageLoaded.value = true
    } else if (current === 'security') {
      const rows = await personalApi.sessions()
      if (!active || version !== loadVersion) return
      sessions.value = rows; sessionsLoaded.value = true
    }
  } catch (reason) { if (active && version === loadVersion) error.value = reason instanceof ApiClientError ? reason.message : '设置加载失败，请重试' }
  finally { if (active && version === loadVersion) loading.value = false }
}
async function run(name: string, task: () => Promise<void>) {
  if (busy.value) return
  busy.value = name; error.value = ''
  try { await task() }
  catch (reason) { if (active) error.value = reason instanceof ApiClientError ? reason.message : '操作失败，请重试' }
  finally { clearSecrets(); if (active) busy.value = '' }
}
function saveProfile() {
  void run('profile', async () => {
    const user = await personalApi.updateProfile({ displayName: profile.displayName.trim(), email: profile.email.trim(), currentPassword: emailChanged.value ? profile.currentPassword : undefined })
    if (!active) return
    authStore.state.user = user; syncProfile(); toastStore.success('个人资料已更新')
  })
}
function changePassword() {
  if (passwords.next !== passwords.confirm) { error.value = '两次输入的新密码不一致'; return }
  if (!window.confirm(uiText('修改密码后，所有设备的登录都会失效，你需要重新登录。继续？'))) return
  void run('password', async () => {
    await personalApi.changePassword(passwords.current, passwords.next)
    authStore.clearSession(); toastStore.success('密码已更新，请重新登录'); await router.replace('/login')
  })
}
function revoke(session?: AccountSession) {
  if (!window.confirm(session?.current ? '退出当前会话并返回登录页？' : session ? '撤销此登录会话？该设备需要重新登录。' : '退出其他所有会话？当前会话将保留。')) return
  void run('sessions', async () => {
    if (session) await personalApi.revokeSession(session.id, securityPassword.value)
    else await personalApi.revokeOtherSessions(securityPassword.value)
    if (session?.current) { authStore.clearSession(); await router.replace('/login'); return }
    await loadSection(); toastStore.success('会话已撤销')
  })
}
function exportData() {
  void run('export', async () => {
    const data = await personalApi.exportData(exportPassword.value)
    if (!active) return
    const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json;charset=utf-8' }))
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = `pkb-ai-evaluation-data-${new Date().toISOString().slice(0, 10)}.json`
    document.body.appendChild(anchor); anchor.click(); anchor.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000)
    toastStore.success('个人数据导出已开始下载')
  })
}
watch(section, () => { clearSecrets(); void loadSection() })
onMounted(() => { syncProfile(); void loadSection() })
onBeforeUnmount(() => { active = false; loadVersion++; clearSecrets() })
</script>

<template>
  <div class="page-stack settings-page personal-settings">
    <section class="page-intro"><p class="page-kicker">YOUR ACCOUNT</p><h2>{{ uiText('个人设置') }}</h2><p>{{ uiText('管理你的资料、个人 AI、使用记录和数据。所有个人设置均属于当前账户。') }}</p></section>
    <div class="personal-settings-layout">
      <aside class="panel settings-navigation">
        <div class="settings-identity"><span class="avatar">{{ authStore.state.user?.displayName?.slice(0, 1) || 'U' }}</span><div><strong>{{ authStore.state.user?.displayName }}</strong><small>@{{ authStore.state.user?.username }}</small></div></div>
        <nav :aria-label="uiText('个人设置分类')"><button v-for="tab in tabs" :key="tab.id" :aria-current="section === tab.id ? 'page' : undefined" :class="{ active: section === tab.id }" :disabled="!!busy" @click="select(tab.id)"><AppIcon :name="tab.icon" :size="17" />{{ uiText(tab.label) }}</button></nav>
        <p class="settings-nav-foot">{{ uiRoleName(authStore.state.user?.roleName, authStore.state.user?.roleCode) }}</p>
      </aside>
      <section class="settings-content" :aria-label="uiText('设置内容')" :aria-busy="loading || !!busy">
        <p v-if="error" class="inline-alert inline-alert--error" role="alert">{{ uiText(error) }} <button class="table-action" :disabled="!!busy" @click="loadSection">{{ uiText('重新加载') }}</button></p>
        <article v-if="section === 'profile'" class="panel settings-card">
          <header><div><h3>{{ uiText('个人资料') }}</h3><p>{{ uiText('用于笔记署名、工作空间和站内交流。') }}</p></div><AppIcon name="users" :size="22" /></header>
          <form class="settings-form" @submit.prevent="saveProfile">
            <div class="field-label">{{ uiText('身份码 · Identity code') }}<IdentityCode :value="authStore.state.user?.identityCode" prominent /><small>{{ uiText('系统分配的唯一身份标识，永久固定。可复制给对方，用于查找并添加你。') }}</small></div>
            <label class="field-label">{{ uiText('用户名') }}<input :value="authStore.state.user?.username" class="field-input" readonly /><small>{{ uiText('登录用户名不可更改。') }}</small></label>
            <div class="settings-fields"><label class="field-label">{{ uiText('显示名称') }}<input v-model="profile.displayName" class="field-input" required maxlength="80" :disabled="!!busy || loading" /></label><label class="field-label">{{ uiText('电子邮箱') }}<input v-model="profile.email" class="field-input" type="email" required maxlength="160" :disabled="!!busy || loading" /></label></div>
            <label v-if="emailChanged" class="field-label">{{ uiText('确认当前密码以修改邮箱') }}<input v-model="profile.currentPassword" class="field-input" type="password" autocomplete="current-password" required :disabled="!!busy" /></label>
            <dl class="settings-facts"><dt>{{ uiText('角色') }}</dt><dd>{{ uiRoleName(authStore.state.user?.roleName, authStore.state.user?.roleCode) }}</dd><dt>{{ uiText('注册时间') }}</dt><dd>{{ fmt(authStore.state.user?.createdAt) }}</dd></dl>
            <div class="settings-button-row"><button class="button button--dark" :disabled="!!busy || loading">{{ busy === 'profile' ? uiText('保存中…') : uiText('保存资料') }}</button><button type="button" class="button button--ghost" :disabled="!!busy" @click="syncProfile">{{ uiText('取消更改') }}</button></div>
          </form>
        </article>
        <PersonalAiSettings v-else-if="section === 'ai'" />
        <template v-else-if="section === 'usage'">
          <article v-if="usageSummary" class="panel settings-card"><header><div><h3>{{ uiText('我的使用概览') }}</h3><p>{{ uiText('当前账户的全部历史汇总，由服务端按归属统计。') }}</p></div></header><div class="settings-metrics settings-metrics--overview"><div><small>{{ uiText('个人 AI 调用') }}</small><strong>{{ usageSummary.ai.total.toLocaleString(uiLocale()) }}</strong><small>{{ uiText('成功') }} {{ usageSummary.ai.succeeded }} {{ uiText('· 失败') }} {{ usageSummary.ai.failed }}</small></div><div><small>{{ uiText('个人图片识别结果') }}</small><strong>{{ usageSummary.recognitionCount.toLocaleString(uiLocale()) }}</strong><small>{{ uiText('已保存到当前账户') }}</small></div><div><small>{{ uiText('平台实验') }}</small><strong>{{ usageSummary.experiments.total.toLocaleString(uiLocale()) }}</strong><small>{{ uiText('完成') }} {{ usageSummary.experiments.completed }} {{ uiText('· 失败') }} {{ usageSummary.experiments.failed }}</small></div><div><small>{{ uiText('文件存储') }}</small><strong>{{ formatBytes(usageSummary.files.bytes) }}</strong><small>{{ usageSummary.files.count.toLocaleString(uiLocale()) }} {{ uiText('个文件') }}</small></div><div><small>{{ uiText('我的笔记') }}</small><strong>{{ usageSummary.noteCount.toLocaleString(uiLocale()) }}</strong><small>{{ uiText('当前账户的笔记总数') }}</small></div><div><small>{{ uiText('累计已报告 Tokens') }}</small><strong>{{ (usageSummary.ai.knownInputTokens + usageSummary.ai.knownOutputTokens).toLocaleString(uiLocale()) }}</strong><small>{{ uiText('输入') }} {{ usageSummary.ai.knownInputTokens.toLocaleString(uiLocale()) }} {{ uiText('· 输出') }} {{ usageSummary.ai.knownOutputTokens.toLocaleString(uiLocale()) }}</small></div></div><p class="field-hint">{{ usageSummary.ai.unknownUsageCalls }} {{ uiText('次调用没有完整用量数据，不计入已报告 Token 合计。这里不估算供应商费用。') }}</p></article>
          <article class="panel settings-card"><header><div><h3>{{ uiText('个人 AI 使用情况') }}</h3><p>{{ uiText('最近最多 100 次调用的真实记录。未发起调用的预览不会计入。') }}</p></div><button class="button button--ghost button--small" :disabled="loading" @click="loadSection">{{ uiText('刷新') }}</button></header>
            <div class="settings-metrics"><div><small>{{ uiText('本页调用记录') }}</small><strong>{{ usageTotals.count }}</strong></div><div><small>{{ uiText('本页成功次数') }}</small><strong>{{ usageTotals.succeeded }}</strong></div><div><small>{{ uiText('本页已报告输入 Tokens') }}</small><strong>{{ usageTotals.input.toLocaleString(uiLocale()) }}</strong></div><div><small>{{ uiText('本页已报告输出 Tokens') }}</small><strong>{{ usageTotals.output.toLocaleString(uiLocale()) }}</strong></div></div>
            <p class="settings-notice">{{ uiText('这里展示供应商返回的 Token 用量，不是费用账单。') }}{{ usageTotals.unknown ? ((usageTotals.unknown) + uiText(' 条记录缺少完整 Token 数据。')) : '' }}{{ uiText('失败或停止等待的请求仍可能被供应商计费。实际费用、余额和额度请在你的供应商账户查看。') }}</p>
            <p v-if="loading" role="status">{{ uiText('正在加载…') }}</p><p v-else-if="usageLoaded && !usage.length" class="settings-empty">{{ uiText('还没有个人 AI 调用。从笔记中选择个人模型并确认发送后，记录会显示在这里。') }}</p>
            <div v-else-if="usage.length" class="data-table-wrap"><table class="data-table"><thead><tr><th>{{ uiText('时间 / 操作') }}</th><th>{{ uiText('供应商 / 模型') }}</th><th>{{ uiText('状态') }}</th><th>{{ uiText('输入 / 输出 Tokens') }}</th></tr></thead><tbody><tr v-for="row in usage" :key="row.id"><td><strong>{{ uiText(actionLabel[row.action] || row.action) }}</strong><small class="settings-table-sub">{{ fmt(row.createdAt) }}</small></td><td>{{ row.provider }}<small class="settings-table-sub">{{ row.model }}</small></td><td>{{ row.status === 'SUCCEEDED' ? uiText('成功') : uiText('失败') }}<small v-if="row.errorCode" class="settings-table-sub">{{ row.errorCode }}</small></td><td>{{ row.inputTokens ?? uiText('未提供') }} / {{ row.outputTokens ?? uiText('未提供') }}</td></tr></tbody></table></div>
          </article>
          <RouterLink v-if="authStore.has('billing:read')" to="/app/billing" class="settings-inline-link">{{ uiText('查看平台实验余额与充值 →') }}</RouterLink>
        </template>
        <template v-else-if="section === 'security'"><AccountSecurity /><AccountActivity />
          <article class="panel settings-card"><header><div><h3>{{ uiText('修改密码') }}</h3><p>{{ uiText('修改成功后，所有会话（含当前会话）立即失效。') }}</p></div><AppIcon name="shield" :size="22" /></header><form class="settings-form" @submit.prevent="changePassword"><label class="field-label">{{ uiText('当前密码') }}<input v-model="passwords.current" type="password" class="field-input" autocomplete="current-password" required :disabled="!!busy" /></label><div class="settings-fields"><label class="field-label">{{ uiText('新密码') }}<input v-model="passwords.next" type="password" class="field-input" autocomplete="new-password" minlength="8" maxlength="72" required :disabled="!!busy" /></label><label class="field-label">{{ uiText('再次输入新密码') }}<input v-model="passwords.confirm" type="password" class="field-input" autocomplete="new-password" minlength="8" maxlength="72" required :disabled="!!busy" /></label></div><small>{{ uiText('至少 8 个字符。请为这个账户使用独立密码。') }}</small><button class="button button--dark" :disabled="!!busy">{{ busy === 'password' ? uiText('更新中…') : uiText('更新密码并重新登录') }}</button></form></article>
          <article class="panel settings-card"><header><div><h3>{{ uiText('登录会话') }}</h3><p>{{ uiText('查看仍然有效的会话，撤销不再使用的登录。') }}</p></div><button class="button button--ghost button--small" :disabled="loading || !!busy" @click="loadSection">{{ uiText('刷新') }}</button></header><label class="field-label">{{ uiText('当前密码（撤销会话时确认）') }}<input v-model="securityPassword" class="field-input" type="password" autocomplete="current-password" :disabled="!!busy" /></label><p v-if="loading" role="status">{{ uiText('正在加载会话…') }}</p><p v-else-if="sessionsLoaded && !sessions.length" class="settings-empty">{{ uiText('没有可展示的有效会话。') }}</p><div class="settings-session-list"><div v-for="item in sessions" :key="item.id" class="settings-session"><AppIcon name="shield" :size="18" /><div><strong>{{ item.current ? uiText('当前会话') : uiText('其他会话') }}</strong><small>{{ item.userAgent || uiText('未记录设备信息') }}</small><small>{{ uiText('登录') }} {{ fmt(item.createdAt) }} {{ uiText('· 到期') }} {{ fmt(item.expiresAt) }}</small><small v-if="item.lastSeenAt">{{ uiText('最近使用') }} {{ fmt(item.lastSeenAt) }}</small></div><button class="button button--ghost button--small" :disabled="!!busy || !securityPassword" @click="revoke(item)">{{ item.current ? uiText('退出') : uiText('撤销') }}</button></div></div><button class="button button--ghost" :disabled="!!busy || !securityPassword || !sessions.some(item => !item.current)" @click="revoke()">{{ uiText('退出其他所有会话') }}</button></article>
        </template>
        <article v-else-if="section === 'privacy'" class="panel settings-card"><header><div><h3>{{ uiText('隐私与数据') }}</h3><p>{{ uiText('清楚了解数据的去向，保留自己的副本。') }}</p></div><AppIcon name="key" :size="22" /></header><div class="settings-privacy-list"><section><h4>{{ uiText('笔记与实验') }}</h4><p>{{ uiText('笔记来源选择器仅列出你自己的实验。插入实验结果前可预览内容，插入后需手动保存。') }}</p></section><section><h4>{{ uiText('个人 AI 数据传输') }}</h4><p>{{ uiText('本地规则整理不向模型供应商发送内容。个人 AI 仅在你确认发送后，将预览中的指令和内容交给选定供应商；对方的数据保留政策适用。请避免发送不需要的个人或机密信息。') }}</p></section><section><h4>{{ uiText('分享与访问') }}</h4><p>{{ uiText('笔记分享由你主动创建，可在对应笔记底部撤销。分享笔记不会自动授权对底层文件、实验或知识卡的访问。') }}</p><RouterLink to="/app/notes" class="settings-inline-link">{{ uiText('管理我的笔记与分享 →') }}</RouterLink></section></div><form class="settings-form settings-export" @submit.prevent="exportData"><h4>{{ uiText('导出我的数据') }}</h4><p>{{ uiText('下载账户资料、笔记、个人知识卡、文件与实验元数据、AI 使用记录、钱包记录、工作空间、已发送消息和私有词书的 JSON 副本。vocabularyBookImports 中的每个对象可单独另存为词书 JSON，重新确认使用权后导入；不恢复学习进度。这不是完整系统备份，不包含上传文件原件、密码、登录令牌或 AI 密钥。下载后请妥善保管。') }}</p><label class="field-label">{{ uiText('当前密码') }}<input v-model="exportPassword" class="field-input" type="password" autocomplete="current-password" required :disabled="!!busy" /></label><button class="button button--dark" :disabled="!!busy">{{ busy === 'export' ? uiText('准备导出…') : uiText('下载个人数据') }}</button></form></article>
        <article v-else-if="section === 'appearance'" class="panel settings-card">
          <header><div><h3>{{ uiText('外观') }}</h3><p>{{ uiText('更改即时生效，仅保存在此浏览器的当前账户下。') }}</p></div><AppIcon name="eye" :size="22" /></header>
          <div class="settings-form">
            <label class="field-label">{{ uiText('界面语言') }}
              <select class="field-input" :value="uiLanguage" @change="setUiLanguage(($event.target as HTMLSelectElement).value)">
                <option value="zh" lang="zh-CN">简体中文</option>
                <option value="en" lang="en">English</option>
              </select>
              <small>{{ uiText('即时切换并记住此浏览器的选择。笔记、消息和学习资料保留原文。') }}</small>
            </label>
            <fieldset class="theme-palettes">
              <legend>{{ uiText('主题配色') }}</legend>
              <p>{{ uiText('为整个空间换一种色调，背景、按钮与轻微波纹一起变化。') }}</p>
              <div class="theme-palette-grid">
                <button v-for="palette in themePalettes" :key="palette.id" type="button" class="theme-palette"
                  :aria-label="((palette.label) + uiText('配色'))" :aria-pressed="themeStore.state.accent === palette.id"
                  @click="themeStore.setAccent(palette.id)">
                  <span class="theme-preview" :data-palette="palette.id" aria-hidden="true">
                    <span class="theme-preview-bar" /><span class="theme-preview-page"><i /><i /><i /></span><span class="theme-preview-action" />
                  </span>
                  <span class="theme-palette-caption">{{ uiText(palette.label) }}<AppIcon v-if="themeStore.state.accent === palette.id" name="check" :size="15" /></span>
                  <small>{{ uiText(palette.description) }}</small>
                </button>
              </div>
            </fieldset>
            <div class="appearance-options">
              <label class="field-label">{{ uiText('明暗模式') }}<select :value="themeStore.state.preference" class="field-input" @change="themeStore.setPreference(($event.target as HTMLSelectElement).value as ThemePreference)"><option value="system">{{ uiText('跟随系统') }}</option><option value="light">{{ uiText('浅色') }}</option><option value="dark">{{ uiText('深色') }}</option></select></label>
              <label class="field-label">{{ uiText('内容密度') }}<select :value="themeStore.state.density" class="field-input" @change="themeStore.setDensity(($event.target as HTMLSelectElement).value as Density)"><option value="comfortable">{{ uiText('舒适') }}</option><option value="compact">{{ uiText('紧凑') }}</option></select></label>
            </div>
            <button class="button button--ghost" @click="themeStore.reset">{{ uiText('恢复默认外观') }}</button>
          </div>
        </article>
        <WorkspaceSettingsPanel v-else-if="section === 'workspace'" />
        <WorkflowPreferences v-else-if="section === 'workflow'" />
        <template v-else-if="section === 'account'"><AccountBindings /><AccountClosure /></template>
      </section>
    </div>
  </div>
</template>
