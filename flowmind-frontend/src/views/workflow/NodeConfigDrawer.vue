<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { nodeTypes, type CanvasNode, type WorkflowDetail } from '@/api/types'
import { workflowApi } from '@/api/workflowApi'
import { useAuthStore } from '@/stores/authStore'
const props = defineProps<{ node?: CanvasNode; readonly?: boolean }>()
const open = defineModel<boolean>({ default: true })
const emit = defineEmits<{ save: [node: CanvasNode] }>()
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const standalone = computed(() => route.path.includes('/node/'))
const detail = ref<WorkflowDetail>(), source = ref<CanvasNode>(), busy = ref(false)
const label = ref(''), configText = ref('{}'), variables = ref('[]'), error = ref('')
const values = reactive({ model: 'DEEPSEEK', temperature: 0.7, systemPrompt: '', userPromptTemplate: '', knowledgeBase: 'ALI_JAVA_SPEC', queryTemplate: '', topK: 3, scriptLang: 'TEXT', ruleType: 'TRIM', expression: '', fieldExpr: '', operator: 'CONTAINS', targetValue: '', outputFormat: 'MARKDOWN', resultTemplate: '' })
const spec = computed(() => nodeTypes.find(n => n.type === source.value?.nodeType))
const readOnly = computed(() => props.readonly || (standalone.value && detail.value && detail.value.workflow.creatorId !== auth.user?.id && !detail.value.workflow.collaboratorIds.includes(auth.user?.id ?? -1)))
function initialize(node?: CanvasNode) {
  source.value = node; label.value = node?.labelName ?? ''; configText.value = JSON.stringify(node?.configJson ?? {}, null, 2); error.value = ''
  const c = node?.configJson ?? {}
  for (const key of Object.keys(values) as (keyof typeof values)[]) {
    const fallback = key === 'model' ? 'DEEPSEEK' : key === 'knowledgeBase' ? 'ALI_JAVA_SPEC' : key === 'operator' ? 'CONTAINS' : key === 'outputFormat' ? 'MARKDOWN' : key === 'scriptLang' ? 'TEXT' : key === 'ruleType' ? 'TRIM' : ''
    if (key === 'temperature') values.temperature = Number(c[key] ?? 0.7)
    else if (key === 'topK') values.topK = Number(c[key] ?? 3)
    else values[key] = String(c[key] ?? fallback)
  }
  variables.value = JSON.stringify(c.variables ?? [], null, 2)
}
watch(() => props.node, initialize, { immediate: true })
watch(() => route.fullPath, async () => {
  if (!standalone.value) return
  busy.value = true
  try { detail.value = await workflowApi.detail(Number(route.params.id)); initialize(detail.value.nodes.find(n => n.elementKey === route.params.nodeKey)) } finally { busy.value = false }
}, { immediate: true })
async function save() {
  if (!source.value || readOnly.value) return
  if (!label.value.trim()) { error.value = '节点名称不能为空'; return }
  try {
    const advanced = JSON.parse(configText.value)
    if (!advanced || Array.isArray(advanced) || typeof advanced !== 'object') throw new Error('扩展配置必须是 JSON 对象')
    let fields: Record<string, unknown> = {}
    switch (source.value.nodeType) {
      case 'INPUT': {
        const parsed = JSON.parse(variables.value)
        if (!Array.isArray(parsed) || parsed.some(v => !v || typeof v.name !== 'string' || !v.name.trim() || typeof v.type !== 'string')) throw new Error('变量应为含 name、type 的数组')
        fields = { variables: parsed }; break
      }
      case 'LLM': fields = { model: values.model, temperature: values.temperature, systemPrompt: values.systemPrompt, userPromptTemplate: values.userPromptTemplate }; break
      case 'KNOWLEDGE': fields = { knowledgeBase: values.knowledgeBase, queryTemplate: values.queryTemplate, topK: values.topK }; break
      case 'CODE': fields = { scriptLang: values.scriptLang, ruleType: values.ruleType, expression: values.expression }; break
      case 'CONDITION': fields = { fieldExpr: values.fieldExpr, operator: values.operator, targetValue: values.targetValue }; break
      case 'OUTPUT': fields = { outputFormat: values.outputFormat, resultTemplate: values.resultTemplate }; break
    }
    const node: CanvasNode = { ...source.value, labelName: label.value.trim(), configJson: { ...advanced, ...fields } }
    if (standalone.value) {
      busy.value = true
      const latest = await workflowApi.detail(Number(route.params.id))
      if (!latest.nodes.some(n => n.elementKey === node.elementKey)) throw new Error('节点已被删除，请返回画布刷新')
      await workflowApi.save(latest.workflow.id, { wfName: latest.workflow.wfName, description: latest.workflow.description, isPublic: latest.workflow.isPublic, createSnapshot: false, nodes: latest.nodes.map(n => n.elementKey === node.elementKey ? node : n), edges: latest.edges })
      ElMessage.success('节点已保存'); await router.push(`/workflow/editor/${latest.workflow.id}`)
    } else emit('save', node)
  } catch (ex) { error.value = ex instanceof Error ? ex.message : '配置格式有误' } finally { busy.value = false }
}
function closed() { if (standalone.value) void router.push(`/workflow/editor/${route.params.id}`) }
</script>
<template><el-drawer v-model="open" :title="`${spec?.label ?? '节点'} · 参数配置`" size="480px" destroy-on-close @closed="closed"><div v-loading="busy"><el-empty v-if="!source" description="未找到节点，请返回画布选择"/><el-form v-else label-position="top" :disabled="!!readOnly"><el-tag effect="plain">{{ source.elementKey }}</el-tag><el-form-item label="节点名称"><el-input v-model="label" maxlength="128" show-word-limit/></el-form-item>
<template v-if="source.nodeType === 'INPUT'"><el-form-item label="输入变量（JSON 数组）"><el-input v-model="variables" type="textarea" :rows="10" class="code-input" placeholder='[{"name":"text","type":"string","required":true}]'/></el-form-item></template>
<template v-if="source.nodeType === 'LLM'"><el-form-item label="模型"><el-select v-model="values.model"><el-option label="DeepSeek" value="DEEPSEEK"/><el-option label="通义千问" value="QWEN"/><el-option label="本地沙箱" value="MOCK_LOCAL"/></el-select></el-form-item><el-form-item label="温度"><el-slider v-model="values.temperature" :min="0" :max="2" :step="0.1" show-input/></el-form-item><el-form-item label="系统提示词"><el-input v-model="values.systemPrompt" type="textarea" :rows="5"/></el-form-item><el-form-item label="用户提示词模板"><el-input v-model="values.userPromptTemplate" type="textarea" :rows="7" class="code-input"/></el-form-item></template>
<template v-if="source.nodeType === 'KNOWLEDGE'"><el-form-item label="知识库"><el-select v-model="values.knowledgeBase"><el-option label="阿里 Java 开发手册" value="ALI_JAVA_SPEC"/><el-option label="OWASP Web 安全" value="OWASP"/><el-option label="Spring Boot 最佳实践" value="SPRING_BOOT"/><el-option label="Vue 3 工程规范" value="VUE3"/></el-select></el-form-item><el-form-item label="检索数量 Top K"><el-input-number v-model="values.topK" :min="1" :max="20"/></el-form-item><el-form-item label="查询模板"><el-input v-model="values.queryTemplate" type="textarea" :rows="6"/></el-form-item></template>
<template v-if="source.nodeType === 'CODE'"><el-form-item label="脚本语言"><el-input v-model="values.scriptLang"/></el-form-item><el-form-item label="处理规则"><el-select v-model="values.ruleType"><el-option label="清理首尾空格" value="TRIM"/><el-option label="正则提取" value="REGEX_EXTRACT"/><el-option label="长度统计" value="LENGTH"/><el-option label="JSON 格式化" value="JSON_FORMAT"/></el-select></el-form-item><el-form-item label="表达式 / 脚本"><el-input v-model="values.expression" type="textarea" :rows="12" class="code-input"/></el-form-item></template>
<template v-if="source.nodeType === 'CONDITION'"><el-form-item label="字段表达式"><el-input v-model="values.fieldExpr"/></el-form-item><el-form-item label="比较方式"><el-select v-model="values.operator"><el-option label="包含" value="CONTAINS"/><el-option label="等于" value="EQUALS"/><el-option label="长度大于" value="LENGTH_GT"/></el-select></el-form-item><el-form-item label="目标值"><el-input v-model="values.targetValue"/></el-form-item><el-alert title="将“是”和“否”锚点分别连接到对应的后续节点。" :closable="false"/></template>
<template v-if="source.nodeType === 'OUTPUT'"><el-form-item label="输出格式"><el-select v-model="values.outputFormat"><el-option value="MARKDOWN" label="Markdown"/><el-option value="JSON" label="JSON"/></el-select></el-form-item><el-form-item label="结果模板"><el-input v-model="values.resultTemplate" type="textarea" :rows="12" class="code-input"/></el-form-item></template>
<p class="muted">模板变量示例：<code v-pre>{{node_input_1.text}}</code></p><el-collapse><el-collapse-item title="扩展配置（保留自定义字段）"><el-input v-model="configText" type="textarea" :rows="10" class="code-input"/><p class="muted">上方表单字段优先；此处用于补充扩展参数。</p></el-collapse-item></el-collapse><el-alert v-if="error" :title="error" type="error" :closable="false"/></el-form></div><template #footer><el-button @click="open = false">关闭</el-button><el-button v-if="!readOnly" type="primary" :loading="busy" :disabled="!source" @click="save">应用配置</el-button></template></el-drawer></template>
