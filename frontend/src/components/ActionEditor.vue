<script setup>
const props = defineProps({
  modelValue: { type: String, default: '{}' }
})
const emit = defineEmits(['update:modelValue'])

function fillExample() {
  emit('update:modelValue', '{"issueType":"CONTENT_LOGIC_CONFLICT","severity":"HIGH","confidence":1.0,"source":"RULE","explanationTemplate":"正文逻辑冲突：${detail}","suggestionTemplate":"请人工核对并修正文案。"}')
}
</script>

<template>
  <div class="field">
    <label>问题输出 JSON</label>
    <textarea class="control code-area" :value="props.modelValue" @input="emit('update:modelValue', $event.target.value)" />
  </div>
  <button class="btn" type="button" @click="fillExample">填入输出示例</button>
</template>

<style scoped>
.code-area { min-height: 160px; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; }
</style>
