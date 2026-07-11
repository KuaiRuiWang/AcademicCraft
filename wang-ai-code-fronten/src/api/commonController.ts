// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 上传文件 POST /common/upload */
export async function upload(file: File, options?: { [key: string]: any }) {
  const formData = new FormData()
  formData.append('file', file)
  return request<API.BaseResponseString>('/common/upload', {
    method: 'POST',
    data: formData,
    ...(options || {}),
  })
}
