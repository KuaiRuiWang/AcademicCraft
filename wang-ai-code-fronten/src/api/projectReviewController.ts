// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 发表项目评价 购买者对已购项目发表1-5星评价，需提供有效订单ID，同一订单只能评价一次 POST /projectReview/add */
export async function addReview(
  body: API.ProjectReviewAddRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseLong>('/projectReview/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 查看项目评价列表 公开接口，获取指定项目的所有评价，按创建时间倒序排列 GET /projectReview/list/vo */
export async function listReviews(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listReviewsParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListProjectReviewVO>('/projectReview/list/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
