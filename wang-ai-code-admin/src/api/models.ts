import { data } from '@/request'

export type ModelProtocol = 'OPENAI_COMPATIBLE' | 'DASHSCOPE_IMAGE'
export const protocolLabels: Record<ModelProtocol, string> = {
  OPENAI_COMPATIBLE: 'OpenAI 兼容对话',
  DASHSCOPE_IMAGE: 'DashScope 图像',
}

export type ModelItem = {
  id: string
  name: string
  protocol: ModelProtocol
  baseUrl: string
  modelId: string
  hasApiKey: boolean
  maxTokens?: number
  temperature?: number
}
export type ModelInput = Omit<ModelItem, 'id' | 'hasApiKey'> & { apiKey?: string }
export type ModelStep = {
  step: string
  label: string
  group: string
  protocol: ModelProtocol
  modelConfigId: string | null
  defaultModel: string
}
type Bindings = { steps: ModelStep[]; models: Pick<ModelItem, 'id' | 'name' | 'protocol'>[] }

export const listModels = () => data<ModelItem[]>('/admin/models')
export const saveModel = (input: ModelInput, id?: string) =>
  data<ModelItem>(id ? `/admin/models/${id}` : '/admin/models', id ? 'PUT' : 'POST', input)
export const deleteModel = (id: string) => data(`/admin/models/${id}`, 'DELETE')
export const getBindings = () => data<Bindings>('/admin/model-bindings')
export const bindModel = (step: string, modelConfigId: string | null) =>
  data(`/admin/model-bindings/${step}`, 'PUT', { modelConfigId })
