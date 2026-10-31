<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { eduApi, taskStatusLabel, type EduTask, type TaskQuery, type TaskSave } from '@/api/eduApi'
import { formatTime, nodeTypes } from '@/api/types'
import './edu.css'
const router = useRouter(), loading = ref(false), saving = ref(false), error = ref(''), rows = ref<EduTask[]>([]), total = ref(0), dialog = ref(false), editingId = ref<number>()
const filters = reactive<TaskQuery>({ keyword: '', courseName: '', targetClass: '', taskStatus: undefined, pageNum: 1, pageSize: 10 })
const blank = (): TaskSave => ({ taskTitle: '', courseName: '', targetClass: '', taskRequirement: '', requiredNodesJson: { minNodes: 4, mustInclude: ['INPUT','LLM','OUTPUT'] }, rubricJson: [{ item: '拓扑设计', maxScore: 30 },{ item: 'Prompt质量', maxScore: 40 },{ item: '实训报告', maxScore: 30 }], deadline: '', taskStatus: 1 })
const form = reactive<TaskSave>(blank())
let requestVersion = 0
async function load(reset = false) {
  if (reset) filters.pageNum = 1
  const version = ++requestVersion; loading.value = true; error.value = ''
  try { const page = await eduApi.tasks({ ...filters }); if (version !== requestVersion) return; rows.value = page.records; total.value = page.total }
  catch { if (version === requestVersion) error.value = '课题加载失败，请重试' } finally { if (version === requestVersion) loading.value = false }
}
function clear() { Object.assign(filters, { keyword: '', courseName: '', targetClass: '', taskStatus: undefined }); void load(true) }
function edit(row?: EduTask) {
  editingId.value = row?.id
  Object.assign(form, row ? JSON.parse(JSON.stringify(row)) : blank())
  form.deadline = form.deadline.replace('T',' '); dialog.value = true
}
async function save() {
  if (![form.taskTitle,form.courseName,form.targetClass,form.taskRequirement,form.deadline].every(s => s.trim())) { ElMessage.warning('请填写全部必填项'); return }
  const rubric = form.rubricJson
  if (rubric && (rubric.some(r => !r.item.trim() || r.maxScore <= 0) || Math.abs(rubric.reduce((n,r) => n+r.maxScore,0)-100) > .001 || new Set(rubric.map(r => r.item)).size !== rubric.length)) { ElMessage.warning('评分项名称须唯一，满分合计须为100'); return }
  saving.value = true
  try { await eduApi.saveTask(editingId.value, form); dialog.value = false; ElMessage.success('课题已保存'); await load() }
  catch { error.value = '保存失败，请检查课题内容后重试' }
  finally { saving.value = false }
}
async function remove(row: EduTask) {
  try { await ElMessageBox.confirm(`删除“${row.taskTitle}”会同时删除该课题全部作业，确认删除？`, '删除课题', { type: 'warning' }) } catch { return }
  try { await eduApi.deleteTask(row.id); ElMessage.success('课题及作业已删除'); if (rows.value.length === 1 && filters.pageNum > 1) filters.pageNum--; await load() }
  catch { error.value = '删除失败，请重试' }
}
onMounted(() => load())
</script>
<template>
  <div class="edu-page"><div class="edu-heading"><div><span class="eyebrow">TEACHING / 课题管理</span><h1>实训课题管理</h1><p>发布实训要求，配置必含节点与分项评分细则。</p></div><el-button type="primary" @click="edit()">发布课题</el-button></div>
    <section class="edu-panel"><el-form class="edu-filters" @submit.prevent="load(true)"><el-input v-model="filters.keyword" placeholder="课题名称 / 编号" clearable/><el-input v-model="filters.courseName" placeholder="课程" clearable/><el-input v-model="filters.targetClass" placeholder="班级（精确匹配）" clearable/><el-select v-model="filters.taskStatus" placeholder="状态" clearable><el-option v-for="s in [1,2,0]" :key="s" :label="taskStatusLabel(s)" :value="s"/></el-select><el-button type="primary" native-type="submit">查询</el-button><el-button @click="clear">重置</el-button></el-form>
      <el-alert v-if="error" :title="error" type="error" class="edu-error" :closable="false"/>
      <el-table v-loading="loading" :data="rows" empty-text="暂无课题，请发布第一项实训"><el-table-column prop="id" label="ID" width="70"/><el-table-column prop="taskTitle" label="课题" min-width="230"/><el-table-column prop="courseName" label="课程" min-width="130"/><el-table-column prop="targetClass" label="班级" width="120"/><el-table-column label="截止时间" min-width="180"><template #default="{row}">{{ formatTime(row.deadline) }}</template></el-table-column><el-table-column label="状态" width="90"><template #default="{row}">{{ taskStatusLabel(row.taskStatus) }}</template></el-table-column><el-table-column label="操作" min-width="220"><template #default="{row}"><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link type="primary" @click="router.push({ path: '/edu/grades', query: { taskId: row.id } })">作业</el-button><el-button link type="primary" @click="router.push({ path: '/edu/analytics', query: { taskId: row.id } })">学情</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
      <el-pagination v-model:current-page="filters.pageNum" v-model:page-size="filters.pageSize" class="edu-pagination" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @current-change="load()" @size-change="load(true)"/>
    </section>
    <el-dialog v-model="dialog" :title="editingId ? '编辑课题' : '发布课题'" width="min(760px, 95vw)" :close-on-click-modal="false">
      <el-form label-position="top" :disabled="saving" @submit.prevent="save"><el-form-item label="课题名称" required><el-input v-model="form.taskTitle" maxlength="128"/></el-form-item><div class="edu-grid"><el-form-item label="课程名称" required><el-input v-model="form.courseName" maxlength="100"/></el-form-item><el-form-item label="面向班级（与学生班级一致）" required><el-input v-model="form.targetClass" maxlength="100"/></el-form-item></div><el-form-item label="实训要求" required><el-input v-model="form.taskRequirement" type="textarea" :rows="5" maxlength="20000"/></el-form-item>
        <el-form-item label="必含节点"><el-checkbox-group v-model="form.requiredNodesJson.mustInclude"><el-checkbox v-for="n in nodeTypes" :key="n.type" :value="n.type">{{ n.label }}</el-checkbox></el-checkbox-group></el-form-item><el-form-item label="最少节点数" required><el-input-number v-model="form.requiredNodesJson.minNodes" :min="0" :max="1000" :precision="0"/></el-form-item>
        <el-form-item label="评分细则（满分合计100）"><div style="width:100%"><div v-for="(item,i) in form.rubricJson ?? []" :key="i" class="edu-row"><el-input v-model="item.item" placeholder="评分项名称"/><el-input-number v-model="item.maxScore" :min="0.01" :max="100" :precision="2"/><el-button @click="form.rubricJson?.splice(i,1)">移除</el-button></div><el-button @click="(form.rubricJson ??= []).push({item:'',maxScore:10})">添加评分项</el-button><el-button @click="form.rubricJson = null">仅总分评分</el-button></div></el-form-item>
        <div class="edu-grid"><el-form-item label="截止时间" required><el-date-picker v-model="form.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择截止时间"/></el-form-item><el-form-item label="课题状态"><el-select v-model="form.taskStatus"><el-option v-for="s in [1,2,0]" :key="s" :value="s" :label="taskStatusLabel(s)"/></el-select></el-form-item></div>
      </el-form><template #footer><el-button :disabled="saving" @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存课题</el-button></template>
    </el-dialog>
  </div>
</template>
