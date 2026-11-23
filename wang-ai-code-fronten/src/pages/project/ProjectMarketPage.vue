<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { listProjectVoByPage } from '@/api/projectController'
import { listCategories } from '@/api/projectCategoryController'
import { starterTemplates } from '@/config/starterTemplates'
import PageHeading from '@/components/PageHeading.vue'
import ProjectCard from '@/components/ProjectCard.vue'
import StarterTemplateCards from '@/components/StarterTemplateCards.vue'

const route = useRoute()
const loading = ref(true)
const loadError = ref(false)
const projects = ref<API.ProjectVO[]>([])
const categories = ref<API.ProjectCategory[]>([])
const total = ref(0)
const selectedCategory = ref('')
const readCategoryId = () =>
  typeof route.query.categoryId === 'string' ? route.query.categoryId : undefined
const linkedCategoryId = ref(readCategoryId())
const categoryName = computed(() =>
  linkedCategoryId.value
    ? categories.value.find((category) => String(category.id) === linkedCategoryId.value)?.name ||
      ''
    : selectedCategory.value,
)
const searchInput = ref('')
const appliedSearch = ref('')
let requestNumber = 0

const query = reactive({
  pageNum: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'desc',
})

const categoryNames = computed(() => [
  ...new Set([
    ...starterTemplates.map((template) => template.category),
    ...categories.value.flatMap((category) => (category.name ? [category.name] : [])),
  ]),
])

const visibleStarters = computed(() => {
  if (query.pageNum !== 1 || (linkedCategoryId.value && !categoryName.value)) return []
  const needle = appliedSearch.value.toLowerCase()
  return starterTemplates.filter(
    (template) =>
      (!categoryName.value || categoryName.value === template.category) &&
      [template.name, template.description, template.category]
        .join(' ')
        .toLowerCase()
        .includes(needle),
  )
})

async function loadProjects() {
  const currentRequest = ++requestNumber
  loading.value = true
  loadError.value = false
  const matchingCategory = categories.value.find((category) => category.name === categoryName.value)
  const categoryId = linkedCategoryId.value
    ? (linkedCategoryId.value as unknown as number)
    : matchingCategory?.id
  if (categoryName.value && !categoryId) {
    projects.value = []
    total.value = 0
    loading.value = false
    return
  }

  try {
    const res = await listProjectVoByPage({
      ...query,
      name: appliedSearch.value || undefined,
      categoryId,
    })
    if (currentRequest !== requestNumber) return
    if (res.data.code === 0 && res.data.data) {
      projects.value = res.data.data.records || []
      total.value = res.data.data.totalRow || 0
    } else {
      loadError.value = true
    }
  } catch {
    if (currentRequest === requestNumber) loadError.value = true
  } finally {
    if (currentRequest === requestNumber) loading.value = false
  }
}

function search() {
  appliedSearch.value = searchInput.value.trim()
  query.pageNum = 1
  void loadProjects()
}

function selectCategory(name: string) {
  selectedCategory.value = name
  linkedCategoryId.value = undefined
  search()
}

function resetFilters() {
  searchInput.value = ''
  query.sortField = 'createTime'
  selectCategory('')
}

watch(
  () => route.query.categoryId,
  () => {
    linkedCategoryId.value = readCategoryId()
    selectedCategory.value = ''
    search()
  },
)

onMounted(async () => {
  try {
    const res = await listCategories()
    if (res.data.code === 0) categories.value = res.data.data || []
  } catch {
    // 远程分类暂不可用时，入门模板仍可按分类检索。
  }
  void loadProjects()
})
</script>

<template>
  <div class="page-shell">
    <PageHeading
      title="模板目录"
      description="在线试用免费模板，按使用许可领取源码，或从模板介绍起草新应用。"
    />

    <div class="catalog-tools">
      <div class="category-chips" role="group" aria-label="模板分类">
        <a-button
          v-for="name in ['', ...categoryNames]"
          :key="name"
          :class="{ 'is-selected': categoryName === name }"
          :aria-pressed="categoryName === name"
          @click="selectCategory(name)"
        >
          {{ name || '全部' }}
        </a-button>
      </div>
      <a-input-search
        v-model:value="searchInput"
        aria-label="搜索模板"
        placeholder="搜索模板"
        allow-clear
        class="catalog-search"
        @search="search"
      />
    </div>

    <section aria-label="模板结果" :aria-busy="loading">
      <ul class="catalog-list">
        <StarterTemplateCards :templates="visibleStarters" />
        <template v-if="!loading && !loadError">
          <ProjectCard
            v-for="project in projects"
            :key="project.id"
            :to="'/project/' + project.id"
            :title="project.name"
            :category="project.categoryName"
            :description="project.description"
            :cover="project.coverImage"
          />
        </template>
      </ul>

      <a-skeleton
        v-if="loading"
        class="catalog-skeleton"
        active
        :title="false"
        :paragraph="{ rows: 3 }"
      />
      <a-alert
        v-else-if="loadError"
        class="catalog-skeleton"
        type="error"
        message="已发布模板加载失败"
        show-icon
      >
        <template #action
          ><a-button size="small" @click="loadProjects">重新加载</a-button></template
        >
      </a-alert>
      <div v-else-if="!visibleStarters.length && !projects.length" class="folio-empty">
        <p>没有找到匹配的模板。</p>
        <a-button @click="resetFilters">重置筛选</a-button>
      </div>
      <div
        v-if="!loading && !loadError && (total || query.sortField !== 'createTime')"
        class="catalog-bottom"
      >
        <label for="catalog-sort">已发布模板排序</label>
        <a-select
          id="catalog-sort"
          v-model:value="query.sortField"
          :options="[
            { value: 'createTime', label: '最新' },
            { value: 'salesCount', label: '领取次数' },
            { value: 'avgRating', label: '评分' },
          ]"
          @change="search"
        />
        <span>共 {{ total }} 个</span>
      </div>
      <div v-if="!loading && !loadError && total > query.pageSize" class="pagination-wrapper">
        <a-pagination
          v-model:current="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          :show-size-changer="false"
          @change="loadProjects"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.catalog-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 14px 18px;
  margin-bottom: 20px;
}

.category-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.category-chips :deep(.ant-btn) {
  min-height: 36px;
  height: auto;
  padding: 5px 11px;
  background: transparent;
  font-size: 0.84rem;
}

.category-chips :deep(.is-selected),
.category-chips :deep(.is-selected:hover) {
  color: var(--color-surface);
  border-color: var(--color-text);
  background-color: var(--color-text);
}

.catalog-search {
  width: min(100%, 240px);
}

.catalog-list {
  margin: 0;
  padding: 0;
  border-top: 1px solid var(--color-text);
}

.catalog-skeleton,
.catalog-bottom {
  margin-top: 20px;
}

.catalog-bottom {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--color-text-secondary);
  font-size: 0.875rem;
}

@media (max-width: 520px) {
  .catalog-search {
    width: 100%;
  }
}
</style>
