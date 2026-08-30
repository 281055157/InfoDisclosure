import { expect, test } from '@playwright/test'

test('task list shows token usage and opens model call detail', async ({ page }) => {
  await mockTaskList(page)
  await mockTaskDetail(page)

  await page.goto('/#/tasks')

  await expect(page.getByText('REV-20260728-675046')).toBeVisible()
  const tokenButton = page.getByRole('button', { name: '16000/2800/118' })
  await expect(tokenButton).toBeVisible()

  await tokenButton.click()
  await expect(page).toHaveURL(/#\/tasks\/1\?tab=llm-calls/)
  await expect(page.getByRole('button', { name: '模型调用' })).toHaveClass(/active/)
  await expect(page.getByText('deepseek-v4-flash')).toBeVisible()
  await expect(page.getByText('16000/2800/118')).toBeVisible()
  await expect(page.getByText('LLM_REVIEWING')).toBeVisible()
})

test('issue list shows false positive state and reuses previous comment', async ({ page }) => {
  await mockTaskList(page)
  await mockTaskDetail(page, {
    issues: [
      issueRow({
        issueId: 101,
        issueCode: 'CONTENT_LOGIC_CONFLICT',
        issueName: '正文逻辑冲突',
        falsePositiveStatus: 'MARKED',
        issueStatus: 'FALSE_POSITIVE',
        falsePositiveFeedback: {
          feedbackId: 501,
          feedbackType: 'FALSE_POSITIVE',
          processStatus: 'NEW',
          comment: '上次误报原因',
          reviewer: 'tester',
          createdAt: '2026-07-28T09:30:00Z',
          processedAt: null
        }
      }),
      issueRow({
        issueId: 102,
        issueCode: 'POSSIBLE_TEMPLATE_RESIDUE',
        issueName: '模板残留',
        falsePositiveStatus: 'PROCESSED',
        issueStatus: 'FALSE_POSITIVE',
        falsePositiveFeedback: {
          feedbackId: 502,
          feedbackType: 'FALSE_POSITIVE',
          processStatus: 'RESOLVED',
          comment: '已处理原因',
          reviewer: 'agent',
          createdAt: '2026-07-28T09:35:00Z',
          processedAt: '2026-07-28T10:00:00Z'
        }
      })
    ]
  })

  let updatePayload = null
  await page.route('**/api/reviews/1/issues/101', async route => {
    updatePayload = route.request().postDataJSON()
    await route.fulfill({ status: 200, body: '' })
  })

  await page.goto('/#/tasks/1')
  await page.getByRole('button', { name: '问题列表' }).click()

  await expect(page.getByText('正文逻辑冲突')).toBeVisible()
  await expect(page.getByRole('button', { name: '已标记' })).toBeVisible()
  await expect(page.getByRole('button', { name: '已处理' })).toBeDisabled()

  await page.getByRole('button', { name: '已标记' }).click()
  await expect(page.locator('textarea')).toHaveValue('上次误报原因')
  await page.locator('textarea').fill('更新后的误报原因')
  await page.getByRole('button', { name: '更新标记' }).click()

  await expect.poll(() => updatePayload?.comment).toBe('更新后的误报原因')
  expect(updatePayload.issueStatus).toBe('FALSE_POSITIVE')
})

test('rule feedback filters by keyword and process status', async ({ page }) => {
  await page.route('**/api/admin/rules/feedback?**', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify([
      feedbackRow({
        id: 1,
        taskId: 10,
        ruleCode: 'TEST_LLM_TEXT_LOGIC',
        declaredProductCode: 'SGN22555',
        processStatus: 'NEW',
        comment: '语义规则误报'
      }),
      feedbackRow({
        id: 2,
        taskId: 11,
        ruleCode: 'POSSIBLE_TEMPLATE_RESIDUE',
        declaredProductCode: 'ABC0001',
        processStatus: 'RESOLVED',
        comment: '模板残留确认'
      })
    ])
  }))

  await page.goto('/#/rule-feedback')
  await expect(page.getByText('TEST_LLM_TEXT_LOGIC', { exact: true })).toBeVisible()
  await expect(page.getByText('POSSIBLE_TEMPLATE_RESIDUE', { exact: true })).toBeVisible()

  await page.locator('input[placeholder="任务/规则/产品/说明"]').fill('SGN22555')
  await expect(page.getByText('TEST_LLM_TEXT_LOGIC', { exact: true })).toBeVisible()
  await expect(page.getByText('POSSIBLE_TEMPLATE_RESIDUE', { exact: true })).not.toBeVisible()

  await page.locator('input[placeholder="任务/规则/产品/说明"]').fill('')
  await page.locator('select').nth(1).selectOption('RESOLVED')
  await expect(page.getByText('POSSIBLE_TEMPLATE_RESIDUE', { exact: true })).toBeVisible()
  await expect(page.getByText('TEST_LLM_TEXT_LOGIC', { exact: true })).not.toBeVisible()
})

test('rule feedback renders empty and error states', async ({ page }) => {
  await page.route('**/api/admin/rules/feedback?**', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify([])
  }))

  await page.goto('/#/rule-feedback')
  await expect(page.getByText('暂无规则反馈')).toBeVisible()

  await page.unroute('**/api/admin/rules/feedback?**')
  await page.route('**/api/admin/rules/feedback?**', route => route.fulfill({
    status: 500,
    contentType: 'text/plain',
    body: 'feedback api down'
  }))

  await page.getByRole('button', { name: '刷新' }).click()
  await expect(page.getByText('feedback api down')).toBeVisible()
})

