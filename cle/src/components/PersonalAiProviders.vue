<script setup lang="ts">
import HelpHint from '@/components/HelpHint.vue'
import { uiText } from '@/i18n'

import PersonalAiDiagnostics from './PersonalAiDiagnostics.vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ApiClientError } from '@/api/client'
import { personalApi } from '@/api/personal'
import type { PersonalAiProvider, PersonalAiSetting } from '@/types/personal'
import { toastStore } from '@/stores/toast'
import { modelFieldError } from '@/lib/personalAiModel'

const providers = ref<PersonalAiProvider[]>([])
const settings = ref<PersonalAiSetting[]>([])
const provider = ref('')
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const form = reactive({ model: '', baseUrl: '', apiKey: '', enabled: true })
const selected = computed(() => providers.value.find(item => item.provider === provider.value))
const existing = computed(() => settings.value.find(item => item.provider === provider.value))
const modelError = computed(() => modelFieldError(provider.value, form.model))
const configuredCount = computed(() => settings.value.filter(item => item.configured && item.enabled).length)
const configurationChanged = computed(() => !!existing.value && (
  form.model.trim() !== existing.value.model || form.baseUrl.trim() !== existing.value.baseUrl ||
  form.enabled !== existing.value.enabled || !!form.apiKey.trim()
))
function endpointLabel(url: string) {
  if (provider.value === 'MOONSHOT') {
    if (url === 'https://api.moonshot.cn/v1') return `国内开放平台 · ${url}`
    if (url === 'https://api.moonshot.ai/v1') return `国际开放平台 · ${url}`
  }
  return url
}
let active = true
function resetForm() {
  form.apiKey = ''
  form.model = existing.value?.model || ''
  form.baseUrl = existing.value?.baseUrl || selected.value?.baseUrls[0] || ''
  form.enabled = existing.value?.enabled ?? true
  error.value = ''
}
watch(provider, resetForm)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const [catalog, saved] = await Promise.all([personalApi.providers(), personalApi.settings()])
    if (!active) return
    providers.value = catalog; settings.value = saved
    if (!provider.value) provider.value = catalog[0]?.provider || ''
    resetForm()
  } catch (reason) { if (active) error.value = reason instanceof ApiClientError ? reason.message : '个人 AI 设置加载失败，请重试' }
  finally { if (active) loading.value = false }
}
async function save() {
  if (busy.value || !provider.value) return
  if (modelError.value) { error.value = modelError.value; return }
  busy.value = true; error.value = ''
  const chosen = provider.value
  try {
    const saved = await personalApi.saveSetting(chosen, { model: form.model.trim(), baseUrl: form.baseUrl.trim() || undefined, apiKey: form.apiKey.trim() || undefined, enabled: form.enabled })
    if (!active) return
    settings.value = [...settings.value.filter(item => item.provider !== chosen), saved]
    resetForm()
    toastStore.success('个人 AI 配置已保存；尚未调用供应商')
  } catch (reason) { if (active) error.value = reason instanceof ApiClientError ? reason.message : '保存失败，请重试' }
  finally { form.apiKey = ''; if (active) busy.value = false }
}
async function remove() {
  if (busy.value || !existing.value) return
  if (!window.confirm(`删除 ${selected.value?.displayName || provider.value} 的个人密钥和配置？后续将无法使用此配置调用模型。`)) return
  busy.value = true; error.value = ''
  const chosen = provider.value
  try {
    await personalApi.deleteSetting(chosen)
    if (!active) return
    settings.value = settings.value.filter(item => item.provider !== chosen)
    resetForm(); toastStore.success('个人密钥和配置已删除')
  } catch (reason) { if (active) error.value = reason instanceof ApiClientError ? reason.message : '删除失败' }
  finally { form.apiKey = ''; if (active) busy.value = false }
}
onMounted(load)
onBeforeUnmount(() => { active = false; form.apiKey = '' })
</script>
<template>
  <article class="panel settings-card personal-ai-settings">
    <header><div><h3>{{ uiText('个人 AI 服务') }}<HelpHint label="个人 AI 服务" text="普通笔记和背单词不用配置 AI。需要 AI 帮助时，选择供应商并保存自己的模型、接口地址和密钥，再单独测试连接。" /></h3><p>{{ uiText('使用你自己的 API 密钥。每个账户独立保存配置，费用由供应商向你的 API 账户结算。') }}</p></div><span class="settings-badge">{{ configuredCount }} {{ uiText('个已启用') }}</span></header>
    <p class="settings-notice">{{ uiText('密钥仅用于你的请求，不会显示给其他用户。每次在笔记中调用模型前，你都可以审阅将发送的完整内容。平台余额与个人 AI 费用分开。') }}</p>
    <p v-if="error" class="inline-alert inline-alert--error" role="alert">{{ uiText(error) }} <button v-if="!providers.length" class="table-action" @click="load">{{ uiText('重试') }}</button></p>
    <p v-if="loading" class="settings-empty" role="status">{{ uiText('正在加载供应商…') }}</p>
    <form v-else-if="providers.length" class="settings-form" autocomplete="off" @submit.prevent="save">
      <div class="settings-fields">
        <label class="field-label">{{ uiText('供应商') }}<select v-model="provider" class="field-input" :disabled="busy"><option v-for="item in providers" :key="item.provider" :value="item.provider">{{ uiText(item.displayName) }}</option></select></label>
        <div class="field-label"><span><label for="personal-ai-model">{{ uiText('模型名称 / 模型 ID') }}</label><HelpHint label="模型 ID" text="填写供应商模型目录里的名称，不是密钥的 ID 或备注。保存配置后，可在下方查询模型目录并选择。" /></span><input id="personal-ai-model" v-model="form.model" class="field-input" maxlength="120" :placeholder="uiText('查询下方模型目录后选择，也可手动填写')" required :disabled="busy" :aria-invalid="!!modelError" aria-describedby="personal-ai-model-hint" /><small id="personal-ai-model-hint">{{ uiText('填写模型目录中的名称，不是密钥管理页的 ID，也不是你给密钥起的名字。') }}</small><small v-if="modelError" class="model-field-error" role="alert">{{ uiText(modelError) }}</small></div>
      </div>
      <p v-if="selected && !selected.remoteEnabled" class="inline-alert">{{ uiText('此部署尚未开启外部模型调用。可以保存配置和预览内容；实际调用需部署管理员启用。') }}</p>
      <div class="field-label"><span><label for="personal-ai-endpoint">{{ uiText('API 端点') }}</label><HelpHint label="API 端点" text="这是模型服务接收请求的地址，不是官网登录页。国内站、国际站可能使用不同地址；按创建密钥的平台选择。" /></span>
        <select v-if="selected?.baseUrls.length && !selected.customEndpointAllowed" id="personal-ai-endpoint" v-model="form.baseUrl" class="field-input" :disabled="busy"><option v-for="url in selected.baseUrls" :key="url" :value="url">{{ uiText(endpointLabel(url)) }}</option></select>
        <input v-else id="personal-ai-endpoint" v-model="form.baseUrl" class="field-input" type="url" maxlength="300" :placeholder="uiText('https://…（必须在部署端允许列表中）')" :disabled="busy" required />
        <small>{{ uiText('仅支持后端允许的 HTTPS 端点。模型名称与账户可用性以供应商为准；保存配置不会验证额度或发起模型调用。') }}</small>
        <small v-if="provider === 'MOONSHOT'">{{ uiText('请按创建密钥的平台选择：platform.moonshot.cn / platform.kimi.com 对应国内站；platform.moonshot.ai / platform.kimi.ai 对应国际站。Kimi Code 的密钥使用单独的接口。') }}</small>
      </div>
      <label class="field-label">{{ existing?.configured ? uiText('更换 API 密钥（留空保留现有密钥）') : uiText('API 密钥') }}<input v-model="form.apiKey" type="password" class="field-input" autocomplete="new-password" name="personal-ai-key" maxlength="1000" :required="!existing?.configured" :disabled="busy" :placeholder="existing?.configured ? uiText('已配置 · 不回显密钥') : uiText('输入你自己的 API 密钥')" /></label>
      <label class="settings-check"><input v-model="form.enabled" type="checkbox" :disabled="busy" /> {{ uiText('启用此个人配置') }}</label>
      <p class="field-hint">{{ uiText('输入的密钥不会写入浏览器存储。保存尝试结束、切换供应商或关闭此页面后，输入框都会清空。') }}</p>
      <div class="settings-button-row"><button class="button button--dark" :disabled="busy">{{ busy ? uiText('处理中…') : uiText('保存个人配置') }}</button><button type="button" class="button button--ghost" :disabled="busy" @click="resetForm">{{ uiText('取消更改') }}</button><button v-if="existing" type="button" class="button button--ghost note-danger" :disabled="busy" @click="remove">{{ uiText('删除密钥与配置') }}</button></div>
    </form>
    <p v-if="configurationChanged" class="field-hint" role="status">{{ uiText('配置有未保存的修改，请先保存，再查询模型目录或测试连接。') }}</p>
    <PersonalAiDiagnostics v-if="existing && !configurationChanged && !busy" :key="existing.provider" :setting="existing" :disabled="busy" @select-model="form.model = $event" /><div v-if="settings.length" class="settings-saved-list"><h4>{{ uiText('我的已保存配置') }}</h4><div v-for="item in settings" :key="item.provider"><strong>{{ uiText(providers.find(p => p.provider === item.provider)?.displayName || item.provider) }}</strong><span>{{ item.model }}</span><span>{{ item.configured ? item.enabled ? uiText('已启用') : uiText('已停用') : uiText('未配置密钥') }}</span></div></div>
  </article>
</template>
