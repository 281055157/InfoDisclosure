<script setup>
import { ExternalLink, RefreshCw, X } from 'lucide-vue-next'
import StatusBadge from './StatusBadge.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  rule: { type: Object, default: null },
  executions: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'refresh'])

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

function goTaskDetail(row) {
  if (!row.taskId) return
  location.hash = `#/tasks/${row.taskId}`
}

function goTaskManagement(row) {
  const keyword = row.taskNo || row.taskId || ''
  location.hash = keyword ? `#/task-admin?keyword=${encodeURIComponent(keyword)}` : '#/task-admin'
}
</script>

<template>
  <Transition name="slide-page">
    <div v-if="props.open" class="form-page-backdrop">
      <aside class="form-page execution-drawer">
        <div class="form-page-header">
          <div>
            <h3>规则执行记录</h3>
            <div class="muted">{{ props.rule?.ruleCode || '-' }} · {{ props.executions.length }} 条</div>
          </div>
          <button class="btn ghost" @click="emit('close')"><X :size="18" /></button>
        </div>
        <div class="form-page-body">
          <section class="panel">
            <div class="panel-header">
              <h3>近期执行记录</h3>
              <button class="btn" :disabled="props.loading" @click="emit('refresh')">
                <RefreshCw :size="16" />{{ props.loading ? '加载中' : '刷新' }}
              </button>
            </div>
            <table class="table execution-table">
              <thead>
                <tr>
                  <th>任务</th>
                  <th>执行状态</th>
                  <th>命中</th>
                  <th>问题</th>
                  <th>执行说明</th>
                  <th>耗时</th>
                  <th>执行时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in props.executions" :key="row.id">
                  <td>
                    <strong>{{ row.taskNo || `TASK-${row.taskId || '-'}` }}</strong>
                    <div class="muted">{{ row.originalFileName || '-' }}</div>
                    <div v-if="row.taskStatus" style="margin-top:4px"><StatusBadge :value="row.taskStatus" /></div>
                  </td>
                  <td><span class="badge" :class="row.executionStatus === 'HIT' ? 'green' : row.executionStatus === 'FAILED' ? 'red' : 'gray'">{{ row.executionStatus }}</span></td>
                  <td>{{ row.matched ? '是' : '否' }}</td>
                  <td>{{ row.issueCount }}</td>
                  <td class="execution-detail">{{ row.resultDetail || row.errorMessage || '-' }}</td>
                  <td>{{ row.durationMs ?? '-' }}ms</td>
                  <td>{{ formatTime(row.createdAt) }}</td>
                  <td>
                    <button class="btn" :disabled="!row.taskId" @click="goTaskDetail(row)">
                      <ExternalLink :size="15" />详情
                    </button>
                    <button class="btn" :disabled="!row.taskId" @click="goTaskManagement(row)">任务管理</button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!props.loading && !props.executions.length" class="empty">暂无执行记录</div>
          </section>
        </div>
      </aside>
    </div>
  </Transition>
</template>

<style scoped>
.execution-drawer { width: min(1120px, calc(100vw - 28px)); }
.execution-table th:first-child { width: 25%; }
.execution-table th:last-child { width: 190px; }
.execution-detail { min-width: 220px; max-width: 360px; white-space: normal; }
</style>
