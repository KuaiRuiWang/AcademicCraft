import { shallowRef } from 'vue'
import { data, sessionKey } from '@/request'

export type AdminIdentity = { id: string; displayName: string; role: string; permissions: string[] }
export const identity = shallowRef<AdminIdentity | null>(null)
let restoring: Promise<void> | undefined

export const can = (permission: string) => identity.value?.permissions.includes(permission) ?? false

export async function restore() {
  if (identity.value || !sessionStorage.getItem(sessionKey)) return
  restoring ??= data<AdminIdentity>('/admin/auth/session')
    .then((admin) => { identity.value = admin })
    .catch(() => { sessionStorage.removeItem(sessionKey) })
    .finally(() => { restoring = undefined })
  await restoring
}

export async function login(username: string, password: string) {
  const result = await data<{ token: string; admin: AdminIdentity }>('/admin/auth/login', 'POST', { username, password })
  sessionStorage.setItem(sessionKey, result.token)
  identity.value = result.admin
}

export function clearSession() {
  sessionStorage.removeItem(sessionKey)
  identity.value = null
}

export async function logout() {
  try {
    await data('/admin/auth/logout', 'POST')
  } finally {
    clearSession()
  }
}
