import { test, expect, type APIRequestContext } from '@playwright/test'
import { writeFile } from 'node:fs/promises'
import type { WorkflowDetail } from '../src/api/types'

const api = 'http://127.0.0.1:8080/api/v1'
async function login(request: APIRequestContext, username: string) {
  const response = await request.post(`${api}/auth/login`, { data: { username, password: '123456' } })
  expect(response.ok()).toBeTruthy()
  return (await response.json()).data.token as string
}

test('defense security: student-only registration, sensitive audit and revoked token', async ({ page }, info) => {
  const username = `defense_${Date.now()}`
  const registration = { username, password: '123456', realName: '演示学生', deptClass: '计科1班' }
  const rejected = await page.request.post(`${api}/auth/register`, { data: { ...registration, roleType: 'TEACHER' } })
  expect(rejected.status()).toBe(400)
  expect((await rejected.json()).code).toBe(40000)
  expect((await page.request.post(`${api}/auth/register`, { data: registration })).ok()).toBeTruthy()
  const token = await login(page.request, username)
  const headers = { Authorization: `Bearer ${token}` }
  const me = await page.request.get(`${api}/auth/me`, { headers })
  expect((await me.json()).data.roleType).toBe('STUDENT')
  await page.addInitScript(token => { if (!localStorage.getItem('flowmind.token')) localStorage.setItem('flowmind.token', token) }, token)
  await page.goto('/admin/users')
  await expect(page.getByRole('heading', { name: '这里需要更多权限' })).toBeVisible()
  await page.screenshot({ path: info.outputPath('step-1-student-403.png'), fullPage: true })
  const blocked = await page.request.post(`${api}/engine/text-to-flow`, { headers, data: { prompt: '违法违禁', category: 'OTHER' } })
  expect(blocked.status()).toBe(400)
  expect((await blocked.json()).code).toBe(40009)
  const admin = await login(page.request, 'admin')
  const audit = await page.request.get(`${api}/admin/audit-logs`, {
    headers: { Authorization: `Bearer ${admin}` }, params: { username, actionType: 'SENSITIVE_BLOCK' }
  })
  const auditData = (await audit.json()).data
  expect(auditData.total).toBe(1)
  await info.attach('step-2-sensitive-audit', { body: JSON.stringify(auditData, null, 2), contentType: 'application/json' })
  await page.evaluate(admin => localStorage.setItem('flowmind.token', admin), admin)
  await page.goto('/admin/audit')
  await page.getByPlaceholder('操作账号').fill(username)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('.el-table__row').filter({ hasText: 'SENSITIVE_BLOCK' })).toHaveCount(1)
  await page.locator('.audit-panel').scrollIntoViewIfNeeded()
  await page.screenshot({ path: info.outputPath('step-2-sensitive-audit.png'), fullPage: true })
  expect((await page.request.post(`${api}/auth/logout`, { headers })).ok()).toBeTruthy()
  const revoked = await page.request.get(`${api}/auth/me`, { headers })
  expect(revoked.status()).toBe(401)
  expect((await revoked.json()).code).toBe(40102)
  await writeFile(info.outputPath('step-2-security.json'), JSON.stringify({ registrationRole: 'STUDENT', extraRoleStatus: 400,
    blockedStatus: 400, blockedCode: 40009, audit: auditData, logoutStatus: revoked.status(), logoutCode: 40102 }, null, 2))
})

