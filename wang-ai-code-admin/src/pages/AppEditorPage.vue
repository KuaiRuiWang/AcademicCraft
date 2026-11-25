<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeftOutlined, ExportOutlined } from '@ant-design/icons-vue'
import { message, type FormInstance, type FormProps } from 'ant-design-vue'
import { getAppVoByIdByAdmin, updateAppByAdmin } from '@/api/operations'
import { formatCodeGenType } from '@/utils/codeGenTypes'
import { formatTime } from '@/utils/time'
import { getDeployUrl } from '@/config/env'
import PageHeading from '@/components/PageHeading.vue'

const route = useRoute()
const id = route.params.id as string
const backPath = '/apps'
const app = ref<API.AppVO>()
const loading = ref(true)
const loadError = ref('')
const submitting = ref(false)
const saveError = ref('')
const formRef = ref<FormInstance>()
const form = reactive({ appName: '', cover: '', priority: 0 })

const rules: FormProps['rules'] = {
  appName: [{ required: true, whitespace: true, max: 50, message: '请输入 1 到 50 个字符' }],
  cover: [{ type: 'url', message: '请输入有效的图片地址', trigger: 'blur' }],
  priority: [{ type: 'number', min: 0, max: 99, message: '优先级范围为 0 到 99' }],
}

function resetForm() {
  form.appName = app.value?.appName || ''
  form.cover = app.value?.cover || ''
  form.priority = app.value?.priority || 0
  saveError.value = ''
  formRef.value?.clearValidate()
}

async function fetchAppInfo() {
  loading.value = true
  loadError.value = ''

  try {
    const res = await getAppVoByIdByAdmin({ id: id as unknown as number })
    if (res.data.code !== 0 || !res.data.data) {
      loadError.value = res.data.message || '无法读取应用'
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
    const res = await updateAppByAdmin({ ...patch, cover: form.cover.trim(), priority: form.priority })
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
    <RouterLink :to="backPath" class="back-link"
      ><ArrowLeftOutlined aria-hidden="true" /> 应用管理</RouterLink
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

            <a-form-item label="应用封面" name="cover" extra="请输入图片链接，推荐 400 × 300。">
              <a-input v-model:value="form.cover" placeholder="https://…" />
              <a-image
                v-if="form.cover"
                :src="form.cover"
                alt="应用封面"
                :width="160"
                class="cover-preview"
              />
            </a-form-item>
            <a-form-item
              label="优先级"
              name="priority"
              extra="优先级为 99 的应用展示在首页精选区。"
            >
              <a-input-number v-model:value="form.priority" :min="0" :max="99" />
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
          <RouterLink class="folio-link" :to="backPath">打开工作区</RouterLink>
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
