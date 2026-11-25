import request from '@/request'

type Result<T> = { code: number; data?: T; message?: string }
type Page<T> = { records?: T[]; totalRow?: number }
type Id = { id?: number }
type PageQuery = { pageNum?: number; pageSize?: number }

const get = <T>(url: string, params?: object) => request<Result<T>>(url, { method: 'GET', params })
const post = <T>(url: string, body: unknown) => request<Result<T>>(url, { method: 'POST', data: body })

export const listUserVoByPage = (input: PageQuery & { userAccount?: string; userName?: string }) =>
  post<Page<API.UserVO>>('/user/list/page/vo', input)
export const deleteUser = (input: Id) => post<boolean>('/user/delete', input)
export const getAppVoByIdByAdmin = (input: Id) => get<API.AppVO>('/app/admin/get/vo', input)
export const listAppVoByPageByAdmin = (input: API.AppQueryRequest) => post<Page<API.AppVO>>('/app/admin/list/page/vo', input)
export const deleteAppByAdmin = (input: Id) => post<boolean>('/app/admin/delete', input)
export const updateAppByAdmin = (input: Id & { appName?: string; cover?: string; priority?: number }) =>
  post<boolean>('/app/admin/update', input)
export const listAllChatHistoryByPageForAdmin = (input: API.ChatHistoryQueryRequest) =>
  post<Page<API.ChatHistory>>('/chatHistory/admin/list/page/vo', input)

export const listProjectVoByPageByAdmin = (input: API.ProjectQueryRequest) =>
  post<Page<API.ProjectVO>>('/project/admin/list/page/vo', input)
export const getProjectVoByIdByAdmin = (input: Id) => get<API.ProjectVO>('/project/admin/get/vo', input)
export const addProject = (input: API.ProjectAddRequest) => post<number>('/project/add', input)
export const updateProjectByAdmin = (input: API.ProjectAddRequest & Id & { status?: number }) =>
  post<boolean>('/project/admin/update', input)
export const deleteProject = (input: Id) => post<boolean>('/project/admin/delete', input)

export const listCategories = () => get<API.ProjectCategory[]>('/projectCategory/list')
export const addCategory = (input: API.ProjectCategory) => post<number>('/projectCategory/admin/add', input)
export const updateCategory = (input: API.ProjectCategory) => post<boolean>('/projectCategory/admin/update', input)
export const deleteCategory = (input: Id) => post<boolean>('/projectCategory/admin/delete', input)
export const upload = (input: FormData) => post<string>('/common/upload', input)
