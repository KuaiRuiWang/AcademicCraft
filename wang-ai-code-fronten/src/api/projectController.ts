// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 发布项目 登录用户发布一个新项目到商城，支持设置分类、标签、价格、交付方式和介绍图片 POST /project/add */
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

/** 管理员查看项目详情 管理员根据ID查看任意项目的完整信息 GET /project/admin/get/vo */
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

/** 管理员查看所有项目 管理员分页查看全部项目（含所有状态），不受发布状态限制 POST /project/admin/list/page/vo */
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

/** 管理员更新项目 管理员编辑任意项目信息，可用于审核、上下架等操作 POST /project/admin/update */
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

/** 删除项目 项目作者或管理员删除项目（逻辑删除），仅本人或管理员可操作 POST /project/delete */
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

/** 获取项目详情 根据项目ID获取项目完整信息，包含作者信息、分类名称、图片列表，同时自动增加浏览量 GET /project/get/vo */
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

/** 分页浏览项目列表 公开接口，分页查询已发布的项目，支持按分类、技术标签、价格区间筛选，支持排序 POST /project/list/page/vo */
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

/** 我发布的项目列表 登录用户查看自己发布的全部项目（含草稿、已发布、已下架） POST /project/my/list/page/vo */
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

/** 编辑项目 项目作者编辑自己的项目信息，仅本人可操作 POST /project/update */
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
