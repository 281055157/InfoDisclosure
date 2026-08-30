import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.route('**/api/admin/rules/feedback?**', route => route.fulfill({
    status: 200, contentType: 'application/json', body: '[]'
  }))
})

test('manual governance run shows grouped workload and token usage', async ({ page }) => {
  await page.route('**/api/rule-governance/runs', async route => {
    if (route.request().method() === 'POST') {
      expect(route.request().headers()['x-operator']).toBe('demo-user')
      await route.fulfill({ status: 202, contentType: 'application/json', body: JSON.stringify(runRow({ id: 2, runNo: 'RGR-MANUAL-2', status: 'RUNNING' })) })
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([runRow({ id: 1 })]) })
    }
  })

  await page.goto('/#/rule-feedback')
  await page.getByRole('button', { name: '治理运行' }).click()
  await expect(page.getByText('RGR-TEST-1')).toBeVisible()
  await expect(page.getByText('1200 / 300 / 100')).toBeVisible()
  await expect(page.getByText('RULE_CODE_VERSION_MISMATCH=3')).toBeVisible()

  await page.getByRole('button', { name: '立即聚合分析' }).click()
  await expect(page.getByText('RGR-MANUAL-2', { exact: true })).toBeVisible()
  await expect(page.getByText(/3 条反馈未进入分析：RULE_CODE_VERSION_MISMATCH=3/)).toBeVisible()
})

test('governance run trace explains no-op and renders serial and parallel spans', async ({ page }) => {
  await page.route('**/api/rule-governance/runs', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify([
      runRow({ id: 3, runNo: 'RGR-NOOP-3', scannedFeedbackCount: 0, createdGroupCount: 0, createdProposalCount: 0 })
    ])
  }))
  await page.route('**/api/rule-governance/runs/3/trace', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(traceResponse())
  }))

  await page.goto('/#/rule-feedback')
  await page.getByRole('button', { name: '治理运行' }).click()
  await page.getByRole('button', { name: '查看链路' }).click()

  const drawer = page.getByRole('dialog', { name: '大模型调用链路' })
  await expect(drawer).toBeVisible()
  await expect(drawer.getByText('NO_ELIGIBLE_FEEDBACK', { exact: true })).toBeVisible()
  await expect(drawer.getByText(/FAILED\/DEFERRED 分组不会被顶部聚合重复扫描/)).toBeVisible()
  await expect(drawer.getByText(/并行分叉 · 2 条/)).toHaveCount(2)
  await expect(drawer.getByText('LLM_ATTEMPT', { exact: true })).toBeVisible()
  await expect(drawer.getByText('TOOL_BATCH', { exact: true })).toBeVisible()
  await expect(drawer.getByText('TOOL_CALL', { exact: true })).toHaveCount(2)
  await expect(drawer.locator('.trace-tool_call > .trace-node-head strong', { hasText: 'validateRuleConfig' })).toBeVisible()
  await expect(drawer.locator('.trace-tool_call > .trace-node-head strong', { hasText: 'runRuleBacktest' })).toBeVisible()
  await expect(drawer.getByText('串行推进').first()).toBeVisible()
  const modelResult = drawer.locator('.trace-model-response')
  await expect(modelResult.getByText('Assistant Message', { exact: true })).not.toBeVisible()
  await modelResult.getByText('查看模型返回结果', { exact: true }).click()
  await expect(modelResult.getByText('Assistant Message', { exact: true })).toBeVisible()
  await expect(modelResult.getByText('Tool Calls（1）', { exact: true })).toBeVisible()
  await expect(modelResult.getByText('call-validate', { exact: true })).toBeVisible()
})

test('governance retry trace keeps the latest execution visible and collapses history', async ({ page }) => {
  await page.route('**/api/rule-governance/runs', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify([
      runRow({ id: 8, runNo: 'RGR-RETRY-8', createdGroupCount: 1, createdProposalCount: 1 })
    ])
  }))
  await page.route('**/api/rule-governance/runs/8/trace', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(retryTraceResponse())
  }))

  await page.goto('/#/rule-feedback')
  await page.getByRole('button', { name: '治理运行' }).click()
  await page.getByRole('button', { name: '查看链路' }).click()

  const drawer = page.getByRole('dialog', { name: '大模型调用链路' })
  await expect(drawer.getByText('治理分组 #5 · 共 2 次执行', { exact: true })).toHaveCount(1)
  await expect(drawer.getByText('当前执行 Agent', { exact: true })).toBeVisible()
  await expect(drawer.getByText('历史执行 Agent', { exact: true })).not.toBeVisible()
  await drawer.getByRole('button', { name: '展开历史链路' }).click()
  await expect(drawer.getByText('历史执行 Agent', { exact: true })).toBeVisible()
  await drawer.getByRole('button', { name: '收起历史链路' }).click()
  await expect(drawer.getByText('历史执行 Agent', { exact: true })).not.toBeVisible()
})

