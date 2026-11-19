import request from '@/request'

export type ThesisChapter = {
  chapterNo: number
  title: string
  content?: string | null
  status: 'pending' | 'generating' | 'ready' | 'failed'
  userEdited: number
  sourceVersion?: string
  errorMessage?: string
}

export type Thesis = {
  appId: string | number
  title: string
  status: 'waiting' | 'pending' | 'generating' | 'ready' | 'failed' | 'stale'
  stage: string
  revision?: string
  sourceVersion?: string
  errorMessage?: string
  chapters: ThesisChapter[]
}

type Response<T> = { code: number; data: T; message?: string }
const path = (appId: string) => `/app/${encodeURIComponent(appId)}/thesis`

export const getThesis = (appId: string) => request.get<Response<Thesis>>(path(appId))
export const generateThesis = (appId: string) =>
  request.post<Response<boolean>>(`${path(appId)}/generate`)
export const saveThesisChapter = (
  appId: string,
  chapterNo: number,
  input: {
    content: string
    originalContent: string | null
    revision?: string
  },
) => request.put<Response<boolean>>(`${path(appId)}/chapters/${chapterNo}`, input)
export const exportThesis = (appId: string) =>
  request.get<Blob>(`${path(appId)}/export`, {
    responseType: 'blob',
    timeout: 200000,
  })
