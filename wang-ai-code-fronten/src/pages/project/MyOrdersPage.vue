<template>
  <div id="myOrdersPage" class="workspace-page">
    <div class="container">
      <div class="page-header">
        <h1 class="page-title">领取记录</h1>
        <a-button size="large" @click="$router.push('/project')">
          <ShoppingOutlined /> 浏览模板
        </a-button>
      </div>

      <a-spin :spinning="loading">
        <template v-if="orders.length > 0">
          <a-table :data-source="orders" :pagination="false" :scroll="{ x: 900 }" row-key="id">
            <a-table-column title="模板或历史项目" key="project" :width="300">
              <template #default="{ record }">
                <a-space>
                  <a-image
                    :src="record.projectCover || defaultCover"
                    :width="48"
                    :height="48"
                    :preview="false"
                    style="border-radius: 6px; object-fit: cover"
                  />
                  <div>
                    <div class="project-name">{{ record.projectName || '项目已移除' }}</div>
                    <div class="order-id">记录编号 {{ record.id }}</div>
                  </div>
                </a-space>
              </template>
            </a-table-column>

            <a-table-column title="金额" :width="100" align="center">
              <template #default="{ record }">
                <span class="price">{{ record.amount ? `¥${record.amount}` : '免费' }}</span>
              </template>
            </a-table-column>

            <a-table-column title="状态" :width="100" align="center">
              <template #default="{ record }">
                <a-tag :color="getStatusColor(record.status)">
                  {{ record.status === 1 && !record.amount ? '已领取' : getStatusText(record.status) }}
                </a-tag>
              </template>
            </a-table-column>

            <a-table-column title="记录时间" data-index="createTime" :width="170" />

            <a-table-column title="操作" key="action" :width="200" align="center" fixed="right">
              <template #default="{ record }">
                <a-space v-if="record.downloadAvailable && record.id">
                  <a-button
                    type="primary"
                    size="small"
                    :href="getTemplateDownloadUrl(record.id)"
                    target="_blank"
                    rel="noopener"
                  >
                    <DownloadOutlined aria-hidden="true" /> 下载
                  </a-button>
                  <a-button size="small" @click="$router.push(`/project/${record.projectId}`)">
                    <EyeOutlined /> 查看
                  </a-button>
                </a-space>
                <span v-else>暂不可下载</span>
              </template>
            </a-table-column>
          </a-table>

          <div class="pagination-wrapper">
            <a-pagination
              v-model:current="query.pageNum"
              :page-size="query.pageSize"
              :total="total"
              :show-size-changer="false"
              :show-total="(t: number) => `共 ${t} 条记录`"
              @change="loadOrders"
            />
          </div>
        </template>

        <a-empty v-else description="还没有领取记录" class="empty-state">
          <a-button type="primary" @click="$router.push('/project')">
            <ShoppingOutlined /> 浏览模板
          </a-button>
        </a-empty>
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { DownloadOutlined, EyeOutlined, ShoppingOutlined } from '@ant-design/icons-vue'
import { listMyOrders, getTemplateDownloadUrl } from '@/api/projectOrderController.ts'

const defaultCover =
  'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDgiIGhlaWdodD0iNDgiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjQ4IiBoZWlnaHQ9IjQ4IiBmaWxsPSIjZjBmMmY1Ii8+PHRleHQgeD0iMjQiIHk9IjI3IiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmaWxsPSIjYmZiZmMwIiBmb250LXNpemU9IjEwIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+Tm9uZTwvdGV4dD48L3N2Zz4='

const loading = ref(false)
const orders = ref<API.ProjectOrderVO[]>([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
})

const statusMap: Record<number, { text: string; color: string }> = {
  0: { text: '待支付', color: 'orange' },
  1: { text: '已支付', color: 'blue' },
  2: { text: '已退款', color: 'red' },
}

const getStatusText = (status?: number) => {
  return statusMap[status ?? 0]?.text || '未知'
}

const getStatusColor = (status?: number) => {
  return statusMap[status ?? 0]?.color || 'default'
}

const loadOrders = async () => {
  loading.value = true
  try {
    const res = await listMyOrders(query)
    if (res.data.code === 0 && res.data.data) {
      orders.value = res.data.data.records || []
      total.value = res.data.data.totalRow || 0
    }
  } catch {
    message.error('加载领取记录失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadOrders()
})
</script>

<style scoped>
.text-muted,
.order-id {
  color: var(--color-text-tertiary);
  font-size: 12px;
}
.project-name {
  font-weight: 600;
  font-size: 14px;
}
.price {
  color: var(--color-primary-strong);
  font-weight: 650;
}
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
:deep(.ant-table) {
  border: 1px solid var(--color-border);
  border-radius: 12px;
}
:deep(.ant-table-thead > tr > th) {
  background: var(--color-surface-soft);
  font-weight: 600;
}
</style>
