<script setup>
import { computed, ref } from 'vue'

defineOptions({ name: 'GovernanceTraceNode' })
const props = defineProps({ node: { type: Object, required: true }, nodes: { type: Array, required: true } })
const collapsed = ref(Boolean(props.node.attributes?.collapsedByDefault))

const children = computed(() => props.nodes
  .filter(item => item.parentId === props.node.id)
  .sort((a, b) => (a.sequence ?? 9999) - (b.sequence ?? 9999)
    || String(a.startedAt || '').localeCompare(String(b.startedAt || ''))))
const hasParallelChildren = computed(() => children.value.filter(item => item.executionMode === 'PARALLEL').length > 1)
const canCollapse = computed(() => props.node.type === 'RETRY_HISTORY' && children.value.length > 0)
const modelResponse = computed(() => props.node.attributes?.modelResponse || null)
const otherAttributes = computed(() => {
  const value = { ...(props.node.attributes || {}) }
  delete value.modelResponse
  return value
})

function tokenTotal(node) { return (node.inputTokens || 0) + (node.outputTokens || 0) }
function formatTime(value) { return value ? new Date(value).toLocaleTimeString() : '-' }
function statusClass(status) {
  if (['SUCCESS', 'COMPLETED', 'CACHED'].includes(status)) return 'success'
  if (['FAILED'].includes(status)) return 'failed'
  if (['DEFERRED', 'PARTIAL_SUCCESS', 'NO_OP', 'HISTORICAL'].includes(status)) return 'warning'
  return 'processing'
}
function toolName(call) { return call?.toolName || call?.name || '-' }
function toolArguments(call) { return call?.arguments || {} }
</script>

<template>
  <div class="trace-branch">
    <article class="trace-node" :class="[`trace-${node.type.toLowerCase()}`, statusClass(node.status)]">
      <div class="trace-node-head">
        <span class="trace-type">{{ node.type }}</span>
        <strong>{{ node.name }}</strong>
        <span class="trace-status">{{ node.status }}</span>
      </div>
      <div class="trace-node-meta">
        <span v-if="node.executionMode">{{ node.executionMode === 'PARALLEL' ? '并行' : '串行' }}</span>
        <span v-if="node.governanceGroupId">分组 #{{ node.governanceGroupId }}</span>
        <span v-if="node.iteration != null">第 {{ node.iteration }} 轮</span>
        <span v-if="node.provider || node.model">{{ node.provider || '-' }} / {{ node.model || '-' }}</span>
        <span v-if="tokenTotal(node)">Token {{ node.inputTokens }} / {{ node.outputTokens }} / {{ node.cacheHitTokens }}</span>
        <span v-if="node.durationMs != null">{{ node.durationMs }} ms</span>
        <span>{{ formatTime(node.startedAt) }}</span>
        <button v-if="canCollapse" type="button" class="trace-collapse-toggle" @click="collapsed = !collapsed">
          {{ collapsed ? '展开历史链路' : '收起历史链路' }}
        </button>
      </div>
      <div v-if="node.errorMessage" class="trace-error">{{ node.errorMessage }}</div>
      <details v-if="modelResponse" class="trace-model-response">
        <summary>查看模型返回结果</summary>
        <div class="trace-model-response-body">
          <div class="trace-response-meta"><span v-if="modelResponse.finishReason">结束原因：{{ modelResponse.finishReason }}</span><span v-if="modelResponse.nextAction">动作：{{ modelResponse.nextAction }}</span></div>
          <section v-if="modelResponse.thoughtSummary"><strong>Message 摘要</strong><p>{{ modelResponse.thoughtSummary }}</p></section>
          <section><strong>Assistant Message</strong><pre>{{ modelResponse.message || '（空消息）' }}</pre></section>
          <section v-if="modelResponse.toolCalls?.length"><strong>Tool Calls（{{ modelResponse.toolCalls.length }}）</strong>
            <div v-for="(call, index) in modelResponse.toolCalls" :key="call.callId || call.id || index" class="trace-response-tool">
              <div><span>#{{ index + 1 }}</span><strong>{{ toolName(call) }}</strong><code>{{ call.callId || call.id || '-' }}</code></div>
              <pre>{{ JSON.stringify(toolArguments(call), null, 2) }}</pre>
            </div>
          </section>
          <div v-else class="muted">本轮未返回 Tool Call</div>
        </div>
      </details>
      <details v-if="Object.keys(otherAttributes).length" class="trace-attributes">
        <summary>埋点属性</summary><pre>{{ JSON.stringify(otherAttributes, null, 2) }}</pre>
      </details>
    </article>
    <div v-if="children.length && !collapsed" class="trace-connector" :class="{ parallel: hasParallelChildren }">
      <span>{{ hasParallelChildren ? `并行分叉 · ${children.length} 条` : '串行推进' }}</span>
    </div>
    <div v-if="children.length && !collapsed" class="trace-children" :class="{ parallel: hasParallelChildren }">
      <GovernanceTraceNode v-for="child in children" :key="child.id" :node="child" :nodes="nodes" />
    </div>
  </div>
</template>
