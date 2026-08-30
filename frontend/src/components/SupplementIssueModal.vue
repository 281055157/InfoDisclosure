<script setup>
import { reactive } from 'vue'
import { X } from 'lucide-vue-next'

defineProps({ open: Boolean })
const emit = defineEmits(['close', 'submit'])
const form = reactive({
  issueType: 'CONTENT_LOGIC_CONFLICT',
  severity: 'MEDIUM',
  confidence: 1,
  pageNumber: null,
  evidenceText: '',
  explanation: '',
  suggestion: ''
})
</script>

<template>
  <div v-if="open" class="modal-backdrop">
    <div class="modal">
      <div class="modal-header">
        <h3>补充人工问题</h3>
        <button class="btn ghost" @click="emit('close')"><X :size="18" /></button>
      </div>
      <div class="panel-body grid cols-2">
        <div class="field"><label>问题类型</label><input v-model="form.issueType" class="control"></div>
        <div class="field"><label>严重程度</label><select v-model="form.severity" class="control"><option>LOW</option><option>MEDIUM</option><option>HIGH</option></select></div>
        <div class="field"><label>页码</label><input v-model.number="form.pageNumber" type="number" class="control"></div>
        <div class="field"><label>置信度</label><input v-model.number="form.confidence" type="number" min="0" max="1" step="0.01" class="control"></div>
        <div class="field" style="grid-column:1/-1"><label>证据文本</label><textarea v-model="form.evidenceText" class="control"></textarea></div>
        <div class="field" style="grid-column:1/-1"><label>说明</label><textarea v-model="form.explanation" class="control"></textarea></div>
        <div class="field" style="grid-column:1/-1"><label>建议</label><textarea v-model="form.suggestion" class="control"></textarea></div>
      </div>
      <div class="modal-footer">
        <button class="btn" @click="emit('close')">取消</button>
        <button class="btn primary" @click="emit('submit', { issue: { ...form, verified: true }, comment: form.explanation, reviewer: 'demo-user' })">保存</button>
      </div>
    </div>
  </div>
</template>
