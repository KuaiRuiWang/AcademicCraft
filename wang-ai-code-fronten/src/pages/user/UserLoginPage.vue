<template>
  <div id="userLoginPage">
    <div class="auth-card-outer">
      <div class="auth-card">
        <div class="auth-head">
          <span class="auth-eyebrow">百夜毕设帮</span>
          <h2 class="auth-title">欢迎回来</h2>
          <p class="auth-desc">不写一行代码，生成计算机毕设</p>
        </div>
        <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
          <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
            <a-input
              v-model:value="formState.userAccount"
              size="large"
              placeholder="请输入账号"
            />
          </a-form-item>
          <a-form-item
            name="userPassword"
            :rules="[
              { required: true, message: '请输入密码' },
              { min: 8, message: '密码长度不能小于 8 位' },
            ]"
          >
            <a-input-password
              v-model:value="formState.userPassword"
              size="large"
              placeholder="请输入密码"
            />
          </a-form-item>
          <div class="auth-tips">
            没有账号
            <RouterLink to="/user/register">去注册</RouterLink>
          </div>
          <a-form-item :margin-bottom="0">
            <a-button type="primary" html-type="submit" size="large" block shape="round">
              登录
            </a-button>
          </a-form-item>
        </a-form>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { reactive } from 'vue'
import { userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

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
}
</script>

<style scoped>
#userLoginPage {
  min-height: calc(100vh - 68px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  background: var(--color-bg);
  position: relative;
  overflow: hidden;
}

#userLoginPage::before {
  content: '';
  position: absolute;
  top: -160px;
  left: 50%;
  transform: translateX(-50%);
  width: 720px;
  height: 420px;
  background: radial-gradient(ellipse at center, rgba(15, 118, 110, 0.1) 0%, transparent 70%);
  pointer-events: none;
}

/* 双嵌套外壳 */
.auth-card-outer {
  position: relative;
  width: 100%;
  max-width: 440px;
  background: rgba(231, 229, 228, 0.5);
  padding: 10px;
  border-radius: 28px;
  border: 1px solid var(--color-border);
}

.auth-card {
  background: var(--color-surface);
  border-radius: 20px;
  padding: 40px 36px 36px;
  box-shadow: var(--shadow-card);
}

.auth-head {
  text-align: center;
  margin-bottom: 32px;
}

.auth-eyebrow {
  display: inline-block;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-primary);
  margin-bottom: 12px;
}

.auth-title {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 0 0 8px;
  color: var(--color-text);
}

.auth-desc {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 15px;
}

.auth-tips {
  text-align: right;
  color: var(--color-text-tertiary);
  font-size: 14px;
  margin-bottom: 24px;
}

.auth-tips a {
  color: var(--color-primary);
  font-weight: 500;
}
</style>
