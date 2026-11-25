<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeftOutlined, ExportOutlined } from '@ant-design/icons-vue'
import { message, type FormInstance, type FormProps } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { getAppVoById, updateApp } from '@/api/appController'
import { formatCodeGenType } from '@/utils/codeGenTypes'
import { formatTime } from '@/utils/time'
import { getDeployUrl } from '@/config/env'
import PageHeading from '@/components/PageHeading.vue'

const route = useRoute()
const loginUserStore = useLoginUserStore()
const id = route.params.id as string
const chatPath = '/app/chat/' + id
const app = ref<API.AppVO>()
const loading = ref(true)
const loadError = ref('')
const submitting = ref(false)
const saveError = ref('')
const formRef = ref<FormInstance>()
const form = reactive({ appName: '' })

const rules: FormProps['rules'] = {
  appName: [{ required: true, whitespace: true, max: 50, message: '请输入 1 到 50 个字符' }],
}

function resetForm() {
  form.appName = app.value?.appName || ''
  saveError.value = ''
  formRef.value?.clearValidate()
}

async function fetchAppInfo() {
  loading.value = true
  loadError.value = ''

  try {
    const res = await getAppVoById({ id: id as unknown as number })
    if (res.data.code !== 0 || !res.data.data) {
      loadError.value = res.data.message || '无法读取应用'
      return
    }
    if (String(res.data.data.userId) !== String(loginUserStore.loginUser.id)) {
      loadError.value = '只有应用作者可以修改设置。'
      return
    }
    app.value = res.data.data
    resetForm()
  } catch {
    loadError.value = '无法读取应用，请检查网络后重试。'
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (!app.value?.id) return
  submitting.value = true
  saveError.value = ''

  const appName = form.appName.trim()
  try {
    const patch = { id: app.value.id, appName }
    const res = await updateApp(patch)
    if (res.data.code === 0) {
      message.success('应用设置已保存')
      await fetchAppInfo()
    } else {
      saveError.value = res.data.message || '保存失败'
    }
  } catch {
    saveError.value = '保存失败，请检查网络后重试'
  } finally {
    submitting.value = false
  }
}

onMounted(fetchAppInfo)
</script>

<template>
  <div class="page-shell">
    <RouterLink :to="chatPath" class="back-link"
      ><ArrowLeftOutlined aria-hidden="true" /> 对话工作区</RouterLink
    >
    <PageHeading title="应用设置" description="更新应用资料，查看构建和部署信息。" />
    <div v-if="loading" class="paper-panel"><a-skeleton active :paragraph="{ rows: 5 }" /></div>
    <a-alert v-else-if="loadError" :message="loadError" type="error" show-icon>
      <template #action><a-button size="small" @click="fetchAppInfo">重新读取</a-button></template>
    </a-alert>

    <div v-else-if="app" class="settings-grid">
      <section class="paper-panel">
        <h2>应用资料</h2>
        <a-form
          ref="formRef"
          :model="form"
          :rules="rules"
          layout="vertical"
          name="app-settings"
          @finish="handleSubmit"
        >
          <a-form-item label="应用名称" name="appName">
            <a-input v-model:value="form.appName" :maxlength="50" show-count />
          </a-form-item>


          <div class="application-brief">
            <h3>初始需求</h3>
            <p>{{ app.initPrompt || '暂无初始需求' }}</p>
          </div>

          <a-alert
            v-if="saveError"
            :message="saveError"
            type="error"
            class="save-error"
            show-icon
          />
          <div class="folio-actions">
            <a-button type="primary" html-type="submit" :loading="submitting">保存更改</a-button>
            <a-button :disabled="submitting" @click="resetForm">重置</a-button>
          </div>
        </a-form>
      </section>

      <aside class="paper-panel">
        <h2>构建信息</h2>
        <dl class="folio-spec">
          <div>
            <dt>构建方式</dt>
            <dd>{{ formatCodeGenType(app.codeGenType) }}</dd>
          </div>
          <div>
            <dt>论文初稿</dt>
            <dd>{{ app.generateThesis ? '已启用' : '未启用' }}</dd>
          </div>
          <div>
            <dt>应用 ID</dt>
            <dd>{{ app.id }}</dd>
          </div>
          <div>
            <dt>创建者</dt>
            <dd>{{ app.user?.userName || '—' }}</dd>
          </div>
          <div v-if="app.createTime">
            <dt>创建时间</dt>
            <dd>{{ formatTime(app.createTime) }}</dd>
          </div>
          <div v-if="app.updateTime">
            <dt>最近更新</dt>
            <dd>{{ formatTime(app.updateTime) }}</dd>
          </div>
          <div>
            <dt>部署时间</dt>
            <dd>{{ app.deployedTime ? formatTime(app.deployedTime) : '未部署' }}</dd>
          </div>
          <div v-if="app.deployKey">
            <dt>部署标识</dt>
            <dd>{{ app.deployKey }}</dd>
          </div>
        </dl>
        <div class="folio-actions settings-links">
          <RouterLink class="folio-link" :to="chatPath">打开工作区</RouterLink>
          <a
            v-if="app.deployKey"
            class="folio-link"
            :href="getDeployUrl(app.deployKey, app.codeGenType)"
            target="_blank"
            rel="noopener"
          >
            已部署应用 <ExportOutlined aria-hidden="true" />
          </a>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.settings-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(245px, 1fr);
  align-items: start;
  gap: clamp(18px, 3vw, 36px);
}

.settings-links {
  margin-top: 14px;
}

.application-brief {
  margin: 0 0 22px;
  padding-block: 15px;
  border-block: 1px solid var(--color-border);
}

.application-brief h3 {
  font-size: 1rem;
}

.application-brief p {
  margin: 7px 0 0;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.cover-preview {
  display: block;
  margin-top: 10px;
}

.save-error {
  margin-bottom: 16px;
}

@media (max-width: 760px) {
  .settings-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
