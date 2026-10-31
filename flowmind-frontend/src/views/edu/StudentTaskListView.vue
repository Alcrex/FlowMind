<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { eduApi, reviewStatusLabel, taskStatusLabel, type EduTask, type TaskQuery } from '@/api/eduApi'
import { formatTime } from '@/api/types'
import './edu.css'
const router = useRouter(), rows = ref<EduTask[]>([]), total = ref(0), loading = ref(false), error = ref('')
const filters = reactive<TaskQuery>({ keyword: '', courseName: '', taskStatus: undefined, pageNum: 1, pageSize: 10 })
let requestVersion = 0
async function load(reset = false) {
  if (reset) filters.pageNum = 1
  const version = ++requestVersion; loading.value = true; error.value = ''
  try { const page = await eduApi.tasks({ ...filters }); if (version !== requestVersion) return; rows.value = page.records; total.value = page.total }
  catch { if (version === requestVersion) error.value = '课题加载失败，请重试' } finally { if (version === requestVersion) loading.value = false }
}
function clear() { Object.assign(filters, { keyword: '', courseName: '', taskStatus: undefined }); void load(true) }
onMounted(() => load())
</script>
<template>
  <div class="edu-page">
    <div class="edu-heading"><div><span class="eyebrow">TRAINING / 课程实训</span><h1>我的实训课题</h1><p>查看本班课题要求，提交工作流与实验报告，跟进批改反馈。</p></div></div>
    <section class="edu-panel">
      <el-form class="edu-filters" @submit.prevent="load(true)">
        <el-input v-model="filters.keyword" placeholder="课题名称 / 编号" clearable aria-label="课题关键词"/>
        <el-input v-model="filters.courseName" placeholder="课程名称" clearable aria-label="课程名称"/>
        <el-select v-model="filters.taskStatus" placeholder="课题状态" clearable aria-label="课题状态"><el-option v-for="s in [1,2,0]" :key="s" :value="s" :label="taskStatusLabel(s)"/></el-select>
        <el-button type="primary" native-type="submit">查询</el-button><el-button @click="clear">重置</el-button>
      </el-form>
      <el-alert v-if="error" class="edu-error" :title="error" type="error" :closable="false"/>
      <el-table v-loading="loading" :data="rows" empty-text="暂无符合条件的实训课题">
        <el-table-column prop="taskTitle" label="实训课题" min-width="240"/><el-table-column prop="courseName" label="课程" min-width="150"/><el-table-column prop="teacherName" label="教师" width="100"/>
        <el-table-column label="截止时间" min-width="180"><template #default="{row}">{{ formatTime(row.deadline) }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{row}"><el-tag>{{ taskStatusLabel(row.taskStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="我的作业" width="120"><template #default="{row}">{{ reviewStatusLabel(row.myReviewStatus) }}<span v-if="row.myScore != null"> · {{ row.myScore }}分</span></template></el-table-column>
        <el-table-column label="操作" width="130"><template #default="{row}"><el-button link type="primary" @click="router.push(`/edu/submit/${row.id}`)">查看 / 提交</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="filters.pageNum" v-model:page-size="filters.pageSize" class="edu-pagination" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @current-change="load()" @size-change="load(true)"/>
    </section>
  </div>
</template>
