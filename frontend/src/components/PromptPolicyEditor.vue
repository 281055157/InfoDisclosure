<script setup>
const props = defineProps({
  modelValue: { type: String, default: '{}' }
})
const emit = defineEmits(['update:modelValue'])

function fillExample() {
  emit('update:modelValue', '{"reviewGoal":"识别正文中除产品细节外的文字逻辑错误。","criteria":"仅当原文自相矛盾且证据明确时判定违规；低置信度不生成问题。","responseFormat":"JSON: {\\"violated\\":true|false,\\"confidence\\":0-1,\\"pageNumber\\":1,\\"evidenceText\\":\\"原文证据\\",\\"explanation\\":\\"说明\\",\\"suggestion\\":\\"建议\\"}"}')
}
</script>

<template>
  <div class="field">
    <label>Prompt Policy JSON</label>
    <textarea class="control code-area" :value="props.modelValue" @input="emit('update:modelValue', $event.target.value)" />
  </div>
  <button class="btn" type="button" @click="fillExample">填入 Prompt 示例</button>
</template>

<style scoped>
.code-area { min-height: 150px; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; }
</style>
