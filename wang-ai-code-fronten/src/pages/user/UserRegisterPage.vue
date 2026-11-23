<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRightOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { userRegister } from '@/api/userController'
import AuthLayout from '@/layouts/AuthLayout.vue'

const route = useRoute()
const router = useRouter()
const submitting = ref(false)
const error = ref('')
const form = reactive({ userAccount: '', userPassword: '', checkPassword: '' })
const loginLocation = computed(() => ({
  path: '/user/login',
  query: { redirect: route.query.redirect },
}))

async function validateCheckPassword(_: unknown, value: string) {
  if (value && value !== form.userPassword) throw new Error('两次输入密码不一致')
}

async function handleSubmit() {
  submitting.value = true
  error.value = ''
  try {
    const res = await userRegister({ ...form, userAccount: form.userAccount.trim() })
    if (res.data.code === 0) {
      message.success('账号已注册，请登录')
      await router.replace(loginLocation.value)
    } else {
      error.value = res.data.message || '账号注册失败'
    }
  } catch {
    error.value = '注册失败，请检查网络后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout title="注册账号" description="注册后可以创建应用、保存成果和领取免费模板。">
    <a-form :model="form" name="register" layout="vertical" @finish="handleSubmit">
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
          autocomplete="new-password"
          placeholder="至少 8 位字符"
          :disabled="submitting"
        />
      </a-form-item>
      <a-form-item
        label="确认密码"
        name="checkPassword"
        :rules="[{ required: true, message: '请确认密码' }, { validator: validateCheckPassword }]"
      >
        <a-input-password
          v-model:value="form.checkPassword"
          autocomplete="new-password"
          placeholder="再次输入密码"
          :disabled="submitting"
        />
      </a-form-item>
      <a-alert v-if="error" :message="error" type="error" class="auth-error" show-icon />
      <a-button type="primary" html-type="submit" :loading="submitting">
        注册 <ArrowRightOutlined aria-hidden="true" />
      </a-button>
    </a-form>
    <p class="auth-next">
      <RouterLink :to="loginLocation" class="folio-link">
        已有账号，去登录 <ArrowRightOutlined aria-hidden="true" />
      </RouterLink>
    </p>
  </AuthLayout>
</template>
