const jsonHeaders = { 'Content-Type': 'application/json' }

async function request(url, options = {}) {
  const response = await fetch(url, options)
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `HTTP ${response.status}`)
  }
  if (response.status === 204) return null
  const type = response.headers.get('content-type') || ''
  return type.includes('application/json') ? response.json() : response.text()
}

function operatorHeaders(operator = 'demo-user') {
  return { ...jsonHeaders, 'X-Operator': operator || 'demo-user' }
}

export const api = {
  statistics: () => request('/api/reviews/statistics/summary'),
  tasks: (params = {}) => request(`/api/reviews?${new URLSearchParams(clean(params))}`),
  task: id => request(`/api/reviews/${id}`),
  report: id => request(`/api/reviews/${id}/report`),
  pages: id => request(`/api/reviews/${id}/pages`),
  timeline: id => request(`/api/reviews/${id}/timeline`),
  llmUsage: id => request(`/api/reviews/${id}/llm-usage`),
  llmCalls: id => request(`/api/reviews/${id}/llm-calls`),
  retry: (id, stage) => request(`/api/reviews/${id}/retry`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ stage })
  }),
  createTask: formData => request('/api/reviews', { method: 'POST', body: formData }),
  manualReview: (id, payload) => request(`/api/reviews/${id}/manual-review`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(payload)
  }),
  updateIssue: (taskId, issueId, payload) => request(`/api/reviews/${taskId}/issues/${issueId}`, {
    method: 'PATCH',
    headers: jsonHeaders,
    body: JSON.stringify(payload)
  }),
  addManualIssue: (taskId, payload) => request(`/api/reviews/${taskId}/issues/manual`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(payload)
  }),
  rules: () => request('/api/admin/rules'),
  rule: id => request(`/api/admin/rules/${id}`),
  executorSchemas: () => request('/api/admin/rules/executor-schemas'),
  saveRule: rule => {
    const method = rule.id ? 'PUT' : 'POST'
    const url = rule.id ? `/api/admin/rules/${rule.id}` : '/api/admin/rules'
    return request(url, { method, headers: jsonHeaders, body: JSON.stringify(rule) })
  },
  createRuleVersion: (id, payload) => request(`/api/admin/rules/${id}/versions`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(payload)
  }),
  updateRuleVersion: (id, versionId, payload) => request(`/api/admin/rules/${id}/versions/${versionId}`, {
    method: 'PUT',
    headers: jsonHeaders,
    body: JSON.stringify(payload)
  }),
  validateRuleVersion: (id, versionId) => request(`/api/admin/rules/${id}/versions/${versionId}/validate`, { method: 'POST' }),
  testRuleVersion: (id, versionId, payload) => request(`/api/admin/rules/${id}/versions/${versionId}/test`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(payload)
  }),
  publishRuleVersion: (id, versionId) => request(`/api/admin/rules/${id}/versions/${versionId}/publish`, { method: 'POST' }),
  ruleExecutions: id => request(`/api/admin/rules/${id}/executions`),
  setRuleEnabled: (id, enabled) => request(`/api/admin/rules/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' }),
  ruleFeedback: (params = {}) => request(`/api/admin/rules/feedback?${new URLSearchParams(clean(params))}`),
  governanceRuns: () => request('/api/rule-governance/runs'),
  governanceRun: id => request(`/api/rule-governance/runs/${id}`),
  governanceRunTrace: id => request(`/api/rule-governance/runs/${id}/trace`),
  deleteGovernanceRun: (id, operator = 'demo-user') => request(`/api/rule-governance/runs/${id}`, {
    method: 'DELETE', headers: operatorHeaders(operator)
  }),
  startGovernanceRun: (operator = 'demo-user') => request('/api/rule-governance/runs', {
    method: 'POST', headers: operatorHeaders(operator)
  }),
  governanceGroups: (params = {}) => request(`/api/rule-governance/groups?${new URLSearchParams(clean(params))}`),
  governanceGroupFeedbacks: id => request(`/api/rule-governance/groups/${id}/feedbacks`),
  analyzeGovernanceGroup: (id, operator = 'demo-user') => request(`/api/rule-governance/groups/${id}/analyze`, {
    method: 'POST', headers: operatorHeaders(operator)
  }),
  deleteGovernanceGroup: (id, operator = 'demo-user') => request(`/api/rule-governance/groups/${id}`, {
    method: 'DELETE', headers: operatorHeaders(operator)
  }),
  governanceProposals: (params = {}) => request(`/api/rule-governance/proposals?${new URLSearchParams(clean(params))}`),
  governanceProposal: id => request(`/api/rule-governance/proposals/${id}`),
  governanceProposalDiff: id => request(`/api/rule-governance/proposals/${id}/diff`),
  governanceProposalBacktest: id => request(`/api/rule-governance/proposals/${id}/backtest`),
  approveGovernanceProposal: (id, payload = {}, operator = 'demo-user') => request(`/api/rule-governance/proposals/${id}/approve`, {
    method: 'POST', headers: operatorHeaders(operator), body: JSON.stringify(payload)
  }),
  approveModifiedGovernanceProposal: (id, payload, operator = 'demo-user') => request(`/api/rule-governance/proposals/${id}/approve-with-modification`, {
    method: 'POST', headers: operatorHeaders(operator), body: JSON.stringify(payload)
  }),
  rejectGovernanceProposal: (id, payload, operator = 'demo-user') => request(`/api/rule-governance/proposals/${id}/reject`, {
    method: 'POST', headers: operatorHeaders(operator), body: JSON.stringify(payload)
  }),
  deferGovernanceProposal: (id, payload, operator = 'demo-user') => request(`/api/rule-governance/proposals/${id}/defer`, {
    method: 'POST', headers: operatorHeaders(operator), body: JSON.stringify(payload)
  }),
  applyGovernanceProposal: (id, payload = {}, operator = 'demo-user') => request(`/api/rule-governance/proposals/${id}/apply`, {
    method: 'POST', headers: operatorHeaders(operator), body: JSON.stringify(payload)
  }),
  evaluateGovernanceEffect: (id, operator = 'demo-user') => request(`/api/rule-governance/proposals/${id}/evaluate-effect`, {
    method: 'POST', headers: operatorHeaders(operator)
  }),
  models: () => request('/api/admin/models'),
  saveModel: model => {
    const method = model.id ? 'PUT' : 'POST'
    const url = model.id ? `/api/admin/models/${model.id}` : '/api/admin/models'
    return request(url, { method, headers: jsonHeaders, body: JSON.stringify(model) })
  },
  testModel: id => request(`/api/admin/models/${id}/test`, { method: 'POST' }),
  providers: () => request('/api/admin/providers'),
  saveProvider: provider => {
    const method = provider.id ? 'PUT' : 'POST'
    const url = provider.id ? `/api/admin/providers/${provider.id}` : '/api/admin/providers'
    return request(url, { method, headers: jsonHeaders, body: JSON.stringify(provider) })
  },
  setProviderEnabled: (id, enabled) => request(`/api/admin/providers/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' }),
  deleteTask: (id, deleteFiles = true) => request(`/api/admin/tasks/${id}?deleteFiles=${deleteFiles}`, { method: 'DELETE' })
}

function clean(params) {
  return Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''))
}