async function mockTaskList(page) {
  await page.route('**/api/reviews?**', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      content: [
        {
          taskId: 1,
          taskNo: 'REV-20260728-675046',
          originalFileName: 'SGN22555_投资协议书.pdf',
          documentCategory: 'PROTOCOL',
          declaredProductCode: 'SGN22555',
          declaredDocumentType: '投资协议书',
          status: 'WAITING_MANUAL_REVIEW',
          technicalStatus: 'SUCCESS',
          businessRisk: 'LOW',
          productIdentityDecision: 'MATCHED',
          businessAcceptanceDecision: 'ACCEPTED',
          currentStage: 'WAITING_MANUAL_REVIEW',
          retryCount: 0,
          llmInputTokens: 16000,
          llmOutputTokens: 2800,
          llmCacheHitTokens: 118,
          createdAt: '2026-07-28T09:00:00Z',
          completedAt: '2026-07-28T09:02:34Z',
          manualReviewedAt: null
        }
      ],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
      empty: false
    })
  }))
}

async function mockTaskDetail(page, options = {}) {
  await page.route('**/api/reviews/1', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      taskId: 1,
      taskNo: 'REV-20260728-675046',
      originalFileName: 'SGN22555_投资协议书.pdf',
      documentCategory: 'PROTOCOL',
      declaredProductCode: 'SGN22555',
      declaredDocumentType: '投资协议书',
      b9Value: null,
      status: 'WAITING_MANUAL_REVIEW',
      technicalStatus: 'SUCCESS',
      businessRisk: 'LOW',
      productIdentityDecision: 'MATCHED',
      businessAcceptanceDecision: 'ACCEPTED',
      currentStage: 'WAITING_MANUAL_REVIEW',
      statusDetail: '',
      reviewVersion: 'v1',
      retryCount: 0,
      createdAt: '2026-07-28T09:00:00Z',
      startedAt: '2026-07-28T09:00:01Z',
      completedAt: '2026-07-28T09:02:34Z',
      manualReviewedAt: null,
      pageCount: 2,
      openIssueCount: 0,
      reviewResult: {
        productMaster: { matched: true, productCode: 'SGN22555', productName: '测试产品' },
        llmResult: { summary: '正文与声明一致', issues: [] },
        mergedIssues: []
      }
    })
  }))
  await page.route('**/api/reviews/1/report', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      taskId: 1,
      taskNo: 'REV-20260728-675046',
      reviewResult: null,
      issues: options.issues || [],
      evidenceChain: [],
      manualReviews: [],
      summary: '正文与声明一致',
      manualSuggestion: '无需人工介入'
    })
  }))
  await page.route('**/api/reviews/1/llm-usage', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      taskId: 1,
      inputTokens: 16000,
      outputTokens: 2800,
      cacheHitTokens: 118,
      callCount: 1
    })
  }))
  await page.route('**/api/reviews/1/llm-calls', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify([
      {
        id: 99,
        stage: 'LLM_REVIEWING',
        operationType: 'COMBINED_REVIEW',
        provider: 'deepseek-default',
        modelName: 'deepseek-v4-flash',
        ruleCode: null,
        ruleVersionId: null,
        chunkIndex: 1,
        pageFrom: 1,
        pageTo: 2,
        inputTokens: 16000,
        outputTokens: 2800,
        cacheHitTokens: 118,
        durationMs: 24000,
        callStatus: 'SUCCESS',
        errorMessage: null,
        createdAt: '2026-07-28T09:02:34Z'
      }
    ])
  }))
  await page.route('**/api/reviews/1/file', route => route.fulfill({
    status: 200,
    contentType: 'application/pdf',
    body: ''
  }))
}

function issueRow(overrides) {
  return {
    issueId: 100,
    issueCode: 'CONTENT_LOGIC_CONFLICT',
    issueName: '正文逻辑冲突',
    severity: 'HIGH',
    confidence: 0.9,
    pageNumber: 1,
    evidenceText: '标题与正文矛盾',
    evidenceVerified: true,
    explanation: '文件标题与正文内容不一致',
    suggestion: '建议人工确认标题与正文',
    sourceType: 'LLM',
    ruleCode: null,
    ruleVersionId: null,
    ruleExecutionId: null,
    issueStatus: 'OPEN',
    falsePositiveStatus: 'UNMARKED',
    falsePositiveFeedback: null,
    createdAt: '2026-07-28T09:20:00Z',
    updatedAt: '2026-07-28T09:20:00Z',
    ...overrides
  }
}

function feedbackRow(overrides) {
  return {
    feedbackType: 'FALSE_POSITIVE',
    reviewer: 'tester',
    createdAt: '2026-07-28T09:10:00Z',
    documentCategory: 'PROTOCOL',
    declaredDocumentType: '投资协议书',
    aggregationKey: `${overrides.ruleCode || 'RULE'}|${overrides.declaredProductCode || ''}`,
    issueSnapshotJson: JSON.stringify({ issueCode: overrides.ruleCode || 'RULE' }),
    ...overrides
  }
}
