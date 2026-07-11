// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /health/ */
export async function health(options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/health/', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /health/redis */
export async function redis(options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/health/redis', {
    method: 'GET',
    ...(options || {}),
  })
}
