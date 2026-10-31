<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { VueFlow, useVueFlow, type Node, type Edge, type Connection } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Clock, User, Back, Delete, Plus, Setting } from '@element-plus/icons-vue'
import { workflowApi } from '@/api/workflowApi'
import { nodeTypes, type CanvasNode, type CanvasEdge, type NodeType } from '@/api/types'
import { useAuthStore } from '@/stores/authStore'
import { useCanvasStore } from '@/stores/canvasStore'
import { CollabSocket } from '@/utils/collabSocket'
import WorkflowNode from '@/components/WorkflowNode.vue'
import NodeConfigDrawer from './NodeConfigDrawer.vue'
import CollabManageModal from './CollabManageModal.vue'
const route = useRoute(), router = useRouter(), auth = useAuthStore(), store = useCanvasStore()
const flow = useVueFlow({ id: 'workflow-editor' })
type EditorNode = Node<CanvasNode> & { selected?: boolean }
type EditorEdge = Edge & { selected?: boolean }
const flowNodes = shallowRef<EditorNode[]>([]), flowEdges = shallowRef<EditorEdge[]>([])
const loading = ref(false), saving = ref(false), failed = ref(false), ready = ref(false)
const selectedKey = ref<string>(), drawer = ref(false), collabOpen = ref(false), stage = ref<HTMLElement>()
const settingsOpen = ref(false)
const metadata = reactive({ wfName: '', description: '', isPublic: 0 })
let socket: CollabSocket | undefined, applying = false, generation = 0
const id = computed(() => Number(route.params.id))
const canEdit = computed(() => store.workflow && (store.workflow.creatorId === auth.user?.id || store.workflow.collaboratorIds?.includes(auth.user?.id ?? -1)))
watch(ready, async value => {
  if (!value) return
  await nextTick()
  requestAnimationFrame(() => { void flow.fitView({ padding: 0.18, maxZoom: 1 }) })
})
const selected = computed(() => store.nodes.find(n => n.elementKey === selectedKey.value))
const cursorStyles = computed(() => store.members.filter(m => m.x !== undefined && m.y !== undefined).map(m => {
  const viewport = flow.viewport.value
  return { ...m, left: `${m.x! * viewport.zoom + viewport.x}px`, top: `${m.y! * viewport.zoom + viewport.y}px` }
}))
function renderGraph(nodes: CanvasNode[], edges: CanvasEdge[]) {
  applying = true
  store.nodes = nodes; store.edges = edges
  const selection = new Set(flowNodes.value.filter(n => n.selected).map(n => n.id))
  flowNodes.value = nodes.map(n => ({ id: n.elementKey, type: 'workflow', label: n.labelName, position: { ...n.positionJson }, data: n, selected: selection.has(n.elementKey) }))
  flowEdges.value = edges.map(e => ({ id: e.elementKey, source: e.sourceKey, target: e.targetKey, sourceHandle: e.sourceHandle ?? 'default', label: e.labelName, type: 'smoothstep', animated: false }))
  applying = false
}
function exportGraph() {
  return {
    nodes: flowNodes.value.map(n => ({ ...n.data!, elementKey: n.id, positionJson: { ...n.position } })),
    edges: flowEdges.value.map(e => ({ elementKey: e.id, sourceKey: e.source, targetKey: e.target, sourceHandle: e.sourceHandle ?? 'default', labelName: typeof e.label === 'string' ? e.label : '' }))
  }
}
watch([flowNodes, flowEdges], () => {
  if (applying || !ready.value || !canEdit.value) return
  const graph = exportGraph()
  if (JSON.stringify(graph.nodes) === JSON.stringify(store.nodes) && JSON.stringify(graph.edges) === JSON.stringify(store.edges)) return
  store.nodes = graph.nodes; store.edges = graph.edges; store.dirty = true; socket?.setGraph(graph.nodes, graph.edges)
}, { deep: true, flush: 'sync' })
async function load() {
  const current = ++generation
  socket?.close(); socket = undefined; ready.value = false; store.reset(); renderGraph([], []); selectedKey.value = undefined; drawer.value = false
  loading.value = true; failed.value = false
  try {
    const result = await workflowApi.detail(id.value)
    if (current !== generation) return
    store.workflow = result.workflow; renderGraph(result.nodes, result.edges)
    if (!canEdit.value) { ready.value = true; return }
    socket = new CollabSocket(id.value, auth.token, {
      graph: (nodes, edges) => { const wasReady = ready.value; renderGraph(nodes, edges); if (wasReady) store.dirty = true },
      presence: members => { store.members = members.map(m => ({ ...store.members.find(p => p.userId === m.userId), ...m })) },
      cursor: cursor => { const member = store.members.find(m => m.userId === cursor.userId); if (member) Object.assign(member, cursor) },
      status: (connected, synchronized) => { store.connected = connected; ready.value = synchronized },
      membersChanged: ids => { if (store.workflow) store.workflow.collaboratorIds = ids },
      revoked: () => { ElMessage.info('协作权限或登录状态已变更'); void load() },
      refresh: () => { ready.value = false; ElMessage.info('工作流版本或协作权限已更新，正在重新加载'); void load() }
    })
  } catch { failed.value = true } finally { if (current === generation) loading.value = false }
}
watch(id, () => { void load() }, { immediate: true })
function defaultConfig(type: NodeType): Record<string, unknown> {
  const configs = {
    INPUT: { variables: [{ name: 'text', type: 'string', required: true }] },
    KNOWLEDGE: { knowledgeBase: 'ALI_JAVA_SPEC', queryTemplate: '{{input.text}}', topK: 3 },
    LLM: { model: 'DEEPSEEK', temperature: 0.7, systemPrompt: '你是一名专业助手。', userPromptTemplate: '{{input.text}}' },
    CODE: { scriptLang: 'TEXT', ruleType: 'TRIM', expression: '{{input.text}}' },
    CONDITION: { fieldExpr: '{{input.text}}', operator: 'CONTAINS', targetValue: '' },
    OUTPUT: { outputFormat: 'MARKDOWN', resultTemplate: '{{node_llm_1.output}}' }
  }
  return configs[type]
}
function addNode(type: NodeType, point?: { x: number; y: number }) {
  if (!ready.value || !canEdit.value) return
  if (!point) {
    point = { x: 360, y: 420 }
    while (store.nodes.some(n => Math.abs(n.positionJson.x - point!.x) < 230 && Math.abs(n.positionJson.y - point!.y) < 140)) {
      point.x += 280
      if (point.x > 920) { point.x = 80; point.y += 180 }
    }
  }
  const spec = nodeTypes.find(n => n.type === type)!
  const node: CanvasNode = { elementKey: `node_${type.toLowerCase()}_${crypto.randomUUID().slice(0, 8)}`, nodeType: type, labelName: spec.label, positionJson: point, configJson: defaultConfig(type) }
  flowNodes.value = [...flowNodes.value, { id: node.elementKey, type: 'workflow', position: point, data: node }]
}
function drop(event: DragEvent) {
  event.preventDefault(); const type = event.dataTransfer?.getData('flowmind/node') as NodeType
  if (nodeTypes.some(n => n.type === type)) addNode(type, flow.screenToFlowCoordinate({ x: event.clientX, y: event.clientY }))
}
function drag(event: DragEvent, type: NodeType) { event.dataTransfer?.setData('flowmind/node', type) }
function connect(connection: Connection) {
  if (!canEdit.value || !ready.value || connection.source === connection.target) return
  if (flowEdges.value.some(e => e.source === connection.source && e.target === connection.target && e.sourceHandle === connection.sourceHandle)) return
  flowEdges.value = [...flowEdges.value, { ...connection, id: `edge_${crypto.randomUUID().slice(0, 12)}`, type: 'smoothstep' }]
}
function configure(node: CanvasNode) {
  flowNodes.value = flowNodes.value.map(n => n.id === node.elementKey ? { ...n, data: node, label: node.labelName } : n)
  drawer.value = false
}
function removeSelected() {
  const ids = new Set(flowNodes.value.filter(n => n.selected).map(n => n.id))
  applying = true
  flowNodes.value = flowNodes.value.filter(n => !ids.has(n.id)); flowEdges.value = flowEdges.value.filter(e => !e.selected && !ids.has(e.source) && !ids.has(e.target))
  applying = false
  const graph = exportGraph(); store.nodes = graph.nodes; store.edges = graph.edges; store.dirty = true; socket?.setGraph(graph.nodes, graph.edges)
}
function move(event: PointerEvent) { const point = flow.screenToFlowCoordinate({ x: event.clientX, y: event.clientY }); socket?.cursor(point.x, point.y) }
function openSettings() {
  if (!store.workflow) return
  Object.assign(metadata, { wfName: store.workflow.wfName, description: store.workflow.description ?? '', isPublic: store.workflow.isPublic })
  settingsOpen.value = true
}
function applySettings() {
  if (!metadata.wfName.trim()) { ElMessage.warning('工作流名称不能为空'); return }
  if (store.workflow) Object.assign(store.workflow, { ...metadata, wfName: metadata.wfName.trim() })
  store.dirty = true; settingsOpen.value = false
}
async function save(snapshot = false) {
  if (!store.workflow || !ready.value || !canEdit.value) return
  let version: string | undefined
  if (snapshot) {
    const result = await ElMessageBox.prompt('输入唯一版本号，保存当前画布快照。', '保存新版本', { inputValue: '', inputValidator: value => !!value.trim() && value.length <= 32 || '版本号为1~32个字符', confirmButtonText: '保存版本', cancelButtonText: '取消' }).catch(() => null)
    if (!result) return
    version = result.value.trim()
  }
  saving.value = true
  try {
    const graph = exportGraph(), sent = JSON.stringify({ ...graph, wfName: store.workflow.wfName, description: store.workflow.description, isPublic: store.workflow.isPublic })
    const result = await workflowApi.save(id.value, { ...graph, wfName: store.workflow.wfName, description: store.workflow.description, isPublic: store.workflow.isPublic, createSnapshot: snapshot, newVersionTag: version })
    const unchanged = JSON.stringify({ ...exportGraph(), wfName: store.workflow.wfName, description: store.workflow.description, isPublic: store.workflow.isPublic }) === sent
    if (unchanged) { store.workflow = result.workflow; store.dirty = false }
    else { store.workflow.versionTag = result.workflow.versionTag; store.workflow.updatedAt = result.workflow.updatedAt }
    ElMessage.success(snapshot ? '版本快照已保存' : '画布已保存')
  } finally { saving.value = false }
}
function beforeUnload(event: BeforeUnloadEvent) { if (store.dirty) { event.preventDefault(); event.returnValue = '' } }
window.addEventListener('beforeunload', beforeUnload)
onBeforeRouteLeave(async () => !store.dirty || await ElMessageBox.confirm('画布尚有未保存的变更，确认离开？', '未保存变更', { type: 'warning', confirmButtonText: '离开', cancelButtonText: '继续编辑' }).then(() => true).catch(() => false))
onBeforeUnmount(() => { generation++; socket?.close(); store.reset(); window.removeEventListener('beforeunload', beforeUnload) })
</script>
<template><section v-loading="loading" class="editor-page"><div class="editor-heading"><div class="editor-title"><el-button :icon="Back" circle @click="router.push('/workflows')"/><div><h2>{{ store.workflow?.wfName ?? '工作流画布' }} <el-tag size="small" effect="plain">{{ store.workflow?.versionTag }}</el-tag></h2><p><span :class="['status-dot', { offline: !store.connected }]"/>{{ canEdit ? (store.connected ? '协同已连接' : '协同连接中 / 已断开') : '只读预览' }}<span class="dirty-label">{{ store.dirty ? '· 有未保存变更' : '· 已保存' }}</span></p></div></div><div class="editor-actions"><el-button v-if="canEdit" :icon="Setting" :disabled="!ready" @click="openSettings">设置</el-button><el-button v-if="canEdit" :icon="User" @click="collabOpen = true">协作 {{ store.members.length }}</el-button><el-button v-if="canEdit" :icon="Clock" @click="router.push(`/workflow/${id}/versions`)">版本</el-button><el-button v-if="canEdit" :disabled="!ready" @click="save(true)">保存新版本</el-button><el-button v-if="canEdit" type="primary" :icon="Check" :loading="saving" :disabled="!ready" @click="save()">保存画布</el-button></div></div>
<el-alert v-if="failed" type="error" title="画布加载失败" :closable="false"><el-button text @click="load">重试</el-button></el-alert><div class="editor-body"><aside v-if="canEdit" class="node-library"><p class="eyebrow">NODE LIBRARY</p><h3>节点工具箱</h3><p class="muted">拖入画布，开始连接</p><button v-for="spec in nodeTypes" :key="spec.type" class="node-palette-item" :draggable="ready" :disabled="!ready" @dragstart="drag($event, spec.type)" @click="addNode(spec.type)"><span :style="{ color: spec.color, background: spec.color + '15' }">{{ spec.type.slice(0, 2) }}</span><div><strong>{{ spec.label }}</strong><small>{{ spec.hint }}</small></div><el-icon><Plus /></el-icon></button><div class="canvas-tip">双击节点配置参数<br>选中节点或连线后点击删除<br>滚轮缩放 · 拖动画布平移</div><el-button :icon="Delete" :disabled="!ready" @click="removeSelected">删除选中元素</el-button></aside>
<div ref="stage" class="canvas-stage" @drop="drop" @dragover.prevent @pointermove="move"><VueFlow id="workflow-editor" v-model:nodes="flowNodes" v-model:edges="flowEdges" :nodes-draggable="!!canEdit && ready" :nodes-connectable="!!canEdit && ready" :elements-selectable="true" :delete-key-code="null" :min-zoom="0.15" :max-zoom="2" fit-view-on-init @connect="connect" @node-double-click="({ node }) => { selectedKey = node.id; drawer = true }"><template #node-workflow="nodeProps"><WorkflowNode :data="nodeProps.data" :selected="nodeProps.selected"/></template><Background :gap="22" pattern-color="#d4ddd7"/><Controls/><MiniMap :node-color="n => nodeTypes.find(t => t.type === n.data?.nodeType)?.color ?? '#888'"/></VueFlow>
<div class="canvas-caption">{{ store.nodes.length }} 个节点 <span>·</span> {{ store.edges.length }} 条连线</div><div class="cursor-layer"><div v-for="cursor in cursorStyles" :key="cursor.userId" class="remote-cursor" :style="{ left: cursor.left, top: cursor.top, color: cursor.color }"><svg width="20" height="24" viewBox="0 0 20 24"><path d="M2 2 L17 13 L10 14 L7 21 Z" fill="currentColor" stroke="white" stroke-width="1.5"/></svg><span :style="{ background: cursor.color }">{{ cursor.realName }}</span></div></div></div></div>
<el-dialog v-model="settingsOpen" title="工作流设置" width="500px"><el-form label-position="top"><el-form-item label="工作流名称"><el-input v-model="metadata.wfName" maxlength="128" show-word-limit/></el-form-item><el-form-item label="简介"><el-input v-model="metadata.description" type="textarea" :rows="4" maxlength="1000"/></el-form-item><el-form-item label="公开到模板广场"><el-switch v-model="metadata.isPublic" :active-value="1" :inactive-value="0" :disabled="store.workflow?.creatorId !== auth.user?.id"/></el-form-item></el-form><template #footer><el-button @click="settingsOpen = false">取消</el-button><el-button type="primary" @click="applySettings">应用设置</el-button></template></el-dialog>
<NodeConfigDrawer v-model="drawer" :node="selected" :readonly="!canEdit || !ready" @save="configure"/><CollabManageModal v-if="collabOpen" v-model="collabOpen" :workflow-id="id"/></section></template>
