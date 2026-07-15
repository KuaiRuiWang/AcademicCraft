<template>
  <div id="userProfilePage">
    <!-- 骨架屏加载 -->
    <template v-if="pageLoading">
      <a-card>
        <a-skeleton active avatar :paragraph="{ rows: 6 }" />
      </a-card>
    </template>

    <template v-else>
      <!-- 头部横幅卡片 -->
      <a-card class="profile-banner">
        <a-row :gutter="[24, 16]" align="middle">
          <a-col flex="auto">
            <a-space :size="16" align="center">
              <!-- 头像上传区域 -->
              <a-upload
                v-if="isEditing"
                :show-upload-list="false"
                :before-upload="beforeUpload"
                :custom-request="customUpload"
                accept="image/*"
              >
                <div class="avatar-upload-trigger">
                  <a-avatar :src="formAvatar" :size="80" />
                  <div class="avatar-mask">
                    <CameraOutlined />
                    <span>更换头像</span>
                  </div>
                </div>
              </a-upload>
              <a-avatar v-else :src="formAvatar" :size="80" />

              <div class="user-info-text">
                <div class="user-display-name">
                  {{ loginUserStore.loginUser.userName ?? '未知用户' }}
                  <a-tag
                    :color="loginUserStore.loginUser.userRole === 'admin' ? '#ff4d4f' : '#1677ff'"
                    class="role-tag"
                  >
                    {{ loginUserStore.loginUser.userRole === 'admin' ? '管理员' : '用户' }}
                  </a-tag>
                </div>
                <div class="user-account">@{{ loginUserStore.loginUser.userAccount ?? '-' }}</div>
              </div>
            </a-space>
          </a-col>
          <a-col>
            <a-button
              v-if="!isEditing"
              type="primary"
              shape="round"
              size="large"
              @click="startEdit"
            >
              <template #icon><EditOutlined /></template>
              编辑资料
            </a-button>
          </a-col>
        </a-row>
      </a-card>

      <!-- 内容卡片 -->
      <a-card class="profile-detail" :bordered="false">
        <!-- 查看模式 -->
        <template v-if="!isEditing">
          <a-row :gutter="[0, 24]">
            <!-- 基本信息 -->
            <a-col :span="24" :md="14">
              <h3 class="section-title">
                <IdcardOutlined />
                基本信息
              </h3>
              <a-descriptions :column="1" size="middle" :label-style="{ color: '#8c8c8c', fontWeight: 500 }">
                <a-descriptions-item label="用户 ID">
                  #{{ loginUserStore.loginUser.id ?? '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="用户名">
                  {{ loginUserStore.loginUser.userName ?? '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="注册时间">
                  <ClockCircleOutlined />
                  {{ loginUserStore.loginUser.createTime ?? '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="最近更新">
                  {{ loginUserStore.loginUser.updateTime ?? '-' }}
                </a-descriptions-item>
              </a-descriptions>
            </a-col>

            <a-col :span="24" :md="10">
              <!-- 个人简介 -->
              <h3 class="section-title">
                <FileTextOutlined />
                个人简介
              </h3>
              <a-typography>
                <a-typography-paragraph
                  :type="loginUserStore.loginUser.userProfile ? undefined : 'secondary'"
                  class="profile-bio"
                >
                  {{ loginUserStore.loginUser.userProfile || '这个人很懒，什么都没写...' }}
                </a-typography-paragraph>
              </a-typography>
            </a-col>
          </a-row>
        </template>

        <!-- 编辑模式 -->
        <a-form
          v-else
          :model="formState"
          layout="vertical"
          :label-col="{ style: { fontWeight: 500 } }"
          class="edit-form"
        >
          <a-row :gutter="[24, 0]">
            <a-col :span="24" :md="14">
              <a-form-item
                label="用户名"
                name="userName"
                :rules="[{ required: true, message: '请输入用户名', trigger: 'blur' }]"
              >
                <a-input
                  v-model:value="formState.userName"
                  placeholder="请输入用户名"
                  size="large"
                  :maxlength="20"
                  show-count
                >
                  <template #prefix><UserOutlined /></template>
                </a-input>
              </a-form-item>
            </a-col>
            <a-col :span="24" :md="10">
              <a-form-item label="头像">
                <a-upload
                  :show-upload-list="false"
                  :before-upload="beforeUpload"
                  :custom-request="customUpload"
                  accept="image/*"
                >
                  <div class="avatar-upload-trigger">
                    <a-avatar :src="formAvatar" :size="96" />
                    <div class="avatar-mask">
                      <CameraOutlined />
                      <span>更换头像</span>
                    </div>
                  </div>
                </a-upload>
                <a-typography-text type="secondary" class="upload-hint">
                  支持 JPG/PNG，不超过 5MB
                </a-typography-text>
              </a-form-item>
            </a-col>
          </a-row>

          <a-form-item label="个人简介" name="userProfile">
            <a-textarea
              v-model:value="formState.userProfile"
              placeholder="介绍一下自己，让更多人认识你..."
              :rows="5"
              :maxlength="200"
              show-count
              size="large"
            />
          </a-form-item>

          <a-divider />

          <a-form-item>
            <a-space :size="12">
              <a-button
                type="primary"
                :loading="saving"
                size="large"
                @click="handleSave"
              >
                <template #icon><SaveOutlined /></template>
                保存修改
              </a-button>
              <a-button size="large" @click="cancelEdit">
                <template #icon><CloseOutlined /></template>
                取消
              </a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </a-card>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import type { UploadProps } from 'ant-design-vue'
import {
  EditOutlined,
  CameraOutlined,
  IdcardOutlined,
  ClockCircleOutlined,
  FileTextOutlined,
  UserOutlined,
  SaveOutlined,
  CloseOutlined,
} from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { updateProfile } from '@/api/userController.ts'
import { upload } from '@/api/commonController.ts'

const loginUserStore = useLoginUserStore()
const pageLoading = ref(true)
const saving = ref(false)
const isEditing = ref(false)

const formState = reactive<API.UserProfileUpdateRequest>({
  userName: '',
  userAvatar: '',
  userProfile: '',
})

const formAvatar = ref('')
const originalAvatar = ref('')

// 页面加载时获取用户信息
onMounted(async () => {
  try {
    await loginUserStore.fetchLoginUser()
    syncFormFromStore()
  } finally {
    pageLoading.value = false
  }
})

// 从 store 同步数据
const syncFormFromStore = () => {
  const user = loginUserStore.loginUser
  formAvatar.value = user.userAvatar ?? ''
  originalAvatar.value = user.userAvatar ?? ''
}

// 进入编辑模式
const startEdit = () => {
  const user = loginUserStore.loginUser
  formState.userName = user.userName ?? ''
  formState.userAvatar = user.userAvatar ?? ''
  formState.userProfile = user.userProfile ?? ''
  formAvatar.value = user.userAvatar ?? ''
  originalAvatar.value = user.userAvatar ?? ''
  isEditing.value = true
}

// 取消编辑
const cancelEdit = () => {
  // 恢复原始值
  formAvatar.value = originalAvatar.value
  isEditing.value = false
}

// 上传前校验
const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const isImage = file.type?.startsWith('image/')
  if (!isImage) {
    message.error('请选择图片文件')
    return false
  }
  const isLt5M = (file.size ?? 0) / 1024 / 1024 < 5
  if (!isLt5M) {
    message.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

// 自定义上传
const customUpload: UploadProps['customRequest'] = async (options) => {
  const { file, onSuccess, onError } = options as any
  try {
    // 本地预览
    const reader = new FileReader()
    reader.onload = () => {
      formAvatar.value = reader.result as string
    }
    reader.readAsDataURL(file as File)

    // 上传服务端
    const formData = new FormData()
    formData.append('file', file as File)
    const res = await upload(formData)
    if (res.data.code === 0 && res.data.data) {
      formState.userAvatar = res.data.data
      message.success('头像上传成功')
      onSuccess?.(res.data, file)
    } else {
      message.error('头像上传失败，' + res.data.message)
      formAvatar.value = originalAvatar.value
      onError?.(new Error(res.data.message))
    }
  } catch {
    message.error('头像上传失败，请稍后重试')
    formAvatar.value = originalAvatar.value
    onError?.(new Error('上传失败'))
  }
}

// 保存修改
const handleSave = async () => {
  if (!formState.userName?.trim()) {
    message.warning('请输入用户名')
    return
  }

  saving.value = true
  try {
    const res = await updateProfile({
      userName: formState.userName?.trim(),
      userAvatar: formState.userAvatar,
      userProfile: formState.userProfile?.trim(),
    })
    if (res.data.code === 0) {
      message.success('个人资料修改成功')
      isEditing.value = false
      await loginUserStore.fetchLoginUser()
      syncFormFromStore()
    } else {
      message.error('修改失败，' + res.data.message)
    }
  } catch {
    message.error('修改失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
#userProfilePage {
  max-width: 800px;
  margin: 24px auto;
  padding: 0 16px;
}

/* ======== 顶部横幅卡片 ======== */
.profile-banner {
  background: linear-gradient(135deg, #1677ff 0%, #69b1ff 100%);
  border-radius: 12px;
  margin-bottom: 16px;
}

.profile-banner :deep(.ant-card-body) {
  padding: 32px 24px;
}

.profile-banner .user-display-name {
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  line-height: 1.3;
}

.profile-banner .user-account {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.75);
  margin-top: 4px;
}

.profile-banner .role-tag {
  vertical-align: middle;
  margin-left: 8px;
  font-weight: 500;
}

.profile-banner :deep(.ant-btn-primary) {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.3);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.profile-banner :deep(.ant-btn-primary:hover) {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.5);
}

/* 横幅中头像样式 */
.profile-banner :deep(.ant-avatar) {
  border: 3px solid rgba(255, 255, 255, 0.4);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* ======== 详情卡片 ======== */
.profile-detail {
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.profile-detail :deep(.ant-card-body) {
  padding: 28px 24px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #262626;
  margin: 0 0 16px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title .anticon {
  color: #1677ff;
  font-size: 16px;
}

/* 简介文本 */
.profile-bio {
  background: #fafafa;
  border-radius: 8px;
  padding: 16px;
  min-height: 80px;
  margin: 0 !important;
  line-height: 1.8;
  white-space: pre-wrap;
}

/* ======== 头像上传触发器 ======== */
.avatar-upload-trigger {
  position: relative;
  display: inline-block;
  cursor: pointer;
  border-radius: 50%;
  line-height: 0;
}

.avatar-mask {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 12px;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.25s ease;
}

.avatar-upload-trigger:hover .avatar-mask {
  opacity: 1;
}

.avatar-mask :deep(.anticon) {
  font-size: 22px;
}

.upload-hint {
  display: block;
  margin-top: 8px;
  font-size: 12px;
}

/* ======== 编辑表单 ======== */
.edit-form {
  margin-top: 4px;
}

:deep(.ant-descriptions-item-label) {
  font-weight: 500;
}

/* ======== 响应式 ======== */
@media (max-width: 768px) {
  .profile-banner :deep(.ant-card-body) {
    padding: 20px 16px;
    text-align: center;
  }

  .profile-banner .user-display-name {
    font-size: 18px;
  }
}
</style>
