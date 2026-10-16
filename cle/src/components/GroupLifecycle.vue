<script setup lang="ts">
import SelectField from '@/components/SelectField.vue'
import { uiText } from '@/i18n'
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { request } from '@/api/client'
import { RouterLink } from 'vue-router'
import { authStore } from '@/stores/auth'
import type { WorkspaceView } from '@/types/api'
const props = defineProps<{ group: WorkspaceView; owner: boolean; manager: boolean }>()
const emit = defineEmits<{ refresh: []; state: [{ archived: boolean; dissolved: boolean; acceptRequests: boolean }]; busy: [boolean] }>()
const state = ref<{ archived: boolean; dissolved: boolean; acceptRequests: boolean }>()
const pending = ref<{ id: string; kind: string; displayName: string; identityCode: string }[]>([])
const reports = ref<{ id: string; messageId: string; reason: string; status: string; displayName: string }[]>([])
const action = ref<'transfer' | 'dissolve' | null>(null), showRequests = ref(false)
const target = ref(''), password = ref(''), name = ref(''), busy = ref(false), error = ref(''), notice = ref('')
const form = ref<HTMLFormElement>(), transferButton = ref<HTMLButtonElement>(), dissolveButton = ref<HTMLButtonElement>()
const candidates = computed(() => props.group.members.filter(m => m.userId !== props.group.ownerId))
let active = true
onBeforeUnmount(() => { active = false; password.value = '' })
const base = `/workspaces/${props.group.id}`
async function load() {
  error.value = ''
  try {
    const s = await request<NonNullable<typeof state.value>>(base + '/lifecycle')
    if (!active) return
    state.value = s; emit('state', s)
    if (props.manager && !s.dissolved) {
      const [p, r] = await Promise.all([request<typeof pending.value>(base + '/invitations'), request<typeof reports.value>(base + '/reports')])
      if (active) { pending.value = p; reports.value = r }
    }
  } catch (e) { if (active) error.value = (e as Error).message }
}
onMounted(load)
async function choose(value: 'transfer' | 'dissolve') {
  if (busy.value) return
  action.value = value; password.value = ''; name.value = ''; target.value = ''; error.value = ''; notice.value = ''
  await nextTick(); form.value?.querySelector<HTMLElement>('button, input')?.focus()
}
function cancel() {
  if (busy.value) return
  const previous = action.value
  action.value = null; password.value = ''; name.value = ''; target.value = ''; error.value = ''
  nextTick(() => (previous === 'transfer' ? transferButton.value : dissolveButton.value)?.focus())
}
async function run(path: string, body: unknown, method = 'POST') {
  if (busy.value) return
  busy.value = true; emit('busy', true); error.value = ''; notice.value = ''
  try {
    await request(path, { method, body: JSON.stringify(body) })
    if (active) { action.value = null; password.value = ''; notice.value = '群组设置已更新'; await load() }
  } catch (e) { if (active) error.value = (e as Error).message }
  finally { password.value = ''; if (active) { busy.value = false; emit('busy', false) } }
  if (active && !error.value) emit('refresh')
}
function submit() {
  if (!state.value || state.value.dissolved || !props.owner || !password.value) return
  if (action.value === 'transfer' && !state.value.archived && candidates.value.some(m => String(m.userId) === target.value)) void run(base + '/transfer', { targetId: Number(target.value), password: password.value })
  if (action.value === 'dissolve' && name.value === props.group.name) void run(base + '/dissolve', { name: name.value, password: password.value })
}
</script>
<template>
  <section class="lifecycle" :aria-label="uiText('群组管理')" :aria-busy="busy">
    <p v-if="state?.archived" class="lifecycle-status" role="status">{{ state.dissolved ? uiText('群组已解散，群主可查阅历史。') : uiText('群组已归档，暂不能发送新消息。') }}</p>
    <p v-if="error" class="form-error" role="alert">{{ uiText(error) }} <button class="table-action" :disabled="busy" @click="load">{{ uiText('重试') }}</button></p>
    <p v-if="notice" role="status">{{ uiText(notice) }}</p>
    <template v-if="owner || manager">
      <div class="lifecycle-heading"><strong>{{ uiText('群组管理') }}</strong><small>#{{ group.id }}</small><span>{{ uiText('管理成员请使用下方「成员与权限」') }}</span></div>
      <div v-if="owner && state && !state.dissolved" class="lifecycle-options">
        <button class="button button--ghost button--small" :disabled="busy" @click="run(base + '/lifecycle', { archived: !state.archived, acceptRequests: state.acceptRequests }, 'PUT')">{{ state.archived ? uiText('恢复群组') : uiText('归档群组') }}</button>
        <button ref="transferButton" class="button button--ghost button--small" :disabled="busy || state.archived || !candidates.length" :title="!candidates.length ? uiText('先邀请一位成员，再转让群主') : ''" @click="choose('transfer')">{{ uiText('转让群主') }}</button>
        <button ref="dissolveButton" class="button button--ghost button--small lifecycle-danger" :disabled="busy" @click="choose('dissolve')">{{ uiText('解散群组') }}</button>
        <label><input :checked="state.acceptRequests" type="checkbox" :disabled="busy || state.archived" @change="run(base + '/lifecycle', { archived: false, acceptRequests: !state.acceptRequests }, 'PUT')" />{{ uiText('允许通过群组编号申请加入') }}</label>
      </div>
      <p v-if="owner && state && !state.dissolved" class="lifecycle-hint">{{ uiText('归档可恢复；解散会移除成员并停止交流，群主保留只读历史。') }}</p>
      <form v-if="action && owner && state && !state.dissolved" ref="form" class="lifecycle-form" @submit.prevent="submit" @keydown.esc.prevent="cancel">
        <h4>{{ action === 'transfer' ? uiText('转让群主') : uiText('解散群组') }}</h4>
        <fieldset :disabled="busy">
          <label v-if="action === 'transfer'" class="field-label">{{ uiText('新群主') }}<SelectField><select v-model="target" class="field-input" required><option value="">{{ uiText('选择现有成员') }}</option><option v-for="m in candidates" :key="m.userId" :value="String(m.userId)">{{ m.displayName }} · @{{ m.username }}</option></select></SelectField></label>
          <p v-if="action === 'transfer'" class="lifecycle-hint">{{ uiText('转让后原群主成为群管理员，新群主管理群组的归档和解散。') }}</p>
          <label v-else class="field-label">{{ uiText('解散前输入群组全名') }}<input v-model="name" class="field-input" :placeholder="group.name" required maxlength="100" /></label>
          <label class="field-label">{{ uiText('确认当前密码') }}<input v-model="password" class="field-input" type="password" autocomplete="current-password" required maxlength="100" /></label>
          <p v-if="action === 'dissolve'" class="lifecycle-hint">{{ uiText('解散后成员失去访问权，群主保留只读历史。此操作不能撤销。') }}</p>
          <div class="lifecycle-options"><button type="submit" class="button button--dark" :disabled="!password || (action === 'transfer' ? !target : name !== group.name)">{{ busy ? uiText('正在处理…') : action === 'transfer' ? uiText('确认转让') : uiText('确认解散') }}</button><button type="button" class="button button--ghost" @click="cancel">{{ uiText('取消') }}</button></div>
        </fieldset>
      </form>
      <button v-if="manager && state && !state.dissolved" class="table-action lifecycle-inbox" :aria-expanded="showRequests" @click="showRequests = !showRequests">{{ uiText('申请、邀请与举报') }} · {{ pending.length + reports.filter(r => r.status === 'OPEN').length }} <span aria-hidden="true">{{ showRequests ? '−' : '+' }}</span></button>
      <div v-if="showRequests">
        <section><h4>{{ uiText('待处理申请与邀请') }}</h4><p v-if="!pending.length" class="lifecycle-hint">{{ uiText('暂无待处理申请或邀请') }}</p><div v-for="p in pending" :key="p.id" class="request-row"><div>{{ p.displayName }}<small>{{ p.identityCode }} · {{ p.kind === 'REQUEST' ? uiText('申请加入') : uiText('邀请待确认') }}</small></div><template v-if="p.kind === 'REQUEST'"><button class="table-action" :disabled="busy" @click="run('/workspaces/invitations/' + p.id + '/decision', { accept: true })">{{ uiText('同意') }}</button><button class="table-action" :disabled="busy" @click="run('/workspaces/invitations/' + p.id + '/decision', { accept: false })">{{ uiText('拒绝') }}</button></template></div></section>
        <section v-if="reports.length"><h4>{{ uiText('消息举报') }}</h4><div v-for="r in reports" :key="r.id" class="request-row"><div>{{ r.reason }}<small>{{ r.displayName }} · {{ r.status }}</small></div><template v-if="r.status === 'OPEN'"><button class="table-action" :disabled="busy" @click="run(base + '/messages/' + r.messageId + '/recall', {})">{{ uiText('撤回消息') }}</button><RouterLink v-if="authStore.has('moderation:review')" to="/app/moderation">{{ uiText('前往平台审核') }}</RouterLink><small v-else>{{ uiText('等待平台审核') }}</small></template></div></section>
      </div>
    </template>
  </section>
