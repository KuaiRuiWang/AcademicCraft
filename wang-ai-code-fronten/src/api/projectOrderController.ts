// @ts-ignore
/* eslint-disable */
import request from '@/request'
import { API_BASE_URL } from '@/config/env'

/** 领取免费模板 POST /projectOrder/create */
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

/** 浏览器直接打开下载链接，避免跨域下载经过 Axios。 */
export function getTemplateDownloadUrl(orderId: number | string) {
  return `${API_BASE_URL.replace(/\/$/, '')}/projectOrder/download/${encodeURIComponent(orderId)}`
}

/** 获取领取记录和下载状态 GET /projectOrder/get/vo */
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

/** 分页获取领取记录与历史订单 POST /projectOrder/my/list/page/vo */
export async function listMyOrders(params?: API.listMyOrdersParams) {
  return request<API.BaseResponsePageProjectOrderVO>('/projectOrder/my/list/page/vo', {
    method: 'POST',
    params,
  })
}
