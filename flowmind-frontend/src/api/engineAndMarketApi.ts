import { request } from '@/utils/request'
import { readSse } from '@/utils/sseClient'
import type { CanvasEdge, CanvasNode, Page, Workflow, WorkflowDetail } from './types'
export interface ExecRecord {
  id: number; execNo: string; workflowId: number; wfName: string; triggerUserId: number; triggerMode: string
  execStatus: string; totalTokens: number; durationMs: number; inputPayload: Record<string, unknown>
  finalOutput: Record<string, unknown>; errorSummary?: string; startedAt: string; finishedAt?: string
}
export interface NodeLog {
  id: number; stepOrder: number; nodeKey: string; nodeName: string; nodeType: string
  stepStatus: string; tokenCost: number; costMs: number; nodeInput?: string; nodeOutput?: string
}
export interface ExecDetail { record: ExecRecord; nodeLogs: NodeLog[] }
export interface ExecuteRequest { workflowId: number; triggerMode: string; inputPayload: Record<string, unknown>; canvasOverride?: { nodes: CanvasNode[]; edges: CanvasEdge[] } }
export interface RecordQuery {
  workflowId?: number; execStatus?: string; triggerMode?: string; minDuration?: number; maxDuration?: number
  startTime?: string; endTime?: string; pageNum: number; pageSize: number
}
export interface MarketQuery { keyword?: string; category?: string; tag?: string; minNodeCount?: number; maxNodeCount?: number; sortBy: string; pageNum: number; pageSize: number }
export interface EngineEvent { name: string; data: Partial<NodeLog> & { nodeKey?: string; chunk?: string; execRecordId?: number } & Partial<ExecDetail> }
export const engineApi = {
  generate: (prompt: string, category: string) => request<WorkflowDetail>({ url: '/engine/text-to-flow', method: 'POST', data: { prompt, category } }),
  sync: (data: ExecuteRequest) => request<ExecDetail>({ url: '/engine/execute/sync', method: 'POST', data, timeout: 300000 }),
  records: (params: RecordQuery) => request<Page<ExecRecord>>({ url: '/engine/records', params }),
  detail: (id: number) => request<ExecDetail>({ url: `/engine/records/${id}` }),
  async stream(data: ExecuteRequest, signal: AbortSignal, onEvent: (event: EngineEvent) => void) {
    const token = localStorage.getItem('flowmind.token')
    const response = await fetch('/api/v1/engine/execute/stream', { method: 'POST', signal,
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(data) })
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: '运行请求失败' }))
      if (response.status === 401) { localStorage.removeItem('flowmind.token'); localStorage.removeItem('flowmind.user'); location.assign(`/login?redirect=${encodeURIComponent(location.pathname)}`) }
      if (response.status === 403) location.assign('/403')
      throw new Error(error.message)
    }
    if (!response.headers.get('content-type')?.includes('text/event-stream')) throw new Error('服务器未返回 SSE 事件流')
    let completed = false
    await readSse(response, frame => {
      const envelope = JSON.parse(frame.data) as { code: number; message: string; data: EngineEvent['data'] }
      if (envelope.code !== 200 || frame.event === 'ERROR') throw new Error(envelope.message)
      if (frame.event === 'WORKFLOW_COMPLETE') completed = true
      onEvent({ name: frame.event, data: envelope.data })
    })
    if (!completed) throw new Error('连接已中断，请在运行记录中查看结果')
  }
}
export const marketApi = {
  list: (params: MarketQuery) => request<Page<Workflow>>({ url: '/market/templates', params }),
  detail: (id: number) => request<WorkflowDetail>({ url: `/market/templates/${id}` }),
  like: (id: number) => request<boolean>({ url: `/market/templates/${id}/like`, method: 'POST' }),
  fork: (id: number) => request<{ newWorkflowId: number }>({ url: `/market/templates/${id}/fork`, method: 'POST' })
}