test('defense engine: three rule graphs, skipped branch, cycle refusal and fresh heatmap', async ({ page }, info) => {
  const token = await login(page.request, 'gufengming'), admin = await login(page.request, 'admin')
  const headers = { Authorization: `Bearer ${token}` }, adminHeaders = { Authorization: `Bearer ${admin}` }
  await page.addInitScript(token => { if (!localStorage.getItem('flowmind.token')) localStorage.setItem('flowmind.token', token) }, token)
  const prompts = [
    '输入 Java 代码，检索开发规约并生成安全审查报告',
    '清洗输入文本，如果包含 Java 则检索知识库并回答，否则给出通用建议',
    '根据本周工作事项生成结构化周报'
  ]
  const expected = [['INPUT', 'KNOWLEDGE', 'LLM', 'OUTPUT'], ['CODE', 'CONDITION', 'INPUT', 'KNOWLEDGE', 'LLM', 'OUTPUT'], ['INPUT', 'LLM', 'OUTPUT']]
  const flows: WorkflowDetail[] = []
  try {
    for (let index = 0; index < prompts.length; index++) {
      await page.goto('/engine/text-to-flow')
      await page.getByLabel('工作流需求').fill(prompts[index])
      const generated = page.waitForResponse(r => r.url().endsWith('/engine/text-to-flow') && r.request().method() === 'POST')
      await page.getByRole('button', { name: '生成并打开画布' }).click()
      const response = await generated
      expect(response.ok()).toBeTruthy()
      const detail: WorkflowDetail = (await response.json()).data
      flows.push(detail)
      expect([...new Set(detail.nodes.map(n => n.nodeType))].sort()).toEqual(expected[index])
      await expect(page.getByText('规则生成', { exact: true })).toBeVisible()
      await expect(page.locator('.vue-flow__node')).toHaveCount(detail.nodes.length)
      await page.screenshot({ path: info.outputPath(`step-4-rule-${index + 1}.png`), fullPage: true })
    }
    const branch = flows[1], id = branch.workflow.id
    const before = (await (await page.request.get(`${api}/admin/dashboard/big-screen`, { headers: adminHeaders })).json()).data
    await page.goto(`/engine/sandbox/${id}`)
    await expect(page.getByRole('textbox', { name: '运行输入 JSON' })).toHaveValue(/text/)
    await page.getByRole('textbox', { name: '运行输入 JSON' }).fill(JSON.stringify({ text: 'Java SQL 注入审查' }))
    await page.getByRole('button', { name: '运行工作流', exact: true }).click()
    await expect(page.locator('.engine-summary')).toContainText('SUCCESS')
    await expect(page.locator('.el-table__row').filter({ hasText: 'SKIPPED' })).toHaveCount(1)
    await expect(page.locator('[aria-live="polite"]')).toContainText('本地沙箱')
    await page.screenshot({ path: info.outputPath('step-4-branch-skipped.png'), fullPage: true })
    const after = (await (await page.request.get(`${api}/admin/dashboard/big-screen`, { headers: adminHeaders })).json()).data
    expect(after.kpi.totalExecutions).toBe(before.kpi.totalExecutions + 1)
    // Compare only hours present in both rolling windows, including a possible hour boundary.
    const oldCalls = new Map<string, number>(before.hourlyTrend.map((h: { hour: string; callCount: number }) => [h.hour, h.callCount]))
    const changes = after.hourlyTrend.filter((h: { hour: string }) => oldCalls.has(h.hour))
      .map((h: { hour: string; callCount: number }) => h.callCount - oldCalls.get(h.hour)!)
    expect(changes.reduce((sum: number, n: number) => sum + n, 0)).toBeGreaterThan(0)

    const cycle = flows[2], cycleId = cycle.workflow.id
    const saved = await page.request.put(`${api}/workflows/${cycleId}/canvas`, { headers, data: {
      wfName: cycle.workflow.wfName, isPublic: 0, createSnapshot: false, nodes: cycle.nodes,
      edges: [...cycle.edges, { elementKey: 'edge_cycle', sourceKey: 'node_output_1', targetKey: 'node_llm_1', sourceHandle: 'default' }]
    } })
    expect(saved.ok()).toBeTruthy()
    await page.goto(`/engine/sandbox/${cycleId}`)
    await expect(page.getByRole('textbox', { name: '运行输入 JSON' })).toHaveValue(/text/)
    const refused = page.waitForResponse(r => r.url().endsWith('/engine/execute/stream'))
    await page.getByRole('button', { name: '运行工作流', exact: true }).click()
    const refusal = await refused
    expect(refusal.status()).toBe(400)
    expect((await refusal.json()).code).toBe(40005)
    await expect(page.locator('.engine-error')).toContainText('循环')
    const records = await page.request.get(`${api}/engine/records`, { headers, params: { workflowId: cycleId, execStatus: 'SUCCESS' } })
    expect((await records.json()).data.total).toBe(0)
    await page.screenshot({ path: info.outputPath('step-5-cycle-refused.png'), fullPage: true })
    await info.attach('step-7-monitor-delta', { body: JSON.stringify({ before, after }, null, 2), contentType: 'application/json' })
    await writeFile(info.outputPath('step-7-monitor-delta.json'), JSON.stringify({ before, after }, null, 2))
    // A full navigation reloads Pinia with the administrator token.
    await page.evaluate(admin => localStorage.setItem('flowmind.token', admin), admin)
    await page.goto('/admin/monitor')
    await expect(page.locator('[_echarts_instance_]')).toHaveCount(3)
    await expect(page.locator('[aria-label="最近24小时模型调用热力图"]')).toHaveAttribute('data-series', 'heatmap')
    await page.screenshot({ path: info.outputPath('step-7-monitor.png'), fullPage: true })
  } finally {
    for (const flow of flows) await page.request.delete(`${api}/workflows/${flow.workflow.id}`, { headers })
  }
})