</template>
<style scoped>
.lifecycle{border-bottom:1px solid var(--line);padding:18px 0;font-size:13px}.lifecycle-heading,.lifecycle-options{display:flex;gap:12px;align-items:center;flex-wrap:wrap}.lifecycle-heading{margin-bottom:14px}.lifecycle-heading span{margin-left:auto;font-size:12px;color:var(--muted)}.lifecycle-heading strong{font-weight:550}.lifecycle-options label{display:flex;align-items:center;gap:8px;font-size:12px}.lifecycle-danger{color:var(--danger,#ad4a4f)}.lifecycle-hint,small{color:var(--muted);font-size:12px;line-height:1.8}.lifecycle-form{margin:20px 0;padding:22px;border:1px solid var(--line);border-radius:12px;background:var(--paper);max-width:600px}.lifecycle-form h4{margin:0 0 16px;font-size:17px}.lifecycle-form fieldset{border:0;padding:0;min-width:0}.lifecycle-form .field-label{margin:16px 0}.lifecycle-inbox{margin:12px 0}.request-row{display:flex;gap:12px;align-items:center;padding:12px 0;border-bottom:1px solid var(--line)}.request-row>div{flex:1}.request-row small{display:block}.lifecycle-status{padding:14px;background:var(--code-paper);border-radius:8px}
</style>
