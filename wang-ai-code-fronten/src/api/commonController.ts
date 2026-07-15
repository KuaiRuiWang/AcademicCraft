// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /common/upload */
export async function upload(body: {}, options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/common/upload', {
    method: 'POST',
    data: body,
    ...(options || {}),
  })
}
