<template>
  <div id="categoryManagePage">
    <!-- 头部操作栏 -->
    <div class="page-toolbar">
      <a-button type="primary" @click="openAddModal">
        <template #icon><PlusOutlined /></template>
        新增分类
      </a-button>
    </div>

    <!-- 分类列表表格 -->
    <a-table
      :columns="columns"
      :data-source="data"
      :pagination="false"
      row-key="id"
    >
      <template #bodyCell="{ column, record, text }">
        <template v-if="column.dataIndex === 'icon'">
          <a-image
            v-if="record.icon"
            :src="record.icon"
            :width="40"
            :height="40"
            style="border-radius: 4px; object-fit: cover"
          />
          <div v-else class="no-icon">-</div>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="primary" size="small" @click="openEditModal(record)">
              <template #icon><EditOutlined /></template>
              编辑
            </a-button>
            <a-popconfirm title="确定要删除这个分类吗？" @confirm="handleDelete(record.id)">
              <a-button danger size="small">
                <template #icon><DeleteOutlined /></template>
                删除
              </a-button>
            </a-popconfirm>
          </a-space>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ formatTime(text) }}
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      :title="isEdit ? '编辑分类' : '新增分类'"
      :confirm-loading="modalLoading"
      @ok="handleSubmit"
      @cancel="resetForm"
      destroyOnClose
    >
      <a-form layout="vertical" :model="formState">
        <a-form-item label="分类名称" required>
          <a-input v-model:value="formState.name" placeholder="输入分类名称" />
        </a-form-item>

        <a-form-item label="分类图标">
          <div class="icon-upload-area">
            <a-upload
              :show-upload-list="false"
              :before-upload="beforeIconUpload"
              :custom-request="customIconUpload"
            >
              <div class="icon-upload-trigger">
                <a-image
                  v-if="formIconPreview"
                  :src="formIconPreview"
                  :width="80"
                  :height="80"
                  style="border-radius: 4px; object-fit: cover"
                />
                <div v-else class="icon-upload-placeholder">
                  <PlusOutlined />
                  <span>上传图标</span>
                </div>
              </div>
            </a-upload>
            <a-typography-text type="secondary" class="upload-hint">
              支持 JPG/PNG，不超过 5MB
            </a-typography-text>
          </div>
        </a-form-item>

        <a-form-item label="排序权重">
          <a-input-number
            v-model:value="formState.sortOrder"
            :min="0"
            placeholder="越大越靠前"
            style="width: 100%"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { UploadProps } from 'ant-design-vue'
import {
  listCategories,
  addCategory,
  updateCategory,
  deleteCategory,
} from '@/api/projectCategoryController'
import { upload } from '@/api/commonController'
import { formatTime } from '@/utils/time'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
} from '@ant-design/icons-vue'

const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    width: 80,
  },
  {
    title: '图标',
    dataIndex: 'icon',
    width: 80,
  },
  {
    title: '分类名称',
    dataIndex: 'name',
    width: 200,
  },
  {
    title: '排序权重',
    dataIndex: 'sortOrder',
    width: 100,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 180,
  },
  {
    title: '操作',
    key: 'action',
    width: 180,
  },
]

// 数据
const data = ref<API.ProjectCategory[]>([])

// 弹窗状态
const modalOpen = ref(false)
const modalLoading = ref(false)
const isEdit = ref(false)
const editId = ref<number | undefined>(undefined)
const formIconPreview = ref<string>('')

// 表单
const formState = reactive<API.ProjectCategory>({
  name: '',
  icon: '',
  sortOrder: 0,
})

// 获取分类列表
const fetchData = async () => {
  try {
    const res = await listCategories()
    if (res.data.data) {
      data.value = res.data.data
    } else {
      message.error('获取分类列表失败，' + res.data.message)
    }
  } catch (error) {
    console.error('获取分类列表失败：', error)
    message.error('获取分类列表失败')
  }
}

onMounted(() => {
  fetchData()
})

// 打开新增弹窗
const openAddModal = () => {
  isEdit.value = false
  editId.value = undefined
  formState.name = ''
  formState.icon = ''
  formState.sortOrder = 0
  formIconPreview.value = ''
  modalOpen.value = true
}

// 打开编辑弹窗
const openEditModal = (record: API.ProjectCategory) => {
  isEdit.value = true
  editId.value = record.id
  formState.name = record.name ?? ''
  formState.icon = record.icon ?? ''
  formState.sortOrder = record.sortOrder ?? 0
  formIconPreview.value = record.icon ?? ''
  modalOpen.value = true
}

// 重置表单
const resetForm = () => {
  formIconPreview.value = ''
}

// 上传前校验
const beforeIconUpload: UploadProps['beforeUpload'] = (file) => {
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

// 自定义图标上传
const customIconUpload: UploadProps['customRequest'] = async (options) => {
  const { file, onSuccess, onError } = options as any
  try {
    // 本地预览
    const reader = new FileReader()
    reader.onload = () => {
      formIconPreview.value = reader.result as string
    }
    reader.readAsDataURL(file as File)

    // 上传服务端
    const formData = new FormData()
    formData.append('file', file as File)
    const res = await upload(formData)
    if (res.data.code === 0 && res.data.data) {
      formState.icon = res.data.data
      message.success('图标上传成功')
      onSuccess?.(res.data, file)
    } else {
      message.error('图标上传失败，' + res.data.message)
      formIconPreview.value = formState.icon || ''
      onError?.(new Error(res.data.message ?? '上传失败'))
    }
  } catch {
    message.error('图标上传失败，请稍后重试')
    formIconPreview.value = formState.icon || ''
    onError?.(new Error('上传失败'))
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formState.name?.trim()) {
    message.warning('请输入分类名称')
    return
  }

  modalLoading.value = true
  try {
    if (isEdit.value && editId.value) {
      // 编辑
      const res = await updateCategory({
        id: editId.value,
        name: formState.name,
        icon: formState.icon,
        sortOrder: formState.sortOrder ?? 0,
      })
      if (res.data.code === 0) {
        message.success('更新成功')
        modalOpen.value = false
        fetchData()
      } else {
        message.error('更新失败：' + res.data.message)
      }
    } else {
      // 新增
      const res = await addCategory({
        name: formState.name,
        icon: formState.icon,
        sortOrder: formState.sortOrder ?? 0,
      })
      if (res.data.code === 0) {
        message.success('添加成功')
        modalOpen.value = false
        fetchData()
      } else {
        message.error('添加失败：' + res.data.message)
      }
    }
  } catch (error) {
    console.error('操作失败：', error)
    message.error('操作失败')
  } finally {
    modalLoading.value = false
  }
}

// 删除分类
const handleDelete = async (id: number | undefined) => {
  if (!id) return

  try {
    const res = await deleteCategory({ id })
    if (res.data.code === 0) {
      message.success('删除成功')
      fetchData()
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}
</script>

<style scoped>
.page-toolbar {
  margin-bottom: 16px;
}

.no-icon {
  width: 40px;
  height: 40px;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 12px;
  border-radius: 4px;
}

.icon-upload-area {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.icon-upload-trigger {
  cursor: pointer;
}

.icon-upload-placeholder {
  width: 80px;
  height: 80px;
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #999;
  font-size: 12px;
  transition: border-color 0.3s;
}

.icon-upload-placeholder:hover {
  border-color: #1890ff;
}

.upload-hint {
  font-size: 12px;
}

:deep(.ant-table-tbody > tr > td) {
  vertical-align: middle;
}
</style>
