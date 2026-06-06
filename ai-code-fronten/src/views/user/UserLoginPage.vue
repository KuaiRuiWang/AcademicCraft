<template>
  <div id="userLoginPage">
    <div class="login-card">
      <div class="card-header">
        <img src="@/assets/logo.png" alt="logo" class="card-logo" />
        <h2 class="title">百夜 AI 应用生成</h2>
        <p class="desc">不写一行代码，生成完整应用</p>
      </div>

      <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
        <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
          <a-input
            v-model:value="formState.userAccount"
            placeholder="请输入账号"
            size="large"
          >
            <template #prefix>
              <UserOutlined style="color: rgba(0, 0, 0, 0.25)" />
            </template>
          </a-input>
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
            placeholder="请输入密码"
            size="large"
          >
            <template #prefix>
              <LockOutlined style="color: rgba(0, 0, 0, 0.25)" />
            </template>
          </a-input-password>
        </a-form-item>

        <div class="tips">
          没有账号？
          <RouterLink to="/user/register">去注册</RouterLink>
        </div>

        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" block>登录</a-button>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useRouter } from 'vue-router'

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
const handleSubmit = async (values: any) => {
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
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.login-card {
  width: 400px;
  padding: 40px 36px 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(22, 119, 255, 0.08), 0 1px 4px rgba(0, 0, 0, 0.06);
}

.card-header {
  text-align: center;
  margin-bottom: 32px;
}

.card-logo {
  width: 56px;
  height: 56px;
  margin-bottom: 16px;
  object-fit: contain;
}

.title {
  font-size: 22px;
  font-weight: 700;
  color: #1677ff;
  margin: 0 0 8px;
}

.desc {
  font-size: 14px;
  color: #bbb;
  margin: 0;
}

.tips {
  margin-bottom: 16px;
  color: #bbb;
  font-size: 13px;
  text-align: right;
}

.tips a {
  color: #1677ff;
}

@media (max-width: 480px) {
  #userLoginPage {
    min-height: 50vh;
  }

  .login-card {
    width: 100%;
    padding: 28px 20px 16px;
    box-shadow: none;
    border-radius: 0;
    background: transparent;
  }

  .card-logo {
    width: 44px;
    height: 44px;
  }

  .title {
    font-size: 18px;
  }
}
</style>
