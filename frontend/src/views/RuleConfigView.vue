<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { CheckCircle2, Plus, RefreshCw, Save, Send, X } from 'lucide-vue-next'
import { api } from '../api'
import StatusBadge from '../components/StatusBadge.vue'
import RuleTemplateSelector from '../components/RuleTemplateSelector.vue'
import RuleScopeEditor from '../components/RuleScopeEditor.vue'
import ConditionBuilder from '../components/ConditionBuilder.vue'
import ActionEditor from '../components/ActionEditor.vue'
import PromptPolicyEditor from '../components/PromptPolicyEditor.vue'
import HybridRuleEditor from '../components/HybridRuleEditor.vue'
import RuleTestWorkbench from '../components/RuleTestWorkbench.vue'
import RuleVersionTimeline from '../components/RuleVersionTimeline.vue'
import RulePublishDialog from '../components/RulePublishDialog.vue'
import RuleExecutionDrawer from '../components/RuleExecutionDrawer.vue'

const rules = ref([])
const schemas = ref({})
const detail = ref(null)
const executions = ref([])
const formOpen = ref(false)
const message = ref('')
const saving = ref(false)
const testing = ref(false)
const publishOpen = ref(false)
const currentStep = ref(0)
const selectedVersion = ref(null)
const testResult = ref(null)
const testError = ref('')
const executionDrawerOpen = ref(false)
const executionDrawerRule = ref(null)
const executionDrawerRows = ref([])
const executionDrawerLoading = ref(false)

const steps = ['模板', '基础', '范围', '条件', '输出', '测试', '发布']

const ruleForm = reactive(defaultRule())
const versionForm = reactive(defaultVersion())
const testPayload = reactive({
  documentCategory: 'PROTOCOL',
  documentType: 'CUSTOMER_RIGHTS_NOTICE',
  declaredProductCode: '',
  declaredDocumentType: '',
  b9Value: '',
  testText: '示例理财甲产品按照风险程度从低到高分为五级，包括：低风险产品（R1）、中低风险产品（R2）、中低风险产品（R3）、中高风险产品（R4）、高风险产品（R5）。'
})

const canModifyVersion = computed(() => !selectedVersion.value || selectedVersion.value.status === 'DRAFT')

function defaultRule() {
  return {
    id: null,
    ruleCode: '',
    ruleName: '',
    ruleType: 'JAVA_PLUGIN',
    ruleCategory: 'JAVA_PLUGIN',
    enabled: true,
    severity: 'MEDIUM',
    confidence: 1,
    documentTypes: '',
    productScope: '',
    parametersJson: '{}',
    versionCode: 'v1',
    activeVersionId: null,
    priority: 100
  }
}

function defaultVersion() {
  return {
    id: null,
    description: '',
    executorType: 'JAVA_PLUGIN',
    scopeJson: '{"documentCategories":[],"documentTypes":[],"productCodes":[],"productTypes":[]}',
    conditionJson: '{"pluginCode":"PRODUCT_CODE_EXTRACTION"}',
    actionJson: '{"source":"RULE"}',
    promptJson: '{}',
    changeSummary: ''
  }
}

async function load() {
  const [ruleRows, schemaRows] = await Promise.all([api.rules(), api.executorSchemas()])
  rules.value = ruleRows
  schemas.value = schemaRows.schemas || {}
}

function openCreate() {
  Object.assign(ruleForm, defaultRule())
  Object.assign(versionForm, defaultVersion())
  detail.value = null
  executions.value = []
  selectedVersion.value = null
  testResult.value = null
  testError.value = ''
  message.value = ''
  currentStep.value = 0
  formOpen.value = true
}

async function openEdit(rule) {
  Object.assign(ruleForm, defaultRule(), rule)
  detail.value = await api.rule(rule.id)
  executions.value = await api.ruleExecutions(rule.id)
  const versions = detail.value.versions || []
  const draft = versions.find(v => v.status === 'DRAFT')
  const active = versions.find(v => v.id === detail.value.rule.activeVersionId)
  selectVersion(draft || active || versions[0] || null)
  message.value = ''
  currentStep.value = 0
  formOpen.value = true
}

function selectVersion(version) {
  selectedVersion.value = version
  Object.assign(versionForm, defaultVersion(), version || {})
  testResult.value = null
  testError.value = ''
}

function closeForm() {
  formOpen.value = false
}

function setStep(index) {
  currentStep.value = index
}

