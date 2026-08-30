<script setup>
import { ref } from 'vue'
import { X } from 'lucide-vue-next'

defineProps({ open: Boolean })
const emit = defineEmits(['close', 'submit'])
const decision = ref('APPROVED_WITH_WARNING')
const comment = ref('')
const containsFalsePositive = ref(false)
const containsFalseNegative = ref(false)
</script>

<template>
  <div v-if="open" class="modal-backdrop">
    <div class="modal">
      <div class="modal-header">
        <h3>提交人工审核结论</h3>
        <button class="btn ghost" @click="emit('close')"><X :size="18" /></button>
      </div>
      <div class="panel-body grid">
        <div class="field">
          <label>审核结论</label>
          <select v-model="decision" class="control">
            <option value="APPROVED">通过</option>
            <option value="APPROVED_WITH_WARNING">带提示通过</option>
            <option value="RETURNED">退回修改</option>
            <option value="REJECTED">拒绝</option>
            <option value="UNABLE_TO_CONFIRM">无法确认</option>
          </select>
        </div>
        <label class="switch"><input v-model="containsFalsePositive" type="checkbox">包含误报</label>
        <label class="switch"><input v-model="containsFalseNegative" type="checkbox">包含漏报</label>
        <div class="field">
          <label>审核意见</label>
          <textarea v-model="comment" class="control" placeholder="请输入人工审核意见"></textarea>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn" @click="emit('close')">取消</button>
        <button class="btn primary" @click="emit('submit', { decision, comment, containsFalsePositive, containsFalseNegative, reviewer: 'demo-user' })">提交</button>
      </div>
    </div>
  </div>
</template>
