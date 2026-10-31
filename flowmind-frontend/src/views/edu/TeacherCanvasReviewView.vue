<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Position, VueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { eduApi, reviewStatusLabel, type Submission } from '@/api/eduApi'
import { engineApi, type EngineEvent, type NodeLog } from '@/api/engineAndMarketApi'
import { formatTime } from '@/api/types'
import './edu.css'
const route = useRoute(), router = useRouter(), submission = ref<Submission>(), loading = ref(false), error = ref(''), saving = ref(false), running = ref(false)
const inputText = ref('{}'), output = ref(''), logs = ref<NodeLog[]>([]), states = ref<Record<string,string>>({}), selected = ref('')
const grade = reactive({ reviewStatus: 1, teacherComment: '', score: 0, details: {} as Record<string,number> })
let controller: AbortController | undefined
const snapshot = computed(() => submission.value?.canvasSnapshotJson)
const nodes = computed(() => snapshot.value?.nodes.map((n,i) => ({ id: n.elementKey, position: n.positionJson ?? { x: 80+i*240, y: 120 }, sourcePosition: Position.Right, targetPosition: Position.Left, label: `${n.labelName ?? n.nodeType} · ${states.value[n.elementKey] ?? n.nodeType}`, type: n.nodeType === 'INPUT' ? 'input' : n.nodeType === 'OUTPUT' ? 'output' : 'default', class: states.value[n.elementKey]?.toLowerCase() })) ?? [])
const edges = computed(() => snapshot.value?.edges.map((e,i) => ({ id: e.elementKey ?? `edge-${i}`, source: e.sourceKey, target: e.targetKey, label: e.sourceHandle === 'default' ? '' : e.sourceHandle, animated: states.value[e.sourceKey] === 'RUNNING' })) ?? [])
const selectedNode = computed(() => snapshot.value?.nodes.find(n => n.elementKey === selected.value))
const total = computed(() => submission.value?.rubricJson?.length ? Math.round(submission.value.rubricJson.reduce((sum,r) => sum+(grade.details[r.item] ?? 0),0)*100)/100 : grade.score)
async function load() {
  controller?.abort(); loading.value = true; error.value = ''; submission.value = undefined; output.value = ''; logs.value = []; states.value = {}; selected.value = ''
  try {
    const s = await eduApi.submission(Number(route.params.id)); submission.value = s
    Object.assign(grade, { reviewStatus: s.reviewStatus === 2 ? 2 : 1, teacherComment: s.teacherComment ?? '', score: s.score ?? 0, details: { ...s.rubricDetailJson } })
    s.rubricJson?.forEach(r => grade.details[r.item] ??= 0)
    const defaults: Record<string,unknown> = {}
    s.canvasSnapshotJson.nodes.filter(n => n.nodeType === 'INPUT').forEach(n => {
      const variables = (n.configJson?.variables ?? []) as { name: string; defaultValue?: unknown; type?: string }[]
      variables.forEach(v => defaults[v.name] = v.defaultValue ?? (v.type === 'number' ? 0 : v.type === 'boolean' ? false : ''))
    })
    inputText.value = JSON.stringify(defaults,null,2)
  } catch { error.value = '作业加载失败，请重试' } finally { loading.value = false }
}
function receive(event: EngineEvent) {
  const { name, data } = event, key = data.nodeKey
  if (key && name === 'NODE_START') states.value[key] = 'RUNNING'
  if (key && name === 'NODE_TOKEN') output.value += data.chunk ?? ''
  if (key && ['NODE_COMPLETE','NODE_SKIPPED','NODE_ERROR'].includes(name)) {
    states.value[key] = data.stepStatus ?? (name === 'NODE_SKIPPED' ? 'SKIPPED' : 'ERROR'); logs.value.push(data as NodeLog)
  }
  if (name === 'WORKFLOW_COMPLETE' && data.record && data.nodeLogs) {
    logs.value = data.nodeLogs; output.value = String(data.record.finalOutput?.report ?? JSON.stringify(data.record.finalOutput,null,2))
    if (data.record.execStatus === 'FAILED') error.value = data.record.errorSummary ?? '试跑失败，请查看节点日志'
  }
}
async function run() {
  const s = submission.value; if (!s) return
  let payload: Record<string,unknown>
  try { payload = JSON.parse(inputText.value); if (!payload || Array.isArray(payload) || typeof payload !== 'object') throw new Error() }
  catch { error.value = '运行输入必须是 JSON 对象'; return }
  running.value = true; error.value = ''; output.value = ''; logs.value = []; states.value = {}; controller = new AbortController()
  try { await engineApi.stream({ workflowId: s.workflowId, triggerMode: 'TEACHER_REVIEW', inputPayload: payload, canvasOverride: { nodes: s.canvasSnapshotJson.nodes, edges: s.canvasSnapshotJson.edges } }, controller.signal, receive) }
  catch (e) { error.value = e instanceof Error && e.name === 'AbortError' ? '已断开试跑显示，可在运行记录查看结果' : e instanceof Error ? e.message : '试跑失败' }
  finally { running.value = false }
}
async function saveGrade() {
  if (!submission.value) return
  if (!grade.teacherComment.trim()) { ElMessage.warning('请填写教师评语'); return }
  if (total.value == null || total.value < 0 || total.value > 100) { ElMessage.warning('总分应为0至100分'); return }
  saving.value = true
  try {
    submission.value = await eduApi.grade(submission.value.id, { reviewStatus: grade.reviewStatus, teacherComment: grade.teacherComment, score: grade.reviewStatus === 1 ? total.value : null, rubricDetailJson: grade.reviewStatus === 1 && submission.value.rubricJson?.length ? grade.details : null })
    ElMessage.success(grade.reviewStatus === 1 ? '评分已保存' : '作业已打回重做')
  } catch { error.value = '批改保存失败，请检查分项得分后重试' } finally { saving.value = false }
}
watch(() => route.params.id, load, { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>
<template>
  <div class="edu-page" v-loading="loading"><div class="edu-heading"><div><span class="eyebrow">CANVAS REVIEW / 快照评测</span><h1>{{ submission?.taskTitle ?? '画布回放与批改' }}</h1><p>{{ submission?.studentName }} · {{ submission?.studentNumber }} · {{ submission?.targetClass }}</p></div><el-button @click="router.push('/edu/grades')">返回作业列表</el-button></div>
    <el-alert v-if="error" class="edu-error" :title="error" type="error" :closable="false"/><el-button v-if="!submission && !loading" @click="load">重新加载</el-button>
    <template v-if="submission"><section class="edu-panel"><p>提交于 {{ formatTime(submission.submittedAt) }} · 版本 {{ submission.versionTag }} · {{ reviewStatusLabel(submission.reviewStatus) }}</p><el-tag :type="submission.isLate ? 'warning' : 'success'">{{ submission.isLate ? '逾期提交' : '按时提交' }}</el-tag> <el-tag :type="submission.autoCheckPassed ? 'success' : 'danger'">{{ submission.autoCheckPassed ? '节点预检通过' : '必含节点未达标' }}</el-tag><p class="edu-hint">以下画布来自提交时固化的快照。试跑使用此快照并消耗当前教师账号的算力。</p></section>
      <div class="edu-stage"><VueFlow :key="submission.id" :nodes="nodes" :edges="edges" fit-view-on-init :nodes-draggable="false" :nodes-connectable="false" :delete-key-code="null" @node-click="selected = $event.node.id"/></div>
      <section v-if="selectedNode" class="edu-panel"><h3>{{ selectedNode.labelName ?? selectedNode.nodeType }} · 配置快照</h3><pre class="edu-pre">{{ JSON.stringify(selectedNode.configJson,null,2) }}</pre></section>
      <div class="edu-grid"><section class="edu-panel"><h3>快照沙箱试跑</h3><el-input v-model="inputText" type="textarea" :rows="7" :disabled="running" class="edu-code" aria-label="试跑输入 JSON"/><div class="edu-actions"><el-button type="primary" :loading="running" @click="run">试跑提交快照</el-button><el-button v-if="running" @click="controller?.abort()">断开显示</el-button></div><pre v-if="output" class="edu-pre" aria-live="polite">{{ output }}</pre><el-table :data="logs" empty-text="试跑后显示节点日志"><el-table-column prop="nodeName" label="节点"/><el-table-column prop="stepStatus" label="状态"/><el-table-column prop="costMs" label="耗时 ms"/><el-table-column type="expand"><template #default="{row}"><pre class="edu-pre">输入：{{ row.nodeInput }}
输出：{{ row.nodeOutput }}</pre></template></el-table-column></el-table></section>
        <section class="edu-panel"><h3>在线批改</h3><el-form label-position="top" :disabled="saving"><el-form-item label="处理结果"><el-radio-group v-model="grade.reviewStatus"><el-radio :value="1">评分完成</el-radio><el-radio :value="2">打回重做</el-radio></el-radio-group></el-form-item>
          <template v-if="grade.reviewStatus === 1"><el-form-item v-for="r in submission.rubricJson ?? []" :key="r.item" :label="`${r.item}（满分 ${r.maxScore}）`"><el-input-number v-model="grade.details[r.item]" :min="0" :max="r.maxScore" :precision="2"/></el-form-item><el-form-item v-if="!submission.rubricJson?.length" label="总分"><el-input-number v-model="grade.score" :min="0" :max="100" :precision="2"/></el-form-item><p>总分：<strong>{{ total }}</strong> / 100</p></template>
          <el-form-item label="教师评语" required><el-input v-model="grade.teacherComment" type="textarea" :rows="6" maxlength="20000" placeholder="填写评价或重做要求"/></el-form-item><el-button type="primary" :loading="saving" @click="saveGrade">{{ grade.reviewStatus === 1 ? '保存评分' : '打回重做' }}</el-button></el-form></section></div>
      <section class="edu-panel"><h3>学生实训报告</h3><p class="edu-report">{{ submission.studentReport }}</p></section>
    </template>
  </div>
</template>
