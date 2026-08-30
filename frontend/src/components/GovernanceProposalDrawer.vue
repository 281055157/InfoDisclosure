<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Check, FileCheck2, Pause, PlayCircle, RefreshCw, ShieldAlert, X } from 'lucide-vue-next'

const props = defineProps({ detail: Object, busy: Boolean, error: String })
const emit = defineEmits(['close', 'action'])

const action = ref('')
const form = reactive({ comment: '', reason: 'INSUFFICIENT_EVIDENCE', reviewAfter: '', candidateText: '' })
const pending = computed(() => props.detail?.summary?.proposalStatus === 'PENDING_REVIEW')
const isComposite = computed(() => props.detail?.summary?.proposalType === 'COMPOSITE_RULE_CHANGE')
const compositeActions = computed(() => props.detail?.actions || [])
const hasPendingCompositeDraft = computed(() => compositeActions.value.some(row => row.actionStatus === 'DRAFT_CREATED'))
const hasPendingCompositeDisable = computed(() => compositeActions.value
  .some(row => row.actionType === 'DISABLE_RULE' && ['DISABLE_PENDING', 'APPROVED'].includes(row.actionStatus)))
const canApply = computed(() => {
  if (props.detail?.summary?.proposalType === 'DISABLE_RULE' && props.detail?.summary?.proposalStatus === 'APPROVED') return true
  return isComposite.value
    && props.detail?.summary?.proposalStatus === 'APPROVED'
    && (hasPendingCompositeDraft.value || hasPendingCompositeDisable.value)
})
const awaitingCompositeApply = computed(() => isComposite.value
  && props.detail?.summary?.proposalStatus === 'APPROVED'
  && (hasPendingCompositeDraft.value || hasPendingCompositeDisable.value))
const compositeApplyMessage = computed(() => {
  if (hasPendingCompositeDraft.value && hasPendingCompositeDisable.value) {
    return '新规则仍为草稿，旧规则仍在运行。二次确认后将先发布并启用新规则，再停用旧规则。'
  }
  if (hasPendingCompositeDraft.value) return '规则变更仍为草稿。二次确认后将发布并启用相应版本。'
  return '新规则已发布，旧规则仍在运行。二次确认后将停用旧规则并完成组合变更。'
})
const compositeApplyLabel = computed(() => hasPendingCompositeDisable.value
  ? '发布新规则并停用旧规则'
  : '发布规则变更')
const canEvaluate = computed(() => props.detail?.summary?.proposalStatus === 'APPLIED')
const backtestRows = computed(() => {
  if (isComposite.value) {
    return (props.detail?.actions || []).map(row => ({
      key: row.id || row.sequenceNo,
      label: `#${row.sequenceNo} · ${row.ruleCode || row.actionType}`,
      result: row.backtestResult || {}
    }))
  }
  return [{ key: 'primary', label: props.detail?.summary?.ruleCode || '候选规则', result: props.detail?.backtestResult || {} }]
})

watch(() => props.detail?.summary?.id, () => {
  action.value = ''
  form.comment = ''
  form.reason = 'INSUFFICIENT_EVIDENCE'
  form.reviewAfter = ''
  form.candidateText = pretty(effectiveRule())
}, { immediate: true })

function pretty(value) {
  return JSON.stringify(value || {}, null, 2)
}

function begin(name) {
  action.value = name
  form.comment = ''
  if (name === 'modify') form.candidateText = pretty(effectiveRule())
}

function effectiveRule() {
  const finalRule = props.detail?.finalRule
  return finalRule && Object.keys(finalRule).length ? finalRule : (props.detail?.afterRule || {})
}

function hasRuleSnapshot(value) {
  return value && Object.keys(value).length > 0
}

function percent(numerator, denominator) {
  if (numerator == null || !denominator) return '-'
  return `${Math.round(numerator * 100 / denominator)}%`
}

function tokenSummary(result) {
  return `${Number(result?.llmInputTokens || 0).toLocaleString()} / ${Number(result?.llmOutputTokens || 0).toLocaleString()} / ${Number(result?.llmCacheHitTokens || 0).toLocaleString()}`
}

