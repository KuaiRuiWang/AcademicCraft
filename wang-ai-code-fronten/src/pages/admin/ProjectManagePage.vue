<template>
  <div id="projectManagePage">
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="项目名称">
        <a-input v-model:value="searchParams.name" placeholder="输入项目名称" />
      </a-form-item>
      <a-form-item label="项目状态">
        <a-select
          v-model:value="searchParams.status"
          placeholder="选择状态"
          style="width: 130px"
          allow-clear
        >
          <a-select-option :value="undefined">全部</a-select-option>
          <a-select-option :value="0">草稿</a-select-option>
          <a-select-option :value="1">已上架</a-select-option>
          <a-select-option :value="2">已下架</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="作者ID">
        <a-input v-model:value="searchParams.authorId" placeholder="输入作者ID" style="width: 130px" />
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit">搜索</a-button>
          <a-button @click="router.push('/project/create')">发布模板</a-button>
        </a-space>
      </a-form-item>
    </a-form>
    <a-divider />

    <!-- 表格 -->
    <a-table
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      @change="doTableChange"
      :scroll="{ x: 1400 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'coverImage'">
          <a-image
            v-if="record.coverImage"
            :src="record.coverImage"
            :width="80"
            :height="50"
            style="border-radius: 4px; object-fit: cover"
          />
          <div v-else class="no-cover">无封面</div>
        </template>
        <template v-else-if="column.dataIndex === 'name'">
          <a-typography-link v-if="record.status === 1" @click="$router.push(`/project/${record.id}`)">
            {{ record.name }}
          </a-typography-link>
          <span v-else>{{ record.name }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'categoryName'">
          <a-tag v-if="record.categoryName">{{ record.categoryName }}</a-tag>
          <span v-else class="text-gray">-</span>
        </template>
        <template v-else-if="column.dataIndex === 'techTags'">
          <template v-if="record.techTags">
            <a-tag v-for="tag in record.techTags.split(',')" :key="tag" size="small" style="margin-bottom: 4px">
              {{ tag.trim() }}
            </a-tag>
          </template>
          <span v-else class="text-gray">-</span>
        </template>
        <template v-else-if="column.dataIndex === 'licenseTerms'">
          <a-tag v-if="record.sourceAttribution && record.licenseTerms" color="green">已登记</a-tag>
          <a-tag v-else color="orange">待登记</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag v-if="record.status === 0" color="default">草稿</a-tag>
          <a-tag v-else-if="record.status === 1" color="green">已上架</a-tag>
          <a-tag v-else-if="record.status === 2" color="orange">已下架</a-tag>
          <a-tag v-else>{{ record.status }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'author'">
          <UserInfo :user="record.author" size="small" />
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="primary" size="small" @click="editProject(record)">
              编辑
            </a-button>
            <template v-if="record.status === 1">
              <a-button size="small" @click="toggleStatus(record, 2)">
                下架
              </a-button>
            </template>
            <template v-else>
              <a-button size="small" @click="toggleStatus(record, 1)">
                上架
              </a-button>
            </template>
            <a-popconfirm title="确定要删除这个项目吗？" @confirm="deleteProjectById(record.id)">
              <a-button danger size="small">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  listProjectVoByPageByAdmin,
  updateProjectByAdmin,
  deleteProject,
} from '@/api/projectController'
import { formatTime } from '@/utils/time'
import UserInfo from '@/components/UserInfo.vue'

const router = useRouter()

const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    width: 70,
    fixed: 'left',
  },
  {
    title: '项目名称',
    dataIndex: 'name',
    width: 180,
  },
  {
    title: '封面',
    dataIndex: 'coverImage',
    width: 100,
  },
  {
    title: '分类',
    dataIndex: 'categoryName',
    width: 90,
  },
  {
    title: '技术标签',
    dataIndex: 'techTags',
    width: 160,
  },
  {
    title: '使用许可',
    dataIndex: 'licenseTerms',
    width: 100,
  },
  {
    title: '领取次数',
    dataIndex: 'salesCount',
    width: 70,
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 80,
  },
  {
    title: '作者',
    dataIndex: 'author',
    width: 120,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 160,
  },
  {
    title: '操作',
    key: 'action',
    width: 220,
    fixed: 'right',
  },
]

// 数据
const data = ref<API.ProjectVO[]>([])
const total = ref(0)

// 搜索条件
const searchParams = reactive<API.ProjectQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

// 获取数据
const fetchData = async () => {
  try {
    const res = await listProjectVoByPageByAdmin({
      ...searchParams,
    })
    if (res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } catch (error) {
    console.error('获取数据失败：', error)
    message.error('获取数据失败')
  }
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})

// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.pageNum ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

// 表格变化处理
const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 搜索
const doSearch = () => {
  searchParams.pageNum = 1
  fetchData()
}

// 编辑项目
const editProject = (project: API.ProjectVO) => {
  router.push(`/project/${project.id}/edit`)
}

// 上下架/切换状态
const toggleStatus = async (project: API.ProjectVO, newStatus: number) => {
  if (!project.id) return

  const statusText: Record<number, string> = { 0: '草稿', 1: '上架', 2: '下架' }

  try {
    const res = await updateProjectByAdmin({
      id: project.id,
      status: newStatus,
    })

    if (res.data.code === 0) {
      message.success(`已${statusText[newStatus]}`)
      fetchData()
    } else {
      message.error('操作失败：' + res.data.message)
    }
  } catch (error) {
    console.error('操作失败：', error)
    message.error('操作失败')
  }
}

// 删除项目
const deleteProjectById = async (id: number | undefined) => {
  if (!id) return

  try {
    const res = await deleteProject({ id })
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
.no-cover {
  width: 80px;
  height: 50px;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 12px;
  border-radius: 4px;
}

.text-gray {
  color: #999;
}

:deep(.ant-table-tbody > tr > td) {
  vertical-align: middle;
}
</style>
