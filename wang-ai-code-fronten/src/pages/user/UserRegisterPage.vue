<template>
  <div class="auth-page">
    <div class="auth-layout">
      <aside class="auth-intro">
        <h2 class="auth-display">选一份成品项目，<br /><span>或让 AI 为你定制。</span></h2>
        <p>精选优质毕设项目源码，购买即交付、可部署上线；没有找到合适的，用 AI 从零生成。</p>
        <RouterLink to="/" class="auth-back"
          ><ArrowLeftOutlined aria-hidden="true" /> 返回首页</RouterLink
        >
      </aside>
      <section class="auth-card" aria-labelledby="auth-title">
        <div class="auth-head">
          <h1 id="auth-title" class="auth-title">创建账号</h1>
          <p class="auth-desc">注册后即可生成应用、购买毕设项目</p>
        </div>
        <a-form :model="formState" name="register" layout="vertical" @finish="handleSubmit">
          <a-form-item
            label="账号"
            name="userAccount"
            :rules="[{ required: true, message: '请输入账号' }]"
          >
            <a-input
              v-model:value="formState.userAccount"
              size="large"
              placeholder="请输入账号"
              autocomplete="username"
              :disabled="submitting"
            />
          </a-form-item>
          <a-form-item
            label="密码"
            name="userPassword"
            :rules="[
              { required: true, message: '请输入密码' },
              { min: 8, message: '密码长度不能小于 8 位' },
            ]"
          >
            <a-input-password
              v-model:value="formState.userPassword"
              size="large"
              placeholder="请输入至少 8 位密码"
              autocomplete="new-password"
              :disabled="submitting"
            />
          </a-form-item>
          <a-form-item
            label="确认密码"
            name="checkPassword"
            :rules="[
              { required: true, message: '请确认密码' },
              { min: 8, message: '密码不能小于 8 位' },
              { validator: validateCheckPassword },
            ]"
          >
            <a-input-password
              v-model:value="formState.checkPassword"
              size="large"
              placeholder="再次输入密码"
              autocomplete="new-password"
              :disabled="submitting"
            />
          </a-form-item>
          <a-form-item class="auth-submit-row"
            ><a-button type="primary" html-type="submit" size="large" block :loading="submitting"
              >注册 <ArrowRightOutlined aria-hidden="true" /></a-button
          ></a-form-item>
          <div class="auth-tips">已有账号？<RouterLink to="/user/login">去登录</RouterLink></div>
        </a-form>
      </section>
    </div>
  </div>
</template>
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { userRegister } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import { reactive, ref } from 'vue'
import { ArrowLeftOutlined, ArrowRightOutlined } from '@ant-design/icons-vue'

const router = useRouter()

const submitting = ref(false)

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

/**
 * 验证确认密码
 * @param rule
 * @param value
 * @param callback
 */
const validateCheckPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && value !== formState.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: API.UserRegisterRequest) => {
  submitting.value = true
  try {
    const res = await userRegister(values)
    // 注册成功，跳转到登录页面
    if (res.data.code === 0) {
      message.success('注册成功')
      router.push({
        path: '/user/login',
        replace: true,
      })
    } else {
      message.error('注册失败，' + res.data.message)
    }
  } catch {
    message.error('注册失败，请检查网络后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style src="@/assets/auth.css"></style>
