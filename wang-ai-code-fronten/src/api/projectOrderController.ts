// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 购买项目 登录用户购买指定项目，不能购买自己的项目，同一项目不可重复购买。免费项目直接完成，付费项目待支付接入 POST /projectOrder/create */
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

/** 下载已购项目源码 买家下载已支付订单对应的项目源码，根据交付方式重定向到下载地址 GET /projectOrder/download/${param0} */
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

/** 获取订单详情 买家查看自己的订单详情，包含项目源码下载地址，仅买家本人可查看 GET /projectOrder/get/vo */
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

/** 我的购买记录 登录用户查看自己的所有购买订单 POST /projectOrder/my/list/page/vo */
export async function listMyOrders(options?: { [key: string]: any }) {
  return request<API.BaseResponsePageProjectOrderVO>('/projectOrder/my/list/page/vo', {
    method: 'POST',
    ...(options || {}),
  })
}
