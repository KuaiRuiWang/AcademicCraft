<script setup lang="ts">
import { EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import UserInfo from './UserInfo.vue'
import { formatTime } from '@/utils/time'
import { formatCodeGenType } from '@/utils/codeGenTypes'

const visible = defineModel<boolean>('open', { required: true })
defineProps<{ app?: API.AppVO; showActions?: boolean }>()
const emit = defineEmits<{ edit: []; delete: [] }>()
</script>

<template>
  <a-modal v-model:open="visible" title="应用详情" :footer="null" :width="500">
    <dl class="folio-spec">
      <div>
        <dt>创建者</dt>
        <dd><UserInfo :user="app?.user" size="small" /></dd>
      </div>
      <div>
        <dt>创建时间</dt>
        <dd>{{ formatTime(app?.createTime) || '—' }}</dd>
      </div>
      <div>
        <dt>构建方式</dt>
        <dd>{{ formatCodeGenType(app?.codeGenType) }}</dd>
      </div>
    </dl>
    <div v-if="showActions" class="folio-actions detail-actions">
      <a-button @click="emit('edit')"><EditOutlined aria-hidden="true" /> 应用设置</a-button>
      <a-popconfirm
        title="删除该应用？"
        ok-text="删除"
        cancel-text="取消"
        @confirm="emit('delete')"
      >
        <a-button danger><DeleteOutlined aria-hidden="true" /> 删除应用</a-button>
      </a-popconfirm>
    </div>
  </a-modal>
</template>

<style scoped>
.detail-actions {
  margin-top: 18px;
  justify-content: end;
}
</style>
