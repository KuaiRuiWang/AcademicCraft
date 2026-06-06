<script setup lang="ts">
import { Layout } from 'ant-design-vue'
</script>

<template>
  <Layout.Footer class="global-footer">
    <div class="footer-content">
      百夜原创项目 by 百夜
    </div>
  </Layout.Footer>
</template>

<style scoped>
.global-footer {
  text-align: center;
  background: #f0f2f5;
  padding: 16px 24px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 14px;
  border-top: 1px solid #e8e8e8;
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
}

@media (max-width: 768px) {
  .global-footer {
    padding: 12px 16px;
    font-size: 12px;
  }
}
</style>
