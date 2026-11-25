<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { firstPage } from '@/router'
import { login } from '@/session'

const router = useRouter()
const credentials = reactive({ username: '', password: '' })
const busy = ref(false)
const error = ref('')

async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await login(credentials.username, credentials.password)
    credentials.password = ''
    await router.replace(firstPage())
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '登录失败，请重试'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="login-screen">
    <section class="login-panel">
      <h1>应用工坊管理端</h1>
      <a-form layout="vertical" :model="credentials" @finish="submit">
        <a-form-item label="管理员账号" name="username" :rules="[{ required: true, message: '请输入管理员账号' }]">
          <a-input v-model:value="credentials.username" autocomplete="username" autofocus />
        </a-form-item>
        <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
          <a-input-password v-model:value="credentials.password" autocomplete="current-password" />
        </a-form-item>
        <a-alert v-if="error" :message="error" type="error" show-icon role="alert" class="form-error" />
        <a-button block type="primary" html-type="submit" :loading="busy">登录管理端</a-button>
      </a-form>
    </section>
  </main>
</template>
