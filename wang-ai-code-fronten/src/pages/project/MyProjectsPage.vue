<template>
  <div id="myProjectsPage">
    <div class="container">
      <div class="page-header">
        <h1 class="page-title">我的项目</h1>
        <a-button
          v-if="canPublishProject"
          type="primary"
          size="large"
          @click="$router.push('/project/create')"
        >
          <PlusOutlined /> 发布新项目
        </a-button>
      </div>

      <a-spin :spinning="loading">
        <template v-if="projects.length > 0">
          <a-table
            :data-source="projects"
            :pagination="pagination"
            row-key="id"
            @change="handleTableChange"
          >
            <a-table-column title="项目" key="name" :width="300">
              <template #default="{ record }">
                <a-space>
                  <a-image
                    :src="record.coverImage || defaultCover"
                    :width="60"
                    :height="40"
                    :preview="false"
                    style="border-radius: 4px; object-fit: cover"
                  />
                  <div>
                    <a-typography-link @click="$router.push(`/project/${record.id}`)">
                      {{ record.name }}
                    </a-typography-link>
                    <div style="font-size: 12px; color: #999">#{{ record.id }}</div>
                  </div>
                </a-space>
              </template>
            </a-table-column>

            <a-table-column title="分类" data-index="categoryName" :width="100">
              <template #default="{ record }">
                <a-tag>{{ record.categoryName || '-' }}</a-tag>
              </template>
            </a-table-column>

            <a-table-column title="价格" data-index="price" :width="100" align="center">
              <template #default="{ record }">
                <span style="color: #ff4d4f; font-weight: 600">
                  {{ record.price ? `¥${record.price}` : '免费' }}
                </span>
              </template>
            </a-table-column>

            <a-table-column title="销量" data-index="salesCount" :width="80" align="center" />

            <a-table-column title="评分" :width="100" align="center">
              <template #default="{ record }">
                <span v-if="record.avgRating">
                  <StarFilled style="color: #faad14; margin-right: 4px" />
                  {{ record.avgRating.toFixed(1) }}
                </span>
                <span v-else class="text-muted">-</span>
              </template>
            </a-table-column>

            <a-table-column title="状态" :width="80" align="center">
              <template #default="{ record }">
                <a-tag :color="record.status === 1 ? 'green' : 'orange'">
                  {{ record.status === 1 ? '上架' : '下架' }}
                </a-tag>
              </template>
            </a-table-column>

            <a-table-column title="创建时间" data-index="createTime" :width="170" />

            <a-table-column title="操作" key="action" :width="180" align="center" fixed="right">
              <template #default="{ record }">
                <a-space>
                  <a-button type="link" size="small" @click="$router.push(`/project/${record.id}/edit`)">
                    <EditOutlined /> 编辑
                  </a-button>
                  <a-popconfirm
                    title="确定删除此项目？"
                    ok-text="确定"
                    cancel-text="取消"
                    @confirm="handleDelete(record.id)"
                  >
                    <a-button type="link" size="small" danger>
                      <DeleteOutlined /> 删除
                    </a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </a-table-column>
          </a-table>
        </template>

        <a-empty v-else description="还没有发布过项目" class="empty-state">
          <a-button v-if="canPublishProject" type="primary" @click="$router.push('/project/create')">
            <PlusOutlined /> 发布第一个项目
          </a-button>
        </a-empty>
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, EditOutlined, DeleteOutlined, StarFilled } from '@ant-design/icons-vue'
import type { TablePaginationConfig } from 'ant-design-vue'
import { listMyProjectVoByPage, deleteProject } from '@/api/projectController.ts'
import { useLoginUserStore } from '@/stores/loginUser'

const loginUserStore = useLoginUserStore()
const canPublishProject = computed(() => loginUserStore.loginUser.userRole === 'admin')

const defaultCover = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNDAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjYwIiBoZWlnaHQ9IjQwIiBmaWxsPSIjZjBmMmY1Ii8+PHRleHQgeD0iMzAiIHk9IjIzIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmaWxsPSIjYmZiZmMwIiBmb250LXNpemU9IjgiIGZvbnQtZmFtaWx5PSJzYW5zLXNlcmlmIj5Ob25lPC90ZXh0Pjwvc3ZnPg=='

const loading = ref(false)
const projects = ref<API.ProjectVO[]>([])
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: (total: number) => `共 ${total} 个项目`,
  showSizeChanger: false,
})

const loadProjects = async () => {
  loading.value = true
  try {
    const res = await listMyProjectVoByPage({
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })
    if (res.data.code === 0 && res.data.data) {
      projects.value = res.data.data.records || []
      pagination.total = res.data.data.totalRow || 0
    }
  } catch {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleTableChange = (pag: TablePaginationConfig) => {
  pagination.current = pag.current || 1
  loadProjects()
}

const handleDelete = async (id?: number) => {
  if (!id) return
  try {
    const res = await deleteProject({ id })
    if (res.data.code === 0) {
      message.success('删除成功')
      loadProjects()
    } else {
      message.error(res.data.message || '删除失败')
    }
  } catch {
    message.error('删除失败')
  }
}

onMounted(() => {
  loadProjects()
})
</script>

<style scoped>
#myProjectsPage {
  min-height: 100vh;
  background: var(--color-bg);
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.text-muted {
  color: #bbb;
}

.empty-state {
  padding: 80px 0;
  background: #fff;
  border-radius: 12px;
}

:deep(.ant-table) {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}

:deep(.ant-table-thead > tr > th) {
  background: #fafafa;
  font-weight: 600;
}
</style>
