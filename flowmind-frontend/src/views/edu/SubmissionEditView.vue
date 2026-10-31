<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { eduApi, reviewStatusLabel, type EduTask, type Submission } from '@/api/eduApi'
import { workflowApi } from '@/api/workflowApi'
import { useAuthStore } from '@/stores/authStore'
import { formatTime, type Workflow, type WorkflowDetail } from '@/api/types'
import './edu.css'
const route = useRoute(), router = useRouter(), task = ref<EduTask>(), previous = ref<Submission>(), loading = ref(false), saving = ref(false), error = ref('')
const workflows = ref<Workflow[]>([]), workflowId = ref<number>(), report = ref(''), canvas = ref<WorkflowDetail>(), canvasLoading = ref(false)
const workflowKeyword = ref(''), workflowPage = ref(1), workflowTotal = ref(0), workflowLoading = ref(false)
const auth = useAuthStore()
const canSubmit = (w: Workflow) => w.creatorId === auth.user?.id || w.collaboratorIds?.includes(auth.user?.id ?? -1)
const precheck = computed(() => {
  if (!task.value || !canvas.value) return []
  const rule = task.value.requiredNodesJson, types = new Set(canvas.value.nodes.map(n => n.nodeType as string))
  return [ ...(canvas.value.nodes.length < rule.minNodes ? [`至少需要 ${rule.minNodes} 个节点，当前 ${canvas.value.nodes.length} 个`] : []), ...rule.mustInclude.filter(t => !types.has(t)).map(t => `缺少 ${t} 节点`) ]
})
async function loadWorkflows(reset = false) {
  if (reset) workflowPage.value = 1
  workflowLoading.value = true
  try {
    const page = await workflowApi.list({ scope: 'ALL', keyword: workflowKeyword.value, pageNum: workflowPage.value, pageSize: 10 })
    workflows.value = page.records; workflowTotal.value = page.total
  } catch { error.value = '工作流加载失败，请重试' } finally { workflowLoading.value = false }
}
async function load() {
  loading.value = true; error.value = ''; task.value = undefined; previous.value = undefined; canvas.value = undefined; workflowId.value = undefined; report.value = ''
  try {
    const t = await eduApi.task(Number(route.params.id)); task.value = t
    if (t.mySubmissionId) { previous.value = await eduApi.submission(t.mySubmissionId); workflowId.value = previous.value.workflowId; report.value = previous.value.studentReport }
    await loadWorkflows(true)
  } catch { error.value = '作业信息加载失败，请重试' } finally { loading.value = false }
}
let canvasRequest = 0
watch(workflowId, async id => {
  const requestId = ++canvasRequest; canvas.value = undefined
  if (!id) { canvasLoading.value = false; return }
  canvasLoading.value = true
  try { const detail = await workflowApi.detail(id); if (requestId === canvasRequest) canvas.value = detail }
  catch { if (requestId === canvasRequest) error.value = '无法加载当前工作流，请重新选择' }
  finally { if (requestId === canvasRequest) canvasLoading.value = false }
})
async function submit() {
  if (!workflowId.value || !report.value.trim()) { ElMessage.warning('请选择工作流并填写实训报告'); return }
  if (previous.value) {
    try { await ElMessageBox.confirm('重新提交将替换原快照并清空原评分，交由教师重新批改。', '确认重新提交', { type: 'warning' }) } catch { return }
  }
  saving.value = true; error.value = ''
  try {
    const result = await eduApi.submit({ taskId: Number(route.params.id), workflowId: workflowId.value, studentReport: report.value })
    previous.value = result; ElMessage.success(result.autoCheckPassed ? '作业已提交，画布快照已保存' : '作业已保存，请注意必含节点未达标预警')
  } catch { error.value = '提交失败，请检查工作流权限后重试' } finally { saving.value = false }
}
watch(() => route.params.id, load, { immediate: true })
</script>
<template>
  <div class="edu-page" v-loading="loading">
    <div class="edu-heading"><div><span class="eyebrow">SUBMISSION / 实训作业</span><h1>{{ task?.taskTitle ?? '提交实训作业' }}</h1><p>{{ task?.courseName }} · 截止 {{ formatTime(task?.deadline) }}</p></div><el-button @click="router.push('/edu/tasks')">返回课题列表</el-button></div>
    <el-alert v-if="error" class="edu-error" type="error" :title="error" :closable="false"/>
    <el-button v-if="!task && !loading" @click="load">重新加载</el-button>
    <template v-if="task">
      <section class="edu-panel"><h3>课题要求</h3><p class="edu-report">{{ task.taskRequirement }}</p><el-tag>至少 {{ task.requiredNodesJson.minNodes }} 个节点</el-tag> <el-tag v-for="type in task.requiredNodesJson.mustInclude" :key="type">{{ type }}</el-tag><p v-if="task.taskStatus === 2" class="edu-hint">课题已截止，继续提交会根据截止时间标记为逾期。</p><p v-if="task.taskStatus === 0" class="edu-hint">课题已归档，当前仅可查看。</p></section>
      <div class="edu-grid"><section class="edu-panel"><h3>选择已保存的工作流</h3>
        <div class="edu-filters"><el-input v-model="workflowKeyword" placeholder="工作流关键词" @keyup.enter="loadWorkflows(true)"/><el-button @click="loadWorkflows(true)">搜索</el-button></div>
        <el-select v-model="workflowId" v-loading="workflowLoading" style="width:100%" placeholder="选择本人或协作工作流" aria-label="提交工作流"><el-option v-for="w in workflows" :key="w.id" :value="w.id" :disabled="!canSubmit(w)" :label="`${w.wfName} · ${w.versionTag}${canSubmit(w) ? '' : '（仅可预览）'}`"/><el-option v-if="previous && !workflows.some(w => w.id === previous!.workflowId)" :value="previous.workflowId" :label="`上次提交的工作流 #${previous.workflowId}`"/></el-select>
        <el-pagination v-model:current-page="workflowPage" :total="workflowTotal" :page-size="10" small layout="prev, pager, next" @current-change="loadWorkflows()"/>
        <div v-loading="canvasLoading" style="min-height:70px"><el-alert v-if="canvas && precheck.length" type="warning" :closable="false" :title="precheck.join('；')"/><el-alert v-else-if="canvas" title="本地节点预检通过" type="success" :closable="false"/></div>
        <p class="edu-hint">先在画布编辑器保存修改。提交时服务器重新读取画布并固化快照，预检未通过仍允许提交。</p>
        <el-button v-if="workflowId" @click="router.push(`/workflow/editor/${workflowId}`)">打开工作流</el-button>
      </section><section class="edu-panel"><h3>批改反馈</h3><template v-if="previous"><p>{{ reviewStatusLabel(previous.reviewStatus) }} · {{ previous.isLate ? '逾期' : '按时' }} · {{ previous.autoCheckPassed ? '节点预检通过' : '节点未达标' }}</p><p>得分：{{ previous.score ?? '待评分' }}</p><p class="edu-report">{{ previous.teacherComment ?? '教师尚未填写评语' }}</p><p class="edu-hint">上次提交 {{ formatTime(previous.submittedAt) }} · 快照版本 {{ previous.versionTag }}</p><el-collapse><el-collapse-item title="查看已提交的固化快照"><pre class="edu-pre">{{ JSON.stringify(previous.canvasSnapshotJson, null, 2) }}</pre></el-collapse-item></el-collapse></template><el-empty v-else description="提交后可在这里查看批改结果"/></section></div>
      <section class="edu-panel"><h3>实训设计报告与心得</h3><el-input v-model="report" type="textarea" :rows="12" maxlength="20000" show-word-limit placeholder="说明设计思路、节点配置、测试过程与实训心得" aria-label="实训报告" :disabled="task.taskStatus === 0"/><div class="edu-actions"><el-button type="primary" :loading="saving" :disabled="task.taskStatus === 0 || canvasLoading || !canvas" @click="submit">{{ previous ? '更新提交并重新批改' : '提交作业' }}</el-button></div></section>
    </template>
  </div>
</template>
