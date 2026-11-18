<template>
  <div id="projectEditPage">
    <div class="container">
      <!-- 页面标题 -->
      <div class="page-header">
        <a-button type="link" @click="$router.back()"> <LeftOutlined /> 返回 </a-button>
        <h1 class="page-title">{{ isEdit ? '编辑项目' : '发布模板' }}</h1>
      </div>

      <a-spin :spinning="submitting" tip="保存中...">
        <a-card class="form-card" :bordered="false">
          <a-form
            :model="form"
            name="template-editor"
            layout="vertical"
            :label-col="{ style: { fontWeight: 600 } }"
            @finish="handleSubmit"
          >
            <a-row :gutter="24">
              <!-- 基本信息 -->
              <a-col :span="24" :md="16">
                <a-form-item label="项目名称" required>
                  <a-input
                    v-model:value="form.name"
                    placeholder="请输入项目名称"
                    size="large"
                    :maxlength="100"
                  />
                </a-form-item>

                <a-form-item label="项目分类" required>
                  <a-select v-model:value="form.categoryId" placeholder="请选择分类" size="large">
                    <a-select-option v-for="cat in categories" :key="cat.id" :value="cat.id">
                      {{ cat.name }}
                    </a-select-option>
                  </a-select>
                </a-form-item>

                <a-form-item label="技术标签">
                  <a-input
                    v-model:value="form.techTags"
                    placeholder="多个标签用逗号分隔，如：Spring Boot,Vue,MySQL"
                    size="large"
                  />
                </a-form-item>

                <a-form-item label="项目简介" required>
                  <a-textarea
                    v-model:value="form.description"
                    placeholder="简要描述项目功能与特点（100字以内）"
                    :rows="3"
                    :maxlength="100"
                    show-count
                    size="large"
                  />
                </a-form-item>

                <a-divider>详细说明</a-divider>

                <a-form-item label="详细内容（支持 Markdown）">
                  <div class="editor-wrapper">
                    <a-tabs>
                      <a-tab-pane key="edit" tab="编辑">
                        <a-textarea
                          v-model:value="form.detailContent"
                          placeholder="使用 Markdown 格式详细描述项目..."
                          :rows="15"
                          class="editor-textarea"
                        />
                      </a-tab-pane>
                      <a-tab-pane key="preview" tab="预览">
                        <div class="markdown-preview" v-html="previewContent"></div>
                      </a-tab-pane>
                    </a-tabs>
                  </div>
                </a-form-item>
              </a-col>

              <!-- 右侧：封面和设置 -->
              <a-col :span="24" :md="8">
                <div class="sidebar-form">
                  <!-- 封面图片 -->
                  <a-form-item label="封面图片">
                    <div class="cover-upload">
                      <a-upload
                        :show-upload-list="false"
                        :before-upload="beforeCoverUpload"
                        :custom-request="customCoverUpload"
                        accept="image/*"
                      >
                        <div v-if="form.coverImage" class="cover-preview-wrapper">
                          <img :src="form.coverImage" class="cover-preview" alt="封面" />
                          <div class="cover-mask">
                            <CameraOutlined />
                            <span>更换封面</span>
                          </div>
                        </div>
                        <div v-else class="cover-placeholder">
                          <PlusOutlined />
                          <span>上传封面</span>
                        </div>
                      </a-upload>
                      <p class="upload-hint">推荐尺寸 800x400，不超过 5MB</p>
                    </div>
                  </a-form-item>

                  <template v-if="isAdmin">
                    <a-form-item
                      label="模板来源或授权方"
                      name="sourceAttribution"
                      validate-trigger="blur"
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
                        placeholder="填写原创作者、上游项目或授权方"
                        :maxlength="500"
                      />
                    </a-form-item>
                    <a-form-item
                      label="使用许可"
                      name="licenseTerms"
                      validate-trigger="blur"
                      :rules="[
                        {
                          required: authorizationRequired,
                          whitespace: true,
                          message: '请填写许可名称、商用和再分发条件',
                        },
                      ]"
                      extra="上架前核对授权。上架后模板可免费领取。"
                    >
                      <a-textarea
                        v-model:value="form.licenseTerms"
                        placeholder="填写许可名称、是否允许商用和再分发，以及适用条件"
                        :rows="3"
                        :maxlength="1000"
                      />
                    </a-form-item>
                  </template>

                  <!-- 演示地址 -->
                  <a-form-item label="演示地址">
                    <a-input v-model:value="form.demoUrl" placeholder="https://..." size="large" />
                  </a-form-item>

                  <a-form-item
                    label="源码下载地址"
                    name="sourceUrl"
                    validate-trigger="blur"
                    :rules="[
                      {
                        required: authorizationRequired,
                        whitespace: true,
                        message: '请填写源码下载地址',
                      },
                    ]"
                  >
                    <a-input
                      v-model:value="form.sourceUrl"
                      placeholder="网盘链接或其他下载地址"
                      :maxlength="500"
                      size="large"
                    />
                  </a-form-item>

                  <a-divider />

                  <!-- 项目截图 -->
                  <a-form-item label="项目截图">
                    <div class="screenshot-list">
                      <div v-for="(img, idx) in form.images" :key="idx" class="screenshot-item">
                        <img :src="img.imageUrl" alt="截图" />
                        <a-button
                          type="text"
                          danger
                          shape="circle"
                          size="small"
                          class="remove-btn"
                          @click="removeImage(idx)"
                        >
                          <DeleteOutlined />
                        </a-button>
                      </div>
                      <a-upload
                        v-if="(form.images?.length || 0) < 6"
                        :show-upload-list="false"
                        :before-upload="beforeImageUpload"
                        :custom-request="customImageUpload"
                        accept="image/*"
                      >
                        <div class="screenshot-add">
                          <PlusOutlined />
                          <span>添加</span>
                        </div>
                      </a-upload>
                    </div>
                    <p class="upload-hint">最多 6 张截图</p>
                  </a-form-item>
                </div>
              </a-col>
            </a-row>

            <a-divider />

            <!-- 提交按钮 -->
            <a-form-item>
              <a-space :size="12">
                <a-button type="primary" size="large" :loading="submitting" html-type="submit">
                  <template #icon><SaveOutlined aria-hidden="true" /></template>
                  {{ isEdit ? '保存修改' : '发布模板' }}
                </a-button>
                <a-button size="large" @click="$router.back()">取消</a-button>
              </a-space>
            </a-form-item>
          </a-form>
        </a-card>
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { marked } from 'marked'
import {
  LeftOutlined,
  CameraOutlined,
  PlusOutlined,
  SaveOutlined,
  DeleteOutlined,
} from '@ant-design/icons-vue'
import type { UploadProps } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { addProject, updateProject, getEditableProjectVo } from '@/api/projectController.ts'
import { listCategories } from '@/api/projectCategoryController.ts'
import { upload } from '@/api/commonController.ts'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const isEdit = computed(() => !!route.params.id)
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')
const projectStatus = ref<number>()
const authorizationRequired = computed(
  () => isAdmin.value && (!isEdit.value || projectStatus.value === 1),
)
const submitting = ref(false)
const categories = ref<API.ProjectCategory[]>([])

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

