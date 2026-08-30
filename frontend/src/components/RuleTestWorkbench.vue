<script setup>
import { Play } from 'lucide-vue-next'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  result: { type: Object, default: null },
  error: { type: String, default: '' },
  running: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'run'])

function setField(key, value) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
</script>

<template>
  <div class="grid cols-2">
    <div class="field">
      <label>文件类别</label>
      <select class="control" :value="props.modelValue.documentCategory || 'PROTOCOL'" @input="setField('documentCategory', $event.target.value)">
        <option>PROTOCOL</option>
        <option>ANNOUNCEMENT</option>
        <option>AUTO</option>
      </select>
    </div>
    <div class="field">
      <label>候选文件类型</label>
      <input class="control" :value="props.modelValue.documentType || 'CUSTOMER_RIGHTS_NOTICE'" @input="setField('documentType', $event.target.value)">
    </div>
    <div class="field">
      <label>声明产品代码</label>
      <input class="control" :value="props.modelValue.declaredProductCode || ''" @input="setField('declaredProductCode', $event.target.value)">
    </div>
    <div class="field">
      <label>B9公告类型</label>
      <input class="control" :value="props.modelValue.b9Value || ''" @input="setField('b9Value', $event.target.value)">
    </div>
    <div class="field" style="grid-column:1/-1">
      <label>测试正文</label>
      <textarea class="control test-text" :value="props.modelValue.testText || ''" @input="setField('testText', $event.target.value)" />
    </div>
  </div>
  <button class="btn primary" type="button" :disabled="props.running" @click="emit('run')">
    <Play :size="16" />{{ props.running ? '测试中' : '运行测试' }}
  </button>
  <div v-if="props.error" class="test-error">
    <strong>测试失败</strong>
    <span>{{ props.error }}</span>
  </div>
  <details v-if="props.result" class="llm-debug" open>
    <summary>测试结果：{{ props.result.status }} / 问题 {{ props.result.issueCount }} / {{ props.result.matched ? '命中' : '未命中' }}</summary>
    <div v-if="props.result.detail" class="test-detail">{{ props.result.detail }}</div>
    <pre>{{ JSON.stringify(props.result, null, 2) }}</pre>
  </details>
</template>

<style scoped>
.test-text { min-height: 180px; }
.test-error { margin-top: 10px; display: grid; gap: 4px; border: 1px solid #fecaca; background: #fef2f2; color: var(--red); border-radius: 8px; padding: 10px 12px; }
.test-detail { margin-top: 10px; padding: 8px 10px; border-radius: 6px; background: #f9fafb; border: 1px solid var(--line); overflow-wrap: anywhere; }
</style>
