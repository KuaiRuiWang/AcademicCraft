<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { message, type TableColumnsType } from 'ant-design-vue'
import {
  deleteProject,
  listProjectVoByPageByAdmin,
  updateProjectByAdmin,
} from '@/api/operations'
import { useAdminTable } from '@/composables/useAdminTable'
import { formatDate } from '@/utils/time'
import PageHeading from '@/components/PageHeading.vue'
import AdminRecords from '@/components/AdminRecords.vue'
import { can } from '@/session'
import { workshopUrl } from '@/config/env'

const router = useRouter()
const filters = reactive<API.ProjectQueryRequest>({})
const { rows, loading, error, pagination, load, search, changePage } = useAdminTable((page) =>
  listProjectVoByPageByAdmin({ ...filters, ...page }),
)

const columns: TableColumnsType<API.ProjectVO> = [
  { title: '模板名称', key: 'template' },
  { title: '状态与许可', key: 'status', width: '22%' },
  { title: '领取', key: 'activity', responsive: ['md'], width: '16%' },
  { title: '操作', key: 'action', width: '24%' },
]
const statuses = [
  { value: 0, label: '草稿' },
  { value: 1, label: '已上架' },
  { value: 2, label: '已下架' },
]

async function toggleStatus(project: API.ProjectVO) {
  if (!project.id) return
  const status = project.status === 1 ? 2 : 1
  try {
    const res = await updateProjectByAdmin({ id: project.id, status })
    if (res.data.code !== 0) throw new Error(res.data.message || '模板状态更新失败')
    message.success(status === 1 ? '模板已上架' : '模板已下架')
    await load()
  } catch (failure) {
    message.error(failure instanceof Error ? failure.message : '模板状态更新失败')
  }
}

async function removeProject(id?: number) {
  if (!id) return
  try {
    const res = await deleteProject({ id })
    if (res.data.code !== 0) throw new Error(res.data.message || '模板删除失败')
    message.success('模板已删除')
    await load()
  } catch (failure) {
    message.error(failure instanceof Error ? failure.message : '模板删除失败')
  }
}
</script>

<template>
  <div>
    <PageHeading title="模板管理" description="维护模板资料、使用许可和上架状态。">
      <a-button v-if="can('TEMPLATE_WRITE')" type="primary" @click="router.push('/templates/create')"
        ><PlusOutlined aria-hidden="true" /> 发布模板</a-button
      >
    </PageHeading>
    <a-form
      class="admin-filters"
      layout="inline"
      :model="filters"
      name="admin-templates"
      @finish="search"
    >
      <a-form-item label="模板名称" name="name">
        <a-input v-model:value="filters.name" placeholder="搜索模板" allow-clear />
      </a-form-item>
      <a-form-item label="上架状态" name="status">
        <a-select
          v-model:value="filters.status"
          placeholder="全部"
          :options="statuses"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="作者 ID" name="authorId"
        ><a-input v-model:value="filters.authorId" allow-clear
      /></a-form-item>
      <a-form-item
        ><a-button type="primary" html-type="submit"
          ><SearchOutlined aria-hidden="true" /> 搜索</a-button
        ></a-form-item
      >
    </a-form>
    <AdminRecords label="模板记录" :error="error" @retry="load">
      <a-table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        row-key="id"
        table-layout="fixed"
      >
        <template #emptyText>没有符合条件的模板</template>
        <template #bodyCell="{ column, record }">
          <div v-if="column.key === 'template'" class="record-identity">
            <a-image
              v-if="record.coverImage"
              :src="record.coverImage"
              :width="44"
              :height="34"
              alt="模板封面"
            />
            <div>
              <a v-if="record.status === 1" :href="workshopUrl('/project/' + record.id)" target="_blank" rel="noopener noreferrer">{{
                record.name
              }}</a>
              <strong v-else class="record-primary">{{ record.name }}</strong>
              <span class="record-note"
                >{{ record.categoryName || '未分类' }} ·
                {{ record.author?.userName || '匿名' }}</span
              >
              <span v-if="record.techTags" class="record-note">{{ record.techTags }}</span>
              <span class="record-note">{{ record.id }}</span>
              <span class="record-note record-compact">领取 {{ record.salesCount || 0 }} 次</span>
              <time v-if="record.createTime" class="record-note record-compact">
                创建于 {{ formatDate(record.createTime) }}
              </time>
            </div>
          </div>
          <div v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'success' : undefined">{{
              statuses[record.status ?? 0]?.label || '未知'
            }}</a-tag>
            <span class="record-note">{{
              record.sourceAttribution && record.licenseTerms ? '许可已登记' : '许可待登记'
            }}</span>
          </div>
          <div v-else-if="column.key === 'activity'">
            {{ record.salesCount || 0 }} 次
            <span class="record-note">创建于 {{ formatDate(record.createTime) }}</span>
          </div>
          <div v-else-if="column.key === 'action' && can('TEMPLATE_WRITE')" class="record-actions">
            <RouterLink class="folio-link" :to="'/templates/' + record.id + '/edit'"
              >编辑模板</RouterLink
            >
            <a-button type="link" @click="toggleStatus(record)">{{
              record.status === 1 ? '下架' : '上架'
            }}</a-button>
            <a-popconfirm
              title="删除该模板？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="removeProject(record.id)"
            >
              <a-button type="link" danger>删除</a-button>
            </a-popconfirm>
          </div>
        </template>
      </a-table>
    </AdminRecords>
    <div v-if="pagination.total && !error" class="pagination-wrapper">
      <a-pagination v-bind="pagination" @change="changePage" />
    </div>
  </div>
</template>
