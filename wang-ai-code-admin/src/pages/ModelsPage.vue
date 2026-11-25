<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message, type TableColumnsType } from 'ant-design-vue'
import { deleteModel, listModels, protocolLabels, saveModel, type ModelInput, type ModelItem, type ModelProtocol } from '@/api/models'
import { can } from '@/session'

const models = ref<ModelItem[]>([])
const loading = ref(false)
const loadError = ref('')
const editError = ref('')
const open = ref(false)
const busy = ref(false)
const deleting = ref<string>()
const selected = ref<ModelItem>()
const provider = ref()
const form = reactive<ModelInput>({ name: '', protocol: 'OPENAI_COMPATIBLE', baseUrl: '', modelId: '', apiKey: '' })
const providers: { value: string; label: string; url: string; model?: string; protocol: ModelProtocol }[] = [
  { value: 'openai', label: 'OpenAI', url: 'https://api.openai.com/v1', protocol: 'OPENAI_COMPATIBLE' },
  { value: 'deepseek', label: 'DeepSeek', url: 'https://api.deepseek.com', model: 'deepseek-flash', protocol: 'OPENAI_COMPATIBLE' },
  { value: 'qwen', label: '阿里云百炼', url: 'https://dashscope.aliyuncs.com/compatible-mode/v1', protocol: 'OPENAI_COMPATIBLE' },
  { value: 'gemini', label: 'Gemini 兼容接口', url: 'https://generativelanguage.googleapis.com/v1beta/openai', protocol: 'OPENAI_COMPATIBLE' },
  { value: 'logo', label: 'DashScope 图像', url: 'https://dashscope.aliyuncs.com/api/v1', model: 'wan2.2-t2i-flash', protocol: 'DASHSCOPE_IMAGE' },
]
const columns = computed<TableColumnsType<ModelItem>>(() => [
  { title: '名称', dataIndex: 'name', key: 'name', width: 170 },
  { title: '接口协议', dataIndex: 'protocol', key: 'protocol', width: 145 },
  { title: 'Base URL', dataIndex: 'baseUrl', key: 'baseUrl' },
  { title: '模型 ID', dataIndex: 'modelId', key: 'modelId', width: 200 },
  { title: 'API Key', key: 'key', width: 90 },
  ...(can('MODEL_WRITE') ? [{ title: '操作', key: 'actions', width: 144 }] : []),
])

async function load() {
  loading.value = true
  loadError.value = ''
  try { models.value = await listModels() }
  catch (error) { loadError.value = error instanceof Error ? error.message : '读取模型失败' }
  finally { loading.value = false }
}

function edit(item?: ModelItem) {
  selected.value = item
  provider.value = undefined
  const { name = '', protocol = 'OPENAI_COMPATIBLE', baseUrl = '', modelId = '', maxTokens, temperature } = item ?? {}
  Object.assign(form, { name, protocol, baseUrl, modelId, maxTokens, temperature, apiKey: '' })
  editError.value = ''
  open.value = true
}

function applyPreset(value: unknown) {
  const preset = providers.find((item) => item.value === value)
  if (!preset) return
  form.protocol = preset.protocol
  form.baseUrl = preset.url
  form.modelId = preset.model ?? ''
}

async function submit() {
  if (busy.value) return
  busy.value = true
  editError.value = ''
  try {
    await saveModel({
      ...form,
      ...(form.protocol === 'DASHSCOPE_IMAGE' ? { maxTokens: undefined, temperature: undefined } : {}),
    }, selected.value?.id)
    form.apiKey = ''
    open.value = false
    message.success('模型已保存')
    await load()
  } catch (error) { editError.value = error instanceof Error ? error.message : '无法保存模型' }
  finally { busy.value = false }
}

async function remove(item: ModelItem) {
  deleting.value = item.id
  try {
    await deleteModel(item.id)
    message.success('模型已删除')
    await load()
  } catch (error) { message.error(error instanceof Error ? error.message : '无法删除模型') }
  finally { deleting.value = undefined }
}

