<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { RefreshCw, Search, Trash2 } from 'lucide-vue-next'
import { api } from '../api'
import StatusBadge from '../components/StatusBadge.vue'

const filters = reactive({ keyword: '', status: '', technicalStatus: '', page: 0, size: 20 })
const props = defineProps({ initialKeyword: { type: String, default: '' } })
const page = ref({ content: [], totalElements: 0 })
const loading = ref(false)
const deleteFiles = ref(true)
const deletingId = ref(null)
const message = ref('')

async function load() {
  loading.value = true
  message.value = ''
  try {
    page.value = await api.tasks(filters)
  } finally {
    loading.value = false
  }
}

async function removeTask(task) {
  const confirmed = window.confirm(`确认删除任务 ${task.taskNo}？相关页面、问题及调用记录也会一并删除。`)
  if (!confirmed) return
  deletingId.value = task.taskId
  message.value = ''
  try {
    const result = await api.deleteTask(task.taskId, deleteFiles.value)
    const warning = result.warnings?.length ? `；警告：${result.warnings.join('；')}` : ''
    message.value = `已删除 ${result.taskNo}${warning}`
    await load()
  } catch (e) {
    message.value = e.message
  } finally {
    deletingId.value = null
  }
}

watch(() => props.initialKeyword, value => {
  if (value !== undefined && value !== filters.keyword) {
    filters.keyword = value || ''
    load()
  }
})

onMounted(() => {
  if (props.initialKeyword) {
    filters.keyword = props.initialKeyword
  }
  load()
})
</script>

<template>
  <div class="page-title">
    <div>
      <h2>任务管理</h2>
      <div class="subtitle">管理并删除不再需要的测试任务记录；相同文件可直接重复创建审核任务</div>
    </div>
    <button class="btn" @click="load"><RefreshCw :size="16" />刷新</button>
  </div>

  <section class="panel">
    <div class="panel-header">
      <h3>删除记录</h3>
      <span class="muted">{{ message || `共 ${page.totalElements || 0} 条` }}</span>
    </div>
    <div class="panel-body toolbar">
      <div class="field"><label>关键字</label><input v-model="filters.keyword" class="control" placeholder="任务号、文件名、产品代码"></div>
      <div class="field"><label>任务状态</label><select v-model="filters.status" class="control"><option value="">全部</option><option value="WAITING_MANUAL_REVIEW">待人工审核</option><option value="PARTIAL_SUCCESS">部分成功</option><option value="FAILED">失败</option><option value="MANUAL_APPROVED">人工通过</option></select></div>
      <div class="field"><label>技术状态</label><select v-model="filters.technicalStatus" class="control"><option value="">全部</option><option value="SUCCESS">成功</option><option value="LLM_FAILED">模型失败</option><option value="UNKNOWN_ERROR">未知错误</option></select></div>
      <label class="switch"><input v-model="deleteFiles" type="checkbox">同时删除本地文件</label>
      <button class="btn primary" @click="load"><Search :size="16" />查询</button>
    </div>
  </section>

  <section class="panel" style="margin-top:14px">
    <table class="table">
      <thead><tr><th>任务编号</th><th>文件名</th><th>产品代码</th><th>技术状态</th><th>任务状态</th><th>创建时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="task in page.content" :key="task.taskId">
          <td>{{ task.taskNo }}</td>
          <td><strong>{{ task.originalFileName }}</strong><div class="muted">{{ task.declaredDocumentType || '-' }}</div></td>
          <td>{{ task.declaredProductCode || '-' }}</td>
          <td><StatusBadge :value="task.technicalStatus" type="tech" /></td>
          <td><StatusBadge :value="task.status" /></td>
          <td>{{ new Date(task.createdAt).toLocaleString() }}</td>
          <td>
            <button class="btn danger" :disabled="deletingId === task.taskId" @click="removeTask(task)">
              <Trash2 :size="15" />{{ deletingId === task.taskId ? '删除中' : '删除' }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-if="!loading && !(page.content || []).length" class="empty">暂无任务记录</div>
  </section>
</template>
