import { request } from '@/utils/request'
import type { CanvasSave, CreateWorkflow, Dashboard, Page, VersionSnapshot, Workflow, WorkflowDetail, WorkflowQuery } from './types'
export const workflowApi = {
  dashboard: () => request<Dashboard>({ url: '/workflows/dashboard' }),
  list: (params: WorkflowQuery) => request<Page<Workflow>>({ url: '/workflows', params }),
  create: (data: CreateWorkflow) => request<WorkflowDetail>({ url: '/workflows', method: 'POST', data }),
  detail: (id: number) => request<WorkflowDetail>({ url: `/workflows/${id}` }),
  save: (id: number, data: CanvasSave) => request<WorkflowDetail>({ url: `/workflows/${id}/canvas`, method: 'PUT', data }),
  versions: (id: number) => request<VersionSnapshot[]>({ url: `/workflows/${id}/versions` }),
  rollback: (id: number, versionTag: string) => request<WorkflowDetail>({ url: `/workflows/${id}/versions/rollback`, method: 'POST', data: { versionTag } }),
  collaborators: (id: number, collaboratorIds: number[]) => request<boolean>({ url: `/workflows/${id}/collaborators`, method: 'PUT', data: { collaboratorIds } }),
  remove: (id: number) => request<boolean>({ url: `/workflows/${id}`, method: 'DELETE' }),
  clone: (id: number) => request<WorkflowDetail>({ url: `/workflows/${id}/clone`, method: 'POST' })
}
