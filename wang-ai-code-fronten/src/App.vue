<script setup lang="ts">
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { themeConfig } from '@/config/theme'
</script>

<template>
  <a-config-provider :theme="themeConfig" :locale="zhCN" :auto-insert-space-in-button="false">
    <BasicLayout />
  </a-config-provider>
</template>
