<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { bindModel, getBindings, protocolLabels, type ModelItem, type ModelStep } from '@/api/models'
import { can } from '@/session'

const steps = ref<ModelStep[]>([])
const models = ref<Pick<ModelItem, 'id' | 'name' | 'protocol'>[]>([])
const choice = reactive<Record<string, string>>({})
const saving = reactive<Record<string, boolean>>({})
const loading = ref(false)
const error = ref('')
const groups = computed(() => [...new Set(steps.value.map((step) => step.group))].map((name) => ({
  name, steps: steps.value.filter((step) => step.group === name),
})))

function options(step: ModelStep) {
  return [
    { value: '', label: step.defaultModel ? `部署默认（${step.defaultModel}）` : '部署默认' },
    ...models.value.filter((model) => model.protocol === step.protocol).map((model) => ({ value: model.id, label: model.name })),
  ]
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await getBindings()
    models.value = result.models
    steps.value = result.steps
    for (const step of result.steps) choice[step.step] = step.modelConfigId ?? ''
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '读取步骤配置失败' }
  finally { loading.value = false }
}

async function save(step: ModelStep) {
  saving[step.step] = true
  const selection = choice[step.step] || null
  try {
    await bindModel(step.step, selection)
    step.modelConfigId = selection
    message.success(`${step.label}已保存`)
  } catch (reason) { message.error(reason instanceof Error ? reason.message : '保存失败') }
  finally { saving[step.step] = false }
}

onMounted(load)
</script>

<template>
  <div>
    <header class="page-head"><div><h1>步骤配置</h1><p>每个步骤独立选择模型，保存后生效于下一次运行该步骤。</p></div></header>
    <a-alert v-if="error" :message="error" type="error" show-icon class="form-error">
      <template #action><a-button size="small" @click="load">重试</a-button></template>
    </a-alert>
    <a-skeleton v-if="loading" active />
    <section v-for="group in groups" :key="group.name" class="paper-panel">
      <h2 class="section-title">{{ group.name }}</h2>
      <div v-for="step in group.steps" :key="step.step" class="binding-row">
        <label :for="`binding-${step.step}`">
          {{ step.label }} <small>{{ protocolLabels[step.protocol] }}</small>
        </label>
        <a-select :id="`binding-${step.step}`" :aria-label="step.label" v-model:value="choice[step.step]"
          :options="options(step)" :disabled="!can('BINDING_WRITE') || saving[step.step]" />
        <a-button v-if="can('BINDING_WRITE')" :aria-label="`保存${step.label}`" :loading="saving[step.step]"
          :disabled="choice[step.step] === (step.modelConfigId ?? '')" @click="save(step)">保存</a-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.section-title { font: 700 19px var(--font-display); margin: 0 0 8px; }
.binding-row { display: grid; grid-template-columns: minmax(145px, 1fr) minmax(220px, 2fr) 64px; align-items: center; gap: 9px; padding: 9px 0; }
.binding-row + .binding-row { border-top: 1px solid var(--color-border); }
.binding-row small { display: block; color: var(--color-text-secondary); }
@media (max-width: 560px) {
  .binding-row { grid-template-columns: minmax(0, 1fr) 60px; }
  .binding-row label { grid-column: 1 / -1; }
}
</style>
