<template>
  <nav class="folio-tabs personal-nav" aria-label="个人区">
    <RouterLink to="/user/profile">个人资料</RouterLink>
    <RouterLink to="/my/projects">我的项目</RouterLink>
    <RouterLink to="/my/orders">领取记录</RouterLink>
  </nav>
</template>

<style scoped>
.personal-nav {
  margin-bottom: 24px;
}
</style>