async function saveDraft() {
  saving.value = true
  message.value = ''
  try {
    let savedRule
    if (ruleForm.id) {
      savedRule = await api.saveRule(ruleForm)
    } else {
      savedRule = await api.saveRule(ruleForm)
      ruleForm.id = savedRule.id
    }

    const payload = versionPayload()
    let savedVersion
    if (!selectedVersion.value) {
      savedVersion = await api.createRuleVersion(ruleForm.id, payload)
    } else if (selectedVersion.value.status === 'DRAFT') {
      savedVersion = await api.updateRuleVersion(ruleForm.id, selectedVersion.value.id, payload)
    } else {
      savedVersion = await api.createRuleVersion(ruleForm.id, payload)
    }
    await reloadDetail(ruleForm.id, savedVersion.id)
    await load()
    message.value = '草稿已保存'
  } finally {
    saving.value = false
  }
}

async function validateVersion() {
  await ensureDraftSaved()
  const result = await api.validateRuleVersion(ruleForm.id, selectedVersion.value.id)
  message.value = result.valid ? '校验通过' : `校验失败：${(result.errors || []).join('；')}`
}

async function runTest() {
  testing.value = true
  testResult.value = null
  testError.value = ''
  message.value = ''
  try {
    await ensureDraftSaved()
    testResult.value = await api.testRuleVersion(ruleForm.id, selectedVersion.value.id, { ...testPayload })
    message.value = `测试完成：${testResult.value.status}${testResult.value.detail ? '，' + testResult.value.detail : ''}`
  } catch (error) {
    testError.value = readableError(error)
    message.value = `测试失败：${testError.value}`
  } finally {
    testing.value = false
  }
}

async function publishVersion() {
  await ensureDraftSaved()
  publishOpen.value = true
}

async function confirmPublish() {
  const published = await api.publishRuleVersion(ruleForm.id, selectedVersion.value.id)
  publishOpen.value = false
  await reloadDetail(ruleForm.id, published.id)
  await load()
  message.value = '版本已发布并切换为当前生效版本'
}

async function ensureDraftSaved() {
  if (!ruleForm.id || !selectedVersion.value || selectedVersion.value.status !== 'DRAFT') {
    await saveDraft()
  }
}

async function reloadDetail(ruleId, selectedVersionId = null) {
  detail.value = await api.rule(ruleId)
  executions.value = await api.ruleExecutions(ruleId)
  Object.assign(ruleForm, defaultRule(), detail.value.rule)
  const versions = detail.value.versions || []
  selectVersion(versions.find(v => v.id === selectedVersionId)
    || versions.find(v => v.status === 'DRAFT')
    || versions.find(v => v.id === detail.value.rule.activeVersionId)
    || versions[0]
    || null)
}

async function toggle(rule) {
  await api.setRuleEnabled(rule.id, !rule.enabled)
  await load()
}

async function openExecutions(rule) {
  executionDrawerRule.value = rule
  executionDrawerOpen.value = true
  await loadExecutionDrawerRows()
}

async function loadExecutionDrawerRows() {
  if (!executionDrawerRule.value?.id) return
  executionDrawerLoading.value = true
  try {
    executionDrawerRows.value = await api.ruleExecutions(executionDrawerRule.value.id)
  } finally {
    executionDrawerLoading.value = false
  }
}

function versionPayload() {
  return {
    description: versionForm.description,
    executorType: versionForm.executorType,
    scopeJson: versionForm.scopeJson,
    conditionJson: versionForm.conditionJson,
    actionJson: versionForm.actionJson,
    promptJson: versionForm.promptJson,
    changeSummary: versionForm.changeSummary,
    active: false
  }
}

function applyTemplate(value) {
  versionForm.executorType = value
  ruleForm.ruleType = value
  ruleForm.ruleCategory = value === 'HYBRID' || value === 'LLM_POLICY' || value === 'JAVA_PLUGIN' ? value : 'HARD_CONFIG'
}

function setTestPayload(value) {
  Object.assign(testPayload, value)
}

function readableError(error) {
  const raw = error?.message || String(error || '')
  try {
    const parsed = JSON.parse(raw)
    return parsed.message || parsed.error || raw
  } catch {
    return raw
  }
}

onMounted(load)
</script>

