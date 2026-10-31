<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { request } from '@/api/client'
import { authStore } from '@/stores/auth'
import { uiText } from '@/i18n'
interface Settings { contactNumber: string | null; discoverable: boolean; searchByNumber: boolean; searchByEmail: boolean; searchByPhone: boolean; protectedAccount: boolean }
const state=ref<Settings>(),number=ref(''),loading=ref(false),busy=ref(false),error=ref(''),message=ref('')
let generation=0
async function load(){const version=++generation;state.value=undefined;number.value='';error.value='';message.value='';busy.value=false;loading.value=true;try{const value=await request<Settings>('/social/discovery');if(version===generation){state.value=value;number.value=value.contactNumber||''}}catch(e){if(version===generation)error.value=(e as Error).message}finally{if(version===generation)loading.value=false}}
watch(()=>authStore.state.user?.id,()=>{if(authStore.state.user)void load();else {generation++;state.value=undefined;number.value='';message.value='';error.value=''}},{immediate:true})
onBeforeUnmount(()=>{generation++})
async function save(kind:'number'|'discovery'){
  if(busy.value||!state.value)return
  const version=generation;busy.value=true;message.value='';error.value=''
  try{const value=await request<Settings>(`/social/${kind}`,{method:'PUT',body:JSON.stringify(kind==='number'?{contactNumber:number.value.trim()}:state.value)})
    if(version===generation){state.value=value;number.value=value.contactNumber||'';message.value=kind==='number'?'用户号已保存':'查找权限已保存'}
  }catch(e){if(version===generation)error.value=(e as Error).message}finally{if(version===generation)busy.value=false}
}
</script>
<template>
  <section class="discovery-settings" :aria-busy="loading||busy">
    <h4>{{ uiText('用户号与查找权限') }}</h4>
    <p>{{ uiText('用户号由你设置，与永久身份码不同。邮箱和手机号只支持完整匹配，搜索结果不会公开这些联系方式。') }}</p>
    <p v-if="loading" role="status">{{ uiText('正在加载…') }}</p>
    <p v-if="error" role="alert" class="form-error">{{ uiText(error) }}</p>
    <button v-if="!state&&!loading" type="button" class="button button--ghost" @click="load">{{ uiText('重新加载') }}</button>
    <template v-if="state">
      <form class="settings-form" @submit.prevent="save('number')">
        <label class="field-label">{{ uiText('用户号') }}<input v-model="number" class="field-input" minlength="6" maxlength="24" pattern="[A-Za-z0-9]{6,24}" autocomplete="off" spellcheck="false" required :disabled="busy" /></label>
        <small>{{ uiText('6–24 位字母或数字，大小写不区分，全站唯一。修改后旧号码不再指向你。') }}</small>
        <button class="button button--ghost" :disabled="busy">{{ uiText('保存用户号') }}</button>
      </form>
      <p v-if="state.protectedAccount" role="note">{{ uiText('管理与审核账号不开放用户搜索和好友添加，个人开关不会解除此限制。') }}</p>
      <form class="settings-form" @submit.prevent="save('discovery')">
        <fieldset :disabled="busy||state.protectedAccount" class="discovery-options">
          <label><input v-model="state.discoverable" type="checkbox" /> {{ uiText('允许别人找到并添加我') }}</label>
          <label><input v-model="state.searchByNumber" type="checkbox" :disabled="!state.discoverable" /> {{ uiText('通过用户号、身份码、用户名或昵称找到我') }}</label>
          <label><input v-model="state.searchByEmail" type="checkbox" :disabled="!state.discoverable" /> {{ uiText('通过已验证邮箱找到我') }}</label>
          <label><input v-model="state.searchByPhone" type="checkbox" :disabled="!state.discoverable" /> {{ uiText('通过已验证手机号找到我') }}</label>
        </fieldset>
        <small>{{ uiText('关闭总开关后，所有查找方式及新的好友申请都会关闭；现有联系人和黑名单保持不变。') }}</small>
        <button class="button button--dark" :disabled="busy||state.protectedAccount">{{ uiText('保存查找权限') }}</button>
      </form>
    </template>
    <p v-if="message" role="status">{{ uiText(message) }}</p>
  </section>
</template>
<style scoped>
.discovery-settings form{margin:20px 0}.discovery-options{display:grid;gap:16px;padding:0;border:0}.discovery-options label{display:flex;align-items:center;gap:10px}.discovery-options input{accent-color:var(--accent);width:18px;height:18px}.discovery-settings small{color:var(--muted);line-height:1.7}
</style>
