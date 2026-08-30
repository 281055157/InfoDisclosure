<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Bot, Boxes, FileText, Play, RefreshCw, Search, Sparkles, Trash2 } from 'lucide-vue-next'
import { api } from '../api'
import GovernanceProposalDrawer from '../components/GovernanceProposalDrawer.vue'
import GovernanceTraceDrawer from '../components/GovernanceTraceDrawer.vue'

const tabs = [
  { key: 'feedback', label: '原始反馈', icon: FileText },
  { key: 'groups', label: '治理分组', icon: Boxes },
  { key: 'proposals', label: '优化提案', icon: Sparkles },
  { key: 'runs', label: '治理运行', icon: Bot }
]
const activeTab = ref('feedback')
const feedback = ref([])
const groups = ref([])
const proposals = ref([])
const runs = ref([])
const loading = ref(false)
const starting = ref(false)
const error = ref('')
const success = ref('')
const selectedProposal = ref(null)
const selectedGroup = ref(null)
const selectedGroupFeedbacks = ref([])
const proposalBusy = ref(false)
const proposalError = ref('')
const selectedTrace = ref(null)
const traceRunId = ref(null)
const traceLoading = ref(false)
const traceError = ref('')
const deletingGroupId = ref(null)
const deletingRunId = ref(null)

const filters = reactive({ keyword: '', feedbackType: '', processStatus: '', ruleCode: '', groupStatus: '', proposalStatus: '', proposalType: '', rootCause: '', proposalDocumentType: '', backtestRisk: '', minimumConfidence: '', createdFrom: '', createdTo: '' })

const filteredFeedback = computed(() => feedback.value.filter(row => {
  const keyword = filters.keyword.trim().toLowerCase()
  const text = [row.taskId, row.ruleCode, row.feedbackType, row.comment, row.declaredProductCode,
    row.declaredDocumentType, row.aggregationKey].filter(Boolean).join(' ').toLowerCase()
  return (!keyword || text.includes(keyword))
    && (!filters.feedbackType || row.feedbackType === filters.feedbackType)
    && (!filters.processStatus || row.processStatus === filters.processStatus)
    && (!filters.ruleCode || (row.ruleCode || '').includes(filters.ruleCode.trim()))
}))

const filteredGroups = computed(() => groups.value.filter(row =>
  (!filters.groupStatus || row.status === filters.groupStatus)
  && (!filters.ruleCode || (row.ruleCode || '').includes(filters.ruleCode.trim()))
))

const filteredProposals = computed(() => proposals.value.filter(row =>
  (!filters.proposalStatus || row.proposalStatus === filters.proposalStatus)
  && (!filters.proposalType || row.proposalType === filters.proposalType)
  && (!filters.rootCause || row.rootCauseType === filters.rootCause)
  && (!filters.proposalDocumentType || (row.declaredFileType || '').includes(filters.proposalDocumentType.trim()))
  && (!filters.backtestRisk || row.backtestRisk === filters.backtestRisk)
  && (!filters.minimumConfidence || (row.agentConfidence || 0) >= Number(filters.minimumConfidence))
  && (!filters.createdFrom || new Date(row.createdAt) >= new Date(`${filters.createdFrom}T00:00:00`))
  && (!filters.createdTo || new Date(row.createdAt) <= new Date(`${filters.createdTo}T23:59:59`))
  && (!filters.ruleCode || (row.ruleCode || '').includes(filters.ruleCode.trim()))
))

