<script setup>
import { onMounted, ref, watch } from 'vue'
import { X } from 'lucide-vue-next'
import { api } from '../api'

const props = defineProps({ taskId: [String, Number], open: Boolean })
const emit = defineEmits(['close'])
const rows = ref([])

async function load() {
  if (!props.open || !props.taskId) return
  rows.value = await api.timeline(props.taskId)
}

watch(() => props.open, load)
onMounted(load)
</script>

<template>
  <div v-if="open" class="drawer">
    <div class="panel-header">
      <h3>任务时间线</h3>
      <button class="btn ghost" @click="emit('close')"><X :size="18" /></button>
    </div>
    <div class="timeline">
      <div v-for="row in rows" :key="row.id" class="timeline-card">
        <strong>{{ row.operationType }}</strong>
        <div>{{ row.operationDetail }}</div>
        <div class="muted">{{ row.operator }} · {{ new Date(row.createdAt).toLocaleString() }}</div>
      </div>
      <div v-if="!rows.length" class="empty">暂无时间线记录</div>
    </div>
  </div>
</template>
