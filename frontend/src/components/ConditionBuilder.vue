<script setup>
const props = defineProps({
  executorType: { type: String, default: 'JAVA_PLUGIN' },
  modelValue: { type: String, default: '{}' }
})
const emit = defineEmits(['update:modelValue'])

const examples = {
  REGEX: '{"pattern":"产品代码[:：]?\\\\s*([A-Za-z0-9-]{4,20})","maxPatternLength":500,"maxInputLength":50000,"maxMatches":20,"contextRadius":80}',
  REQUIRED: '{"mode":"MUST_APPEAR","values":["产品代码","风险等级"]}',
  ENUM_MAPPING: '{"headerPattern":"(?:风险程度|风险等级)[^.;。；]{0,120}(?:从低到高|由低到高)(?:分为|包括)五级","entryPattern":"(低风险|中低风险|中风险|中高风险|高风险)产品?\\\\(R([1-5])\\\\)","labelGroup":1,"codeGroup":2,"expectedMapping":{"R1":"低风险","R2":"中低风险","R3":"中风险","R4":"中高风险","R5":"高风险"},"checkDuplicates":true,"checkMissing":true,"checkOrder":true}',
  NUMERIC_RANGE: '{"pattern":"费率[:：]?\\\\s*([0-9.]+)%","valueGroup":1,"min":0,"max":5}',
  LLM_POLICY: '{"minConfidence":0.75}',
  HYBRID: '{"locator":"POSSIBLE_TEMPLATE_RESIDUE","minConfidence":0.85}',
  JAVA_PLUGIN: '{"pluginCode":"PRODUCT_CODE_EXTRACTION"}'
}

function fillExample() {
  emit('update:modelValue', examples[props.executorType] || '{}')
}
</script>

<template>
  <div class="field">
    <label>条件 JSON</label>
    <textarea class="control code-area" :value="props.modelValue" @input="emit('update:modelValue', $event.target.value)" />
  </div>
  <button class="btn" type="button" @click="fillExample">填入当前模板示例</button>
</template>

<style scoped>
.code-area { min-height: 180px; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; }
</style>
