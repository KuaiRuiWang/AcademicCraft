<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRightOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { userLogin } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'
import AuthLayout from '@/layouts/AuthLayout.vue'

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()
const submitting = ref(false)
const error = ref('')
const form = reactive({ userAccount: '', userPassword: '' })
const registerLocation = computed(() => ({
  path: '/user/register',
  query: { redirect: route.query.redirect },
}))

async function handleSubmit() {
  submitting.value = true
  error.value = ''
  try {
    const res = await userLogin({ ...form, userAccount: form.userAccount.trim() })
    if (res.data.code === 0 && res.data.data) {
      loginUserStore.setLoginUser(res.data.data)
      message.success('登录成功')
      const redirect = route.query.redirect
      const nextPath =
        typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
          ? redirect
          : '/'
      await router.replace(nextPath)
    } else {
      error.value = res.data.message || '登录失败，请核对账号和密码'
    }
  } catch {
    error.value = '登录失败，请检查网络后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout title="登录工坊" description="登录后即可继续创作、领取模板和管理项目。">
    <a-form :model="form" name="login" layout="vertical" @finish="handleSubmit">
      <a-form-item
        label="账号"
        name="userAccount"
        :rules="[{ required: true, message: '请输入账号' }]"
      >
        <a-input
          v-model:value="form.userAccount"
          autocomplete="username"
          placeholder="请输入账号"
          :disabled="submitting"
        />
      </a-form-item>
      <a-form-item
        label="密码"
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码长度至少为 8 位' },
        ]"
      >
        <a-input-password
          v-model:value="form.userPassword"
          autocomplete="current-password"
          placeholder="请输入密码"
          :disabled="submitting"
        />
      </a-form-item>
      <a-alert v-if="error" :message="error" type="error" class="auth-error" show-icon />
      <a-button type="primary" html-type="submit" :loading="submitting">
        登录 <ArrowRightOutlined aria-hidden="true" />
      </a-button>
    </a-form>
    <p class="auth-next">
      <RouterLink :to="registerLocation" class="folio-link">
        还没有账号，去注册 <ArrowRightOutlined aria-hidden="true" />
      </RouterLink>
    </p>
  </AuthLayout>
</template>
