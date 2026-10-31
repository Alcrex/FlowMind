import { request } from '@/utils/request'
import type { Page, Role, UserInfo } from './types'

export interface SystemUser extends UserInfo { email?: string; avatarUrl?: string; status: number; lastLoginAt?: string; createdAt: string; updatedAt: string }
export interface Registration { username: string; password: string; realName: string; roleType: 'STUDENT' | 'TEACHER'; deptClass: string; email?: string }
export interface UserQuery { keyword?: string; roleType?: Role; deptClass?: string; status?: number; minQuota?: number; maxQuota?: number; pageNum: number; pageSize: number }
export interface UserUpdate { roleType?: Role; deptClass?: string; status?: number; tokenQuota?: number; resetPassword?: boolean }
export interface AuditQuery { username?: string; moduleName?: string; actionType?: string; modelProvider?: string; httpStatus?: number; startTime?: string; endTime?: string; pageNum: number; pageSize: number }
export interface AuditLog { id: number; userId?: number; username?: string; moduleName: string; actionType: string; modelProvider?: string; requestUri?: string; requestMethod?: string; requestIp?: string; httpStatus: number; costTokens: number; durationMs: number; detailJson?: unknown; errorMsg?: string; createdAt: string }
export interface ModelConfig { activeProvider: 'DEEPSEEK' | 'QWEN' | 'MOCK_LOCAL'; baseUrl: string; apiKeyMasked: string; apiKeyPlain: string; modelName: string; enableMockFallback: boolean; sensitiveWords: string[] }
export interface BigScreenStats {
  kpi: { totalUsers: number; totalWorkflows: number; totalExecutions: number; totalTokensUsed: number }
  sankeyData: { nodes: { name: string }[]; links: { source: string; target: string; value: number }[] }
  deptRanking: { deptClass: string; workflowCount: number; execCount: number }[]
  hourlyTrend: { hour: string; callCount: number; avgLatencyMs: number }[]
}
const params = (value: object) => Object.fromEntries(Object.entries(value).filter(([, v]) => v !== '' && v !== undefined && v !== null))
export const authAndAdminApi = {
  login: (username: string, password: string) => request<{ token: string; tokenId: string; userInfo: SystemUser }>({ url: '/auth/login', method: 'POST', data: { username, password } }),
  register: (data: Registration) => request<{ userId: number; username: string }>({ url: '/auth/register', method: 'POST', data }),
  me: () => request<SystemUser>({ url: '/auth/me' }),
  logout: () => request<boolean>({ url: '/auth/logout', method: 'POST' }),
  users: (query: UserQuery) => request<Page<SystemUser>>({ url: '/admin/users', params: params(query) }),
  updateUser: (id: number, data: UserUpdate) => request<SystemUser>({ url: `/admin/users/${id}`, method: 'PUT', data }),
  auditLogs: (query: AuditQuery) => request<Page<AuditLog>>({ url: '/admin/audit-logs', params: params(query) }),
  modelConfig: () => request<ModelConfig>({ url: '/admin/models/config' }),
  updateModelConfig: (data: ModelConfig) => request<ModelConfig>({ url: '/admin/models/config', method: 'PUT', data }),
  bigScreen: () => request<BigScreenStats>({ url: '/admin/dashboard/big-screen' })
}
export const roleOptions: { value: Role; label: string }[] = [{ value: 'STUDENT', label: '学生' }, { value: 'TEACHER', label: '教师' }, { value: 'ADMIN', label: '管理员' }]
