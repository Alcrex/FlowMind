import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import type { Role } from '@/api/types'
import { useAuthStore } from '@/stores/authStore'
import MainLayout from '@/layouts/MainLayout.vue'
import ModulePending from '@/components/ModulePending.vue'
const modules = import.meta.glob('../views/**/*.vue')
const view = (path: string) => modules[`../views/${path}.vue`] ?? ModulePending
const routes: RouteRecordRaw[] = [
  { path: '/login', component: view('auth/LoginView'), meta: { public: true, title: '登录' } },
  { path: '/register', component: view('auth/RegisterView'), meta: { public: true, title: '注册' } },
  { path: '/403', component: view('error/Error403View'), meta: { public: true, title: '权限不足' } },
  { path: '/', component: MainLayout, redirect: '/workspace', children: [
    { path: 'workspace', component: view('workspace/DashboardView'), meta: { title: '工作台' } },
    { path: 'workflows', component: view('workflow/WorkflowListView'), meta: { title: '我的工作流' } },
    { path: 'workflow/editor/:id', component: view('workflow/CanvasEditorView'), meta: { title: '画布编辑器' } },
    { path: 'workflow/:id/node/:nodeKey', component: view('workflow/NodeConfigDrawer'), meta: { title: '节点配置' } },
    { path: 'workflow/:id/versions', component: view('workflow/VersionHistoryView'), meta: { title: '版本历史' } },
    { path: 'workflow/:id/collaborators', component: view('workflow/CollabManageModal'), meta: { title: '协作管理' } },
    { path: 'engine/sandbox/:id', component: view('engine/SandboxRunnerView'), meta: { title: '调试沙箱' } },
    { path: 'engine/text-to-flow', component: view('engine/TextToFlowView'), meta: { title: '一句话生成' } },
    { path: 'engine/records', component: view('engine/ExecRecordListView'), meta: { title: '运行记录' } },
    { path: 'engine/records/:id', component: view('engine/ExecNodeDetailView'), meta: { title: '运行详情' } },
    { path: 'market', component: view('market/TemplateMarketView'), meta: { title: '模板广场', public: true } },
    { path: 'market/:id', component: view('market/TemplateDetailView'), meta: { title: '模板详情', public: true } },
    { path: 'edu/tasks', component: view('edu/StudentTaskListView'), meta: { title: '我的实训', roles: ['STUDENT'] } },
    { path: 'edu/submit/:id', component: view('edu/SubmissionEditView'), meta: { title: '提交作业', roles: ['STUDENT'] } },
    { path: 'edu/manage', component: view('edu/TeacherTaskManageView'), meta: { title: '课题管理', roles: ['TEACHER', 'ADMIN'] } },
    { path: 'edu/grades', component: view('edu/TeacherGradeListView'), meta: { title: '作业批改', roles: ['TEACHER', 'ADMIN'] } },
    { path: 'edu/review/:id', component: view('edu/TeacherCanvasReviewView'), meta: { title: '画布评测', roles: ['TEACHER', 'ADMIN'] } },
    { path: 'edu/analytics', component: view('edu/EduAnalyticsView'), meta: { title: '学情分析', roles: ['TEACHER', 'ADMIN'] } },
    { path: 'admin/monitor', component: view('admin/BigScreenMonitorView'), meta: { title: '全局监控', roles: ['TEACHER', 'ADMIN'] } },
    { path: 'admin/users', component: view('admin/UserAndRbacView'), meta: { title: '用户与权限', roles: ['ADMIN'] } },
    { path: 'admin/audit', component: view('admin/ModelAndAuditLogView'), meta: { title: '模型与审计', roles: ['ADMIN'] } }
  ] },
  { path: '/:pathMatch(.*)*', redirect: '/workspace' }
]
const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(async to => {
  document.title = `${String(to.meta.title ?? '工作空间')} · FlowMind 智流`
  const auth = useAuthStore()
  if (to.meta.public) return true
  if (!auth.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (!auth.user) try { await auth.loadUser() } catch { auth.clear(); return '/login' }
  const roles = to.meta.roles as Role[] | undefined
  if (roles && (!auth.user || !roles.includes(auth.user.roleType))) return '/403'
  return true
})
export default router