<template>
  <div class="page-title">
    <div>
      <h2>规则模板管理</h2>
      <div class="subtitle">按规则定义、草稿版本、发布版本和执行记录管理审核规则</div>
    </div>
    <div style="display:flex;gap:8px">
      <button class="btn" @click="load"><RefreshCw :size="16" />刷新</button>
      <button class="btn primary" @click="openCreate"><Plus :size="16" />新增规则</button>
    </div>
  </div>

  <section class="panel">
    <div class="panel-header"><h3>规则列表</h3><span class="muted">{{ rules.length }} 条</span></div>
    <table class="table rule-table">
      <thead><tr><th>规则</th><th>执行器</th><th>版本</th><th>优先级</th><th>严重程度</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="rule in rules" :key="rule.id">
          <td><strong>{{ rule.ruleCode }}</strong><div class="muted">{{ rule.ruleName }}</div></td>
          <td>{{ rule.ruleType || rule.ruleCategory || '-' }}</td>
          <td>{{ rule.versionCode || '-' }}</td>
          <td>{{ rule.priority ?? '-' }}</td>
          <td><StatusBadge :value="rule.severity" type="severity" /></td>
          <td><span class="badge" :class="rule.enabled ? 'green' : 'gray'">{{ rule.enabled ? '启用' : '停用' }}</span></td>
          <td>
            <div class="action-btns">
              <button class="btn btn-sm" @click="openEdit(rule)">编辑</button>
              <button class="btn btn-sm" @click="openExecutions(rule)">执行记录</button>
              <button class="btn btn-sm" @click="toggle(rule)">{{ rule.enabled ? '停用' : '启用' }}</button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-if="!rules.length" class="empty">暂无规则配置</div>
  </section>

  <Transition name="slide-page">
    <div v-if="formOpen" class="form-page-backdrop">
      <aside class="form-page rule-form-page">
        <div class="form-page-header">
          <div>
            <h3>{{ ruleForm.id ? '编辑规则' : '新增规则' }}</h3>
            <div class="muted">{{ selectedVersion?.versionCode || '新草稿' }} · {{ canModifyVersion ? '可编辑' : '已发布版本将另存为新草稿' }}</div>
          </div>
          <button class="btn ghost" @click="closeForm"><X :size="18" /></button>
        </div>

        <div class="form-page-body">
          <div class="stepper">
            <button
              v-for="(step, index) in steps"
              :key="step"
              type="button"
              :class="{ active: currentStep === index }"
              @click="setStep(index)"
            >
              <span>{{ index + 1 }}</span>{{ step }}
            </button>
          </div>

          <section v-if="currentStep === 0" class="panel">
            <div class="panel-header"><h3>模板选择</h3><span class="muted">选择执行器类型</span></div>
            <div class="panel-body">
              <RuleTemplateSelector :model-value="versionForm.executorType" :schemas="schemas" @update:model-value="applyTemplate" />
            </div>
          </section>

          <section v-if="currentStep === 1" class="panel">
            <div class="panel-header"><h3>基础信息</h3><span class="muted">规则定义不随版本变化</span></div>
            <div class="panel-body grid cols-2">
              <div class="field"><label>规则编码</label><input v-model="ruleForm.ruleCode" class="control" placeholder="CONTENT_LOGIC_CONFLICT"></div>
              <div class="field"><label>规则名称</label><input v-model="ruleForm.ruleName" class="control" placeholder="正文逻辑冲突"></div>
              <div class="field"><label>规则类型</label><input v-model="ruleForm.ruleType" class="control"></div>
              <div class="field"><label>规则分类</label><input v-model="ruleForm.ruleCategory" class="control"></div>
              <div class="field"><label>严重程度</label><select v-model="ruleForm.severity" class="control"><option>NORMAL</option><option>LOW</option><option>MEDIUM</option><option>HIGH</option></select></div>
              <div class="field"><label>置信度</label><input v-model.number="ruleForm.confidence" type="number" min="0" max="1" step="0.01" class="control"></div>
              <div class="field"><label>优先级</label><input v-model.number="ruleForm.priority" type="number" class="control"></div>
              <label class="switch"><input v-model="ruleForm.enabled" type="checkbox">启用规则</label>
              <div class="field" style="grid-column:1/-1"><label>版本说明</label><input v-model="versionForm.description" class="control"></div>
              <div class="field" style="grid-column:1/-1"><label>变更摘要</label><input v-model="versionForm.changeSummary" class="control"></div>
            </div>
          </section>

          <section v-if="currentStep === 2" class="panel">
            <div class="panel-header"><h3>适用范围</h3><span class="muted">scope_json</span></div>
            <div class="panel-body">
              <RuleScopeEditor v-model="versionForm.scopeJson" />
            </div>
          </section>

          <section v-if="currentStep === 3" class="panel">
            <div class="panel-header"><h3>条件构造</h3><span class="muted">{{ versionForm.executorType }}</span></div>
            <div class="panel-body">
              <HybridRuleEditor
                v-if="versionForm.executorType === 'HYBRID'"
                v-model:condition-json="versionForm.conditionJson"
                v-model:prompt-json="versionForm.promptJson"
              />
              <ConditionBuilder v-else v-model="versionForm.conditionJson" :executor-type="versionForm.executorType" />
            </div>
          </section>

          <section v-if="currentStep === 4" class="panel">
            <div class="panel-header"><h3>问题输出</h3><span class="muted">action_json / prompt_json</span></div>
            <div class="panel-body grid cols-2">
              <ActionEditor v-model="versionForm.actionJson" />
              <PromptPolicyEditor v-model="versionForm.promptJson" />
            </div>
          </section>

          <section v-if="currentStep === 5" class="panel">
            <div class="panel-header"><h3>测试工作台</h3><span class="muted">保存草稿后执行</span></div>
            <div class="panel-body">
              <RuleTestWorkbench :model-value="testPayload" :result="testResult" :error="testError" :running="testing" @update:model-value="setTestPayload" @run="runTest" />
            </div>
          </section>

          <section v-if="currentStep === 6" class="panel">
            <div class="panel-header"><h3>发布与追踪</h3><span class="muted">版本不可变，执行可追溯</span></div>
            <div class="panel-body">
              <RuleVersionTimeline
                :versions="detail?.versions || []"
                :executions="executions"
                :active-version-id="detail?.rule?.activeVersionId"
                @select="selectVersion"
              />
            </div>
          </section>

          <div v-if="message" class="llm-note">{{ message }}</div>
        </div>

        <div class="form-page-footer">
          <button class="btn" :disabled="currentStep === 0" @click="currentStep--">上一步</button>
          <button class="btn" :disabled="currentStep === steps.length - 1" @click="currentStep++">下一步</button>
          <button class="btn" @click="validateVersion"><CheckCircle2 :size="16" />校验</button>
          <button class="btn" @click="saveDraft" :disabled="saving"><Save :size="16" />{{ saving ? '保存中' : '保存草稿' }}</button>
          <button class="btn primary" @click="publishVersion"><Send :size="16" />发布</button>
        </div>
      </aside>
    </div>
  </Transition>

  <RulePublishDialog :open="publishOpen" :version="selectedVersion" @close="publishOpen = false" @confirm="confirmPublish" />
  <RuleExecutionDrawer
    :open="executionDrawerOpen"
    :rule="executionDrawerRule"
    :executions="executionDrawerRows"
    :loading="executionDrawerLoading"
    @close="executionDrawerOpen = false"
    @refresh="loadExecutionDrawerRows"
  />
