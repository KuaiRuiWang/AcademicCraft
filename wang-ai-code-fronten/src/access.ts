import { useLoginUserStore } from '@/stores/loginUser'
import { message } from 'ant-design-vue'
import router from '@/router'

// 是否为首次获取登录用户
let firstFetchLoginUser = true

/**
 * 全局权限校验
 */
router.beforeEach(async (to) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser
  // 确保页面刷新，首次加载时，能够等后端返回用户信息后再校验权限
  if (firstFetchLoginUser) {
    try {
      await loginUserStore.fetchLoginUser()
    } catch {
      // 登录态接口暂不可用时允许浏览公共页面，管理端仍由下方权限检查拦截。
      console.warn('暂时无法获取登录状态，按访客模式继续访问')
    }
    loginUser = loginUserStore.loginUser
    firstFetchLoginUser = false
  }
  const requiresAdmin = to.meta.layout === 'admin' || to.meta.requiresAdmin === true
  if (!loginUser.id && (to.meta.requiresLogin || requiresAdmin)) {
    return { path: '/user/login', query: { redirect: to.fullPath } }
  }
  if (requiresAdmin && loginUser.userRole !== 'admin') {
    message.error('此页面仅对管理员开放')
    return { path: to.meta.layout === 'admin' ? '/' : '/my/projects' }
  }
})
