<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { VueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import './engine.css'
import { workflowApi } from '@/api/workflowApi'
import { engineApi, type ExecDetail, type EngineEvent, type NodeLog } from '@/api/engineAndMarketApi'
import type { WorkflowDetail } from '@/api/types'
import { useCanvasStore } from '@/stores/canvasStore'
const route = useRoute(), router = useRouter(), canvasStore = useCanvasStore()
const canvas = ref<WorkflowDetail>(), loading = ref(false), running = ref(false), error = ref(''), output = ref('')
const inputText = ref('{}'), result = ref<ExecDetail>(), states = ref<Record<string,string>>({}), logs = ref<NodeLog[]>([])
let controller: AbortController | undefined
const nodes = computed(() => canvas.value?.nodes.map(n => ({ id: n.elementKey, position: n.positionJson, label: `${n.labelName} · ${states.value[n.elementKey] ?? '等待'}`, class: (states.value[n.elementKey] ?? '').toLowerCase(), type: n.nodeType === 'INPUT' ? 'input' : n.nodeType === 'OUTPUT' ? 'output' : 'default' })) ?? [])
const edges = computed(() => canvas.value?.edges.map(e => ({ id: e.elementKey, source: e.sourceKey, target: e.targetKey, label: e.sourceHandle === 'default' ? '' : e.sourceHandle, animated: states.value[e.sourceKey] === 'RUNNING' })) ?? [])
const variables = computed(() => canvas.value?.nodes.filter(n => n.nodeType === 'INPUT').flatMap(n => (n.configJson.variables ?? []) as { name: string; type?: string; required?: boolean; defaultValue?: unknown }[]) ?? [])
async function load() {
  controller?.abort(); loading.value = true; error.value = ''; result.value = undefined; output.value = ''; states.value = {}; logs.value = []
  try {
    canvas.value = await workflowApi.detail(Number(route.params.id))
    const defaults: Record<string, unknown> = {}
    variables.value.forEach(v => defaults[v.name] = v.defaultValue ?? (v.type === 'number' ? 0 : v.type === 'boolean' ? false : ''))
    inputText.value = JSON.stringify(defaults, null, 2)
  } catch { error.value = '加载工作流失败，请重试' } finally { loading.value = false }
}
function receive(event: EngineEvent) {
  const { name, data } = event, key = data.nodeKey
  if (key && name === 'NODE_START') states.value[key] = 'RUNNING'
  if (key && name === 'NODE_TOKEN') output.value += data.chunk ?? ''
  if (key && ['NODE_COMPLETE','NODE_SKIPPED','NODE_ERROR'].includes(name)) {
    states.value[key] = data.stepStatus ?? (name === 'NODE_SKIPPED' ? 'SKIPPED' : 'ERROR')
    logs.value.push(data as NodeLog)
  }
  if (name === 'WORKFLOW_COMPLETE' && data.record && data.nodeLogs) {
    result.value = { record: data.record, nodeLogs: data.nodeLogs }; logs.value = data.nodeLogs
    output.value = String(data.record.finalOutput?.report ?? output.value)
    if (data.record.execStatus === 'FAILED') error.value = data.record.errorSummary ?? '运行失败'
  }
}
async function run() {
  error.value = ''
  let payload: Record<string, unknown>
  try { payload = JSON.parse(inputText.value); if (!payload || typeof payload !== 'object' || Array.isArray(payload)) throw new Error() }
  catch { error.value = '输入必须是 JSON 对象'; return }
  running.value = true; result.value = undefined; states.value = {}; logs.value = []; output.value = ''; controller = new AbortController()
  try { await engineApi.stream({ workflowId: Number(route.params.id), triggerMode: 'SANDBOX', inputPayload: payload }, controller.signal, receive) }
  catch (e) { error.value = e instanceof Error && e.name === 'AbortError' ? '已断开显示；后台运行结果可在运行记录中查看。' : e instanceof Error ? e.message : '运行失败' }
  finally { running.value = false }
}
async function useUnsaved() {
  if (canvasStore.workflow?.id === Number(route.params.id) && canvasStore.dirty) {
    error.value = '画布存在未保存修改，请返回画布保存后运行。'; return
  }
  await run()
}
watch(() => route.params.id, load, { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>
<template>
  <div v-loading="loading">
    <div class="engine-heading"><div><span class="eyebrow">SANDBOX / 调试沙箱</span><h1>{{ canvas?.workflow.wfName ?? '工作流调试' }}</h1><p>逐步追踪节点，实时查看模型输出与算力消耗。</p></div><div><el-button @click="router.push(`/workflow/editor/${route.params.id}`)">返回画布</el-button><el-button @click="router.push('/engine/records')">运行记录</el-button></div></div>
    <el-alert v-if="error" class="engine-error" :title="error" type="error" :closable="false"/>
    <div v-if="canvas" class="engine-stage"><VueFlow :nodes="nodes" :edges="edges" fit-view-on-init :nodes-draggable="false" :nodes-connectable="false"/></div>
    <div class="engine-grid"><section class="engine-panel"><h3>运行输入</h3><p class="engine-hint">填写 JSON 输入参数，变量需与 INPUT 节点定义一致。</p><div v-for="v in variables" :key="v.name" class="engine-variable"><el-tag>{{ v.name }} · {{ v.type ?? 'string' }}{{ v.required ? ' · 必填' : '' }}</el-tag></div><el-input v-model="inputText" type="textarea" :rows="12" class="code-input" :disabled="running" aria-label="运行输入 JSON"/><div class="engine-actions"><el-button type="primary" :loading="running" :disabled="!canvas" @click="useUnsaved">运行工作流</el-button><el-button v-if="running" @click="controller?.abort()">断开显示</el-button><el-button v-if="!canvas" @click="load">重新加载</el-button></div></section>
    <section class="engine-panel"><h3>输出结果 <el-tag v-if="running">流式生成中</el-tag></h3><div v-if="result" class="engine-summary"><span>{{ result.record.execStatus }}</span><span>{{ result.record.totalTokens }} Tokens</span><span>{{ result.record.durationMs }} ms</span><el-link type="primary" @click="router.push(`/engine/records/${result.record.id}`)">查看明细</el-link></div><pre v-if="output" class="engine-pre" aria-live="polite">{{ output }}</pre><el-empty v-else description="运行后将在此显示结果"/></section></div>
    <section class="engine-panel"><h3>节点追踪</h3><el-table :data="logs" empty-text="尚无节点执行日志"><el-table-column prop="stepOrder" label="步骤" width="70"/><el-table-column prop="nodeName" label="节点"/><el-table-column prop="stepStatus" label="状态"/><el-table-column prop="tokenCost" label="Tokens"/><el-table-column prop="costMs" label="耗时 ms"/></el-table></section>
  </div>
</template>
