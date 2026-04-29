<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NButton, NCard, NForm, NFormItem, NInput, useMessage, NSpin } from 'naive-ui'
import { loginApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const auth = useAuthStore()

const phone = ref('')
const password = ref('')
const submitting = ref(false)
const autoLoginLoading = ref(false)

async function onSubmit() {
  if (submitting.value) return
  submitting.value = true
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
    submitting.value = false
  }
}

// 自动登录逻辑：URL 中包含 phone 参数时触发
onMounted(async () => {
  const phoneParam = route.query.phone as string | undefined
  if (phoneParam) {
    autoLoginLoading.value = true
    try {
      console.log('[Auto Login] 开始自动登录，手机号:', phoneParam)
      // 只传 phone，不传 password，后端会自动使用 loginType=2 免密登录
      const res = await loginApi({
        phone: phoneParam.trim(),
      })
      console.log('[Auto Login] 登录成功，响应:', res)
      auth.setFromLogin(res)
      message.success('登录成功')
      const redirect = (route.query.redirect as string) || '/chat'
      await router.replace(redirect)
    } catch (e: unknown) {
      console.error('[Auto Login] 自动登录失败，错误详情:', e)
      autoLoginLoading.value = false
      const err = e as { response?: { data?: { error?: string }; status?: number }; message?: string }
      const errorMsg = err.response?.data?.error || err.message || '未知错误'
      console.error('[Auto Login] 错误状态码:', err.response?.status)
      console.error('[Auto Login] 错误信息:', errorMsg)
      message.error(`自动登录失败: ${errorMsg}，请手动登录`)
      // 自动登录失败时，填充手机号让用户手动输入密码
      phone.value = phoneParam.trim()
    }
  }
})
</script>

<template>
  <div class="login-page">
    <n-card class="login-card" :bordered="false">
      <!-- 自动登录加载中 -->
      <div v-if="autoLoginLoading" class="auto-login-loading">
        <n-spin size="large" />
        <p class="loading-text">正在登录中...</p>
      </div>
      <!-- 正常登录表单 -->
      <template v-else>
        <div class="login-header">
          <h2 class="login-title">专家领域</h2>
        </div>
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
            />
          </n-form-item>
          <n-button type="primary" block :loading="submitting" :disabled="submitting" attr-type="submit">
            登录
          </n-button>
        </n-form>
      </template>
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
.login-header {
  text-align: center;
  margin-bottom: 8px;
}
.login-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #1f2937;
}
.hint {
  color: #6b7280;
  font-size: 13px;
  margin: 0 0 16px;
}
.auto-login-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  gap: 16px;
}
.loading-text {
  margin: 0;
  font-size: 15px;
  color: #6b7280;
}
</style>
