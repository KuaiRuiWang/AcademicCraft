<script setup lang="ts">
import { reactive } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { message, type TableColumnsType } from 'ant-design-vue'
import { listAppVoByPageByAdmin, deleteAppByAdmin, updateAppByAdmin } from '@/api/appController'
import { useAdminTable } from '@/composables/useAdminTable'
import { CODE_GEN_TYPE_OPTIONS, formatCodeGenType } from '@/utils/codeGenTypes'
import { formatDate } from '@/utils/time'
import PageHeading from '@/components/PageHeading.vue'
import AdminRecords from '@/components/AdminRecords.vue'

const filters = reactive<API.AppQueryRequest>({})
const { rows, loading, error, pagination, load, search, changePage } = useAdminTable((page) =>
  listAppVoByPageByAdmin({ ...filters, ...page }),
)

const columns: TableColumnsType<API.AppVO> = [
  { title: '应用名称', key: 'app' },
  { title: '构建方式', key: 'build', width: '21%' },
  { title: '最近更新', key: 'activity', responsive: ['md'], width: '19%' },
  { title: '操作', key: 'action', width: '24%' },
]

async function toggleFeatured(app: API.AppVO) {
  if (!app.id) return
  const priority = app.priority === 99 ? 0 : 99
  try {
    const res = await updateAppByAdmin({ id: app.id, priority })
    if (res.data.code !== 0) throw new Error(res.data.message || '精选状态更新失败')
    message.success(priority === 99 ? '已设为精选' : '已取消精选')
    await load()
  } catch (failure) {
    message.error(failure instanceof Error ? failure.message : '操作失败')
  }
}

async function removeApp(id?: number) {
  if (!id) return
  try {
    const res = await deleteAppByAdmin({ id })
    if (res.data.code !== 0) throw new Error(res.data.message || '删除应用失败')
    message.success('应用已删除')
    await load()
  } catch (failure) {
    message.error(failure instanceof Error ? failure.message : '删除应用失败')
  }
}
</script>

<template>
  <div>
    <PageHeading title="应用管理" description="编辑应用资料，选择在首页展示的精选案例。" />
    <a-form
      class="admin-filters"
      layout="inline"
      :model="filters"
      name="admin-apps"
      @finish="search"
    >
      <a-form-item label="应用名称" name="appName">
        <a-input v-model:value="filters.appName" placeholder="搜索应用" allow-clear />
      </a-form-item>
      <a-form-item label="创建者" name="userId">
        <a-input v-model:value="filters.userId" placeholder="用户 ID" allow-clear />
      </a-form-item>
      <a-form-item label="构建方式" name="codeGenType">
        <a-select
          v-model:value="filters.codeGenType"
          placeholder="全部"
          allow-clear
          :options="CODE_GEN_TYPE_OPTIONS"
        />
      </a-form-item>
      <a-form-item
        ><a-button type="primary" html-type="submit"
          ><SearchOutlined aria-hidden="true" /> 搜索</a-button
        ></a-form-item
      >
    </a-form>
    <AdminRecords label="应用记录" :error="error" @retry="load">
      <a-table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        row-key="id"
        table-layout="fixed"
      >
        <template #emptyText>没有符合条件的应用</template>
        <template #bodyCell="{ column, record }">
          <div v-if="column.key === 'app'" class="record-identity">
            <a-image
              v-if="record.cover"
              :src="record.cover"
              :width="44"
              :height="34"
              alt="应用封面"
            />
            <div>
              <strong class="record-primary">{{ record.appName || '未命名应用' }}</strong>
              <span class="record-note"
                >{{ record.id }} · {{ record.user?.userName || '未知创建者' }}</span
              >
              <time
                v-if="record.updateTime || record.createTime"
                class="record-note record-compact"
              >
                更新于 {{ formatDate(record.updateTime || record.createTime) }}
              </time>
              <span class="record-note record-compact">
                {{ record.deployedTime ? '部署于 ' + formatDate(record.deployedTime) : '未部署' }}
              </span>
            </div>
          </div>
          <div v-else-if="column.key === 'build'">
            {{ formatCodeGenType(record.codeGenType) }}
            <span class="record-note">{{
              record.priority === 99 ? '首页精选' : '优先级 ' + (record.priority || 0)
            }}</span>
          </div>
          <div v-else-if="column.key === 'activity'">
            {{ formatDate(record.updateTime || record.createTime) }}
            <span class="record-note">{{
              record.deployedTime ? '部署于 ' + formatDate(record.deployedTime) : '未部署'
            }}</span>
          </div>
          <div v-else-if="column.key === 'action'" class="record-actions">
            <RouterLink class="folio-link" :to="'/app/edit/' + record.id">编辑应用</RouterLink>
            <a-button type="link" @click="toggleFeatured(record)">{{
              record.priority === 99 ? '取消精选' : '设为精选'
            }}</a-button>
            <a-popconfirm
              title="删除该应用？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="removeApp(record.id)"
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
