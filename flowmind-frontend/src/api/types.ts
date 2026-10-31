export type Role = 'ADMIN' | 'TEACHER' | 'STUDENT'
export interface UserInfo { id: number; username: string; realName: string; roleType: Role; deptClass: string; tokenQuota: number; tokenUsed: number }
export type NodeType = 'INPUT' | 'KNOWLEDGE' | 'LLM' | 'CODE' | 'CONDITION' | 'OUTPUT'
export interface CanvasNode { id?: number; elementKey: string; nodeType: NodeType; labelName: string; positionJson: { x: number; y: number }; configJson: Record<string, unknown> }
export interface CanvasEdge { id?: number; elementKey: string; labelName?: string; sourceKey: string; targetKey: string; sourceHandle: string }
export interface Workflow { id: number; wfCode: string; wfName: string; description?: string; category: string; tags?: string; creatorId: number; creatorName: string; collaboratorIds: number[]; isPublic: number; nodeCount: number; edgeCount: number; forkCount: number; likeCount: number; versionTag: string; createdAt: string; updatedAt: string }
export interface WorkflowDetail { workflow: Workflow; nodes: CanvasNode[]; edges: CanvasEdge[] }
export interface Page<T> { records: T[]; total: number; pageNum: number; pageSize: number }
export interface WorkflowQuery { keyword?: string; category?: string; isPublic?: number; minNodeCount?: number; maxNodeCount?: number; scope: string; startTime?: string; endTime?: string; pageNum: number; pageSize: number }
export interface CreateWorkflow { wfName: string; description: string; category: string; tags: string; isPublic: number }
export interface CanvasSave { wfName: string; description?: string; isPublic: number; createSnapshot: boolean; newVersionTag?: string; nodes: CanvasNode[]; edges: CanvasEdge[] }
export interface VersionSnapshot { versionTag: string; savedAt: string; savedBy: number; wfName?: string; description?: string; nodes: CanvasNode[]; edges: CanvasEdge[] }
export interface Dashboard { myWorkflowCount: number; collabWorkflowCount: number; pendingTaskCount: number; remainingTokenQuota: number; recentWorkflows: Workflow[] }
export interface Presence { userId: number; realName: string; color: string; x?: number; y?: number }
export const categories = [{ value: 'CODE_ASSIST', label: '代码辅助' }, { value: 'DATA_ANALYSIS', label: '数据分析' }, { value: 'TEXT_GEN', label: '文本生成' }, { value: 'KNOWLEDGE_QA', label: '知识问答' }, { value: 'OTHER', label: '其他' }]
export const nodeTypes: { type: NodeType; label: string; color: string; hint: string }[] = [
  { type: 'INPUT', label: '输入', color: '#368c73', hint: '定义工作流输入变量' },
  { type: 'KNOWLEDGE', label: '知识检索', color: '#b08642', hint: '检索专业知识与规范' },
  { type: 'LLM', label: '大语言模型', color: '#6c63ae', hint: '配置模型与提示词' },
  { type: 'CODE', label: '代码处理', color: '#4282aa', hint: '清洗、提取和转换内容' },
  { type: 'CONDITION', label: '条件判断', color: '#c27b48', hint: '根据条件路由到不同分支' },
  { type: 'OUTPUT', label: '输出', color: '#667683', hint: '汇总并格式化最终结果' }
]
export const categoryLabel = (value: string) => categories.find(c => c.value === value)?.label ?? value
export const formatTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN') : '—'
