<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NButton, NCard, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import { loginApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const auth = useAuthStore()

const phone = ref('')
const password = ref('')
const loading = ref(false)

async function onSubmit() {
  loading.value = true
  try {
    const res = await loginApi({
      phone: phone.value.trim(),
      password: password.value,
    })
    auth.setFromLogin(res)
    message.success('登录成功')
    const redirect = (route.query.redirect as string) || '/chat'
    await router.replace(redirect)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } } }
    message.error(err.response?.data?.error || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <n-card title="智蚁AI" class="login-card" :bordered="false">
      <p class="hint">使用倚天系统账号与密码登录</p>
      <n-form @submit.prevent="onSubmit">
        <n-form-item label="账号">
          <n-input v-model:value="phone" placeholder="账号" autocomplete="tel" />
        </n-form-item>
        <n-form-item label="密码">
          <n-input
            v-model:value="password"
            type="password"
            placeholder="密码"
            show-password-on="click"
            autocomplete="current-password"
            @keyup.enter="onSubmit"
          />
        </n-form-item>
        <n-button type="primary" block :loading="loading" attr-type="submit" @click="onSubmit">
          登录
        </n-button>
      </n-form>
    </n-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #f3f4f6;
}
.login-card {
  width: 100%;
  max-width: 400px;
  background: #ffffff !important;
  border: 1px solid #e5e7eb !important;
}
.hint {
  color: #6b7280;
  font-size: 13px;
  margin: 0 0 16px;
}
</style>