// 加载分类
const loadCategories = async () => {
  try {
    const res = await listCategories()
    if (res.data.code === 0 && res.data.data) {
      categories.value = res.data.data
    }
  } catch {
    // ignore
  }
}

// 编辑模式：加载现有数据
const loadProject = async () => {
  if (!isEdit.value) return
  try {
    // 不使用 Number()，避免 JS 大数精度丢失。后端已配置 Long → String 序列化
    const id = route.params.id as string
    const res = await getEditableProjectVo({ id: id as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      const p = res.data.data
      form.name = p.name || ''
      form.categoryId = p.categoryId
      form.techTags = p.techTags || ''
      form.description = p.description || ''
      form.detailContent = p.detailContent || ''
      form.coverImage = p.coverImage || ''
      projectStatus.value = p.status
      form.sourceType = p.sourceType
      form.sourceUrl = p.sourceUrl || ''
      form.sourceAttribution = p.sourceAttribution || ''
      form.licenseTerms = p.licenseTerms || ''
      form.demoUrl = p.demoUrl || ''
      form.images = (p.images || []).map((img) => ({
        imageUrl: img.imageUrl || '',
        sortOrder: img.sortOrder || 0,
        type: img.type || 0,
      }))
    } else {
      message.error(res.data.message || '加载项目失败')
      router.back()
    }
  } catch {
    message.error('加载项目失败')
    router.back()
  }
}

// Markdown 预览
const previewContent = computed(() => {
  if (!form.detailContent) return '<p style="color:#999">暂无内容</p>'
  try {
    return marked.parse(form.detailContent) as string
  } catch {
    return form.detailContent
  }
})

// 封面上传
const beforeCoverUpload: UploadProps['beforeUpload'] = (file) => {
  if (!file.type?.startsWith('image/')) {
    message.error('请选择图片文件')
    return false
  }
  if ((file.size ?? 0) / 1024 / 1024 > 5) {
    message.error('图片不能超过 5MB')
    return false
  }
  return true
}

const customCoverUpload: UploadProps['customRequest'] = async (options) => {
  const { file, onSuccess, onError } = options as any
  try {
    const formData = new FormData()
    formData.append('file', file as File)
    const res = await upload(formData)
    if (res.data.code === 0 && res.data.data) {
      form.coverImage = res.data.data
      message.success('封面上传成功')
      onSuccess?.(res.data, file)
    } else {
      onError?.(new Error(res.data.message))
    }
  } catch {
    onError?.(new Error('上传失败'))
  }
}

// 截图上上传
const beforeImageUpload: UploadProps['beforeUpload'] = (file) => {
  if (!file.type?.startsWith('image/')) {
    message.error('请选择图片文件')
    return false
  }
  if ((file.size ?? 0) / 1024 / 1024 > 5) {
    message.error('图片不能超过 5MB')
    return false
  }
  return true
}

const customImageUpload: UploadProps['customRequest'] = async (options) => {
  const { file, onSuccess, onError } = options as any
  try {
    const formData = new FormData()
    formData.append('file', file as File)
    const res = await upload(formData)
    if (res.data.code === 0 && res.data.data) {
      form.images = [
        ...(form.images || []),
        { imageUrl: res.data.data, sortOrder: (form.images?.length || 0) + 1, type: 0 },
      ]
      message.success('截图上上传成功')
      onSuccess?.(res.data, file)
    } else {
      onError?.(new Error(res.data.message))
    }
  } catch {
    onError?.(new Error('上传失败'))
  }
}

const removeImage = (idx: number) => {
  form.images = (form.images || []).filter((_, i) => i !== idx)
}

// 提交
const handleSubmit = async () => {
  if (!form.name?.trim()) {
    message.warning('请输入项目名称')
    return
  }
  if (!form.categoryId) {
    message.warning('请选择项目分类')
    return
  }
  if (!form.description?.trim()) {
    message.warning('请填写项目简介')
    return
  }
  submitting.value = true
  try {
    const template = {
      ...form,
      name: form.name.trim(),
      techTags: form.techTags?.trim(),
      description: form.description.trim(),
      detailContent: form.detailContent?.trim(),
      sourceUrl: form.sourceUrl?.trim(),
      demoUrl: form.demoUrl?.trim(),
      price: isAdmin.value ? 0 : undefined,
      sourceAttribution: isAdmin.value ? form.sourceAttribution?.trim() : undefined,
      licenseTerms: isAdmin.value ? form.licenseTerms?.trim() : undefined,
    }
    const res = isEdit.value
      ? await updateProject({ ...template, id: route.params.id as unknown as number })
      : await addProject(template)
    if (res.data.code === 0) {
      message.success(isEdit.value ? '修改成功' : '发布成功')
      router.push(isAdmin.value ? '/admin/projectManage' : '/my/projects')
    } else {
      message.error(res.data.message || '操作失败')
    }
  } catch {
    message.error('操作失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadCategories()
  loadProject()
})
</script>

<style scoped>
#projectEditPage {
  min-height: 60dvh;
  background: var(--color-bg);
}

.container {
  max-width: calc(var(--page-width) + var(--page-gutter) * 2);
  margin: 0 auto;
  padding: 32px var(--page-gutter) 64px;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: clamp(1.5rem, 1rem + 1.5vw, 2rem);
  font-weight: 700;
  color: var(--color-text);
  margin: 8px 0 0;
}

.form-card {
  border-radius: 12px;
  border: 1px solid var(--color-border);
}

.form-card :deep(.ant-card-body) {
  padding: 32px;
}

/* 编辑器 */
.editor-wrapper {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}

.editor-textarea {
  border: none !important;
  border-radius: 0;
  font-family: 'SF Mono', 'Monaco', 'Menlo', monospace;
  font-size: 14px;
}

.markdown-preview {
  padding: 16px;
  min-height: 300px;
  max-height: 500px;
  overflow-y: auto;
  line-height: 1.8;
}

.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3) {
  margin-top: 12px;
  margin-bottom: 8px;
}

.markdown-preview :deep(pre) {
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
}

.markdown-preview :deep(code) {
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  padding: 2px 4px;
  border-radius: 4px;
}

.markdown-preview :deep(img) {
  max-width: 100%;
  border-radius: 6px;
}

/* 侧边栏 */
.sidebar-form {
  padding-top: 4px;
}

/* 封面上传 */
.cover-upload {
  text-align: center;
}

.cover-placeholder {
  width: 100%;
  height: 180px;
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
  cursor: pointer;
  transition: all 0.3s;
  gap: 8px;
}

.cover-placeholder:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.cover-preview-wrapper {
  position: relative;
  width: 100%;
  height: 180px;
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;
}

.cover-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 12px;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.cover-preview-wrapper:hover .cover-mask {
  opacity: 1;
}

.upload-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 8px;
}

/* 截图列表 */
.screenshot-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.screenshot-item {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 6px;
  overflow: hidden;
}

.screenshot-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.remove-btn {
  position: absolute;
  top: -4px;
  right: -4px;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
}

.screenshot-add {
  width: 80px;
  height: 80px;
  border: 2px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
  cursor: pointer;
  font-size: 12px;
  gap: 4px;
  transition: all 0.3s;
}

.screenshot-add:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
</style>
