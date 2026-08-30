<script setup>
import { AlertTriangle, CheckCircle2 } from 'lucide-vue-next'
import StatusBadge from './StatusBadge.vue'

defineProps({ issues: { type: Array, default: () => [] }, taskId: [String, Number] })
const emit = defineEmits(['false-positive'])

function falsePositiveState(issue) {
  if (!issue) return 'UNMARKED'
  if (issue.falsePositiveStatus) return issue.falsePositiveStatus
  return issue.issueStatus === 'FALSE_POSITIVE' ? 'MARKED' : 'UNMARKED'
}

function falsePositiveLabel(issue) {
  const state = falsePositiveState(issue)
  if (state === 'PROCESSED') return '已处理'
  if (state === 'MARKED') return '已标记'
  return '标记误报'
}

function canMarkFalsePositive(issue) {
  return !!issue?.issueId && falsePositiveState(issue) !== 'PROCESSED'
}

function markBadgeClass(issue) {
  return falsePositiveState(issue) === 'PROCESSED' ? 'green' : 'orange'
}
</script>

<template>
  <div v-if="!issues.length" class="empty">暂无问题</div>
  <article v-for="issue in issues" :key="issue.issueId || issue.issueCode + issue.evidenceText" class="issue">
    <div class="issue-title">
      <strong><AlertTriangle :size="16" /> {{ issue.issueName || issue.issueCode || issue.issueType }}</strong>
      <StatusBadge :value="issue.severity" type="severity" />
    </div>
    <div class="muted">置信度：{{ Math.round((issue.confidence || 0) * 100) }}% · 页码：{{ issue.pageNumber || '-' }} · 来源：{{ issue.sourceType || issue.source || '-' }}</div>
    <div>{{ issue.explanation || issue.evidenceText || '-' }}</div>
    <div class="muted">建议：{{ issue.suggestion || '-' }}</div>
    <div v-if="issue.evidenceText">证据：{{ issue.evidenceText }}</div>
    <div style="display:flex;justify-content:space-between;gap:10px;align-items:center">
      <div style="display:flex;gap:8px;align-items:center">
        <span class="muted"><CheckCircle2 :size="14" /> {{ issue.evidenceVerified || issue.verified ? '已验证' : '未验证' }}</span>
        <span v-if="falsePositiveState(issue) !== 'UNMARKED'" class="badge" :class="markBadgeClass(issue)">
          {{ falsePositiveLabel(issue) }}
        </span>
      </div>
      <button
        v-if="issue.issueId"
        class="btn"
        :disabled="!canMarkFalsePositive(issue)"
        @click="emit('false-positive', issue)"
      >
        {{ falsePositiveLabel(issue) }}
      </button>
    </div>
  </article>
</template>
