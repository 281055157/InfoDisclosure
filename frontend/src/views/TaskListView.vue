<script setup>
import { onMounted, reactive, ref } from 'vue'
import { FilePlus2, Search } from 'lucide-vue-next'
import { api } from '../api'
import StatusBadge from '../components/StatusBadge.vue'

const filters = reactive({ keyword: '', status: '', technicalStatus: '', businessRisk: '', page: 0, size: 20 })
const page = ref({ content: [], totalElements: 0 })
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    page.value = await api.tasks(filters)
  } finally {
    loading.value = false
  }
}

onMounted(load)

function go(hash) {
  location.hash = hash
}

function tokenSummary(task) {
  const input = Number(task.llmInputTokens || 0)
  const output = Number(task.llmOutputTokens || 0)
  const cacheHit = Number(task.llmCacheHitTokens || 0)
  if (!input && !output && !cacheHit) return '-'
  return `${input}/${output}/${cacheHit}`
}
</script>

<template>
  <div class="page-title">
    <div><h2>审核任务列表</h2><div class="subtitle">任务查询、人工处理和异常重试</div></div>
    <button class="btn primary" @click="go('#/create')"><FilePlus2 :size="16" />新建审核</button>
  </div>

  <section class="panel">
    <div class="panel-header"><h3>筛选条件</h3><span class="muted">共 {{ page.totalElements || 0 }} 条</span></div>
    <div class="panel-body toolbar">
      <div class="field"><label>关键字</label><input v-model="filters.keyword" class="control" placeholder="文件名/任务编号/产品代码"></div>
      <div class="field"><label>任务状态</label><select v-model="filters.status" class="control"><option value="">全部</option><option value="WAITING_MANUAL_REVIEW">待人工审核</option><option value="PARTIAL_SUCCESS">部分成功</option><option value="FAILED">失败</option><option value="MANUAL_APPROVED">人工通过</option></select></div>
      <div class="field"><label>技术状态</label><select v-model="filters.technicalStatus" class="control"><option value="">全部</option><option value="SUCCESS">成功</option><option value="LLM_FAILED">模型失败</option><option value="PDF_PARSE_FAILED">PDF失败</option></select></div>
      <div class="field"><label>业务风险</label><select v-model="filters.businessRisk" class="control"><option value="">全部</option><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option></select></div>
      <button class="btn primary" @click="load"><Search :size="16" />查询</button>
    </div>
  </section>

  <section class="panel" style="margin-top:14px">
    <table class="table">
      <thead><tr><th>任务编号</th><th>文件名</th><th>声明类型</th><th>技术状态</th><th>业务风险</th><th>模型Token</th><th>任务状态</th><th>人工审核</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="task in page.content" :key="task.taskId">
          <td>{{ task.taskNo }}</td>
          <td><strong>{{ task.originalFileName }}</strong><div class="muted">{{ task.declaredProductCode || '-' }}</div></td>
          <td>{{ task.declaredDocumentType || '-' }}</td>
          <td><StatusBadge :value="task.technicalStatus" type="tech" /></td>
          <td><StatusBadge :value="task.businessRisk" type="risk" /></td>
          <td><button class="btn ghost" @click="go(`#/tasks/${task.taskId}?tab=llm-calls`)">{{ tokenSummary(task) }}</button><div class="muted">入/出/命中</div></td>
          <td><StatusBadge :value="task.status" /></td>
          <td>{{ task.manualReviewedAt ? '已审核' : '待审核' }}</td>
          <td><button class="btn" @click="go(`#/tasks/${task.taskId}`)">审核</button></td>
        </tr>
      </tbody>
    </table>
    <div v-if="!loading && !(page.content || []).length" class="empty">暂无审核任务</div>
  </section>
</template>