test('deleting governance group and run requires confirmation and releases feedback', async ({ page }) => {
  let deletedGroup = false
  let deletedRun = false
  await page.route('**/api/rule-governance/groups?**', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify([groupRow()])
  }))
  await page.route('**/api/rule-governance/groups/5', route => {
    deletedGroup = route.request().method() === 'DELETE'
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ deletedGroupId: 5, releasedFeedbackCount: 5 }) })
  })
  await page.route('**/api/rule-governance/runs', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify([runRow({
      id: 6, runNo: 'RGR-DELETE-6', status: 'SUCCESS', createdGroupCount: 0, createdProposalCount: 0
    })])
  }))
  await page.route('**/api/rule-governance/runs/6', route => {
    deletedRun = route.request().method() === 'DELETE'
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ deletedRunId: 6, releasedFeedbackCount: 0 }) })
  })

  await page.goto('/#/rule-feedback')
  await page.getByRole('button', { name: '治理分组' }).click()
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', { name: '删除' }).click()
  await expect.poll(() => deletedGroup).toBe(true)
  await expect(page.getByText(/5 条反馈已恢复为 PENDING/)).toBeVisible()

  await page.getByRole('button', { name: '治理运行' }).click()
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', { name: '删除' }).click()
  await expect.poll(() => deletedRun).toBe(true)
  await expect(page.getByText(/治理运行 RGR-DELETE-6 已删除/)).toBeVisible()
})

test('proposal drawer shows evidence and approves without publishing rule', async ({ page }) => {
  let approvePayload
  await mockProposalList(page)
  await page.route('**/api/rule-governance/proposals/7', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(proposalDetail())
  }))
  await page.route('**/api/rule-governance/proposals/7/approve', async route => {
    approvePayload = route.request().postDataJSON()
    expect(route.request().headers()['x-operator']).toBe('demo-user')
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(proposalDetail({ proposalStatus: 'APPROVED' })) })
  })

  await page.goto('/#/rule-feedback')
  await page.getByRole('button', { name: '优化提案' }).click()
  await page.getByRole('button', { name: '查看与审批' }).click()
  await expect(page.getByRole('complementary', { name: '治理提案详情' })).toBeVisible()
  await expect(page.getByText('原始反馈样本')).toBeVisible()
  await expect(page.getByText('标题与正文矛盾')).toBeVisible()
  await expect(page.getByText('回测结果')).toBeVisible()
  await expect(page.getByText('TEST_RULE · COMPLETED')).toBeVisible()
  await expect(page.getByText('模型调用批次')).toBeVisible()
  await expect(page.getByText('12,500 / 860 / 2,000')).toBeVisible()
  await page.getByText('查看逐样本判定与证据').click()
  await expect(page.locator('details').filter({ hasText: '查看逐样本判定与证据' }).locator('pre')).toContainText('模型判定未命中')

  await page.getByRole('button', { name: '批准', exact: true }).click()
  await page.locator('.governance-action-box textarea').fill('批准生成草稿，发布仍由规则管理员执行')
  await page.getByRole('button', { name: '确认提交' }).click()

  await expect.poll(() => approvePayload?.comment).toContain('生成草稿')
  await expect(page.getByRole('complementary', { name: '治理提案详情' }).getByText('APPROVED', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '批准', exact: true })).not.toBeVisible()
})

test('modified approval validates JSON and surfaces concurrent review conflict', async ({ page }) => {
  let modifiedPayload
  await mockProposalList(page)
  await page.route('**/api/rule-governance/proposals/7', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(proposalDetail())
  }))
  await page.route('**/api/rule-governance/proposals/7/approve-with-modification', async route => {
    modifiedPayload = route.request().postDataJSON()
    await route.fulfill({ status: 409, contentType: 'text/plain', body: '提案已被其他用户处理' })
  })

  await page.goto('/#/rule-feedback')
  await page.getByRole('button', { name: '优化提案' }).click()
  await page.getByRole('button', { name: '查看与审批' }).click()
  await page.getByRole('button', { name: '修改后批准' }).click()
  const editor = page.locator('.code-editor')
  await editor.fill('{bad json')
  await page.getByRole('button', { name: '确认提交' }).click()
  await expect(page.getByText('候选规则不是合法 JSON')).toBeVisible()

  await editor.fill(JSON.stringify(candidateRule()))
  await page.getByRole('button', { name: '确认提交' }).click()
  await expect.poll(() => modifiedPayload?.candidateRule?.ruleCode).toBe('TEST_RULE')
  await expect(page.getByText('提案已被其他用户处理')).toBeVisible()
})

