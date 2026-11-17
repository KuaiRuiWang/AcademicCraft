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
          <h1 id="auth-title" class="auth-title">欢迎回来</h1>
          <p class="auth-desc">不写一行代码，生成计算机毕设</p>
        </div>
        <a-form :model="formState" name="login" layout="vertical" @finish="handleSubmit">
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
              autocomplete="current-password"
              :disabled="submitting"
            />
          </a-form-item>
          <a-form-item class="auth-submit-row"
            ><a-button type="primary" html-type="submit" size="large" block :loading="submitting"
              >登录 <ArrowRightOutlined aria-hidden="true" /></a-button
          ></a-form-item>
          <div class="auth-tips">没有账号？<RouterLink to="/user/register">去注册</RouterLink></div>
        </a-form>
      </section>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { reactive, ref } from 'vue'
import { ArrowLeftOutlined, ArrowRightOutlined } from '@ant-design/icons-vue'
import { userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

const submitting = ref(false)

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const router = useRouter()
const loginUserStore = useLoginUserStore()

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: API.UserLoginRequest) => {
  submitting.value = true
  try {
    const res = await userLogin(values)
    // 登录成功，把登录态保存到全局状态中
    if (res.data.code === 0 && res.data.data) {
      await loginUserStore.fetchLoginUser()
      message.success('登录成功')
      router.push({
        path: '/',
        replace: true,
      })
    } else {
      message.error('登录失败，' + res.data.message)
    }
  } catch {
    message.error('登录失败，请检查网络后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style src="@/assets/auth.css"></style>
