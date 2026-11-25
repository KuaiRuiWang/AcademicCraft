import { createRouter, createWebHistory } from 'vue-router'
import {
  ApartmentOutlined, AppstoreOutlined, CommentOutlined, DatabaseOutlined,
  FolderOutlined, SafetyCertificateOutlined, TagsOutlined, TeamOutlined,
} from '@ant-design/icons-vue'
import { can, identity, restore } from '@/session'

export const navigation = [
  { path: '/models', title: '模型管理', permission: 'MODEL_READ', icon: DatabaseOutlined, group: '模型配置',
    component: () => import('@/pages/ModelsPage.vue') },
  { path: '/model-bindings', title: '步骤配置', permission: 'BINDING_READ', icon: ApartmentOutlined, group: '模型配置',
    component: () => import('@/pages/ModelBindingsPage.vue') },
  { path: '/users', title: '用户管理', permission: 'USER_READ', icon: TeamOutlined, group: '平台业务',
    component: () => import('@/pages/UserManagePage.vue') },
  { path: '/apps', title: '应用管理', permission: 'APP_READ', icon: AppstoreOutlined, group: '平台业务',
    component: () => import('@/pages/AppManagePage.vue') },
  { path: '/chats', title: '对话管理', permission: 'CHAT_READ', icon: CommentOutlined, group: '平台业务',
    component: () => import('@/pages/ChatManagePage.vue') },
  { path: '/templates', title: '模板管理', permission: 'TEMPLATE_READ', icon: FolderOutlined, group: '平台业务',
    component: () => import('@/pages/ProjectManagePage.vue') },
  { path: '/categories', title: '分类管理', permission: 'CATEGORY_READ', icon: TagsOutlined, group: '平台业务',
    component: () => import('@/pages/CategoryManagePage.vue') },
  { path: '/access', title: '管理员与角色', permission: 'SUPER_ADMIN', icon: SafetyCertificateOutlined, group: '权限',
    component: () => import('@/pages/AccessPage.vue') },
]

export const firstPage = () => navigation.find((item) => can(item.permission))?.path || '/access-denied'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', component: () => import('@/pages/LoginPage.vue') },
    ...navigation.map(({ path, title, permission, component }) => ({ path, component, meta: { title, permission } })),
    { path: '/apps/:id/edit', component: () => import('@/pages/AppEditorPage.vue'), meta: { title: '编辑应用', permission: 'APP_WRITE' } },
    { path: '/templates/create', component: () => import('@/pages/TemplateEditorPage.vue'), meta: { title: '发布模板', permission: 'TEMPLATE_WRITE' } },
    { path: '/templates/:id/edit', component: () => import('@/pages/TemplateEditorPage.vue'), meta: { title: '编辑模板', permission: 'TEMPLATE_WRITE' } },
    { path: '/access-denied', component: () => import('@/pages/AccessDeniedPage.vue'), meta: { title: '权限不足' } },
    { path: '/:pathMatch(.*)*', component: () => import('@/pages/AccessDeniedPage.vue') },
  ],
})

router.beforeEach(async (to) => {
  await restore()
  if (to.path === '/login') return identity.value ? firstPage() : true
  if (!identity.value) return '/login'
  if (!to.meta.title) return firstPage()
  if (typeof to.meta.permission === 'string' && !can(to.meta.permission)) return '/access-denied'
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 应用工坊管理端` : '应用工坊管理端'
})

export default router