test('proposal drawer renders composite action diffs and advice without empty after-rule', async ({ page }) => {
  await page.route('**/api/rule-governance/proposals?**', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify([
      proposalSummary({ id: 8, proposalNo: 'RCP-COMPOSITE-8', proposalType: 'COMPOSITE_RULE_CHANGE', rootCauseType: 'RULE_EXECUTOR', actionCount: 2 }),
      proposalSummary({ id: 9, proposalNo: 'RCP-ADVICE-9', proposalType: 'OPTIMIZATION_ADVICE', rootCauseType: 'LLM_POLICY' })
    ])
  }))
  await page.route('**/api/rule-governance/proposals/8', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(compositeProposalDetail())
  }))
  await page.route('**/api/rule-governance/proposals/9', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(proposalDetail({
      id: 9,
      proposalNo: 'RCP-ADVICE-9',
      proposalType: 'OPTIMIZATION_ADVICE',
      rootCauseType: 'LLM_POLICY'
    }, {
      afterRule: {},
      finalRule: {},
      optimizationCategory: 'EXECUTOR_UPGRADE',
      optimizationAdvice: '建议升级为 LLM_POLICY 语义规则。'
    }))
  }))

  await page.goto('/#/rule-feedback')
  await page.getByRole('button', { name: '优化提案' }).click()
  await expect(page.getByText('COMPOSITE_RULE_CHANGE · 2 个动作')).toBeVisible()
  await page.getByRole('row', { name: /RCP-COMPOSITE-8/ }).getByRole('button', { name: '查看与审批' }).click()
  await expect(page.getByText('#1 · DISABLE_RULE')).toBeVisible()
  await expect(page.getByText('#2 · CREATE_RULE')).toBeVisible()
  await expect(page.locator('.governance-action-diff pre', { hasText: '"executorType": "LLM_POLICY"' })).toBeVisible()
  await page.getByRole('button', { name: '关闭' }).click()

  await page.getByRole('row', { name: /RCP-ADVICE-9/ }).getByRole('button', { name: '查看与审批' }).click()
  await expect(page.getByText('该提案不包含规则变更；请查看上方治理建议。')).toBeVisible()
  await expect(page.locator('.governance-diff pre', { hasText: '{}' })).toHaveCount(0)
})

async function mockProposalList(page) {
  await page.route('**/api/rule-governance/proposals?**', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify([proposalSummary()])
  }))
}

function proposalSummary(overrides = {}) {
  return {
    id: 7,
    proposalNo: 'RCP-20260729-0007',
    proposalType: 'UPDATE_RULE',
    proposalStatus: 'PENDING_REVIEW',
    ruleCode: 'TEST_RULE',
    ruleName: '测试规则',
    sourceRuleVersion: 'v1',
    rootCauseType: 'RULE_SCOPE',
    feedbackCount: 3,
    agentConfidence: 0.91,
    backtestRisk: 'LOW',
    documentCategory: 'PROTOCOL',
    declaredFileType: '投资协议书',
    agentModel: 'deepseek-v4-flash',
    reviewedBy: null,
    createdAt: '2026-07-29T01:00:00Z',
    reviewedAt: null,
    actionCount: 0,
    ...overrides
  }
}

