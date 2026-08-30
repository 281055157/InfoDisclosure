<script setup>
import { ref, watch } from 'vue'
import { X } from 'lucide-vue-next'

const props = defineProps({ open: Boolean, issue: Object })
const emit = defineEmits(['close', 'submit'])
const templateComment = '规则或模型判断与正文业务含义不一致，标记为误报。'
const comment = ref(templateComment)

function initialComment(issue) {
  return issue?.falsePositiveFeedback?.comment || templateComment
}

function isMarked(issue) {
  return issue?.falsePositiveStatus === 'MARKED' || issue?.issueStatus === 'FALSE_POSITIVE'
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

watch(() => props.issue, issue => { comment.value = initialComment(issue) })
</script>

<template>
  <div v-if="open" class="modal-backdrop">
    <div class="modal">
      <div class="modal-header">
        <h3>{{ isMarked(issue) ? '修改误报标记' : '标记误报' }}</h3>
        <button class="btn ghost" @click="emit('close')"><X :size="18" /></button>
      </div>
      <div class="panel-body grid">
        <div class="issue" v-if="issue">
          <strong>{{ issue.issueName || issue.issueCode }}</strong>
          <div>{{ issue.explanation || issue.evidenceText }}</div>
        </div>
        <div v-if="issue?.falsePositiveFeedback" class="issue">
          <strong>上次标记信息</strong>
          <div class="muted">
            {{ issue.falsePositiveFeedback.reviewer || 'demo-user' }} ·
            {{ formatTime(issue.falsePositiveFeedback.createdAt) }} ·
            {{ issue.falsePositiveFeedback.processStatus || 'NEW' }}
          </div>
          <div>{{ issue.falsePositiveFeedback.comment || '-' }}</div>
        </div>
        <div class="field">
          <label>误报原因</label>
          <textarea v-model="comment" class="control"></textarea>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn" @click="emit('close')">取消</button>
        <button class="btn primary" @click="emit('submit', comment)">
          {{ isMarked(issue) ? '更新标记' : '确认标记' }}
        </button>
      </div>
    </div>
  </div>
</template>