async function load(tab = activeTab.value) {
  loading.value = true
  error.value = ''
  try {
    if (tab === 'feedback') feedback.value = await api.ruleFeedback()
    if (tab === 'groups') groups.value = await api.governanceGroups()
    if (tab === 'proposals') proposals.value = await api.governanceProposals()
    if (tab === 'runs') runs.value = await api.governanceRuns()
  } catch (e) {
    error.value = e?.message || '数据加载失败'
    if (tab === 'feedback') feedback.value = []
    if (tab === 'groups') groups.value = []
    if (tab === 'proposals') proposals.value = []
    if (tab === 'runs') runs.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(tab) {
  activeTab.value = tab
  success.value = ''
  load(tab)
}

async function startRun() {
  starting.value = true
  error.value = ''
  try {
    const row = await api.startGovernanceRun()
    runs.value = [row, ...runs.value.filter(item => item.id !== row.id)]
    success.value = row.createdGroupCount === 0
      ? `治理运行 ${row.runNo} 已完成扫描，但没有创建新分组：${row.skipReasonSummary || '没有 NEW/PENDING 且尚未归组的反馈；FAILED/DEFERRED 分组请在“治理分组”中重新分析。'}`
      : row.skippedFeedbackCount > 0
      ? `治理运行 ${row.runNo} 已创建；${row.skippedFeedbackCount} 条反馈未进入分析：${row.skipReasonSummary || '请查看运行记录'}。`
      : `治理运行 ${row.runNo} 已创建，后台将异步分析符合阈值的反馈分组。`
    activeTab.value = 'runs'
  } catch (e) {
    error.value = e?.message || '启动治理运行失败'
  } finally {
    starting.value = false
  }
}

async function openTrace(row) {
  traceRunId.value = row.id
  selectedTrace.value = null
  traceLoading.value = true
  traceError.value = ''
  try { selectedTrace.value = await api.governanceRunTrace(row.id) }
  catch (e) { traceError.value = e?.message || '调用链路加载失败' }
  finally { traceLoading.value = false }
}

function closeTrace() {
  selectedTrace.value = null
  traceRunId.value = null
  traceError.value = ''
}

async function analyzeGroup(row) {
  error.value = ''
  try {
    await api.analyzeGovernanceGroup(row.id)
    row.status = 'PENDING'
    success.value = `分组 #${row.id} 已重新提交分析。`
  } catch (e) { error.value = e?.message || '提交分析失败' }
}

async function deleteGroup(row) {
  if (!window.confirm(`确认删除治理分组 #${row.id}？\n该分组的 ${row.feedbackCount || 0} 条来源反馈将恢复为 PENDING，可重新聚合分析。`)) return
  deletingGroupId.value = row.id
  error.value = ''
  try {
    const result = await api.deleteGovernanceGroup(row.id)
    groups.value = groups.value.filter(item => item.id !== row.id)
    success.value = `治理分组 #${row.id} 已删除，${result.releasedFeedbackCount || 0} 条反馈已恢复为 PENDING。`
  } catch (e) { error.value = e?.message || '删除治理分组失败' }
  finally { deletingGroupId.value = null }
}

async function deleteRun(row) {
  if (!window.confirm(`确认删除治理运行 ${row.runNo}？\n其 FAILED/DEFERRED 且未形成提案的分组会一并删除，来源反馈将恢复为 PENDING。`)) return
  deletingRunId.value = row.id
  error.value = ''
  try {
    const result = await api.deleteGovernanceRun(row.id)
    runs.value = runs.value.filter(item => item.id !== row.id)
    groups.value = groups.value.filter(item => item.governanceRunId !== row.id)
    if (traceRunId.value === row.id) closeTrace()
    success.value = `治理运行 ${row.runNo} 已删除，${result.releasedFeedbackCount || 0} 条反馈已恢复为 PENDING。`
  } catch (e) { error.value = e?.message || '删除治理运行失败' }
  finally { deletingRunId.value = null }
}

async function openGroup(row) {
  error.value = ''
  try {
    selectedGroupFeedbacks.value = await api.governanceGroupFeedbacks(row.id)
    selectedGroup.value = row
  } catch (e) { error.value = e?.message || '分组反馈加载失败' }
}

async function openProposal(row) {
  proposalError.value = ''
  selectedProposal.value = await api.governanceProposal(row.id).catch(e => {
    error.value = e?.message || '提案详情加载失败'
    return null
  })
}

async function handleProposalAction({ type, payload, message }) {
  if (type === 'client-error') { proposalError.value = message; return }
  const id = selectedProposal.value?.summary?.id
  if (!id) return
  proposalBusy.value = true
  proposalError.value = ''
  try {
    let detail
    if (type === 'approve') detail = await api.approveGovernanceProposal(id, payload)
    if (type === 'modify') detail = await api.approveModifiedGovernanceProposal(id, payload)
    if (type === 'reject') detail = await api.rejectGovernanceProposal(id, payload)
    if (type === 'defer') detail = await api.deferGovernanceProposal(id, payload)
    if (type === 'apply') detail = await api.applyGovernanceProposal(id, payload)
    if (type === 'evaluate') {
      const result = await api.evaluateGovernanceEffect(id)
      success.value = `效果评估完成：${result.decision || 'UNKNOWN'}`
      detail = await api.governanceProposal(id)
    }
    selectedProposal.value = detail
    const index = proposals.value.findIndex(row => row.id === id)
    if (index >= 0 && detail?.summary) proposals.value[index] = detail.summary
    success.value ||= `提案 ${detail?.summary?.proposalNo || id} 已处理。`
  } catch (e) {
    proposalError.value = e?.message || '提案处理失败；若被其他审核人抢先处理，请刷新后重试。'
  } finally {
    proposalBusy.value = false
  }
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function snapshotText(value) {
  if (!value) return '-'
  try { return JSON.stringify(JSON.parse(value), null, 2) } catch (_) { return value }
}

function tokenTotal(row) {
  return (row.inputTokens || 0) + (row.outputTokens || 0)
}

onMounted(() => load('feedback'))
</script>

<template>
  <div class="page-title">
    <div>
      <h2>反馈治理闭环</h2>
      <div class="subtitle">从人工反馈聚合、Agent 分析和规则提案，到人工审批与效果留痕</div>
    </div>
    <div class="toolbar">
      <button class="btn" @click="load()"><RefreshCw :size="16" />刷新</button>
      <button class="btn primary" :disabled="starting" @click="startRun"><Play :size="16" />{{ starting ? '启动中…' : '立即聚合分析' }}</button>
    </div>
  </div>

  <div class="tabs governance-tabs">
    <button v-for="tab in tabs" :key="tab.key" class="tab" :class="{ active: activeTab === tab.key }" @click="switchTab(tab.key)">
      <component :is="tab.icon" :size="15" />{{ tab.label }}
    </button>
  </div>

  <div v-if="success" class="governance-notice">{{ success }}</div>
  <div v-if="error" class="issue governance-error">{{ error }}</div>

  <template v-if="activeTab === 'feedback'">
    <section class="panel governance-filter-panel">
      <div class="panel-header"><h3>反馈筛选</h3><span class="muted">共 {{ filteredFeedback.length }} / {{ feedback.length }} 条</span></div>
      <div class="panel-body toolbar">
        <div class="field"><label>关键字</label><input v-model="filters.keyword" class="control" placeholder="任务/规则/产品/说明"></div>
        <div class="field"><label>反馈类型</label><select v-model="filters.feedbackType" class="control"><option value="">全部</option><option value="FALSE_POSITIVE">误报</option><option value="FALSE_NEGATIVE">漏报</option></select></div>
        <div class="field"><label>处理状态</label><select v-model="filters.processStatus" class="control"><option value="">全部</option><option v-for="status in ['NEW','PENDING','GROUPED','ANALYZING','PROPOSAL_CREATED','DEFERRED','RESOLVED','FAILED']" :key="status" :value="status">{{ status }}</option></select></div>
        <div class="field"><label>规则</label><input v-model="filters.ruleCode" class="control" placeholder="ruleCode"></div>
        <button class="btn primary" @click="load"><Search :size="16" />查询</button>
      </div>
    </section>

    <section class="panel governance-table-panel">
      <div class="panel-header"><h3>规则反馈</h3><span class="muted">{{ loading ? '加载中' : `${filteredFeedback.length} 条` }}</span></div>
      <div class="table-scroll"><table class="table rule-feedback-table">
        <thead><tr><th>任务</th><th>规则</th><th>反馈</th><th>声明信息</th><th>聚合 Key</th><th>说明与快照</th><th>审核人</th><th>时间</th></tr></thead>
        <tbody><tr v-for="row in filteredFeedback" :key="row.id">
          <td>{{ row.taskId }}</td>
          <td><strong>{{ row.ruleCode || '-' }}</strong><div class="muted">v{{ row.ruleVersionId || '-' }} / exec {{ row.ruleExecutionId || '-' }}</div></td>
          <td><span class="badge" :class="row.feedbackType === 'FALSE_POSITIVE' ? 'orange' : 'red'">{{ row.feedbackType }}</span><div class="muted">{{ row.processStatus || 'PENDING' }}</div></td>
          <td><strong>{{ row.declaredProductCode || '-' }}</strong><div class="muted">{{ row.documentCategory || '-' }} · {{ row.declaredDocumentType || '-' }}</div></td>
          <td class="muted">{{ row.aggregationKey || '-' }}</td>
          <td><div>{{ row.comment || '-' }}</div><details v-if="row.issueSnapshotJson" class="llm-debug"><summary>问题快照</summary><pre>{{ snapshotText(row.issueSnapshotJson) }}</pre></details></td>
          <td>{{ row.reviewer || '-' }}</td><td>{{ formatTime(row.createdAt) }}</td>
        </tr></tbody>
      </table></div>
      <div v-if="!filteredFeedback.length" class="empty">暂无规则反馈</div>
    </section>
  </template>

  <template v-if="activeTab === 'groups'">
    <section class="panel governance-filter-panel"><div class="panel-body toolbar">
      <div class="field"><label>分组状态</label><select v-model="filters.groupStatus" class="control"><option value="">全部</option><option v-for="status in ['PENDING','ANALYZING','PROPOSAL_CREATED','DEFERRED','RESOLVED','FAILED']" :key="status" :value="status">{{ status }}</option></select></div>
      <div class="field"><label>规则</label><input v-model="filters.ruleCode" class="control" placeholder="ruleCode"></div>
    </div></section>
    <section class="panel governance-table-panel"><div class="panel-header"><h3>治理分组</h3><span class="muted">{{ filteredGroups.length }} 组</span></div>
      <div class="table-scroll"><table class="table"><thead><tr><th>分组 / 规则</th><th>范围</th><th>反馈数</th><th>状态</th><th>最新反馈</th><th>操作</th></tr></thead>
        <tbody><tr v-for="row in filteredGroups" :key="row.id"><td><strong>#{{ row.id }} · {{ row.ruleCode || row.issueType || '待创建规则' }}</strong><div class="muted">{{ row.ruleName || row.groupKey }}</div></td><td>{{ row.documentCategory || '-' }} · {{ row.declaredFileType || '-' }}<div class="muted">{{ row.governanceIntent === 'RULE_GAP' ? '规则缺口 / 漏报' : '规则纠偏 / 误报' }} · {{ row.issueType || '-' }}</div></td><td>{{ row.feedbackCount }}</td><td><span class="badge" :class="row.status === 'FAILED' ? 'red' : row.hasProposal ? 'green' : 'gray'">{{ row.status }}</span><div v-if="row.errorMessage" class="muted">{{ row.errorMessage }}</div></td><td>{{ formatTime(row.latestFeedbackAt) }}</td><td><button class="btn" @click="openGroup(row)">查看反馈</button><button class="btn" :disabled="!['FAILED','DEFERRED'].includes(row.status)" @click="analyzeGroup(row)">重新分析</button><button class="btn danger" :disabled="deletingGroupId === row.id || row.hasProposal || !['FAILED','DEFERRED'].includes(row.status)" title="删除后来源反馈恢复为 PENDING" @click="deleteGroup(row)"><Trash2 :size="14" />{{ deletingGroupId === row.id ? '删除中…' : '删除' }}</button></td></tr></tbody>
      </table></div><div v-if="!filteredGroups.length" class="empty">暂无治理分组；反馈数达到阈值后可通过“立即聚合分析”创建。</div>
    </section>
  </template>

  <template v-if="activeTab === 'proposals'">
    <section class="panel governance-filter-panel"><div class="panel-body toolbar">
      <div class="field"><label>审批状态</label><select v-model="filters.proposalStatus" class="control"><option value="">全部</option><option v-for="status in ['PENDING_REVIEW','APPROVED','APPROVED_WITH_MODIFICATION','REJECTED','DEFERRED','APPLIED']" :key="status" :value="status">{{ status }}</option></select></div>
      <div class="field"><label>提案类型</label><select v-model="filters.proposalType" class="control"><option value="">全部</option><option v-for="type in ['UPDATE_RULE','DISABLE_RULE','CREATE_RULE','CREATE_EXCEPTION','COMPOSITE_RULE_CHANGE','OPTIMIZATION_ADVICE','NO_ACTION']" :key="type" :value="type">{{ type }}</option></select></div>
      <div class="field"><label>根因</label><select v-model="filters.rootCause" class="control"><option value="">全部</option><option v-for="type in ['RULE_SCOPE','RULE_CONFIG','RULE_EXECUTOR','RULE_EXCEPTION','PRODUCT_DATA','DOCUMENT_PARSING','LLM_POLICY','HUMAN_INCONSISTENCY','INSUFFICIENT_EVIDENCE','NO_ACTION']" :key="type" :value="type">{{ type }}</option></select></div>
      <div class="field"><label>文档类型</label><input v-model="filters.proposalDocumentType" class="control" placeholder="文件类型"></div>
      <div class="field"><label>回测风险</label><select v-model="filters.backtestRisk" class="control"><option value="">全部</option><option value="LOW">LOW</option><option value="MEDIUM">MEDIUM</option><option value="HIGH">HIGH</option></select></div>
      <div class="field"><label>最低置信度</label><input v-model="filters.minimumConfidence" type="number" min="0" max="1" step="0.05" class="control" placeholder="0.70"></div>
      <div class="field"><label>创建日期从</label><input v-model="filters.createdFrom" type="date" class="control"></div>
      <div class="field"><label>创建日期至</label><input v-model="filters.createdTo" type="date" class="control"></div>
      <div class="field"><label>规则</label><input v-model="filters.ruleCode" class="control" placeholder="ruleCode"></div>
    </div></section>
    <section class="panel governance-table-panel"><div class="panel-header"><h3>规则优化提案</h3><span class="muted">{{ filteredProposals.length }} 条</span></div>
      <div class="table-scroll"><table class="table"><thead><tr><th>提案</th><th>规则与根因</th><th>样本</th><th>置信度 / 风险</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead>
        <tbody><tr v-for="row in filteredProposals" :key="row.id"><td><strong>{{ row.proposalNo }}</strong><div class="muted">{{ row.proposalType }}<template v-if="row.actionCount"> · {{ row.actionCount }} 个动作</template></div></td><td>{{ row.ruleCode || '-' }}<div class="muted">{{ row.rootCauseType }}</div></td><td>{{ row.feedbackCount }}</td><td>{{ row.agentConfidence == null ? '-' : `${Math.round(row.agentConfidence * 100)}%` }}<div class="muted">{{ row.backtestRisk || '-' }}</div></td><td><span class="badge" :class="row.proposalStatus === 'REJECTED' ? 'red' : row.proposalStatus === 'PENDING_REVIEW' ? 'orange' : 'green'">{{ row.proposalStatus }}</span></td><td>{{ formatTime(row.createdAt) }}</td><td><button class="btn primary" @click="openProposal(row)">查看与审批</button></td></tr></tbody>
      </table></div><div v-if="!filteredProposals.length" class="empty">暂无优化提案</div>
    </section>
  </template>

  <template v-if="activeTab === 'runs'">
    <section class="grid cols-4 governance-run-summary">
      <div class="panel metric"><div class="metric-label">治理运行</div><div class="metric-value">{{ runs.length }}</div></div>
      <div class="panel metric"><div class="metric-label">已生成提案</div><div class="metric-value">{{ runs.reduce((sum, row) => sum + (row.createdProposalCount || 0), 0) }}</div></div>
      <div class="panel metric"><div class="metric-label">失败分组</div><div class="metric-value">{{ runs.reduce((sum, row) => sum + (row.failedGroupCount || 0), 0) }}</div></div>
      <div class="panel metric"><div class="metric-label">模型 Token</div><div class="metric-value">{{ runs.reduce((sum, row) => sum + tokenTotal(row), 0).toLocaleString() }}</div></div>
    </section>
    <section class="panel governance-table-panel"><div class="panel-header"><h3>治理运行记录</h3><span class="muted">{{ runs.length }} 次</span></div>
      <div class="table-scroll"><table class="table"><thead><tr><th>运行编号</th><th>触发方式</th><th>状态</th><th>扫描 / 分组 / 提案 / 跳过</th><th>Token（入 / 出 / 缓存）</th><th>耗时</th><th>开始时间</th><th>操作</th></tr></thead>
        <tbody><tr v-for="row in runs" :key="row.id"><td><strong>{{ row.runNo }}</strong><div v-if="row.errorMessage" class="muted">{{ row.errorMessage }}</div></td><td>{{ row.triggerType }}</td><td><span class="badge" :class="row.status === 'FAILED' ? 'red' : row.status === 'SUCCESS' ? 'green' : row.status === 'PARTIAL_SUCCESS' ? 'orange' : 'gray'">{{ row.status }}</span></td><td>{{ row.scannedFeedbackCount }} / {{ row.createdGroupCount }} / {{ row.createdProposalCount }} / {{ row.skippedFeedbackCount || 0 }}<div v-if="row.skipReasonSummary" class="muted">{{ row.skipReasonSummary }}</div></td><td>{{ row.inputTokens }} / {{ row.outputTokens }} / {{ row.cacheHitTokens }}</td><td>{{ row.durationMs == null ? '-' : `${row.durationMs} ms` }}</td><td>{{ formatTime(row.startedAt || row.createdAt) }}</td><td><button class="btn" @click="openTrace(row)">查看链路</button><button class="btn danger" :disabled="deletingRunId === row.id || ['RUNNING','CREATED'].includes(row.status) || row.createdProposalCount > 0" title="仅可删除未形成提案的已结束运行" @click="deleteRun(row)"><Trash2 :size="14" />{{ deletingRunId === row.id ? '删除中…' : '删除' }}</button></td></tr></tbody>
      </table></div><div v-if="!runs.length" class="empty">暂无治理运行</div>
    </section>
  </template>

  <GovernanceProposalDrawer v-if="selectedProposal" :detail="selectedProposal" :busy="proposalBusy" :error="proposalError" @close="selectedProposal = null" @action="handleProposalAction" />
  <GovernanceTraceDrawer v-if="traceRunId" :trace="selectedTrace" :loading="traceLoading" :error="traceError" @close="closeTrace" @refresh="openTrace({ id: traceRunId })" />
  <div v-if="selectedGroup" class="modal-backdrop" @click.self="selectedGroup = null">
    <section class="modal governance-group-modal" aria-label="治理分组反馈">
      <div class="modal-header"><div><strong>分组 #{{ selectedGroup.id }} 的反馈样本</strong><div class="muted">{{ selectedGroup.ruleCode || selectedGroup.issueType || '待创建规则' }} · {{ selectedGroup.documentCategory }} · {{ selectedGroup.declaredFileType || '-' }}</div></div><button class="btn ghost" @click="selectedGroup = null">关闭</button></div>
      <div class="panel-body">
        <div v-for="row in selectedGroupFeedbacks" :key="row.feedbackId" class="governance-sample"><strong>{{ row.taskNo }} · 第 {{ row.evidencePage || '-' }} 页</strong><span>{{ row.falsePositiveReason || row.issueDescription || '-' }}</span><blockquote v-if="row.evidenceText">{{ row.evidenceText }}</blockquote></div>
        <div v-if="!selectedGroupFeedbacks.length" class="empty">暂无反馈样本</div>
      </div>
    </section>
  </div>
</template>
