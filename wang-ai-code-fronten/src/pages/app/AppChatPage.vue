<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, onUnmounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  getAppVoById,
  deployApp as deployAppApi,
  deleteApp as deleteAppApi,
} from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { CodeGenTypeEnum } from '@/utils/codeGenTypes'

import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import ThesisPanel from '@/components/ThesisPanel.vue'
import AgentRunView from '@/components/agent/AgentRunView.vue'
import { createAgentRun, restoreAgentRun, type AgentRun } from '@/utils/agentRun'
import { newRunId, useAgentRun } from '@/composables/useAgentRun'
import PageHeading from '@/components/PageHeading.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import { API_BASE_URL, getDeployUrl, getStaticPreviewUrl, toRelativeDeployUrl } from '@/config/env'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'

import {
  CloudUploadOutlined,
  SendOutlined,
  ExportOutlined,
  EditOutlined,
  DownOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 应用信息
const appInfo = ref<API.AppVO>()
const appId = ref('')
const loadingApp = ref(true)
const appError = ref('')
const previewFrame = ref<HTMLIFrameElement>()

// 对话相关
interface Message {
  id: string
  type: 'user' | 'ai'
  content: string
  loading?: boolean
  createTime?: string
  run?: AgentRun
}

const messages = ref<Message[]>([])
const userInput = ref('')
const isGenerating = ref(false)
const messagesContainer = ref<HTMLElement>()
const followOutput = ref(true)
const hasNewOutput = ref(false)
const { execute: executeAgentRun } = useAgentRun()

// 对话历史相关
const loadingHistory = ref(false)
const hasMoreHistory = ref(false)
const lastCreateTime = ref<string>()
const historyLoaded = ref(false)

// 预览相关
const previewUrl = ref('')
const previewTab = ref<'app' | 'thesis'>(route.query.tab === 'thesis' ? 'thesis' : 'app')
const previewReady = ref(false)

type WorkspaceView = 'chat' | 'split' | 'output'
const desktopViews = [
  { value: 'chat', label: '仅对话' },
  { value: 'split', label: '双栏' },
  { value: 'output', label: '仅成果' },
] as const
const desktopView = ref<WorkspaceView>('split')
const compactView = ref<'chat' | 'output'>(
  route.query.tab === 'thesis' || route.query.view === '1' ? 'output' : 'chat',
)
const compactMedia = window.matchMedia('(width < 960px)')
const compactLayout = ref(compactMedia.matches)
const isSplit = computed(() => !compactLayout.value && desktopView.value === 'split')
const chatVisible = computed(() =>
  compactLayout.value ? compactView.value === 'chat' : desktopView.value !== 'output',
)
const outputVisible = computed(() =>
  compactLayout.value ? compactView.value === 'output' : desktopView.value !== 'chat',
)

const DEFAULT_SPLIT = 44
const MIN_SPLIT = 35
const MAX_SPLIT = 65
const splitPercent = ref(DEFAULT_SPLIT)
const workspaceGrid = ref<HTMLElement>()
const resizeStart = ref<{ pointerId: number; x: number; width: number; percent: number }>()

function setWorkspaceView(view: WorkspaceView) {
  if (view !== 'split') compactView.value = view
  if (!compactLayout.value) desktopView.value = view
}

function selectOutputTab(tab: string | number) {
  previewTab.value = tab === 'thesis' ? 'thesis' : 'app'
  compactView.value = 'output'
}

watch(
  () => route.query.tab,
  (tab) => {
    previewTab.value = tab === 'thesis' ? 'thesis' : 'app'
    if (tab === 'thesis') {
      compactView.value = 'output'
      if (desktopView.value === 'chat') desktopView.value = 'output'
    }
  },
)

function setSplit(percent: number) {
  splitPercent.value = Math.max(MIN_SPLIT, Math.min(MAX_SPLIT, Math.round(percent)))
}

function startResizing(event: PointerEvent) {
  const separator = event.currentTarget as HTMLElement
  const width = (workspaceGrid.value?.clientWidth ?? 0) - separator.offsetWidth
  if (!event.isPrimary || event.button !== 0 || width <= 0) return
  resizeStart.value = {
    pointerId: event.pointerId,
    x: event.clientX,
    width,
    percent: splitPercent.value,
  }
  separator.setPointerCapture(event.pointerId)
  separator.focus({ preventScroll: true })
}

function resizePanels(event: PointerEvent) {
  const start = resizeStart.value
  if (start && start.pointerId === event.pointerId) {
    setSplit(start.percent + ((event.clientX - start.x) / start.width) * 100)
  }
}

function stopResizing(event: PointerEvent) {
  if (resizeStart.value?.pointerId === event.pointerId) resizeStart.value = undefined
}

function resizeWithKeyboard(event: KeyboardEvent) {
  switch (event.key) {
    case 'ArrowLeft':
      setSplit(splitPercent.value - 3)
      break
    case 'ArrowRight':
      setSplit(splitPercent.value + 3)
      break
    case 'Home':
      setSplit(MIN_SPLIT)
      break
    case 'End':
      setSplit(MAX_SPLIT)
      break
    default:
      return
  }
  event.preventDefault()
}

watch(isSplit, (split) => {
  if (!split) resizeStart.value = undefined
})

// 部署相关
const deploying = ref(false)
const deployModalVisible = ref(false)
const deployUrl = ref('')

// 下载相关
const downloading = ref(false)

// 可视化编辑相关
const isEditMode = ref(false)
const selectedElementInfo = ref<ElementInfo | null>(null)
const visualEditor = new VisualEditor({
  onElementSelected: (elementInfo: ElementInfo) => {
    selectedElementInfo.value = elementInfo
  },
})

// 权限相关
const isOwner = computed(() => {
  return Boolean(
    loginUserStore.loginUser.id &&
      String(appInfo.value?.userId) === String(loginUserStore.loginUser.id),
  )
})

const activeOutputTab = computed(() => (isOwner.value ? previewTab.value : 'app'))

// 应用详情相关
const appDetailVisible = ref(false)
const projectMenu = ref<HTMLDetailsElement>()

function closeProjectMenu(restoreFocus = false) {
  const menu = projectMenu.value
  if (!menu?.open) return
  menu.open = false
  if (restoreFocus) menu.querySelector('summary')?.focus()
}

function onProjectMenuBlur(event: FocusEvent) {
  if (!projectMenu.value?.contains(event.relatedTarget as Node | null)) closeProjectMenu()
}

// 显示应用详情
const showAppDetail = () => {
  appDetailVisible.value = true
}

// 加载对话历史
const loadChatHistory = async (isLoadMore = false) => {
  if (!appId.value || loadingHistory.value) return
  loadingHistory.value = true
  try {
    const params: API.listAppChatHistoryParams = {
      appId: appId.value as unknown as number,
      pageSize: 10,
    }
    // 如果是加载更多，传递最后一条消息的创建时间作为游标
    if (isLoadMore && lastCreateTime.value) {
      params.lastCreateTime = lastCreateTime.value
    }
    const res = await listAppChatHistory(params)
    if (res.data.code === 0 && res.data.data) {
      const chatHistories = res.data.data.records || []
      if (chatHistories.length > 0) {
        // 将对话历史转换为消息格式，并按时间正序排列（老消息在前）
        const historyMessages: Message[] = chatHistories
          .map((chat) => ({
            id: `history-${chat.id}`,
            type: (chat.messageType === 'user' ? 'user' : 'ai') as 'user' | 'ai',
            content: chat.message || '',
            createTime: chat.createTime,
            run: chat.messageType === 'ai' ? restoreAgentRun(chat.renderData) : undefined,
          }))
          .reverse() // 反转数组，让老消息在前
        if (isLoadMore) {
          // 加载更多时，将历史消息添加到开头
          messages.value.unshift(...historyMessages)
        } else {
          // 初始加载，直接设置消息列表
          messages.value = historyMessages
        }
        // 更新游标
        lastCreateTime.value = chatHistories[chatHistories.length - 1]?.createTime
        // 检查是否还有更多历史
        hasMoreHistory.value = chatHistories.length === 10
      } else {
        hasMoreHistory.value = false
      }
      historyLoaded.value = true
    }
  } catch (error) {
    console.error('加载对话历史失败：', error)
    message.error('加载对话历史失败')
  } finally {
    loadingHistory.value = false
  }
}

// 加载更多历史消息
const loadMoreHistory = async () => {
  const container = messagesContainer.value
  const previousHeight = container?.scrollHeight || 0
  const previousTop = container?.scrollTop || 0
  await loadChatHistory(true)
  await nextTick()
  if (container) container.scrollTop = previousTop + container.scrollHeight - previousHeight
}

// 获取应用信息
const fetchAppInfo = async () => {
  const id = route.params.id as string
  if (!id) {
    message.error('应用ID不存在')
    router.push('/')
    return
  }

  appId.value = id
  appError.value = ''

  try {
    const res = await getAppVoById({ id: id as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data

      // 先加载对话历史
      await loadChatHistory()
      scrollToBottom(true)
      // 如果有至少2条对话记录，展示对应的网站
      if (appInfo.value.deployKey || messages.value.length >= 2) {
        updatePreview()
      }
      // 检查是否需要自动发送初始提示词
      // 只有在是自己的应用且没有对话历史时才自动发送
      if (
        appInfo.value.initPrompt &&
        isOwner.value &&
        messages.value.length === 0 &&
        historyLoaded.value
      ) {
        await sendInitialMessage(appInfo.value.initPrompt)
      }
    } else {
      appError.value = res.data.message || '无法读取应用。'
    }
  } catch {
    appError.value = '无法读取应用，请检查网络后重试。'
  } finally {
    loadingApp.value = false
  }
}

// 发送初始消息
const sendInitialMessage = async (prompt: string) => {
  // 添加用户消息
  messages.value.push({
    id: newRunId(),
    type: 'user',
    content: prompt,
  })

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    id: newRunId(),
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom(true)

  // 开始生成
  isGenerating.value = true
  await generateCode(prompt, aiMessageIndex)
}

// 发送消息
const sendMessage = async () => {
  if (!isOwner.value || !userInput.value.trim() || isGenerating.value) {
    return
  }

  let message = userInput.value.trim()
  // 如果有选中的元素，将元素信息添加到提示词中
  if (selectedElementInfo.value) {
    let elementContext = `\n\n选中元素信息：`
    if (selectedElementInfo.value.pagePath) {
      elementContext += `\n- 页面路径: ${selectedElementInfo.value.pagePath}`
    }
    elementContext += `\n- 标签: ${selectedElementInfo.value.tagName.toLowerCase()}\n- 选择器: ${selectedElementInfo.value.selector}`
    if (selectedElementInfo.value.textContent) {
      elementContext += `\n- 当前内容: ${selectedElementInfo.value.textContent.substring(0, 100)}`
    }
    message += elementContext
  }
  userInput.value = ''
  // 添加用户消息（包含元素信息）
  messages.value.push({
    id: newRunId(),
    type: 'user',
    content: message,
  })

  // 发送消息后，清除选中元素并退出编辑模式
  if (selectedElementInfo.value) {
    clearSelectedElement()
    if (isEditMode.value) {
      toggleEditMode()
    }
  }

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    id: newRunId(),
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom(true)

  // 开始生成
  isGenerating.value = true
  await generateCode(message, aiMessageIndex)
}

// AG-UI 事件按智能体、工具调用分别更新，历史翻页不会改变本轮消息的引用。
const generateCode = async (userMessage: string, aiMessageIndex: number) => {
  const targetMessage = messages.value[aiMessageIndex]
  if (!targetMessage) return
  const run = reactive(createAgentRun(newRunId()))
  targetMessage.run = run
  try {
    await executeAgentRun(String(appId.value), userMessage, run, () => {
      targetMessage.loading = run.status === 'running'
      if (followOutput.value) scrollToBottom()
      else hasNewOutput.value = true
    })
    if (run.status === 'success') {
      // 更新应用信息与预览，保留已展开的本轮工具详情。
      const response = await getAppVoById({ id: appId.value as unknown as number })
      if (response.data.code === 0 && response.data.data) appInfo.value = response.data.data
      updatePreview()
    }
  } catch (error) {
    console.warn('生成完成后读取预览信息失败：', error)
    message.warning('代码已生成，预览信息读取失败，可刷新页面重试')
  } finally {
    targetMessage.loading = false
    isGenerating.value = false
  }
}

// 更新预览
const updatePreview = () => {
  if (appId.value) {
    const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML
    const deployKey = appInfo.value?.deployKey
    // 全栈应用带 Node.js 后端，静态产物预览里页面内 ./api 请求没有后端可服务，
    // 已部署过则直接预览部署地址（/apps/{deployKey}/），未部署回退到静态产物
    const nextUrl =
      deployKey && codeGenType === CodeGenTypeEnum.FULLSTACK_VUE_NODEJS
        ? getDeployUrl(deployKey, codeGenType)
        : getStaticPreviewUrl(codeGenType, appId.value)
    if (nextUrl !== previewUrl.value) previewReady.value = false
    previewUrl.value = nextUrl
  }
}

// 滚动到底部（使用 RAF 避免强制同步布局）
const scrollToBottom = (force = false) => {
  if (force) {
    followOutput.value = true
    hasNewOutput.value = false
  }
  nextTick(() => {
    if (chatVisible.value && messagesContainer.value && followOutput.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

watch([chatVisible, splitPercent, desktopView], () => {
  if (chatVisible.value) scrollToBottom()
})

const handleChatScroll = () => {
  const container = messagesContainer.value
  if (!container || !chatVisible.value) return
  followOutput.value = container.scrollHeight - container.clientHeight - container.scrollTop < 80
  if (followOutput.value) hasNewOutput.value = false
}

// 下载代码
const downloadCode = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  downloading.value = true
  try {
    const url = `${API_BASE_URL}/app/download/${appId.value}`
    const response = await fetch(url, {
      method: 'GET',
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status}`)
    }
    if (response.headers.get('Content-Type')?.includes('json')) {
      const result = (await response.json()) as { message?: string }
      throw new Error(result.message || '下载失败，请先生成源码')
    }
    // 获取文件名
    const contentDisposition = response.headers.get('Content-Disposition')
    const fileName = contentDisposition?.match(/filename="(.+)"/)?.[1] || `app-${appId.value}.zip`
    // 下载文件
    const blob = await response.blob()
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    document.body.append(link)
    link.click()
    link.remove()
    // 清理
    setTimeout(() => URL.revokeObjectURL(downloadUrl), 1000)
    message.success('源码已下载')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// 部署应用
const deployApp = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }

  deploying.value = true
  try {
    const res = await deployAppApi({
      appId: appId.value as unknown as number,
    })

    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = toRelativeDeployUrl(res.data.data)
      deployModalVisible.value = true
      message.success('部署成功')
      // 重新拉取应用信息以拿到 deployKey，让全栈应用预览切换到部署地址
      await fetchAppInfo()
      updatePreview()
    } else {
      message.error('部署失败：' + res.data.message)
    }
  } catch (error) {
    console.error('部署失败：', error)
    message.error('部署失败，请重试')
  } finally {
    deploying.value = false
  }
}

// 打开部署的网站
const openDeployedSite = () => {
  if (deployUrl.value) {
    window.open(deployUrl.value, '_blank', 'noopener')
  }
}

// iframe加载完成
const onIframeLoad = () => {
  previewReady.value = true
  const iframe = previewFrame.value
  if (iframe) {
    visualEditor.init(iframe)
    visualEditor.onIframeLoad()
  }
}

// 编辑应用
const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`)
  }
}

// 删除应用
const deleteApp = async () => {
  if (!appInfo.value?.id) return

  try {
    const res = await deleteAppApi({ id: appInfo.value.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      appDetailVisible.value = false
      router.push('/')
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}

// 可视化编辑相关函数
const toggleEditMode = () => {
  // 检查 iframe 是否已经加载
  const iframe = previewFrame.value
  if (!iframe) {
    message.warning('请等待页面加载完成')
    return
  }
  // 确保 visualEditor 已初始化
  if (!previewReady.value) {
    message.warning('请等待页面加载完成')
    return
  }
  const newEditMode = visualEditor.toggleEditMode()
  isEditMode.value = newEditMode
}

const clearSelectedElement = () => {
  selectedElementInfo.value = null
  visualEditor.clearSelection()
}

async function composeElementEdit() {
  setWorkspaceView(compactLayout.value ? 'chat' : 'split')
  await nextTick()
  document.getElementById('chat-input')?.focus()
}

const getInputPlaceholder = () => {
  if (selectedElementInfo.value) {
    return `正在编辑 ${selectedElementInfo.value.tagName.toLowerCase()} 元素，描述您想要的修改...`
  }
  return '描述下一步想调整的地方…'
}

function onComposerEnter(event: KeyboardEvent) {
  if (event.isComposing || event.keyCode === 229 || event.shiftKey) return
  event.preventDefault()
  void sendMessage()
}

function updateCompactLayout(event: MediaQueryListEvent) {
  compactLayout.value = event.matches
}

// 页面加载时获取应用信息
onMounted(() => {
  compactMedia.addEventListener('change', updateCompactLayout)
  fetchAppInfo()

  // 监听 iframe 消息
  window.addEventListener('message', handlePreviewMessage)
})

// 清理资源
onUnmounted(() => {
  compactMedia.removeEventListener('change', updateCompactLayout)
  window.removeEventListener('message', handlePreviewMessage)
})

const handlePreviewMessage = (event: MessageEvent) => visualEditor.handleIframeMessage(event)
</script>

<template>
  <div class="app-workspace" :class="{ 'is-compact': compactLayout }">
    <div v-if="loadingApp && !appInfo" class="paper-panel" aria-busy="true">
      <a-skeleton active :paragraph="{ rows: 6 }" />
    </div>
    <template v-else-if="appError">
      <PageHeading title="应用无法打开" :description="appError">
        <a-button @click="fetchAppInfo">重新读取</a-button>
        <RouterLink class="folio-link" to="/">返回首页</RouterLink>
      </PageHeading>
    </template>
    <template v-else-if="appInfo">
      <header class="workspace-bar">
        <h1 :title="appInfo.appName">{{ appInfo.appName || '对话工作区' }}</h1>
        <div class="project-actions">
          <details
            v-if="isOwner"
            ref="projectMenu"
            class="project-menu"
            @focusout="onProjectMenuBlur"
            @keydown.esc.stop="closeProjectMenu(true)"
          >
            <summary aria-label="项目操作">项目 <DownOutlined aria-hidden="true" /></summary>
            <div class="project-menu-actions" @click="closeProjectMenu(true)">
              <a-button type="text" block @click="editApp">应用设置</a-button>
              <a-button type="text" block @click="showAppDetail">应用详情</a-button>
              <a-button
                v-if="isOwner"
                type="text"
                block
                :disabled="isGenerating || downloading"
                @click="downloadCode"
              >
                {{ downloading ? '正在下载…' : '下载源码' }}
              </a-button>
            </div>
          </details>
          <a-button v-else @click="showAppDetail">应用详情</a-button>
          <a-button v-if="isOwner" :loading="deploying" :disabled="isGenerating" @click="deployApp">
            <CloudUploadOutlined aria-hidden="true" /> 部署
          </a-button>
        </div>
        <div v-if="compactLayout" class="view-options" role="group" aria-label="工作区视图">
          <button
            type="button"
            aria-controls="project-conversation"
            :aria-pressed="chatVisible"
            @click="setWorkspaceView('chat')"
          >
            对话
          </button>
          <button
            type="button"
            aria-controls="project-output"
            :aria-pressed="outputVisible && activeOutputTab === 'app'"
            @click="selectOutputTab('app')"
          >
            应用预览
          </button>
          <button
            v-if="isOwner"
            type="button"
            aria-controls="project-output"
            :aria-pressed="outputVisible && activeOutputTab === 'thesis'"
            @click="selectOutputTab('thesis')"
          >
            论文初稿
          </button>
        </div>
        <div v-else class="view-options" role="group" aria-label="工作区视图">
          <button
            v-for="{ value, label } in desktopViews"
            :key="value"
            type="button"
            :aria-pressed="desktopView === value"
            @click="setWorkspaceView(value)"
          >
            {{ label }}
          </button>
        </div>
      </header>

      <div
        ref="workspaceGrid"
        class="work-grid"
        :class="{ 'is-split': isSplit, 'is-resizing': resizeStart }"
        :style="{
          '--chat-width': splitPercent + 'fr',
          '--output-width': 100 - splitPercent + 'fr',
        }"
      >
        <section
          v-show="chatVisible"
          id="project-conversation"
          class="conversation"
          aria-labelledby="conversation-title"
        >
          <h2 id="conversation-title">项目对话</h2>
          <div v-if="loadingHistory && !messages.length" class="history-loading" aria-busy="true">
            <a-skeleton active :title="false" :paragraph="{ rows: 3 }" />
          </div>
          <div
            v-else-if="messages.length"
            ref="messagesContainer"
            class="messages-container"
            role="region"
            aria-label="对话消息"
            tabindex="0"
            @scroll.passive="handleChatScroll"
          >
            <div class="message-list">
              <div v-if="hasMoreHistory" class="load-more">
                <a-button type="link" :loading="loadingHistory" @click="loadMoreHistory">
                  加载更多历史消息
                </a-button>
              </div>
              <article v-for="chatMessage in messages" :key="chatMessage.id" class="message-item">
                <h3 :class="{ 'user-message': chatMessage.type === 'user' }">
                  {{ chatMessage.type === 'user' ? (isOwner ? '你' : '创作者') : '工坊助手' }}
                </h3>
                <p v-if="chatMessage.type === 'user'" class="message-text">
                  {{ chatMessage.content }}
                </p>
                <div v-else>
                  <AgentRunView
                    v-if="chatMessage.run"
                    :run="chatMessage.run"
                    :fullstack="appInfo.codeGenType === CodeGenTypeEnum.FULLSTACK_VUE_NODEJS"
                  />
                  <MarkdownRenderer
                    v-else-if="chatMessage.content"
                    :content="chatMessage.content"
                  />
                  <div
                    v-if="chatMessage.loading && !chatMessage.run"
                    class="loading-indicator"
                    role="status"
                  >
                    <a-spin size="small" /> 正在准备生成…
                  </div>
                </div>
              </article>
            </div>
          </div>
          <p v-else class="chat-empty">还没有对话消息。</p>

          <div v-if="isOwner || hasNewOutput" class="conversation-end">
            <div v-if="hasNewOutput" class="new-output-notice" aria-live="polite">
              <a-button size="small" @click="scrollToBottom(true)">有新输出 · 回到底部</a-button>
            </div>
            <a-alert
              v-if="selectedElementInfo"
              class="element-alert"
              type="info"
              closable
              @close="clearSelectedElement"
            >
              <template #message>
                <div><strong>选中元素</strong> {{ selectedElementInfo.tagName.toLowerCase() }}</div>
                <div v-if="selectedElementInfo.pagePath">{{ selectedElementInfo.pagePath }}</div>
                <code>{{ selectedElementInfo.selector }}</code>
                <div v-if="selectedElementInfo.textContent">
                  {{ selectedElementInfo.textContent.slice(0, 50) }}
                </div>
              </template>
            </a-alert>
            <form v-if="isOwner" class="chat-composer" @submit.prevent="sendMessage">
              <label for="chat-input">继续修改</label>
              <a-textarea
                id="chat-input"
                v-model:value="userInput"
                :placeholder="getInputPlaceholder()"
                :auto-size="{ minRows: 2, maxRows: 5 }"
                :maxlength="1000"
                :disabled="isGenerating"
                @keydown.enter="onComposerEnter"
              />
              <a-button
                type="primary"
                html-type="submit"
                :loading="isGenerating"
                :disabled="!userInput.trim()"
              >
                发送 <SendOutlined aria-hidden="true" />
              </a-button>
            </form>
          </div>
        </section>

        <div
          v-if="isSplit"
          class="workspace-divider"
          role="separator"
          tabindex="0"
          aria-orientation="vertical"
          aria-labelledby="conversation-title"
          aria-controls="project-conversation"
          :aria-valuemin="MIN_SPLIT"
          :aria-valuemax="MAX_SPLIT"
          :aria-valuenow="splitPercent"
          :aria-valuetext="splitPercent + '%'"
          title="拖动或用左右方向键调整栏宽"
          @pointerdown="startResizing"
          @pointermove="resizePanels"
          @pointerup="stopResizing"
          @pointercancel="stopResizing"
          @lostpointercapture="stopResizing"
          @keydown="resizeWithKeyboard"
        ></div>

        <section
          v-show="outputVisible"
          id="project-output"
          class="workspace-output"
          aria-label="应用交付内容"
        >
          <a-tabs :active-key="activeOutputTab" class="workspace-tabs" @change="selectOutputTab">
            <a-tab-pane key="app" tab="应用预览">
              <div class="preview-panel">
                <div class="preview-toolbar">
                  <span class="preview-caption" :class="{ ready: previewUrl && !isGenerating }">
                    {{ isGenerating ? '正在生成' : previewUrl ? '可交互预览' : '等待产物' }}
                  </span>
                  <div class="preview-actions">
                    <a-button
                      v-if="isOwner && selectedElementInfo && !chatVisible"
                      type="link"
                      @click="composeElementEdit"
                    >
                      描述修改
                    </a-button>
                    <a-button
                      v-if="isOwner && previewUrl"
                      type="link"
                      :disabled="isGenerating"
                      :danger="isEditMode"
                      @click="toggleEditMode"
                    >
                      <EditOutlined aria-hidden="true" />
                      {{ isEditMode ? '退出编辑' : '编辑模式' }}
                    </a-button>
                    <a-button
                      v-if="previewUrl && !isGenerating"
                      type="link"
                      :href="previewUrl"
                      target="_blank"
                      rel="noopener"
                    >
                      独立打开 <ExportOutlined aria-hidden="true" />
                    </a-button>
                  </div>
                </div>
                <div v-if="isGenerating" class="preview-empty" role="status">
                  <a-spin />
                  <p>应用正在生成，可在项目对话中查看进度。</p>
                </div>
                <iframe
                  v-else-if="previewUrl"
                  ref="previewFrame"
                  :src="previewUrl"
                  :title="(appInfo.appName || '应用') + '预览'"
                  class="preview-iframe"
                  @load="onIframeLoad"
                ></iframe>
                <div v-else class="preview-empty">网站文件生成后将在此展示。</div>
              </div>
            </a-tab-pane>
            <a-tab-pane v-if="isOwner" key="thesis" tab="论文初稿">
              <div class="thesis-scroll" role="region" aria-label="论文工作区" tabindex="0">
                <ThesisPanel :app-id="appId" :app-generating="isGenerating" />
              </div>
            </a-tab-pane>
          </a-tabs>
        </section>
      </div>

      <AppDetailModal
        v-model:open="appDetailVisible"
        :app="appInfo"
        :show-actions="isOwner"
        @edit="editApp"
        @delete="deleteApp"
      />
      <DeploySuccessModal
        v-model:open="deployModalVisible"
        :deploy-url="deployUrl"
        @open-site="openDeployedSite"
      />
    </template>
  </div>
</template>

<style scoped>
.app-workspace {
  --pane-padding: clamp(12px, 1.55vw, 24px);
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  width: var(--page-measure);
  min-height: 0;
  margin-inline: auto;
  padding-block: 12px max(12px, env(safe-area-inset-bottom));
}

.workspace-bar {
  display: grid;
  grid-template-columns: minmax(0, max-content) auto 1fr;
  align-items: center;
  gap: 8px 16px;
}

.workspace-bar h1 {
  overflow: hidden;
  font-size: clamp(1.16rem, 1.65vw, 1.55rem);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.project-actions > :deep(.ant-btn),
.project-menu summary {
  min-height: 43px;
  padding-inline: 10px;
}

.project-menu {
  position: relative;
}

.project-menu summary {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text);
  font-size: 0.875rem;
  cursor: pointer;
}

.project-menu summary::-webkit-details-marker {
  display: none;
}

.project-menu summary:hover,
.project-menu[open] summary {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.project-menu-actions {
  position: absolute;
  z-index: 2;
  inset: calc(100% + 4px) 0 auto auto;
  min-width: 138px;
  padding: 4px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
}

.project-menu-actions :deep(.ant-btn) {
  text-align: start;
}

.view-options {
  display: grid;
  grid-auto-columns: 1fr;
  grid-auto-flow: column;
  justify-self: end;
  min-width: 0;
  padding: 2px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-soft);
}

.view-options button {
  min-height: 34px;
  padding-inline: 12px;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-secondary);
  font: 650 0.875rem/1.25 var(--font-body);
  white-space: nowrap;
  cursor: pointer;
}

.view-options button:hover {
  color: var(--color-primary);
}

.view-options button[aria-pressed='true'] {
  background: var(--color-surface);
  box-shadow: inset 0 -2px var(--color-primary);
  color: var(--color-primary);
}

.view-options button:focus-visible {
  outline-offset: -3px;
}

.work-grid {
  display: grid;
  flex: 1;
  grid-template: minmax(0, 1fr) / minmax(0, 1fr);
  min-height: 0;
}

.work-grid.is-split {
  grid-template-columns: minmax(0, var(--chat-width)) 24px minmax(0, var(--output-width));
}

.work-grid.is-resizing {
  cursor: col-resize;
  user-select: none;
}

.work-grid.is-resizing iframe {
  pointer-events: none;
}

.workspace-divider {
  display: grid;
  place-items: center;
  cursor: col-resize;
  touch-action: none;
}

.workspace-divider::before {
  content: '';
  width: 6px;
  height: 36px;
  border-inline: 1px solid var(--color-text-tertiary);
}

.workspace-divider:hover,
.workspace-divider:focus-visible,
.is-resizing .workspace-divider {
  background: var(--color-primary-soft);
}

.workspace-divider:focus-visible {
  outline-offset: -2px;
}

.conversation,
.workspace-output {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
}

.conversation h2 {
  display: flex;
  flex: none;
  align-items: center;
  min-height: 44px;
  padding-inline: var(--pane-padding);
  border-bottom: 1px solid var(--color-border);
  font-size: 1.1rem;
}

.messages-container,
.history-loading,
.chat-empty {
  flex: 1;
  min-height: 0;
}

.messages-container,
.thesis-scroll {
  overflow: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.messages-container:focus-visible,
.thesis-scroll:focus-visible {
  outline-offset: -3px;
}

.message-list {
  max-width: 54rem;
  margin-inline: auto;
  padding: 4px var(--pane-padding);
}

.message-item {
  padding-block: 12px 14px;
  overflow-wrap: anywhere;
}

.message-item + .message-item {
  border-top: 1px solid var(--color-border);
}

.message-item h3 {
  margin-bottom: 5px;
  font: 700 0.86rem/1.5 var(--font-body);
  letter-spacing: 0;
}

.message-item .user-message {
  color: var(--color-primary);
}

.message-text {
  margin: 0;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
}

.load-more {
  padding: 4px 0;
  text-align: center;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--color-text-secondary);
}

.history-loading,
.chat-empty {
  margin: 0;
  padding: var(--pane-padding);
  color: var(--color-text-secondary);
}

.conversation-end {
  display: grid;
  gap: 8px;
  padding: 10px var(--pane-padding) 12px;
  border-top: 1px solid var(--color-border);
  background: var(--color-bg);
}

.conversation-end > * {
  width: 100%;
  max-width: 54rem;
  min-width: 0;
  margin-inline: auto;
}

.element-alert {
  overflow-wrap: anywhere;
}

.chat-composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 6px 10px;
}

.chat-composer label {
  grid-column: 1 / -1;
  font-size: 0.875rem;
  font-weight: 650;
}

.workspace-tabs,
.workspace-tabs :deep(.ant-tabs-content-holder),
.workspace-tabs :deep(.ant-tabs-content),
.workspace-tabs :deep(.ant-tabs-tabpane-active),
.preview-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.workspace-tabs :deep(.ant-tabs-nav) {
  min-height: 44px;
  margin: 0;
  padding-inline: var(--pane-padding);
}

.workspace-tabs :deep(.ant-tabs-nav)::before {
  border-color: var(--color-border);
}

.workspace-tabs :deep(.ant-tabs-tab) {
  padding: 8px 3px;
}

.workspace-tabs :deep(.ant-tabs-tab + .ant-tabs-tab) {
  margin-inline-start: 16px;
}

.preview-toolbar {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0 12px;
  min-height: 42px;
  padding-inline: var(--pane-padding);
  border-bottom: 1px solid var(--color-border);
  font-size: 0.85rem;
}

.preview-caption {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 650;
}

.preview-caption.ready::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-primary);
}

.preview-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 14px;
}

.preview-actions :deep(.ant-btn) {
  min-height: 40px;
  font-size: 0.85rem;
}

.preview-empty {
  display: grid;
  flex: 1;
  place-content: center;
  padding: 20px;
  color: var(--color-text-secondary);
  text-align: center;
}

.preview-empty p {
  margin: 12px 0 0;
}

.preview-iframe {
  display: block;
  flex: 1;
  width: 100%;
  min-height: 0;
  border: 0;
  background: white;
}

.thesis-scroll {
  flex: 1;
  min-height: 0;
}

.thesis-scroll :deep(.thesis-panel) {
  border: 0;
}

.is-compact {
  gap: 8px;
  padding-top: 8px;
}

.is-compact .workspace-bar {
  grid-template-columns: minmax(0, 1fr) auto;
}

.is-compact .workspace-bar h1 {
  font-size: 1.12rem;
}

.is-compact .view-options {
  grid-column: 1 / -1;
  justify-self: stretch;
}

.is-compact .view-options button {
  min-height: 42px;
  padding-inline: 6px;
}

.is-compact .workspace-tabs :deep(.ant-tabs-nav) {
  display: none;
}

.is-compact .preview-actions :deep(.ant-btn) {
  min-height: 44px;
}

@media (height < 480px) {
  .messages-container,
  .thesis-scroll {
    overscroll-behavior: auto;
  }
}
</style>
