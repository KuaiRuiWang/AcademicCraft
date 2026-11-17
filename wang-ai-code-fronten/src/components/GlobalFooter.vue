<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <div>
        <RouterLink to="/" class="footer-brand"
          >百夜毕设帮<span class="brand-dot" aria-hidden="true"></span
        ></RouterLink>
        <p class="copyright">
          © {{ new Date().getFullYear() }} 百夜毕设帮 · 不写一行代码，生成计算机毕设
        </p>
      </div>
      <nav class="footer-links" aria-label="页脚导航">
        <RouterLink to="/project">项目商城</RouterLink>
        <RouterLink to="/create">AI 定制</RouterLink>
        <RouterLink to="/user/profile">个人中心</RouterLink>
      </nav>
    </div>
  </a-layout-footer>
</template>

<style scoped>
.footer {
  padding: 36px var(--page-gutter);
  background: var(--color-bg);
  border-top: 1px solid var(--color-border);
}
.footer-content {
  max-width: var(--page-width);
  margin-inline: auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
}
.footer-brand {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 17px;
  font-weight: 700;
  color: var(--color-text);
}
.brand-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--color-primary);
}
.copyright {
  margin: 6px 0 0;
  color: var(--color-text-tertiary);
  font-size: 12px;
}
.footer-links {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
}
.footer-links a {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.footer-links a:hover {
  color: var(--color-primary);
}
@media (max-width: 600px) {
  .footer {
    padding-block: 28px;
  }
  .footer-content {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
  .footer-links {
    gap: 24px;
  }
}
</style>
