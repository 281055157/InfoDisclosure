<script setup>
const props = defineProps({
  modelValue: { type: String, default: 'JAVA_PLUGIN' },
  schemas: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['update:modelValue'])

const templates = [
  ['REGEX', '正则匹配', 'RE2/J 安全正则，适合字段或关键词定位'],
  ['REQUIRED', '必填/禁用', '检查关键文本必须出现或不得出现'],
  ['ENUM_MAPPING', '枚举映射', '检查 R1-R5 等编号与名称映射'],
  ['NUMERIC_RANGE', '数值范围', '抽取数值并检查上下限'],
  ['LLM_POLICY', 'LLM 策略', '把动态规则注入大模型策略'],
  ['HYBRID', '混合复核', '硬规则定位候选后由 LLM 复核'],
  ['JAVA_PLUGIN', 'Java 插件', '复用内置基础设施规则']
]
</script>

<template>
  <div class="template-grid">
    <button
      v-for="[value, title, desc] in templates"
      :key="value"
      type="button"
      class="template-card"
      :class="{ active: props.modelValue === value }"
      @click="emit('update:modelValue', value)"
    >
      <strong>{{ title }}</strong>
      <span>{{ value }}</span>
      <small>{{ desc }}</small>
    </button>
  </div>
  <details v-if="Object.keys(props.schemas || {}).length" class="llm-debug">
    <summary>执行器 schema</summary>
    <pre>{{ JSON.stringify(props.schemas[props.modelValue] || {}, null, 2) }}</pre>
  </details>
</template>

<style scoped>
.template-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.template-card { text-align: left; display: grid; gap: 4px; min-height: 104px; border: 1px solid var(--line); border-radius: 8px; padding: 12px; background: white; }
.template-card.active { border-color: var(--primary); box-shadow: inset 0 0 0 1px var(--primary); }
.template-card strong { font-size: 14px; }
.template-card span { color: var(--primary); font-size: 12px; font-weight: 700; }
.template-card small { color: var(--muted); line-height: 1.5; }
@media (max-width: 980px) { .template-grid { grid-template-columns: 1fr; } }
</style>
