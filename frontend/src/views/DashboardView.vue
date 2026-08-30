<script setup>
import { onMounted, ref } from 'vue'
import { FilePlus2, RefreshCw } from 'lucide-vue-next'
import { api } from '../api'
import StatusBadge from '../components/StatusBadge.vue'

const summary = ref({})
const tasks = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    summary.value = await api.statistics()
    const page = await api.tasks({ size: 6, status: 'WAITING_MANUAL_REVIEW' })
    tasks.value = page.content || []
  } finally {
    loading.value = false
  }
}

onMounted(load)

function go(hash) {
  location.hash = hash
}
</script>

<template>
  <div class="page-title">
    <div>
      <h2>工作台</h2>
      <div class="subtitle">今日任务、风险分布和待处理事项</div>
    </div>
    <div style="display:flex;gap:8px">
      <button class="btn" @click="load"><RefreshCw :size="16" />刷新</button>
      <button class="btn primary" @click="go('#/create')"><FilePlus2 :size="16" />新建任务</button>
    </div>
  </div>

  <section class="grid cols-4">
    <div class="panel metric"><div class="metric-label">任务总数</div><div class="metric-value">{{ summary.total || 0 }}</div></div>
    <div class="panel metric"><div class="metric-label">待人工审核</div><div class="metric-value">{{ summary.waitingManualReview || 0 }}</div></div>
    <div class="panel metric"><div class="metric-label">高风险任务</div><div class="metric-value">{{ summary.highRisk || 0 }}</div></div>
    <div class="panel metric"><div class="metric-label">部分成功</div><div class="metric-value">{{ summary.partialSuccess || 0 }}</div></div>
  </section>

  <section class="panel" style="margin-top:14px">
    <div class="panel-header"><h3>待处理任务</h3><span class="muted">{{ loading ? '加载中' : `共 ${tasks.length} 条` }}</span></div>
    <table class="table">
      <thead><tr><th>任务号</th><th>文件名</th><th>风险</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="task in tasks" :key="task.taskId">
          <td>{{ task.taskNo }}</td>
          <td><strong>{{ task.originalFileName }}</strong><div class="muted">{{ task.declaredProductCode || '-' }}</div></td>
          <td><StatusBadge :value="task.businessRisk" type="risk" /></td>
          <td><StatusBadge :value="task.status" /></td>
          <td>{{ new Date(task.createdAt).toLocaleString() }}</td>
          <td><button class="btn" @click="go(`#/tasks/${task.taskId}`)">进入审核</button></td>
        </tr>
      </tbody>
    </table>
    <div v-if="!tasks.length" class="empty">暂无待处理任务</div>
  </section>
</template>
