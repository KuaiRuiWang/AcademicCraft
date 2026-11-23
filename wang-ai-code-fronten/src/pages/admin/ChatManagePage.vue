<script setup lang="ts">
import { reactive } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { listAllChatHistoryByPageForAdmin } from '@/api/chatHistoryController'
import { useAdminTable } from '@/composables/useAdminTable'
import { formatTime } from '@/utils/time'
import PageHeading from '@/components/PageHeading.vue'
import AdminRecords from '@/components/AdminRecords.vue'

const filters = reactive<API.ChatHistoryQueryRequest>({})
const { rows, loading, error, pagination, load, search, changePage } = useAdminTable((page) =>
  listAllChatHistoryByPageForAdmin({ ...filters, ...page }),
)

const columns: TableColumnsType<API.ChatHistory> = [
  { title: '对话内容', key: 'message' },
  { title: '发言者', key: 'role', width: '22%' },
  { title: '发送时间', key: 'sent', responsive: ['md'], width: '20%' },
  { title: '操作', key: 'action', width: '25%' },
]

const messageTypes = [
  { value: 'user', label: '用户消息' },
  { value: 'ai', label: '助手消息' },
]
</script>

<template>
  <div>
    <PageHeading title="对话管理" description="按应用、用户或消息内容查看创作记录。" />
    <a-form
      class="admin-filters"
      layout="inline"
      :model="filters"
      name="admin-chat"
      @finish="search"
    >
      <a-form-item label="消息内容" name="message">
        <a-input v-model:value="filters.message" placeholder="搜索消息" allow-clear />
      </a-form-item>
      <a-form-item label="消息类型" name="messageType">
        <a-select
          v-model:value="filters.messageType"
          placeholder="全部"
          :options="messageTypes"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="应用 ID" name="appId"
        ><a-input v-model:value="filters.appId" allow-clear
      /></a-form-item>
      <a-form-item label="用户 ID" name="userId"
        ><a-input v-model:value="filters.userId" allow-clear
      /></a-form-item>
      <a-form-item
        ><a-button type="primary" html-type="submit"
          ><SearchOutlined aria-hidden="true" /> 搜索</a-button
        ></a-form-item
      >
    </a-form>
    <AdminRecords label="对话记录" :error="error" @retry="load">
      <a-table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        row-key="id"
        table-layout="fixed"
      >
        <template #emptyText>没有符合条件的对话</template>
        <template #bodyCell="{ column, record }">
          <div v-if="column.key === 'message'">
            <details v-if="record.message && record.message.length > 100" class="message-details">
              <summary>展开消息</summary>
              <p>{{ record.message }}</p>
            </details>
            <span v-else>{{ record.message || '空消息' }}</span>
            <span class="record-note">应用 {{ record.appId }}</span>
            <time v-if="record.createTime" class="record-note record-compact">
              {{ formatTime(record.createTime) }}
            </time>
          </div>
          <div v-else-if="column.key === 'role'">
            {{ record.messageType === 'user' ? '创作者' : '工坊助手' }}
            <span class="record-note">{{ record.userId }}</span>
          </div>
          <span v-else-if="column.key === 'sent'">{{ formatTime(record.createTime) }}</span>
          <RouterLink
            v-else-if="column.key === 'action' && record.appId"
            class="folio-link"
            :to="'/app/chat/' + record.appId"
            >打开对话</RouterLink
          >
        </template>
      </a-table>
    </AdminRecords>
    <div v-if="pagination.total && !error" class="pagination-wrapper">
      <a-pagination v-bind="pagination" @change="changePage" />
    </div>
  </div>
</template>

<style scoped>
.message-details summary {
  color: var(--color-primary);
  cursor: pointer;
}

.message-details p {
  white-space: pre-wrap;
  margin-block: 8px;
}
</style>
