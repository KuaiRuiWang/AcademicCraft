<script setup lang="ts">
import { reactive } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { message, type TableColumnsType } from 'ant-design-vue'
import { deleteUser, listUserVoByPage } from '@/api/userController'
import { useAdminTable } from '@/composables/useAdminTable'
import { useLoginUserStore } from '@/stores/loginUser'
import { formatDate } from '@/utils/time'
import AdminRecords from '@/components/AdminRecords.vue'
import PageHeading from '@/components/PageHeading.vue'

const loginUserStore = useLoginUserStore()
const filters = reactive({ userAccount: '', userName: '' })
const { rows, loading, error, pagination, load, search, changePage } = useAdminTable((page) =>
  listUserVoByPage({ ...filters, ...page }),
)

const columns: TableColumnsType<API.UserVO> = [
  { title: '用户', key: 'identity' },
  { title: '角色', key: 'role', width: '21%' },
  { title: '注册时间', key: 'joined', responsive: ['md'], width: '22%' },
  { title: '操作', key: 'action', width: '18%' },
]

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
    <PageHeading title="用户管理" description="按账号或用户名查找工坊成员。" />
    <a-form
      class="admin-filters"
      layout="inline"
      :model="filters"
      name="admin-users"
      @finish="search"
    >
      <a-form-item label="账号" name="userAccount">
        <a-input v-model:value="filters.userAccount" placeholder="搜索账号" allow-clear />
      </a-form-item>
      <a-form-item label="用户名" name="userName">
        <a-input v-model:value="filters.userName" placeholder="搜索用户名" allow-clear />
      </a-form-item>
      <a-form-item
        ><a-button type="primary" html-type="submit"
          ><SearchOutlined aria-hidden="true" /> 搜索</a-button
        ></a-form-item
      >
    </a-form>
    <AdminRecords label="用户记录" :error="error" @retry="load">
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
            <a-avatar :src="record.userAvatar" :size="32">{{
              record.userName?.charAt(0)
            }}</a-avatar>
            <div>
              <strong class="record-primary">{{ record.userName || '未命名用户' }}</strong>
              <span class="record-note">{{ record.userAccount }} · {{ record.id }}</span>
              <span v-if="record.userProfile" class="record-note">{{ record.userProfile }}</span>
              <time v-if="record.createTime" class="record-note record-compact">
                注册于 {{ formatDate(record.createTime) }}
              </time>
            </div>
          </div>
          <a-tag
            v-else-if="column.key === 'role'"
            :color="record.userRole === 'admin' ? 'processing' : undefined"
          >
            {{ record.userRole === 'admin' ? '管理员' : '用户' }}
          </a-tag>
          <span v-else-if="column.key === 'joined'">{{ formatDate(record.createTime) }}</span>
          <template v-else-if="column.key === 'action'">
            <a-popconfirm
              v-if="record.id !== loginUserStore.loginUser.id"
              title="删除该用户？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="removeUser(record.id)"
            >
              <a-button type="link" danger>删除</a-button>
            </a-popconfirm>
            <span v-else class="record-note">当前账号</span>
          </template>
        </template>
      </a-table>
    </AdminRecords>
    <div v-if="pagination.total && !error" class="pagination-wrapper">
      <a-pagination v-bind="pagination" @change="changePage" />
    </div>
  </div>
</template>
