<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftOutlined,
  CameraOutlined,
  DeleteOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue'
import { message, type UploadProps } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { addProject, getEditableProjectVo, updateProject } from '@/api/projectController'
import { listCategories } from '@/api/projectCategoryController'
import { upload } from '@/api/commonController'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import PageHeading from '@/components/PageHeading.vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const id = route.params.id as string | undefined
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')
const backPath = computed(() => (isAdmin.value ? '/admin/projectManage' : '/my/projects'))
const status = ref(1)
const authorizationRequired = computed(() => isAdmin.value && status.value === 1)
const loading = ref(true)
const submitting = ref(false)
const uploads = ref(0)
const loadError = ref('')
const categoryError = ref('')
const saveError = ref('')
const categories = ref<API.ProjectCategory[]>([])
const statusOptions = [
  { value: 0, label: '草稿' },
  { value: 1, label: '已上架' },
  { value: 2, label: '已下架' },
]

const form = reactive<API.ProjectAddRequest>({
  name: '',
  categoryId: undefined,
  techTags: '',
  description: '',
  detailContent: '',
  coverImage: '',
  sourceType: 2,
  sourceUrl: '',
  sourceAttribution: '',
  licenseTerms: '',
  demoUrl: '',
  images: [],
})

async function loadCategories() {
  categoryError.value = ''
  try {
    const res = await listCategories()
    if (res.data.code !== 0) throw new Error(res.data.message)
    categories.value = res.data.data || []
  } catch {
    categoryError.value = '分类加载失败，请重新读取。'
  }
}

async function loadProject() {
  if (!id) return
  loadError.value = ''
  try {
    const res = await getEditableProjectVo({ id: id as unknown as number })
    const project = res.data.data
    if (res.data.code !== 0 || !project) {
      loadError.value = res.data.message || '无法读取模板资料。'
      return
    }
    status.value = project.status ?? 0
    Object.assign(form, {
      name: project.name || '',
      categoryId: project.categoryId,
      techTags: project.techTags || '',
      description: project.description || '',
      detailContent: project.detailContent || '',
      coverImage: project.coverImage || '',
      sourceType: project.sourceType,
      sourceUrl: project.sourceUrl || '',
      sourceAttribution: project.sourceAttribution || '',
      licenseTerms: project.licenseTerms || '',
      demoUrl: project.demoUrl || '',
      images: (project.images || []).map((image) => ({
        imageUrl: image.imageUrl || '',
        sortOrder: image.sortOrder || 0,
        type: image.type || 0,
      })),
    })
  } catch {
    loadError.value = '模板资料加载失败，请检查网络后重试。'
  }
}

async function loadEditor() {
  loading.value = true
  await Promise.all([loadCategories(), loadProject()])
  loading.value = false
}

const beforeImageUpload: UploadProps['beforeUpload'] = (file) => {
  if (!file.type?.startsWith('image/') || file.size > 5 * 1024 * 1024) {
    message.error('请选择 5 MB 以内的图片')
    return false
  }
  return true
}

function uploadImage(target: 'cover' | 'screenshot'): NonNullable<UploadProps['customRequest']> {
  return async ({ file, onSuccess, onError }) => {
    uploads.value++
    try {
      const data = new FormData()
      data.append('file', file as File)
      const res = await upload(data)
      const url = res.data.data
      if (res.data.code !== 0 || !url) throw new Error(res.data.message || '图片上传失败')
      if (target === 'cover') {
        form.coverImage = url
      } else {
        const images = form.images || []
        form.images = [...images, { imageUrl: url, sortOrder: images.length + 1, type: 0 }]
      }
      onSuccess?.(res.data)
      message.success('图片已上传')
    } catch (error) {
      const failure = error instanceof Error ? error : new Error('图片上传失败，请重试')
      onError?.(failure)
      message.error(failure.message)
    } finally {
      uploads.value--
    }
  }
}
const uploadCover = uploadImage('cover')
const uploadScreenshot = uploadImage('screenshot')

function removeImage(index: number) {
  form.images = form.images?.filter((_, itemIndex) => itemIndex !== index)
}

