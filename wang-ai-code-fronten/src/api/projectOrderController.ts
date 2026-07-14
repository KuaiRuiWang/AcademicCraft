// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /projectOrder/create */
export async function createOrder(
  body: API.ProjectOrderCreateRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseLong>('/projectOrder/create', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /projectOrder/download/${param0} */
export async function downloadProject(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.downloadProjectParams,
  options?: { [key: string]: any }
) {
  const { orderId: param0, ...queryParams } = params
  return request<any>(`/projectOrder/download/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /projectOrder/get/vo */
export async function getOrderVoById(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getOrderVOByIdParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseProjectOrderVO>('/projectOrder/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /projectOrder/my/list/page/vo */
export async function listMyOrders(options?: { [key: string]: any }) {
  return request<API.BaseResponsePageProjectOrderVO>('/projectOrder/my/list/page/vo', {
    method: 'POST',
    ...(options || {}),
  })
}