function proposalDetail(summaryOverrides = {}, detailOverrides = {}) {
  const candidate = candidateRule()
  return {
    summary: proposalSummary(summaryOverrides),
    group: { id: 3, ruleCode: 'TEST_RULE', feedbackCount: 3, status: 'PROPOSAL_CREATED' },
    problemSummary: '同一类协议连续出现误报',
    rootCauseAnalysis: '规则范围过宽',
    changeReason: '收窄适用文件类型',
    expectedEffect: '降低误报',
    riskDescription: '可能遗漏旧模板',
    beforeRule: { ...candidate, scope: { documentCategories: ['PROTOCOL'] } },
    afterRule: candidate,
    finalRule: {},
    validationResult: { valid: true },
    backtestResult: {
      riskLevel: 'LOW', executionStatus: 'COMPLETED', executorType: 'LLM_POLICY',
      falsePositiveSampleCount: 3, confirmedPositiveSampleCount: 1, normalSampleCount: 1,
      determinateSampleCount: 5, resolvedFalsePositiveCount: 3, lostConfirmedPositiveCount: 0,
      newUnexpectedHitCount: 0, unresolvedSampleCount: 0, llmCallCount: 1,
      llmInputTokens: 12500, llmOutputTokens: 860, llmCacheHitTokens: 2000,
      coverageWarnings: [], details: [{ taskId: 1, sampleType: 'FALSE_POSITIVE', candidateRuleMatched: false, status: 'NOT_HIT', detail: '模型判定未命中', segmentCount: 1 }]
    },
    affectedScope: { historicalExecutionCount: 20 },
    agentProvider: 'deepseek',
    agentModel: 'deepseek-v4-flash',
    promptVersion: 'governance-v1',
    feedbacks: [{ feedbackId: 9, taskNo: 'REV-1', evidencePage: 2, evidenceText: '标题与正文矛盾', falsePositiveReason: '该处是合法模板文字' }],
    actions: [],
    memories: [],
    toolCalls: [{ id: 1, toolName: 'runRuleBacktest', status: 'SUCCESS' }],
    auditTrail: [],
    ...detailOverrides
  }
}

function compositeProposalDetail() {
  const disabled = { ...candidateRule(), enabled: false }
  const llm = {
    ruleCode: 'TEST_RULE_LLM', ruleName: '测试语义规则', executorType: 'LLM_POLICY', enabled: true, priority: 100,
    scope: { documentCategories: ['PROTOCOL'] },
    condition: { minConfidence: 0.8 },
    action: { issueType: 'CONTENT_LOGIC_CONFLICT', severity: 'HIGH', confidence: 0.8 },
    prompt: { reviewGoal: '识别正向保本承诺', criteria: '否定语境不违规' }
  }
  return proposalDetail({
    id: 8,
    proposalNo: 'RCP-COMPOSITE-8',
    proposalType: 'COMPOSITE_RULE_CHANGE',
    rootCauseType: 'RULE_EXECUTOR',
    actionCount: 2
  }, {
    afterRule: { actions: [] },
    finalRule: {},
    actions: [
      { id: 1, sequenceNo: 1, actionType: 'DISABLE_RULE', actionStatus: 'PENDING_REVIEW', ruleCode: 'TEST_RULE', beforeRule: candidateRule(), afterRule: disabled },
      { id: 2, sequenceNo: 2, actionType: 'CREATE_RULE', actionStatus: 'PENDING_REVIEW', ruleCode: 'TEST_RULE_LLM', beforeRule: {}, afterRule: llm }
    ]
  })
}

function candidateRule() {
  return {
    ruleCode: 'TEST_RULE', ruleName: '测试规则', executorType: 'REGEX', enabled: true, priority: 100,
    scope: { documentCategories: ['PROTOCOL'], documentTypes: ['投资协议书'] },
    condition: { patterns: ['产品代码[:：]\\s*[A-Z0-9]+'] }, action: {}, prompt: {}
  }
}

function runRow(overrides = {}) {
  return {
    id: 1, runNo: 'RGR-TEST-1', triggerType: 'MANUAL', status: 'SUCCESS',
    scannedFeedbackCount: 6, createdGroupCount: 2, createdProposalCount: 1, failedGroupCount: 0,
    skippedFeedbackCount: 3, skipReasonSummary: 'RULE_CODE_VERSION_MISMATCH=3',
    inputTokens: 1200, outputTokens: 300, cacheHitTokens: 100, durationMs: 800,
    startedAt: '2026-07-29T01:00:00Z', createdAt: '2026-07-29T01:00:00Z', ...overrides
  }
}

function groupRow(overrides = {}) {
  return {
    id: 5, groupKey: 'TEST_RULE|1|PROTOCOL', ruleCode: 'TEST_RULE', ruleName: '测试规则',
    ruleVersionId: 1, ruleVersion: 'v1', documentCategory: 'PROTOCOL', declaredFileType: '产品说明书',
    issueType: 'CONTENT_LOGIC_CONFLICT', feedbackCount: 5, status: 'DEFERRED', hasProposal: false,
    governanceRunId: 6, errorMessage: '未形成安全提案', latestFeedbackAt: '2026-07-29T01:00:00Z', ...overrides
  }
}

