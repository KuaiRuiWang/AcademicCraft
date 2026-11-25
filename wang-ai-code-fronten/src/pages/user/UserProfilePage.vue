<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { CameraOutlined } from '@ant-design/icons-vue'
import { message, type UploadProps } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { updateProfile } from '@/api/userController'
import { upload } from '@/api/commonController'
import { formatDate } from '@/utils/time'
import PageHeading from '@/components/PageHeading.vue'
import PersonalNav from '@/components/PersonalNav.vue'

const loginUserStore = useLoginUserStore()
const user = computed(() => loginUserStore.loginUser)
const saving = ref(false)
const uploading = ref(false)
const saveError = ref('')
const form = reactive({
  userName: user.value.userName ?? '',
  userAvatar: user.value.userAvatar ?? '',
  userProfile: user.value.userProfile ?? '',
})

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  if (!file.type?.startsWith('image/') || file.size > 5 * 1024 * 1024) {
    message.error('请选择 5 MB 以内的图片')
    return false
  }
  return true
}

const uploadAvatar: UploadProps['customRequest'] = async ({ file, onSuccess, onError }) => {
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', file as File)
    const res = await upload(data)
    if (res.data.code !== 0 || !res.data.data) {
      throw new Error(res.data.message || '头像上传失败')
    }
    form.userAvatar = res.data.data
    onSuccess?.(res.data)
    message.success('头像已上传，保存资料后生效')
  } catch (error) {
    const failure = error instanceof Error ? error : new Error('头像上传失败')
    onError?.(failure)
    message.error(failure.message)
  } finally {
    uploading.value = false
  }
}

function resetForm() {
  form.userName = user.value.userName ?? ''
  form.userAvatar = user.value.userAvatar ?? ''
  form.userProfile = user.value.userProfile ?? ''
  saveError.value = ''
}

async function handleSave() {
  const userName = form.userName.trim()
  if (!userName) {
    saveError.value = '请输入显示名称'
    return
  }

  saving.value = true
  saveError.value = ''
  try {
    const nextProfile = { ...form, userName, userProfile: form.userProfile.trim() }
    const res = await updateProfile(nextProfile)
    if (res.data.code === 0) {
      loginUserStore.setLoginUser({ ...user.value, ...nextProfile })
      message.success('个人资料已保存')
      await loginUserStore.fetchLoginUser().catch(() => undefined)
      resetForm()
    } else {
      saveError.value = res.data.message || '资料保存失败'
    }
  } catch {
    saveError.value = '保存失败，请检查网络后重试'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page-shell">
    <PageHeading title="个人资料" />
    <PersonalNav />
    <div class="profile-grid">
      <section class="paper-panel profile-card">
        <a-avatar :src="user.userAvatar" :size="64" class="profile-avatar">
          {{ user.userName?.charAt(0) }}
        </a-avatar>
        <h2>{{ user.userName || '未命名用户' }}</h2>
        <p class="profile-bio">{{ user.userProfile || '还没有填写个人简介。' }}</p>
        <dl class="folio-spec">
          <div>
            <dt>账号</dt>
            <dd>{{ user.userAccount || '—' }}</dd>
          </div>
          <div>
            <dt>角色</dt>
            <dd>用户</dd>
          </div>
          <div v-if="user.id">
            <dt>用户 ID</dt>
            <dd>{{ user.id }}</dd>
          </div>
          <div v-if="user.createTime">
            <dt>注册于</dt>
            <dd>{{ formatDate(user.createTime) }}</dd>
          </div>
          <div v-if="user.updateTime">
            <dt>最近更新</dt>
            <dd>{{ formatDate(user.updateTime) }}</dd>
          </div>
        </dl>
        <RouterLink to="/create#my-apps" class="folio-link">打开我的应用</RouterLink>
      </section>

      <section class="paper-panel">
        <h2>编辑资料</h2>
        <a-form
          :model="form"
          :disabled="saving"
          name="profile"
          layout="vertical"
          @finish="handleSave"
        >
          <a-form-item
            label="显示名称"
            name="userName"
            :rules="[{ required: true, whitespace: true, message: '请输入显示名称' }]"
          >
            <a-input v-model:value="form.userName" :maxlength="20" show-count />
          </a-form-item>
          <a-form-item label="个人简介" name="userProfile">
            <a-textarea v-model:value="form.userProfile" :rows="5" :maxlength="200" show-count />
          </a-form-item>
          <a-form-item label="头像" extra="支持图片文件，最大 5 MB。">
            <div class="avatar-field">
              <a-avatar :src="form.userAvatar" :size="36" class="profile-avatar">{{
                form.userName.charAt(0)
              }}</a-avatar>
              <a-upload
                :show-upload-list="false"
                :before-upload="beforeUpload"
                :custom-request="uploadAvatar"
                :disabled="saving || uploading"
                accept="image/*"
              >
                <a-button :loading="uploading"
                  ><CameraOutlined aria-hidden="true" /> 更换头像</a-button
                >
              </a-upload>
            </div>
          </a-form-item>
          <a-alert
            v-if="saveError"
            class="save-error"
            type="error"
            :message="saveError"
            show-icon
          />
          <div class="folio-actions">
            <a-button html-type="submit" type="primary" :loading="saving" :disabled="uploading"
              >保存资料</a-button
            >
            <a-button :disabled="uploading" @click="resetForm">重置</a-button>
          </div>
        </a-form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.4fr);
  gap: clamp(18px, 3vw, 36px);
  align-items: start;
}

.profile-avatar {
  background: var(--color-primary);
}

.profile-card h2 {
  margin: 14px 0 8px;
}

.profile-bio {
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.profile-card .folio-link {
  margin-top: 12px;
}

.avatar-field {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.save-error {
  margin-bottom: 16px;
}

@media (max-width: 760px) {
  .profile-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
