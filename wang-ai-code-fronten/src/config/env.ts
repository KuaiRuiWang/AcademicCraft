/**
 * 环境变量配置
 */
import {CodeGenTypeEnum} from "@/utils/codeGenTypes.ts";

// API 基础地址
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'

// 静态资源地址
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

// 获取部署应用的访问地址（相对路径，跟随当前站点域名）
// 全栈应用由 Nginx 反代到 /apps/{deployKey}/，普通应用直接放在 Nginx 根目录
export const getDeployUrl = (deployKey: string, codeGenType?: string) => {
  const prefix = codeGenType === CodeGenTypeEnum.FULLSTACK_VUE_NODEJS ? '/apps' : ''
  return `${prefix}/${deployKey}/`
}

// 将后端返回的绝对部署地址转为相对路径，避免写死 localhost 导致链接不可访问
export const toRelativeDeployUrl = (url: string) => {
  if (!url) {
    return ''
  }
  try {
    const parsed = new URL(url, window.location.origin)
    return `${parsed.pathname}${parsed.search}`
  } catch {
    return url
  }
}

// 获取静态资源预览URL
export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType}_${appId}/`
  // 如果是 Vue 项目，浏览地址需要添加 dist 后缀
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}dist/index.html`
  }
  return baseUrl
}