function submit() {
  let payload = { comment: form.comment }
  if (action.value === 'modify') {
    try {
      payload = { candidateRule: JSON.parse(form.candidateText), comment: form.comment }
    } catch (_) {
      emit('action', { type: 'client-error', message: '候选规则不是合法 JSON' })
      return
    }
  }
  if (action.value === 'reject') {
    if (form.reason === 'OTHER' && !form.comment.trim()) {
      emit('action', { type: 'client-error', message: '拒绝原因为“其他”时必须填写审核意见' })
      return
    }
    payload = { reason: form.reason, comment: form.comment }
  }
  if (action.value === 'defer') {
    if (!form.comment.trim()) {
      emit('action', { type: 'client-error', message: '暂缓原因不能为空' })
      return
    }
    payload = { reason: form.comment, reviewAfter: form.reviewAfter ? new Date(form.reviewAfter).toISOString() : null }
  }
  emit('action', { type: action.value, payload })
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <div class="form-page-backdrop" @click.self="emit('close')">
    <aside class="form-page governance-proposal-drawer" aria-label="治理提案详情">
      <header class="form-page-header">
        <div>
          <h3>{{ detail?.summary?.proposalNo || '治理提案' }}</h3>
          <div class="muted">{{ detail?.summary?.ruleCode || '-' }} · {{ detail?.summary?.proposalType || '-' }}</div>
        </div>
        <button class="btn ghost" aria-label="关闭" @click="emit('close')"><X :size="18" /></button>
      </header>

      <div class="form-page-body governance-detail" v-if="detail">
        <div class="governance-kpis">
          <div><span>状态</span><strong>{{ detail.summary.proposalStatus }}</strong></div>
          <div><span>置信度</span><strong>{{ detail.summary.agentConfidence == null ? '-' : `${Math.round(detail.summary.agentConfidence * 100)}%` }}</strong></div>
          <div><span>回测风险</span><strong>{{ detail.summary.backtestRisk || '-' }}</strong></div>
          <div><span>反馈样本</span><strong>{{ detail.summary.feedbackCount }}</strong></div>
        </div>

        <div v-if="awaitingCompositeApply" class="governance-callout governance-application-pending">
          <strong>审批已通过，规则变更尚未生效</strong>
          <span>{{ compositeApplyMessage }}</span>
        </div>

        <section class="governance-section">
          <h4>问题与根因</h4>
          <p>{{ detail.problemSummary || '-' }}</p>
          <div class="governance-callout"><strong>{{ detail.summary.rootCauseType }}</strong><span>{{ detail.rootCauseAnalysis || '-' }}</span></div>
        </section>

        <section class="governance-section">
          <h4>调整建议</h4>
          <p>{{ detail.changeReason || detail.optimizationAdvice || '-' }}</p>
          <dl class="governance-meta">
            <dt>预期效果</dt><dd>{{ detail.expectedEffect || '-' }}</dd>
            <dt>风险说明</dt><dd>{{ detail.riskDescription || '-' }}</dd>
            <dt>模型</dt><dd>{{ [detail.agentProvider, detail.agentModel].filter(Boolean).join(' / ') || '-' }}</dd>
          </dl>
        </section>

        <section class="governance-section">
          <h4>规则前后对比</h4>
          <div v-if="isComposite && detail.actions?.length" class="governance-action-diffs">
            <div v-for="row in detail.actions" :key="row.id || row.sequenceNo" class="governance-action-diff">
              <div class="governance-callout">
                <strong>#{{ row.sequenceNo }} · {{ row.actionType }}</strong>
                <span>{{ row.ruleCode || '-' }} · {{ row.actionStatus || '-' }}</span>
              </div>
              <div class="governance-diff">
                <div><span>调整前</span><pre>{{ pretty(row.beforeRule) }}</pre></div>
                <div><span>调整后</span><pre>{{ pretty(row.afterRule) }}</pre></div>
              </div>
            </div>
          </div>
          <div v-else-if="!hasRuleSnapshot(effectiveRule()) && ['OPTIMIZATION_ADVICE','NO_ACTION'].includes(detail.summary.proposalType)" class="empty">
            该提案不包含规则变更；请查看上方治理建议。
          </div>
          <div v-else class="governance-diff">
            <div><span>调整前</span><pre>{{ pretty(detail.beforeRule) }}</pre></div>
            <div><span>调整后</span><pre>{{ pretty(effectiveRule()) }}</pre></div>
          </div>
        </section>

        <section class="governance-section">
          <h4>回测结果</h4>
          <div v-for="row in backtestRows" :key="row.key" class="governance-backtest-block">
            <div class="governance-callout">
              <strong>{{ row.label }} · {{ row.result.executionStatus || '历史回测' }}</strong>
              <span>{{ row.result.executorType || '-' }} · 风险 {{ row.result.riskLevel || '-' }}</span>
            </div>
            <div class="governance-kpis compact">
              <div><span>样本（误报/确认/正常）</span><strong>{{ row.result.falsePositiveSampleCount ?? 0 }} / {{ row.result.confirmedPositiveSampleCount ?? 0 }} / {{ row.result.normalSampleCount ?? 0 }}</strong></div>
              <div><span>反馈样本 / 唯一文档</span><strong>{{ row.result.sampleCount ?? 0 }} / {{ row.result.uniqueDocumentCount ?? row.result.sampleCount ?? 0 }}</strong></div>
              <div><span>可判定 / 不确定</span><strong>{{ row.result.determinateSampleCount ?? '-' }} / {{ row.result.unresolvedSampleCount ?? '-' }}</strong></div>
              <div><span>模型调用批次</span><strong>{{ row.result.llmCallCount ?? 0 }}</strong></div>
              <div><span>Token（入/出/缓存）</span><strong>{{ tokenSummary(row.result) }}</strong></div>
              <div><span>误报修复率</span><strong>{{ percent(row.result.resolvedFalsePositiveCount, row.result.falsePositiveSampleCount) }}</strong></div>
              <div><span>正样本丢失</span><strong>{{ row.result.lostConfirmedPositiveCount ?? '-' }}</strong></div>
              <div><span>正常样本新增命中</span><strong>{{ row.result.newUnexpectedHitCount ?? '-' }}</strong></div>
              <div><span>窗口/证据详情</span><strong>{{ row.result.details?.length ?? 0 }}</strong></div>
            </div>
            <div v-if="row.result.coverageWarnings?.length" class="governance-callout governance-warning">
              <strong>样本覆盖或调用警告</strong><span>{{ row.result.coverageWarnings.join('；') }}</span>
            </div>
            <details v-if="row.result.details?.length" class="llm-debug">
              <summary>查看逐样本判定与证据</summary><pre>{{ pretty(row.result.details) }}</pre>
            </details>
          </div>
          <details class="llm-debug"><summary>查看完整校验与回测数据</summary><pre>{{ pretty({ validation: detail.validationResult, backtest: detail.backtestResult }) }}</pre></details>
        </section>

        <section class="governance-section">
          <h4>原始反馈样本</h4>
          <div v-for="row in detail.feedbacks" :key="row.feedbackId" class="governance-sample">
            <strong>{{ row.taskNo }} · 第 {{ row.evidencePage || '-' }} 页</strong>
            <span>{{ row.falsePositiveReason || row.issueDescription || '-' }}</span>
            <blockquote v-if="row.evidenceText">{{ row.evidenceText }}</blockquote>
          </div>
          <div v-if="!detail.feedbacks?.length" class="empty">暂无样本</div>
        </section>

        <section class="governance-section">
          <h4>人工决策与审计</h4>
          <div v-for="row in detail.auditTrail" :key="row.id" class="timeline-card">
            <strong>{{ row.operationType }}</strong>
            <div>{{ row.operator }} · {{ formatTime(row.createdAt) }}</div>
            <div class="muted">{{ row.detail || '-' }}</div>
          </div>
          <div v-if="!detail.auditTrail?.length" class="empty">尚无人工决策</div>
        </section>

        <div v-if="error" class="issue governance-error">{{ error }}</div>

        <section v-if="action" class="governance-action-box">
          <h4>{{ ({ approve: isComposite ? '批准并创建规则草稿' : '批准提案', modify: '修改后批准', reject: '拒绝提案', defer: '暂缓处理', apply: isComposite ? '确认应用组合规则变更' : '二次确认停用规则', evaluate: '评估治理效果' })[action] }}</h4>
          <div v-if="action === 'modify'" class="field"><label>候选规则 JSON</label><textarea v-model="form.candidateText" class="control code-editor"></textarea></div>
          <div v-if="action === 'reject'" class="field"><label>拒绝原因</label><select v-model="form.reason" class="control"><option value="ROOT_CAUSE_WRONG">根因判断错误</option><option value="CHANGE_TOO_BROAD">变更范围过宽</option><option value="BUSINESS_RULE_INCORRECT">业务口径不正确</option><option value="INSUFFICIENT_EVIDENCE">证据不足</option><option value="BACKTEST_FAILED">回测未通过</option><option value="DUPLICATE_PROPOSAL">重复提案</option><option value="TEMPORARY_EXCEPTION">临时例外</option><option value="TECHNICAL_SOLUTION_INCORRECT">技术方案不正确</option><option value="OTHER">其他</option></select></div>
          <div v-if="action === 'defer'" class="field"><label>建议复核时间</label><input v-model="form.reviewAfter" type="datetime-local" class="control"></div>
          <div v-if="!['evaluate'].includes(action)" class="field"><label>{{ action === 'defer' ? '暂缓原因' : '审核意见' }}</label><textarea v-model="form.comment" class="control" :required="action === 'defer' || (action === 'reject' && form.reason === 'OTHER')"></textarea></div>
          <div class="governance-action-buttons"><button class="btn" @click="action = ''">取消</button><button class="btn primary" :disabled="busy" @click="submit">{{ busy ? '处理中…' : '确认提交' }}</button></div>
        </section>
      </div>

      <footer class="form-page-footer governance-footer" v-if="detail && !action">
        <template v-if="pending">
          <button class="btn" @click="begin('defer')"><Pause :size="15" />暂缓</button>
          <button class="btn danger" @click="begin('reject')"><ShieldAlert :size="15" />拒绝</button>
          <button class="btn" v-if="['UPDATE_RULE','CREATE_RULE','CREATE_EXCEPTION'].includes(detail.summary.proposalType)" @click="begin('modify')"><FileCheck2 :size="15" />修改后批准</button>
          <button class="btn primary" @click="begin('approve')"><Check :size="15" />{{ isComposite ? '批准并创建草稿' : '批准' }}</button>
        </template>
        <button v-if="canApply" class="btn danger" @click="begin('apply')"><PlayCircle :size="15" />{{ isComposite ? compositeApplyLabel : '确认停用规则' }}</button>
        <button v-if="canEvaluate" class="btn" @click="begin('evaluate')"><RefreshCw :size="15" />评估效果</button>
      </footer>
    </aside>
  </div>
</template>
