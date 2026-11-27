import request from '@/request'

export const documentKinds = [
  { key: 'assignment', label: '任务书' },
  { key: 'proposal', label: '开题报告' },
] as const

export type DocumentKind = (typeof documentKinds)[number]['key']
export interface GraduationDocument {
  status: 'waiting' | 'generating' | 'ready' | 'failed'
  error?: string
  templateId?: string
  templateName?: string
  templateFields: string[]
  defaultFields: string[]
  resultTemplateName?: string
  sections: Record<string, string>
}

const path = (appId: string, kind: DocumentKind) =>
  `/app/${encodeURIComponent(appId)}/graduation-documents/${kind}`
type Envelope<T> = { code: number; data: T; message?: string }
function unwrap<T>({ data }: { data: Envelope<T> }) {
  if (data.code !== 0) throw new Error(data.message || '请求失败')
  return data.data
}

export const readDocument = (appId: string, kind: DocumentKind) =>
  request.get<Envelope<GraduationDocument>>(path(appId, kind)).then(unwrap)

export function importDocumentTemplate(appId: string, kind: DocumentKind, file: File) {
  return request.post<Envelope<boolean>>(`${path(appId, kind)}/template`, file, {
    params: { filename: file.name },
    headers: { 'Content-Type': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' },
  }).then(unwrap)
}

export const generateDocument = (appId: string, kind: DocumentKind, templateId: string) =>
  request.post<Envelope<boolean>>(`${path(appId, kind)}/generate`, null, { params: { templateId } }).then(unwrap)

export async function downloadDocument(appId: string, kind: DocumentKind) {
  const { data } = await request.get<Blob>(`${path(appId, kind)}/export`, { responseType: 'blob' })
  if (data.type.includes('json')) throw new Error(JSON.parse(await data.text()).message || '下载失败')
  return data
}
