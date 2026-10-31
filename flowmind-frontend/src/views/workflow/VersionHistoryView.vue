<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back, Refresh } from '@element-plus/icons-vue'
import { workflowApi } from '@/api/workflowApi'
import { formatTime, type VersionSnapshot, type WorkflowDetail } from '@/api/types'
const route = useRoute(), router = useRouter(), id = Number(route.params.id)
const versions = ref<VersionSnapshot[]>([]), detail = ref<WorkflowDetail>(), loading = ref(false), busy = ref(false), failed = ref(false)
const selected = ref(''), baseline = ref('__current'), page = ref(1), pageSize = 8
const rows = computed(() => [...versions.value].reverse().slice((page.value - 1) * pageSize, page.value * pageSize))
const target = computed(() => versions.value.find(v => v.versionTag === selected.value))
const comparison = computed(() => baseline.value === '__current' ? detail.value : versions.value.find(v => v.versionTag === baseline.value))
function normalized(value: unknown) { return JSON.stringify(value, (key, item) => ['id', 'workflowId', 'createdAt', 'updatedAt', 'elementType'].includes(key) ? undefined : item) }
const differences = computed(() => {
  if (!target.value || !comparison.value) return []
  const before = new Map([...comparison.value.nodes, ...comparison.value.edges].map(n => [n.elementKey, n]))
  const after = new Map([...target.value.nodes, ...target.value.edges].map(n => [n.elementKey, n]))
  return [...new Set([...before.keys(), ...after.keys()])].map(key => ({ key, before: before.get(key), after: after.get(key), kind: !before.has(key) ? '新增' : !after.has(key) ? '移除' : normalized(before.get(key)) !== normalized(after.get(key)) ? '修改' : '相同' })).filter(d => d.kind !== '相同')
})
async function load() {
  loading.value = true; failed.value = false
  try { [detail.value, versions.value] = await Promise.all([workflowApi.detail(id), workflowApi.versions(id)]); selected.value ||= versions.value.at(-1)?.versionTag ?? '' } catch { failed.value = true } finally { loading.value = false }
}
async function rollback(versionTag: string) {
  const confirmed = await ElMessageBox.confirm(`将画布恢复至 ${versionTag}。请先保存其他窗口尚未持久化的变更。`, '回滚版本', { type: 'warning', confirmButtonText: '确认回滚', cancelButtonText: '取消' }).then(() => true).catch(() => false)
  if (!confirmed) return
  busy.value = true
  try { await workflowApi.rollback(id, versionTag); ElMessage.success('版本已回滚'); await load() } finally { busy.value = false }
}
function selectVersion(row: VersionSnapshot | undefined) { if (row) selected.value = row.versionTag }
onMounted(load)
</script>
<template><section v-loading="loading"><div class="page-heading"><div><p class="eyebrow">VERSION HISTORY</p><h1>每一步创作，都有迹可循。</h1><p class="subtitle">{{ detail?.workflow.wfName }} · 当前 {{ detail?.workflow.versionTag }}</p></div><el-button :icon="Back" @click="router.push(`/workflow/editor/${id}`)">返回画布</el-button></div><el-alert v-if="failed" title="版本加载失败" type="error" :closable="false"><el-button text @click="load">重试</el-button></el-alert><div class="panel list-panel"><div class="panel-heading"><h2>已保存版本</h2><el-button :icon="Refresh" @click="load">刷新</el-button></div><el-table :data="rows" empty-text="尚无版本快照，可在画布中保存新版本" highlight-current-row @current-change="selectVersion"><el-table-column prop="versionTag" label="版本"/><el-table-column label="保存时间" min-width="180"><template #default="{ row }">{{ formatTime(row.savedAt) }}</template></el-table-column><el-table-column prop="savedBy" label="保存者 ID"/><el-table-column label="节点 / 连线"><template #default="{ row }">{{ row.nodes.length }} / {{ row.edges.length }}</template></el-table-column><el-table-column label="操作"><template #default="{ row }"><el-button link type="primary" @click="selected = row.versionTag">对比</el-button><el-button link type="warning" :loading="busy" @click.stop="rollback(row.versionTag)">回滚</el-button></template></el-table-column></el-table><div class="pagination"><span>{{ versions.length }} 个快照</span><el-pagination v-model:current-page="page" :total="versions.length" :page-size="pageSize" layout="prev, pager, next"/></div></div>
<div class="panel list-panel version-compare"><div class="panel-heading"><div><h2>快照差异</h2><p>从基准画布到所选快照的节点、参数及连线变化</p></div><div class="compare-controls"><el-select v-model="baseline" placeholder="基准版本"><el-option value="__current" label="当前画布"/><el-option v-for="v in versions" :key="v.versionTag" :value="v.versionTag" :label="v.versionTag"/></el-select><span>→</span><el-select v-model="selected" placeholder="目标快照"><el-option v-for="v in versions" :key="v.versionTag" :value="v.versionTag" :label="v.versionTag"/></el-select></div></div><el-table :data="differences" empty-text="没有元素差异"><el-table-column type="expand"><template #default="{ row }"><div class="diff-content"><div><h4>基准</h4><pre>{{ JSON.stringify(row.before ?? null, null, 2) }}</pre></div><div><h4>目标快照</h4><pre>{{ JSON.stringify(row.after ?? null, null, 2) }}</pre></div></div></template></el-table-column><el-table-column prop="key" label="元素 Key"/><el-table-column prop="kind" label="变化" width="120"/></el-table></div></section></template>
