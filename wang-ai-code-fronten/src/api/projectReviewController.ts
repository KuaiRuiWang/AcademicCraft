// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /projectReview/add */
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

/** 此处后端没有提供注释 GET /projectReview/list/vo */
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
