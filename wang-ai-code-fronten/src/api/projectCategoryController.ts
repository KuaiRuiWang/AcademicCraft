// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /projectCategory/admin/add */
export async function addCategory(body: API.ProjectCategory, options?: { [key: string]: any }) {
  return request<API.BaseResponseLong>('/projectCategory/admin/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /projectCategory/admin/delete */
export async function deleteCategory(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/projectCategory/admin/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /projectCategory/admin/update */
export async function updateCategory(body: API.ProjectCategory, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/projectCategory/admin/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /projectCategory/list */
export async function listCategories(options?: { [key: string]: any }) {
  return request<API.BaseResponseListProjectCategory>('/projectCategory/list', {
    method: 'GET',
    ...(options || {}),
  })
}
