<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, onUnmounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  getAppVoById,
  deployApp as deployAppApi,
  deleteApp as deleteAppApi,
  deleteAppByAdmin,
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
  DownloadOutlined,
  EditOutlined,
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
const previewTab = ref(route.query.tab === 'thesis' ? 'thesis' : 'app')
const compactMedia = window.matchMedia('(max-width: 760px)')
const compactLayout = ref(compactMedia.matches)
const previewFirst = computed(
  () => compactLayout.value && (Boolean(previewUrl.value) || previewTab.value === 'thesis'),
)
watch(
  () => route.query.tab,
  (tab) => {
    previewTab.value = tab === 'thesis' ? 'thesis' : 'app'
  },
)
const previewReady = ref(false)

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

const isAdmin = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin'
})

// 应用详情相关
const appDetailVisible = ref(false)

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
    if (messagesContainer.value && followOutput.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

const handleChatScroll = () => {
  const container = messagesContainer.value
  if (!container) return
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
    const removeApp = isAdmin.value ? deleteAppByAdmin : deleteAppApi
    const res = await removeApp({ id: appInfo.value.id })
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
  <div class="page-shell app-workspace">
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
      <PageHeading :title="appInfo.appName || '对话工作区'">
        <a-button v-if="isOwner || isAdmin" @click="editApp">应用设置</a-button>
        <a-button @click="showAppDetail">应用详情</a-button>
        <a-button
          v-if="isOwner"
          :loading="downloading"
          :disabled="isGenerating"
          @click="downloadCode"
        >
          <DownloadOutlined aria-hidden="true" /> 下载源码
        </a-button>
        <a-button v-if="isOwner" :loading="deploying" :disabled="isGenerating" @click="deployApp">
          <CloudUploadOutlined aria-hidden="true" /> 部署
        </a-button>
      </PageHeading>

      <div class="work-grid">
        <div id="compact-app-output" v-show="previewFirst" class="compact-app-output"></div>
        <section class="conversation" aria-labelledby="conversation-title">
          <h2 id="conversation-title">项目对话</h2>
          <div v-if="loadingHistory && !messages.length" class="history-loading" aria-busy="true">
            <a-skeleton active :title="false" :paragraph="{ rows: 3 }" />
          </div>
          <div
            v-else-if="messages.length"
            ref="messagesContainer"
            class="messages-container"
            aria-label="对话消息"
            tabindex="0"
            @scroll.passive="handleChatScroll"
          >
            <div v-if="hasMoreHistory" class="load-more">
              <a-button type="link" @click="loadMoreHistory" :loading="loadingHistory"
                >加载更多历史消息</a-button
              >
            </div>
            <article v-for="chatMessage in messages" :key="chatMessage.id" class="message-item">
              <h3>{{ chatMessage.type === 'user' ? (isOwner ? '你' : '创作者') : '工坊助手' }}</h3>
              <p v-if="chatMessage.type === 'user'" class="message-text">
                {{ chatMessage.content }}
              </p>
              <div v-else>
                <AgentRunView
                  v-if="chatMessage.run"
                  :run="chatMessage.run"
                  :fullstack="appInfo.codeGenType === CodeGenTypeEnum.FULLSTACK_VUE_NODEJS"
                />
                <MarkdownRenderer v-else-if="chatMessage.content" :content="chatMessage.content" />
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
          <p v-else class="chat-empty">还没有对话消息。</p>

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
              :rows="3"
              :maxlength="1000"
              :disabled="isGenerating"
              @keydown.enter="onComposerEnter"
            />
            <div class="composer-actions">
              <a-button
                type="primary"
                html-type="submit"
                :loading="isGenerating"
                :disabled="!userInput.trim()"
              >
                发送 <SendOutlined aria-hidden="true" />
              </a-button>
            </div>
          </form>
        </section>

        <Teleport defer to="#compact-app-output" :disabled="!previewFirst">
          <section class="workspace-output" aria-label="应用交付内容">
            <a-tabs v-model:activeKey="previewTab" class="workspace-tabs">
              <a-tab-pane key="app" tab="应用预览">
                <div class="preview-panel">
                  <div class="preview-toolbar">
                    <span class="preview-caption" :class="{ ready: previewUrl && !isGenerating }">
                      {{ isGenerating ? '正在生成' : previewUrl ? '可交互预览' : '等待产物' }}
                    </span>
                    <div class="preview-actions">
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
                <ThesisPanel :app-id="appId" :app-generating="isGenerating" />
              </a-tab-pane>
            </a-tabs>
          </section>
        </Teleport>
      </div>

      <AppDetailModal
        v-model:open="appDetailVisible"
        :app="appInfo"
        :show-actions="isOwner || isAdmin"
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
.work-grid {
  display: grid;
  grid-template-columns: minmax(245px, 0.8fr) minmax(0, 2fr);
  gap: clamp(22px, 3vw, 32px);
  align-items: start;
}

.conversation,
.workspace-output {
  min-width: 0;
}

.compact-app-output {
  display: contents;
}

.conversation {
  border-top: 2px solid var(--color-text);
  padding-top: 16px;
}

.conversation h2 {
  font-size: clamp(1.35rem, 2.15vw, 1.75rem);
  margin-bottom: 15px;
}

.messages-container {
  max-height: min(55dvh, 550px);
  overflow: auto;
  scrollbar-gutter: stable;
  border-block: 1px solid var(--color-border);
}

.message-item {
  padding: 12px 10px 14px 0;
  overflow-wrap: anywhere;
}

.message-item + .message-item,
.message-item:first-child:not(:only-child) {
  border-top: 1px solid var(--color-border);
}

.message-item h3 {
  font: 700 0.86rem/1.5 var(--font-body);
  letter-spacing: 0;
  margin-bottom: 5px;
}

.message-text {
  white-space: pre-wrap;
  color: var(--color-text-secondary);
  margin: 0;
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

.new-output-notice,
.element-alert {
  margin-top: 10px;
}

.element-alert code {
  overflow-wrap: anywhere;
}

.chat-empty {
  margin: 0;
  padding: 12px 0;
  color: var(--color-text-secondary);
  border-block-start: 1px solid var(--color-border);
}

.chat-composer {
  margin-top: 22px;
}

.chat-composer label {
  display: block;
  margin-bottom: 7px;
  font-weight: 650;
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.workspace-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 16px;
}

.workspace-tabs :deep(.ant-tabs-tab) {
  padding: 8px 3px 10px;
}

.workspace-tabs :deep(.ant-tabs-tab + .ant-tabs-tab) {
  margin-left: 12px;
}

.preview-panel {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
}

.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 5px 12px;
  flex-wrap: wrap;
  min-height: 41px;
  padding: 0 14px;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.85rem;
}

.preview-caption {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  font-weight: 650;
}

.preview-caption.ready::before {
  content: '';
  width: 7px;
  height: 7px;
  background: var(--color-primary);
  border-radius: 50%;
}

.preview-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 16px;
}

.preview-actions :deep(.ant-btn) {
  min-height: 41px;
  font-size: 0.85rem;
}

.preview-empty {
  display: grid;
  place-content: center;
  min-height: clamp(240px, 38vw, 540px);
  padding: 24px;
  text-align: center;
  color: var(--color-text-secondary);
  background: var(--color-surface);
}

.preview-empty p {
  margin: 12px 0 0;
}

.preview-iframe {
  display: block;
  width: 100%;
  height: clamp(370px, 44vw, 610px);
  border: 0;
  background: white;
}

@media (max-width: 760px) {
  .work-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 27px;
  }

  .messages-container {
    max-height: clamp(150px, 38dvh, 340px);
  }

  .preview-toolbar {
    padding-inline: 10px;
  }
}
</style>
