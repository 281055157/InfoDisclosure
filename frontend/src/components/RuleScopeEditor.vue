<script setup>
const props = defineProps({
  modelValue: { type: String, default: '{}' }
})
const emit = defineEmits(['update:modelValue'])

function update(value) {
  emit('update:modelValue', value)
}
</script>

<template>
  <div class="grid cols-2">
    <div class="field" style="grid-column:1/-1">
      <label>适用范围 JSON</label>
      <textarea
        class="control"
        :value="props.modelValue"
        placeholder='{"documentCategories":["PROTOCOL"],"documentTypes":["CUSTOMER_RIGHTS_NOTICE"],"productCodes":[],"productTypes":[]}'
        @input="update($event.target.value)"
      />
    </div>
    <div class="llm-note">
      <strong>文件类别</strong>
      <p class="muted">协议传 `PROTOCOL`；历史兼容别名 `AGREEMENT` 后端会归一。公告传 `ANNOUNCEMENT`。</p>
    </div>
    <div class="llm-note">
      <strong>文件类型</strong>
      <p class="muted">支持枚举名或中文类型，例如 `RISK_DISCLOSURE`、`风险揭示书`。</p>
    </div>
  </div>
</template>
