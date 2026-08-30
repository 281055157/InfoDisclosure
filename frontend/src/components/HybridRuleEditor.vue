<script setup>
const props = defineProps({
  conditionJson: { type: String, default: '{}' },
  promptJson: { type: String, default: '{}' }
})
const emit = defineEmits(['update:conditionJson', 'update:promptJson'])
</script>

<template>
  <div class="grid cols-2">
    <div class="field">
      <label>Hybrid 条件 JSON</label>
      <textarea class="control code-area" :value="props.conditionJson" @input="emit('update:conditionJson', $event.target.value)" />
    </div>
    <div class="field">
      <label>Hybrid 复核 Prompt JSON</label>
      <textarea class="control code-area" :value="props.promptJson" @input="emit('update:promptJson', $event.target.value)" />
    </div>
  </div>
</template>

<style scoped>
.code-area { min-height: 190px; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; }
</style>
