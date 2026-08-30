<script setup>
import { X, RefreshCw } from 'lucide-vue-next'
import GovernanceTraceNode from './GovernanceTraceNode.vue'

defineProps({ trace: Object, loading: Boolean, error: String })
defineEmits(['close', 'refresh'])
</script>

<template>
  <div class="modal-backdrop governance-trace-backdrop" @click.self="$emit('close')">
    <aside class="drawer governance-trace-drawer" role="dialog" aria-label="大模型调用链路">
      <div class="drawer-header">
        <div><h3>大模型与 Tool 调用链路</h3><div class="muted">{{ trace?.runNo || '正在加载运行信息' }}</div></div>
        <div class="toolbar"><button class="btn" :disabled="loading" @click="$emit('refresh')"><RefreshCw :size="15" />刷新</button><button class="btn ghost" @click="$emit('close')"><X :size="18" /></button></div>
      </div>
      <div class="drawer-body governance-trace-body">
        <div v-if="loading" class="empty">正在读取埋点链路…</div>
        <div v-else-if="error" class="issue governance-error">{{ error }}</div>
        <template v-else-if="trace">
          <section class="trace-current" :class="trace.status === 'FAILED' ? 'failed' : ''">
            <div><span>当前步骤</span><strong>{{ trace.currentStep }}</strong></div>
            <p>{{ trace.currentMessage }}</p>
            <div class="trace-current-meta"><span>Trace ID：{{ trace.traceId }}</span><span>{{ trace.instrumented ? '统一 Span 埋点' : '历史记录还原' }}</span></div>
          </section>
          <div class="trace-legend">
            <span><i class="dot success"></i>成功</span><span><i class="dot processing"></i>执行中</span>
            <span><i class="dot warning"></i>暂缓/空跑</span><span><i class="dot failed"></i>失败</span>
            <span>同层“并行分叉”表示可并行；其余箭头为串行。</span>
          </div>
          <section class="trace-canvas">
            <GovernanceTraceNode v-for="node in trace.nodes.filter(item => !item.parentId)" :key="node.id" :node="node" :nodes="trace.nodes" />
          </section>
        </template>
      </div>
    </aside>
  </div>
</template>
