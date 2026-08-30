<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ClipboardCheck, History, Plus, RefreshCw } from 'lucide-vue-next'
import { api } from '../api'
import StatusBadge from '../components/StatusBadge.vue'
import PdfPreview from '../components/PdfPreview.vue'
import IssueList from '../components/IssueList.vue'
import TimelineDrawer from '../components/TimelineDrawer.vue'
import ManualReviewModal from '../components/ManualReviewModal.vue'
import FalsePositiveModal from '../components/FalsePositiveModal.vue'
import SupplementIssueModal from '../components/SupplementIssueModal.vue'

const props = defineProps({ taskId: [String, Number], initialTab: String })
const detail = ref(null)
const report = ref(null)
const tab = ref(props.initialTab === 'llm-calls' ? 'llm-calls' : 'conclusion')
const llmCalls = ref([])
const llmUsage = ref(null)
const timelineOpen = ref(false)
const manualOpen = ref(false)
const falsePositiveIssue = ref(null)
const supplementOpen = ref(false)
const loading = ref(false)
const detailGrid = ref(null)
const workspaceHeight = ref(560)

const issues = computed(() => report.value?.issues || detail.value?.reviewResult?.mergedIssues || [])
const result = computed(() => detail.value?.reviewResult)
const llm = computed(() => result.value?.llmResult || null)
const rawJsonOpen = ref(false)

async function load() {
  if (!props.taskId) return
  loading.value = true
  try {
    detail.value = await api.task(props.taskId)
    report.value = await api.report(props.taskId)
    llmUsage.value = await api.llmUsage(props.taskId)
    llmCalls.value = await api.llmCalls(props.taskId)
    await nextTick()
    updateWorkspaceHeight()
  } finally {
    loading.value = false
  }
}

async function retry(stage = 'LLM_REVIEWING') {
  await api.retry(props.taskId, stage)
  await load()
}

async function submitManual(payload) {
  await api.manualReview(props.taskId, payload)
  manualOpen.value = false
  await load()
}

async function markFalsePositive(comment) {
  await api.updateIssue(props.taskId, falsePositiveIssue.value.issueId, { issueStatus: 'FALSE_POSITIVE', comment })
  falsePositiveIssue.value = null
  await load()
}

async function addSupplement(payload) {
  await api.addManualIssue(props.taskId, payload)
  supplementOpen.value = false
  await load()
}

function percent(value) {
  if (value === undefined || value === null || Number.isNaN(Number(value))) return '-'
  return `${Math.round(Number(value) * 100)}%`
}

function valueOrDash(value) {
  return value === undefined || value === null || value === '' ? '-' : value
}

function evidenceList(field) {
  return field?.evidence || []
}

function jsonText(value) {
  return JSON.stringify(value || {}, null, 2)
}

