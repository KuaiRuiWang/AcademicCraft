<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { DownloadOutlined } from '@ant-design/icons-vue'
import { listMyOrders, getTemplateDownloadUrl } from '@/api/projectOrderController'
import { formatTime } from '@/utils/time'
import PageHeading from '@/components/PageHeading.vue'
import PersonalNav from '@/components/PersonalNav.vue'
import TemplateArtwork from '@/components/TemplateArtwork.vue'

const router = useRouter()
const loading = ref(true)
const loadError = ref(false)
const orders = ref<API.ProjectOrderVO[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10 })

const statuses: Record<number, string> = { 0: '待支付', 1: '已支付', 2: '已退款' }

function statusText(order: API.ProjectOrderVO) {
  if (order.status === 1 && !order.amount) return '已领取'
  return statuses[order.status ?? 0] || '未知状态'
}

async function loadOrders() {
  loading.value = true
  loadError.value = false
  try {
    const res = await listMyOrders(query)
    if (res.data.code === 0 && res.data.data) {
      orders.value = res.data.data.records || []
      total.value = res.data.data.totalRow || 0
    } else {
      loadError.value = true
    }
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(loadOrders)
</script>

<template>
  <div class="page-shell">
    <PageHeading title="领取记录" description="查看已领取的模板与历史记录，下载可用的源码。">
      <a-button type="primary" @click="router.push('/project')">浏览模板</a-button>
    </PageHeading>
    <PersonalNav />
    <a-skeleton v-if="loading" active :title="false" :paragraph="{ rows: 3 }" />
    <a-alert v-else-if="loadError" type="error" message="领取记录加载失败" show-icon>
      <template #action><a-button size="small" @click="loadOrders">重试</a-button></template>
    </a-alert>
    <ul v-else-if="orders.length" class="paper-list">
      <li v-for="order in orders" :key="order.id" class="collection-entry">
        <TemplateArtwork class="collection-art" :cover="order.projectCover" />
        <div class="collection-copy">
          <span class="tabular">记录编号 {{ order.id }}</span>
          <h2>
            <RouterLink
              v-if="order.downloadAvailable && order.projectId"
              :to="'/project/' + order.projectId"
            >
              {{ order.projectName }}
            </RouterLink>
            <template v-else>{{ order.projectName || '项目已移除' }}</template>
          </h2>
          <p class="collection-meta tabular">
            <a-tag :color="order.status === 1 ? 'success' : undefined">{{
              statusText(order)
            }}</a-tag>
            <span>{{ order.amount ? '¥' + order.amount : '免费' }}</span>
            <time v-if="order.createTime">{{ formatTime(order.createTime) }}</time>
          </p>
        </div>
        <div class="folio-actions collection-actions">
          <a-button
            v-if="order.downloadAvailable && order.id"
            :href="getTemplateDownloadUrl(order.id)"
            target="_blank"
            rel="noopener"
          >
            <DownloadOutlined aria-hidden="true" /> 下载源码
          </a-button>
          <span v-else class="collection-unavailable">暂不可下载</span>
        </div>
      </li>
    </ul>
    <div v-else class="folio-empty">
      <p>还没有领取过模板。</p>
      <RouterLink class="folio-link" to="/project">去模板目录看看</RouterLink>
    </div>
    <div v-if="total > query.pageSize" class="pagination-wrapper">
      <a-pagination
        v-model:current="query.pageNum"
        :page-size="query.pageSize"
        :total="total"
        :show-size-changer="false"
        @change="loadOrders"
      />
    </div>
  </div>
</template>
