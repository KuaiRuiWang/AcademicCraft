<script setup lang="ts">
import { computed, reactive } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { message, type TableColumnsType } from 'ant-design-vue'
import { deleteUser, listUserVoByPage } from '@/api/operations'
import { useAdminTable } from '@/composables/useAdminTable'
import { can } from '@/session'
import { formatDate } from '@/utils/time'
import AdminRecords from '@/components/AdminRecords.vue'
import PageHeading from '@/components/PageHeading.vue'

const filters = reactive({ userAccount: '', userName: '' })
const { rows, loading, error, pagination, load, search, changePage } = useAdminTable((page) =>
  listUserVoByPage({ ...filters, ...page }),
)

const columns = computed<TableColumnsType<API.UserVO>>(() => [
  { title: '用户', key: 'identity' },
  { title: '角色', key: 'role', width: '16%' },
  { title: '注册时间', key: 'joined', responsive: ['md'], width: '23%' },
  ...(can('USER_WRITE') ? [{ title: '操作', key: 'action', width: 86, align: 'right' as const }] : []),
])

async function removeUser(id?: number) {
  if (!id) return
  try {
    const res = await deleteUser({ id })
    if (res.data.code !== 0) throw new Error(res.data.message || '删除用户失败')
    message.success('用户已删除')
    await load()
  } catch (failure) {
    message.error(failure instanceof Error ? failure.message : '删除用户失败')
  }
}
</script>

<template>
  <div>
    <PageHeading title="用户管理" />
    <AdminRecords label="用户记录" :error="error" @retry="load">
      <template #filters>
        <a-form
          class="admin-filters"
          layout="vertical"
          size="large"
          :model="filters"
          name="admin-users"
          @finish="search"
        >
          <a-form-item label="账号" name="userAccount">
            <a-input
              v-model:value="filters.userAccount"
              placeholder="输入账号"
              autocomplete="off"
              enterkeyhint="search"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="用户名" name="userName">
            <a-input
              v-model:value="filters.userName"
              placeholder="输入用户名"
              autocomplete="off"
              enterkeyhint="search"
              allow-clear
            />
          </a-form-item>
          <a-button type="primary" size="large" html-type="submit" :loading="loading">
            <SearchOutlined aria-hidden="true" /> 搜索
          </a-button>
        </a-form>
      </template>
      <a-table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        row-key="id"
        table-layout="fixed"
      >
        <template #emptyText>没有符合条件的用户</template>
        <template #bodyCell="{ column, record }">
          <div v-if="column.key === 'identity'" class="record-identity">
            <a-avatar :src="record.userAvatar" :size="32" aria-hidden="true">
              {{ (record.userName || record.userAccount || '用').charAt(0).toUpperCase() }}
            </a-avatar>
            <div>
              <strong class="record-primary">{{ record.userName || record.userAccount || '未命名用户' }}</strong>
              <span class="record-note">
                <template v-if="record.userName && record.userAccount">{{ record.userAccount }} · </template>
                {{ record.id }}
              </span>
              <span v-if="record.userProfile" class="record-note">{{ record.userProfile }}</span>
              <time v-if="record.createTime" class="record-note record-compact">
                注册于 {{ formatDate(record.createTime) }}
              </time>
            </div>
          </div>
          <span v-else-if="column.key === 'role'" class="muted">用户</span>
          <span v-else-if="column.key === 'joined'">{{ formatDate(record.createTime) }}</span>
          <template v-else-if="column.key === 'action'">
            <a-popconfirm
              title="删除该用户？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="removeUser(record.id)"
            >
              <a-button type="text" danger>删除</a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
      <template v-if="pagination.total" #pagination>
        <a-pagination v-bind="pagination" @change="changePage" />
      </template>
    </AdminRecords>
  </div>
</template>
