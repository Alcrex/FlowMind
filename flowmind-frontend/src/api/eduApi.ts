import { request } from '@/utils/request'
import type { Page, WorkflowDetail } from './types'

export interface NodeRule { minNodes: number; mustInclude: string[] }
export interface RubricItem { item: string; maxScore: number }
export interface TaskSave {
  taskTitle: string; courseName: string; targetClass: string; taskRequirement: string
  requiredNodesJson: NodeRule; rubricJson: RubricItem[] | null; deadline: string; taskStatus: number
}
export interface EduTask extends TaskSave {
  id: number; taskCode: string; teacherId: number; teacherName: string; createdAt: string
  mySubmissionId?: number; myReviewStatus?: number; myScore?: number
}
export interface TaskQuery { keyword?: string; courseName?: string; targetClass?: string; taskStatus?: number; pageNum: number; pageSize: number }
export interface Submission {
  id: number; taskId: number; studentId: number; workflowId: number; versionTag: string
  canvasSnapshotJson: WorkflowDetail; studentReport: string; isLate: number; autoCheckPassed: number
  rubricDetailJson?: Record<string, number>; score?: number; teacherComment?: string; reviewStatus: number
  submittedAt: string; reviewedAt?: string; taskTitle: string; targetClass: string; studentName: string
  studentNumber: string; rubricJson: RubricItem[] | null
}
export interface SubmissionQuery {
  taskId?: number; targetClass?: string; reviewStatus?: number; isLate?: number; autoCheckPassed?: number
  minScore?: number; maxScore?: number; studentKeyword?: string; pageNum: number; pageSize: number
}
export interface Grade { rubricDetailJson: Record<string, number> | null; score: number | null; teacherComment: string; reviewStatus: number }
export interface EduAnalytics {
  summary: { totalSubmissions: number; gradedCount: number; passRate: number; avgScore: number }
  scoreBuckets: { bucket: string; count: number }[]
  radarDimensions: { dimension: string; avgRate: number }[]
  dailySubmissionTrend: { date: string; submitCount: number; debugCount: number }[]
  errorNodeDistribution: { nodeType: string; errorCount: number }[]
}
export const eduApi = {
  tasks: (params: TaskQuery) => request<Page<EduTask>>({ url: '/edu/tasks', params }),
  task: (id: number) => request<EduTask>({ url: `/edu/tasks/${id}` }),
  saveTask: (id: number | undefined, data: TaskSave) => request<EduTask>({ url: id ? `/edu/tasks/${id}` : '/edu/tasks', method: id ? 'PUT' : 'POST', data }),
  deleteTask: (id: number) => request<boolean>({ url: `/edu/tasks/${id}`, method: 'DELETE' }),
  submit: (data: { taskId: number; workflowId: number; studentReport: string }) => request<Submission>({ url: '/edu/submissions', method: 'POST', data }),
  submissions: (params: SubmissionQuery) => request<Page<Submission>>({ url: '/edu/submissions', params }),
  submission: (id: number) => request<Submission>({ url: `/edu/submissions/${id}` }),
  grade: (id: number, data: Grade) => request<Submission>({ url: `/edu/submissions/${id}/grade`, method: 'PUT', data }),
  analytics: (params: { taskId?: number; targetClass?: string }) => request<EduAnalytics>({ url: '/edu/analytics', params })
}
export const taskStatusLabel = (status: number) => ['已归档', '进行中', '已截止'][status] ?? '未知'
export const reviewStatusLabel = (status?: number) => status === undefined ? '未提交' : ['待批改', '已批改', '打回重做'][status] ?? '未知'
