<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { eduApi, type EduAnalytics } from '@/api/eduApi'
import './edu.css'
const route = useRoute(), filters = reactive<{taskId?: number; targetClass?: string}>({}), data = ref<EduAnalytics>(), loading = ref(false), error = ref('')
const scores = ref<HTMLDivElement>(), radar = ref<HTMLDivElement>(), trend = ref<HTMLDivElement>(), errors = ref<HTMLDivElement>()
let charts: echarts.ECharts[] = [], observer: ResizeObserver | undefined, version = 0
function dispose() { observer?.disconnect(); charts.forEach(c => c.dispose()); charts = [] }
function render() {
  dispose(); const d = data.value
  if (!d || !scores.value || !radar.value || !trend.value || !errors.value) return
  charts = [scores.value, radar.value, trend.value, errors.value].map(el => echarts.init(el))
  const color = ['#368c73','#6c83bc','#d1a35a','#a57b98','#a1bfad']
  charts[0].setOption({ color, tooltip: { trigger: 'axis' }, grid: { left: 55, right: 20, bottom: 85 }, xAxis: { type: 'category', data: d.scoreBuckets.map(b => b.bucket), axisLabel: { interval: 0, rotate: 20 } }, yAxis: { type: 'value', minInterval: 1 }, series: [{ type: 'bar', data: d.scoreBuckets.map(b => b.count), barMaxWidth: 45 }] })
  charts[1].setOption({ color, tooltip: {}, radar: { indicator: d.radarDimensions.map(r => ({ name: r.dimension, max: 100 })), radius: '58%' }, series: [{ type: 'radar', data: [{ name: '班级维度指标', value: d.radarDimensions.map(r => r.avgRate) }], areaStyle: { opacity: .2 } }] })
  charts[2].setOption({ color, tooltip: { trigger: 'axis' }, legend: { data: ['作业提交','沙箱调试'] }, grid: { left: 50, right: 25, bottom: 60 }, xAxis: { type: 'category', data: d.dailySubmissionTrend.map(t => t.date) }, yAxis: { type: 'value', minInterval: 1 }, series: [{ name: '作业提交', type: 'line', data: d.dailySubmissionTrend.map(t => t.submitCount) },{ name: '沙箱调试', type: 'line', data: d.dailySubmissionTrend.map(t => t.debugCount) }] })
  charts[3].setOption({ color, tooltip: { trigger: 'item' }, legend: { bottom: 0 }, series: [{ type: 'pie', radius: ['35%','65%'], data: d.errorNodeDistribution.map(e => ({ name: e.nodeType, value: e.errorCount })) }], graphic: d.errorNodeDistribution.length ? [] : [{ type: 'text', left: 'center', top: 'middle', style: { text: '暂无节点报错', fill: '#88928c' } }] })
  observer = new ResizeObserver(() => charts.forEach(c => c.resize()))
  ;[scores.value,radar.value,trend.value,errors.value].forEach(el => observer!.observe(el))
}
async function load() {
  const requestVersion = ++version; loading.value = true; error.value = ''
  try { const result = await eduApi.analytics(filters); if (requestVersion !== version) return; data.value = result; await nextTick(); render() }
  catch { if (requestVersion === version) { data.value = undefined; dispose(); error.value = '统计数据加载失败，请重试' } }
  finally { if (requestVersion === version) loading.value = false }
}
function clear() { filters.taskId = undefined; filters.targetClass = ''; void load() }
watch(() => route.query.taskId, id => { filters.taskId = id && Number(id) > 0 ? Number(id) : undefined; void load() })
onMounted(() => { filters.taskId = route.query.taskId && Number(route.query.taskId) > 0 ? Number(route.query.taskId) : undefined; void load() })
onBeforeUnmount(() => { version++; dispose() })
</script>
<template>
  <div class="edu-page" v-loading="loading"><div class="edu-heading"><div><span class="eyebrow">ANALYTICS / 教学洞察</span><h1>班级学情分析</h1><p>按课题和班级查看成绩、能力指标与调试反馈。</p></div></div>
    <section class="edu-panel"><el-form class="edu-filters" @submit.prevent="load"><el-input-number v-model="filters.taskId" :min="1" :precision="0" :controls="false" placeholder="课题 ID（可选）" aria-label="统计课题ID"/><el-input v-model="filters.targetClass" placeholder="班级（精确匹配）" clearable/><el-button type="primary" native-type="submit">查询统计</el-button><el-button @click="clear">重置</el-button></el-form><p class="edu-hint">成绩与及格率按已批改作业计算；调试与报错统计来自相关学生在所交工作流上的沙箱运行。</p></section>
    <el-alert v-if="error" class="edu-error" type="error" :title="error" :closable="false"/>
    <template v-if="data"><div class="edu-kpis"><div class="edu-kpi">作业提交<strong>{{ data.summary.totalSubmissions }}</strong></div><div class="edu-kpi">已批改<strong>{{ data.summary.gradedCount }}</strong></div><div class="edu-kpi">及格率<strong>{{ data.summary.passRate }}%</strong></div><div class="edu-kpi">平均成绩<strong>{{ data.summary.avgScore }}</strong></div></div>
      <el-empty v-if="!data.summary.totalSubmissions" description="暂无提交数据，学生提交后将生成统计"/>
      <div class="edu-grid"><section class="edu-panel"><h3>成绩分段</h3><div ref="scores" class="edu-chart" role="img" aria-label="成绩分段柱状图"/></section><section class="edu-panel"><h3>五维能力指标</h3><div ref="radar" class="edu-chart" role="img" aria-label="五维能力雷达图"/></section><section class="edu-panel"><h3>提交与调试趋势</h3><div ref="trend" class="edu-chart" role="img" aria-label="每日提交与调试趋势图"/></section><section class="edu-panel"><h3>高频报错节点分布</h3><div ref="errors" class="edu-chart" role="img" aria-label="节点报错分布图"/></section></div>
      <section class="edu-panel"><h3>指标说明</h3><p class="edu-hint">拓扑维度为快照拓扑校验通过率；Prompt 维度为同时配置系统提示和用户模板的模型节点比例；分支维度为具备有效条件配置及双分支的条件节点比例；沙箱维度为已结束调试的成功率；报告维度为教师对含“报告”评分项的平均得分率。无可用样本的维度显示为0。</p></section>
    </template>
  </div>
</template>
