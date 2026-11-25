import { CodeGenTypeEnum } from '@/utils/codeGenTypes'

export const workshopUrl = (path: string) =>
  new URL(path, import.meta.env.VITE_WORKSHOP_URL || 'http://localhost:5173').toString()

export function getDeployUrl(deployKey: string, codeGenType?: string) {
  const prefix = codeGenType === CodeGenTypeEnum.FULLSTACK_VUE_NODEJS ? '/apps' : ''
  return workshopUrl(`${prefix}/${encodeURIComponent(deployKey)}/`)
}
