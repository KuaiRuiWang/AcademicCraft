<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { listMyProjectVoByPage, deleteProject } from '@/api/projectController'
import { useLoginUserStore } from '@/stores/loginUser'
import { formatDate } from '@/utils/time'
import PageHeading from '@/components/PageHeading.vue'
import PersonalNav from '@/components/PersonalNav.vue'
import TemplateArtwork from '@/components/TemplateArtwork.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const canPublishProject = computed(() => loginUserStore.loginUser.userRole === 'admin')
const loading = ref(true)
const loadError = ref(false)
const projects = ref<API.ProjectVO[]>([])
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })

const statusLabels: Record<number, string> = { 0: '草稿', 1: '已上架', 2: '已下架' }

async function loadProjects() {
  loading.value = true
  loadError.value = false
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
    } else {
      loadError.value = true
    }
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function handleDelete(id?: number) {
  if (!id) return
  try {
    const res = await deleteProject({ id })
    if (res.data.code === 0) {
      message.success('模板已删除')
      if (projects.value.length === 1 && pagination.current > 1) pagination.current--
      await loadProjects()
    } else {
      message.error(res.data.message || '删除失败')
    }
  } catch {
    message.error('删除失败，请稍后重试')
  }
}

onMounted(loadProjects)
</script>

<template>
  <div class="page-shell">
    <PageHeading title="我的项目" description="管理已发布的模板和还在准备的草稿。">
      <a-button v-if="canPublishProject" type="primary" @click="router.push('/project/create')">
        <PlusOutlined aria-hidden="true" /> 发布模板
      </a-button>
    </PageHeading>
    <PersonalNav />
    <a-skeleton v-if="loading" active :title="false" :paragraph="{ rows: 3 }" />
    <a-alert v-else-if="loadError" type="error" message="项目列表加载失败" show-icon>
      <template #action><a-button size="small" @click="loadProjects">重试</a-button></template>
    </a-alert>
    <ul v-else-if="projects.length" class="paper-list">
      <li v-for="project in projects" :key="project.id" class="collection-entry">
        <TemplateArtwork class="collection-art" :cover="project.coverImage" />
        <div class="collection-copy">
          <span>{{ project.categoryName || '应用模板' }}</span>
          <h2>
            <RouterLink v-if="project.status === 1" :to="'/project/' + project.id">
              {{ project.name }}
            </RouterLink>
            <template v-else>{{ project.name }}</template>
          </h2>
          <p class="collection-meta tabular">
            <a-tag :color="project.status === 1 ? 'success' : undefined">{{
              statusLabels[project.status ?? 0] || '已下架'
            }}</a-tag>
            <span>{{ project.price ? '¥' + project.price : '免费' }}</span>
            <span>领取 {{ project.salesCount || 0 }} 次</span>
            <span v-if="project.avgRating">{{ project.avgRating.toFixed(1) }} 分</span>
            <time v-if="project.createTime">{{ formatDate(project.createTime) }}</time>
          </p>
        </div>
        <div class="folio-actions collection-actions">
          <a-button @click="router.push('/project/' + project.id + '/edit')">编辑模板</a-button>
          <a-popconfirm
            title="删除这份模板？"
            ok-text="删除"
            cancel-text="取消"
            @confirm="handleDelete(project.id)"
          >
            <a-button danger type="link">删除</a-button>
          </a-popconfirm>
        </div>
      </li>
    </ul>
    <div v-else class="folio-empty">
      <p>还没有发布过模板。</p>
      <RouterLink class="folio-link" to="/project">浏览模板目录</RouterLink>
    </div>
    <div v-if="pagination.total > pagination.pageSize" class="pagination-wrapper">
      <a-pagination
        v-model:current="pagination.current"
        :page-size="pagination.pageSize"
        :total="pagination.total"
        :show-size-changer="false"
        @change="loadProjects"
      />
    </div>
  </div>
</template>