function traceResponse() {
  const base = { status: 'SUCCESS', executionMode: 'SERIAL', inputTokens: 0, outputTokens: 0, cacheHitTokens: 0, durationMs: 10, startedAt: '2026-07-29T01:00:00Z', finishedAt: '2026-07-29T01:00:01Z', attributes: {} }
  return {
    runId: 3, runNo: 'RGR-NOOP-3', traceId: 'governance-run-3', status: 'SUCCESS',
    currentStep: 'NO_ELIGIBLE_FEEDBACK',
    currentMessage: '没有 NEW/PENDING 且尚未归组的反馈；已有 FAILED/DEFERRED 分组不会被顶部聚合重复扫描，请到治理分组中重新分析。',
    instrumented: true,
    nodes: [
      { ...base, id: 'root', parentId: null, type: 'RUN', name: '反馈治理运行' },
      { ...base, id: 'mq-1', parentId: 'root', governanceGroupId: 1, type: 'MESSAGE_CONSUMER', name: '分组 1', executionMode: 'PARALLEL', parallelGroup: 'groups', sequence: 1 },
      { ...base, id: 'mq-2', parentId: 'root', governanceGroupId: 2, type: 'MESSAGE_CONSUMER', name: '分组 2', executionMode: 'PARALLEL', parallelGroup: 'groups', sequence: 2 },
      { ...base, id: 'agent', parentId: 'mq-1', governanceGroupId: 1, type: 'AGENT', name: '反馈治理 Agent', sequence: 1 },
      { ...base, id: 'llm', parentId: 'agent', governanceGroupId: 1, type: 'LLM_CALL', name: '第 1 轮模型决策', sequence: 1, attributes: { modelResponse: { message: '{"nextAction":"CALL_TOOLS"}', thoughtSummary: '准备并行校验', nextAction: 'CALL_TOOLS', finishReason: 'stop', toolCalls: [{ callId: 'call-validate', toolName: 'validateRuleConfig', arguments: { candidateRule: { ruleCode: 'TEST_RULE' } } }] } } },
      { ...base, id: 'attempt', parentId: 'llm', governanceGroupId: 1, type: 'LLM_ATTEMPT', name: 'deepseek-v4-flash', provider: 'deepseek', model: 'deepseek-v4-flash', inputTokens: 100, outputTokens: 20, sequence: 1 },
      { ...base, id: 'batch', parentId: 'agent', governanceGroupId: 1, type: 'TOOL_BATCH', name: '第 1 轮 Tool 批次', sequence: 2, attributes: { toolCount: 2, childExecutionMode: 'PARALLEL' } },
      { ...base, id: 'tool-1', parentId: 'batch', governanceGroupId: 1, type: 'TOOL_CALL', name: 'validateRuleConfig', executionMode: 'PARALLEL', parallelGroup: 'tool-batch-1', sequence: 101 },
      { ...base, id: 'tool-2', parentId: 'batch', governanceGroupId: 1, type: 'TOOL_CALL', name: 'runRuleBacktest', executionMode: 'PARALLEL', parallelGroup: 'tool-batch-1', sequence: 102 }
    ], edges: []
  }
}

function retryTraceResponse() {
  const base = { status: 'SUCCESS', executionMode: 'SERIAL', inputTokens: 0, outputTokens: 0, cacheHitTokens: 0, durationMs: 10, startedAt: '2026-07-30T01:00:00Z', finishedAt: '2026-07-30T01:00:01Z', attributes: {} }
  return {
    runId: 8, runNo: 'RGR-RETRY-8', traceId: 'governance-run-8', status: 'SUCCESS',
    currentStep: 'SUCCESS', currentMessage: '治理调用链已完成。', instrumented: true,
    nodes: [
      { ...base, id: 'root', parentId: null, type: 'RUN', name: '反馈治理运行' },
      { ...base, id: 'retry-group', parentId: 'root', governanceGroupId: 5, type: 'RETRY_GROUP', name: '治理分组 #5 · 共 2 次执行', attributes: { executionCount: 2 } },
      { ...base, id: 'current-message', parentId: 'retry-group', governanceGroupId: 5, type: 'MESSAGE_CONSUMER', name: '当前执行（第 2 次）', sequence: 1 },
      { ...base, id: 'current-agent', parentId: 'current-message', governanceGroupId: 5, type: 'AGENT', name: '当前执行 Agent', sequence: 1 },
      { ...base, id: 'history', parentId: 'retry-group', governanceGroupId: 5, type: 'RETRY_HISTORY', name: '历史执行（1 次）', status: 'HISTORICAL', sequence: 2, attributes: { collapsedByDefault: true } },
      { ...base, id: 'old-message', parentId: 'history', governanceGroupId: 5, type: 'MESSAGE_CONSUMER', name: '第 1 次执行', sequence: 1 },
      { ...base, id: 'old-agent', parentId: 'old-message', governanceGroupId: 5, type: 'AGENT', name: '历史执行 Agent', sequence: 1 }
    ], edges: []
  }
}
