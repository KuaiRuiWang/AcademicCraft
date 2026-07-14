// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /project/add */
export async function addProject(body: API.ProjectAddRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseLong>('/project/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /project/admin/get/vo */
export async function getProjectVoByIdByAdmin(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getProjectVOByIdByAdminParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseProjectVO>('/project/admin/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /project/admin/list/page/vo */
export async function listProjectVoByPageByAdmin(
  body: API.ProjectQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageProjectVO>('/project/admin/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /project/admin/update */
export async function updateProjectByAdmin(
  body: API.ProjectUpdateRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/project/admin/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /project/delete */
export async function deleteProject(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/project/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /project/get/vo */
export async function getProjectVoById(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getProjectVOByIdParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseProjectVO>('/project/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /project/list/page/vo */
export async function listProjectVoByPage(
  body: API.ProjectQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageProjectVO>('/project/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /project/my/list/page/vo */
export async function listMyProjectVoByPage(
  body: API.ProjectQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageProjectVO>('/project/my/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /project/update */
export async function updateProject(
  body: API.ProjectUpdateRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/project/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
