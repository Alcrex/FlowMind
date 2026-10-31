<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { use } from 'echarts/core'
import { BarChart, LineChart, SankeyChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { init, type EChartsType } from 'echarts/core'
import { Refresh } from '@element-plus/icons-vue'
import { authAndAdminApi, type BigScreenStats } from '@/api/authAndAdminApi'
use([BarChart, LineChart, SankeyChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
const stats = ref<BigScreenStats>(), loading = ref(false), failed = ref(false), updatedAt = ref('')
const flow = ref<HTMLDivElement>(), ranking = ref<HTMLDivElement>(), trend = ref<HTMLDivElement>()
const charts: EChartsType[] = []
const cards = computed(() => [
  { label: '平台注册用户', value: stats.value?.kpi.totalUsers, unit: '人' }, { label: '有效工作流', value: stats.value?.kpi.totalWorkflows, unit: '个' },
  { label: '累计运行', value: stats.value?.kpi.totalExecutions, unit: '次' }, { label: '累计算力消耗', value: stats.value?.kpi.totalTokensUsed, unit: 'Tokens' }
])
const colors = ['#347f67', '#6e9cba', '#c1a362', '#9182b1', '#6fafa0', '#b99585']
let observer: ResizeObserver | undefined, timer: ReturnType<typeof setInterval> | undefined, active = true
async function load() {
  if (loading.value) return
  loading.value = true; failed.value = false
  try {
    const result = await authAndAdminApi.bigScreen()
    if (!active) return
    stats.value = result; updatedAt.value = new Date().toLocaleTimeString('zh-CN')
    await nextTick(); draw()
  } catch { if (active) failed.value = true } finally { if (active) loading.value = false }
}
function draw() {
  if (!stats.value || !flow.value || !ranking.value || !trend.value) return
  if (!charts.length) {
    charts.push(init(flow.value), init(ranking.value), init(trend.value))
    observer = new ResizeObserver(() => charts.forEach(c => c.resize()))
    ;[flow.value, ranking.value, trend.value].forEach(el => observer?.observe(el))
  }
  const data = stats.value, ranks = data.deptRanking.slice(0, 10).reverse()
  const common = { color: colors, textStyle: { fontFamily: 'Microsoft YaHei, sans-serif', color: '#718176', fontSize: 11 }, tooltip: { trigger: 'item', renderMode: 'richText' } }
  charts[0].setOption({ ...common, series: [{ type: 'sankey', left: 15, right: 110, top: 25, bottom: 25, nodeWidth: 13, nodeGap: 22,
    data: data.sankeyData.nodes, links: data.sankeyData.links, emphasis: { focus: 'adjacency' }, label: { fontSize: 11 }, lineStyle: { color: 'gradient', opacity: 0.25, curveness: 0.5 } }] }, true)
  charts[1].setOption({ ...common, legend: { data: ['工作流', '运行次数'], bottom: 0, textStyle: { color: '#718176', fontSize: 11 } }, grid: { left: 12, right: 28, top: 12, bottom: 35, containLabel: true },
    xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#eef2ed' } } }, yAxis: { type: 'category', data: ranks.map(r => r.deptClass), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { width: 90, overflow: 'truncate' } },
    series: [{ name: '工作流', type: 'bar', barMaxWidth: 12, itemStyle: { borderRadius: [0, 4, 4, 0] }, data: ranks.map(r => r.workflowCount) }, { name: '运行次数', type: 'bar', barMaxWidth: 12, itemStyle: { borderRadius: [0, 4, 4, 0] }, data: ranks.map(r => r.execCount) }] }, true)
  charts[2].setOption({ ...common, tooltip: { trigger: 'axis', renderMode: 'richText' }, legend: { data: ['模型调用', '平均延迟'], top: 0 }, grid: { left: 45, right: 60, top: 50, bottom: 35 },
    xAxis: { type: 'category', boundaryGap: false, data: data.hourlyTrend.map(h => h.hour), axisLine: { lineStyle: { color: '#dce5db' } } },
    yAxis: [{ type: 'value', name: '次', minInterval: 1, splitLine: { lineStyle: { color: '#eef2ed' } } }, { type: 'value', name: 'ms', splitLine: { show: false } }],
    series: [{ name: '模型调用', type: 'line', smooth: true, symbolSize: 5, areaStyle: { opacity: 0.08 }, data: data.hourlyTrend.map(h => h.callCount) }, { name: '平均延迟', type: 'line', yAxisIndex: 1, smooth: true, symbolSize: 4, data: data.hourlyTrend.map(h => h.avgLatencyMs) }] }, true)
}
onMounted(() => { void load(); timer = setInterval(() => { if (!document.hidden) void load() }, 60000) })
onBeforeUnmount(() => { active = false; if (timer) clearInterval(timer); observer?.disconnect(); charts.forEach(c => c.dispose()) })
</script>
<template><section class="monitor"><div class="page-heading"><div><p class="eyebrow">PLATFORM OBSERVATORY</p><h1>看见每一次智能连接</h1><p class="subtitle">全校工作流、算力与模型调用概览。{{ updatedAt ? `最近更新 ${updatedAt} · 每分钟自动刷新` : '正在连接平台数据' }}</p></div><el-button :icon="Refresh" :loading="loading" @click="load">刷新数据</el-button></div><el-alert v-if="failed" title="监控数据加载失败，可点击刷新重试" type="error" :closable="false" show-icon/><div v-loading="loading" class="monitor-content"><div class="metric-grid"><article v-for="(card, i) in cards" :key="card.label" class="metric-card"><div class="metric-top">{{ card.label }}<span :class="['metric-icon', ['green','purple','blue','amber'][i]]">{{ ['◉','⌘','↗','✦'][i] }}</span></div><strong>{{ card.value?.toLocaleString() ?? '—' }}</strong><p>{{ card.unit }}</p></article></div><div class="monitor-grid"><article class="panel"><div class="panel-heading"><div><h2>算力流向</h2><p>班级 → 用户角色 → 模型通道 · 按模型调用审计 Token 汇总</p></div><el-tag effect="plain" size="small">Tokens</el-tag></div><div class="chart-wrap"><div ref="flow" class="monitor-chart" role="img" aria-label="班级到角色到模型通道的算力流向桑基图"/><el-empty v-if="stats && !stats.sankeyData.links.length" description="暂无模型调用算力记录" class="chart-empty"/></div></article><article class="panel"><div class="panel-heading"><div><h2>班级活跃排行</h2><p>按运行次数与工作流数量排序 · 前 10 个班级</p></div></div><div class="chart-wrap"><div ref="ranking" class="monitor-chart" role="img" aria-label="班级工作流及运行次数排行"/><el-empty v-if="stats && !stats.deptRanking.length" description="暂无班级数据" class="chart-empty"/></div></article></div><article class="panel"><div class="panel-heading"><div><h2>最近 24 小时模型调用</h2><p>每小时调用次数与平均响应延迟 · 包含当前小时</p></div><span class="muted">{{ stats?.hourlyTrend.reduce((sum, h) => sum + h.callCount, 0) ?? '—' }} 次调用</span></div><div ref="trend" class="monitor-chart trend-chart" role="img" aria-label="最近24小时模型调用次数和平均延迟趋势"/></article></div></section></template>
<style scoped>.monitor-content{margin-top:24px}.monitor-grid{display:grid;grid-template-columns:1.3fr 1fr;gap:22px;margin-bottom:22px}.monitor-chart{height:340px;width:100%}.trend-chart{height:300px}.chart-wrap{position:relative;padding:0 14px}.chart-empty{position:absolute;inset:0;background:#ffffffed}.monitor .metric-card strong{font-size:28px}@media(max-width:1150px){.monitor-grid{grid-template-columns:1fr}.monitor .metric-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:600px){.monitor .metric-grid{grid-template-columns:1fr}}</style>