async function handleSubmit() {
  if (uploads.value || submitting.value) return
  submitting.value = true
  saveError.value = ''
  try {
    const template = {
      ...form,
      name: form.name?.trim(),
      techTags: form.techTags?.trim(),
      description: form.description?.trim(),
      detailContent: form.detailContent?.trim(),
      sourceUrl: form.sourceUrl?.trim(),
      demoUrl: form.demoUrl?.trim(),
      price: isAdmin.value ? 0 : undefined,
      sourceAttribution: isAdmin.value ? form.sourceAttribution?.trim() : undefined,
      licenseTerms: isAdmin.value ? form.licenseTerms?.trim() : undefined,
    }
    const res = id
      ? await updateProject({
          ...template,
          id: id as unknown as number,
          status: isAdmin.value ? status.value : undefined,
        })
      : await addProject(template)
    if (res.data.code === 0) {
      message.success(id ? '模板已保存' : '模板已发布')
      await router.push(backPath.value)
    } else {
      saveError.value = res.data.message || '模板保存失败'
    }
  } catch {
    saveError.value = '模板保存失败，请检查网络后重试。'
  } finally {
    submitting.value = false
  }
}

onMounted(loadEditor)
</script>

<template>
  <div class="page-shell">
    <RouterLink :to="backPath" class="back-link">
      <ArrowLeftOutlined aria-hidden="true" /> {{ isAdmin ? '模板管理' : '我的项目' }}
    </RouterLink>
    <PageHeading
      :title="id ? '编辑模板' : '发布模板'"
      description="整理模板介绍、演示和源码地址，补充来源及使用许可。"
    />

    <div v-if="loading" class="paper-panel"><a-skeleton active :paragraph="{ rows: 5 }" /></div>
    <a-alert v-else-if="loadError" :message="loadError" type="error" show-icon>
      <template #action><a-button size="small" @click="loadEditor">重新读取</a-button></template>
    </a-alert>

    <a-form
      v-else
      :model="form"
      :disabled="submitting"
      name="template-editor"
      layout="vertical"
      @finish="handleSubmit"
    >
      <div class="template-editor">
        <section class="paper-panel">
          <h2>模板资料</h2>
          <a-form-item
            label="模板名称"
            name="name"
            :rules="[{ required: true, whitespace: true, message: '请输入模板名称' }]"
          >
            <a-input
              v-model:value="form.name"
              placeholder="例如，工作室库存台账"
              :maxlength="100"
            />
          </a-form-item>
          <a-form-item
            label="简短介绍"
            name="description"
            :rules="[{ required: true, whitespace: true, message: '请填写模板介绍' }]"
          >
            <a-textarea
              v-model:value="form.description"
              placeholder="简要描述主要功能"
              :rows="3"
              :maxlength="100"
              show-count
            />
          </a-form-item>
          <a-form-item label="技术标签" name="techTags" extra="使用逗号分隔，例如 Vue, MySQL。">
            <a-input v-model:value="form.techTags" />
          </a-form-item>

          <div class="markdown-field">
            <label for="template-detail">详细内容</label>
            <a-tabs size="small">
              <a-tab-pane key="edit" tab="编辑">
                <a-textarea
                  id="template-detail"
                  v-model:value="form.detailContent"
                  placeholder="支持 Markdown，可填写安装说明和主要功能。"
                  :rows="10"
                />
              </a-tab-pane>
              <a-tab-pane key="preview" tab="预览">
                <MarkdownRenderer v-if="form.detailContent" :content="form.detailContent" />
                <p v-else class="folio-empty">写下详细内容后可在这里预览。</p>
              </a-tab-pane>
            </a-tabs>
          </div>

          <div class="asset-field">
            <h3>模板截图</h3>
            <p>最多 6 张。每张图片不超过 5 MB。</p>
            <div class="screenshot-list">
              <div v-for="(image, index) in form.images" :key="index" class="screenshot-item">
                <img :src="image.imageUrl" :alt="'模板截图 ' + (index + 1)" />
                <a-button
                  danger
                  size="small"
                  :aria-label="'删除截图 ' + (index + 1)"
                  @click="removeImage(index)"
                >
                  <DeleteOutlined aria-hidden="true" />
                </a-button>
              </div>
              <a-upload
                v-if="(form.images?.length || 0) < 6"
                :show-upload-list="false"
                :before-upload="beforeImageUpload"
                :custom-request="uploadScreenshot"
                :disabled="Boolean(uploads) || submitting"
                accept="image/*"
              >
                <a-button :loading="Boolean(uploads)"
                  ><PlusOutlined aria-hidden="true" /> 添加截图</a-button
                >
              </a-upload>
            </div>
          </div>
        </section>

        <aside class="paper-panel publishing-settings">
          <h2>发布设置</h2>
          <a-alert
            v-if="categoryError"
            :message="categoryError"
            type="error"
            class="save-error"
            show-icon
          >
            <template #action
              ><a-button size="small" @click="loadCategories">重试</a-button></template
            >
          </a-alert>
          <a-form-item
            label="模板分类"
            name="categoryId"
            :rules="[{ required: true, message: '请选择分类' }]"
          >
            <a-select
              v-model:value="form.categoryId"
              placeholder="请选择分类"
              :options="
                categories.map((category) => ({ value: category.id, label: category.name }))
              "
            />
          </a-form-item>
          <a-form-item v-if="id && isAdmin" label="上架状态">
            <a-select aria-label="上架状态" v-model:value="status" :options="statusOptions" />
          </a-form-item>
          <p v-else-if="!id" class="publish-note">发布后，模板将在目录开放免费领取。</p>
          <a-form-item label="演示地址" name="demoUrl">
            <a-input v-model:value="form.demoUrl" placeholder="https://…" />
          </a-form-item>
          <a-form-item
            label="源码下载地址"
            name="sourceUrl"
            :rules="[
              { required: authorizationRequired, whitespace: true, message: '请填写源码下载地址' },
            ]"
          >
            <a-input v-model:value="form.sourceUrl" placeholder="https://…" :maxlength="500" />
          </a-form-item>
          <template v-if="isAdmin">
            <a-form-item
              label="模板来源或授权方"
              name="sourceAttribution"
              :rules="[
                {
                  required: authorizationRequired,
                  whitespace: true,
                  message: '请填写模板来源或授权方',
                },
              ]"
            >
              <a-input
                v-model:value="form.sourceAttribution"
                placeholder="原创作者、上游项目或授权方"
                :maxlength="500"
              />
            </a-form-item>
            <a-form-item
              label="使用许可"
              name="licenseTerms"
              :rules="[
                { required: authorizationRequired, whitespace: true, message: '请填写使用许可' },
              ]"
            >
              <a-textarea
                v-model:value="form.licenseTerms"
                placeholder="许可名称、商用和再分发条件"
                :rows="3"
                :maxlength="1000"
              />
            </a-form-item>
          </template>
          <div class="asset-field cover-field">
            <h3>封面图片</h3>
            <img v-if="form.coverImage" :src="form.coverImage" alt="模板封面" />
            <p>推荐 800 × 400，最大 5 MB。</p>
            <a-upload
              :show-upload-list="false"
              :before-upload="beforeImageUpload"
              :custom-request="uploadCover"
              :disabled="Boolean(uploads) || submitting"
              accept="image/*"
            >
              <a-button :loading="Boolean(uploads)">
                <CameraOutlined aria-hidden="true" />
                {{ form.coverImage ? '更换封面' : '上传封面' }}
              </a-button>
            </a-upload>
          </div>
        </aside>
      </div>

      <a-alert v-if="saveError" class="save-error" type="error" :message="saveError" show-icon />
      <div class="folio-actions template-actions">
        <a-button
          type="primary"
          html-type="submit"
          :loading="submitting"
          :disabled="Boolean(uploads)"
        >
          {{ id ? '保存模板' : '发布模板' }}
        </a-button>
        <RouterLink class="folio-link" :to="backPath">取消</RouterLink>
      </div>
    </a-form>
  </div>
</template>

<style scoped>
.template-editor {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(250px, 1fr);
  gap: clamp(18px, 3vw, 36px);
  align-items: start;
  margin-bottom: 20px;
}

.markdown-field > label {
  font-weight: 650;
}

.asset-field {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid var(--color-border);
}

.asset-field h3 {
  font-size: 1.1rem;
}

.asset-field p,
.publish-note {
  margin: 5px 0 12px;
  color: var(--color-text-secondary);
  font-size: 0.85rem;
}

.cover-field img {
  display: block;
  max-width: 100%;
  max-height: 150px;
  margin: 10px 0;
  object-fit: cover;
}

.screenshot-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.screenshot-item {
  position: relative;
  width: 90px;
  height: 70px;
}

.screenshot-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.screenshot-item :deep(button) {
  position: absolute;
  inset: 4px 4px auto auto;
}

.save-error {
  margin-bottom: 16px;
}

.template-actions {
  justify-content: end;
}

@media (max-width: 760px) {
  .template-editor {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