</template>

<style scoped>
.rule-table { font-size: 13px; }
.rule-table th, .rule-table td { padding: 10px 10px; }
.rule-table th:nth-child(1) { width: 26%; }
.rule-table th:nth-child(2) { width: 12%; }
.rule-table th:nth-child(3) { width: 7%; }
.rule-table th:nth-child(4) { width: 8%; }
.rule-table th:nth-child(5) { width: 11%; }
.rule-table th:nth-child(6) { width: 8%; }
.rule-table th:nth-child(7) { width: 28%; }
.rule-table td strong { font-size: 13px; }
.rule-table td .muted { font-size: 12px; }
.action-btns { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.btn-sm { min-height: 28px; padding: 4px 10px; font-size: 12px; border-radius: 5px; }
.rule-form-page { width: min(1120px, calc(100vw - 28px)); }
.stepper { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 8px; margin-bottom: 14px; }
.stepper button { min-width: 0; border: 1px solid var(--line); border-radius: 8px; background: white; padding: 8px 6px; display: flex; align-items: center; justify-content: center; gap: 6px; color: var(--muted); }
.stepper button.active { color: var(--primary); border-color: var(--primary); background: #eff6ff; }
.stepper span { width: 20px; height: 20px; border-radius: 999px; display: inline-grid; place-items: center; background: #eef2ff; font-size: 12px; }
.form-page-body > .panel + .llm-note { margin-top: 12px; }
@media (max-width: 980px) { .stepper { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
