<template>
  <div id="userRegisterPage">
    <div class="auth-card-outer">
      <div class="auth-card">
        <div class="auth-head">
          <span class="auth-eyebrow">百夜毕设帮</span>
          <h2 class="auth-title">创建账号</h2>
          <p class="auth-desc">注册后即可生成应用、购买毕设项目</p>
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
              { min: 8, message: '密码不能小于 8 位' },
            ]"
          >
            <a-input-password
              v-model:value="formState.userPassword"
              size="large"
              placeholder="请输入密码"
            />
          </a-form-item>
          <a-form-item
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
              placeholder="请确认密码"
            />
          </a-form-item>
          <div class="auth-tips">
            已有账号？
            <RouterLink to="/user/login">去登录</RouterLink>
          </div>
          <a-form-item :margin-bottom="0">
            <a-button type="primary" html-type="submit" size="large" block shape="round">
              注册
            </a-button>
          </a-form-item>
        </a-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { userRegister } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import { reactive } from 'vue'

const router = useRouter()

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
}
</script>

<style scoped>
#userRegisterPage {
  min-height: calc(100vh - 68px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  background: var(--color-bg);
  position: relative;
  overflow: hidden;
}

#userRegisterPage::before {
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
