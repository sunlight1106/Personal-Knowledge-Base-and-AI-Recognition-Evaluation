<script setup lang="ts">
import { uiText } from '@/i18n'

import { onMounted,onBeforeUnmount,ref } from 'vue'
import { accountApi } from '@/api/accounts'
import { securityApi,type SecurityState } from '@/api/security'
import { authStore } from '@/stores/auth'
const email=ref(''),password=ref(''),otp=ref(''),state=ref<SecurityState>(),busy=ref(false),message=ref(''),error=ref('')
let active=true;const owner=authStore.state.user?.id
onBeforeUnmount(()=>{active=false;password.value='';otp.value=''})
onMounted(async()=>{try{const result=await securityApi.state();if(active&&owner===authStore.state.user?.id)state.value=result}catch(e){if(active)error.value=(e as Error).message}})
async function send(){if(busy.value)return;busy.value=true;message.value='';error.value='';try{await accountApi.bind(email.value.trim(),password.value,otp.value);if(active&&owner===authStore.state.user?.id)message.value='请到新邮箱打开验证链接。验证成功后会更新绑定，并退出所有设备。'}catch(e){if(active)error.value=(e as Error).message}finally{password.value='';otp.value='';busy.value=false}}
</script>
<template><article class="panel settings-card"><header><div><h3>{{ uiText('邮箱绑定') }}</h3><p>{{ uiText('验证后的邮箱可用于登录和找回密码。新邮箱验证完成前，原邮箱继续有效。') }}</p></div></header><p>{{ uiText('当前邮箱：') }}{{authStore.state.user?.email}} · {{state?.emailVerified?uiText('已验证'):uiText('未验证')}}</p><p v-if="state&&!state.mailAvailable">{{ uiText('部署者尚未启用邮件服务，暂不能更换绑定。') }}</p><form class="settings-form" @submit.prevent="send"><label class="field-label">{{ uiText('新邮箱') }}<input v-model.trim="email" class="field-input" type="email" maxlength="160" autocomplete="email" required :disabled="busy" /></label><label class="field-label">{{ uiText('当前密码') }}<input v-model="password" type="password" class="field-input" autocomplete="current-password" required maxlength="100" :disabled="busy" /></label><label v-if="state?.mfaEnabled" class="field-label">{{ uiText('验证码或恢复码') }}<input v-model="otp" class="field-input" autocomplete="one-time-code" maxlength="32" :disabled="busy" /></label><p v-if="error" role="alert" class="form-error">{{ uiText(error) }}</p><p v-if="message" role="status">{{message}}</p><button class="button button--dark" :disabled="busy||!state?.mailAvailable">{{busy?uiText('正在提交…'):uiText('发送新邮箱验证邮件')}}</button></form></article></template>
