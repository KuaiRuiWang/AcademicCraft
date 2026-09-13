<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <div class="footer-brand">百夜毕设帮</div>
      <p class="copyright">© {{ new Date().getFullYear() }} 百夜毕设帮 · 不写一行代码，生成计算机毕设</p>
      <div class="footer-links">
        <RouterLink to="/user/login">隐私政策</RouterLink>
        <span class="dot">·</span>
        <RouterLink to="/user/login">服务条款</RouterLink>
      </div>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
// 无需额外的响应式数据
</script>

<style scoped>
.footer {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(10px);
  text-align: center;
  padding: 32px 24px;
  margin-top: 48px;
  border-top: 1px solid var(--color-border);
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.footer-brand {
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--color-text);
}

.copyright {
  margin: 0;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

.footer-links {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.footer-links a {
  color: var(--color-text-tertiary);
}

.footer-links a:hover {
  color: var(--color-primary);
}

.dot {
  color: var(--color-border);
}
</style>
