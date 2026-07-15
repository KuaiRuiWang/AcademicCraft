// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 添加项目分类（管理员） 管理员新增一个项目分类 POST /projectCategory/admin/add */
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

/** 删除项目分类（管理员） 管理员根据ID逻辑删除一个项目分类 POST /projectCategory/admin/delete */
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

/** 更新项目分类（管理员） 管理员更新一个项目分类的名称、图标或排序 POST /projectCategory/admin/update */
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

/** 获取全部分类列表 按排序权重升序返回所有未删除的项目分类 GET /projectCategory/list */
export async function listCategories(options?: { [key: string]: any }) {
  return request<API.BaseResponseListProjectCategory>('/projectCategory/list', {
    method: 'GET',
    ...(options || {}),
  })
}
