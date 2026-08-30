<script setup>
const props = defineProps({ value: [String, Number], type: { type: String, default: 'status' } })

const maps = {
  status: {
    CREATED: ['已创建', 'gray'],
    FILE_STORED: ['已入队', 'gray'],
    PARSING: ['解析中', 'purple'],
    RULE_REVIEWING: ['规则审核', 'purple'],
    LLM_REVIEWING: ['模型审核', 'purple'],
    EVIDENCE_VERIFYING: ['证据回查', 'purple'],
    RESULT_MERGING: ['结果合并', 'purple'],
    WAITING_MANUAL_REVIEW: ['待人工审核', 'orange'],
    PARTIAL_SUCCESS: ['部分成功', 'orange'],
    FAILED: ['失败', 'red'],
    MANUAL_APPROVED: ['人工通过', 'green'],
    MANUAL_APPROVED_WITH_WARNING: ['带提示通过', 'orange'],
    MANUAL_RETURNED: ['退回修改', 'red'],
    MANUAL_REJECTED: ['拒绝', 'red']
  },
  risk: {
    LOW: ['低风险', 'green'],
    MEDIUM: ['中风险', 'orange'],
    HIGH: ['高风险', 'red'],
    UNKNOWN: ['未知', 'gray']
  },
  tech: {
    SUCCESS: ['成功', 'green'],
    PARTIAL_SUCCESS: ['部分成功', 'orange'],
    LLM_FAILED: ['模型失败', 'gray'],
    LLM_CALL_FAILED: ['模型调用失败', 'gray'],
    PDF_PARSE_FAILED: ['PDF失败', 'red'],
    EXCEL_PARSE_FAILED: ['Excel失败', 'orange'],
    UNKNOWN_ERROR: ['未知错误', 'red']
  },
  severity: {
    LOW: ['LOW', 'green'],
    MEDIUM: ['MEDIUM', 'orange'],
    HIGH: ['HIGH', 'red']
  }
}

function pair() {
  const map = maps[props.type] || {}
  return map[props.value] || [props.value || '-', 'gray']
}
</script>

<template>
  <span class="badge" :class="pair()[1]">{{ pair()[0] }}</span>
</template>
