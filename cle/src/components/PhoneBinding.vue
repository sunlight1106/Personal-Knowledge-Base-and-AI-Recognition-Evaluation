<script setup lang="ts">
import { ref,watch,onBeforeUnmount } from 'vue'
import { request } from '@/api/client'
import { authStore } from '@/stores/auth'
import { uiText } from '@/i18n'
import ActionConfirmDialog from './ActionConfirmDialog.vue'
interface State { smsAvailable:boolean;maskedPhone:string|null;verified:boolean }
const state=ref<State>(),phone=ref(''),password=ref(''),otp=ref(''),code=ref(''),sent=ref(false),busy=ref(false),error=ref(''),message=ref(''),removeOpen=ref(false)
let generation=0
async function load(){const v=++generation;state.value=undefined;phone.value='';password.value='';otp.value='';code.value='';sent.value=false;busy.value=false;error.value='';message.value='';removeOpen.value=false;try{const value=await request<State>('/account/phone');if(v===generation)state.value=value}catch(e){if(v===generation)error.value=(e as Error).message}}
watch(()=>authStore.state.user?.id,()=>{if(authStore.state.user)void load();else {generation++;password.value='';otp.value='';code.value='';state.value=undefined;removeOpen.value=false}},{immediate:true})
watch(phone,()=>{sent.value=false;code.value=''})
onBeforeUnmount(()=>{generation++;password.value='';otp.value='';code.value=''})
async function perform(kind:'code'|'confirm'|'remove'){
 if(busy.value)return;const v=generation;busy.value=true;error.value='';message.value=''
 try{
   await request(`/account/phone${kind==='remove'?'':'/'+kind}`,{method:kind==='remove'?'DELETE':'POST',body:JSON.stringify(kind==='confirm'?{phone:phone.value,code:code.value}:{phone:phone.value,password:password.value,otp:otp.value})})
   if(v!==generation)return
   if(kind==='code'){sent.value=true;message.value='验证码已发送，5 分钟内有效。'}
   else {removeOpen.value=false;sent.value=false;code.value='';message.value=kind==='confirm'?'手机号已验证并绑定':'手机号已解除绑定';const value=await request<State>('/account/phone');if(v===generation)state.value=value}
 }catch(e){if(v===generation)error.value=(e as Error).message}finally{if(v===generation){busy.value=false;password.value='';otp.value=''}}
}
</script>
<template>
<article class="panel settings-card">
 <header><div><h3>{{ uiText('手机号绑定') }}</h3><p>{{ uiText('验证号码归属后，才可在隐私设置中允许他人通过手机号找到你。') }}</p></div></header>
 <p v-if="state?.verified">{{ uiText('已绑定') }} · {{ state.maskedPhone }}</p>
 <p v-if="state&&!state.smsAvailable" role="status">{{ uiText('尚未配置短信服务，手机号绑定暂不可用') }}</p>
 <p v-if="error&&!removeOpen" role="alert" class="form-error">{{ uiText(error) }}</p><p v-if="message" role="status">{{ uiText(message) }}</p>
 <button v-if="!state" class="button button--ghost" @click="load">{{ uiText('重新加载') }}</button>
 <form v-if="state" class="settings-form" @submit.prevent="perform('code')">
  <label class="field-label">{{ uiText('手机号（含国家区号）') }}<input v-model="phone" class="field-input" type="tel" autocomplete="tel" maxlength="32" :disabled="busy||!state.smsAvailable" :required="state.smsAvailable" placeholder="+8613812345678" /></label>
  <label class="field-label">{{ uiText('当前密码') }}<input v-model="password" class="field-input" type="password" autocomplete="current-password" maxlength="100" required :disabled="busy||(!state.smsAvailable&&!state.verified)" /></label>
  <label class="field-label">{{ uiText('双重验证代码（已开启时填写）') }}<input v-model="otp" class="field-input" autocomplete="one-time-code" maxlength="32" :disabled="busy||(!state.smsAvailable&&!state.verified)" /></label>
  <button class="button button--ghost" :disabled="busy||!state.smsAvailable">{{ uiText('发送短信验证码') }}</button>
  <button v-if="state.verified" type="button" class="button button--ghost" :disabled="busy||!password" @click="removeOpen=true">{{ uiText('解除手机号绑定') }}</button>
 </form>
 <form v-if="sent" class="settings-form" @submit.prevent="perform('confirm')"><label class="field-label">{{ uiText('短信验证码') }}<input v-model="code" class="field-input" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" required :disabled="busy" /></label><button class="button button--dark" :disabled="busy">{{ uiText('验证并绑定') }}</button></form>
 <ActionConfirmDialog v-if="removeOpen" title="解除手机号绑定" description="解除后，该手机号不能再用于查找你。邮箱、用户号与现有联系人不受影响。" confirm-label="确认解除" :busy="busy" :error="error" @cancel="removeOpen=false" @confirm="perform('remove')" />
</article>
</template>