onMounted(load)
</script>

<template>
  <div>
    <header class="page-head">
      <div><h1>模型管理</h1><p>登记模型接口，在步骤配置中选择使用位置。</p></div>
      <a-button v-if="can('MODEL_WRITE')" type="primary" @click="edit()">
        <PlusOutlined aria-hidden="true" /> 添加模型
      </a-button>
    </header>
    <section class="paper-panel">
      <a-alert v-if="loadError" :message="loadError" type="error" show-icon class="form-error">
        <template #action><a-button size="small" @click="load">重试</a-button></template>
      </a-alert>
      <a-table :columns="columns" :data-source="models" :loading="loading" row-key="id" :scroll="{ x: 890 }" :pagination="{ pageSize: 10 }">
        <template #emptyText>暂无自定义模型</template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'protocol'">{{ protocolLabels[(record as ModelItem).protocol] }}</template>
          <span v-else-if="column.key === 'baseUrl' || column.key === 'modelId'" class="mono">{{ record[column.key] }}</span>
          <span v-else-if="column.key === 'key'">{{ record.hasApiKey ? '已配置' : '待配置' }}</span>
          <div v-else-if="column.key === 'actions'" class="compact-actions">
            <a-button size="small" type="link" @click="edit(record as ModelItem)">编辑</a-button>
            <a-popconfirm title="删除这个模型配置？" @confirm="remove(record as ModelItem)">
              <a-button size="small" type="link" danger :loading="deleting === record.id">删除</a-button>
            </a-popconfirm>
          </div>
        </template>
      </a-table>
    </section>
    <a-modal v-model:open="open" :title="selected ? '编辑模型' : '添加模型'" :confirm-loading="busy" ok-text="保存"
      :mask-closable="!busy" :closable="!busy" :cancel-button-props="{ disabled: busy }" @ok="submit" @after-close="form.apiKey = ''">
      <a-form layout="vertical" :model="form" @finish="submit">
        <div class="form-grid">
          <a-form-item label="接口示例">
            <a-select v-model:value="provider" :options="providers" placeholder="选择后填入示例地址" allow-clear @change="applyPreset" />
          </a-form-item>
          <a-form-item label="接口协议" name="protocol" required>
            <a-select v-model:value="form.protocol" :options="Object.entries(protocolLabels).map(([value, label]) => ({ value, label }))" />
          </a-form-item>
          <a-form-item label="显示名称" name="name" required class="span-all"><a-input v-model:value="form.name" :maxlength="100" /></a-form-item>
          <a-form-item label="Base URL" name="baseUrl" required class="span-all" extra="填写服务方的 API 根地址，例如 https://api.openai.com/v1。">
            <a-input v-model:value="form.baseUrl" type="url" :maxlength="512" />
          </a-form-item>
          <a-form-item label="模型 ID" name="modelId" required class="span-all"><a-input v-model:value="form.modelId" :maxlength="180" /></a-form-item>
          <a-form-item label="API Key" name="apiKey" :required="!selected" class="span-all" :extra="selected ? '留空继续使用已配置的密钥。' : undefined">
            <a-input-password v-model:value="form.apiKey" autocomplete="new-password" :maxlength="4096" />
          </a-form-item>
          <template v-if="form.protocol === 'OPENAI_COMPATIBLE'">
            <a-form-item label="最大输出 Token" name="maxTokens"><a-input-number v-model:value="form.maxTokens" :min="1" :precision="0" placeholder="服务方默认" style="width:100%" /></a-form-item>
            <a-form-item label="温度" name="temperature"><a-input-number v-model:value="form.temperature" :min="0" :max="2" :step="0.1" placeholder="服务方默认" style="width:100%" /></a-form-item>
          </template>
        </div>
        <a-alert v-if="editError" :message="editError" type="error" show-icon role="alert" />
      </a-form>
    </a-modal>
  </div>
</template>