function tokenValue(value) {
  return value === undefined || value === null ? 0 : value
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function updateWorkspaceHeight() {
  if (!detailGrid.value || window.innerWidth <= 980) return
  const top = detailGrid.value.getBoundingClientRect().top
  const contentBottomPadding = 40
  const available = window.innerHeight - top - contentBottomPadding
  workspaceHeight.value = Math.max(520, Math.floor(available))
}

watch(() => props.taskId, load)
watch(() => props.initialTab, value => {
  if (value === 'llm-calls') tab.value = 'llm-calls'
})
onMounted(() => {
  load()
  window.addEventListener('resize', updateWorkspaceHeight)
  nextTick(updateWorkspaceHeight)
})
onBeforeUnmount(() => window.removeEventListener('resize', updateWorkspaceHeight))
</script>

<template>
  <div class="task-detail-page">
    <div class="page-title">
      <div>
        <h2>{{ detail?.taskNo || '任务详情' }}</h2>
        <div class="subtitle">{{ detail?.originalFileName || '加载中' }}</div>
      </div>
      <div style="display:flex;gap:8px">
        <button class="btn" @click="timelineOpen = true"><History :size="16" />时间线</button>
        <button class="btn" @click="retry()"><RefreshCw :size="16" />重试模型</button>
        <button class="btn primary" @click="manualOpen = true"><ClipboardCheck :size="16" />人工审核</button>
      </div>
    </div>

    <section v-if="detail" class="grid cols-4" style="margin-bottom:14px">
      <div class="panel metric"><div class="metric-label">技术状态</div><div class="metric-value" style="font-size:16px"><StatusBadge :value="detail.technicalStatus" type="tech" /></div></div>
      <div class="panel metric"><div class="metric-label">业务风险</div><div class="metric-value" style="font-size:16px"><StatusBadge :value="detail.businessRisk" type="risk" /></div></div>
      <div class="panel metric"><div class="metric-label">任务状态</div><div class="metric-value" style="font-size:16px"><StatusBadge :value="detail.status" /></div></div>
      <div class="panel metric"><div class="metric-label">声明信息</div><div><strong>{{ detail.declaredProductCode || '-' }}</strong><div class="muted">{{ detail.declaredDocumentType || '-' }}</div></div></div>
    </section>

    <div ref="detailGrid" class="detail-grid task-detail-grid" :style="{ '--detail-workspace-height': `${workspaceHeight}px` }">
      <PdfPreview :task-id="props.taskId" />
      <section class="panel detail-side-panel">
      <div class="tabs">
        <button class="tab" :class="{active: tab==='conclusion'}" @click="tab='conclusion'">审核结论</button>
        <button class="tab" :class="{active: tab==='issues'}" @click="tab='issues'">问题列表</button>
        <button class="tab" :class="{active: tab==='product'}" @click="tab='product'">产品信息</button>
        <button class="tab" :class="{active: tab==='llm'}" @click="tab='llm'">LLM 分析</button>
        <button class="tab" :class="{active: tab==='llm-calls'}" @click="tab='llm-calls'">模型调用</button>
      </div>
      <div class="panel-body detail-side-body">
        <div v-if="tab==='conclusion'" class="grid">
          <div v-if="detail?.statusDetail" class="issue"><strong>状态说明</strong><div>{{ detail.statusDetail }}</div></div>
          <div class="grid cols-2">
            <div><div class="muted">产品识别</div><strong>{{ detail?.productIdentityDecision || '-' }}</strong></div>
            <div><div class="muted">业务结论</div><strong>{{ detail?.businessAcceptanceDecision || '-' }}</strong></div>
          </div>
          <div><div class="muted">摘要</div><p>{{ report?.summary || result?.llmResult?.summary || '暂无摘要' }}</p></div>
          <div><div class="muted">人工建议</div><p>{{ report?.manualSuggestion || result?.llmResult?.manualReviewSuggestion || '暂无建议' }}</p></div>
        </div>
        <div v-else-if="tab==='issues'">
          <div style="display:flex;justify-content:flex-end;margin-bottom:10px">
            <button class="btn" @click="supplementOpen = true"><Plus :size="16" />补充问题</button>
          </div>
          <IssueList :issues="issues" :task-id="props.taskId" @false-positive="falsePositiveIssue = $event" />
        </div>
        <div v-else-if="tab==='product'" class="grid cols-2">
          <div><div class="muted">产品库匹配</div><strong>{{ result?.productMaster?.matched ? '已匹配' : '未匹配' }}</strong></div>
          <div><div class="muted">产品代码</div><strong>{{ result?.productMaster?.productCode || detail?.declaredProductCode || '-' }}</strong></div>
          <div style="grid-column:1/-1"><div class="muted">产品名称</div><strong>{{ result?.productMaster?.productName || '-' }}</strong></div>
          <div style="grid-column:1/-1"><div class="muted">B9 类型</div><strong>{{ detail?.b9Value || '-' }}</strong></div>
        </div>
        <div v-else-if="tab==='llm'" class="grid">
          <div v-if="!llm || (!llm.summary && !llm.mainProductCode && !llm.mainProductName && !llm.candidateDocumentType)" class="empty">
            暂无模型分析结果
          </div>
          <template v-else>
            <section class="llm-summary">
              <div>
                <div class="muted">候选文档类型</div>
                <strong>{{ valueOrDash(llm.candidateDocumentType?.value || result?.candidateDocumentType?.value) }}</strong>
                <div class="muted">置信度：{{ percent(llm.candidateDocumentType?.confidence || result?.candidateDocumentType?.confidence) }}</div>
              </div>
              <div>
                <div class="muted">目标产品判断</div>
                <strong>{{ valueOrDash(llm.targetProductAssessment?.decision || result?.targetProductAssessment?.decision) }}</strong>
                <div class="muted">置信度：{{ percent(llm.targetProductAssessment?.confidence || result?.targetProductAssessment?.confidence) }}</div>
              </div>
              <div>
                <div class="muted">模型问题数</div>
                <strong>{{ (llm.issues || []).length }}</strong>
                <div class="muted">已回查证据：{{ (llm.issues || []).filter(item => item.verified).length }}</div>
              </div>
            </section>

            <section class="llm-card">
              <h3>模型摘要</h3>
              <p>{{ llm.summary || '暂无摘要' }}</p>
              <div v-if="llm.manualReviewSuggestion" class="llm-note">
                <strong>人工审核建议：</strong>{{ llm.manualReviewSuggestion }}
              </div>
            </section>

            <section class="llm-card">
              <h3>核心识别字段</h3>
              <div class="llm-field-grid">
                <div class="llm-field">
                  <div class="muted">主产品代码</div>
                  <strong>{{ valueOrDash(llm.mainProductCode?.value) }}</strong>
                  <span class="muted">置信度：{{ percent(llm.mainProductCode?.confidence) }}</span>
                </div>
                <div class="llm-field">
                  <div class="muted">主产品名称</div>
                  <strong>{{ valueOrDash(llm.mainProductName?.value) }}</strong>
                  <span class="muted">置信度：{{ percent(llm.mainProductName?.confidence) }}</span>
                </div>
              </div>
              <div class="llm-evidence-list">
                <div v-for="item in [...evidenceList(llm.mainProductCode), ...evidenceList(llm.mainProductName)]" :key="`${item.pageNumber}-${item.text}`" class="llm-evidence">
                  <span class="badge" :class="item.verified ? 'green' : 'gray'">第 {{ item.pageNumber || '-' }} 页</span>
                  <span>{{ item.text || '-' }}</span>
                </div>
                <div v-if="!evidenceList(llm.mainProductCode).length && !evidenceList(llm.mainProductName).length" class="muted">暂无字段证据</div>
              </div>
            </section>

            <section class="llm-card">
              <h3>目标产品评估</h3>
              <div class="llm-field-grid">
                <div class="llm-field"><div class="muted">声明产品代码</div><strong>{{ valueOrDash(llm.targetProductAssessment?.declaredProductCode || detail?.declaredProductCode) }}</strong></div>
                <div class="llm-field"><div class="muted">匹配产品代码</div><strong>{{ valueOrDash(llm.targetProductAssessment?.matchedProductCode) }}</strong></div>
                <div class="llm-field"><div class="muted">匹配产品名称</div><strong>{{ valueOrDash(llm.targetProductAssessment?.matchedProductName) }}</strong></div>
                <div class="llm-field"><div class="muted">匹配机构</div><strong>{{ valueOrDash(llm.targetProductAssessment?.matchedInstitution) }}</strong></div>
              </div>
              <p>{{ llm.targetProductAssessment?.explanation || result?.targetProductAssessment?.explanation || '暂无目标产品评估说明' }}</p>
            </section>

            <section class="llm-card">
              <h3>产品引用</h3>
              <table class="table compact">
                <thead><tr><th>产品代码</th><th>产品名称</th><th>角色/评估</th><th>页码</th><th>置信度</th></tr></thead>
                <tbody>
                  <tr v-for="item in [...(llm.productOccurrences || []), ...(llm.otherProductReferences || [])]" :key="`${item.productCode}-${item.productName}-${item.pageNumber}`">
                    <td>{{ valueOrDash(item.productCode) }}</td>
                    <td>{{ valueOrDash(item.productName) }}</td>
                    <td>{{ valueOrDash(item.role || item.assessment) }}</td>
                    <td>{{ valueOrDash(item.pageNumber) }}</td>
                    <td>{{ percent(item.confidence) }}</td>
                  </tr>
                </tbody>
              </table>
              <div v-if="!(llm.productOccurrences || []).length && !(llm.otherProductReferences || []).length" class="muted">暂无其他产品引用</div>
            </section>

            <section class="llm-card">
              <h3>模型发现问题</h3>
              <IssueList :issues="llm.issues || []" :task-id="props.taskId" @false-positive="falsePositiveIssue = $event" />
            </section>

            <details class="llm-debug" :open="rawJsonOpen" @toggle="rawJsonOpen = $event.target.open">
              <summary>查看原始模型结构化响应</summary>
              <pre>{{ jsonText(llm) }}</pre>
            </details>
          </template>
        </div>
        <div v-else class="grid">
          <section class="llm-summary">
            <div><div class="muted">输入Token</div><strong>{{ tokenValue(llmUsage?.inputTokens) }}</strong></div>
            <div><div class="muted">输出Token</div><strong>{{ tokenValue(llmUsage?.outputTokens) }}</strong></div>
            <div><div class="muted">命中Token</div><strong>{{ tokenValue(llmUsage?.cacheHitTokens) }}</strong></div>
          </section>
          <section class="llm-card">
            <h3>调用明细</h3>
            <table class="table compact">
              <thead><tr><th>时间</th><th>阶段</th><th>操作</th><th>模型</th><th>页码</th><th>Token</th><th>耗时</th><th>状态</th></tr></thead>
              <tbody>
                <tr v-for="call in llmCalls" :key="call.id">
                  <td>{{ formatTime(call.createdAt) }}</td>
                  <td>{{ valueOrDash(call.stage) }}</td>
                  <td>{{ valueOrDash(call.operationType || call.ruleCode) }}</td>
                  <td><strong>{{ valueOrDash(call.modelName) }}</strong><div class="muted">{{ valueOrDash(call.provider) }}</div></td>
                  <td>{{ valueOrDash(call.pageFrom) }}-{{ valueOrDash(call.pageTo) }}</td>
                  <td>{{ tokenValue(call.inputTokens) }}/{{ tokenValue(call.outputTokens) }}/{{ tokenValue(call.cacheHitTokens) }}</td>
                  <td>{{ call.durationMs == null ? '-' : `${call.durationMs}ms` }}</td>
                  <td><span class="badge" :class="call.callStatus === 'SUCCESS' ? 'green' : 'red'">{{ call.callStatus || '-' }}</span></td>
                </tr>
              </tbody>
            </table>
            <div v-if="!llmCalls.length" class="empty">暂无模型调用记录</div>
          </section>
        </div>
      </div>
      </section>
    </div>

    <TimelineDrawer :task-id="props.taskId" :open="timelineOpen" @close="timelineOpen = false" />
    <ManualReviewModal :open="manualOpen" @close="manualOpen = false" @submit="submitManual" />
    <FalsePositiveModal :open="!!falsePositiveIssue" :issue="falsePositiveIssue" @close="falsePositiveIssue = null" @submit="markFalsePositive" />
    <SupplementIssueModal :open="supplementOpen" @close="supplementOpen = false" @submit="addSupplement" />
  </div>
</template>
