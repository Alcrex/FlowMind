<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, Refresh, Connection } from '@element-plus/icons-vue'
import { workflowApi } from '@/api/workflowApi'
import { categories, categoryLabel, formatTime, type Workflow, type WorkflowQuery, type CreateWorkflow } from '@/api/types'
import { useAuthStore } from '@/stores/authStore'
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const loading = ref(false), rows = ref<Workflow[]>([]), total = ref(0), error = ref(false)
const query = reactive<WorkflowQuery>({ scope: String(route.query.scope ?? 'MINE'), keyword: '', pageNum: 1, pageSize: 10 })
const dates = ref<[string, string] | null>(null), dialog = ref(false), saving = ref(false), form = ref<FormInstance>()
const draft = reactive<CreateWorkflow>({ wfName: '', description: '', category: 'CODE_ASSIST', tags: '', isPublic: 0 })
const rules: FormRules = { wfName: [{ required: true, message: '请输入工作流名称', trigger: 'blur' }, { max: 128, message: '最多128个字符' }] }
const owner = computed(() => auth.user?.id)
let requestId = 0
async function load(reset = false) {
  if (reset) query.pageNum = 1
  if (query.minNodeCount !== undefined && query.maxNodeCount !== undefined && query.minNodeCount > query.maxNodeCount) { ElMessage.warning('节点数下限不能大于上限'); return }
  const current = ++requestId; loading.value = true; error.value = false
  try {
    const result = await workflowApi.list({ ...query, startTime: dates.value?.[0], endTime: dates.value?.[1] })
    if (current === requestId) { rows.value = result.records; total.value = result.total }
  } catch { if (current === requestId) error.value = true } finally { if (current === requestId) loading.value = false }
}
function reset() { Object.assign(query, { keyword: '', category: undefined, isPublic: undefined, minNodeCount: undefined, maxNodeCount: undefined, scope: 'MINE', pageNum: 1 }); dates.value = null; void load() }
async function create() {
  if (!(await form.value?.validate().catch(() => false))) return
  saving.value = true
  try { const result = await workflowApi.create(draft); dialog.value = false; ElMessage.success('工作流已创建'); await router.push(`/workflow/editor/${result.workflow.id}`) } finally { saving.value = false }
}
async function remove(w: Workflow) {
  const confirmed = await ElMessageBox.confirm(`确认删除「${w.wfName}」及其全部节点、连线？`, '删除工作流', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }).then(() => true).catch(() => false)
  if (!confirmed) return
  await workflowApi.remove(w.id); ElMessage.success('已删除'); if (rows.value.length === 1 && query.pageNum > 1) query.pageNum--; await load()
}
async function clone(w: Workflow) { const result = await workflowApi.clone(w.id); ElMessage.success('已完整克隆到个人空间'); await router.push(`/workflow/editor/${result.workflow.id}`) }
onMounted(() => { void load(); if (route.query.create === '1') dialog.value = true })
</script>
<template><section><div class="page-heading"><div><p class="eyebrow">WORKFLOWS</p><h1>连接每一个好想法<span class="heading-count">{{ total }}</span></h1><p class="subtitle">管理、编排，与团队一起构建智能工作流。</p></div><el-button type="primary" :icon="Plus" size="large" @click="dialog = true">新建工作流</el-button></div>
<div class="panel list-panel"><el-tabs v-model="query.scope" @tab-change="load(true)"><el-tab-pane label="我的工作流" name="MINE"/><el-tab-pane label="参与协作" name="COLLAB"/><el-tab-pane label="全部可见" name="ALL"/></el-tabs>
<el-form class="search-form" inline @submit.prevent="load(true)"><el-form-item><el-input v-model="query.keyword" placeholder="搜索名称、描述或标签" :prefix-icon="Search" clearable @keyup.enter="load(true)" /></el-form-item><el-form-item><el-select v-model="query.category" placeholder="全部分类" clearable><el-option v-for="c in categories" :key="c.value" :value="c.value" :label="c.label" /></el-select></el-form-item><el-form-item><el-select v-model="query.isPublic" placeholder="可见性" clearable><el-option :value="0" label="私有"/><el-option :value="1" label="公开"/></el-select></el-form-item>
<el-form-item label="节点数"><el-input-number v-model="query.minNodeCount" :min="0" :controls="false" placeholder="最少"/><span class="range-separator">—</span><el-input-number v-model="query.maxNodeCount" :min="0" :controls="false" placeholder="最多"/></el-form-item><el-form-item><el-date-picker v-model="dates" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss" start-placeholder="更新开始时间" end-placeholder="结束时间" /></el-form-item><el-form-item><el-button type="primary" :icon="Search" @click="load(true)">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button></el-form-item></el-form>
<el-alert v-if="error" title="列表加载失败，请重试" type="error" :closable="false"/>
<el-table v-loading="loading" :data="rows" empty-text="暂无符合条件的工作流"><el-table-column label="工作流" min-width="260"><template #default="{ row }"><div class="workflow-name-cell"><span class="workflow-glyph"><el-icon><Connection /></el-icon></span><div><el-link :underline="false" @click="router.push(`/workflow/editor/${row.id}`)">{{ row.wfName }}</el-link><small>{{ row.description || '暂无描述' }}</small></div></div></template></el-table-column><el-table-column label="分类" min-width="110"><template #default="{ row }">{{ categoryLabel(row.category) }}</template></el-table-column><el-table-column label="节点 / 连线" width="110"><template #default="{ row }">{{ row.nodeCount }} / {{ row.edgeCount }}</template></el-table-column><el-table-column prop="creatorName" label="创建者" width="100"/><el-table-column label="状态" width="85"><template #default="{ row }"><el-tag :type="row.isPublic ? 'success' : 'info'" effect="plain" size="small">{{ row.isPublic ? '公开' : '私有' }}</el-tag></template></el-table-column><el-table-column label="更新时间" min-width="160"><template #default="{ row }">{{ formatTime(row.updatedAt) }}</template></el-table-column><el-table-column label="操作" min-width="210" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="router.push(`/workflow/editor/${row.id}`)">打开</el-button><el-button link @click="clone(row)">克隆</el-button><el-button v-if="row.creatorId === owner || row.collaboratorIds?.includes(owner)" link @click="router.push(`/workflow/${row.id}/versions`)">版本</el-button><el-button v-if="row.creatorId === owner" link type="danger" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
<div class="pagination"><span>共 {{ total }} 个工作流</span><el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" :page-sizes="[10,20,50]" layout="sizes, prev, pager, next" @size-change="load(true)" @current-change="load()"/></div></div>
<el-dialog v-model="dialog" title="创建新的工作流" width="520px" destroy-on-close><el-form ref="form" :model="draft" :rules="rules" label-position="top"><el-form-item label="工作流名称" prop="wfName"><el-input v-model="draft.wfName" maxlength="128" show-word-limit placeholder="为你的想法取一个名字"/></el-form-item><el-form-item label="简介"><el-input v-model="draft.description" type="textarea" :rows="3" maxlength="1000" placeholder="它能帮助你完成什么？"/></el-form-item><el-form-item label="分类"><el-select v-model="draft.category"><el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value"/></el-select></el-form-item><el-form-item label="标签"><el-input v-model="draft.tags" maxlength="255" placeholder="多个标签以逗号分隔"/></el-form-item><el-form-item label="公开到模板广场"><el-switch v-model="draft.isPublic" :active-value="1" :inactive-value="0"/></el-form-item></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="create">创建并打开画布</el-button></template></el-dialog></section></template>
