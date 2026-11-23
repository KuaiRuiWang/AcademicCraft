<script setup lang="ts">
import { computed } from 'vue'
import { CopyOutlined, ExportOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'

const visible = defineModel<boolean>('open', { required: true })
const props = defineProps<{ deployUrl: string }>()
const emit = defineEmits<{ 'open-site': [] }>()
const shareUrl = computed(() =>
  props.deployUrl ? new URL(props.deployUrl, window.location.origin).href : '',
)

async function copyUrl() {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    message.success('部署链接已复制')
  } catch {
    message.error('无法访问剪贴板，可手动选择并复制链接')
  }
}
</script>

<template>
  <a-modal v-model:open="visible" title="应用已部署" :footer="null" :width="560">
    <label for="public-deploy-url">部署链接</label>
    <a-input id="public-deploy-url" :value="shareUrl" readonly class="deploy-url" />
    <div class="folio-actions deploy-actions">
      <a-button @click="copyUrl"><CopyOutlined aria-hidden="true" /> 复制链接</a-button>
      <a-button type="primary" @click="emit('open-site')">
        访问网站 <ExportOutlined aria-hidden="true" />
      </a-button>
    </div>
  </a-modal>
</template>

<style scoped>
.deploy-url {
  margin: 8px 0 18px;
}

.deploy-actions {
  justify-content: end;
}
</style>
