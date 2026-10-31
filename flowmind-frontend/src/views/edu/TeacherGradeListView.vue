<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { eduApi, reviewStatusLabel, type Submission, type SubmissionQuery } from '@/api/eduApi'
import { formatTime } from '@/api/types'
import './edu.css'
const route = useRoute(), router = useRouter(), rows = ref<Submission[]>([]), total = ref(0), loading = ref(false), error = ref('')
const filters = reactive<SubmissionQuery>({ pageNum: 1, pageSize: 10 })
let requestVersion = 0
async function load(reset = false) {
  if (reset) filters.pageNum = 1
  if (filters.minScore != null && filters.maxScore != null && filters.minScore > filters.maxScore) { error.value = '最低分不能大于最高分'; return }
  const version = ++requestVersion; loading.value = true; error.value = ''
  try { const page = await eduApi.submissions({ ...filters }); if (version !== requestVersion) return; rows.value = page.records; total.value = page.total }
  catch { if (version === requestVersion) error.value = '作业加载失败，请重试' } finally { if (version === requestVersion) loading.value = false }
}
function clear() { Object.assign(filters, { taskId: undefined, targetClass: '', reviewStatus: undefined, isLate: undefined, autoCheckPassed: undefined, minScore: undefined, maxScore: undefined, studentKeyword: '' }); void load(true) }
watch(() => route.query.taskId, id => { filters.taskId = id && Number(id) > 0 ? Number(id) : undefined; void load(true) }, { immediate: true })
</script>
<template>
  <div class="edu-page"><div class="edu-heading"><div><span class="eyebrow">REVIEW / 作业批改</span><h1>学生实训作业</h1><p>组合筛选提交记录，查看固化快照并在线批改。</p></div><el-button @click="router.push('/edu/manage')">课题管理</el-button></div>
    <section class="edu-panel"><el-form class="edu-filters" @submit.prevent="load(true)">
      <el-input-number v-model="filters.taskId" placeholder="课题 ID" :min="1" :precision="0" :controls="false" aria-label="课题ID"/>
      <el-input v-model="filters.targetClass" placeholder="班级（精确匹配）" clearable/><el-input v-model="filters.studentKeyword" placeholder="学号 / 姓名" clearable/>
      <el-select v-model="filters.reviewStatus" placeholder="批改状态" clearable><el-option v-for="s in [0,1,2]" :key="s" :value="s" :label="reviewStatusLabel(s)"/></el-select>
      <el-select v-model="filters.isLate" placeholder="逾期情况" clearable><el-option :value="0" label="按时"/><el-option :value="1" label="逾期"/></el-select>
      <el-select v-model="filters.autoCheckPassed" placeholder="节点预检" clearable><el-option :value="1" label="已通过"/><el-option :value="0" label="未达标"/></el-select>
      <el-input-number v-model="filters.minScore" :min="0" :max="100" :precision="2" placeholder="最低分" :controls="false"/><el-input-number v-model="filters.maxScore" :min="0" :max="100" :precision="2" placeholder="最高分" :controls="false"/>
      <el-button type="primary" native-type="submit">查询</el-button><el-button @click="clear">重置</el-button>
    </el-form><el-alert v-if="error" class="edu-error" :title="error" type="error" :closable="false"/>
      <el-table v-loading="loading" :data="rows" empty-text="暂无符合筛选条件的提交记录"><el-table-column prop="studentName" label="姓名" width="100"/><el-table-column prop="studentNumber" label="学号 / 账号" width="140"/><el-table-column prop="targetClass" label="班级" width="110"/><el-table-column prop="taskTitle" label="课题" min-width="200"/><el-table-column label="提交时间" min-width="180"><template #default="{row}">{{ formatTime(row.submittedAt) }}</template></el-table-column><el-table-column label="提交检查" width="130"><template #default="{row}"><el-tag :type="row.isLate ? 'warning' : 'success'">{{ row.isLate ? '逾期' : '按时' }}</el-tag><el-tag :type="row.autoCheckPassed ? 'success' : 'danger'">{{ row.autoCheckPassed ? '节点通过' : '节点未达标' }}</el-tag></template></el-table-column><el-table-column label="批改状态" width="110"><template #default="{row}">{{ reviewStatusLabel(row.reviewStatus) }}</template></el-table-column><el-table-column label="得分" width="80"><template #default="{row}">{{ row.score ?? '—' }}</template></el-table-column><el-table-column label="操作" width="120"><template #default="{row}"><el-button link type="primary" @click="router.push(`/edu/review/${row.id}`)">回放与批改</el-button></template></el-table-column></el-table>
      <el-pagination v-model:current-page="filters.pageNum" v-model:page-size="filters.pageSize" class="edu-pagination" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @current-change="load()" @size-change="load(true)"/>
    </section>
  </div>
</template>
