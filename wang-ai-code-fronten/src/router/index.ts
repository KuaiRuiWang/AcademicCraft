import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import CreatePage from '@/pages/CreatePage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserProfilePage from '@/pages/user/UserProfilePage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import AppManagePage from '@/pages/admin/AppManagePage.vue'
import AppEditPage from '@/pages/app/AppEditPage.vue'
import ChatManagePage from '@/pages/admin/ChatManagePage.vue'
import ProjectManagePage from '@/pages/admin/ProjectManagePage.vue'
import CategoryManagePage from '@/pages/admin/CategoryManagePage.vue'
import ProjectMarketPage from '@/pages/project/ProjectMarketPage.vue'
import ProjectDetailPage from '@/pages/project/ProjectDetailPage.vue'
import ProjectEditPage from '@/pages/project/ProjectEditPage.vue'
import MyProjectsPage from '@/pages/project/MyProjectsPage.vue'
import MyOrdersPage from '@/pages/project/MyOrdersPage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior(to, _from, savedPosition) {
    return savedPosition || (to.hash === '#my-apps' ? { el: to.hash, top: 18 } : { top: 0 })
  },
  routes: [
    {
      path: '/',
      name: '主页',
      component: HomePage,
    },
    {
      path: '/create',
      name: '创建应用',
      component: CreatePage,
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: UserLoginPage,
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: UserRegisterPage,
    },
    {
      path: '/user/profile',
      name: '个人中心',
      component: UserProfilePage,
      meta: { requiresLogin: true },
    },
    {
      path: '/admin/userManage',
      name: '用户管理',
      component: UserManagePage,
      meta: { layout: 'admin' },
    },
    {
      path: '/admin/appManage',
      name: '应用管理',
      component: AppManagePage,
      meta: { layout: 'admin' },
    },
    {
      path: '/admin/chatManage',
      name: '对话管理',
      component: ChatManagePage,
      meta: { layout: 'admin' },
    },
    {
      path: '/admin/projectManage',
      name: '模板管理',
      component: ProjectManagePage,
      meta: { layout: 'admin' },
    },
    {
      path: '/admin/categoryManage',
      name: '分类管理',
      component: CategoryManagePage,
      meta: { layout: 'admin' },
    },
    {
      path: '/app/chat/:id',
      name: '应用对话',
      component: () => import('@/pages/app/AppChatPage.vue'),
    },
    {
      path: '/app/edit/:id',
      name: '编辑应用',
      component: AppEditPage,
      meta: { requiresLogin: true },
    },
    // --- 模板库 ---
    {
      path: '/project',
      name: '模板库',
      component: ProjectMarketPage,
    },
    {
      path: '/project/create',
      name: '发布模板',
      component: ProjectEditPage,
      meta: { requiresAdmin: true },
    },
    {
      path: '/project/:id',
      name: '项目详情',
      component: ProjectDetailPage,
    },
    {
      path: '/project/:id/edit',
      name: '编辑项目',
      component: ProjectEditPage,
      meta: { requiresLogin: true },
    },
    // --- 我的 ---
    {
      path: '/my/projects',
      name: '我的项目',
      component: MyProjectsPage,
      meta: { requiresLogin: true },
    },
    {
      path: '/my/orders',
      name: '领取记录',
      component: MyOrdersPage,
      meta: { requiresLogin: true },
    },
  ],
})

export default router
