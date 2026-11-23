<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { PlusOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { message, type TableColumnsType, type UploadProps } from 'ant-design-vue'
import {
  addCategory,
  deleteCategory,
  listCategories,
  updateCategory,
} from '@/api/projectCategoryController'
import { upload } from '@/api/commonController'
import { formatDate } from '@/utils/time'
import PageHeading from '@/components/PageHeading.vue'
import AdminRecords from '@/components/AdminRecords.vue'

const columns: TableColumnsType<API.ProjectCategory> = [
  { title: '分类名称', key: 'category' },
  { title: '排序权重', dataIndex: 'sortOrder', width: '22%' },
  { title: '创建时间', key: 'created', responsive: ['md'], width: '20%' },
  { title: '操作', key: 'action', width: '26%' },
]

const rows = ref<API.ProjectCategory[]>([])
const loading = ref(true)
const loadError = ref('')
const search = ref('')
const visibleRows = computed(() =>
  rows.value.filter((category) =>
    category.name?.toLowerCase().includes(search.value.trim().toLowerCase()),
  ),
)

const modalOpen = ref(false)
const modalLoading = ref(false)
const uploading = ref(false)
const saveError = ref('')
const editId = ref<number>()
const form = reactive({ name: '', icon: '', sortOrder: 0 })

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listCategories()
    if (res.data.code !== 0) throw new Error(res.data.message || '分类加载失败')
    rows.value = res.data.data || []
  } catch (failure) {
    loadError.value = failure instanceof Error ? failure.message : '分类加载失败'
  } finally {
    loading.value = false
  }
}

function openEditor(record?: API.ProjectCategory) {
  editId.value = record?.id
  form.name = record?.name || ''
  form.icon = record?.icon || ''
  form.sortOrder = record?.sortOrder || 0
  saveError.value = ''
  modalOpen.value = true
}

const beforeIconUpload: UploadProps['beforeUpload'] = (file) => {
  if (!file.type?.startsWith('image/') || file.size > 5 * 1024 * 1024) {
    message.error('请选择 5 MB 以内的图片')
    return false
  }
  return true
}

const uploadIcon: UploadProps['customRequest'] = async ({ file, onSuccess, onError }) => {
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', file as File)
    const res = await upload(data)
    if (res.data.code !== 0 || !res.data.data) throw new Error(res.data.message || '图标上传失败')
    form.icon = res.data.data
    onSuccess?.(res.data)
  } catch (failure) {
    const error = failure instanceof Error ? failure : new Error('图标上传失败')
    onError?.(error)
    message.error(error.message)
  } finally {
    uploading.value = false
  }
}

async function save() {
  if (uploading.value || modalLoading.value) return
  const name = form.name.trim()
  if (!name) {
    saveError.value = '请输入分类名称'
    return
  }

  modalLoading.value = true
  saveError.value = ''
  try {
    const category = { ...form, name, sortOrder: form.sortOrder ?? 0 }
    const res = editId.value
      ? await updateCategory({ ...category, id: editId.value })
      : await addCategory(category)
    if (res.data.code !== 0) throw new Error(res.data.message || '分类保存失败')
    message.success('分类已保存')
    modalOpen.value = false
    await load()
  } catch (failure) {
    saveError.value = failure instanceof Error ? failure.message : '分类保存失败'
  } finally {
    modalLoading.value = false
  }
}

async function removeCategory(id?: number) {
  if (!id) return
  try {
    const res = await deleteCategory({ id })
    if (res.data.code !== 0) throw new Error(res.data.message || '分类删除失败')
    message.success('分类已删除')
    await load()
  } catch (failure) {
    message.error(failure instanceof Error ? failure.message : '分类删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeading title="分类管理" description="整理模板目录中的分类与显示顺序。">
      <a-button type="primary" @click="openEditor()"
        ><PlusOutlined aria-hidden="true" /> 新增分类</a-button
      >
    </PageHeading>
    <div class="admin-filters">
      <a-input
        v-model:value="search"
        aria-label="搜索分类"
        placeholder="搜索分类"
        allow-clear
        class="category-search"
      />
    </div>
    <AdminRecords label="分类记录" :error="loadError" @retry="load">
      <a-table
        :columns="columns"
        :data-source="visibleRows"
        :loading="loading"
        :pagination="false"
        row-key="id"
        table-layout="fixed"
      >
        <template #emptyText>没有符合条件的分类</template>
        <template #bodyCell="{ column, record }">
          <div v-if="column.key === 'category'" class="record-identity">
            <a-image
              v-if="record.icon"
              :src="record.icon"
              :width="32"
              :height="32"
              alt="分类图标"
            />
            <div>
              <strong class="record-primary">{{ record.name }}</strong>
              <span class="record-note">{{ record.id }}</span>
              <time v-if="record.createTime" class="record-note record-compact">
                创建于 {{ formatDate(record.createTime) }}
              </time>
            </div>
          </div>
          <span v-else-if="column.key === 'created'">{{ formatDate(record.createTime) }}</span>
          <div v-else-if="column.key === 'action'" class="record-actions">
            <a-button type="link" @click="openEditor(record)">编辑分类</a-button>
            <a-popconfirm
              title="删除该分类？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="removeCategory(record.id)"
            >
              <a-button danger type="link">删除</a-button>
            </a-popconfirm>
          </div>
        </template>
      </a-table>
    </AdminRecords>
    <a-modal
      v-model:open="modalOpen"
      :title="editId ? '编辑分类' : '新增分类'"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="modalLoading"
      :ok-button-props="{ disabled: uploading }"
      :closable="!uploading"
      :mask-closable="!uploading"
      :keyboard="!uploading"
      :cancel-button-props="{ disabled: uploading }"
      @ok="save"
    >
      <a-form layout="vertical" :model="form" name="category-editor" @finish="save">
        <a-form-item label="分类名称" name="name" required>
          <a-input v-model:value="form.name" :maxlength="50" placeholder="输入分类名称" />
        </a-form-item>
        <a-form-item label="排序权重" name="sortOrder" extra="数值越小，排序越靠前。">
          <a-input-number v-model:value="form.sortOrder" :min="0" :precision="0" />
        </a-form-item>
        <a-form-item label="分类图标" extra="支持 5 MB 以内的图片。">
          <div class="folio-actions">
            <a-image v-if="form.icon" :src="form.icon" alt="分类图标" :width="42" :height="42" />
            <a-upload
              :show-upload-list="false"
              :before-upload="beforeIconUpload"
              :custom-request="uploadIcon"
              :disabled="uploading"
              accept="image/*"
            >
              <a-button :loading="uploading"
                ><UploadOutlined aria-hidden="true" /> 上传图标</a-button
              >
            </a-upload>
          </div>
        </a-form-item>
        <a-alert v-if="saveError" type="error" :message="saveError" show-icon />
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.category-search {
  width: min(100%, 260px);
}
</style>
